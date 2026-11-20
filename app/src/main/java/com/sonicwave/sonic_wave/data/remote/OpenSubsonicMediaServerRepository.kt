package com.sonicwave.sonic_wave.data.remote

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.sonicwave.sonic_wave.core.network.AuthAndFailoverInterceptor
import com.sonicwave.sonic_wave.core.network.HttpClientFactory
import com.sonicwave.sonic_wave.core.network.SubsonicAuthHelper
import com.sonicwave.sonic_wave.core.security.SecureCredentialsStore
import com.sonicwave.sonic_wave.core.utils.LrcParser
import com.sonicwave.sonic_wave.core.utils.generateDeterministicWaveform
import com.sonicwave.sonic_wave.domain.models.AlbumEntity
import com.sonicwave.sonic_wave.domain.models.ArtistEntity
import com.sonicwave.sonic_wave.domain.models.AudioCodec
import com.sonicwave.sonic_wave.domain.models.GenreEntity
import com.sonicwave.sonic_wave.domain.models.LyricLine
import com.sonicwave.sonic_wave.domain.models.MediaContentType
import com.sonicwave.sonic_wave.domain.models.MediaItemEntity
import com.sonicwave.sonic_wave.domain.models.MusicFolderNodeEntity
import com.sonicwave.sonic_wave.domain.models.PlaylistEntity
import com.sonicwave.sonic_wave.domain.models.ReplayGainInfo
import com.sonicwave.sonic_wave.domain.models.ServerProfile
import com.sonicwave.sonic_wave.domain.models.ServerProtocol
import com.sonicwave.sonic_wave.domain.models.SynchronizedLyricsEntity
import com.sonicwave.sonic_wave.domain.models.TranscodingProfile
import com.sonicwave.sonic_wave.domain.repository.AlbumListType
import com.sonicwave.sonic_wave.domain.repository.MediaServerRepository
import com.sonicwave.sonic_wave.domain.repository.SearchFilter
import com.sonicwave.sonic_wave.domain.repository.SearchResultBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class SubsonicApiException(val code: Int, message: String) : IOException("Subsonic Error [$code]: $message")

class OpenSubsonicMediaServerRepository(
    private val context: Context,
    initialProfile: ServerProfile
) : MediaServerRepository {

    @Volatile
    private var _profile: ServerProfile = initialProfile

    override val currentProfile: ServerProfile
        get() = _profile

    private val credentialsStore = SecureCredentialsStore.getInstance(context)
    private val authInterceptor = AuthAndFailoverInterceptor { _profile }
    private val apiClient: OkHttpClient = HttpClientFactory.createApiClient(
        profileProvider = { _profile },
        credentialsStore = credentialsStore,
        authInterceptor = authInterceptor
    )
    private val gson = Gson()
    private val memoryCache = ConcurrentHashMap<String, String>()

    private val isJellyfin: Boolean
        get() = _profile.protocol == ServerProtocol.JELLYFIN

    private val isCustomRest: Boolean
        get() = _profile.protocol == ServerProtocol.CUSTOM_REST

    override fun updateServerProfile(newProfile: ServerProfile) {
        val changed = newProfile.id != _profile.id || newProfile.primaryUrl != _profile.primaryUrl
        _profile = newProfile
        authInterceptor.resetLanFailover()
        if (changed) {
            memoryCache.clear()
        }
    }

    override fun resetLanFailover() {
        authInterceptor.resetLanFailover()
    }

    private fun cacheFile(): File {
        val safeId = _profile.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(context.filesDir, "library_cache_$safeId.json")
    }

    @Synchronized
    private fun loadCacheFromDisk(): MutableMap<String, String> {
        if (memoryCache.isNotEmpty()) return memoryCache
        val file = cacheFile()
        if (file.exists()) {
            runCatching {
                val json = gson.fromJson(file.readText(), JsonObject::class.java)
                json?.entrySet()?.forEach { (k, v) ->
                    if (v.isJsonPrimitive) memoryCache[k] = v.asString
                }
            }
        }
        return memoryCache
    }

    @Synchronized
    private fun saveCacheKey(key: String, jsonPayload: String) {
        memoryCache[key] = jsonPayload
        runCatching {
            val obj = JsonObject()
            memoryCache.forEach { (k, v) -> obj.addProperty(k, v) }
            cacheFile().writeText(gson.toJson(obj))
        }
    }

    override suspend fun invalidateServerCache() = withContext(Dispatchers.IO) {
        memoryCache.clear()
        runCatching { cacheFile().delete() }
        Unit
    }

    private suspend fun getCachedOrFetch(
        cacheKey: String,
        forceRefresh: Boolean,
        fetcher: suspend () -> String
    ): String {
        val cache = loadCacheFromDisk()
        if (!forceRefresh) {
            cache[cacheKey]?.let { return it }
        }
        return try {
            val fresh = fetcher()
            saveCacheKey(cacheKey, fresh)
            fresh
        } catch (e: Exception) {
            cache[cacheKey] ?: throw e
        }
    }

    private fun baseUrl(): String = authInterceptor.activeBaseUrl(_profile)

    private fun buildUrl(path: String, query: Map<String, String?> = emptyMap()): String {
        val cleanBase = baseUrl().trimEnd('/')
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        val httpUrl = "$cleanBase$cleanPath".toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid server URL: $cleanBase$cleanPath")
        val builder = httpUrl.newBuilder()
        query.forEach { (k, v) ->
            if (!v.isNullOrEmpty()) builder.addQueryParameter(k, v)
        }
        return builder.build().toString()
    }

    private suspend fun executeGetJson(path: String, query: Map<String, String?> = emptyMap()): JsonObject =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(buildUrl(path, query)).get().build()
            apiClient.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IOException("HTTP ${resp.code}: ${resp.message}")
                }
                gson.fromJson(bodyStr, JsonObject::class.java) ?: JsonObject()
            }
        }

    private suspend fun executeGetElement(path: String, query: Map<String, String?> = emptyMap()): JsonElement =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(buildUrl(path, query)).get().build()
            apiClient.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IOException("HTTP ${resp.code}: ${resp.message}")
                }
                gson.fromJson(bodyStr, JsonElement::class.java) ?: JsonObject()
            }
        }

    private suspend fun executePostJson(path: String, bodyJson: String): Unit =
        withContext(Dispatchers.IO) {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val req = Request.Builder()
                .url(buildUrl(path))
                .post(bodyJson.toRequestBody(mediaType))
                .build()
            apiClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IOException("HTTP ${resp.code}: ${resp.message}")
                }
            }
        }

    private suspend fun executeDelete(path: String): Unit =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(buildUrl(path)).delete().build()
            apiClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IOException("HTTP ${resp.code}: ${resp.message}")
                }
            }
        }

    private fun unwrapSubsonic(root: JsonObject): JsonObject {
        val sub = root.getAsJsonObject("subsonic-response") ?: root
        val status = sub.get("status")?.asString
        if (status != null && status != "ok") {
            val err = sub.getAsJsonObject("error")
            val code = err?.get("code")?.asInt ?: -1
            val msg = err?.get("message")?.asString ?: "Subsonic API error"
            throw SubsonicApiException(code, msg)
        }
        return sub
    }

    override suspend fun pingServer(): Boolean = withContext(Dispatchers.IO) {
        try {
            when {
                isJellyfin -> {
                    runCatching { executeGetElement("/System/Ping"); true }.getOrElse {
                        runCatching { executeGetElement("/System/Info/Public"); true }.getOrElse {
                            val sub = unwrapSubsonic(executeGetJson("/rest/ping.view"))
                            sub.get("status")?.asString == "ok"
                        }
                    }
                }
                isCustomRest -> {
                    runCatching { executeGetElement("/api/ping"); true }.getOrElse {
                        runCatching { executeGetElement("/health"); true }.getOrElse {
                            val sub = unwrapSubsonic(executeGetJson("/rest/ping.view"))
                            sub.get("status")?.asString == "ok"
                        }
                    }
                }
                else -> {
                    val sub = unwrapSubsonic(executeGetJson("/rest/ping.view"))
                    sub.get("status")?.asString == "ok"
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    override fun buildStreamUri(
        trackId: String,
        profile: TranscodingProfile,
        isWifi: Boolean,
        timeOffsetMs: Long?
    ): String {
        val codec = profile.codecFor(isWifi)
        val bitrate = profile.maxBitrateFor(isWifi)
        val base = baseUrl().trimEnd('/')

        return when {
            isJellyfin -> {
                val container = if (codec == AudioCodec.ORIGINAL) "flac,mp3,aac,opus,ogg" else codec.wireName
                val params = mutableMapOf(
                    "api_key" to _profile.secretOrPassword,
                    "UserId" to _profile.username,
                    "Container" to container
                )
                if (bitrate > 0) params["MaxStreamingBitrate"] = "${bitrate * 1000}"
                if (timeOffsetMs != null && timeOffsetMs > 0) {
                    params["StartTimeTicks"] = "${timeOffsetMs * 10_000L}"
                }
                buildUrl("/Audio/$trackId/universal", params)
            }
            isCustomRest -> {
                val params = mutableMapOf<String, String?>(
                    "format" to if (codec == AudioCodec.ORIGINAL) "raw" else codec.wireName
                )
                if (bitrate > 0) params["maxBitRate"] = "$bitrate"
                if (timeOffsetMs != null && timeOffsetMs > 0) {
                    params["timeOffset"] = "${timeOffsetMs / 1000L}"
                }
                buildUrl("/api/v1/tracks/$trackId/stream", params)
            }
            else -> {
                val auth = SubsonicAuthHelper.buildAuthParams(
                    _profile,
                    deterministicSeed = "stream_${_profile.id}_$trackId"
                )
                val params = auth.toMutableMap()
                params["id"] = trackId
                params["estimateContentLength"] = "true"
                if (codec == AudioCodec.ORIGINAL) {
                    params["format"] = "raw"
                    params["maxBitRate"] = "0"
                } else {
                    params["format"] = codec.wireName
                    if (bitrate > 0) params["maxBitRate"] = "$bitrate"
                }
                if (timeOffsetMs != null && timeOffsetMs > 0) {
                    params["timeOffset"] = "${timeOffsetMs / 1000L}"
                }
                buildUrl("/rest/stream.view", params)
            }
        }
    }

    override fun buildCoverArtUri(coverArtId: String, size: Int): String {
        return when {
            isJellyfin -> buildUrl(
                "/Items/$coverArtId/Images/Primary",
                mapOf("maxWidth" to "$size", "quality" to "90", "api_key" to _profile.secretOrPassword)
            )
            isCustomRest -> buildUrl("/api/v1/covers/$coverArtId", mapOf("size" to "$size"))
            else -> {
                val auth = SubsonicAuthHelper.buildAuthParams(
                    _profile,
                    deterministicSeed = "cover_${_profile.id}_${coverArtId}_$size"
                )
                val params = auth.toMutableMap()
                params["id"] = coverArtId
                params["size"] = "$size"
                buildUrl("/rest/getCoverArt.view", params)
            }
        }
    }

    override suspend fun getAlbumList(
        type: AlbumListType,
        size: Int,
        offset: Int,
        genre: String?,
        forceRefresh: Boolean
    ): List<AlbumEntity> {
        val key = "albums_${type.subsonicType}_${size}_${offset}_${genre ?: ""}"
        val raw = getCachedOrFetch(key, forceRefresh) {
            if (isJellyfin) {
                val sortBy = when (type) {
                    AlbumListType.NEWEST, AlbumListType.RECENT -> "DateCreated"
                    AlbumListType.FREQUENT -> "PlayCount"
                    AlbumListType.RANDOM -> "Random"
                    else -> "SortName"
                }
                val params = mutableMapOf<String, String?>(
                    "IncludeItemTypes" to "MusicAlbum",
                    "Recursive" to "true",
                    "Limit" to "$size",
                    "StartIndex" to "$offset",
                    "SortBy" to sortBy,
                    "SortOrder" to if (type == AlbumListType.NEWEST || type == AlbumListType.FREQUENT || type == AlbumListType.RECENT) "Descending" else "Ascending"
                )
                if (type == AlbumListType.STARRED) params["Filters"] = "IsFavorite"
                if (!genre.isNullOrBlank()) params["Genres"] = genre
                val json = executeGetJson("/Items", params)
                gson.toJson(parseJellyfinAlbums(json.getAsJsonArray("Items")))
            } else {
                val sub = unwrapSubsonic(
                    executeGetJson(
                        "/rest/getAlbumList2.view",
                        mapOf("type" to type.subsonicType, "size" to "$size", "offset" to "$offset", "genre" to genre)
                    )
                )
                val arr = sub.getAsJsonObject("albumList2")?.getAsJsonArray("album") ?: JsonArray()
                gson.toJson(arr.mapNotNull { parseSubsonicAlbum(it.asJsonObject) })
            }
        }
        return gson.fromJson(raw, Array<AlbumEntity>::class.java).toList()
    }

    override suspend fun getAlbumDetails(albumId: String, forceRefresh: Boolean): AlbumEntity {
        val key = "album_detail_$albumId"
        val raw = getCachedOrFetch(key, forceRefresh) {
            if (isJellyfin) {
                val albumJson = executeGetJson("/Items/$albumId")
                val itemsJson = executeGetJson(
                    "/Items",
                    mapOf("ParentId" to albumId, "IncludeItemTypes" to "Audio", "SortBy" to "ParentIndexNumber,IndexNumber")
                )
                val tracks = parseJellyfinTracks(itemsJson.getAsJsonArray("Items"))
                val base = parseJellyfinAlbum(albumJson)
                gson.toJson(base.copy(tracks = tracks, songCount = tracks.size))
            } else {
                val sub = unwrapSubsonic(executeGetJson("/rest/getAlbum.view", mapOf("id" to albumId)))
                val albumObj = sub.getAsJsonObject("album") ?: JsonObject()
                val songs = albumObj.getAsJsonArray("song")?.mapNotNull { parseSubsonicTrack(it.asJsonObject) } ?: emptyList()
                val album = parseSubsonicAlbum(albumObj)?.copy(tracks = songs)
                    ?: AlbumEntity(id = albumId, name = "Unknown Album", artist = "Unknown Artist", artistId = "", songCount = songs.size, durationMs = songs.sumOf { it.durationMs }, tracks = songs)
                gson.toJson(album)
            }
        }
        return gson.fromJson(raw, AlbumEntity::class.java)
    }

    override suspend fun getArtists(forceRefresh: Boolean): List<ArtistEntity> {
        val raw = getCachedOrFetch("artists", forceRefresh) {
            if (isJellyfin) {
                val json = executeGetJson("/Artists", mapOf("Recursive" to "true"))
                val items = json.getAsJsonArray("Items") ?: JsonArray()
                val artists = items.mapNotNull { el ->
                    val o = el.asJsonObject
                    val id = o.str("Id") ?: return@mapNotNull null
                    ArtistEntity(id = id, name = o.str("Name") ?: "Unknown Artist", albumCount = o.int("AlbumCount") ?: 0, coverArtId = id)
                }
                gson.toJson(artists)
            } else {
                val sub = unwrapSubsonic(executeGetJson("/rest/getArtists.view"))
                val indexes = sub.getAsJsonObject("artists")?.getAsJsonArray("index") ?: JsonArray()
                val artists = mutableListOf<ArtistEntity>()
                for (idx in indexes) {
                    val arr = idx.asJsonObject.getAsJsonArray("artist") ?: continue
                    for (a in arr) {
                        val o = a.asJsonObject
                        val id = o.str("id") ?: continue
                        artists.add(
                            ArtistEntity(
                                id = id,
                                name = o.str("name") ?: "Unknown Artist",
                                albumCount = o.int("albumCount") ?: 0,
                                coverArtId = o.str("coverArt") ?: id,
                                isStarred = o.has("starred")
                            )
                        )
                    }
                }
                gson.toJson(artists)
            }
        }
        return gson.fromJson(raw, Array<ArtistEntity>::class.java).toList()
    }

    override suspend fun getArtistAlbums(artistId: String, forceRefresh: Boolean): List<AlbumEntity> {
        val raw = getCachedOrFetch("artist_albums_$artistId", forceRefresh) {
            if (isJellyfin) {
                val json = executeGetJson("/Items", mapOf("IncludeItemTypes" to "MusicAlbum", "AlbumArtistIds" to artistId, "Recursive" to "true"))
                gson.toJson(parseJellyfinAlbums(json.getAsJsonArray("Items")))
            } else {
                val sub = unwrapSubsonic(executeGetJson("/rest/getArtist.view", mapOf("id" to artistId)))
                val arr = sub.getAsJsonObject("artist")?.getAsJsonArray("album") ?: JsonArray()
                gson.toJson(arr.mapNotNull { parseSubsonicAlbum(it.asJsonObject) })
            }
        }
        return gson.fromJson(raw, Array<AlbumEntity>::class.java).toList()
    }

    override suspend fun getAllTracks(count: Int, offset: Int, forceRefresh: Boolean): List<MediaItemEntity> {
        val key = "all_tracks_${count}_$offset"
        val raw = getCachedOrFetch(key, forceRefresh) {
            if (isJellyfin) {
                val json = executeGetJson("/Items", mapOf("IncludeItemTypes" to "Audio", "Recursive" to "true", "SortBy" to "SortName", "Limit" to "$count", "StartIndex" to "$offset"))
                gson.toJson(parseJellyfinTracks(json.getAsJsonArray("Items")))
            } else {
                val tracks = runCatching {
                    val sub = unwrapSubsonic(
                        executeGetJson(
                            "/rest/search3.view",
                            mapOf("query" to "", "songCount" to "$count", "songOffset" to "$offset", "albumCount" to "0", "artistCount" to "0")
                        )
                    )
                    val arr = sub.getAsJsonObject("searchResult3")?.getAsJsonArray("song") ?: JsonArray()
                    arr.mapNotNull { parseSubsonicTrack(it.asJsonObject) }
                }.getOrDefault(emptyList())
                val finalTracks = if (tracks.isEmpty()) getQuickMix(count = count, forceRefresh = forceRefresh) else tracks
                gson.toJson(finalTracks)
            }
        }
        return gson.fromJson(raw, Array<MediaItemEntity>::class.java).toList()
    }

    override suspend fun getGenres(forceRefresh: Boolean): List<GenreEntity> {
        val raw = getCachedOrFetch("genres", forceRefresh) {
            if (isJellyfin) {
                val json = executeGetJson("/MusicGenres", mapOf("Recursive" to "true"))
                val items = json.getAsJsonArray("Items") ?: JsonArray()
                val genres = items.mapNotNull { el ->
                    val o = el.asJsonObject
                    val name = o.str("Name") ?: return@mapNotNull null
                    GenreEntity(name = name, songCount = o.int("SongCount") ?: 0, albumCount = o.int("AlbumCount") ?: 0)
                }
                gson.toJson(genres)
            } else {
                val sub = unwrapSubsonic(executeGetJson("/rest/getGenres.view"))
                val arr = sub.getAsJsonObject("genres")?.getAsJsonArray("genre") ?: JsonArray()
                val genres = arr.mapNotNull { el ->
                    val o = el.asJsonObject
                    val name = o.str("value") ?: o.str("name") ?: return@mapNotNull null
                    GenreEntity(name = name, songCount = o.int("songCount") ?: 0, albumCount = o.int("albumCount") ?: 0)
                }
                gson.toJson(genres)
            }
        }
        return gson.fromJson(raw, Array<GenreEntity>::class.java).toList()
    }

    override suspend fun getSongsByGenre(genre: String, count: Int, forceRefresh: Boolean): List<MediaItemEntity> {
        val raw = getCachedOrFetch("genre_songs_${genre}_$count", forceRefresh) {
            if (isJellyfin) {
                val json = executeGetJson("/Items", mapOf("IncludeItemTypes" to "Audio", "Genres" to genre, "Limit" to "$count", "Recursive" to "true"))
                gson.toJson(parseJellyfinTracks(json.getAsJsonArray("Items")))
            } else {
                val sub = unwrapSubsonic(executeGetJson("/rest/getSongsByGenre.view", mapOf("genre" to genre, "count" to "$count")))
                val arr = sub.getAsJsonObject("songsByGenre")?.getAsJsonArray("song") ?: JsonArray()
                gson.toJson(arr.mapNotNull { parseSubsonicTrack(it.asJsonObject) })
            }
        }
        return gson.fromJson(raw, Array<MediaItemEntity>::class.java).toList()
    }

    override suspend fun getPlaylists(forceRefresh: Boolean): List<PlaylistEntity> {
        val raw = getCachedOrFetch("playlists", forceRefresh) {
            if (isJellyfin) {
                val json = executeGetJson("/Items", mapOf("IncludeItemTypes" to "Playlist", "Recursive" to "true"))
                val items = json.getAsJsonArray("Items") ?: JsonArray()
                val list = items.mapNotNull { el ->
                    val o = el.asJsonObject
                    val id = o.str("Id") ?: return@mapNotNull null
                    PlaylistEntity(
                        id = id,
                        name = o.str("Name") ?: "Playlist",
                        songCount = o.int("ChildCount") ?: 0,
                        durationMs = (o.long("RunTimeTicks") ?: 0L) / 10_000L,
                        coverArtId = id
                    )
                }
                gson.toJson(list)
            } else {
                val sub = unwrapSubsonic(executeGetJson("/rest/getPlaylists.view"))
                val arr = sub.getAsJsonObject("playlists")?.getAsJsonArray("playlist") ?: JsonArray()
                val list = arr.mapNotNull { el ->
                    val o = el.asJsonObject
                    val id = o.str("id") ?: return@mapNotNull null
                    PlaylistEntity(
                        id = id,
                        name = o.str("name") ?: "Playlist",
                        comment = o.str("comment"),
                        owner = o.str("owner"),
                        songCount = o.int("songCount") ?: 0,
                        durationMs = (o.long("duration") ?: 0L) * 1000L,
                        coverArtId = o.str("coverArt") ?: id
                    )
                }
                gson.toJson(list)
            }
        }
        return gson.fromJson(raw, Array<PlaylistEntity>::class.java).toList()
    }

    override suspend fun getPlaylistDetails(playlistId: String, forceRefresh: Boolean): PlaylistEntity {
        val raw = getCachedOrFetch("playlist_$playlistId", forceRefresh) {
            if (isJellyfin) {
                val json = executeGetJson("/Playlists/$playlistId/Items")
                val tracks = parseJellyfinTracks(json.getAsJsonArray("Items"))
                gson.toJson(
                    PlaylistEntity(
                        id = playlistId,
                        name = "Playlist",
                        songCount = tracks.size,
                        durationMs = tracks.sumOf { it.durationMs },
                        coverArtId = playlistId,
                        tracks = tracks
                    )
                )
            } else {
                val sub = unwrapSubsonic(executeGetJson("/rest/getPlaylist.view", mapOf("id" to playlistId)))
                val o = sub.getAsJsonObject("playlist") ?: JsonObject()
                val tracks = o.getAsJsonArray("entry")?.mapNotNull { parseSubsonicTrack(it.asJsonObject) } ?: emptyList()
                gson.toJson(
                    PlaylistEntity(
                        id = o.str("id") ?: playlistId,
                        name = o.str("name") ?: "Playlist",
                        comment = o.str("comment"),
                        owner = o.str("owner"),
                        songCount = o.int("songCount") ?: tracks.size,
                        durationMs = (o.long("duration") ?: 0L) * 1000L,
                        coverArtId = o.str("coverArt") ?: playlistId,
                        tracks = tracks
                    )
                )
            }
        }
        return gson.fromJson(raw, PlaylistEntity::class.java)
    }

    override suspend fun getMusicRootEntries(forceRefresh: Boolean): List<MusicFolderNodeEntity> {
        val raw = getCachedOrFetch("folders_root", forceRefresh) {
            val sub = unwrapSubsonic(executeGetJson("/rest/getIndexes.view"))
            val indexes = sub.getAsJsonObject("indexes") ?: JsonObject()
            val nodes = mutableListOf<MusicFolderNodeEntity>()
            indexes.getAsJsonArray("index")?.forEach { idx ->
                idx.asJsonObject.getAsJsonArray("artist")?.forEach { a ->
                    val o = a.asJsonObject
                    val id = o.str("id") ?: return@forEach
                    nodes.add(
                        MusicFolderNodeEntity(
                            id = id,
                            title = o.str("name") ?: "Folder",
                            isDirectory = true,
                            coverArtId = o.str("coverArt")
                        )
                    )
                }
            }
            indexes.getAsJsonArray("child")?.forEach { c ->
                val o = c.asJsonObject
                val id = o.str("id") ?: return@forEach
                val isDir = o.bool("isDir") ?: false
                val track = if (!isDir) parseSubsonicTrack(o) else null
                nodes.add(
                    MusicFolderNodeEntity(
                        id = id,
                        title = o.str("title") ?: o.str("name") ?: "Item",
                        subtitle = o.str("artist"),
                        isDirectory = isDir,
                        coverArtId = o.str("coverArt"),
                        track = track
                    )
                )
            }
            gson.toJson(nodes)
        }
        return gson.fromJson(raw, Array<MusicFolderNodeEntity>::class.java).toList()
    }

    override suspend fun getMusicDirectory(directoryId: String, forceRefresh: Boolean): List<MusicFolderNodeEntity> {
        val raw = getCachedOrFetch("folder_$directoryId", forceRefresh) {
            val sub = unwrapSubsonic(executeGetJson("/rest/getMusicDirectory.view", mapOf("id" to directoryId)))
            val children = sub.getAsJsonObject("directory")?.getAsJsonArray("child") ?: JsonArray()
            val nodes = children.mapNotNull { el ->
                val o = el.asJsonObject
                val id = o.str("id") ?: return@mapNotNull null
                val isDir = o.bool("isDir") ?: false
                val track = if (!isDir) parseSubsonicTrack(o) else null
                MusicFolderNodeEntity(
                    id = id,
                    title = o.str("title") ?: o.str("name") ?: "Folder",
                    subtitle = o.str("artist"),
                    isDirectory = isDir,
                    coverArtId = o.str("coverArt"),
                    track = track
                )
            }
            gson.toJson(nodes)
        }
        return gson.fromJson(raw, Array<MusicFolderNodeEntity>::class.java).toList()
    }

    override suspend fun getQuickMix(
        seedArtistId: String?,
        genre: String?,
        count: Int,
        forceRefresh: Boolean
    ): List<MediaItemEntity> {
        val key = "quickmix_${seedArtistId ?: ""}_${genre ?: ""}_$count"
        val raw = getCachedOrFetch(key, forceRefresh) {
            if (isJellyfin) {
                val params = mutableMapOf<String, String?>(
                    "IncludeItemTypes" to "Audio",
                    "SortBy" to "Random",
                    "Limit" to "$count",
                    "Recursive" to "true"
                )
                if (!seedArtistId.isNullOrBlank()) params["ArtistIds"] = seedArtistId
                if (!genre.isNullOrBlank()) params["Genres"] = genre
                val json = executeGetJson("/Items", params)
                gson.toJson(parseJellyfinTracks(json.getAsJsonArray("Items")))
            } else {
                val tracks = if (!seedArtistId.isNullOrBlank()) {
                    val sub = unwrapSubsonic(executeGetJson("/rest/getSimilarSongs2.view", mapOf("id" to seedArtistId, "count" to "$count")))
                    sub.getAsJsonObject("similarSongs2")?.getAsJsonArray("song")?.mapNotNull { parseSubsonicTrack(it.asJsonObject) } ?: emptyList()
                } else {
                    val sub = unwrapSubsonic(executeGetJson("/rest/getRandomSongs.view", mapOf("size" to "$count", "genre" to genre)))
                    sub.getAsJsonObject("randomSongs")?.getAsJsonArray("song")?.mapNotNull { parseSubsonicTrack(it.asJsonObject) } ?: emptyList()
                }
                gson.toJson(tracks)
            }
        }
        return gson.fromJson(raw, Array<MediaItemEntity>::class.java).toList()
    }

    override suspend fun getPodcastEpisodes(channelId: String?, forceRefresh: Boolean): List<MediaItemEntity> {
        val raw = getCachedOrFetch("podcasts_${channelId ?: "all"}", forceRefresh) {
            val episodes = runCatching {
                val sub = unwrapSubsonic(
                    executeGetJson(
                        "/rest/getPodcasts.view",
                        mapOf("includeEpisodes" to "true", "id" to channelId)
                    )
                )
                val channels = sub.getAsJsonObject("podcasts")?.getAsJsonArray("channel") ?: JsonArray()
                val list = mutableListOf<MediaItemEntity>()
                for (ch in channels) {
                    val chObj = ch.asJsonObject
                    val eps = chObj.getAsJsonArray("episode") ?: continue
                    for (ep in eps) {
                        parseSubsonicTrack(ep.asJsonObject, defaultType = MediaContentType.PODCAST)?.let { list.add(it) }
                    }
                }
                list
            }.getOrDefault(emptyList())
            val finalEps = if (episodes.isEmpty()) {
                runCatching { getSongsByGenre("Podcast", count = 50, forceRefresh = forceRefresh) }.getOrDefault(emptyList())
            } else episodes
            gson.toJson(finalEps)
        }
        return gson.fromJson(raw, Array<MediaItemEntity>::class.java).toList()
    }

    override suspend fun getAudiobooks(forceRefresh: Boolean): List<MediaItemEntity> {
        val raw = getCachedOrFetch("audiobooks", forceRefresh) {
            val books = runCatching {
                val sub = unwrapSubsonic(executeGetJson("/rest/getBookmarks.view"))
                val bms = sub.getAsJsonObject("bookmarks")?.getAsJsonArray("bookmark") ?: JsonArray()
                bms.mapNotNull { el ->
                    val o = el.asJsonObject
                    val posMs = o.long("position") ?: 0L
                    val entry = o.getAsJsonObject("entry") ?: return@mapNotNull null
                    parseSubsonicTrack(entry, defaultType = MediaContentType.AUDIOBOOK)
                        ?.copy(bookmarkPositionMs = posMs)
                }
            }.getOrDefault(emptyList())
            val finalBooks = if (books.isEmpty()) {
                runCatching { getSongsByGenre("Audiobook", count = 50, forceRefresh = forceRefresh) }.getOrDefault(emptyList())
            } else books
            gson.toJson(finalBooks)
        }
        return gson.fromJson(raw, Array<MediaItemEntity>::class.java).toList()
    }

    override suspend fun getLyrics(trackId: String, artist: String?, title: String?): SynchronizedLyricsEntity {
        runCatching {
            val sub = unwrapSubsonic(executeGetJson("/rest/getLyricsBySongId.view", mapOf("id" to trackId)))
            val structured = sub.getAsJsonObject("lyricsList")
                ?.getAsJsonArray("structuredLyrics")
                ?.firstOrNull()?.asJsonObject
            if (structured != null) {
                val synced = structured.bool("synced") ?: false
                val lines = structured.getAsJsonArray("line")?.mapNotNull { el ->
                    val o = el.asJsonObject
                    val text = o.str("value")?.trim().orEmpty()
                    if (text.isEmpty()) return@mapNotNull null
                    LyricLine(timestampMs = o.long("start") ?: 0L, text = text)
                } ?: emptyList()
                if (lines.isNotEmpty()) {
                    return SynchronizedLyricsEntity(trackId = trackId, isSynced = synced, lines = lines)
                }
            }
        }

        runCatching {
            val sub = unwrapSubsonic(
                executeGetJson("/rest/getLyrics.view", mapOf("artist" to artist, "title" to title))
            )
            val rawText = sub.getAsJsonObject("lyrics")?.str("value").orEmpty()
            if (rawText.isNotBlank()) {
                return LrcParser.parse(trackId = trackId, rawLrc = rawText)
            }
        }

        return SynchronizedLyricsEntity(trackId = trackId, isSynced = false, lines = emptyList())
    }

    override suspend fun search(filter: SearchFilter): SearchResultBundle {
        val bundle = if (isJellyfin) {
            val json = executeGetJson(
                "/Items",
                mapOf(
                    "SearchTerm" to filter.query,
                    "IncludeItemTypes" to "Audio,MusicAlbum,MusicArtist",
                    "Recursive" to "true",
                    "Limit" to "${filter.limit}"
                )
            )
            val items = json.getAsJsonArray("Items") ?: JsonArray()
            val tracks = mutableListOf<MediaItemEntity>()
            val albums = mutableListOf<AlbumEntity>()
            val artists = mutableListOf<ArtistEntity>()
            for (el in items) {
                val o = el.asJsonObject
                when (o.str("Type")) {
                    "Audio" -> parseJellyfinTrack(o)?.let { tracks.add(it) }
                    "MusicAlbum" -> albums.add(parseJellyfinAlbum(o))
                    "MusicArtist" -> o.str("Id")?.let { id ->
                        artists.add(ArtistEntity(id = id, name = o.str("Name") ?: "Unknown Artist", coverArtId = id))
                    }
                }
            }
            SearchResultBundle(tracks = tracks, albums = albums, artists = artists)
        } else {
            val sub = unwrapSubsonic(
                executeGetJson(
                    "/rest/search3.view",
                    mapOf(
                        "query" to filter.query,
                        "songCount" to "${filter.limit}",
                        "songOffset" to "${filter.offset}",
                        "albumCount" to "25",
                        "artistCount" to "15"
                    )
                )
            )
            val sr = sub.getAsJsonObject("searchResult3") ?: JsonObject()
            val tracks = sr.getAsJsonArray("song")?.mapNotNull { parseSubsonicTrack(it.asJsonObject) } ?: emptyList()
            val albums = sr.getAsJsonArray("album")?.mapNotNull { parseSubsonicAlbum(it.asJsonObject) } ?: emptyList()
            val artists = sr.getAsJsonArray("artist")?.mapNotNull { el ->
                val o = el.asJsonObject
                val id = o.str("id") ?: return@mapNotNull null
                ArtistEntity(id = id, name = o.str("name") ?: "Unknown Artist", albumCount = o.int("albumCount") ?: 0, coverArtId = o.str("coverArt") ?: id)
            } ?: emptyList()
            SearchResultBundle(tracks = tracks, albums = albums, artists = artists)
        }

        val filteredTracks = bundle.tracks.filter { track ->
            val genreOk = filter.genre == null || track.genre.equals(filter.genre, ignoreCase = true)
            val fromOk = filter.fromYear == null || (track.year != null && track.year >= filter.fromYear)
            val toOk = filter.toYear == null || (track.year != null && track.year <= filter.toYear)
            val typeOk = filter.contentType == null || track.contentType == filter.contentType
            genreOk && fromOk && toOk && typeOk
        }
        return bundle.copy(tracks = filteredTracks)
    }

    override suspend fun scrobble(trackId: String, timestampMs: Long, submission: Boolean) {
        if (isJellyfin) {
            val endpoint = if (submission) "/Sessions/Playing/Stopped" else "/Sessions/Playing"
            executePostJson(endpoint, gson.toJson(mapOf("ItemId" to trackId)))
        } else {
            unwrapSubsonic(
                executeGetJson(
                    "/rest/scrobble.view",
                    mapOf("id" to trackId, "time" to "$timestampMs", "submission" to "$submission")
                )
            )
        }
    }

    override suspend fun setFavorite(id: String, isFavorite: Boolean) {
        if (isJellyfin) {
            if (isFavorite) {
                executePostJson("/FavoriteItems/$id", "{}")
            } else {
                executeDelete("/FavoriteItems/$id")
            }
        } else {
            val endpoint = if (isFavorite) "/rest/star.view" else "/rest/unstar.view"
            unwrapSubsonic(executeGetJson(endpoint, mapOf("id" to id)))
        }
    }

    override suspend fun setRating(id: String, rating: Int) {
        unwrapSubsonic(executeGetJson("/rest/setRating.view", mapOf("id" to id, "rating" to "${rating.coerceIn(0, 5)}")))
    }

    override suspend fun saveBookmark(trackId: String, positionMs: Long, comment: String?) {
        unwrapSubsonic(
            executeGetJson(
                "/rest/createBookmark.view",
                mapOf("id" to trackId, "position" to "$positionMs", "comment" to comment)
            )
        )
    }

    // --- Parsers ---

    private fun parseSubsonicTrack(
        o: JsonObject,
        defaultType: MediaContentType = MediaContentType.MUSIC
    ): MediaItemEntity? {
        val id = o.str("id") ?: return null
        val rg = o.getAsJsonObject("replayGain")
        val replayGain = ReplayGainInfo(
            trackGainDb = rg?.double("trackGain"),
            albumGainDb = rg?.double("albumGain"),
            trackPeak = rg?.double("trackPeak"),
            albumPeak = rg?.double("albumPeak")
        )
        val genreStr = o.str("genre")
        val type = when {
            o.str("type").equals("podcast", ignoreCase = true) -> MediaContentType.PODCAST
            o.str("type").equals("audiobook", ignoreCase = true) -> MediaContentType.AUDIOBOOK
            genreStr?.contains("podcast", ignoreCase = true) == true -> MediaContentType.PODCAST
            genreStr?.contains("audiobook", ignoreCase = true) == true -> MediaContentType.AUDIOBOOK
            else -> defaultType
        }
        val bookmarkSec = o.long("bookmarkPosition")
        return MediaItemEntity(
            id = id,
            serverId = _profile.id,
            title = o.str("title") ?: "Untitled",
            artist = o.str("artist") ?: "Unknown Artist",
            artistId = o.str("artistId") ?: "",
            album = o.str("album") ?: "Unknown Album",
            albumId = o.str("albumId") ?: "",
            durationMs = (o.long("duration") ?: 0L) * 1000L,
            trackNumber = o.int("track"),
            discNumber = o.int("discNumber"),
            year = o.int("year"),
            genre = genreStr,
            bitrateKbps = o.int("bitRate"),
            suffix = o.str("suffix"),
            fileSize = o.long("size"),
            coverArtId = o.str("coverArt") ?: o.str("albumId") ?: id,
            isStarred = o.has("starred"),
            userRating = o.int("userRating") ?: 0,
            contentType = type,
            bookmarkPositionMs = bookmarkSec?.let { it * 1000L },
            replayGain = replayGain,
            waveformPeaks = generateDeterministicWaveform(id)
        )
    }

    private fun parseSubsonicAlbum(o: JsonObject): AlbumEntity? {
        val id = o.str("id") ?: return null
        return AlbumEntity(
            id = id,
            name = o.str("name") ?: o.str("title") ?: "Unknown Album",
            artist = o.str("artist") ?: "Unknown Artist",
            artistId = o.str("artistId") ?: "",
            coverArtId = o.str("coverArt") ?: id,
            songCount = o.int("songCount") ?: 0,
            durationMs = (o.long("duration") ?: 0L) * 1000L,
            year = o.int("year"),
            genre = o.str("genre"),
            isStarred = o.has("starred")
        )
    }

    private fun parseJellyfinAlbums(arr: JsonArray?): List<AlbumEntity> {
        if (arr == null) return emptyList()
        return arr.map { parseJellyfinAlbum(it.asJsonObject) }
    }

    private fun parseJellyfinAlbum(o: JsonObject): AlbumEntity {
        val id = o.str("Id") ?: ""
        return AlbumEntity(
            id = id,
            name = o.str("Name") ?: "Unknown Album",
            artist = o.str("AlbumArtist") ?: o.getAsJsonArray("Artists")?.firstOrNull()?.asString ?: "Unknown Artist",
            artistId = o.getAsJsonArray("ArtistItems")?.firstOrNull()?.asJsonObject?.str("Id") ?: "",
            coverArtId = id,
            songCount = o.int("ChildCount") ?: 0,
            durationMs = (o.long("RunTimeTicks") ?: 0L) / 10_000L,
            year = o.int("ProductionYear"),
            genre = o.getAsJsonArray("Genres")?.firstOrNull()?.asString,
            isStarred = o.getAsJsonObject("UserData")?.bool("IsFavorite") ?: false
        )
    }

    private fun parseJellyfinTracks(arr: JsonArray?): List<MediaItemEntity> {
        if (arr == null) return emptyList()
        return arr.mapNotNull { parseJellyfinTrack(it.asJsonObject) }
    }

    private fun parseJellyfinTrack(o: JsonObject): MediaItemEntity? {
        val id = o.str("Id") ?: return null
        val userData = o.getAsJsonObject("UserData")
        val posTicks = userData?.long("PlaybackPositionTicks") ?: 0L
        return MediaItemEntity(
            id = id,
            serverId = _profile.id,
            title = o.str("Name") ?: "Untitled",
            artist = o.getAsJsonArray("Artists")?.firstOrNull()?.asString ?: o.str("AlbumArtist") ?: "Unknown Artist",
            artistId = o.getAsJsonArray("ArtistItems")?.firstOrNull()?.asJsonObject?.str("Id") ?: "",
            album = o.str("Album") ?: "Unknown Album",
            albumId = o.str("AlbumId") ?: "",
            durationMs = (o.long("RunTimeTicks") ?: 0L) / 10_000L,
            trackNumber = o.int("IndexNumber"),
            discNumber = o.int("ParentIndexNumber"),
            year = o.int("ProductionYear"),
            genre = o.getAsJsonArray("Genres")?.firstOrNull()?.asString,
            coverArtId = id,
            isStarred = userData?.bool("IsFavorite") ?: false,
            bookmarkPositionMs = (posTicks / 10_000L).takeIf { it > 0L },
            replayGain = ReplayGainInfo(trackGainDb = o.double("NormalizationGain")),
            waveformPeaks = generateDeterministicWaveform(id)
        )
    }

    private fun JsonObject.str(key: String): String? =
        get(key)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString

    private fun JsonObject.int(key: String): Int? =
        get(key)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.let { runCatching { it.asInt }.getOrNull() }

    private fun JsonObject.long(key: String): Long? =
        get(key)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.let { runCatching { it.asLong }.getOrNull() }

    private fun JsonObject.double(key: String): Double? =
        get(key)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.let { runCatching { it.asDouble }.getOrNull() }

    private fun JsonObject.bool(key: String): Boolean? =
        get(key)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.let { runCatching { it.asBoolean }.getOrNull() }
}
