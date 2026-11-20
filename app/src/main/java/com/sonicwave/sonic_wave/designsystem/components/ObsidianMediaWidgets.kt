package com.sonicwave.sonic_wave.designsystem.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.sonicwave.sonic_wave.designsystem.painters.ActiveTrackEqBars
import com.sonicwave.sonic_wave.designsystem.painters.VinylGrooveDisc
import com.sonicwave.sonic_wave.designsystem.painters.formatDurationMs
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianRadius
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianSpacing
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianTheme
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianTypography
import com.sonicwave.sonic_wave.domain.models.AlbumEntity
import com.sonicwave.sonic_wave.domain.models.MediaItemEntity
import com.sonicwave.sonic_wave.domain.models.PlaylistEntity
import com.sonicwave.sonic_wave.domain.repository.MediaServerRepository
import com.sonicwave.sonic_wave.presentation.viewmodel.DownloadItemStatus
import com.sonicwave.sonic_wave.presentation.viewmodel.DownloadJobItem
import java.io.File

@Composable
fun ObsidianAudioSpecBadge(
    suffix: String?,
    bitrateKbps: Int?,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
    isHighlighted: Boolean = false
) {
    val palette = ObsidianTheme.palette
    val fmt = suffix?.uppercase()?.takeIf { it.isNotBlank() } ?: "AUDIO"
    val isLossless = fmt in setOf("FLAC", "ALAC", "WAV", "AIFF")
    val label = when {
        isLossless && isCompact -> "$fmt 24/96"
        isLossless -> "$fmt 24-BIT / 96KHZ"
        bitrateKbps != null && bitrateKbps > 0 -> "$fmt • ${bitrateKbps}K"
        else -> fmt
    }
    val fg = if (isHighlighted || isLossless) palette.accentPrimary else palette.textMuted
    val bg = fg.copy(alpha = 0.12f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(ObsidianRadius.xs))
            .background(bg)
            .border(1.dp, fg.copy(alpha = 0.25f), RoundedCornerShape(ObsidianRadius.xs))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = ObsidianTypography.technicalMono(palette, if (isCompact) 9.5f else 10.5f, fg),
            maxLines = 1
        )
    }
}

@Composable
fun ObsidianArtworkFrame(
    repository: MediaServerRepository?,
    coverArtId: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    cornerRadius: Dp = ObsidianRadius.sm,
    requestResolution: Int = 300,
    localCoverPath: String? = null
) {
    val palette = ObsidianTheme.palette
    val context = LocalContext.current
    val shape = RoundedCornerShape(cornerRadius)

    val model: Any? = when {
        !localCoverPath.isNullOrBlank() && File(localCoverPath).exists() -> File(localCoverPath)
        !coverArtId.isNullOrBlank() && repository != null -> repository.buildCoverArtUri(coverArtId, size = requestResolution)
        else -> null
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(palette.surfaceChassis)
            .border(1.dp, palette.hairlineBorder, shape),
        contentAlignment = Alignment.Center
    ) {
        if (model == null) {
            VinylGrooveDisc(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(size * 0.12f),
                accentColor = palette.accentPrimary,
                isDark = palette.isDark
            )
        } else {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(model)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    VinylGrooveDisc(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(size * 0.12f),
                        accentColor = palette.accentPrimary,
                        isDark = palette.isDark
                    )
                },
                error = {
                    VinylGrooveDisc(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(size * 0.12f),
                        accentColor = palette.accentPrimary,
                        isDark = palette.isDark
                    )
                }
            )
        }
    }
}

@Composable
fun ObsidianLiveEqualizerBadge(
    isPlaying: Boolean,
    size: Dp = 16.dp,
    color: Color = ObsidianTheme.palette.accentPrimary
) {
    ActiveTrackEqBars(isPlaying = isPlaying, color = color, size = size)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ObsidianTrackRow(
    track: MediaItemEntity,
    index: Int,
    repository: MediaServerRepository?,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    isOfflineReady: Boolean = track.isOfflineAvailable,
    downloadJob: DownloadJobItem? = null,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    onDownloadTap: (() -> Unit)? = null,
    trailingOverride: (@Composable () -> Unit)? = null
) {
    val palette = ObsidianTheme.palette
    val shape = RoundedCornerShape(ObsidianRadius.md)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isActive) palette.accentPrimary.copy(alpha = 0.10f) else Color.Transparent)
            .border(
                width = if (isActive) 1.dp else 0.dp,
                color = if (isActive) palette.accentPrimary.copy(alpha = 0.35f) else Color.Transparent,
                shape = shape
            )
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isActive) {
                ObsidianLiveEqualizerBadge(isPlaying = isPlaying, size = 16.dp)
            } else {
                Text(
                    text = "%02d".format(index + 1),
                    style = ObsidianTypography.technicalMono(palette, 11f, palette.textMuted)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))
        ObsidianArtworkFrame(
            repository = repository,
            coverArtId = track.coverArtId,
            size = 44.dp,
            cornerRadius = ObsidianRadius.sm
        )
        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isOfflineReady) {
                    Icon(
                        imageVector = Icons.Rounded.OfflinePin,
                        contentDescription = null,
                        tint = palette.statusOnline,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = track.title,
                    style = ObsidianTypography.titleSm(palette).copy(
                        color = if (isActive) palette.accentPrimary else palette.textPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = track.artist,
                    style = ObsidianTypography.bodySm(palette),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (!track.suffix.isNullOrBlank()) {
                    ObsidianAudioSpecBadge(
                        suffix = track.suffix,
                        bitrateKbps = track.bitrateKbps,
                        isCompact = true
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = formatDurationMs(track.durationMs),
            style = ObsidianTypography.technicalMono(palette, 11f, palette.textMuted)
        )

        if (trailingOverride != null) {
            Spacer(modifier = Modifier.width(6.dp))
            trailingOverride()
        } else if (onDownloadTap != null) {
            Spacer(modifier = Modifier.width(6.dp))
            when (downloadJob?.status) {
                DownloadItemStatus.DOWNLOADING, DownloadItemStatus.QUEUED -> {
                    CircularProgressIndicator(
                        progress = { downloadJob.progress.coerceIn(0.05f, 1f) },
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = palette.accentPrimary
                    )
                }
                DownloadItemStatus.FAILED -> {
                    ObsidianIconTrigger(
                        icon = Icons.Rounded.ErrorOutline,
                        onClick = onDownloadTap,
                        size = 32.dp,
                        iconSize = 17.dp,
                        activeColor = palette.statusDanger,
                        isActive = true
                    )
                }
                else -> {
                    ObsidianIconTrigger(
                        icon = if (isOfflineReady) Icons.Rounded.CheckCircle else Icons.Rounded.Download,
                        onClick = if (isOfflineReady) null else onDownloadTap,
                        size = 32.dp,
                        iconSize = 17.dp,
                        isActive = isOfflineReady,
                        activeColor = palette.statusOnline
                    )
                }
            }
        }
    }
}

@Composable
fun ObsidianAlbumShowcaseCard(
    album: AlbumEntity,
    repository: MediaServerRepository?,
    onPlay: () -> Unit,
    onTapDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = ObsidianTheme.palette
    ObsidianConsoleCard(
        modifier = modifier.width(176.dp),
        padding = PaddingValues(12.dp),
        onTap = onTapDetails
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
        ) {
            VinylGrooveDisc(
                modifier = Modifier
                    .size(126.dp)
                    .align(Alignment.CenterEnd)
                    .offset(x = 8.dp),
                accentColor = palette.accentPrimary,
                isDark = palette.isDark
            )
            ObsidianArtworkFrame(
                repository = repository,
                coverArtId = album.coverArtId,
                size = 136.dp,
                cornerRadius = ObsidianRadius.md,
                requestResolution = 400,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            ObsidianIconTrigger(
                icon = Icons.Rounded.PlayArrow,
                onClick = onPlay,
                size = 36.dp,
                iconSize = 20.dp,
                isPrimaryFilled = true,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = album.name,
            style = ObsidianTypography.titleSm(palette),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = album.artist,
            style = ObsidianTypography.bodySm(palette),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        val meta = buildString {
            album.year?.let { append("$it • ") }
            append("${album.songCount} TRK")
        }
        Text(
            text = meta,
            style = ObsidianTypography.technicalMono(palette, 10.5f, palette.textMuted),
            maxLines = 1
        )
    }
}

@Composable
fun ObsidianPlaylistMosaicCard(
    playlist: PlaylistEntity,
    repository: MediaServerRepository?,
    onTap: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = ObsidianTheme.palette
    val icons = listOf(Icons.Rounded.Album, Icons.Rounded.MusicNote, Icons.Rounded.GraphicEq, Icons.Rounded.LibraryMusic)

    ObsidianConsoleCard(
        modifier = modifier.fillMaxWidth(),
        padding = PaddingValues(12.dp),
        onTap = onTap
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!playlist.coverArtId.isNullOrBlank()) {
                ObsidianArtworkFrame(
                    repository = repository,
                    coverArtId = playlist.coverArtId,
                    size = 58.dp,
                    cornerRadius = ObsidianRadius.md
                )
            } else {
                Column(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(ObsidianRadius.md))
                        .background(palette.surfaceChassis),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    for (r in 0..1) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            for (c in 0..1) {
                                val idx = r * 2 + c
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                        .background(palette.accentPrimary.copy(alpha = 0.08f + idx * 0.04f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icons[idx],
                                        contentDescription = null,
                                        tint = palette.accentPrimary.copy(alpha = 0.7f),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(ObsidianRadius.xs))
                            .background(palette.accentPrimary.copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "PLAYLIST",
                            style = ObsidianTypography.technicalMono(palette, 9.5f, palette.accentPrimary)
                        )
                    }
                    val mins = (playlist.durationMs / 60_000L).toInt()
                    Text(
                        text = "${playlist.songCount} TRK • ${mins}M",
                        style = ObsidianTypography.technicalMono(palette, 10.5f, palette.textMuted)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = playlist.name,
                    style = ObsidianTypography.titleSm(palette),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!playlist.comment.isNullOrBlank()) {
                    Text(
                        text = playlist.comment,
                        style = ObsidianTypography.bodySm(palette),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            ObsidianIconTrigger(
                icon = Icons.Rounded.PlayArrow,
                onClick = onPlay,
                size = 38.dp,
                isPrimaryFilled = true
            )
        }
    }
}

@Composable
fun ObsidianLoadingPanel(
    label: String? = null,
    skeletonCount: Int = 4,
    modifier: Modifier = Modifier
) {
    val palette = ObsidianTheme.palette
    val transition = rememberInfiniteTransition(label = "loading")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(ObsidianSpacing.md),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!label.isNullOrBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ObsidianLiveEqualizerBadge(isPlaying = true, size = 16.dp)
                Text(
                    text = label,
                    style = ObsidianTypography.studioLabel(palette, palette.accentPrimary)
                )
            }
        }
        repeat(skeletonCount) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(ObsidianRadius.md))
                    .background(palette.surfaceConsole.copy(alpha = alpha))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(ObsidianRadius.sm))
                        .background(palette.surfaceElevated)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.65f)
                            .height(12.dp)
                            .clip(CircleShape)
                            .background(palette.surfaceElevated)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(palette.surfaceElevated.copy(alpha = 0.7f))
                    )
                }
            }
        }
    }
}
