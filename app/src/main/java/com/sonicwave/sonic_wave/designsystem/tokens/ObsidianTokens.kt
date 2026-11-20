package com.sonicwave.sonic_wave.designsystem.tokens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import com.sonicwave.sonic_wave.domain.services.ObsidianThemeMode

@Immutable
data class ObsidianPalette(
    val isDark: Boolean,
    val surfaceVoid: Color,
    val surfaceChassis: Color,
    val surfaceConsole: Color,
    val surfaceElevated: Color,
    val surfaceGlass: Color,
    val hairlineBorder: Color,
    val specularHighlight: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accentPrimary: Color,
    val accentSecondary: Color,
    val ambientGlow: Color,
    val statusOnline: Color,
    val statusWarning: Color,
    val statusDanger: Color
) {
    companion object {
        val DefaultDarkSeed = Color(0xFFE5A93C)
        val DefaultLightSeed = Color(0xFFC87D28)

        fun harmonizeTrackAccent(rawSeed: Color, isDark: Boolean): Color {
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(rawSeed.toArgb(), hsl)
            hsl[1] = hsl[1].coerceIn(0.48f, 0.88f)
            hsl[2] = if (isDark) hsl[2].coerceIn(0.58f, 0.74f) else hsl[2].coerceIn(0.32f, 0.46f)
            return Color(ColorUtils.HSLToColor(hsl))
        }

        fun harmonizeSecondaryAccent(primary: Color, isDark: Boolean): Color {
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(primary.toArgb(), hsl)
            hsl[0] = (hsl[0] + 38f) % 360f
            hsl[1] = (hsl[1] * 0.92f).coerceIn(0.45f, 0.85f)
            hsl[2] = if (isDark) (hsl[2] + 0.06f).coerceIn(0.55f, 0.78f) else (hsl[2] - 0.05f).coerceIn(0.28f, 0.45f)
            return Color(ColorUtils.HSLToColor(hsl))
        }

        fun obsidianDark(seedColor: Color = DefaultDarkSeed): ObsidianPalette {
            val primary = harmonizeTrackAccent(seedColor, isDark = true)
            val secondary = harmonizeSecondaryAccent(primary, isDark = true)
            return ObsidianPalette(
                isDark = true,
                surfaceVoid = Color(0xFF090A0F),
                surfaceChassis = Color(0xFF11131A),
                surfaceConsole = Color(0xFF191C26),
                surfaceElevated = Color(0xFF222634),
                surfaceGlass = Color(0xCC131620),
                hairlineBorder = Color(0x18FFFFFF),
                specularHighlight = Color(0x29FFFFFF),
                textPrimary = Color(0xFFF3F5FA),
                textSecondary = Color(0xFFA6AEBF),
                textMuted = Color(0xFF687085),
                accentPrimary = primary,
                accentSecondary = secondary,
                ambientGlow = primary.copy(alpha = 0.28f),
                statusOnline = Color(0xFF3CE5A0),
                statusWarning = Color(0xFFF5B041),
                statusDanger = Color(0xFFFF5A67)
            )
        }

        fun pureOled(seedColor: Color = DefaultDarkSeed): ObsidianPalette {
            val primary = harmonizeTrackAccent(seedColor, isDark = true)
            val secondary = harmonizeSecondaryAccent(primary, isDark = true)
            return ObsidianPalette(
                isDark = true,
                surfaceVoid = Color(0xFF000000),
                surfaceChassis = Color(0xFF07080C),
                surfaceConsole = Color(0xFF0E1017),
                surfaceElevated = Color(0xFF171A24),
                surfaceGlass = Color(0xD9050609),
                hairlineBorder = Color(0x18FFFFFF),
                specularHighlight = Color(0x29FFFFFF),
                textPrimary = Color(0xFFF3F5FA),
                textSecondary = Color(0xFFA6AEBF),
                textMuted = Color(0xFF687085),
                accentPrimary = primary,
                accentSecondary = secondary,
                ambientGlow = primary.copy(alpha = 0.24f),
                statusOnline = Color(0xFF3CE5A0),
                statusWarning = Color(0xFFF5B041),
                statusDanger = Color(0xFFFF5A67)
            )
        }

        fun warmAlabaster(seedColor: Color = DefaultLightSeed): ObsidianPalette {
            val primary = harmonizeTrackAccent(seedColor, isDark = false)
            val secondary = harmonizeSecondaryAccent(primary, isDark = false)
            return ObsidianPalette(
                isDark = false,
                surfaceVoid = Color(0xFFF6F4EF),
                surfaceChassis = Color(0xFFEEEBE3),
                surfaceConsole = Color(0xFFE5E1D7),
                surfaceElevated = Color(0xFFDAD5C9),
                surfaceGlass = Color(0xD9F4F1EA),
                hairlineBorder = Color(0x1F181A20),
                specularHighlight = Color(0x80FFFFFF),
                textPrimary = Color(0xFF14161D),
                textSecondary = Color(0xFF4E5361),
                textMuted = Color(0xFF7D8290),
                accentPrimary = primary,
                accentSecondary = secondary,
                ambientGlow = primary.copy(alpha = 0.18f),
                statusOnline = Color(0xFF169B62),
                statusWarning = Color(0xFFD48317),
                statusDanger = Color(0xFFD93848)
            )
        }
    }
}

object ObsidianSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 44.dp
    val dockClearance = 156.dp
}

object ObsidianRadius {
    val xs = 6.dp
    val sm = 10.dp
    val md = 14.dp
    val lg = 20.dp
    val xl = 26.dp
    val xxl = 34.dp
    val capsule = 999.dp
}

object ObsidianTypography {
    fun displayHero(p: ObsidianPalette) = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.8).sp,
        lineHeight = 32.sp,
        color = p.textPrimary
    )

    fun headingLg(p: ObsidianPalette) = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
        lineHeight = 26.sp,
        color = p.textPrimary
    )

    fun headingMd(p: ObsidianPalette) = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.3).sp,
        lineHeight = 21.sp,
        color = p.textPrimary
    )

    fun titleSm(p: ObsidianPalette) = TextStyle(
        fontSize = 14.5.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.15).sp,
        lineHeight = 18.5.sp,
        color = p.textPrimary
    )

    fun bodyMd(p: ObsidianPalette) = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 19.5.sp,
        color = p.textSecondary
    )

    fun bodySm(p: ObsidianPalette) = TextStyle(
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.05.sp,
        lineHeight = 17.sp,
        color = p.textSecondary
    )

    fun studioLabel(p: ObsidianPalette, color: Color = p.textMuted) = TextStyle(
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.35.sp,
        lineHeight = 13.sp,
        color = color
    )

    fun technicalMono(p: ObsidianPalette, sizeSp: Float = 11.5f, color: Color = p.textSecondary) = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = sizeSp.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.4.sp,
        lineHeight = (sizeSp * 1.2f).sp,
        fontFeatureSettings = "tnum, zero",
        color = color
    )
}

val LocalObsidianPalette = staticCompositionLocalOf { ObsidianPalette.obsidianDark() }

object ObsidianTheme {
    val palette: ObsidianPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalObsidianPalette.current
}

@Composable
fun SonicWaveTheme(
    themeMode: ObsidianThemeMode,
    seedColor: Color,
    content: @Composable () -> Unit
) {
    val target = when (themeMode) {
        ObsidianThemeMode.OBSIDIAN_DARK -> ObsidianPalette.obsidianDark(seedColor)
        ObsidianThemeMode.PURE_OLED -> ObsidianPalette.pureOled(seedColor)
        ObsidianThemeMode.WARM_ALABASTER -> ObsidianPalette.warmAlabaster(seedColor)
    }

    val animAccent by animateColorAsState(target.accentPrimary, tween(420), label = "accent")
    val animSecondary by animateColorAsState(target.accentSecondary, tween(420), label = "secondary")
    val animVoid by animateColorAsState(target.surfaceVoid, tween(320), label = "void")
    val animChassis by animateColorAsState(target.surfaceChassis, tween(320), label = "chassis")
    val animConsole by animateColorAsState(target.surfaceConsole, tween(320), label = "console")
    val animElevated by animateColorAsState(target.surfaceElevated, tween(320), label = "elevated")

    val animatedPalette = target.copy(
        accentPrimary = animAccent,
        accentSecondary = animSecondary,
        ambientGlow = animAccent.copy(alpha = if (target.isDark) 0.28f else 0.18f),
        surfaceVoid = animVoid,
        surfaceChassis = animChassis,
        surfaceConsole = animConsole,
        surfaceElevated = animElevated
    )

    val colorScheme = if (animatedPalette.isDark) {
        darkColorScheme(
            primary = animatedPalette.accentPrimary,
            secondary = animatedPalette.accentSecondary,
            background = animatedPalette.surfaceVoid,
            surface = animatedPalette.surfaceChassis,
            surfaceVariant = animatedPalette.surfaceConsole,
            onPrimary = Color(0xFF090A0F),
            onBackground = animatedPalette.textPrimary,
            onSurface = animatedPalette.textPrimary,
            error = animatedPalette.statusDanger
        )
    } else {
        lightColorScheme(
            primary = animatedPalette.accentPrimary,
            secondary = animatedPalette.accentSecondary,
            background = animatedPalette.surfaceVoid,
            surface = animatedPalette.surfaceChassis,
            surfaceVariant = animatedPalette.surfaceConsole,
            onPrimary = Color.White,
            onBackground = animatedPalette.textPrimary,
            onSurface = animatedPalette.textPrimary,
            error = animatedPalette.statusDanger
        )
    }

    CompositionLocalProvider(LocalObsidianPalette provides animatedPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography(),
            content = content
        )
    }
}
