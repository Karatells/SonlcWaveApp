package com.sonicwave.sonic_wave.core.utils

import com.sonicwave.sonic_wave.domain.models.LyricLine
import com.sonicwave.sonic_wave.domain.models.SynchronizedLyricsEntity

object LrcParser {
    private val timestampRegex = Regex("""\[(\d{1,3}):(\d{2})(?:\.(\d{1,3}))?]""")

    fun parse(trackId: String, rawLrc: String): SynchronizedLyricsEntity {
        if (rawLrc.isBlank()) {
            return SynchronizedLyricsEntity(trackId = trackId, isSynced = false, lines = emptyList())
        }

        val lines = mutableListOf<LyricLine>()
        var hasTimestamps = false

        for (rawLine in rawLrc.lines()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue

            val matches = timestampRegex.findAll(trimmed).toList()
            if (matches.isEmpty()) {
                if (!trimmed.startsWith("[")) {
                    lines.add(LyricLine(timestampMs = 0L, text = trimmed))
                }
                continue
            }

            hasTimestamps = true
            val textOnly = trimmed.replace(timestampRegex, "").trim()
            if (textOnly.isEmpty()) continue

            for (match in matches) {
                val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                val fracStr = match.groupValues[3]
                val millis = when (fracStr.length) {
                    0 -> 0L
                    1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
                    2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
                    else -> fracStr.take(3).toLongOrNull() ?: 0L
                }
                val ts = (minutes * 60_000L) + (seconds * 1_000L) + millis
                lines.add(LyricLine(timestampMs = ts, text = textOnly))
            }
        }

        if (hasTimestamps) {
            lines.sortBy { it.timestampMs }
        }

        return SynchronizedLyricsEntity(
            trackId = trackId,
            isSynced = hasTimestamps && lines.isNotEmpty(),
            lines = lines
        )
    }
}
