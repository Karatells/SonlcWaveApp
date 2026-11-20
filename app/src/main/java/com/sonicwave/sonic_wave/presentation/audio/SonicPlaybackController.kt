package com.sonicwave.sonic_wave.presentation.audio

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.google.gson.Gson
import com.sonicwave.sonic_wave.core.network.HttpClientFactory
import com.sonicwave.sonic_wave.core.security.SecureCredentialsStore
import com.sonicwave.sonic_wave.data.local.AppDatabase
import com.sonicwave.sonic_wave.domain.models.MediaContentType
import com.sonicwave.sonic_wave.domain.models.MediaItemEntity
import com.sonicwave.sonic_wave.domain.models.TranscodingProfile
import com.sonicwave.sonic_wave.domain.repository.MediaServerRepository
import com.sonicwave.sonic_wave.domain.services.AppSettingsData
import com.sonicwave.sonic_wave.presentation.widget.SonicWaveWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

enum class RepeatLoopMode {
    OFF, ALL, ONE
}

data class PlaybackSessionSnapshot(
    val queue: List<MediaItemEntity> = emptyList(),
    val currentIndex: Int = 0,
    val positionMs: Long = 0L,
    val repeatMode: RepeatLoopMode = RepeatLoopMode.OFF,
    val shuffleEnabled: Boolean = false
)

data class StudioTelemetrySnapshot(
    val totalTracksPlayed: Int = 0,
    val totalListeningMs: Long = 0L,
    val artistCounts: Map<String, Int> = emptyMap(),
    val codecCounts: Map<String, Int> = emptyMap()
) {
    val topArtist: String?
        get() = artistCounts.maxByOrNull { it.value }?.key

    val topCodec: String?
        get() = codecCounts.maxByOrNull { it.value }?.key
}

@OptIn(UnstableApi::class)
class SonicPlaybackController(
    private val context: Context,
    private val database: AppDatabase,
    private val repository: MediaServerRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val gson = Gson()
    private val credentialsStore = SecureCredentialsStore.getInstance(context)
    private val precacheClient = HttpClientFactory.createStreamingClient(
        profileProvider = { repository.currentProfile },
        credentialsStore = credentialsStore
    )

    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs = */ 30_000,
            /* maxBufferMs = */ 120_000,
            /* bufferForPlaybackMs = */ 1_200,
            /* bufferForPlaybackAfterRebufferMs = */ 2_500
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setLoadControl(loadControl)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    private var equalizer: Equalizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    private val _loopStartMs = MutableStateFlow<Long?>(null)
    val loopStartMs: StateFlow<Long?> = _loopStartMs.asStateFlow()

    private val _loopEndMs = MutableStateFlow<Long?>(null)
    val loopEndMs: StateFlow<Long?> = _loopEndMs.asStateFlow()

    private val _telemetry = MutableStateFlow(StudioTelemetrySnapshot())
    val telemetry: StateFlow<StudioTelemetrySnapshot> = _telemetry.asStateFlow()

    private val _queue = MutableStateFlow<List<MediaItemEntity>>(emptyList())
    val queue: StateFlow<List<MediaItemEntity>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _bufferedPositionMs = MutableStateFlow(0L)
    val bufferedPositionMs: StateFlow<Long> = _bufferedPositionMs.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatLoopMode.OFF)
    val repeatMode: StateFlow<RepeatLoopMode> = _repeatMode.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _sleepTimerRemainingMs = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingMs: StateFlow<Long?> = _sleepTimerRemainingMs.asStateFlow()

    private val _cacheChangedTick = MutableStateFlow(0L)
    val cacheChangedTick: StateFlow<Long> = _cacheChangedTick.asStateFlow()

    var hasRestoredSession: Boolean = false
        private set

    // Preferences
    private var transcodingProfile = TranscodingProfile()
    private var replayGainEnabled = true
    private var preferAlbumGain = false
    private var replayGainPreampDb = 0.0
    private var crossfadeDurationMs = 0L
    private var autoCacheEnabled = true
    private var wifiOnlyDownloads = false
    private var maxAutoCacheBytes = 2L * 1024L * 1024L * 1024L
    private var scrobbleEnabled = true

    private var eqEnabled = false
    private var eqBandsDb = List(10) { 0.0 }
    private var bassBoostEnabled = false
    private var bassBoostDb = 0.0
    private var virtualizerEnabled = false
    private var virtualizerStrength = 0
    private var skipSilenceForSpoken = true

    private var sleepTimerJob: Job? = null
    private var crossfadeJob: Job? = null
    private var sessionSaveJob: Job? = null
    private var precacheJob: Job? = null
    private var lastScrobbledTrackId: String? = null
    private var scrobbleSubmittedForCurrent = false
    private var lastBookmarkSaveMs = 0L

    init {
        loadTelemetryFromDisk()
        setupPlayerListeners()
        startPositionTicker()
    }

    private fun setupPlayerListeners() {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                updateWidgetFromCurrent()
                if (!isPlaying) {
                    saveBookmarkIfNeeded()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _isBuffering.value = playbackState == Player.STATE_BUFFERING
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val idx = player.currentMediaItemIndex
                if (idx in _queue.value.indices) {
                    _currentIndex.value = idx
                    val track = _queue.value[idx]
                    clearAbLoop()
                    cancelCrossfade()
                    applyReplayGainForTrack(track)
                    applySkipSilenceForTrack(track)
                    onTrackStarted(track)
                    scheduleSessionSave()
                    precacheUpcomingTracks(idx)
                    updateWidgetFromCurrent()
                }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                initAudioEffects(audioSessionId)
            }
        })
    }

    private fun initAudioEffects(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId == 0) return
        runCatching {
            equalizer?.release()
            equalizer = Equalizer(0, audioSessionId)
        }
        runCatching {
            loudnessEnhancer?.release()
            loudnessEnhancer = LoudnessEnhancer(audioSessionId)
        }
        runCatching {
            bassBoost?.release()
            bassBoost = BassBoost(0, audioSessionId)
        }
        runCatching {
            virtualizer?.release()
            virtualizer = Virtualizer(0, audioSessionId)
        }
        applyAudioEffects()
    }

    private fun applyAudioEffects() {
        runCatching {
            equalizer?.let { eq ->
                eq.enabled = eqEnabled
                val numBands = eq.numberOfBands.toInt()
                val range = eq.bandLevelRange
                val minMb = range[0].toInt()
                val maxMb = range[1].toInt()
                for (i in 0 until numBands) {
                    val db = eqBandsDb.getOrElse(i) { 0.0 }
                    val mb = (db * 100.0).toInt().coerceIn(minMb, maxMb).toShort()
                    eq.setBandLevel(i.toShort(), mb)
                }
            }
        }
        runCatching {
            loudnessEnhancer?.let { le ->
                le.enabled = bassBoostEnabled && bassBoostDb > 0.0
                le.setTargetGain((bassBoostDb * 100.0).toInt().coerceIn(0, 1200))
            }
        }
        runCatching {
            bassBoost?.let { bb ->
                bb.enabled = bassBoostEnabled && bassBoostDb > 0.0
                if (bb.strengthSupported) {
                    val strength = ((bassBoostDb / 12.0) * 1000.0).toInt().coerceIn(0, 1000).toShort()
                    bb.setStrength(strength)
                }
            }
        }
        runCatching {
            virtualizer?.let { vz ->
                vz.enabled = virtualizerEnabled && virtualizerStrength > 0
                if (vz.strengthSupported) {
                    vz.setStrength(virtualizerStrength.coerceIn(0, 1000).toShort())
                }
            }
        }
    }

    fun configureAudioEffects(
        equalizerEnabled: Boolean,
        bandGainsDb: List<Double>,
        bassBoostEnabled: Boolean,
        bassBoostStrengthDb: Double,
        virtualizerEnabled: Boolean = this.virtualizerEnabled,
        virtualizerStrength: Int = this.virtualizerStrength
    ) {
        this.eqEnabled = equalizerEnabled
        this.eqBandsDb = bandGainsDb
        this.bassBoostEnabled = bassBoostEnabled
        this.bassBoostDb = bassBoostStrengthDb
        this.virtualizerEnabled = virtualizerEnabled
        this.virtualizerStrength = virtualizerStrength
        applyAudioEffects()
    }

    private fun applySkipSilenceForTrack(track: MediaItemEntity?) {
        val enable = skipSilenceForSpoken && track != null && track.contentType != MediaContentType.MUSIC
        player.skipSilenceEnabled = enable
    }

    fun toggleAbLoopPoint() {
        val pos = player.currentPosition.coerceAtLeast(0L)
        val a = _loopStartMs.value
        val b = _loopEndMs.value
        when {
            a == null -> _loopStartMs.value = pos
            b == null -> {
                if (pos > a + 500L) {
                    _loopEndMs.value = pos
                } else {
                    _loopStartMs.value = pos
                }
            }
            else -> clearAbLoop()
        }
    }

    fun clearAbLoop() {
        _loopStartMs.value = null
        _loopEndMs.value = null
    }

    fun applySettings(settings: AppSettingsData, applyRepeatMode: Boolean = true) {
        transcodingProfile = settings.toTranscodingProfile()
        replayGainEnabled = settings.replayGainEnabled
        preferAlbumGain = settings.preferAlbumGain
        replayGainPreampDb = settings.replayGainPreampDb
        crossfadeDurationMs = (settings.crossfadeSeconds * 1000.0).toLong()
        autoCacheEnabled = settings.autoCacheEnabled
        wifiOnlyDownloads = settings.wifiOnlyDownloads
        maxAutoCacheBytes = (settings.maxCacheGb * 1024.0 * 1024.0 * 1024.0).toLong()
        scrobbleEnabled = settings.scrobbleEnabled
        skipSilenceForSpoken = settings.skipSilenceForSpoken
        player.playbackParameters = PlaybackParameters(
            settings.playbackSpeed.toFloat().coerceIn(0.5f, 2.5f),
            settings.playbackPitch.toFloat().coerceIn(0.5f, 1.5f)
        )

        configureAudioEffects(
            equalizerEnabled = settings.equalizerEnabled,
            bandGainsDb = settings.equalizerBandsDb,
            bassBoostEnabled = settings.bassBoostEnabled,
            bassBoostStrengthDb = settings.bassBoostDb,
            virtualizerEnabled = settings.virtualizerEnabled,
            virtualizerStrength = settings.virtualizerStrength
        )

        if (applyRepeatMode) {
            val mode = when (settings.defaultRepeatMode.lowercase()) {
                "all" -> RepeatLoopMode.ALL
                "one" -> RepeatLoopMode.ONE
                else -> RepeatLoopMode.OFF
            }
            setRepeatMode(mode)
        }

        val current = _queue.value.getOrNull(_currentIndex.value)
        current?.let { applyReplayGainForTrack(it) }
        applySkipSilenceForTrack(current)
    }

    private fun startPositionTicker() {
        scope.launch {
            while (true) {
                if (player.isPlaying) {
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    val dur = player.duration.coerceAtLeast(0L)
                    _positionMs.value = pos
                    _bufferedPositionMs.value = player.bufferedPosition.coerceAtLeast(pos)

                    val a = _loopStartMs.value
                    val b = _loopEndMs.value
                    if (a != null && b != null && pos >= b) {
                        player.seekTo(a)
                        _positionMs.value = a
                    }

                    val track = _queue.value.getOrNull(_currentIndex.value)
                    if (track != null) {
                        // Scrobble submission at 50% (clamped 30s..240s)
                        if (scrobbleEnabled && !scrobbleSubmittedForCurrent && dur > 0L) {
                            val threshold = (dur / 2L).coerceIn(30_000L, 240_000L)
                            if (pos >= threshold) {
                                scrobbleSubmittedForCurrent = true
                                dispatchScrobble(track, submission = true)
                            }
                        }

                        // Bookmark for podcasts/audiobooks every 15s
                        if ((track.contentType == MediaContentType.AUDIOBOOK || track.contentType == MediaContentType.PODCAST) &&
                            pos - lastBookmarkSaveMs >= 15_000L
                        ) {
                            lastBookmarkSaveMs = pos
                            saveBookmarkForTrack(track, pos)
                        }

                        // Crossfade
                        if (crossfadeDurationMs > 0L && crossfadeJob == null && dur > crossfadeDurationMs && player.hasNextMediaItem()) {
                            val remaining = dur - pos
                            if (remaining in 100L..crossfadeDurationMs) {
                                triggerCrossfade(remaining)
                            }
                        }
                    }
                }
                delay(250L)
            }
        }
    }

    private fun triggerCrossfade(remainingMs: Long) {
        crossfadeJob?.cancel()
        crossfadeJob = scope.launch {
            val baseVol = player.volume
            val steps = 16
            val stepDelay = (remainingMs / steps).coerceAtLeast(40L)
            for (i in 1..steps) {
                val factor = 1f - (i.toFloat() / steps.toFloat())
                player.volume = (baseVol * factor).coerceAtLeast(0.05f)
                delay(stepDelay)
            }
            crossfadeJob = null
        }
    }

    private fun cancelCrossfade() {
        crossfadeJob?.cancel()
        crossfadeJob = null
    }

    private fun applyReplayGainForTrack(track: MediaItemEntity) {
        if (!replayGainEnabled) {
            player.volume = 1.0f
            return
        }
        player.volume = track.replayGain.computeLinearScale(
            preferAlbumGain = preferAlbumGain,
            preampDb = replayGainPreampDb
        )
    }

    suspend fun loadQueue(
        items: List<MediaItemEntity>,
        initialIndex: Int = 0,
        initialPositionMs: Long = 0L,
        autoPlay: Boolean = true
    ) {
        if (items.isEmpty()) return
        val safeIdx = initialIndex.coerceIn(0, items.lastIndex)
        _queue.value = items
        _currentIndex.value = safeIdx

        val mediaItems = withContext(Dispatchers.IO) {
            items.map { buildExoMediaItem(it) }
        }

        player.setMediaItems(mediaItems, safeIdx, initialPositionMs)
        player.prepare()
        if (autoPlay) {
            player.play()
        }
        applyReplayGainForTrack(items[safeIdx])
        onTrackStarted(items[safeIdx])
        scheduleSessionSave()
        precacheUpcomingTracks(safeIdx)
        updateWidgetFromCurrent()
    }

    private suspend fun buildExoMediaItem(item: MediaItemEntity): MediaItem {
        val uriString = resolvePlaybackUri(item)
        val artUri = item.coverArtId?.let {
            runCatching { Uri.parse(repository.buildCoverArtUri(it, 600)) }.getOrNull()
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(item.title)
            .setArtist(item.artist)
            .setAlbumTitle(item.album)
            .setArtworkUri(artUri)
            .build()

        return MediaItem.Builder()
            .setMediaId(item.id)
            .setUri(uriString)
            .setMediaMetadata(metadata)
            .build()
    }

    private suspend fun resolvePlaybackUri(item: MediaItemEntity): String {
        val local = item.localFilePath
        if (!local.isNullOrBlank()) {
            if (local.startsWith("content://") || File(local).exists()) {
                return local
            }
        }
        val cached = database.getCachedTrack(item.id, item.serverId)
        if (cached != null && File(cached.localFilePath).exists()) {
            database.touchCachedTrack(item.id, item.serverId)
            return Uri.fromFile(File(cached.localFilePath)).toString()
        }
        return repository.buildStreamUri(
            trackId = item.id,
            profile = transcodingProfile,
            isWifi = isWifiConnected()
        )
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
        _positionMs.value = positionMs.coerceAtLeast(0L)
        scheduleSessionSave()
    }

    fun skipToNext() {
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        } else if (_queue.value.isNotEmpty() && _repeatMode.value == RepeatLoopMode.ALL) {
            player.seekTo(0, 0L)
        }
    }

    fun skipToPrevious() {
        if (player.currentPosition > 4000L) {
            player.seekTo(0L)
        } else if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        } else if (_queue.value.isNotEmpty()) {
            player.seekTo(0L)
        }
    }

    fun skipToIndex(index: Int) {
        if (index in _queue.value.indices) {
            _currentIndex.value = index
            player.seekTo(index, 0L)
            player.play()
        }
    }

    suspend fun playNext(item: MediaItemEntity) {
        val currentList = _queue.value.toMutableList()
        if (currentList.isEmpty()) {
            loadQueue(listOf(item), initialIndex = 0, autoPlay = true)
            return
        }
        val insertIdx = (_currentIndex.value + 1).coerceIn(0, currentList.size)
        currentList.add(insertIdx, item)
        _queue.value = currentList
        val mediaItem = withContext(Dispatchers.IO) { buildExoMediaItem(item) }
        player.addMediaItem(insertIdx, mediaItem)
        scheduleSessionSave()
    }

    fun reorderQueue(oldIndex: Int, newIndex: Int) {
        val list = _queue.value.toMutableList()
        if (oldIndex !in list.indices || newIndex !in list.indices || oldIndex == newIndex) return
        val moved = list.removeAt(oldIndex)
        list.add(newIndex, moved)
        _queue.value = list
        player.moveMediaItem(oldIndex, newIndex)
        _currentIndex.value = player.currentMediaItemIndex.coerceIn(0, list.lastIndex)
        scheduleSessionSave()
    }

    fun setRepeatMode(mode: RepeatLoopMode) {
        _repeatMode.value = mode
        player.repeatMode = when (mode) {
            RepeatLoopMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatLoopMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatLoopMode.ONE -> Player.REPEAT_MODE_ONE
        }
        scheduleSessionSave()
    }

    fun cycleRepeatMode() {
        val next = when (_repeatMode.value) {
            RepeatLoopMode.OFF -> RepeatLoopMode.ALL
            RepeatLoopMode.ALL -> RepeatLoopMode.ONE
            RepeatLoopMode.ONE -> RepeatLoopMode.OFF
        }
        setRepeatMode(next)
    }

    fun toggleShuffle() {
        val next = !_isShuffleEnabled.value
        _isShuffleEnabled.value = next
        player.shuffleModeEnabled = next
        scheduleSessionSave()
    }

    fun setSleepTimer(durationMs: Long?) {
        sleepTimerJob?.cancel()
        if (durationMs == null || durationMs <= 0L) {
            _sleepTimerRemainingMs.value = null
            _queue.value.getOrNull(_currentIndex.value)?.let { applyReplayGainForTrack(it) }
            return
        }

        var remaining: Long = durationMs
        _sleepTimerRemainingMs.value = remaining
        sleepTimerJob = scope.launch {
            val baseTrack = _queue.value.getOrNull(_currentIndex.value)
            val baseVol = baseTrack?.replayGain?.computeLinearScale(preferAlbumGain, replayGainPreampDb) ?: 1.0f
            while (remaining > 0L) {
                delay(1000L)
                remaining = (remaining - 1000L).coerceAtLeast(0L)
                _sleepTimerRemainingMs.value = remaining
                if (remaining in 1L..10_000L) {
                    val fade = (remaining / 10_000f).coerceIn(0.05f, 1.0f)
                    player.volume = baseVol * fade
                }
            }
            player.pause()
            _sleepTimerRemainingMs.value = null
            _queue.value.getOrNull(_currentIndex.value)?.let { applyReplayGainForTrack(it) }
        }
    }

    fun updateTrackInQueue(updated: MediaItemEntity) {
        _queue.value = _queue.value.map { if (it.id == updated.id && it.serverId == updated.serverId) updated else it }
    }

    fun onTrackCachedExternally(item: MediaItemEntity, localPath: String) {
        val updated = item.copy(localFilePath = localPath)
        updateTrackInQueue(updated)
        val idx = _queue.value.indexOfFirst { it.id == item.id && it.serverId == item.serverId }
        if (idx >= 0) {
            scope.launch { hotSwapQueueItemToFile(idx, updated) }
        }
        _cacheChangedTick.value = System.currentTimeMillis()
    }

    private suspend fun hotSwapQueueItemToFile(index: Int, updatedItem: MediaItemEntity) {
        updateTrackInQueue(updatedItem)
        val mediaItem = withContext(Dispatchers.IO) { buildExoMediaItem(updatedItem) }
        withContext(Dispatchers.Main.immediate) {
            if (index in 0 until player.mediaItemCount && player.currentMediaItemIndex != index) {
                player.replaceMediaItem(index, mediaItem)
            }
        }
    }

    private fun precacheUpcomingTracks(fromIndex: Int) {
        if (!autoCacheEnabled) return
        if (wifiOnlyDownloads && !isWifiConnected()) return

        precacheJob?.cancel()
        precacheJob = scope.launch(Dispatchers.IO) {
            val currentList = _queue.value
            if (currentList.isEmpty()) return@launch

            val nextIdx = when {
                fromIndex + 1 < currentList.size -> fromIndex + 1
                _repeatMode.value == RepeatLoopMode.ALL && currentList.size > 1 -> 0
                else -> -1
            }
            val nextNextIdx = when {
                nextIdx >= 0 && nextIdx + 1 < currentList.size -> nextIdx + 1
                nextIdx == 0 && currentList.size > 2 -> 1
                else -> -1
            }

            // Prioritize next track first for instant skip, then current & +2
            val targetIndices = listOf(nextIdx, fromIndex, nextNextIdx)
                .filter { it in currentList.indices }
                .distinct()

            val dir = File(context.filesDir, "offline_media").apply { mkdirs() }
            for (targetIdx in targetIndices) {
                val item = currentList.getOrNull(targetIdx) ?: continue
                if (item.serverId.startsWith("local_") || item.isOfflineAvailable) continue
                val existing = database.getCachedTrack(item.id, item.serverId)
                if (existing != null && File(existing.localFilePath).exists()) {
                    hotSwapQueueItemToFile(targetIdx, item.copy(localFilePath = existing.localFilePath))
                    continue
                }

                val safeKey = "${item.serverId}_${item.id}".replace(Regex("[^a-zA-Z0-9_-]"), "_")
                val ext = item.suffix?.lowercase()?.takeIf { it.isNotBlank() } ?: "audio"
                val targetFile = File(dir, "auto_$safeKey.$ext")
                val partFile = File(dir, "auto_$safeKey.$ext.part")

                runCatching {
                    val url = repository.buildStreamUri(item.id, transcodingProfile, isWifiConnected())
                    val req = Request.Builder().url(url).get().build()
                    precacheClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@use
                        val body = resp.body ?: return@use
                        partFile.outputStream().use { out ->
                            body.byteStream().copyTo(out)
                        }
                        if (partFile.renameTo(targetFile)) {
                            database.upsertCachedTrack(
                                item = item,
                                localFilePath = targetFile.absolutePath,
                                fileSizeBytes = targetFile.length(),
                                isPinnedDownload = false
                            )
                            database.evictAutoCacheUsingLru(maxAutoCacheBytes)
                            hotSwapQueueItemToFile(targetIdx, item.copy(localFilePath = targetFile.absolutePath))
                            _cacheChangedTick.value = System.currentTimeMillis()
                        }
                    }
                }
            }
        }
    }

    private fun onTrackStarted(track: MediaItemEntity) {
        if (lastScrobbledTrackId != track.id) {
            lastScrobbledTrackId = track.id
            scrobbleSubmittedForCurrent = false
            lastBookmarkSaveMs = 0L
            recordTelemetry(track)
            if (scrobbleEnabled && !track.serverId.startsWith("local_")) {
                dispatchScrobble(track, submission = false)
            }
        }
    }

    private fun telemetryFile(): File = File(context.filesDir, "studio_telemetry.json")

    private fun loadTelemetryFromDisk() {
        scope.launch(Dispatchers.IO) {
            val file = telemetryFile()
            if (file.exists()) {
                runCatching {
                    val snap = gson.fromJson(file.readText(), StudioTelemetrySnapshot::class.java)
                    if (snap != null) _telemetry.value = snap
                }
            }
        }
    }

    private fun recordTelemetry(track: MediaItemEntity) {
        val prev = _telemetry.value
        val artists = prev.artistCounts.toMutableMap()
        if (track.artist.isNotBlank()) {
            artists[track.artist] = (artists[track.artist] ?: 0) + 1
        }
        val codecs = prev.codecCounts.toMutableMap()
        val fmt = track.suffix?.uppercase()?.takeIf { it.isNotBlank() } ?: "STREAM"
        codecs[fmt] = (codecs[fmt] ?: 0) + 1

        val updated = prev.copy(
            totalTracksPlayed = prev.totalTracksPlayed + 1,
            totalListeningMs = prev.totalListeningMs + track.durationMs.coerceAtLeast(0L),
            artistCounts = artists,
            codecCounts = codecs
        )
        _telemetry.value = updated
        scope.launch(Dispatchers.IO) {
            runCatching { telemetryFile().writeText(gson.toJson(updated)) }
        }
    }

    private fun dispatchScrobble(track: MediaItemEntity, submission: Boolean) {
        if (track.serverId.startsWith("local_")) return
        scope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            try {
                repository.scrobble(track.id, now, submission)
            } catch (_: Exception) {
                database.enqueueSyncAction(
                    serverId = track.serverId,
                    actionType = "scrobble",
                    targetId = track.id,
                    payloadJson = gson.toJson(mapOf("timestampMs" to now, "submission" to submission))
                )
            }
        }
    }

    private fun saveBookmarkIfNeeded() {
        val track = _queue.value.getOrNull(_currentIndex.value) ?: return
        if (track.contentType == MediaContentType.AUDIOBOOK || track.contentType == MediaContentType.PODCAST) {
            saveBookmarkForTrack(track, player.currentPosition.coerceAtLeast(0L))
        }
    }

    private fun saveBookmarkForTrack(track: MediaItemEntity, positionMs: Long) {
        if (track.serverId.startsWith("local_")) return
        scope.launch(Dispatchers.IO) {
            try {
                repository.saveBookmark(track.id, positionMs)
            } catch (_: Exception) {
                database.enqueueSyncAction(
                    serverId = track.serverId,
                    actionType = "bookmark",
                    targetId = track.id,
                    payloadJson = gson.toJson(mapOf("positionMs" to positionMs))
                )
            }
        }
    }

    private fun sessionFile(): File = File(context.filesDir, "playback_session.json")

    private fun scheduleSessionSave() {
        sessionSaveJob?.cancel()
        sessionSaveJob = scope.launch(Dispatchers.IO) {
            delay(350L)
            val snap = PlaybackSessionSnapshot(
                queue = _queue.value,
                currentIndex = _currentIndex.value,
                positionMs = _positionMs.value,
                repeatMode = _repeatMode.value,
                shuffleEnabled = _isShuffleEnabled.value
            )
            runCatching { sessionFile().writeText(gson.toJson(snap)) }
        }
    }

    suspend fun restorePlaybackSession() = withContext(Dispatchers.IO) {
        val file = sessionFile()
        if (!file.exists()) return@withContext
        val snap = runCatching {
            gson.fromJson(file.readText(), PlaybackSessionSnapshot::class.java)
        }.getOrNull() ?: return@withContext
        if (snap.queue.isEmpty()) return@withContext

        withContext(Dispatchers.Main.immediate) {
            hasRestoredSession = true
            setRepeatMode(snap.repeatMode)
            _isShuffleEnabled.value = snap.shuffleEnabled
            player.shuffleModeEnabled = snap.shuffleEnabled
            loadQueue(
                items = snap.queue,
                initialIndex = snap.currentIndex,
                initialPositionMs = snap.positionMs,
                autoPlay = false
            )
        }
    }

    private fun updateWidgetFromCurrent() {
        val track = _queue.value.getOrNull(_currentIndex.value)
        SonicWaveWidgetProvider.updateWidget(
            context = context,
            title = track?.title ?: "SonicWave",
            artist = track?.artist ?: "",
            isPlaying = _isPlaying.value
        )
    }

    fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}
