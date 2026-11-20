package com.sonicwave.sonic_wave.domain.repository

import com.sonicwave.sonic_wave.domain.models.AlbumEntity
import com.sonicwave.sonic_wave.domain.models.ArtistEntity
import com.sonicwave.sonic_wave.domain.models.GenreEntity
import com.sonicwave.sonic_wave.domain.models.MediaContentType
import com.sonicwave.sonic_wave.domain.models.MediaItemEntity
import com.sonicwave.sonic_wave.domain.models.MusicFolderNodeEntity
import com.sonicwave.sonic_wave.domain.models.PlaylistEntity
import com.sonicwave.sonic_wave.domain.models.ServerProfile
import com.sonicwave.sonic_wave.domain.models.SynchronizedLyricsEntity
import com.sonicwave.sonic_wave.domain.models.TranscodingProfile

enum class AlbumListType(val subsonicType: String) {
    NEWEST("newest"),
    FREQUENT("frequent"),
    RECENT("recent"),
    ALPHABETICAL_BY_NAME("alphabeticalByName"),
    STARRED("starred"),
    RANDOM("random"),
    BY_GENRE("byGenre")
}

data class SearchFilter(
    val query: String,
    val genre: String? = null,
    val fromYear: Int? = null,
    val toYear: Int? = null,
    val contentType: MediaContentType? = null,
    val limit: Int = 30,
    val offset: Int = 0
)

data class SearchResultBundle(
    val tracks: List<MediaItemEntity> = emptyList(),
    val albums: List<AlbumEntity> = emptyList(),
    val artists: List<ArtistEntity> = emptyList()
)

interface MediaServerRepository {
    val currentProfile: ServerProfile
    fun updateServerProfile(newProfile: ServerProfile)
    fun resetLanFailover()

    suspend fun pingServer(): Boolean
    suspend fun invalidateServerCache()

    fun buildStreamUri(
        trackId: String,
        profile: TranscodingProfile,
        isWifi: Boolean,
        timeOffsetMs: Long? = null
    ): String

    fun buildCoverArtUri(coverArtId: String, size: Int = 600): String

    suspend fun getAlbumList(
        type: AlbumListType,
        size: Int = 20,
        offset: Int = 0,
        genre: String? = null,
        forceRefresh: Boolean = false
    ): List<AlbumEntity>

    suspend fun getAlbumDetails(albumId: String, forceRefresh: Boolean = false): AlbumEntity
    suspend fun getArtists(forceRefresh: Boolean = false): List<ArtistEntity>
    suspend fun getArtistAlbums(artistId: String, forceRefresh: Boolean = false): List<AlbumEntity>
    suspend fun getAllTracks(count: Int = 150, offset: Int = 0, forceRefresh: Boolean = false): List<MediaItemEntity>
    suspend fun getGenres(forceRefresh: Boolean = false): List<GenreEntity>
    suspend fun getSongsByGenre(genre: String, count: Int = 100, forceRefresh: Boolean = false): List<MediaItemEntity>
    suspend fun getPlaylists(forceRefresh: Boolean = false): List<PlaylistEntity>
    suspend fun getPlaylistDetails(playlistId: String, forceRefresh: Boolean = false): PlaylistEntity
    suspend fun getMusicRootEntries(forceRefresh: Boolean = false): List<MusicFolderNodeEntity>
    suspend fun getMusicDirectory(directoryId: String, forceRefresh: Boolean = false): List<MusicFolderNodeEntity>
    suspend fun getQuickMix(
        seedArtistId: String? = null,
        genre: String? = null,
        count: Int = 50,
        forceRefresh: Boolean = false
    ): List<MediaItemEntity>

    suspend fun getPodcastEpisodes(channelId: String? = null, forceRefresh: Boolean = false): List<MediaItemEntity>
    suspend fun getAudiobooks(forceRefresh: Boolean = false): List<MediaItemEntity>
    suspend fun getLyrics(trackId: String, artist: String? = null, title: String? = null): SynchronizedLyricsEntity
    suspend fun search(filter: SearchFilter): SearchResultBundle

    suspend fun scrobble(trackId: String, timestampMs: Long, submission: Boolean)
    suspend fun setFavorite(id: String, isFavorite: Boolean)
    suspend fun setRating(id: String, rating: Int)
    suspend fun saveBookmark(trackId: String, positionMs: Long, comment: String? = null)
}
