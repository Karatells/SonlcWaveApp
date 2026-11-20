package com.sonicwave.sonic_wave.presentation.screens

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.sonicwave.sonic_wave.AppContainer
import com.sonicwave.sonic_wave.core.l10n.AppLocalizations
import com.sonicwave.sonic_wave.designsystem.components.ObsidianAmbientScaffold
import com.sonicwave.sonic_wave.designsystem.components.ObsidianArtworkFrame
import com.sonicwave.sonic_wave.designsystem.components.ObsidianAudioSpecBadge
import com.sonicwave.sonic_wave.designsystem.components.ObsidianConsoleCard
import com.sonicwave.sonic_wave.designsystem.components.ObsidianFilterCapsule
import com.sonicwave.sonic_wave.designsystem.components.ObsidianIconTrigger
import com.sonicwave.sonic_wave.designsystem.components.ObsidianModalSheet
import com.sonicwave.sonic_wave.designsystem.components.ObsidianPrecisionFader
import com.sonicwave.sonic_wave.designsystem.components.ObsidianSwitchRow
import com.sonicwave.sonic_wave.designsystem.components.ObsidianTactileButton
import com.sonicwave.sonic_wave.designsystem.painters.VinylGrooveDisc
import com.sonicwave.sonic_wave.designsystem.painters.WaveformSeekBar
import com.sonicwave.sonic_wave.designsystem.painters.formatDurationMs
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianRadius
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianSpacing
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianTheme
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianTypography
import com.sonicwave.sonic_wave.domain.models.MediaItemEntity
import com.sonicwave.sonic_wave.domain.services.AppSettingsService
import com.sonicwave.sonic_wave.presentation.audio.RepeatLoopMode
import com.sonicwave.sonic_wave.presentation.viewmodel.PlayerUiState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullPlayerScreen(
    container: AppContainer,
    onDismiss: () -> Unit
) {
    val palette = ObsidianTheme.palette
    val strings = AppLocalizations.current()
    val state by container.playerViewModel.state.collectAsState()
    val downloadState by container.downloadViewModel.state.collectAsState()
    val settings by container.settingsService.settings.collectAsState()

    val queue = state.queue
    val currentTrack = state.currentTrack
    if (currentTrack == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    var showLyrics by remember { mutableStateOf(false) }
    var showEqualizerSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showRepeatSheet by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }

    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val dragProgress = (dragOffsetY / 600f).coerceIn(0f, 1f)

    val pagerState = rememberPagerState(
        initialPage = state.currentIndex.coerceIn(0, queue.lastIndex),
        pageCount = { queue.size }
    )

    LaunchedEffect(state.currentIndex, queue.size) {
        val target = state.currentIndex.coerceIn(0, queue.lastIndex)
        if (pagerState.currentPage != target) {
            pagerState.animateScrollToPage(target)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                if (page != state.currentIndex && page in queue.indices) {
                    container.playerViewModel.skipToIndex(page)
                }
            }
    }

    val cornerRadius = (dragProgress * 38f).dp
    val scale = 1f - dragProgress * 0.08f

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isOffline = downloadState.isTrackOffline(currentTrack.id) || currentTrack.isOfflineAvailable

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, dragOffsetY.roundToInt()) }
            .scale(scale)
            .clip(RoundedCornerShape(cornerRadius))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        dragOffsetY = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                    },
                    onDragEnd = {
                        if (dragOffsetY > 180f) onDismiss() else dragOffsetY = 0f
                    },
                    onDragCancel = { dragOffsetY = 0f }
                )
            }
    ) {
        ObsidianAmbientScaffold(
            overrideOrbColor = state.dynamicSeedColor,
            glowIntensity = 0.48f
        ) {
            if (isLandscape) {
                // Landscape Studio Turntable & Console Dual-Pane Layout
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .displayCutoutPadding()
                        .padding(horizontal = ObsidianSpacing.md, vertical = ObsidianSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Pane: Unclipped Turntable Stage / Synced Lyrics + Floating Dismiss
                    Box(
                        modifier = Modifier
                            .weight(0.44f)
                            .fillMaxHeight()
                    ) {
                        PlayerStageArea(
                            container = container,
                            state = state,
                            pagerState = pagerState,
                            showLyrics = showLyrics,
                            vinylWavesEnabled = settings.vinylPulseWavesEnabled,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                        ObsidianIconTrigger(
                            icon = Icons.Rounded.KeyboardArrowDown,
                            onClick = onDismiss,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // Right Pane: Studio Control Console
                    val consoleShape = RoundedCornerShape(ObsidianRadius.xl)
                    Column(
                        modifier = Modifier
                            .weight(0.56f)
                            .fillMaxHeight()
                            .clip(consoleShape)
                            .background(palette.surfaceGlass.copy(alpha = 0.55f))
                            .border(1.dp, palette.hairlineBorder, consoleShape)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Console Header: Content Type + Album + Equalizer
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentTrack.contentType.name,
                                    style = ObsidianTypography.studioLabel(palette, palette.accentPrimary)
                                )
                                Text(
                                    text = currentTrack.album,
                                    style = ObsidianTypography.titleSm(palette),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            ObsidianIconTrigger(
                                icon = Icons.Rounded.Equalizer,
                                size = 36.dp,
                                iconSize = 18.dp,
                                isActive = state.equalizerEnabled,
                                onClick = { showEqualizerSheet = true }
                            )
                        }

                        PlayerTrackHeader(
                            currentTrack = currentTrack,
                            onToggleFavorite = { container.playerViewModel.toggleFavorite() }
                        )

                        PlayerWaveformSection(
                            currentTrack = currentTrack,
                            state = state,
                            isOffline = isOffline,
                            onSeek = { container.playerViewModel.seekTo(it) }
                        )

                        PlayerTransportDeck(
                            state = state,
                            container = container,
                            isCompact = true,
                            onShowRepeatSheet = { showRepeatSheet = true }
                        )

                        PlayerUtilityDock(
                            state = state,
                            isOffline = isOffline,
                            showLyrics = showLyrics,
                            currentTrack = currentTrack,
                            container = container,
                            isCompact = true,
                            onToggleLyrics = { showLyrics = !showLyrics },
                            onShowSleepTimer = { showSleepTimerSheet = true },
                            onShowQueue = { showQueueSheet = true }
                        )
                    }
                }
            } else {
                // Portrait Layout
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = ObsidianSpacing.md, vertical = ObsidianSpacing.sm)
                ) {
                    // Top Console Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ObsidianIconTrigger(
                            icon = Icons.Rounded.KeyboardArrowDown,
                            onClick = onDismiss
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = currentTrack.contentType.name,
                                style = ObsidianTypography.studioLabel(palette, palette.accentPrimary)
                            )
                            Text(
                                text = currentTrack.album,
                                style = ObsidianTypography.titleSm(palette),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        ObsidianIconTrigger(
                            icon = Icons.Rounded.Equalizer,
                            isActive = state.equalizerEnabled,
                            onClick = { showEqualizerSheet = true }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    PlayerStageArea(
                        container = container,
                        state = state,
                        pagerState = pagerState,
                        showLyrics = showLyrics,
                        vinylWavesEnabled = settings.vinylPulseWavesEnabled,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    PlayerTrackHeader(
                        currentTrack = currentTrack,
                        onToggleFavorite = { container.playerViewModel.toggleFavorite() }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    PlayerWaveformSection(
                        currentTrack = currentTrack,
                        state = state,
                        isOffline = isOffline,
                        onSeek = { container.playerViewModel.seekTo(it) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    PlayerTransportDeck(
                        state = state,
                        container = container,
                        isCompact = false,
                        onShowRepeatSheet = { showRepeatSheet = true }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    PlayerUtilityDock(
                        state = state,
                        isOffline = isOffline,
                        showLyrics = showLyrics,
                        currentTrack = currentTrack,
                        container = container,
                        isCompact = false,
                        onToggleLyrics = { showLyrics = !showLyrics },
                        onShowSleepTimer = { showSleepTimerSheet = true },
                        onShowQueue = { showQueueSheet = true }
                    )
                }
            }
        }
    }

    if (showEqualizerSheet) {
        EqualizerModalSheet(container = container, onDismiss = { showEqualizerSheet = false })
    }

    if (showSleepTimerSheet) {
        SleepTimerSheet(container = container, onDismiss = { showSleepTimerSheet = false })
    }

    if (showRepeatSheet) {
        RepeatModeSheet(container = container, onDismiss = { showRepeatSheet = false })
    }

    if (showQueueSheet) {
        QueueModalSheet(container = container, onDismiss = { showQueueSheet = false })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerStageArea(
    container: AppContainer,
    state: PlayerUiState,
    pagerState: PagerState,
    showLyrics: Boolean,
    vinylWavesEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = ObsidianTheme.palette
    val strings = AppLocalizations.current()
    val queue = state.queue

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(targetState = showLyrics, label = "stageSwitch") { lyricsMode ->
            if (!lyricsMode) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val item = queue.getOrNull(page) ?: return@HorizontalPager
                    val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).let { abs(it) }
                    val itemScale = (1f - pageOffset * 0.14f).coerceIn(0.84f, 1f)
                    val itemAlpha = (1f - pageOffset * 0.45f).coerceIn(0.45f, 1f)

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = itemScale
                                scaleY = itemScale
                                alpha = itemAlpha
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val stageSize = min(maxWidth, maxHeight) * 0.92f
                        Box(
                            modifier = Modifier
                                .size(stageSize)
                                .aspectRatio(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            val isCurrentPage = page == state.currentIndex
                            var vinylAngle by remember { mutableFloatStateOf(0f) }
                            var wavePhase by remember { mutableFloatStateOf(0f) }
                            LaunchedEffect(state.isPlaying, isCurrentPage, vinylWavesEnabled) {
                                if (state.isPlaying && isCurrentPage) {
                                    var lastNanos = withFrameNanos { it }
                                    while (true) {
                                        val now = withFrameNanos { it }
                                        val dtSec = (now - lastNanos) / 1_000_000_000f
                                        lastNanos = now
                                        vinylAngle = (vinylAngle + dtSec * 32f) % 360f
                                        if (vinylWavesEnabled) {
                                            wavePhase = (wavePhase + dtSec * 0.48f) % 1000f
                                        }
                                    }
                                }
                            }
                            val rawEnergy = remember(item.waveformPeaks, state.progressFraction, state.isPlaying, isCurrentPage) {
                                if (!state.isPlaying || !isCurrentPage) {
                                    0f
                                } else if (item.waveformPeaks.isNotEmpty()) {
                                    val idx = (state.progressFraction * item.waveformPeaks.lastIndex)
                                        .roundToInt()
                                        .coerceIn(0, item.waveformPeaks.lastIndex)
                                    item.waveformPeaks[idx].coerceIn(0.15f, 1f)
                                } else {
                                    0.45f
                                }
                            }
                            val smoothEnergy by animateFloatAsState(
                                targetValue = rawEnergy,
                                animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.85f),
                                label = "vinylEnergy"
                            )
                            val tonearmAngle by animateFloatAsState(
                                targetValue = if (state.isPlaying && isCurrentPage) 14f else -16f,
                                animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.72f),
                                label = "tonearm"
                            )

                            if (vinylWavesEnabled && isCurrentPage && smoothEnergy > 0.01f) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    val vinylRadius = (size.minDimension * 0.86f) / 2f
                                    // Gentle ambient halo around the vinyl edge
                                    drawCircle(
                                        color = state.dynamicSeedColor.copy(alpha = 0.04f + smoothEnergy * 0.06f),
                                        radius = vinylRadius * (1.03f + smoothEnergy * 0.04f),
                                        center = center
                                    )
                                    // 3 soft concentric rhythm rings expanding outward
                                    for (ring in 0 until 3) {
                                        val progress = (wavePhase + ring / 3f) % 1f
                                        val radius = vinylRadius * (0.94f + progress * (0.14f + smoothEnergy * 0.14f))
                                        val fade = sin(progress * PI).toFloat().coerceIn(0f, 1f)
                                        val alpha = fade * (0.11f + smoothEnergy * 0.15f)
                                        drawCircle(
                                            color = state.dynamicSeedColor.copy(alpha = alpha),
                                            radius = radius,
                                            center = center,
                                            style = Stroke(width = (1.4f + smoothEnergy * 1.1f).dp.toPx())
                                        )
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize(0.86f)
                                    .align(Alignment.Center),
                                contentAlignment = Alignment.Center
                            ) {
                                VinylGrooveDisc(
                                    modifier = Modifier.fillMaxSize(),
                                    accentColor = state.dynamicSeedColor,
                                    isDark = palette.isDark,
                                    rotationDegrees = vinylAngle
                                )
                                if (!item.coverArtId.isNullOrBlank()) {
                                    ObsidianArtworkFrame(
                                        repository = container.repository,
                                        coverArtId = item.coverArtId,
                                        size = 220.dp,
                                        cornerRadius = ObsidianRadius.capsule,
                                        requestResolution = 700,
                                        modifier = Modifier
                                            .fillMaxSize(0.60f)
                                            .graphicsLayer { rotationZ = vinylAngle }
                                            .clip(CircleShape)
                                            .border(1.5.dp, state.dynamicSeedColor.copy(alpha = 0.55f), CircleShape)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF090A0F))
                                            .border(2.dp, state.dynamicSeedColor.copy(alpha = 0.85f), CircleShape)
                                    )
                                }
                            }
                            // Animated Tonearm
                            Canvas(
                                modifier = Modifier
                                    .size(96.dp)
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-4).dp, y = 4.dp)
                            ) {
                                val pivot = Offset(size.width * 0.78f, size.height * 0.16f)
                                drawCircle(
                                    color = palette.surfaceElevated,
                                    radius = 10.dp.toPx(),
                                    center = pivot
                                )
                                drawCircle(
                                    color = state.dynamicSeedColor,
                                    radius = 4.dp.toPx(),
                                    center = pivot
                                )
                                rotate(degrees = tonearmAngle, pivot = pivot) {
                                    val elbow = Offset(pivot.x - 10.dp.toPx(), pivot.y + 42.dp.toPx())
                                    val tip = Offset(elbow.x - 22.dp.toPx(), elbow.y + 24.dp.toPx())
                                    drawLine(
                                        color = palette.textSecondary,
                                        start = pivot,
                                        end = elbow,
                                        strokeWidth = 3.2.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                    drawLine(
                                        color = palette.textPrimary,
                                        start = elbow,
                                        end = tip,
                                        strokeWidth = 2.6.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                    drawCircle(
                                        color = state.dynamicSeedColor,
                                        radius = 3.5.dp.toPx(),
                                        center = tip
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(10.dp)
                                    .clip(RoundedCornerShape(ObsidianRadius.xs))
                                    .background(palette.surfaceGlass)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "%02d / %02d".format(page + 1, queue.size),
                                    style = ObsidianTypography.technicalMono(palette, 10.5f, palette.accentPrimary)
                                )
                            }
                        }
                    }
                }
            } else {
                // Synced Lyrics Panel
                val lyrics = state.lyrics
                val listState = rememberLazyListState()
                LaunchedEffect(state.activeLyricLineIndex) {
                    if (state.activeLyricLineIndex >= 0) {
                        runCatching {
                            listState.animateScrollToItem((state.activeLyricLineIndex - 2).coerceAtLeast(0))
                        }
                    }
                }

                if (lyrics == null || lyrics.lines.isEmpty()) {
                    ObsidianConsoleCard {
                        Text(
                            text = strings.tr("player_no_lyrics"),
                            style = ObsidianTypography.bodyMd(palette),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(lyrics.lines) { idx, line ->
                            val isCurrent = idx == state.activeLyricLineIndex
                            val lineScale by animateFloatAsState(
                                targetValue = if (isCurrent) 1.03f else 0.97f,
                                label = "lyricScale"
                            )
                            val lineAlpha by animateFloatAsState(
                                targetValue = if (isCurrent || !lyrics.isSynced) 1f else 0.48f,
                                label = "lyricAlpha"
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        scaleX = lineScale
                                        scaleY = lineScale
                                        alpha = lineAlpha
                                    }
                                    .then(
                                        if (!isCurrent && lyrics.isSynced) Modifier.blur(1.2.dp) else Modifier
                                    )
                                    .clip(RoundedCornerShape(ObsidianRadius.md))
                                    .background(if (isCurrent) palette.accentPrimary.copy(alpha = 0.16f) else Color.Transparent)
                                    .border(
                                        width = if (isCurrent) 1.dp else 0.dp,
                                        color = if (isCurrent) palette.accentPrimary.copy(alpha = 0.35f) else Color.Transparent,
                                        shape = RoundedCornerShape(ObsidianRadius.md)
                                    )
                                    .clickable(enabled = lyrics.isSynced) {
                                        container.playerViewModel.seekTo(line.timestampMs)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (lyrics.isSynced) {
                                    Text(
                                        text = formatDurationMs(line.timestampMs),
                                        style = ObsidianTypography.technicalMono(
                                            palette,
                                            10.5f,
                                            if (isCurrent) palette.accentPrimary else palette.textMuted
                                        ),
                                        modifier = Modifier.width(46.dp)
                                    )
                                }
                                Text(
                                    text = line.text,
                                    style = ObsidianTypography.headingMd(palette).copy(
                                        fontSize = if (isCurrent) 19.sp else 16.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isCurrent) palette.textPrimary else palette.textMuted
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerTrackHeader(
    currentTrack: MediaItemEntity,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = ObsidianTheme.palette
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = currentTrack.title,
                style = ObsidianTypography.headingLg(palette),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = currentTrack.artist,
                style = ObsidianTypography.bodyMd(palette),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        ObsidianIconTrigger(
            icon = if (currentTrack.isStarred) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            isActive = currentTrack.isStarred,
            onClick = onToggleFavorite
        )
    }
}

@Composable
private fun PlayerWaveformSection(
    currentTrack: MediaItemEntity,
    state: PlayerUiState,
    isOffline: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = ObsidianTheme.palette
    val strings = AppLocalizations.current()
    Column(modifier = modifier.fillMaxWidth()) {
        WaveformSeekBar(
            peaks = currentTrack.waveformPeaks,
            progressFraction = state.progressFraction,
            bufferedFraction = state.bufferedFraction,
            durationMs = currentTrack.durationMs,
            onSeek = onSeek,
            loopStartMs = state.loopStartMs,
            loopEndMs = state.loopEndMs
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatDurationMs(state.positionMs),
                style = ObsidianTypography.technicalMono(palette, 11.5f, palette.accentPrimary)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isOffline) strings.tr("player_stream_offline") else strings.tr("player_stream_pcm"),
                    style = ObsidianTypography.technicalMono(
                        palette,
                        10f,
                        if (isOffline) palette.statusOnline else palette.textMuted
                    )
                )
                if (!currentTrack.suffix.isNullOrBlank()) {
                    ObsidianAudioSpecBadge(
                        suffix = currentTrack.suffix,
                        bitrateKbps = currentTrack.bitrateKbps,
                        isCompact = true,
                        isHighlighted = true
                    )
                }
            }
            Text(
                text = formatDurationMs(currentTrack.durationMs),
                style = ObsidianTypography.technicalMono(palette, 11.5f, palette.textMuted)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerTransportDeck(
    state: PlayerUiState,
    container: AppContainer,
    isCompact: Boolean,
    onShowRepeatSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = ObsidianTheme.palette
    val skipSize = if (isCompact) 44.dp else 50.dp
    val skipIconSize = if (isCompact) 24.dp else 28.dp
    val playSize = if (isCompact) 58.dp else 68.dp
    val playIconSize = if (isCompact) 30.dp else 34.dp
    val sideSize = if (isCompact) 36.dp else 40.dp

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ObsidianIconTrigger(
            icon = Icons.Rounded.Shuffle,
            size = sideSize,
            isActive = state.isShuffleEnabled,
            onClick = { container.playerViewModel.toggleShuffle() }
        )
        ObsidianIconTrigger(
            icon = Icons.Rounded.SkipPrevious,
            size = skipSize,
            iconSize = skipIconSize,
            onClick = { container.playerViewModel.previous() }
        )
        if (state.isBuffering) {
            Box(
                modifier = Modifier
                    .size(playSize)
                    .clip(RoundedCornerShape(if (isCompact) 18.dp else 22.dp))
                    .background(palette.accentPrimary),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(if (isCompact) 24.dp else 28.dp),
                    strokeWidth = 3.dp,
                    color = if (palette.isDark) Color(0xFF090A0F) else Color.White
                )
            }
        } else {
            ObsidianIconTrigger(
                icon = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                size = playSize,
                iconSize = playIconSize,
                isPrimaryFilled = true,
                onClick = { container.playerViewModel.togglePlayPause() }
            )
        }
        ObsidianIconTrigger(
            icon = Icons.Rounded.SkipNext,
            size = skipSize,
            iconSize = skipIconSize,
            onClick = { container.playerViewModel.next() }
        )
        Box(
            modifier = Modifier
                .size(sideSize)
                .clip(RoundedCornerShape(14.dp))
                .background(if (state.repeatMode != RepeatLoopMode.OFF) palette.accentPrimary.copy(alpha = 0.16f) else palette.surfaceConsole)
                .border(
                    1.dp,
                    if (state.repeatMode != RepeatLoopMode.OFF) palette.accentPrimary.copy(alpha = 0.5f) else palette.hairlineBorder,
                    RoundedCornerShape(14.dp)
                )
                .combinedClickable(
                    onClick = { container.playerViewModel.cycleRepeatMode() },
                    onLongClick = onShowRepeatSheet
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (state.repeatMode == RepeatLoopMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                contentDescription = null,
                tint = if (state.repeatMode != RepeatLoopMode.OFF) palette.accentPrimary else palette.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun PlayerUtilityDock(
    state: PlayerUiState,
    isOffline: Boolean,
    showLyrics: Boolean,
    currentTrack: MediaItemEntity,
    container: AppContainer,
    isCompact: Boolean,
    onToggleLyrics: () -> Unit,
    onShowSleepTimer: () -> Unit,
    onShowQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = ObsidianTheme.palette
    val btnSize = if (isCompact) 36.dp else 40.dp
    val iconSize = if (isCompact) 18.dp else 20.dp

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ObsidianIconTrigger(
            icon = Icons.Rounded.Lyrics,
            size = btnSize,
            iconSize = iconSize,
            isActive = showLyrics,
            onClick = onToggleLyrics
        )
        ObsidianIconTrigger(
            icon = Icons.Rounded.Bedtime,
            size = btnSize,
            iconSize = iconSize,
            isActive = state.sleepTimerRemainingMs != null,
            onClick = onShowSleepTimer
        )
        val abLabel = when {
            state.loopStartMs != null && state.loopEndMs != null -> "A-B"
            state.loopStartMs != null -> "A.."
            else -> "A-B"
        }
        ObsidianFilterCapsule(
            label = abLabel,
            selected = state.loopStartMs != null,
            onClick = { container.playerViewModel.toggleAbLoopPoint() }
        )
        ObsidianIconTrigger(
            icon = if (isOffline) Icons.Rounded.CheckCircle else Icons.Rounded.Download,
            size = btnSize,
            iconSize = iconSize,
            isActive = isOffline,
            activeColor = palette.statusOnline,
            onClick = {
                if (!isOffline) {
                    container.downloadViewModel.enqueueDownloads(listOf(currentTrack), currentTrack.album)
                }
            }
        )
        ObsidianIconTrigger(
            icon = Icons.Rounded.Radio,
            size = btnSize,
            iconSize = iconSize,
            onClick = { container.playerViewModel.startTrackRadio(currentTrack) }
        )
        ObsidianIconTrigger(
            icon = Icons.Rounded.QueueMusic,
            size = btnSize,
            iconSize = iconSize,
            onClick = onShowQueue
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EqualizerModalSheet(
    container: AppContainer,
    onDismiss: () -> Unit
) {
    val palette = ObsidianTheme.palette
    val strings = AppLocalizations.current()
    val state by container.playerViewModel.state.collectAsState()
    val bandLabels = listOf("31", "62", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")

    ObsidianModalSheet(onDismiss = onDismiss) {
        Text(text = strings.tr("player_equalizer"), style = ObsidianTypography.headingMd(palette))
        Spacer(modifier = Modifier.height(8.dp))
        ObsidianSwitchRow(
            title = strings.tr("player_eq_enable"),
            value = state.equalizerEnabled,
            onChanged = { en ->
                container.playerViewModel.updateEqualizer(
                    enabled = en,
                    bandGainsDb = state.equalizerBandGainsDb,
                    bassBoostEnabled = state.bassBoostEnabled,
                    bassBoostDb = state.bassBoostDb
                )
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppSettingsService.EQ_PRESETS.forEach { (preset, bands) ->
                ObsidianFilterCapsule(
                    label = preset.uppercase(),
                    selected = false,
                    onClick = {
                        container.playerViewModel.updateEqualizer(
                            enabled = true,
                            bandGainsDb = bands,
                            bassBoostEnabled = state.bassBoostEnabled,
                            bassBoostDb = state.bassBoostDb
                        )
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        LazyColumn(modifier = Modifier.height(if (isLandscape) 130.dp else 240.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(bandLabels) { idx, freq ->
                val db = state.equalizerBandGainsDb.getOrElse(idx) { 0.0 }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = freq,
                        style = ObsidianTypography.technicalMono(palette, 11f, palette.textMuted),
                        modifier = Modifier.width(38.dp)
                    )
                    ObsidianPrecisionFader(
                        value = db.toFloat(),
                        min = -12f,
                        max = 12f,
                        divisions = 24,
                        modifier = Modifier.weight(1f),
                        onValueChange = { newVal ->
                            val updated = state.equalizerBandGainsDb.toMutableList().apply {
                                if (idx in indices) set(idx, newVal.toDouble())
                            }
                            container.playerViewModel.updateEqualizer(
                                enabled = true,
                                bandGainsDb = updated,
                                bassBoostEnabled = state.bassBoostEnabled,
                                bassBoostDb = state.bassBoostDb
                            )
                        }
                    )
                    Text(
                        text = "%+.0f".format(db),
                        style = ObsidianTypography.technicalMono(palette, 11f, palette.accentPrimary),
                        modifier = Modifier.width(36.dp),
                        textAlign = TextAlign.End
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        ObsidianSwitchRow(
            title = "Virtualizer (Stereo Width)",
            value = state.virtualizerEnabled,
            onChanged = { en ->
                container.playerViewModel.updateEqualizer(
                    enabled = state.equalizerEnabled,
                    bandGainsDb = state.equalizerBandGainsDb,
                    bassBoostEnabled = state.bassBoostEnabled,
                    bassBoostDb = state.bassBoostDb,
                    virtualizerEnabled = en,
                    virtualizerStrength = state.virtualizerStrength
                )
            }
        )
        ObsidianPrecisionFader(
            value = state.virtualizerStrength.toFloat(),
            min = 0f,
            max = 1000f,
            divisions = 20,
            onValueChange = { str ->
                container.playerViewModel.updateEqualizer(
                    enabled = state.equalizerEnabled,
                    bandGainsDb = state.equalizerBandGainsDb,
                    bassBoostEnabled = state.bassBoostEnabled,
                    bassBoostDb = state.bassBoostDb,
                    virtualizerEnabled = true,
                    virtualizerStrength = str.toInt()
                )
            }
        )
    }
}

@Composable
private fun SleepTimerSheet(
    container: AppContainer,
    onDismiss: () -> Unit
) {
    val palette = ObsidianTheme.palette
    val strings = AppLocalizations.current()
    val state by container.playerViewModel.state.collectAsState()
    val options = listOf(
        strings.tr("player_sleep_off") to null,
        strings.tr("player_sleep_15") to 15 * 60_000L,
        strings.tr("player_sleep_30") to 30 * 60_000L,
        strings.tr("player_sleep_45") to 45 * 60_000L,
        strings.tr("player_sleep_60") to 60 * 60_000L
    )

    ObsidianModalSheet(onDismiss = onDismiss) {
        Text(text = strings.tr("player_sleep_timer"), style = ObsidianTypography.headingMd(palette))
        state.sleepTimerRemainingMs?.let { rem ->
            Text(
                text = formatDurationMs(rem),
                style = ObsidianTypography.technicalMono(palette, 13f, palette.accentPrimary)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (label, durMs) ->
                ObsidianTactileButton(
                    label = label,
                    fullWidth = true,
                    isPrimary = durMs != null && state.sleepTimerRemainingMs != null,
                    onClick = {
                        container.playerViewModel.setSleepTimer(durMs)
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun RepeatModeSheet(
    container: AppContainer,
    onDismiss: () -> Unit
) {
    val palette = ObsidianTheme.palette
    val strings = AppLocalizations.current()
    val state by container.playerViewModel.state.collectAsState()
    val modes = listOf(
        RepeatLoopMode.OFF to strings.tr("player_repeat_off"),
        RepeatLoopMode.ALL to strings.tr("player_repeat_all"),
        RepeatLoopMode.ONE to strings.tr("player_repeat_one")
    )

    ObsidianModalSheet(onDismiss = onDismiss) {
        Text(text = strings.tr("srv_default_repeat"), style = ObsidianTypography.headingMd(palette))
        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            modes.forEach { (mode, label) ->
                ObsidianTactileButton(
                    label = label,
                    isPrimary = state.repeatMode == mode,
                    fullWidth = true,
                    onClick = {
                        container.playerViewModel.setRepeatMode(mode)
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun QueueModalSheet(
    container: AppContainer,
    onDismiss: () -> Unit
) {
    val palette = ObsidianTheme.palette
    val strings = AppLocalizations.current()
    val state by container.playerViewModel.state.collectAsState()

    ObsidianModalSheet(onDismiss = onDismiss) {
        Text(
            text = "${strings.tr("player_queue")} (${state.queue.size})",
            style = ObsidianTypography.headingMd(palette)
        )
        Spacer(modifier = Modifier.height(10.dp))
        if (state.queue.isEmpty()) {
            Text(text = strings.tr("player_queue_empty"), style = ObsidianTypography.bodyMd(palette))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(state.queue, key = { idx, t -> "${t.id}_$idx" }) { idx, track ->
                    val isCurrent = idx == state.currentIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(ObsidianRadius.md))
                            .background(if (isCurrent) palette.accentPrimary.copy(alpha = 0.14f) else palette.surfaceConsole)
                            .clickable { container.playerViewModel.skipToIndex(idx) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "%02d".format(idx + 1),
                            style = ObsidianTypography.technicalMono(
                                palette,
                                11f,
                                if (isCurrent) palette.accentPrimary else palette.textMuted
                            ),
                            modifier = Modifier.width(26.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = ObsidianTypography.titleSm(palette).copy(
                                    color = if (isCurrent) palette.accentPrimary else palette.textPrimary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist,
                                style = ObsidianTypography.bodySm(palette),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (idx > 0) {
                            ObsidianIconTrigger(
                                icon = Icons.Rounded.ArrowUpward,
                                size = 28.dp,
                                iconSize = 15.dp,
                                onClick = { container.playerViewModel.reorderQueue(idx, idx - 1) }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        if (idx < state.queue.lastIndex) {
                            ObsidianIconTrigger(
                                icon = Icons.Rounded.ArrowDownward,
                                size = 28.dp,
                                iconSize = 15.dp,
                                onClick = { container.playerViewModel.reorderQueue(idx, idx + 1) }
                            )
                        }
                    }
                }
            }
        }
    }
}
