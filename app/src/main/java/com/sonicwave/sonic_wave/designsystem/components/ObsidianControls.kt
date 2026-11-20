package com.sonicwave.sonic_wave.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sonicwave.sonic_wave.core.l10n.AppLocalizations
import com.sonicwave.sonic_wave.designsystem.painters.ObsidianDockIcon
import com.sonicwave.sonic_wave.designsystem.painters.ObsidianDockIconType
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianRadius
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianSpacing
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianTheme
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianTypography
import kotlin.math.roundToInt

@Composable
fun ObsidianTactileButton(
    label: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isPrimary: Boolean = false,
    isDanger: Boolean = false,
    isLoading: Boolean = false,
    showLed: Boolean = false,
    fullWidth: Boolean = false,
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
) {
    val palette = ObsidianTheme.palette
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && onClick != null) 0.96f else 1f, tween(140), label = "btnScale")

    val bg = when {
        isDanger -> palette.statusDanger.copy(alpha = 0.16f)
        isPrimary -> palette.accentPrimary
        else -> palette.surfaceElevated
    }
    val contentColor = when {
        isDanger -> palette.statusDanger
        isPrimary -> if (palette.isDark) Color(0xFF090A0F) else Color.White
        else -> palette.textPrimary
    }
    val border = when {
        isDanger -> palette.statusDanger.copy(alpha = 0.45f)
        isPrimary -> palette.accentPrimary
        else -> palette.hairlineBorder
    }

    val shape = RoundedCornerShape(ObsidianRadius.md)
    Row(
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .scale(scale)
            .clip(shape)
            .background(bg)
            .border(BorderStroke(1.dp, border), shape)
            .clickable(
                enabled = onClick != null && !isLoading,
                interactionSource = interactionSource,
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick?.invoke()
            }
            .padding(padding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = contentColor
            )
            Spacer(modifier = Modifier.width(8.dp))
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        } else if (showLed) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (isPrimary) contentColor else palette.statusOnline)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = label,
            style = ObsidianTypography.titleSm(palette).copy(color = contentColor),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ObsidianIconTrigger(
    icon: ImageVector,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    isActive: Boolean = false,
    isPrimaryFilled: Boolean = false,
    activeColor: Color? = null,
    contentDescription: String? = null
) {
    val palette = ObsidianTheme.palette
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && onClick != null) 0.90f else 1f, tween(140), label = "iconScale")

    val accent = activeColor ?: palette.accentPrimary
    val bg = when {
        isPrimaryFilled -> accent
        isActive -> accent.copy(alpha = 0.16f)
        else -> palette.surfaceConsole
    }
    val tint = when {
        isPrimaryFilled -> if (palette.isDark) Color(0xFF090A0F) else Color.White
        isActive -> accent
        else -> palette.textSecondary
    }
    val shape = RoundedCornerShape(size * 0.34f)

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(shape)
            .background(bg)
            .border(1.dp, if (isActive || isPrimaryFilled) accent.copy(alpha = 0.5f) else palette.hairlineBorder, shape)
            .clickable(
                enabled = onClick != null,
                interactionSource = interactionSource,
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick?.invoke()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
        if (isActive && !isPrimaryFilled) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
        }
    }
}

@Composable
fun ObsidianSwitchRow(
    title: String,
    value: Boolean,
    onChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    val palette = ObsidianTheme.palette
    val haptic = LocalHapticFeedback.current
    val trackColor by animateColorAsState(
        if (value) palette.accentPrimary.copy(alpha = 0.32f) else palette.surfaceElevated,
        tween(200),
        label = "switchTrack"
    )
    val thumbOffset by animateDpAsState(
        if (value) 22.dp else 4.dp,
        tween(220),
        label = "switchThumb"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ObsidianRadius.md))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onChanged(!value)
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = ObsidianTypography.titleSm(palette))
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, style = ObsidianTypography.bodySm(palette))
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 26.dp)
                .clip(CircleShape)
                .background(trackColor)
                .border(1.dp, if (value) palette.accentPrimary else palette.hairlineBorder, CircleShape),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (value) palette.accentPrimary else palette.textMuted)
            )
        }
    }
}

@Composable
fun ObsidianPrecisionFader(
    value: Float,
    min: Float = 0f,
    max: Float = 1f,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    divisions: Int? = null
) {
    val palette = ObsidianTheme.palette
    val haptic = LocalHapticFeedback.current
    var isDragging by remember { mutableStateOf(false) }

    val range = (max - min).coerceAtLeast(0.0001f)
    val fraction = ((value - min) / range).coerceIn(0f, 1f)

    fun snap(rawFrac: Float): Float {
        val clamped = rawFrac.coerceIn(0f, 1f)
        val rawVal = min + clamped * range
        return if (divisions != null && divisions > 0) {
            val step = range / divisions
            (min + ((rawVal - min) / step).roundToInt() * step).coerceIn(min, max)
        } else rawVal
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
    ) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }.coerceAtLeast(1f)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(min, max, divisions) {
                    detectTapGestures { offset ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onValueChange(snap(offset.x / widthPx))
                    }
                }
                .pointerInput(min, max, divisions) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            isDragging = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            onValueChange(snap(change.position.x / widthPx))
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false }
                    )
                }
        ) {
            val trackH = 4.dp.toPx()
            val cy = size.height / 2f
            val pad = 10.dp.toPx()
            val trackW = (size.width - pad * 2).coerceAtLeast(1f)

            // Tick marks
            val tickCount = (divisions ?: 10).coerceIn(4, 24)
            for (i in 0..tickCount) {
                val tx = pad + (i.toFloat() / tickCount) * trackW
                drawLine(
                    color = palette.hairlineBorder,
                    start = Offset(tx, cy - 8.dp.toPx()),
                    end = Offset(tx, cy - 5.dp.toPx()),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Track background
            drawRoundRect(
                color = palette.surfaceElevated,
                topLeft = Offset(pad, cy - trackH / 2f),
                size = Size(trackW, trackH),
                cornerRadius = CornerRadius(trackH / 2f)
            )

            // Active fill
            val activeW = trackW * fraction
            drawRoundRect(
                color = palette.accentPrimary,
                topLeft = Offset(pad, cy - trackH / 2f),
                size = Size(activeW, trackH),
                cornerRadius = CornerRadius(trackH / 2f)
            )

            // Cap
            val capX = pad + activeW
            val capW = if (isDragging) 22.dp.toPx() else 18.dp.toPx()
            val capH = if (isDragging) 24.dp.toPx() else 20.dp.toPx()
            if (isDragging) {
                drawCircle(
                    color = palette.accentPrimary.copy(alpha = 0.25f),
                    radius = capH * 0.85f,
                    center = Offset(capX, cy)
                )
            }
            drawRoundRect(
                color = palette.textPrimary,
                topLeft = Offset(capX - capW / 2f, cy - capH / 2f),
                size = Size(capW, capH),
                cornerRadius = CornerRadius(6.dp.toPx())
            )
            drawLine(
                color = palette.accentPrimary,
                start = Offset(capX, cy - capH * 0.26f),
                end = Offset(capX, cy + capH * 0.26f),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}

@Composable
fun ObsidianFilterCapsule(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    badgeText: String? = null
) {
    val palette = ObsidianTheme.palette
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(ObsidianRadius.capsule)
    val bg = if (selected) palette.accentPrimary.copy(alpha = 0.18f) else palette.surfaceConsole
    val border = if (selected) palette.accentPrimary else palette.hairlineBorder
    val fg = if (selected) palette.accentPrimary else palette.textSecondary

    Row(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(palette.accentPrimary)
            )
        }
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = fg, modifier = Modifier.size(15.dp))
        }
        Text(
            text = label,
            style = ObsidianTypography.titleSm(palette).copy(color = fg),
            maxLines = 1
        )
        if (!badgeText.isNullOrBlank()) {
            Text(
                text = badgeText,
                style = ObsidianTypography.technicalMono(palette, 10.5f, fg.copy(alpha = 0.85f))
            )
        }
    }
}

@Composable
fun ObsidianTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    hint: String? = null,
    prefixIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    obscureText: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    maxLines: Int = 1,
    isMonospace: Boolean = false
) {
    val palette = ObsidianTheme.palette
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(ObsidianRadius.md)

    Column(modifier = modifier.fillMaxWidth()) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label.uppercase(),
                style = ObsidianTypography.studioLabel(palette, if (focused) palette.accentPrimary else palette.textMuted),
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(palette.surfaceChassis)
                .border(
                    width = 1.dp,
                    color = if (focused) palette.accentPrimary else palette.hairlineBorder,
                    shape = shape
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (prefixIcon != null) {
                Icon(
                    imageVector = prefixIcon,
                    contentDescription = null,
                    tint = if (focused) palette.accentPrimary else palette.textMuted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty() && !hint.isNullOrBlank()) {
                    Text(
                        text = hint,
                        style = ObsidianTypography.bodyMd(palette).copy(color = palette.textMuted),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused },
                    textStyle = if (isMonospace) {
                        ObsidianTypography.technicalMono(palette, 13.5f, palette.textPrimary)
                    } else {
                        ObsidianTypography.bodyMd(palette).copy(color = palette.textPrimary)
                    },
                    singleLine = maxLines == 1,
                    maxLines = maxLines,
                    cursorBrush = SolidColor(palette.accentPrimary),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    visualTransformation = if (obscureText) PasswordVisualTransformation() else VisualTransformation.None
                )
            }
            if (trailing != null) {
                Spacer(modifier = Modifier.width(8.dp))
                trailing()
            }
        }
    }
}

@Composable
private fun obsidianDockItems(): List<Pair<ObsidianDockIconType, String>> {
    val strings = AppLocalizations.current()
    return listOf(
        ObsidianDockIconType.CONSOLE_HOME to strings.tr("nav_studio"),
        ObsidianDockIconType.LIBRARY_VAULT to strings.tr("nav_library"),
        ObsidianDockIconType.SONIC_SEARCH to strings.tr("nav_search"),
        ObsidianDockIconType.SERVER_RACK to strings.tr("nav_settings")
    )
}

@Composable
fun ObsidianFloatingDock(
    selectedIndex: Int,
    onDestinationSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = ObsidianTheme.palette
    val haptic = LocalHapticFeedback.current
    val items = obsidianDockItems()

    val shape = RoundedCornerShape(ObsidianRadius.xl)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = ObsidianSpacing.md, vertical = ObsidianSpacing.xs)
            .height(64.dp)
            .shadow(16.dp, shape, ambientColor = palette.ambientGlow)
            .clip(shape)
            .background(palette.surfaceGlass)
            .border(1.dp, palette.hairlineBorder, shape)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, (iconType, label) ->
            val selected = selectedIndex == index
            val progress by animateFloatAsState(if (selected) 1f else 0f, tween(220), label = "dock_$index")
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(ObsidianRadius.md))
                    .background(if (selected) palette.accentPrimary.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDestinationSelected(index)
                    }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                ObsidianDockIcon(
                    type = iconType,
                    activeProgress = progress,
                    activeColor = palette.accentPrimary,
                    inactiveColor = palette.textMuted,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = label,
                    style = ObsidianTypography.studioLabel(
                        palette,
                        if (selected) palette.accentPrimary else palette.textMuted
                    ).copy(letterSpacing = 0.5.spFromDp()),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Vertical navigation rail used in landscape orientation to preserve vertical content height.
 */
@Composable
fun ObsidianNavigationRail(
    selectedIndex: Int,
    onDestinationSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = ObsidianTheme.palette
    val haptic = LocalHapticFeedback.current
    val items = obsidianDockItems()

    val shape = RoundedCornerShape(ObsidianRadius.xl)
    Column(
        modifier = modifier
            .statusBarsPadding()
            .navigationBarsPadding()
            .displayCutoutPadding()
            .padding(start = ObsidianSpacing.sm, top = ObsidianSpacing.sm, bottom = ObsidianSpacing.sm)
            .width(78.dp)
            .fillMaxHeight()
            .shadow(16.dp, shape, ambientColor = palette.ambientGlow)
            .clip(shape)
            .background(palette.surfaceGlass)
            .border(1.dp, palette.hairlineBorder, shape)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items.forEachIndexed { index, (iconType, label) ->
            val selected = selectedIndex == index
            val progress by animateFloatAsState(if (selected) 1f else 0f, tween(220), label = "rail_$index")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(ObsidianRadius.md))
                    .background(if (selected) palette.accentPrimary.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDestinationSelected(index)
                    }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                ObsidianDockIcon(
                    type = iconType,
                    activeProgress = progress,
                    activeColor = palette.accentPrimary,
                    inactiveColor = palette.textMuted,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    style = ObsidianTypography.studioLabel(
                        palette,
                        if (selected) palette.accentPrimary else palette.textMuted
                    ).copy(letterSpacing = 0.3.spFromDp()),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun Double.spFromDp() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
