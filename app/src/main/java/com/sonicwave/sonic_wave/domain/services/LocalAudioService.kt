package com.sonicwave.sonic_wave.domain.services

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.sonicwave.sonic_wave.core.utils.generateDeterministicWaveform
import com.sonicwave.sonic_wave.domain.models.MediaContentType
import com.sonicwave.sonic_wave.domain.models.MediaItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

data class LocalAudioFolder(
    val id: String,
    val name: String,
    val uriOrPath: String,
    val trackCount: Int = 0,
    val lastScannedAtMs: Long = System.currentTimeMillis()
)

data class LocalLibraryDiskPayload(
    val folders: List<LocalAudioFolder> = emptyList(),
    val tracks: List<MediaItemEntity> = emptyList()
)

class LocalAudioService(private val context: Context) {

    private val gson = Gson()
    private val supportedExtensions = setOf(
        "mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "wma", "alac", "aiff", "m4b"
    )

    private val _folders = MutableStateFlow<List<LocalAudioFolder>>(emptyList())
    val folders: StateFlow<List<LocalAudioFolder>> = _folders.asStateFlow()

    private val _tracks = MutableStateFlow<List<MediaItemEntity>>(emptyList())
    val tracks: StateFlow<List<MediaItemEntity>> = _tracks.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgressText = MutableStateFlow<String?>(null)
    val scanProgressText: StateFlow<String?> = _scanProgressText.asStateFlow()

    private fun libraryFile(): File = File(context.filesDir, "local_audio_library.json")

    suspend fun loadFromDisk() = withContext(Dispatchers.IO) {
        val file = libraryFile()
        if (!file.exists()) return@withContext
        runCatching {
            val payload = gson.fromJson(file.readText(), LocalLibraryDiskPayload::class.java)
            if (payload != null) {
                _folders.value = payload.folders
                _tracks.value = payload.tracks
            }
        }
    }

    private suspend fun saveToDisk() = withContext(Dispatchers.IO) {
        runCatching {
            val payload = LocalLibraryDiskPayload(
                folders = _folders.value,
                tracks = _tracks.value
            )
            libraryFile().writeText(gson.toJson(payload))
        }
    }

    suspend fun addFolderByUri(treeUri: Uri): LocalAudioFolder? = withContext(Dispatchers.IO) {
        val doc = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext null
        val name = doc.name ?: treeUri.lastPathSegment ?: "Local Music"
        val folder = LocalAudioFolder(
            id = "local_${treeUri.toString().hashCode().toUInt()}",
            name = name,
            uriOrPath = treeUri.toString()
        )
        addOrUpdateFolder(folder)
        rescanFolder(folder.id)
        _folders.value.firstOrNull { it.id == folder.id } ?: folder
    }

    suspend fun addFolderByPath(path: String): LocalAudioFolder = withContext(Dispatchers.IO) {
        val trimmed = path.trim()
        val file = File(trimmed)
        val name = file.name.ifEmpty { trimmed }
        val folder = LocalAudioFolder(
            id = "local_${trimmed.hashCode().toUInt()}",
            name = name,
            uriOrPath = trimmed
        )
        addOrUpdateFolder(folder)
        rescanFolder(folder.id)
        _folders.value.firstOrNull { it.id == folder.id } ?: folder
    }

    private fun addOrUpdateFolder(folder: LocalAudioFolder) {
        val existing = _folders.value.toMutableList()
        val idx = existing.indexOfFirst { it.id == folder.id || it.uriOrPath == folder.uriOrPath }
        if (idx >= 0) existing[idx] = folder else existing.add(folder)
        _folders.value = existing
    }

    suspend fun rescanFolder(folderId: String) = withContext(Dispatchers.IO) {
        val folder = _folders.value.firstOrNull { it.id == folderId } ?: return@withContext
        _isScanning.value = true
        try {
            val scanned = if (folder.uriOrPath.startsWith("content://")) {
                scanContentTree(Uri.parse(folder.uriOrPath), folder)
            } else {
                scanFileDirectory(File(folder.uriOrPath), folder)
            }
            val serverKey = "local_${folder.id}"
            val otherTracks = _tracks.value.filter { it.serverId != serverKey }
            _tracks.value = otherTracks + scanned
            _folders.value = _folders.value.map {
                if (it.id == folderId) it.copy(trackCount = scanned.size, lastScannedAtMs = System.currentTimeMillis()) else it
            }
            saveToDisk()
        } finally {
            _isScanning.value = false
            _scanProgressText.value = null
        }
    }

    suspend fun rescanAll() {
        for (folder in _folders.value) {
            rescanFolder(folder.id)
        }
    }

    suspend fun removeFolder(folderId: String) = withContext(Dispatchers.IO) {
        val serverKey = "local_$folderId"
        _folders.value = _folders.value.filterNot { it.id == folderId }
        _tracks.value = _tracks.value.filterNot { it.serverId == serverKey }
        saveToDisk()
    }

    suspend fun restoreFoldersFromImport(imported: List<LocalAudioFolder>) = withContext(Dispatchers.IO) {
        for (folder in imported) {
            addOrUpdateFolder(folder)
        }
        saveToDisk()
        rescanAll()
    }

    private fun scanContentTree(treeUri: Uri, folder: LocalAudioFolder): List<MediaItemEntity> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val results = mutableListOf<MediaItemEntity>()
        fun walk(dir: DocumentFile) {
            for (child in dir.listFiles()) {
                if (child.isDirectory) {
                    walk(child)
                } else if (child.isFile) {
                    val name = child.name ?: continue
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext in supportedExtensions) {
                        _scanProgressText.value = name
                        results.add(buildLocalMediaItem(child.uri.toString(), name, ext, child.length(), folder))
                    }
                }
            }
        }
        walk(root)
        return results
    }

    private fun scanFileDirectory(dir: File, folder: LocalAudioFolder): List<MediaItemEntity> {
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val results = mutableListOf<MediaItemEntity>()
        dir.walkTopDown().forEach { file ->
            if (file.isFile) {
                val ext = file.extension.lowercase()
                if (ext in supportedExtensions) {
                    _scanProgressText.value = file.name
                    results.add(buildLocalMediaItem(file.absolutePath, file.name, ext, file.length(), folder))
                }
            }
        }
        return results
    }

    private fun buildLocalMediaItem(
        uriOrPath: String,
        fileName: String,
        ext: String,
        fileSize: Long,
        folder: LocalAudioFolder
    ): MediaItemEntity {
        var title = fileName.substringBeforeLast('.')
        var artist = "Local Artist"
        var album = folder.name
        var durationMs = 0L

        val retriever = MediaMetadataRetriever()
        runCatching {
            if (uriOrPath.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(uriOrPath))
            } else {
                retriever.setDataSource(uriOrPath)
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }?.let { title = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() }?.let { artist = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() }?.let { album = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.let { durationMs = it }
        }
        runCatching { retriever.release() }

        val id = "loc_${uriOrPath.hashCode().toUInt()}"
        return MediaItemEntity(
            id = id,
            serverId = "local_${folder.id}",
            title = title,
            artist = artist,
            artistId = "local_artist_${artist.hashCode()}",
            album = album,
            albumId = "local_album_${album.hashCode()}",
            durationMs = durationMs,
            suffix = ext.uppercase(),
            fileSize = fileSize,
            contentType = if (ext == "m4b") MediaContentType.AUDIOBOOK else MediaContentType.MUSIC,
            waveformPeaks = generateDeterministicWaveform(id),
            localFilePath = uriOrPath
        )
    }
}
