package com.sonicwave.sonic_wave.core.utils

fun generateDeterministicWaveform(seed: String): List<Float> {
    var hash = 5381L
    for (ch in seed) {
        hash = ((hash shl 5) + hash + ch.code.toLong()) and 0x7FFFFFFFL
    }
    val raw = FloatArray(72)
    for (i in raw.indices) {
        hash = ((hash * 1103515245L + 12345L) and 0x7FFFFFFFL)
        val norm = (hash % 1000L) / 1000f
        val envelope = when {
            i < 6 -> (i + 1) / 7f
            i > 65 -> (72 - i) / 7f
            else -> 1.0f
        }
        raw[i] = (0.22f + norm * 0.78f) * envelope
    }
    return raw.indices.map { i ->
        val prev = if (i > 0) raw[i - 1] else raw[i]
        val next = if (i < raw.lastIndex) raw[i + 1] else raw[i]
        ((prev * 0.25f) + (raw[i] * 0.5f) + (next * 0.25f)).coerceIn(0.16f, 1.0f)
    }
}
