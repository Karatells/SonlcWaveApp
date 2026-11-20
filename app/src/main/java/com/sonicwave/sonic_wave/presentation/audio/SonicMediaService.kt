package com.sonicwave.sonic_wave.presentation.audio

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.sonicwave.sonic_wave.MainActivity
import com.sonicwave.sonic_wave.SonicWaveApplication
import com.sonicwave.sonic_wave.domain.models.MediaItemEntity
import com.sonicwave.sonic_wave.domain.repository.AlbumListType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future

@OptIn(UnstableApi::class)
class SonicMediaService : MediaLibraryService() {

    private var mediaLibrarySession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val autoTrackPool = mutableMapOf<String, MediaItemEntity>()

    override fun onCreate() {
        super.onCreate()
        val container = (application as SonicWaveApplication).container
        val controller = container.playbackController

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaLibrarySession = MediaLibrarySession.Builder(this, controller.player, LibraryCallback())
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onDestroy() {
        mediaLibrarySession?.run {
            release()
            mediaLibrarySession = null
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val root = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("SonicWave Studio")
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .build()
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val container = (application as SonicWaveApplication).container
            val repo = container.repository
            val db = container.database

            return serviceScope.future {
                val items: List<MediaItem> = runCatching {
                    when {
                        parentId == ROOT_ID -> listOf(
                            browsableFolder("auto_recent", "Recent Albums"),
                            browsableFolder("auto_quick_mix", "Quick Mix"),
                            browsableFolder("auto_audiobooks", "Audiobooks"),
                            browsableFolder("auto_offline", "Offline Vault")
                        )
                        parentId == "auto_recent" -> {
                            repo.getAlbumList(AlbumListType.NEWEST, size = 15).map { alb ->
                                browsableFolder("album:${alb.id}", alb.name, alb.artist)
                            }
                        }
                        parentId.startsWith("album:") -> {
                            val albumId = parentId.removePrefix("album:")
                            val details = repo.getAlbumDetails(albumId)
                            details.tracks.map { t ->
                                autoTrackPool[t.id] = t
                                playableItem(t, repo.buildCoverArtUri(t.coverArtId ?: "", 400))
                            }
                        }
                        parentId == "auto_quick_mix" -> {
                            repo.getQuickMix(count = 25).map { t ->
                                autoTrackPool[t.id] = t
                                playableItem(t, repo.buildCoverArtUri(t.coverArtId ?: "", 400))
                            }
                        }
                        parentId == "auto_audiobooks" -> {
                            repo.getAudiobooks().map { t ->
                                autoTrackPool[t.id] = t
                                playableItem(t, repo.buildCoverArtUri(t.coverArtId ?: "", 400))
                            }
                        }
                        parentId == "auto_offline" -> {
                            db.getAllCachedTracks().map { row ->
                                val t = db.mapCachedRowToEntity(row)
                                autoTrackPool[t.id] = t
                                playableItem(t, null)
                            }
                        }
                        else -> emptyList()
                    }
                }.getOrDefault(emptyList())

                LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
            }
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val container = (application as SonicWaveApplication).container
            val pb = container.playbackController
            val repo = container.repository

            return serviceScope.future {
                val first = mediaItems.firstOrNull()
                val rawId = first?.mediaId.orEmpty()
                when {
                    rawId.startsWith("album:") -> {
                        val albumId = rawId.removePrefix("album:")
                        val details = repo.getAlbumDetails(albumId)
                        if (details.tracks.isNotEmpty()) {
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                pb.loadQueue(details.tracks)
                            }
                        }
                    }
                    rawId.startsWith("track:") -> {
                        val trackId = rawId.removePrefix("track:")
                        val idx = pb.queue.value.indexOfFirst { it.id == trackId }
                        if (idx >= 0) {
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                pb.skipToIndex(idx)
                            }
                        } else {
                            autoTrackPool[trackId]?.let { t ->
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    pb.loadQueue(listOf(t))
                                }
                            }
                        }
                    }
                }
                mediaItems
            }
        }
    }

    private fun browsableFolder(id: String, title: String, subtitle: String? = null): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()
    }

    private fun playableItem(item: MediaItemEntity, artUrl: String?): MediaItem {
        return MediaItem.Builder()
            .setMediaId("track:${item.id}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(item.title)
                    .setArtist(item.artist)
                    .setAlbumTitle(item.album)
                    .setArtworkUri(artUrl?.let { runCatching { Uri.parse(it) }.getOrNull() })
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
            .build()
    }

    companion object {
        private const val ROOT_ID = "sonicwave_auto_root"
    }
}
