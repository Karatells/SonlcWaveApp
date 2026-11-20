package com.sonicwave.sonic_wave.core.utils

import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.sonicwave.sonic_wave.domain.models.ServerProfile
import com.sonicwave.sonic_wave.domain.models.ServerProtocol
import java.nio.charset.StandardCharsets
import kotlin.math.abs

object ServerQrPayloadCodec {
    private const val SCHEME_PREFIX = "sonicwave://server?data="
    private val gson = Gson()

    fun encodeProfile(profile: ServerProfile, includePassword: Boolean = true): String {
        val obj = JsonObject().apply {
            addProperty("v", 1)
            addProperty("n", profile.name)
            addProperty("p", when (profile.protocol) {
                ServerProtocol.OPEN_SUBSONIC -> "openSubsonic"
                ServerProtocol.NAVIDROME -> "navidrome"
                ServerProtocol.JELLYFIN -> "jellyfin"
                ServerProtocol.CUSTOM_REST -> "customRest"
            })
            addProperty("u", profile.primaryUrl)
            if (!profile.lanUrl.isNullOrBlank()) {
                addProperty("l", profile.lanUrl)
            }
            addProperty("usr", profile.username)
            if (includePassword && profile.secretOrPassword.isNotBlank()) {
                addProperty("pwd", profile.secretOrPassword)
            }
            addProperty("ssl", if (profile.allowSelfSignedSsl) 1 else 0)
        }
        val jsonBytes = gson.toJson(obj).toByteArray(StandardCharsets.UTF_8)
        val b64 = Base64.encodeToString(jsonBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return "$SCHEME_PREFIX$b64"
    }

    fun tryDecodeProfile(raw: String): ServerProfile? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return try {
            val jsonString = when {
                trimmed.startsWith("{") -> trimmed
                trimmed.startsWith(SCHEME_PREFIX) -> {
                    val b64 = trimmed.removePrefix(SCHEME_PREFIX)
                    String(Base64.decode(b64, Base64.URL_SAFE or Base64.NO_WRAP), StandardCharsets.UTF_8)
                }
                trimmed.startsWith("sonicwave://") -> {
                    val idx = trimmed.indexOf("data=")
                    if (idx < 0) return null
                    val b64 = trimmed.substring(idx + 5).substringBefore("&")
                    String(Base64.decode(b64, Base64.URL_SAFE or Base64.NO_WRAP), StandardCharsets.UTF_8)
                }
                else -> {
                    String(Base64.decode(trimmed, Base64.URL_SAFE or Base64.NO_WRAP), StandardCharsets.UTF_8)
                }
            }

            val obj = gson.fromJson(jsonString, JsonObject::class.java) ?: return null
            val url = obj.get("u")?.asString?.trim().orEmpty()
            if (url.isEmpty()) return null

            val name = obj.get("n")?.asString?.trim().orEmpty().ifEmpty { "SonicWave Server" }
            val protocol = ServerProtocol.fromName(obj.get("p")?.asString)
            val lanUrl = obj.get("l")?.asString?.trim()?.takeIf { it.isNotEmpty() }
            val username = obj.get("usr")?.asString?.trim().orEmpty()
            val password = obj.get("pwd")?.asString ?: ""
            val ssl = (obj.get("ssl")?.asInt ?: 0) == 1

            ServerProfile(
                id = "srv_${System.currentTimeMillis()}",
                name = name,
                protocol = protocol,
                primaryUrl = url,
                lanUrl = lanUrl,
                username = username,
                secretOrPassword = password,
                allowSelfSignedSsl = ssl,
                isActive = true
            )
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Pure-Kotlin ISO/IEC 18004 QR matrix generator (Byte Mode, ECC Level L, Versions 1..20).
 */
object QrMatrixGenerator {
    private val eccTableL = arrayOf(
        intArrayOf(1, 19, 7, 1, 19, 0, 0),
        intArrayOf(2, 34, 10, 1, 34, 0, 0),
        intArrayOf(3, 55, 15, 1, 55, 0, 0),
        intArrayOf(4, 80, 20, 1, 80, 0, 0),
        intArrayOf(5, 108, 26, 1, 108, 0, 0),
        intArrayOf(6, 136, 18, 2, 68, 0, 0),
        intArrayOf(7, 156, 20, 2, 78, 0, 0),
        intArrayOf(8, 194, 24, 2, 97, 0, 0),
        intArrayOf(9, 232, 30, 2, 116, 0, 0),
        intArrayOf(10, 274, 18, 2, 68, 2, 69),
        intArrayOf(11, 324, 20, 4, 81, 0, 0),
        intArrayOf(12, 370, 24, 2, 92, 2, 93),
        intArrayOf(13, 428, 26, 4, 107, 0, 0),
        intArrayOf(14, 461, 30, 3, 115, 1, 116),
        intArrayOf(15, 523, 22, 5, 87, 1, 88),
        intArrayOf(16, 589, 24, 5, 98, 1, 99),
        intArrayOf(17, 647, 28, 1, 107, 5, 108),
        intArrayOf(18, 721, 30, 5, 120, 1, 121),
        intArrayOf(19, 795, 28, 3, 113, 4, 114),
        intArrayOf(20, 861, 28, 3, 107, 5, 108)
    )

    private val alignmentPositions = arrayOf(
        intArrayOf(),
        intArrayOf(6, 18),
        intArrayOf(6, 22),
        intArrayOf(6, 26),
        intArrayOf(6, 30),
        intArrayOf(6, 34),
        intArrayOf(6, 22, 38),
        intArrayOf(6, 24, 42),
        intArrayOf(6, 26, 46),
        intArrayOf(6, 28, 50),
        intArrayOf(6, 30, 54),
        intArrayOf(6, 32, 58),
        intArrayOf(6, 34, 62),
        intArrayOf(6, 26, 46, 66),
        intArrayOf(6, 26, 48, 70),
        intArrayOf(6, 26, 50, 74),
        intArrayOf(6, 30, 54, 78),
        intArrayOf(6, 30, 56, 82),
        intArrayOf(6, 30, 58, 86),
        intArrayOf(6, 34, 62, 90)
    )

    private val gfExp = IntArray(512)
    private val gfLog = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            gfExp[i] = x
            gfLog[x] = i
            x = x shl 1
            if (x and 0x100 != 0) x = x xor 0x11D
        }
        for (i in 255 until 512) {
            gfExp[i] = gfExp[i - 255]
        }
    }

    private fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return gfExp[(gfLog[a] + gfLog[b]) % 255]
    }

    private fun rsGeneratorPoly(degree: Int): IntArray {
        var poly = intArrayOf(1)
        for (i in 0 until degree) {
            val next = IntArray(poly.size + 1)
            val factor = gfExp[i]
            for (j in poly.indices) {
                next[j] = next[j] xor poly[j]
                next[j + 1] = next[j + 1] xor gfMul(poly[j], factor)
            }
            poly = next
        }
        return poly
    }

    private fun rsEncode(data: IntArray, ecCount: Int): IntArray {
        val gen = rsGeneratorPoly(ecCount)
        val res = IntArray(data.size + ecCount)
        System.arraycopy(data, 0, res, 0, data.size)
        for (i in data.indices) {
            val coef = res[i]
            if (coef != 0) {
                for (j in gen.indices) {
                    res[i + j] = res[i + j] xor gfMul(gen[j], coef)
                }
            }
        }
        return res.copyOfRange(data.size, res.size)
    }

    fun generate(text: String): Array<BooleanArray> {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        val spec = eccTableL.firstOrNull { row ->
            val version = row[0]
            val countBits = if (version <= 9) 8 else 16
            val neededBits = 4 + countBits + bytes.size * 8
            neededBits <= row[1] * 8
        } ?: eccTableL.last()

        val version = spec[0]
        val totalDataCodewords = spec[1]
        val ecPerBlock = spec[2]
        val g1Blocks = spec[3]
        val g1DataLen = spec[4]
        val g2Blocks = spec[5]
        val g2DataLen = spec[6]

        val bits = ArrayList<Int>(totalDataCodewords * 8)
        fun pushBits(value: Int, count: Int) {
            for (i in count - 1 downTo 0) {
                bits.add((value ushr i) and 1)
            }
        }

        // Byte mode indicator 0100
        pushBits(0b0100, 4)
        val countBits = if (version <= 9) 8 else 16
        pushBits(bytes.size.coerceAtMost((1 shl countBits) - 1), countBits)
        for (b in bytes) {
            if (bits.size + 8 > totalDataCodewords * 8) break
            pushBits(b.toInt() and 0xFF, 8)
        }
        // Terminator
        val maxBits = totalDataCodewords * 8
        repeat(minOf(4, maxBits - bits.size)) { bits.add(0) }
        while (bits.size % 8 != 0) bits.add(0)

        val dataCodewords = IntArray(totalDataCodewords)
        val byteCount = bits.size / 8
        for (i in 0 until byteCount) {
            var v = 0
            for (b in 0 until 8) {
                v = (v shl 1) or bits[i * 8 + b]
            }
            dataCodewords[i] = v
        }
        val padBytes = intArrayOf(0xEC, 0x11)
        var padIdx = 0
        for (i in byteCount until totalDataCodewords) {
            dataCodewords[i] = padBytes[padIdx % 2]
            padIdx++
        }

        // Split into blocks and compute ECC
        val blocks = ArrayList<IntArray>()
        val ecBlocks = ArrayList<IntArray>()
        var offset = 0
        repeat(g1Blocks) {
            val block = dataCodewords.copyOfRange(offset, offset + g1DataLen)
            blocks.add(block)
            ecBlocks.add(rsEncode(block, ecPerBlock))
            offset += g1DataLen
        }
        repeat(g2Blocks) {
            val block = dataCodewords.copyOfRange(offset, offset + g2DataLen)
            blocks.add(block)
            ecBlocks.add(rsEncode(block, ecPerBlock))
            offset += g2DataLen
        }

        val finalBytes = ArrayList<Int>()
        val maxDataLen = maxOf(g1DataLen, g2DataLen)
        for (i in 0 until maxDataLen) {
            for (block in blocks) {
                if (i < block.size) finalBytes.add(block[i])
            }
        }
        for (i in 0 until ecPerBlock) {
            for (ec in ecBlocks) {
                finalBytes.add(ec[i])
            }
        }

        val size = version * 4 + 17
        val modules = Array(size) { BooleanArray(size) }
        val isFunction = Array(size) { BooleanArray(size) }

        fun setFunc(r: Int, c: Int, v: Boolean) {
            if (r in 0 until size && c in 0 until size) {
                modules[r][c] = v
                isFunction[r][c] = true
            }
        }

        fun drawFinder(top: Int, left: Int) {
            for (dr in -1..7) {
                for (dc in -1..7) {
                    val r = top + dr
                    val c = left + dc
                    if (r !in 0 until size || c !in 0 until size) continue
                    val on = dr in 0..6 && dc in 0..6 &&
                        (dr == 0 || dr == 6 || dc == 0 || dc == 6 || (dr in 2..4 && dc in 2..4))
                    setFunc(r, c, on)
                }
            }
        }

        drawFinder(0, 0)
        drawFinder(0, size - 7)
        drawFinder(size - 7, 0)

        // Timing patterns
        for (i in 8 until size - 8) {
            val dark = i % 2 == 0
            setFunc(6, i, dark)
            setFunc(i, 6, dark)
        }

        // Alignment patterns
        val align = alignmentPositions[version - 1]
        for (rCenter in align) {
            for (cCenter in align) {
                if (isFunction[rCenter][cCenter]) continue
                for (dr in -2..2) {
                    for (dc in -2..2) {
                        val on = abs(dr) == 2 || abs(dc) == 2 || (dr == 0 && dc == 0)
                        setFunc(rCenter + dr, cCenter + dc, on)
                    }
                }
            }
        }

        // Dark module & reserve format info
        setFunc(size - 8, 8, true)
        for (i in 0..8) {
            if (i != 6) {
                isFunction[8][i] = true
                isFunction[i][8] = true
            }
        }
        for (i in 0..7) {
            isFunction[8][size - 1 - i] = true
            if (i < 7) isFunction[size - 1 - i][8] = true
        }
        if (version >= 7) {
            for (i in 0 until 6) {
                for (j in 0 until 3) {
                    isFunction[i][size - 11 + j] = true
                    isFunction[size - 11 + j][i] = true
                }
            }
        }

        // Place data bits
        var bitIndex = 0
        val totalBits = finalBytes.size * 8
        var col = size - 1
        var upward = true
        while (col > 0) {
            if (col == 6) col--
            for (step in 0 until size) {
                val r = if (upward) size - 1 - step else step
                for (dc in 0..1) {
                    val c = col - dc
                    if (!isFunction[r][c]) {
                        val bit = if (bitIndex < totalBits) {
                            ((finalBytes[bitIndex / 8] ushr (7 - (bitIndex % 8))) and 1) == 1
                        } else false
                        modules[r][c] = bit
                        bitIndex++
                    }
                }
            }
            upward = !upward
            col -= 2
        }

        // Evaluate 8 masks and pick best
        var bestMask = 0
        var bestPenalty = Int.MAX_VALUE
        for (mask in 0 until 8) {
            val candidate = Array(size) { r ->
                BooleanArray(size) { c ->
                    val raw = modules[r][c]
                    if (isFunction[r][c]) raw else (raw xor maskBit(mask, r, c))
                }
            }
            applyFormatBits(candidate, mask, size)
            val penalty = computePenalty(candidate, size)
            if (penalty < bestPenalty) {
                bestPenalty = penalty
                bestMask = mask
            }
        }

        val finalMatrix = Array(size) { r ->
            BooleanArray(size) { c ->
                val raw = modules[r][c]
                if (isFunction[r][c]) raw else (raw xor maskBit(bestMask, r, c))
            }
        }
        applyFormatBits(finalMatrix, bestMask, size)
        return finalMatrix
    }

    private fun maskBit(mask: Int, r: Int, c: Int): Boolean = when (mask) {
        0 -> (r + c) % 2 == 0
        1 -> r % 2 == 0
        2 -> c % 3 == 0
        3 -> (r + c) % 3 == 0
        4 -> ((r / 2) + (c / 3)) % 2 == 0
        5 -> (r * c) % 2 + (r * c) % 3 == 0
        6 -> ((r * c) % 2 + (r * c) % 3) % 2 == 0
        else -> ((r + c) % 2 + (r * c) % 3) % 2 == 0
    }

    private fun applyFormatBits(matrix: Array<BooleanArray>, mask: Int, size: Int) {
        // ECC Level L = 01
        val data = (0b01 shl 3) or mask
        var rem = data
        repeat(10) { rem = (rem shl 1) xor (((rem ushr 9) and 1) * 0x537) }
        val bits = ((data shl 10) or rem) xor 0x5412

        for (i in 0..5) matrix[8][i] = ((bits ushr i) and 1) == 1
        matrix[8][7] = ((bits ushr 6) and 1) == 1
        matrix[8][8] = ((bits ushr 7) and 1) == 1
        matrix[7][8] = ((bits ushr 8) and 1) == 1
        for (i in 9..14) matrix[14 - i][8] = ((bits ushr i) and 1) == 1

        for (i in 0..7) matrix[size - 1 - i][8] = ((bits ushr i) and 1) == 1
        for (i in 8..14) matrix[8][size - 15 + i] = ((bits ushr i) and 1) == 1
    }

    private fun computePenalty(matrix: Array<BooleanArray>, size: Int): Int {
        var penalty = 0
        for (r in 0 until size) {
            var run = 1
            for (c in 1 until size) {
                if (matrix[r][c] == matrix[r][c - 1]) {
                    run++
                    if (run == 5) penalty += 3 else if (run > 5) penalty += 1
                } else run = 1
            }
        }
        for (c in 0 until size) {
            var run = 1
            for (r in 1 until size) {
                if (matrix[r][c] == matrix[r - 1][c]) {
                    run++
                    if (run == 5) penalty += 3 else if (run > 5) penalty += 1
                } else run = 1
            }
        }
        for (r in 0 until size - 1) {
            for (c in 0 until size - 1) {
                val b = matrix[r][c]
                if (b == matrix[r][c + 1] && b == matrix[r + 1][c] && b == matrix[r + 1][c + 1]) {
                    penalty += 3
                }
            }
        }
        return penalty
    }
}

@Composable
fun QrCodeView(
    data: String,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    foregroundColor: Color = Color(0xFF090A0F),
    backgroundColor: Color = Color.White
) {
    val matrix = remember(data) { QrMatrixGenerator.generate(data) }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .padding(12.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val count = matrix.size
            val cellSize = this.size.width / count.toFloat()
            for (r in 0 until count) {
                for (c in 0 until count) {
                    if (matrix[r][c]) {
                        drawRect(
                            color = foregroundColor,
                            topLeft = Offset(c * cellSize, r * cellSize),
                            size = Size(cellSize + 0.5f, cellSize + 0.5f)
                        )
                    }
                }
            }
        }
    }
}
