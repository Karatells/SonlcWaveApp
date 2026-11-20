package com.sonicwave.sonic_wave.presentation.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonicwave.sonic_wave.core.network.HttpClientFactory
import com.sonicwave.sonic_wave.core.security.SecureCredentialsStore
import com.sonicwave.sonic_wave.data.local.AppDatabase
import com.sonicwave.sonic_wave.data.local.CachedTrackRow
import com.sonicwave.sonic_wave.data.local.DownloadTaskRow
import com.sonicwave.sonic_wave.domain.models.MediaItemEntity
import com.sonicwave.sonic_wave.domain.repository.MediaServerRepository
import com.sonicwave.sonic_wave.domain.services.AppSettingsService
import com.sonicwave.sonic_wave.presentation.audio.SonicPlaybackController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

enum class DownloadItemStatus {
    QUEUED, DOWNLOADING, COMPLETED, FAILED, CANCELLED
}

data class DownloadJobItem(
    val track: MediaItemEntity,
    val groupTitle: String? = null,
    val status: DownloadItemStatus = DownloadItemStatus.QUEUED,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L
)

data class DownloadUiState(
    val jobsByTrackId: Map<String, DownloadJobItem> = emptyMap(),
    val pinnedTracks: List<CachedTrackRow> = emptyList(),
    val autoCachedTracks: List<CachedTrackRow> = emptyList(),
    val offlineTrackIds: Set<String> = emptySet(),
    val pinnedCacheSizeBytes: Long = 0L,
    val autoCacheSizeBytes: Long = 0L,
    val maxAutoCacheBytes: Long = 2L * 1024L * 1024L * 1024L,
    val isWaitingForWifi: Boolean = false
) {
    val activeJobsCount: Int
        get() = jobsByTrackId.values.count {
            it.status == DownloadItemStatus.QUEUED || it.status == DownloadItemStatus.DOWNLOADING
        }

    val failedJobsCount: Int
        get() = jobsByTrackId.values.count { it.status == DownloadItemStatus.FAILED }

    val overallProgress: Float
        get() {
            val active = jobsByTrackId.values.filter {
                it.status == DownloadItemStatus.QUEUED || it.status == DownloadItemStatus.DOWNLOADING
            }
            if (active.isEmpty()) return 0f
            return active.map { it.progress }.average().toFloat()
        }

    fun isTrackOffline(trackId: String): Boolean = trackId in offlineTrackIds
}

class DownloadViewModel(
    private val context: Context,
    private val database: AppDatabase,
    private val repository: MediaServerRepository,
    private val settingsService: AppSettingsService,
    private val playbackController: SonicPlaybackController
) : ViewModel() {

    private val _state = MutableStateFlow(DownloadUiState())
    val state: StateFlow<DownloadUiState> = _state.asStateFlow()

    private val credentialsStore = SecureCredentialsStore.getInstance(context)
    private val downloadClient = HttpClientFactory.createStreamingClient(
        profileProvider = { repository.currentProfile },
        credentialsStore = credentialsStore
    )

    private var workerJob: Job? = null

    init {
        viewModelScope.launch {
            cleanupStalePartFiles()
            refreshStorage()
        }
        viewModelScope.launch {
            playbackController.cacheChangedTick.collect {
                refreshStorage()
            }
        }
        registerNetworkWatcher()
    }

    private fun registerNetworkWatcher() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (_state.value.isWaitingForWifi && playbackController.isWifiConnected()) {
                    _state.value = _state.value.copy(isWaitingForWifi = false)
                    startWorkerIfNeeded()
                }
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(callback) }
    }

    private suspend fun cleanupStalePartFiles() = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "offline_media")
        dir.listFiles()?.filter { it.name.endsWith(".part") }?.forEach {
            runCatching { it.delete() }
        }
    }

    fun refreshStorage() {
        viewModelScope.launch {
            val pinned = database.getAllCachedTracks(isPinned = true).filter { File(it.localFilePath).exists() }
            val auto = database.getAllCachedTracks(isPinned = false).filter { File(it.localFilePath).exists() }
            val pinnedBytes = pinned.sumOf { it.fileSizeBytes }
            val autoBytes = auto.sumOf { it.fileSizeBytes }
            val maxBytes = (settingsService.settings.value.maxCacheGb * 1024.0 * 1024.0 * 1024.0).toLong()
            val offlineIds = (pinned + auto).map { it.trackId }.toSet()

            _state.value = _state.value.copy(
                pinnedTracks = pinned,
                autoCachedTracks = auto,
                offlineTrackIds = offlineIds,
                pinnedCacheSizeBytes = pinnedBytes,
                autoCacheSizeBytes = autoBytes,
                maxAutoCacheBytes = maxBytes
            )
        }
    }

    fun enqueueDownloads(items: List<MediaItemEntity>, groupTitle: String? = null) {
        val updated = _state.value.jobsByTrackId.toMutableMap()
        for (item in items) {
            if (item.serverId.startsWith("local_")) continue
            val existing = updated[item.id]
            if (existing != null && (existing.status == DownloadItemStatus.QUEUED || existing.status == DownloadItemStatus.DOWNLOADING)) {
                continue
            }
            updated[item.id] = DownloadJobItem(
                track = item,
                groupTitle = groupTitle,
                status = DownloadItemStatus.QUEUED,
                progress = 0f
            )
        }
        _state.value = _state.value.copy(jobsByTrackId = updated)
        startWorkerIfNeeded()
    }

    fun cancelTask(trackId: String) {
        val updated = _state.value.jobsByTrackId.toMutableMap()
        updated.remove(trackId)
        _state.value = _state.value.copy(jobsByTrackId = updated)
        viewModelScope.launch {
            database.deleteDownloadTask(trackId)
        }
    }

    fun deleteTrack(trackId: String, serverId: String) {
        viewModelScope.launch {
            database.deleteCachedTrack(trackId, serverId)
            refreshStorage()
        }
    }

    fun togglePin(trackId: String, serverId: String, isPinned: Boolean) {
        viewModelScope.launch {
            database.setTrackPinnedStatus(trackId, serverId, isPinned)
            refreshStorage()
        }
    }

    fun evictAutoCache(maxBytes: Long) {
        viewModelScope.launch {
            database.evictAutoCacheUsingLru(maxBytes)
            refreshStorage()
        }
    }

    fun clearAutoCache() {
        viewModelScope.launch {
            database.clearAllAutoCache()
            refreshStorage()
        }
    }

    fun clearAllPinned() {
        viewModelScope.launch {
            database.clearAllPinnedDownloads()
            refreshStorage()
        }
    }

    fun retryFailed() {
        val updated = _state.value.jobsByTrackId.mapValues { (_, job) ->
            if (job.status == DownloadItemStatus.FAILED) {
                job.copy(status = DownloadItemStatus.QUEUED, progress = 0f)
            } else job
        }
        _state.value = _state.value.copy(jobsByTrackId = updated)
        startWorkerIfNeeded()
    }

    private fun startWorkerIfNeeded() {
        if (workerJob?.isActive == true) return
        workerJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val nextJob = _state.value.jobsByTrackId.values.firstOrNull {
                    it.status == DownloadItemStatus.QUEUED
                } ?: break

                val wifiOnly = settingsService.settings.value.wifiOnlyDownloads
                if (wifiOnly && !playbackController.isWifiConnected()) {
                    _state.value = _state.value.copy(isWaitingForWifi = true)
                    break
                }
                _state.value = _state.value.copy(isWaitingForWifi = false)

                val item = nextJob.track
                val existingCached = database.getCachedTrack(item.id, item.serverId)
                if (existingCached != null && File(existingCached.localFilePath).exists()) {
                    database.setTrackPinnedStatus(item.id, item.serverId, true)
                    updateJob(item.id) {
                        it.copy(status = DownloadItemStatus.COMPLETED, progress = 1f)
                    }
                    playbackController.onTrackCachedExternally(item, existingCached.localFilePath)
                    refreshStorage()
                    continue
                }

                updateJob(item.id) { it.copy(status = DownloadItemStatus.DOWNLOADING, progress = 0.02f) }

                val dir = File(context.filesDir, "offline_media").apply { mkdirs() }
                val safeKey = "${item.serverId}_${item.id}".replace(Regex("[^a-zA-Z0-9_-]"), "_")
                val ext = item.suffix?.lowercase()?.takeIf { it.isNotBlank() } ?: "audio"
                val targetFile = File(dir, "$safeKey.$ext")
                val partFile = File(dir, "$safeKey.$ext.part")

                val success = runCatching {
                    val transcode = settingsService.settings.value.toTranscodingProfile()
                    val url = repository.buildStreamUri(item.id, transcode, playbackController.isWifiConnected())
                    val req = Request.Builder().url(url).get().build()
                    downloadClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                        val body = resp.body ?: throw IllegalStateException("Empty body")
                        val total = body.contentLength()
                        var downloaded = 0L
                        var lastEmit = 0L
                        val buf = ByteArray(16 * 1024)
                        body.byteStream().use { input ->
                            partFile.outputStream().use { out ->
                                while (true) {
                                    val read = input.read(buf)
                                    if (read == -1) break
                                    out.write(buf, 0, read)
                                    downloaded += read
                                    val now = System.currentTimeMillis()
                                    if (now - lastEmit >= 220L) {
                                        lastEmit = now
                                        val prog = if (total > 0) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 0.99f) else 0.5f
                                        updateJob(item.id) {
                                            it.copy(progress = prog, downloadedBytes = downloaded, totalBytes = total)
                                        }
                                    }
                                }
                            }
                        }
                        if (!partFile.renameTo(targetFile)) {
                            partFile.copyTo(targetFile, overwrite = true)
                            partFile.delete()
                        }
                        database.upsertCachedTrack(
                            item = item,
                            localFilePath = targetFile.absolutePath,
                            fileSizeBytes = targetFile.length(),
                            isPinnedDownload = true
                        )
                        database.deleteDownloadTask(item.id)
                    }
                    true
                }.getOrElse { false }

                if (success) {
                    updateJob(item.id) { it.copy(status = DownloadItemStatus.COMPLETED, progress = 1f) }
                    playbackController.onTrackCachedExternally(item, targetFile.absolutePath)
                    refreshStorage()
                } else {
                    runCatching { partFile.delete() }
                    updateJob(item.id) { it.copy(status = DownloadItemStatus.FAILED) }
                }
            }
        }
    }

    private fun updateJob(trackId: String, transform: (DownloadJobItem) -> DownloadJobItem) {
        val current = _state.value.jobsByTrackId.toMutableMap()
        val job = current[trackId] ?: return
        current[trackId] = transform(job)
        _state.value = _state.value.copy(jobsByTrackId = current)
    }
}
