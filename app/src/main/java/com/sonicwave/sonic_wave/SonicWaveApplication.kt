package com.sonicwave.sonic_wave

import android.app.Application
import com.sonicwave.sonic_wave.data.local.AppDatabase
import com.sonicwave.sonic_wave.data.remote.OpenSubsonicMediaServerRepository
import com.sonicwave.sonic_wave.domain.models.ServerProfile
import com.sonicwave.sonic_wave.domain.models.ServerProtocol
import com.sonicwave.sonic_wave.domain.repository.MediaServerRepository
import com.sonicwave.sonic_wave.domain.services.AppSettingsService
import com.sonicwave.sonic_wave.domain.services.LocalAudioService
import com.sonicwave.sonic_wave.domain.services.SyncQueueWorker
import com.sonicwave.sonic_wave.presentation.audio.SonicPlaybackController
import com.sonicwave.sonic_wave.presentation.viewmodel.DownloadViewModel
import com.sonicwave.sonic_wave.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class AppContainer(private val application: Application) {
    val database = AppDatabase(application)
    val localAudioService = LocalAudioService(application)
    val settingsService = AppSettingsService(application, database, localAudioService)

    private val unconfiguredProfile = ServerProfile(
        id = "unconfigured",
        name = "SonicWave",
        protocol = ServerProtocol.NAVIDROME,
        primaryUrl = "http://127.0.0.1:4533",
        username = "",
        secretOrPassword = ""
    )

    val repository: MediaServerRepository = OpenSubsonicMediaServerRepository(application, unconfiguredProfile)
    val playbackController = SonicPlaybackController(application, database, repository)
    val syncWorker = SyncQueueWorker(application, database, repository)

    val playerViewModel = PlayerViewModel(
        context = application,
        playbackController = playbackController,
        repository = repository,
        database = database,
        settingsService = settingsService
    )

    val downloadViewModel = DownloadViewModel(
        context = application,
        database = database,
        repository = repository,
        settingsService = settingsService,
        playbackController = playbackController
    )

    val hasConfiguredServer = MutableStateFlow(false)
    val isInitialized = MutableStateFlow(false)

    private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun initialize() {
        initScope.launch {
            localAudioService.loadFromDisk()
            settingsService.loadFromDisk()

            val profiles = database.getAllServerProfiles()
            val active = profiles.firstOrNull { it.isActive } ?: profiles.firstOrNull()
            if (active != null) {
                repository.updateServerProfile(active)
                syncWorker.startListening(active.id)
            }

            hasConfiguredServer.value = profiles.isNotEmpty() || localAudioService.folders.value.isNotEmpty()

            playbackController.restorePlaybackSession()
            playbackController.applySettings(
                settingsService.settings.value,
                applyRepeatMode = !playbackController.hasRestoredSession
            )
            // Keep playbackController synced with future settings changes
            launch {
                settingsService.settings.collect { newSettings ->
                    playbackController.applySettings(newSettings, applyRepeatMode = false)
                }
            }
            isInitialized.value = true
        }
    }
}

class SonicWaveApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.initialize()
    }
}
