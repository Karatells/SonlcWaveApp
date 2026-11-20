package com.sonicwave.sonic_wave.domain.services

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.sonicwave.sonic_wave.core.l10n.AppLocalizations
import com.sonicwave.sonic_wave.data.local.AppDatabase
import com.sonicwave.sonic_wave.domain.models.AudioCodec
import com.sonicwave.sonic_wave.domain.models.ServerProfile
import com.sonicwave.sonic_wave.domain.models.TranscodingProfile
import com.sonicwave.sonic_wave.domain.repository.MediaServerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

enum class ObsidianThemeMode {
    OBSIDIAN_DARK,
    PURE_OLED,
    WARM_ALABASTER
}

data class AppSettingsData(
    val themeMode: ObsidianThemeMode = ObsidianThemeMode.OBSIDIAN_DARK,
    val dynamicColorsEnabled: Boolean = true,
    val vinylPulseWavesEnabled: Boolean = true,
    val wifiCodec: AudioCodec = AudioCodec.ORIGINAL,
    val wifiMaxBitrateKbps: Int = 0,
    val mobileCodec: AudioCodec = AudioCodec.OPUS,
    val mobileMaxBitrateKbps: Int = 160,
    val maxCacheGb: Double = 2.0,
    val autoCacheEnabled: Boolean = true,
    val wifiOnlyDownloads: Boolean = false,
    val crossfadeSeconds: Double = 0.0,
    val replayGainEnabled: Boolean = true,
    val preferAlbumGain: Boolean = false,
    val replayGainPreampDb: Double = 0.0,
    val equalizerEnabled: Boolean = false,
    val equalizerPreset: String = "flat",
    val equalizerBandsDb: List<Double> = List(10) { 0.0 },
    val bassBoostEnabled: Boolean = false,
    val bassBoostDb: Double = 0.0,
    val virtualizerEnabled: Boolean = false,
    val virtualizerStrength: Int = 0,
    val playbackSpeed: Double = 1.0,
    val playbackPitch: Double = 1.0,
    val skipSilenceForSpoken: Boolean = true,
    val defaultRepeatMode: String = "off",
    val scrobbleEnabled: Boolean = true,
    val languageCode: String = "system"
) {
    fun toTranscodingProfile(): TranscodingProfile = TranscodingProfile(
        wifiCodec = wifiCodec,
        wifiMaxBitrateKbps = wifiMaxBitrateKbps,
        mobileCodec = mobileCodec,
        mobileMaxBitrateKbps = mobileMaxBitrateKbps
    )
}

data class SettingsExportBundle(
    val version: Int = 1,
    val settings: AppSettingsData = AppSettingsData(),
    val servers: List<ServerProfile> = emptyList(),
    val localFolders: List<LocalAudioFolder> = emptyList()
)

class AppSettingsService(
    private val context: Context,
    private val database: AppDatabase,
    private val localAudioService: LocalAudioService
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    private val _settings = MutableStateFlow(AppSettingsData())
    val settings: StateFlow<AppSettingsData> = _settings.asStateFlow()

    private fun settingsFile(): File = File(context.filesDir, "sonicwave_settings.json")

    suspend fun loadFromDisk() = withContext(Dispatchers.IO) {
        val file = settingsFile()
        if (file.exists()) {
            runCatching {
                val parsed = gson.fromJson(file.readText(), AppSettingsData::class.java)
                if (parsed != null) {
                    _settings.value = sanitize(parsed)
                }
            }
        }
        AppLocalizations.setLocaleMode(_settings.value.languageCode)
    }

    suspend fun updateSettings(transform: (AppSettingsData) -> AppSettingsData) {
        val updated = sanitize(transform(_settings.value))
        _settings.value = updated
        AppLocalizations.setLocaleMode(updated.languageCode)
        withContext(Dispatchers.IO) {
            runCatching { settingsFile().writeText(gson.toJson(updated)) }
        }
    }

    private fun sanitize(data: AppSettingsData): AppSettingsData {
        val bands = if (data.equalizerBandsDb.size == 10) {
            data.equalizerBandsDb.map { it.coerceIn(-12.0, 12.0) }
        } else {
            List(10) { 0.0 }
        }
        return data.copy(
            maxCacheGb = data.maxCacheGb.coerceIn(0.5, 16.0),
            crossfadeSeconds = data.crossfadeSeconds.coerceIn(0.0, 12.0),
            replayGainPreampDb = data.replayGainPreampDb.coerceIn(-12.0, 12.0),
            equalizerBandsDb = bands,
            bassBoostDb = data.bassBoostDb.coerceIn(0.0, 12.0),
            virtualizerStrength = data.virtualizerStrength.coerceIn(0, 1000),
            playbackSpeed = data.playbackSpeed.coerceIn(0.5, 2.0),
            playbackPitch = data.playbackPitch.coerceIn(0.5, 1.5)
        )
    }

    suspend fun exportToJsonString(includePasswords: Boolean = true): String {
        val servers = database.getAllServerProfiles().map { srv ->
            if (includePasswords) srv else srv.copy(secretOrPassword = "")
        }
        val bundle = SettingsExportBundle(
            version = 1,
            settings = _settings.value,
            servers = servers,
            localFolders = localAudioService.folders.value
        )
        return gson.toJson(bundle)
    }

    suspend fun importFromJsonString(
        rawJson: String,
        repository: MediaServerRepository
    ): Pair<Int, Int> {
        val bundle = gson.fromJson(rawJson, SettingsExportBundle::class.java)
            ?: throw IllegalArgumentException("Invalid JSON configuration")

        updateSettings { bundle.settings }

        var importedServers = 0
        for (srv in bundle.servers) {
            database.upsertServerProfile(srv)
            if (srv.isActive) {
                database.setActiveServerProfile(srv.id)
                repository.updateServerProfile(srv)
            }
            importedServers++
        }
        if (bundle.servers.isNotEmpty() && bundle.servers.none { it.isActive }) {
            val first = bundle.servers.first().copy(isActive = true)
            database.setActiveServerProfile(first.id)
            repository.updateServerProfile(first)
        }

        if (bundle.localFolders.isNotEmpty()) {
            localAudioService.restoreFoldersFromImport(bundle.localFolders)
        }
        return importedServers to bundle.localFolders.size
    }

    companion object {
        val EQ_PRESETS: Map<String, List<Double>> = mapOf(
            "flat" to listOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
            "bass" to listOf(6.0, 5.0, 4.0, 2.0, 0.0, -1.0, 0.0, 1.0, 2.0, 2.5),
            "rock" to listOf(4.5, 3.0, 1.5, -1.5, -2.0, 0.5, 2.5, 4.0, 4.5, 5.0),
            "jazz" to listOf(2.5, 1.5, 0.5, 2.0, -1.5, -1.0, 1.0, 2.0, 3.0, 3.5),
            "vocal" to listOf(-2.0, -1.0, 0.5, 3.0, 4.5, 4.0, 2.5, 1.0, 0.0, -1.0),
            "club" to listOf(5.0, 4.5, 2.5, 0.0, -1.0, 1.0, 2.5, 3.5, 4.0, 3.0)
        )
    }
}
