package com.sonicwave.sonic_wave.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sonicwave.sonic_wave.core.l10n.AppLocalizations
import com.sonicwave.sonic_wave.designsystem.painters.AcousticMeshSurface
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianRadius
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianSpacing
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianTheme
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianTypography

@Composable
fun ObsidianAmbientScaffold(
    modifier: Modifier = Modifier,
    overrideOrbColor: Color? = null,
    glowIntensity: Float = 0.32f,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        AcousticMeshSurface(
            overrideOrbColor = overrideOrbColor,
            glowIntensity = glowIntensity
        )
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ObsidianConsoleCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(ObsidianSpacing.md),
    cornerRadius: Dp = ObsidianRadius.lg,
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    elevated: Boolean = false,
    onTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = ObsidianTheme.palette
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && (onTap != null || onLongPress != null)) 0.982f else 1f,
        animationSpec = tween(140),
        label = "cardScale"
    )

    val baseBg = backgroundColor ?: if (elevated) palette.surfaceElevated else palette.surfaceConsole
    val resolvedBorder = borderColor ?: if (pressed) palette.accentPrimary.copy(alpha = 0.45f) else palette.hairlineBorder
    val shape = RoundedCornerShape(cornerRadius)

    val clickMod = if (onTap != null || onLongPress != null) {
        Modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onTap?.invoke()
            },
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onLongPress?.invoke()
            }
        )
    } else Modifier

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = if (elevated) 14.dp else 8.dp,
                shape = shape,
                ambientColor = palette.ambientGlow,
                spotColor = Color.Black.copy(alpha = if (palette.isDark) 0.55f else 0.14f)
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        baseBg,
                        baseBg.copy(alpha = if (palette.isDark) 0.88f else 0.96f)
                    )
                )
            )
            .border(BorderStroke(1.dp, resolvedBorder), shape)
            .then(clickMod)
            .padding(padding)
    ) {
        Column(content = content)
    }
}

@Composable
fun ObsidianTopBar(
    title: String,
    subtitleLabel: String = "OBSIDIAN ACOUSTIC STUDIO",
    showStatusDot: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null
) {
    val palette = ObsidianTheme.palette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.surfaceGlass)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ObsidianSpacing.md, vertical = ObsidianSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showStatusDot) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(palette.accentPrimary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = subtitleLabel.uppercase(),
                        style = ObsidianTypography.studioLabel(palette, palette.accentPrimary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = title,
                    style = ObsidianTypography.headingLg(palette),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }
        if (bottomBar != null) {
            bottomBar()
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(palette.hairlineBorder)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObsidianModalSheet(
    onDismiss: () -> Unit,
    maxHeightFraction: Float = 0.88f,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = ObsidianTheme.palette
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxHeight = (LocalConfiguration.current.screenHeightDp * maxHeightFraction).dp

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.surfaceChassis,
        contentColor = palette.textPrimary,
        scrimColor = Color.Black.copy(alpha = 0.62f),
        shape = RoundedCornerShape(topStart = ObsidianRadius.xl, topEnd = ObsidianRadius.xl),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 6.dp)
                    .size(width = 42.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(palette.textMuted.copy(alpha = 0.5f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .padding(horizontal = ObsidianSpacing.md)
                .padding(bottom = ObsidianSpacing.xl),
            content = content
        )
    }
}

@Composable
fun ObsidianConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String? = null,
    cancelLabel: String? = null,
    isDanger: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = ObsidianTheme.palette
    val strings = AppLocalizations.current()
    Dialog(onDismissRequest = onDismiss) {
        ObsidianConsoleCard(
            elevated = true,
            modifier = Modifier.widthIn(max = 420.dp),
            cornerRadius = ObsidianRadius.xl,
            padding = PaddingValues(ObsidianSpacing.lg)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isDanger) palette.statusDanger else palette.accentPrimary)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = ObsidianTypography.headingMd(palette)
                )
            }
            Spacer(modifier = Modifier.height(ObsidianSpacing.sm))
            Text(
                text = message,
                style = ObsidianTypography.bodyMd(palette)
            )
            Spacer(modifier = Modifier.height(ObsidianSpacing.lg))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
            ) {
                ObsidianTactileButton(
                    label = cancelLabel ?: strings.tr("common_cancel"),
                    onClick = onDismiss
                )
                ObsidianTactileButton(
                    label = confirmLabel ?: strings.tr("common_confirm"),
                    isPrimary = !isDanger,
                    isDanger = isDanger,
                    onClick = {
                        onConfirm()
                        onDismiss()
                    }
                )
            }
        }
    }
}
