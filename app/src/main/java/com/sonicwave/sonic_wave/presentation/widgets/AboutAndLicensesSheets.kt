package com.sonicwave.sonic_wave.presentation.widgets

import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import com.sonicwave.sonic_wave.core.l10n.AppLocalizations
import com.sonicwave.sonic_wave.designsystem.components.ObsidianConsoleCard
import com.sonicwave.sonic_wave.designsystem.components.ObsidianFilterCapsule
import com.sonicwave.sonic_wave.designsystem.components.ObsidianModalSheet
import com.sonicwave.sonic_wave.designsystem.components.ObsidianTactileButton
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianRadius
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianTheme
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianTypography
import kotlin.math.PI
import kotlin.math.sin

private data class OpenSourceLibrary(
    val name: String,
    val author: String,
    val license: String
)

private val openSourceLibraries = listOf(
    OpenSourceLibrary("Jetpack Compose & Material3", "Google / AOSP", "Apache License 2.0"),
    OpenSourceLibrary("AndroidX Media3 (ExoPlayer)", "Google / AOSP", "Apache License 2.0"),
    OpenSourceLibrary("AndroidX Room", "Google / AOSP", "Apache License 2.0"),
    OpenSourceLibrary("AndroidX Security Crypto", "Google / AOSP", "Apache License 2.0"),
    OpenSourceLibrary("OkHttp", "Square, Inc.", "Apache License 2.0"),
    OpenSourceLibrary("Coil Compose", "Coil Contributors", "Apache License 2.0"),
    OpenSourceLibrary("Gson", "Google Inc.", "Apache License 2.0"),
    OpenSourceLibrary("Kotlin Coroutines", "JetBrains s.r.o.", "Apache License 2.0")
)

private const val MIT_LICENSE_TEXT = """MIT License

Copyright (c) 2026 SonicWave Studio Contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE."""

@Composable
fun AboutAppSection() {
    val palette = ObsidianTheme.palette
    val strings = AppLocalizations.current()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var showMitSheet by remember { mutableStateOf(false) }
    var showLibsSheet by remember { mutableStateOf(false) }
    var showSecretLab by remember { mutableStateOf(false) }
    var secretTapCount by remember { mutableIntStateOf(0) }

    val versionLabel = remember(context) {
        runCatching {
            val info = if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
            "v${info.versionName ?: "1.0.0"} ($code)"
        }.getOrDefault("v1.0.0 (1)")
    }

    ObsidianConsoleCard {
        // App Identity Header (7 taps unlocks Secret Oscilloscope Lab)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(ObsidianRadius.md))
                .clickable {
                    secretTapCount += 1
                    if (secretTapCount in 4..6) {
                        val left = 7 - secretTapCount
                        Toast.makeText(
                            context,
                            strings.tr("about_easter_hint", mapOf("left" to left.toString())),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else if (secretTapCount >= 7) {
                        secretTapCount = 0
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showSecretLab = true
                    }
                }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(ObsidianRadius.md))
                    .background(palette.accentPrimary.copy(alpha = 0.16f))
                    .border(1.dp, palette.accentPrimary.copy(alpha = 0.45f), RoundedCornerShape(ObsidianRadius.md)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = palette.accentPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "SonicWave Studio", style = ObsidianTypography.headingMd(palette))
                Text(
                    text = strings.tr("about_tagline"),
                    style = ObsidianTypography.bodySm(palette)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${strings.tr("about_version")} $versionLabel • ${context.packageName}",
                    style = ObsidianTypography.technicalMono(palette, 10.5f, palette.accentPrimary)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Open-Source License & Privacy Compliance
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(ObsidianRadius.md))
                .background(palette.surfaceConsole)
                .border(1.dp, palette.hairlineBorder, RoundedCornerShape(ObsidianRadius.md))
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.VerifiedUser,
                        contentDescription = null,
                        tint = palette.statusOnline,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = strings.tr("about_license_label"),
                        style = ObsidianTypography.studioLabel(palette, palette.statusOnline)
                    )
                }
                Text(text = strings.tr("about_license_name"), style = ObsidianTypography.titleSm(palette))
                Text(text = strings.tr("about_privacy_note"), style = ObsidianTypography.bodySm(palette))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ObsidianTactileButton(
                label = strings.tr("about_view_license"),
                icon = Icons.Rounded.Description,
                modifier = Modifier.weight(1f),
                onClick = { showMitSheet = true }
            )
            ObsidianTactileButton(
                label = strings.tr("about_third_party"),
                icon = Icons.Rounded.Code,
                isPrimary = true,
                modifier = Modifier.weight(1f),
                onClick = { showLibsSheet = true }
            )
        }
    }

    if (showMitSheet) {
        ObsidianModalSheet(onDismiss = { showMitSheet = false }) {
            Text(text = strings.tr("about_license_name"), style = ObsidianTypography.headingMd(palette))
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(ObsidianRadius.md))
                    .background(palette.surfaceConsole)
                    .border(1.dp, palette.hairlineBorder, RoundedCornerShape(ObsidianRadius.md))
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = MIT_LICENSE_TEXT,
                    style = ObsidianTypography.technicalMono(palette, 11f, palette.textSecondary)
                )
            }
        }
    }

    if (showLibsSheet) {
        ObsidianModalSheet(onDismiss = { showLibsSheet = false }) {
            Text(text = strings.tr("about_third_party"), style = ObsidianTypography.headingMd(palette))
            Text(text = strings.tr("about_third_party_sub"), style = ObsidianTypography.bodySm(palette))
            Spacer(modifier = Modifier.height(10.dp))
            LazyColumn(
                modifier = Modifier.height(280.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(openSourceLibraries) { lib ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(ObsidianRadius.md))
                            .background(palette.surfaceConsole)
                            .border(1.dp, palette.hairlineBorder, RoundedCornerShape(ObsidianRadius.md))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = lib.name, style = ObsidianTypography.titleSm(palette))
                            Text(text = lib.author, style = ObsidianTypography.bodySm(palette))
                        }
                        Text(
                            text = lib.license,
                            style = ObsidianTypography.technicalMono(palette, 10f, palette.accentPrimary)
                        )
                    }
                }
            }
        }
    }

    if (showSecretLab) {
        SecretOscilloscopeSheet(onDismiss = { showSecretLab = false })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SecretOscilloscopeSheet(onDismiss: () -> Unit) {
    val palette = ObsidianTheme.palette
    val strings = AppLocalizations.current()

    var freqX by remember { mutableFloatStateOf(3f) }
    var freqY by remember { mutableFloatStateOf(2f) }
    var phase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var lastNanos = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = (now - lastNanos) / 1_000_000_000f
            lastNanos = now
            phase = (phase + dt * 2.2f) % (2f * PI.toFloat())
        }
    }

    ObsidianModalSheet(onDismiss = onDismiss) {
        Text(
            text = strings.tr("about_easter_title"),
            style = ObsidianTypography.studioLabel(palette, palette.accentPrimary)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = strings.tr("about_easter_sub"), style = ObsidianTypography.bodySm(palette))
        Spacer(modifier = Modifier.height(10.dp))

        // Interactive CRT Lissajous Oscilloscope
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(175.dp)
                .clip(RoundedCornerShape(ObsidianRadius.lg))
                .background(Color(0xFF07090E))
                .border(1.dp, palette.accentPrimary.copy(alpha = 0.45f), RoundedCornerShape(ObsidianRadius.lg))
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val nx = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        val ny = (change.position.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                        freqX = 1f + nx * 6f
                        freqY = 1f + ny * 6f
                    }
                }
        ) {
            // Subtle CRT graticule grid
            val cols = 8
            val rows = 4
            for (c in 1 until cols) {
                val x = size.width * c / cols
                drawLine(Color.White.copy(alpha = 0.06f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            }
            for (r in 1 until rows) {
                val y = size.height * r / rows
                drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }

            val path = Path()
            val steps = 260
            val cx = size.width / 2f
            val cy = size.height / 2f
            val rx = size.width * 0.42f
            val ry = size.height * 0.40f

            for (i in 0..steps) {
                val t = (i.toFloat() / steps) * 2f * PI.toFloat()
                val x = cx + rx * sin(freqX * t + phase)
                val y = cy + ry * sin(freqY * t)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            drawPath(
                path = path,
                color = palette.accentPrimary.copy(alpha = 0.28f),
                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
            )
            drawPath(
                path = path,
                color = palette.accentPrimary,
                style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "FREQ X: %.2f Hz".format(freqX * 110f),
                style = ObsidianTypography.technicalMono(palette, 11f, palette.accentPrimary)
            )
            Text(
                text = "FREQ Y: %.2f Hz".format(freqY * 110f),
                style = ObsidianTypography.technicalMono(palette, 11f, palette.statusOnline)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ObsidianFilterCapsule(label = "HARMONIC 3:2", selected = false, onClick = { freqX = 3f; freqY = 2f })
            ObsidianFilterCapsule(label = "808 SUB 5:4", selected = false, onClick = { freqX = 5f; freqY = 4f })
            ObsidianFilterCapsule(label = "WARP 7:3", selected = false, onClick = { freqX = 7f; freqY = 3f })
        }
    }
}
