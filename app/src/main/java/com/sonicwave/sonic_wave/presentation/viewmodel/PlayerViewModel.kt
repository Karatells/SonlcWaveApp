package com.sonicwave.sonic_wave.presentation.viewmodel

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import com.sonicwave.sonic_wave.data.local.AppDatabase
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianPalette
import com.sonicwave.sonic_wave.domain.models.MediaItemEntity
import com.sonicwave.sonic_wave.domain.models.SynchronizedLyricsEntity
import com.sonicwave.sonic_wave.domain.repository.MediaServerRepository
import com.sonicwave.sonic_wave.domain.services.AppSettingsService
import com.sonicwave.sonic_wave.presentation.audio.RepeatLoopMode
import com.sonicwave.sonic_wave.presentation.audio.SonicPlaybackController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

data class PlayerUiState(
    val queue: List<MediaItemEntity> = emptyList(),
    val currentIndex: Int = 0,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val repeatMode: RepeatLoopMode = RepeatLoopMode.OFF,
    val isShuffleEnabled: Boolean = false,
    val lyrics: SynchronizedLyricsEntity? = null,
    val activeLyricLineIndex: Int = -1,
    val dynamicSeedColor: Color = ObsidianPalette.DefaultDarkSeed,
    val sleepTimerRemainingMs: Long? = null,
    val equalizerEnabled: Boolean = false,
    val equalizerBandGainsDb: List<Double> = List(10) { 0.0 },
    val bassBoostEnabled: Boolean = false,
    val bassBoostDb: Double = 0.0,
    val virtualizerEnabled: Boolean = false,
    val virtualizerStrength: Int = 0,
    val loopStartMs: Long? = null,
    val loopEndMs: Long? = null
) {
    val currentTrack: MediaItemEntity?
        get() = queue.getOrNull(currentIndex)

    val progressFraction: Float
        get() {
            val dur = currentTrack?.durationMs ?: 0L
            return if (dur <= 0L) 0f else (positionMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
        }

    val bufferedFraction: Float
        get() {
            val dur = currentTrack?.durationMs ?: 0L
            return if (dur <= 0L) 0f else (bufferedPositionMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
        }
}

class PlayerViewModel(
    private val context: Context,
    private val playbackController: SonicPlaybackController,
    private val repository: MediaServerRepository,
    private val database: AppDatabase,
    private val settingsService: AppSettingsService
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val colorCache = LinkedHashMap<String, Color>(100, 0.75f, true)
    private var metadataJob: Job? = null
    private var lastLoadedTrackId: String? = null

    init {
        viewModelScope.launch {
            combine(
                playbackController.queue,
                playbackController.currentIndex,
                playbackController.isPlaying,
                playbackController.isBuffering,
                playbackController.positionMs
            ) { queue, idx, playing, buffering, pos ->
                PlayerCoreTuple(queue, idx, playing, buffering, pos)
            }.combine(
                combine(
                    playbackController.bufferedPositionMs,
                    playbackController.repeatMode,
                    playbackController.isShuffleEnabled,
                    playbackController.sleepTimerRemainingMs,
                    settingsService.settings
                ) { buf, repeat, shuffle, sleep, settings ->
                    PlayerPrefTuple(buf, repeat, shuffle, sleep, settings)
                }
            ) { core, pref ->
                val current = _state.value
                val track = core.queue.getOrNull(core.idx)
                val activeLine = current.lyrics?.activeLineIndexAt(core.pos) ?: -1
                if (track != null && track.id != lastLoadedTrackId) {
                    lastLoadedTrackId = track.id
                    loadTrackMetadataAndPalette(track)
                }
                current.copy(
                    queue = core.queue,
                    currentIndex = core.idx,
                    isPlaying = core.playing,
                    isBuffering = core.buffering,
                    positionMs = core.pos,
                    bufferedPositionMs = pref.buf,
                    repeatMode = pref.repeat,
                    isShuffleEnabled = pref.shuffle,
                    activeLyricLineIndex = activeLine,
                    sleepTimerRemainingMs = pref.sleep,
                    equalizerEnabled = pref.settings.equalizerEnabled,
                    equalizerBandGainsDb = pref.settings.equalizerBandsDb,
                    bassBoostEnabled = pref.settings.bassBoostEnabled,
                    bassBoostDb = pref.settings.bassBoostDb,
                    virtualizerEnabled = pref.settings.virtualizerEnabled,
                    virtualizerStrength = pref.settings.virtualizerStrength,
                    loopStartMs = playbackController.loopStartMs.value,
                    loopEndMs = playbackController.loopEndMs.value
                )
            }.collect { updated ->
                _state.value = updated
            }
        }
    }

    private fun loadTrackMetadataAndPalette(track: MediaItemEntity) {
        metadataJob?.cancel()
        metadataJob = viewModelScope.launch {
            val lyricsJob = launch {
                val lyrics = if (!track.serverId.startsWith("local_")) {
                    runCatching { repository.getLyrics(track.id, track.artist, track.title) }.getOrNull()
                } else null
                _state.value = _state.value.copy(
                    lyrics = lyrics,
                    activeLyricLineIndex = lyrics?.activeLineIndexAt(_state.value.positionMs) ?: -1
                )
            }

            val paletteJob = launch {
                val coverId = track.coverArtId
                if (coverId.isNullOrBlank()) {
                    _state.value = _state.value.copy(dynamicSeedColor = ObsidianPalette.DefaultDarkSeed)
                    return@launch
                }
                colorCache[coverId]?.let { cached ->
                    _state.value = _state.value.copy(dynamicSeedColor = cached)
                    return@launch
                }
                val extracted = withContext(Dispatchers.IO) {
                    runCatching {
                        val url = repository.buildCoverArtUri(coverId, 200)
                        val conn = URL(url).openConnection().apply {
                            connectTimeout = 4000
                            readTimeout = 4000
                        }
                        val bmp = conn.getInputStream().use { BitmapFactory.decodeStream(it) }
                        if (bmp != null) {
                            val pal = Palette.from(bmp).generate()
                            val argb = pal.getVibrantColor(
                                pal.getDominantColor(0xFFE5A93C.toInt())
                            )
                            bmp.recycle()
                            Color(argb)
                        } else null
                    }.getOrNull()
                }
                if (extracted != null) {
                    if (colorCache.size >= 100) {
                        val firstKey = colorCache.keys.firstOrNull()
                        if (firstKey != null) colorCache.remove(firstKey)
                    }
                    colorCache[coverId] = extracted
                    _state.value = _state.value.copy(dynamicSeedColor = extracted)
                }
            }

            lyricsJob.join()
            paletteJob.join()
        }
    }

    fun loadQueue(queue: List<MediaItemEntity>, initialIndex: Int = 0, initialPositionMs: Long = 0L) {
        viewModelScope.launch {
            playbackController.loadQueue(queue, initialIndex, initialPositionMs, autoPlay = true)
        }
    }

    fun playNext(track: MediaItemEntity) {
        viewModelScope.launch {
            playbackController.playNext(track)
        }
    }

    fun startTrackRadio(track: MediaItemEntity) {
        viewModelScope.launch {
            val similar = if (!track.serverId.startsWith("local_")) {
                runCatching {
                    repository.getQuickMix(
                        seedArtistId = track.artistId.takeIf { it.isNotBlank() },
                        genre = track.genre,
                        count = 30
                    )
                }.getOrDefault(emptyList())
            } else emptyList()
            val radioQueue = (listOf(track) + similar.filterNot { it.id == track.id }).distinctBy { it.id }
            playbackController.loadQueue(radioQueue, initialIndex = 0, autoPlay = true)
        }
    }

    fun togglePlayPause() = playbackController.togglePlayPause()
    fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)
    fun next() = playbackController.skipToNext()
    fun previous() = playbackController.skipToPrevious()
    fun skipToIndex(index: Int) = playbackController.skipToIndex(index)
    fun reorderQueue(oldIndex: Int, newIndex: Int) = playbackController.reorderQueue(oldIndex, newIndex)
    fun cycleRepeatMode() = playbackController.cycleRepeatMode()
    fun setRepeatMode(mode: RepeatLoopMode) = playbackController.setRepeatMode(mode)
    fun toggleShuffle() = playbackController.toggleShuffle()
    fun setSleepTimer(durationMs: Long?) = playbackController.setSleepTimer(durationMs)
    fun toggleAbLoopPoint() = playbackController.toggleAbLoopPoint()
    fun clearAbLoop() = playbackController.clearAbLoop()

    fun toggleFavorite() {
        val track = _state.value.currentTrack ?: return
        if (track.serverId.startsWith("local_")) return
        val nextFav = !track.isStarred
        playbackController.updateTrackInQueue(track.copy(isStarred = nextFav))
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.setFavorite(track.id, nextFav)
            } catch (_: Exception) {
                database.enqueueSyncAction(
                    serverId = track.serverId,
                    actionType = if (nextFav) "star" else "unstar",
                    targetId = track.id,
                    payloadJson = "{}"
                )
            }
        }
    }

    fun updateEqualizer(
        enabled: Boolean,
        bandGainsDb: List<Double>,
        bassBoostEnabled: Boolean,
        bassBoostDb: Double,
        virtualizerEnabled: Boolean = _state.value.virtualizerEnabled,
        virtualizerStrength: Int = _state.value.virtualizerStrength
    ) {
        viewModelScope.launch {
            settingsService.updateSettings {
                it.copy(
                    equalizerEnabled = enabled,
                    equalizerBandsDb = bandGainsDb,
                    bassBoostEnabled = bassBoostEnabled,
                    bassBoostDb = bassBoostDb,
                    virtualizerEnabled = virtualizerEnabled,
                    virtualizerStrength = virtualizerStrength
                )
            }
            playbackController.configureAudioEffects(
                enabled,
                bandGainsDb,
                bassBoostEnabled,
                bassBoostDb,
                virtualizerEnabled,
                virtualizerStrength
            )
        }
    }

    private data class PlayerCoreTuple(
        val queue: List<MediaItemEntity>,
        val idx: Int,
        val playing: Boolean,
        val buffering: Boolean,
        val pos: Long
    )

    private data class PlayerPrefTuple(
        val buf: Long,
        val repeat: RepeatLoopMode,
        val shuffle: Boolean,
        val sleep: Long?,
        val settings: com.sonicwave.sonic_wave.domain.services.AppSettingsData
    )
}
