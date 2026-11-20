package com.sonicwave.sonic_wave.designsystem.painters

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianPalette
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianTheme
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianTypography
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun AcousticMeshSurface(
    modifier: Modifier = Modifier,
    overrideOrbColor: Color? = null,
    glowIntensity: Float = 0.32f
) {
    val palette = ObsidianTheme.palette
    val transition = rememberInfiniteTransition(label = "mesh")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "meshPhase"
    )

    val orb1Color = overrideOrbColor ?: palette.accentPrimary
    val orb2Color = palette.accentSecondary

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(color = palette.surfaceVoid)
        val w = size.width
        val h = size.height
        val maxDim = max(w, h)

        val alpha1 = (if (palette.isDark) 0.26f else 0.16f) * glowIntensity
        val c1 = Offset(
            w * (0.22f + 0.08f * sin(phase)),
            h * (0.16f + 0.05f * cos(phase * 0.7f))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to orb1Color.copy(alpha = alpha1),
                    0.52f to orb1Color.copy(alpha = alpha1 * 0.35f),
                    1.0f to Color.Transparent
                ),
                center = c1,
                radius = maxDim * 0.68f
            ),
            radius = maxDim * 0.68f,
            center = c1
        )

        val alpha2 = (if (palette.isDark) 0.18f else 0.11f) * glowIntensity
        val c2 = Offset(
            w * (0.82f - 0.07f * cos(phase)),
            h * (0.48f + 0.06f * sin(phase * 0.9f))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to orb2Color.copy(alpha = alpha2),
                    0.52f to orb2Color.copy(alpha = alpha2 * 0.35f),
                    1.0f to Color.Transparent
                ),
                center = c2,
                radius = maxDim * 0.58f
            ),
            radius = maxDim * 0.58f,
            center = c2
        )
    }
}

@Composable
fun VinylGrooveDisc(
    modifier: Modifier = Modifier,
    accentColor: Color = ObsidianTheme.palette.accentPrimary,
    isDark: Boolean = ObsidianTheme.palette.isDark,
    rotationDegrees: Float = 0f
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxR = size.minDimension / 2f

        val discColors = if (isDark) {
            listOf(Color(0xFF1E222E), Color(0xFF0E1017), Color(0xFF07080C))
        } else {
            listOf(Color(0xFF2B2E3B), Color(0xFF181A24), Color(0xFF0E1017))
        }
        drawCircle(
            brush = Brush.radialGradient(colors = discColors, center = center, radius = maxR),
            radius = maxR,
            center = center
        )

        rotate(degrees = rotationDegrees, pivot = center) {
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = if (isDark) 0.06f else 0.05f),
                        accentColor.copy(alpha = 0.12f),
                        Color.Transparent,
                        Color.White.copy(alpha = 0.04f),
                        Color.Transparent
                    ),
                    center = center
                ),
                radius = maxR,
                center = center
            )
        }

        var r = maxR * 0.36f
        val endR = maxR * 0.94f
        while (r <= endR) {
            drawCircle(
                color = Color.White.copy(alpha = 0.055f),
                radius = r,
                center = center,
                style = Stroke(width = 0.7f)
            )
            r += 3.6f
        }

        drawCircle(
            color = accentColor.copy(alpha = 0.35f),
            radius = maxR - 1f,
            center = center,
            style = Stroke(width = 1.2f)
        )

        drawCircle(
            color = accentColor.copy(alpha = 0.85f),
            radius = maxR * 0.26f,
            center = center
        )

        drawCircle(
            color = Color(0xFF090A0F),
            radius = maxR * 0.065f,
            center = center
        )
    }
}

enum class ObsidianDockIconType {
    CONSOLE_HOME,
    LIBRARY_VAULT,
    SONIC_SEARCH,
    SERVER_RACK
}

@Composable
fun ObsidianDockIcon(
    type: ObsidianDockIconType,
    activeProgress: Float,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier.size(22.dp)
) {
    Canvas(modifier = modifier) {
        val color = lerp(inactiveColor, activeColor, activeProgress)
        val strokeWidth = (1.65f + 0.35f * activeProgress) * density
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        val fillAlpha = 0.18f * activeProgress
        val w = size.width
        val h = size.height

        when (type) {
            ObsidianDockIconType.CONSOLE_HOME -> {
                val rectSize = Size(w * 0.76f, h * 0.68f)
                val topLeft = Offset(w * 0.12f, h * 0.16f)
                if (activeProgress > 0.05f) {
                    drawRoundRect(
                        color = activeColor.copy(alpha = fillAlpha),
                        topLeft = topLeft,
                        size = rectSize,
                        cornerRadius = CornerRadius(w * 0.14f)
                    )
                }
                drawRoundRect(
                    color = color,
                    topLeft = topLeft,
                    size = rectSize,
                    cornerRadius = CornerRadius(w * 0.14f),
                    style = stroke
                )
                drawCircle(color = color, radius = w * 0.14f, center = Offset(w * 0.5f, h * 0.45f), style = stroke)
                drawLine(color = color, start = Offset(w * 0.32f, h * 0.72f), end = Offset(w * 0.68f, h * 0.72f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
            }
            ObsidianDockIconType.LIBRARY_VAULT -> {
                val sleeve = Size(w * 0.58f, h * 0.68f)
                val topLeft = Offset(w * 0.12f, h * 0.16f)
                if (activeProgress > 0.05f) {
                    drawRoundRect(
                        color = activeColor.copy(alpha = fillAlpha),
                        topLeft = topLeft,
                        size = sleeve,
                        cornerRadius = CornerRadius(w * 0.1f)
                    )
                }
                drawRoundRect(
                    color = color,
                    topLeft = topLeft,
                    size = sleeve,
                    cornerRadius = CornerRadius(w * 0.1f),
                    style = stroke
                )
                drawCircle(
                    color = color,
                    radius = w * 0.20f,
                    center = Offset(w * 0.72f, h * 0.52f),
                    style = stroke
                )
                drawCircle(color = color, radius = w * 0.05f, center = Offset(w * 0.72f, h * 0.52f))
            }
            ObsidianDockIconType.SONIC_SEARCH -> {
                val center = Offset(w * 0.44f, h * 0.44f)
                val radius = w * 0.28f
                if (activeProgress > 0.05f) {
                    drawCircle(color = activeColor.copy(alpha = fillAlpha), radius = radius, center = center)
                }
                drawCircle(color = color, radius = radius, center = center, style = stroke)
                val path = Path().apply {
                    moveTo(w * 0.28f, h * 0.44f)
                    lineTo(w * 0.38f, h * 0.34f)
                    lineTo(w * 0.48f, h * 0.54f)
                    lineTo(w * 0.60f, h * 0.44f)
                }
                drawPath(path = path, color = color, style = stroke)
                drawLine(
                    color = color,
                    start = Offset(w * 0.65f, h * 0.65f),
                    end = Offset(w * 0.86f, h * 0.86f),
                    strokeWidth = strokeWidth * 1.15f,
                    cap = StrokeCap.Round
                )
            }
            ObsidianDockIconType.SERVER_RACK -> {
                val rackW = w * 0.76f
                val rackH = h * 0.28f
                val top1 = Offset(w * 0.12f, h * 0.18f)
                val top2 = Offset(w * 0.12f, h * 0.54f)
                if (activeProgress > 0.05f) {
                    drawRoundRect(color = activeColor.copy(alpha = fillAlpha), topLeft = top1, size = Size(rackW, rackH), cornerRadius = CornerRadius(w * 0.08f))
                    drawRoundRect(color = activeColor.copy(alpha = fillAlpha), topLeft = top2, size = Size(rackW, rackH), cornerRadius = CornerRadius(w * 0.08f))
                }
                drawRoundRect(color = color, topLeft = top1, size = Size(rackW, rackH), cornerRadius = CornerRadius(w * 0.08f), style = stroke)
                drawRoundRect(color = color, topLeft = top2, size = Size(rackW, rackH), cornerRadius = CornerRadius(w * 0.08f), style = stroke)
                drawCircle(color = color, radius = w * 0.045f, center = Offset(w * 0.26f, h * 0.32f))
                drawCircle(color = color, radius = w * 0.045f, center = Offset(w * 0.26f, h * 0.68f))
            }
        }
    }
}

@Composable
fun ActiveTrackEqBars(
    isPlaying: Boolean,
    color: Color = ObsidianTheme.palette.accentPrimary,
    size: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "eqBars")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "eqPhase"
    )

    Canvas(modifier = modifier.size(size)) {
        val barCount = 4
        val barWidth = this.size.width / (barCount * 1.75f)
        val gap = (this.size.width - barCount * barWidth) / (barCount - 1)

        for (i in 0 until barCount) {
            val factor = if (isPlaying) {
                0.28f + 0.70f * ((sin(phase + i * 1.35f) + 1f) / 2f)
            } else {
                0.25f
            }
            val barH = (this.size.height * factor).coerceIn(3f, this.size.height)
            val x = i * (barWidth + gap)
            val y = this.size.height - barH
            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2f)
            )
        }
    }
}

@Composable
fun StorageAllocationBar(
    usedRatio: Float,
    pinnedRatio: Float,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(22.dp),
    palette: ObsidianPalette = ObsidianTheme.palette
) {
    Canvas(modifier = modifier) {
        val segments = 36
        val gap = 3.dp.toPx()
        val segW = (size.width - gap * (segments - 1)) / segments
        val segH = size.height * 0.62f
        val top = (size.height - segH) / 2f

        for (i in 0 until segments) {
            val ratio = (i + 0.5f) / segments
            val color = when {
                ratio <= pinnedRatio -> palette.accentPrimary
                ratio <= pinnedRatio + usedRatio -> palette.accentSecondary
                else -> palette.hairlineBorder
            }
            val x = i * (segW + gap)
            if (ratio <= pinnedRatio + usedRatio) {
                drawRoundRect(
                    color = color.copy(alpha = 0.25f),
                    topLeft = Offset(x - 1f, top - 1f),
                    size = Size(segW + 2f, segH + 2f),
                    cornerRadius = CornerRadius(3.dp.toPx())
                )
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(x, top),
                size = Size(segW, segH),
                cornerRadius = CornerRadius(2.5.dp.toPx())
            )
        }
    }
}

@Composable
fun WaveformSeekBar(
    peaks: List<Float>,
    progressFraction: Float,
    bufferedFraction: Float,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    loopStartMs: Long? = null,
    loopEndMs: Long? = null
) {
    val palette = ObsidianTheme.palette
    val haptic = LocalHapticFeedback.current
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(progressFraction) }
    val activeFraction = if (isDragging) dragFraction else progressFraction

    val transition = rememberInfiniteTransition(label = "wavePulse")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
    ) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }.coerceAtLeast(1f)

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        val frac = (offset.x / widthPx).coerceIn(0f, 1f)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSeek((durationMs * frac).toLong())
                    }
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragFraction = (offset.x / widthPx).coerceIn(0f, 1f)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragFraction = (change.position.x / widthPx).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            isDragging = false
                            onSeek((durationMs * dragFraction).toLong())
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onDragCancel = {
                            isDragging = false
                        }
                    )
                }
        ) {
            val count = peaks.size.coerceAtLeast(1)
            val gap = 2.dp.toPx()
            val barW = ((size.width - gap * (count - 1)) / count).coerceAtLeast(1.5f)
            val centerY = size.height / 2f
            val maxBarH = size.height * 0.78f

            for (i in 0 until count) {
                val barFrac = i.toFloat() / count.toFloat()
                val basePeak = peaks[i].coerceIn(0.15f, 1.0f)
                val distFromCursor = abs(barFrac - activeFraction)
                val lensBoost = if (isDragging && distFromCursor < 0.08f) {
                    1f + (1f - distFromCursor / 0.08f) * 0.24f
                } else {
                    1f + 0.03f * sin(pulse + i * 0.4f)
                }
                val h = (basePeak * maxBarH * lensBoost).coerceAtMost(size.height * 0.92f)
                val x = i * (barW + gap)

                val barColor = when {
                    barFrac <= activeFraction -> lerp(palette.accentPrimary, palette.accentSecondary, barFrac)
                    barFrac <= bufferedFraction -> palette.textMuted.copy(alpha = 0.55f)
                    else -> palette.hairlineBorder.copy(alpha = if (palette.isDark) 0.35f else 0.55f)
                }

                if (barFrac <= activeFraction) {
                    drawRoundRect(
                        color = palette.accentPrimary.copy(alpha = 0.22f),
                        topLeft = Offset(x - 0.5f, centerY - h / 2f - 1f),
                        size = Size(barW + 1f, h + 2f),
                        cornerRadius = CornerRadius(barW / 2f)
                    )
                }

                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, centerY - h / 2f),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(barW / 2f)
                )
            }

            // A-B Loop overlay
            if (durationMs > 0L && loopStartMs != null) {
                val aX = ((loopStartMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)) * size.width
                if (loopEndMs != null && loopEndMs > loopStartMs) {
                    val bX = ((loopEndMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)) * size.width
                    drawRoundRect(
                        color = palette.statusOnline.copy(alpha = 0.14f),
                        topLeft = Offset(aX, 2.dp.toPx()),
                        size = Size((bX - aX).coerceAtLeast(2f), size.height - 4.dp.toPx()),
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )
                    drawLine(
                        color = palette.statusOnline,
                        start = Offset(bX, 2.dp.toPx()),
                        end = Offset(bX, size.height - 2.dp.toPx()),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                drawLine(
                    color = palette.statusOnline,
                    start = Offset(aX, 2.dp.toPx()),
                    end = Offset(aX, size.height - 2.dp.toPx()),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Playhead needle
            val headX = (activeFraction * size.width).coerceIn(2f, size.width - 2f)
            drawLine(
                color = palette.textPrimary,
                start = Offset(headX, 4.dp.toPx()),
                end = Offset(headX, size.height - 4.dp.toPx()),
                strokeWidth = if (isDragging) 3.dp.toPx() else 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        if (isDragging) {
            val scrubMs = (durationMs * dragFraction).toLong()
            val tooltipX = (dragFraction * widthPx).roundToInt().coerceIn(40, (widthPx - 80).roundToInt().coerceAtLeast(40))
            Box(
                modifier = Modifier
                    .offset { IntOffset(tooltipX - 28, -28) }
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.surfaceElevated)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = formatDurationMs(scrubMs),
                    style = ObsidianTypography.technicalMono(palette, 11f, palette.accentPrimary)
                )
            }
        }
    }
}

fun formatDurationMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
