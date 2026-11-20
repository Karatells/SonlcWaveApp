package com.sonicwave.sonic_wave.domain.models

import com.sonicwave.sonic_wave.core.utils.generateDeterministicWaveform
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class ServerProtocol(val displayName: String) {
    OPEN_SUBSONIC("OpenSubsonic"),
    NAVIDROME("Navidrome"),
    JELLYFIN("Jellyfin"),
    CUSTOM_REST("Custom REST");

    companion object {
        fun fromName(name: String?): ServerProtocol =
            entries.firstOrNull {
                it.name.equals(name, ignoreCase = true) ||
                    it.name.replace("_", "").equals(name, ignoreCase = true) ||
                    when (name?.lowercase()) {
                        "opensubsonic" -> it == OPEN_SUBSONIC
                        "customrest" -> it == CUSTOM_REST
                        else -> false
                    }
            } ?: OPEN_SUBSONIC
    }
}

enum class MediaContentType {
    MUSIC,
    PODCAST,
    AUDIOBOOK;

    companion object {
        fun fromName(name: String?): MediaContentType =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: MUSIC
    }
}

enum class AudioCodec(val wireName: String) {
    ORIGINAL("original"),
    OPUS("opus"),
    MP3("mp3"),
    AAC("aac");

    companion object {
        fun fromName(name: String?): AudioCodec =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: ORIGINAL
    }
}

data class TranscodingProfile(
    val wifiCodec: AudioCodec = AudioCodec.ORIGINAL,
    val wifiMaxBitrateKbps: Int = 0,
    val mobileCodec: AudioCodec = AudioCodec.OPUS,
    val mobileMaxBitrateKbps: Int = 160
) {
    fun codecFor(isWifi: Boolean): AudioCodec = if (isWifi) wifiCodec else mobileCodec
    fun maxBitrateFor(isWifi: Boolean): Int = if (isWifi) wifiMaxBitrateKbps else mobileMaxBitrateKbps
}

data class ServerProfile(
    val id: String,
    val name: String,
    val protocol: ServerProtocol,
    val primaryUrl: String,
    val lanUrl: String? = null,
    val username: String,
    val secretOrPassword: String,
    val allowSelfSignedSsl: Boolean = false,
    val isActive: Boolean = false
)

data class ReplayGainInfo(
    val trackGainDb: Double? = null,
    val albumGainDb: Double? = null,
    val trackPeak: Double? = null,
    val albumPeak: Double? = null
) {
    fun computeLinearScale(preferAlbumGain: Boolean = false, preampDb: Double = 0.0): Float {
        val gainDb = (if (preferAlbumGain) albumGainDb ?: trackGainDb else trackGainDb ?: albumGainDb) ?: 0.0
        val peak = (if (preferAlbumGain) albumPeak ?: trackPeak else trackPeak ?: albumPeak) ?: 1.0
        if (gainDb == 0.0 && preampDb == 0.0) return 1.0f

        var linear = dbToLinear(gainDb + preampDb)
        if (peak > 0.0) {
            linear = min(linear, 1.0 / peak)
        }
        return linear.coerceIn(0.1, 1.0).toFloat()
    }

    private fun dbToLinear(db: Double): Double {
        val x = db * 0.11512925464970229
        var term = 1.0
        var sum = 1.0
        for (i in 1..12) {
            term *= x / i
            sum += term
        }
        return sum
    }
}

data class MediaItemEntity(
    val id: String,
    val serverId: String,
    val title: String,
    val artist: String,
    val artistId: String,
    val album: String,
    val albumId: String,
    val durationMs: Long,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val bitrateKbps: Int? = null,
    val suffix: String? = null,
    val fileSize: Long? = null,
    val coverArtId: String? = null,
    val isStarred: Boolean = false,
    val userRating: Int = 0,
    val contentType: MediaContentType = MediaContentType.MUSIC,
    val bookmarkPositionMs: Long? = null,
    val replayGain: ReplayGainInfo = ReplayGainInfo(),
    val waveformPeaks: List<Float> = generateDeterministicWaveform(id),
    val localFilePath: String? = null
) {
    val isOfflineAvailable: Boolean
        get() = !localFilePath.isNullOrBlank()
}

data class AlbumEntity(
    val id: String,
    val name: String,
    val artist: String,
    val artistId: String,
    val coverArtId: String? = null,
    val songCount: Int,
    val durationMs: Long,
    val year: Int? = null,
    val genre: String? = null,
    val isStarred: Boolean = false,
    val tracks: List<MediaItemEntity> = emptyList()
)

data class ArtistEntity(
    val id: String,
    val name: String,
    val albumCount: Int = 0,
    val coverArtId: String? = null,
    val isStarred: Boolean = false
)

data class PlaylistEntity(
    val id: String,
    val name: String,
    val comment: String? = null,
    val owner: String? = null,
    val songCount: Int,
    val durationMs: Long,
    val coverArtId: String? = null,
    val tracks: List<MediaItemEntity> = emptyList()
)

data class GenreEntity(
    val name: String,
    val songCount: Int,
    val albumCount: Int
)

data class MusicFolderNodeEntity(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val isDirectory: Boolean,
    val coverArtId: String? = null,
    val track: MediaItemEntity? = null
)

data class LyricLine(
    val timestampMs: Long,
    val text: String
)

data class SynchronizedLyricsEntity(
    val trackId: String,
    val isSynced: Boolean,
    val lines: List<LyricLine>
) {
    fun activeLineIndexAt(positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        for (i in lines.indices.reversed()) {
            if (positionMs >= lines[i].timestampMs) {
                return i
            }
        }
        return 0
    }
}
