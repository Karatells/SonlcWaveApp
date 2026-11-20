package com.sonicwave.sonic_wave.presentation.screens

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonicwave.sonic_wave.AppContainer
import com.sonicwave.sonic_wave.designsystem.components.ObsidianArtworkFrame
import com.sonicwave.sonic_wave.designsystem.components.ObsidianIconTrigger
import com.sonicwave.sonic_wave.designsystem.components.ObsidianLiveEqualizerBadge
import com.sonicwave.sonic_wave.designsystem.painters.formatDurationMs
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianRadius
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianSpacing
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianTheme
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianTypography
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayerBar(
    container: AppContainer,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = ObsidianTheme.palette
    val state by container.playerViewModel.state.collectAsState()
    val queue = state.queue
    if (queue.isEmpty()) return

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

    val shape = RoundedCornerShape(ObsidianRadius.lg)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ObsidianSpacing.md)
            .shadow(14.dp, shape, ambientColor = palette.ambientGlow)
            .clip(shape)
            .background(palette.surfaceGlass)
            .border(1.dp, palette.hairlineBorder, shape)
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount < -18f) onExpand()
                }
            }
            .clickable(onClick = onExpand)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val track = queue.getOrNull(page) ?: return@HorizontalPager
            val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = if (isLandscape) 6.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ObsidianArtworkFrame(
                    repository = container.repository,
                    coverArtId = track.coverArtId,
                    size = if (isLandscape) 38.dp else 44.dp,
                    cornerRadius = ObsidianRadius.sm
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ObsidianLiveEqualizerBadge(isPlaying = state.isPlaying && page == state.currentIndex, size = 12.dp)
                        Text(
                            text = track.title,
                            style = ObsidianTypography.titleSm(palette),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = track.artist,
                            style = ObsidianTypography.bodySm(palette),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(
                            text = "${formatDurationMs(state.positionMs)} / ${formatDurationMs(track.durationMs)}",
                            style = ObsidianTypography.technicalMono(palette, 10f, palette.textMuted)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                ObsidianIconTrigger(
                    icon = if (track.isStarred) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    size = 34.dp,
                    iconSize = 18.dp,
                    isActive = track.isStarred,
                    onClick = { container.playerViewModel.toggleFavorite() }
                )
                Spacer(modifier = Modifier.width(6.dp))
                if (state.isBuffering) {
                    Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = palette.accentPrimary
                        )
                    }
                } else {
                    ObsidianIconTrigger(
                        icon = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        size = 38.dp,
                        iconSize = 22.dp,
                        isPrimaryFilled = true,
                        onClick = { container.playerViewModel.togglePlayPause() }
                    )
                }
            }
        }

        // Bottom 3dp progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(palette.surfaceElevated)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(state.progressFraction.coerceIn(0f, 1f))
                    .height(3.dp)
                    .background(palette.accentPrimary)
            )
        }
    }
}
