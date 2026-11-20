package com.sonicwave.sonic_wave.data.local

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sonicwave.sonic_wave.core.security.SecureCredentialsStore
import com.sonicwave.sonic_wave.core.utils.generateDeterministicWaveform
import com.sonicwave.sonic_wave.domain.models.MediaContentType
import com.sonicwave.sonic_wave.domain.models.MediaItemEntity
import com.sonicwave.sonic_wave.domain.models.ReplayGainInfo
import com.sonicwave.sonic_wave.domain.models.ServerProfile
import com.sonicwave.sonic_wave.domain.models.ServerProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Entity(tableName = "server_profiles_table")
data class ServerProfileRow(
    @PrimaryKey val id: String,
    val name: String,
    val protocol: String,
    @ColumnInfo(name = "primary_url") val primaryUrl: String,
    @ColumnInfo(name = "lan_url") val lanUrl: String? = null,
    val username: String,
    @ColumnInfo(name = "secret_or_password") val secretOrPassword: String,
    @ColumnInfo(name = "allow_self_signed_ssl") val allowSelfSignedSsl: Boolean = false,
    @ColumnInfo(name = "is_active") val isActive: Boolean = false
)

@Entity(
    tableName = "cached_tracks_table",
    primaryKeys = ["track_id", "server_id"],
    indices = [Index(name = "idx_cached_pinned_lru", value = ["is_pinned_download", "last_accessed_at"])]
)
data class CachedTrackRow(
    @ColumnInfo(name = "track_id") val trackId: String,
    @ColumnInfo(name = "server_id") val serverId: String,
    val title: String,
    val artist: String,
    @ColumnInfo(name = "artist_id") val artistId: String,
    val album: String,
    @ColumnInfo(name = "album_id") val albumId: String,
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Int,
    @ColumnInfo(name = "cover_art_id") val coverArtId: String? = null,
    @ColumnInfo(name = "local_file_path") val localFilePath: String,
    @ColumnInfo(name = "local_cover_path") val localCoverPath: String? = null,
    @ColumnInfo(name = "file_size_bytes") val fileSizeBytes: Long,
    @ColumnInfo(name = "is_pinned_download") val isPinnedDownload: Boolean = false,
    @ColumnInfo(name = "is_starred") val isStarred: Boolean = false,
    @ColumnInfo(name = "user_rating") val userRating: Int = 0,
    @ColumnInfo(name = "content_type") val contentType: String = "MUSIC",
    @ColumnInfo(name = "bookmark_position_ms") val bookmarkPositionMs: Long = 0L,
    @ColumnInfo(name = "track_gain_db") val trackGainDb: Double? = null,
    @ColumnInfo(name = "album_gain_db") val albumGainDb: Double? = null,
    @ColumnInfo(name = "track_peak") val trackPeak: Double? = null,
    @ColumnInfo(name = "waveform_json") val waveformJson: String = "[]",
    @ColumnInfo(name = "last_accessed_at") val lastAccessedAt: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Entity(tableName = "download_tasks_table")
data class DownloadTaskRow(
    @PrimaryKey @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "track_id") val trackId: String,
    @ColumnInfo(name = "server_id") val serverId: String,
    @ColumnInfo(name = "group_title") val groupTitle: String? = null,
    @ColumnInfo(name = "track_title") val trackTitle: String,
    val artist: String,
    @ColumnInfo(name = "cover_art_id") val coverArtId: String? = null,
    @ColumnInfo(name = "target_file_path") val targetFilePath: String,
    val status: String,
    val progress: Double = 0.0,
    @ColumnInfo(name = "downloaded_bytes") val downloadedBytes: Long = 0L,
    @ColumnInfo(name = "total_bytes") val totalBytes: Long = 0L,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "pending_sync_actions_table",
    indices = [Index(name = "idx_sync_server_created", value = ["server_id", "created_at"])]
)
data class PendingSyncActionRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "server_id") val serverId: String,
    @ColumnInfo(name = "action_type") val actionType: String,
    @ColumnInfo(name = "target_id") val targetId: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "retry_count") val retryCount: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Dao
interface ServerProfileDao {
    @Query("SELECT * FROM server_profiles_table")
    suspend fun getAll(): List<ServerProfileRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: ServerProfileRow)

    @Query("UPDATE server_profiles_table SET is_active = 0")
    suspend fun deactivateAll()

    @Query("UPDATE server_profiles_table SET is_active = 1 WHERE id = :profileId")
    suspend fun activate(profileId: String)

    @Transaction
    suspend fun setActive(profileId: String) {
        deactivateAll()
        activate(profileId)
    }

    @Query("DELETE FROM server_profiles_table WHERE id = :profileId")
    suspend fun deleteById(profileId: String)
}

@Dao
interface CachedTrackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: CachedTrackRow)

    @Query("SELECT * FROM cached_tracks_table WHERE track_id = :trackId AND server_id = :serverId LIMIT 1")
    suspend fun getTrack(trackId: String, serverId: String): CachedTrackRow?

    @Query("SELECT * FROM cached_tracks_table WHERE server_id = :serverId")
    suspend fun getTracksForServer(serverId: String): List<CachedTrackRow>

    @Query("UPDATE cached_tracks_table SET last_accessed_at = :timestamp WHERE track_id = :trackId AND server_id = :serverId")
    suspend fun touch(trackId: String, serverId: String, timestamp: Long)

    @Query("SELECT COALESCE(SUM(file_size_bytes), 0) FROM cached_tracks_table WHERE is_pinned_download = 0")
    suspend fun getAutoCacheSizeBytes(): Long

    @Query("SELECT COALESCE(SUM(file_size_bytes), 0) FROM cached_tracks_table WHERE is_pinned_download = 1")
    suspend fun getPinnedCacheSizeBytes(): Long

    @Query("SELECT * FROM cached_tracks_table ORDER BY last_accessed_at DESC")
    suspend fun getAll(): List<CachedTrackRow>

    @Query("SELECT * FROM cached_tracks_table WHERE is_pinned_download = :isPinned ORDER BY last_accessed_at DESC")
    suspend fun getByPinned(isPinned: Boolean): List<CachedTrackRow>

    @Query("SELECT * FROM cached_tracks_table WHERE is_pinned_download = 0 ORDER BY last_accessed_at ASC")
    suspend fun getAutoCacheLruOrder(): List<CachedTrackRow>

    @Query("UPDATE cached_tracks_table SET is_pinned_download = :isPinned WHERE track_id = :trackId AND server_id = :serverId")
    suspend fun setPinned(trackId: String, serverId: String, isPinned: Boolean)

    @Query("DELETE FROM cached_tracks_table WHERE track_id = :trackId AND server_id = :serverId")
    suspend fun deleteTrack(trackId: String, serverId: String)

    @Query("DELETE FROM cached_tracks_table WHERE server_id = :serverId")
    suspend fun deleteByServer(serverId: String)

    @Query("DELETE FROM cached_tracks_table WHERE is_pinned_download = 1")
    suspend fun deleteAllPinned()
}

@Dao
interface DownloadTaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: DownloadTaskRow)

    @Query("SELECT * FROM download_tasks_table WHERE status IN ('queued', 'downloading') ORDER BY updated_at ASC")
    suspend fun getPending(): List<DownloadTaskRow>

    @Query("DELETE FROM download_tasks_table WHERE task_id = :taskId")
    suspend fun deleteById(taskId: String)

    @Query("DELETE FROM download_tasks_table WHERE status = 'completed'")
    suspend fun clearCompleted()

    @Query("DELETE FROM download_tasks_table WHERE server_id = :serverId")
    suspend fun deleteByServer(serverId: String)
}

@Dao
interface PendingSyncActionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: PendingSyncActionRow)

    @Query("SELECT * FROM pending_sync_actions_table WHERE server_id = :serverId ORDER BY created_at ASC")
    suspend fun getForServer(serverId: String): List<PendingSyncActionRow>

    @Query("DELETE FROM pending_sync_actions_table WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE pending_sync_actions_table SET retry_count = retry_count + 1 WHERE id = :id")
    suspend fun incrementRetry(id: Long)

    @Query("DELETE FROM pending_sync_actions_table WHERE server_id = :serverId")
    suspend fun deleteByServer(serverId: String)
}

@Database(
    entities = [
        ServerProfileRow::class,
        CachedTrackRow::class,
        DownloadTaskRow::class,
        PendingSyncActionRow::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SonicWaveRoomDatabase : RoomDatabase() {
    abstract fun serverProfileDao(): ServerProfileDao
    abstract fun cachedTrackDao(): CachedTrackDao
    abstract fun downloadTaskDao(): DownloadTaskDao
    abstract fun pendingSyncActionDao(): PendingSyncActionDao
}

class AppDatabase(private val context: Context) {
    private val db: SonicWaveRoomDatabase = Room.databaseBuilder(
        context.applicationContext,
        SonicWaveRoomDatabase::class.java,
        "sonic_wave_storage.sqlite"
    ).build()

    private val credentialsStore = SecureCredentialsStore.getInstance(context)
    private val gson = Gson()
    private val floatListType = object : TypeToken<List<Float>>() {}.type

    suspend fun getAllServerProfiles(): List<ServerProfile> = withContext(Dispatchers.IO) {
        val rows = db.serverProfileDao().getAll()
        rows.map { row ->
            val secret = if (row.secretOrPassword == SecureCredentialsStore.SECURE_MARKER) {
                credentialsStore.readSecret(row.id) ?: ""
            } else {
                if (row.secretOrPassword.isNotBlank()) {
                    val saved = credentialsStore.saveSecret(row.id, row.secretOrPassword)
                    if (saved) {
                        db.serverProfileDao().upsert(row.copy(secretOrPassword = SecureCredentialsStore.SECURE_MARKER))
                    }
                }
                row.secretOrPassword
            }
            ServerProfile(
                id = row.id,
                name = row.name,
                protocol = ServerProtocol.fromName(row.protocol),
                primaryUrl = row.primaryUrl,
                lanUrl = row.lanUrl,
                username = row.username,
                secretOrPassword = secret,
                allowSelfSignedSsl = row.allowSelfSignedSsl,
                isActive = row.isActive
            )
        }
    }

    suspend fun upsertServerProfile(profile: ServerProfile) = withContext(Dispatchers.IO) {
        val savedSecure = if (profile.secretOrPassword.isNotBlank()) {
            credentialsStore.saveSecret(profile.id, profile.secretOrPassword)
        } else {
            credentialsStore.saveSecret(profile.id, "")
        }
        val storedSecret = if (savedSecure) SecureCredentialsStore.SECURE_MARKER else profile.secretOrPassword
        db.serverProfileDao().upsert(
            ServerProfileRow(
                id = profile.id,
                name = profile.name,
                protocol = profile.protocol.name,
                primaryUrl = profile.primaryUrl,
                lanUrl = profile.lanUrl,
                username = profile.username,
                secretOrPassword = storedSecret,
                allowSelfSignedSsl = profile.allowSelfSignedSsl,
                isActive = profile.isActive
            )
        )
    }

    suspend fun setActiveServerProfile(profileId: String) = withContext(Dispatchers.IO) {
        db.serverProfileDao().setActive(profileId)
    }

    suspend fun deleteServerProfile(profileId: String) = withContext(Dispatchers.IO) {
        credentialsStore.deleteSecret(profileId)
        val cached = db.cachedTrackDao().getTracksForServer(profileId)
        for (row in cached) {
            runCatching { File(row.localFilePath).delete() }
        }
        db.cachedTrackDao().deleteByServer(profileId)
        db.downloadTaskDao().deleteByServer(profileId)
        db.pendingSyncActionDao().deleteByServer(profileId)
        db.serverProfileDao().deleteById(profileId)
    }

    suspend fun upsertCachedTrack(
        item: MediaItemEntity,
        localFilePath: String,
        fileSizeBytes: Long,
        isPinnedDownload: Boolean,
        localCoverPath: String? = null
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existing = db.cachedTrackDao().getTrack(item.id, item.serverId)
        val waveformJson = gson.toJson(item.waveformPeaks)
        db.cachedTrackDao().upsert(
            CachedTrackRow(
                trackId = item.id,
                serverId = item.serverId,
                title = item.title,
                artist = item.artist,
                artistId = item.artistId,
                album = item.album,
                albumId = item.albumId,
                durationSeconds = (item.durationMs / 1000L).toInt(),
                coverArtId = item.coverArtId,
                localFilePath = localFilePath,
                localCoverPath = localCoverPath ?: existing?.localCoverPath,
                fileSizeBytes = fileSizeBytes,
                isPinnedDownload = isPinnedDownload || (existing?.isPinnedDownload == true),
                isStarred = item.isStarred,
                userRating = item.userRating,
                contentType = item.contentType.name,
                bookmarkPositionMs = item.bookmarkPositionMs ?: 0L,
                trackGainDb = item.replayGain.trackGainDb,
                albumGainDb = item.replayGain.albumGainDb,
                trackPeak = item.replayGain.trackPeak,
                waveformJson = waveformJson,
                lastAccessedAt = now,
                createdAt = existing?.createdAt ?: now
            )
        )
    }

    suspend fun touchCachedTrack(trackId: String, serverId: String) = withContext(Dispatchers.IO) {
        db.cachedTrackDao().touch(trackId, serverId, System.currentTimeMillis())
    }

    suspend fun getCachedTrack(trackId: String, serverId: String): CachedTrackRow? = withContext(Dispatchers.IO) {
        db.cachedTrackDao().getTrack(trackId, serverId)
    }

    suspend fun getAutoCacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        db.cachedTrackDao().getAutoCacheSizeBytes()
    }

    suspend fun getPinnedCacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        db.cachedTrackDao().getPinnedCacheSizeBytes()
    }

    suspend fun getAllCachedTracks(isPinned: Boolean? = null): List<CachedTrackRow> = withContext(Dispatchers.IO) {
        when (isPinned) {
            null -> db.cachedTrackDao().getAll()
            else -> db.cachedTrackDao().getByPinned(isPinned)
        }
    }

    suspend fun setTrackPinnedStatus(trackId: String, serverId: String, isPinned: Boolean) = withContext(Dispatchers.IO) {
        db.cachedTrackDao().setPinned(trackId, serverId, isPinned)
    }

    suspend fun deleteCachedTrack(trackId: String, serverId: String) = withContext(Dispatchers.IO) {
        val row = db.cachedTrackDao().getTrack(trackId, serverId)
        if (row != null) {
            runCatching { File(row.localFilePath).delete() }
        }
        db.cachedTrackDao().deleteTrack(trackId, serverId)
    }

    suspend fun clearAllPinnedDownloads() = withContext(Dispatchers.IO) {
        val pinned = db.cachedTrackDao().getByPinned(true)
        for (row in pinned) {
            runCatching { File(row.localFilePath).delete() }
        }
        db.cachedTrackDao().deleteAllPinned()
    }

    suspend fun clearAllAutoCache() = withContext(Dispatchers.IO) {
        evictAutoCacheUsingLru(0L)
        context.cacheDir.listFiles()?.filter { it.name.startsWith("stream_cache_") }?.forEach {
            runCatching { it.delete() }
        }
    }

    suspend fun evictAutoCacheUsingLru(maxCacheBytes: Long) = withContext(Dispatchers.IO) {
        var currentSize = db.cachedTrackDao().getAutoCacheSizeBytes()
        if (currentSize <= maxCacheBytes) return@withContext
        val lruRows = db.cachedTrackDao().getAutoCacheLruOrder()
        for (row in lruRows) {
            if (currentSize <= maxCacheBytes) break
            runCatching { File(row.localFilePath).delete() }
            db.cachedTrackDao().deleteTrack(row.trackId, row.serverId)
            currentSize -= row.fileSizeBytes
        }
    }

    fun mapCachedRowToEntity(row: CachedTrackRow): MediaItemEntity {
        val peaks: List<Float> = runCatching {
            val parsed: List<Float>? = gson.fromJson(row.waveformJson, floatListType)
            if (parsed.isNullOrEmpty()) generateDeterministicWaveform(row.trackId) else parsed
        }.getOrElse { generateDeterministicWaveform(row.trackId) }

        return MediaItemEntity(
            id = row.trackId,
            serverId = row.serverId,
            title = row.title,
            artist = row.artist,
            artistId = row.artistId,
            album = row.album,
            albumId = row.albumId,
            durationMs = row.durationSeconds * 1000L,
            coverArtId = row.coverArtId,
            isStarred = row.isStarred,
            userRating = row.userRating,
            contentType = MediaContentType.fromName(row.contentType),
            bookmarkPositionMs = row.bookmarkPositionMs.takeIf { it > 0L },
            replayGain = ReplayGainInfo(
                trackGainDb = row.trackGainDb,
                albumGainDb = row.albumGainDb,
                trackPeak = row.trackPeak
            ),
            waveformPeaks = peaks,
            localFilePath = row.localFilePath
        )
    }

    // Sync Queue
    suspend fun enqueueSyncAction(
        serverId: String,
        actionType: String,
        targetId: String,
        payloadJson: String
    ) = withContext(Dispatchers.IO) {
        db.pendingSyncActionDao().insert(
            PendingSyncActionRow(
                serverId = serverId,
                actionType = actionType,
                targetId = targetId,
                payloadJson = payloadJson,
                retryCount = 0,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getPendingSyncActions(serverId: String): List<PendingSyncActionRow> = withContext(Dispatchers.IO) {
        db.pendingSyncActionDao().getForServer(serverId)
    }

    suspend fun deleteSyncAction(id: Long) = withContext(Dispatchers.IO) {
        db.pendingSyncActionDao().deleteById(id)
    }

    suspend fun incrementSyncActionRetry(id: Long) = withContext(Dispatchers.IO) {
        db.pendingSyncActionDao().incrementRetry(id)
    }

    // Download Tasks
    suspend fun upsertDownloadTask(row: DownloadTaskRow) = withContext(Dispatchers.IO) {
        db.downloadTaskDao().upsert(row)
    }

    suspend fun getPendingDownloadTasks(): List<DownloadTaskRow> = withContext(Dispatchers.IO) {
        db.downloadTaskDao().getPending()
    }

    suspend fun deleteDownloadTask(taskId: String) = withContext(Dispatchers.IO) {
        db.downloadTaskDao().deleteById(taskId)
    }

    suspend fun clearCompletedDownloadTasks() = withContext(Dispatchers.IO) {
        db.downloadTaskDao().clearCompleted()
    }
}
