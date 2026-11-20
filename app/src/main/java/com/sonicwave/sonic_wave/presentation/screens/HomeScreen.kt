package com.sonicwave.sonic_wave.presentation.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonicwave.sonic_wave.AppContainer
import com.sonicwave.sonic_wave.core.l10n.AppLocalizations
import com.sonicwave.sonic_wave.core.utils.QrCodeView
import com.sonicwave.sonic_wave.core.utils.ServerQrPayloadCodec
import com.sonicwave.sonic_wave.designsystem.components.ObsidianArtworkFrame
import com.sonicwave.sonic_wave.designsystem.components.ObsidianConsoleCard
import com.sonicwave.sonic_wave.designsystem.components.ObsidianIconTrigger
import com.sonicwave.sonic_wave.designsystem.components.ObsidianLoadingPanel
import com.sonicwave.sonic_wave.designsystem.components.ObsidianModalSheet
import com.sonicwave.sonic_wave.designsystem.components.ObsidianSwitchRow
import com.sonicwave.sonic_wave.designsystem.components.ObsidianTactileButton
import com.sonicwave.sonic_wave.designsystem.components.ObsidianTopBar
import com.sonicwave.sonic_wave.designsystem.components.ObsidianTrackRow
import com.sonicwave.sonic_wave.designsystem.components.ObsidianAlbumShowcaseCard
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianRadius
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianSpacing
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianTheme
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianTypography
import com.sonicwave.sonic_wave.domain.models.AlbumEntity
import com.sonicwave.sonic_wave.domain.models.MediaItemEntity
import com.sonicwave.sonic_wave.domain.repository.AlbumListType
import com.sonicwave.sonic_wave.domain.services.ObsidianThemeMode
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    container: AppContainer,
    onNavigateToServers: () -> Unit
) {
    val palette = ObsidianTheme.palette
    val strings = AppLocalizations.current()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val settings by container.settingsService.settings.collectAsState()
    val playerState by container.playerViewModel.state.collectAsState()
    val downloadState by container.downloadViewModel.state.collectAsState()
    val localTracks by container.localAudioService.tracks.collectAsState()
    val telemetry by container.playbackController.telemetry.collectAsState()

    var recentAlbums by remember { mutableStateOf<List<AlbumEntity>>(emptyList()) }
    var frequentAlbums by remember { mutableStateOf<List<AlbumEntity>>(emptyList()) }
    var quickMix by remember { mutableStateOf<List<MediaItemEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }

    var selectedAlbumForModal by remember { mutableStateOf<AlbumEntity?>(null) }
    var selectedTrackForActions by remember { mutableStateOf<MediaItemEntity?>(null) }
    var showQrShareModal by remember { mutableStateOf(false) }

    fun loadDashboard(forceRefresh: Boolean = false) {
        scope.launch {
            isLoading = true
            hasError = false
            val profile = container.repository.currentProfile
            if (profile.id == "unconfigured") {
                isLoading = false
                return@launch
            }
            val recent = runCatching {
                container.repository.getAlbumList(AlbumListType.NEWEST, size = 12, forceRefresh = forceRefresh)
            }.getOrNull()
            val frequent = runCatching {
                container.repository.getAlbumList(AlbumListType.FREQUENT, size = 8, forceRefresh = forceRefresh)
            }.getOrNull()
            val mix = runCatching {
                container.repository.getQuickMix(count = 20, forceRefresh = forceRefresh)
            }.getOrNull()

            if (recent != null) recentAlbums = recent
            if (frequent != null) frequentAlbums = frequent
            if (mix != null) quickMix = mix
            hasError = recent == null && frequent == null && mix == null
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadDashboard(forceRefresh = false)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ObsidianTopBar(
            title = strings.tr("home_title"),
            subtitleLabel = strings.tr("home_subtitle"),
            actions = {
                ObsidianIconTrigger(
                    icon = if (settings.themeMode == ObsidianThemeMode.OBSIDIAN_DARK) {
                        Icons.Rounded.LightMode
                    } else {
                        Icons.Rounded.DarkMode
                    },
                    onClick = {
                        scope.launch {
                            container.settingsService.updateSettings {
                                it.copy(
                                    themeMode = when (it.themeMode) {
                                        ObsidianThemeMode.OBSIDIAN_DARK -> ObsidianThemeMode.PURE_OLED
                                        ObsidianThemeMode.PURE_OLED -> ObsidianThemeMode.WARM_ALABASTER
                                        ObsidianThemeMode.WARM_ALABASTER -> ObsidianThemeMode.OBSIDIAN_DARK
                                    }
                                )
                            }
                        }
                    }
                )
                if (container.repository.currentProfile.id != "unconfigured") {
                    ObsidianIconTrigger(
                        icon = Icons.Rounded.QrCode2,
                        onClick = { showQrShareModal = true }
                    )
                    ObsidianIconTrigger(
                        icon = Icons.Rounded.Refresh,
                        onClick = { loadDashboard(forceRefresh = true) }
                    )
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = ObsidianSpacing.md,
                end = ObsidianSpacing.md,
                top = ObsidianSpacing.md,
                bottom = ObsidianSpacing.dockClearance
            ),
            verticalArrangement = Arrangement.spacedBy(ObsidianSpacing.lg)
        ) {
            // Studio Console Deck
            item {
                val profile = container.repository.currentProfile
                ObsidianConsoleCard(
                    elevated = true,
                    onTap = onNavigateToServers
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (profile.id == "unconfigured") palette.statusWarning else palette.statusOnline
                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = strings.tr("home_active_node"),
                                    style = ObsidianTypography.studioLabel(palette)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (profile.id == "unconfigured") strings.tr("home_no_server") else profile.name,
                                style = ObsidianTypography.headingMd(palette),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (profile.id == "unconfigured") {
                                    "${localTracks.size} ${strings.tr("common_tracks")}"
                                } else {
                                    "${profile.protocol.displayName} • ${profile.primaryUrl}"
                                },
                                style = ObsidianTypography.technicalMono(palette, 11f, palette.textMuted),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (quickMix.isNotEmpty() || localTracks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        ObsidianTactileButton(
                            label = strings.tr("home_instant_mix"),
                            icon = Icons.Rounded.AutoAwesome,
                            isPrimary = true,
                            fullWidth = true,
                            onClick = {
                                val pool = quickMix.ifEmpty { localTracks }
                                if (pool.isNotEmpty()) {
                                    container.playerViewModel.loadQueue(pool.shuffled())
                                }
                            }
                        )
                    }
                }
            }

            // Local Device Library
            if (localTracks.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = strings.tr("home_local_library"),
                            style = ObsidianTypography.studioLabel(palette, palette.accentPrimary)
                        )
                        localTracks.take(6).forEachIndexed { idx, track ->
                            ObsidianTrackRow(
                                track = track,
                                index = idx,
                                repository = container.repository,
                                isActive = playerState.currentTrack?.id == track.id,
                                isPlaying = playerState.isPlaying,
                                isOfflineReady = true,
                                onTap = { container.playerViewModel.loadQueue(localTracks, initialIndex = idx) }
                            )
                        }
                    }
                }
            }

            // Loading / Error
            if (isLoading && recentAlbums.isEmpty()) {
                item {
                    ObsidianLoadingPanel(label = strings.tr("home_vinyl_showcase"), skeletonCount = 3)
                }
            } else if (hasError && recentAlbums.isEmpty() && container.repository.currentProfile.id != "unconfigured") {
                item {
                    ObsidianConsoleCard {
                        Text(text = strings.tr("home_empty_albums"), style = ObsidianTypography.bodyMd(palette))
                        Spacer(modifier = Modifier.height(10.dp))
                        ObsidianTactileButton(
                            label = strings.tr("home_retry"),
                            icon = Icons.Rounded.Refresh,
                            onClick = { loadDashboard(forceRefresh = true) }
                        )
                    }
                }
            }

            // Vinyl Showcase // Newest
            if (recentAlbums.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = strings.tr("home_vinyl_showcase"),
                            style = ObsidianTypography.studioLabel(palette, palette.accentPrimary)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(recentAlbums, key = { it.id }) { album ->
                                ObsidianAlbumShowcaseCard(
                                    album = album,
                                    repository = container.repository,
                                    onPlay = {
                                        scope.launch {
                                            val details = runCatching { container.repository.getAlbumDetails(album.id) }.getOrNull()
                                            if (details != null && details.tracks.isNotEmpty()) {
                                                container.playerViewModel.loadQueue(details.tracks)
                                            }
                                        }
                                    },
                                    onTapDetails = { selectedAlbumForModal = album }
                                )
                            }
                        }
                    }
                }
            }

            // Heavy Rotation // Frequent
            if (frequentAlbums.isNotEmpty()) {
                item {
                    Text(
                        text = strings.tr("home_heavy_rotation"),
                        style = ObsidianTypography.studioLabel(palette, palette.accentPrimary)
                    )
                }
                itemsIndexed(frequentAlbums, key = { _, a -> "freq_${a.id}" }) { idx, album ->
                    ObsidianConsoleCard(
                        padding = PaddingValues(12.dp),
                        onTap = { selectedAlbumForModal = album }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "%02d".format(idx + 1),
                                style = ObsidianTypography.technicalMono(palette, 13f, palette.accentPrimary),
                                modifier = Modifier.width(28.dp)
                            )
                            ObsidianArtworkFrame(
                                repository = container.repository,
                                coverArtId = album.coverArtId,
                                size = 50.dp,
                                cornerRadius = ObsidianRadius.sm
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = album.name,
                                    style = ObsidianTypography.titleSm(palette),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = album.artist,
                                    style = ObsidianTypography.bodySm(palette),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            ObsidianIconTrigger(
                                icon = Icons.Rounded.PlayArrow,
                                onClick = {
                                    scope.launch {
                                        val details = runCatching { container.repository.getAlbumDetails(album.id) }.getOrNull()
                                        if (details != null && details.tracks.isNotEmpty()) {
                                            container.playerViewModel.loadQueue(details.tracks)
                                        }
                                    }
                                },
                                size = 36.dp,
                                isPrimaryFilled = true
                            )
                        }
                    }
                }
            }

            // Direct Stream // Quick Mix
            if (quickMix.isNotEmpty()) {
                item {
                    Text(
                        text = strings.tr("home_quick_mix"),
                        style = ObsidianTypography.studioLabel(palette, palette.accentPrimary)
                    )
                }
                itemsIndexed(quickMix.take(8), key = { _, t -> "mix_${t.id}" }) { idx, track ->
                    ObsidianTrackRow(
                        track = track,
                        index = idx,
                        repository = container.repository,
                        isActive = playerState.currentTrack?.id == track.id,
                        isPlaying = playerState.isPlaying,
                        isOfflineReady = downloadState.isTrackOffline(track.id),
                        downloadJob = downloadState.jobsByTrackId[track.id],
                        onTap = { container.playerViewModel.loadQueue(quickMix, initialIndex = idx) },
                        onLongPress = { selectedTrackForActions = track },
                        onDownloadTap = { container.downloadViewModel.enqueueDownloads(listOf(track), track.album) }
                    )
                }
            }

            // Studio Telemetry Card
            if (telemetry.totalTracksPlayed > 0) {
                item {
                    val mins = (telemetry.totalListeningMs / 60_000L).toInt()
                    ObsidianConsoleCard {
                        Text(
                            text = "STUDIO TELEMETRY",
                            style = ObsidianTypography.studioLabel(palette, palette.accentPrimary)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(text = "${telemetry.totalTracksPlayed}", style = ObsidianTypography.headingMd(palette))
                                Text(text = "PLAYS", style = ObsidianTypography.technicalMono(palette, 10f, palette.textMuted))
                            }
                            Column {
                                Text(text = "${mins}m", style = ObsidianTypography.headingMd(palette))
                                Text(text = "TIME", style = ObsidianTypography.technicalMono(palette, 10f, palette.textMuted))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = telemetry.topCodec ?: "STREAM",
                                    style = ObsidianTypography.headingMd(palette).copy(color = palette.accentPrimary)
                                )
                                Text(
                                    text = telemetry.topArtist ?: "TOP ARTIST",
                                    style = ObsidianTypography.technicalMono(palette, 10f, palette.textMuted),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selectedTrackForActions?.let { track ->
        ObsidianModalSheet(onDismiss = { selectedTrackForActions = null }) {
            Text(text = track.title, style = ObsidianTypography.headingMd(palette), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = track.artist, style = ObsidianTypography.bodySm(palette))
            Spacer(modifier = Modifier.height(14.dp))
            ObsidianTactileButton(
                label = "Play Next",
                icon = Icons.Rounded.PlaylistPlay,
                isPrimary = true,
                fullWidth = true,
                onClick = {
                    container.playerViewModel.playNext(track)
                    Toast.makeText(context, "Added to Play Next", Toast.LENGTH_SHORT).show()
                    selectedTrackForActions = null
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            ObsidianTactileButton(
                label = "Start Smart Radio",
                icon = Icons.Rounded.Radio,
                fullWidth = true,
                onClick = {
                    container.playerViewModel.startTrackRadio(track)
                    selectedTrackForActions = null
                }
            )
            if (!track.serverId.startsWith("local_")) {
                Spacer(modifier = Modifier.height(8.dp))
                ObsidianTactileButton(
                    label = strings.tr("player_offline_cache"),
                    icon = Icons.Rounded.Download,
                    fullWidth = true,
                    onClick = {
                        container.downloadViewModel.enqueueDownloads(listOf(track), track.album)
                        selectedTrackForActions = null
                    }
                )
            }
        }
    }

    selectedAlbumForModal?.let { album ->
        AlbumDetailsModalSheet(
            album = album,
            container = container,
            onDismiss = { selectedAlbumForModal = null }
        )
    }

    if (showQrShareModal) {
        QuickQrShareSheet(
            container = container,
            onDismiss = { showQrShareModal = false }
        )
    }
}

@Composable
fun AlbumDetailsModalSheet(
    album: AlbumEntity,
    container: AppContainer,
    onDismiss: () -> Unit
) {
    val palette = ObsidianTheme.palette
    val strings = AppLocalizations.current()
    val playerState by container.playerViewModel.state.collectAsState()
    val downloadState by container.downloadViewModel.state.collectAsState()

    var detailedAlbum by remember(album.id) { mutableStateOf(album) }
    var isLoading by remember(album.id) { mutableStateOf(album.tracks.isEmpty()) }

    LaunchedEffect(album.id) {
        if (album.tracks.isEmpty()) {
            runCatching { container.repository.getAlbumDetails(album.id) }.onSuccess {
                detailedAlbum = it
            }
            isLoading = false
        }
    }

    ObsidianModalSheet(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ObsidianArtworkFrame(
                repository = container.repository,
                coverArtId = detailedAlbum.coverArtId,
                size = 76.dp,
                cornerRadius = ObsidianRadius.md,
                requestResolution = 500
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = detailedAlbum.name,
                    style = ObsidianTypography.headingMd(palette),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = detailedAlbum.artist,
                    style = ObsidianTypography.bodyMd(palette),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = buildString {
                    detailedAlbum.year?.let { append("$it • ") }
                    append("${detailedAlbum.tracks.size.takeIf { it > 0 } ?: detailedAlbum.songCount} TRK")
                }
                Text(text = meta, style = ObsidianTypography.technicalMono(palette, 11f, palette.textMuted))
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ObsidianTactileButton(
                label = strings.tr("lib_play_album"),
                icon = Icons.Rounded.PlayArrow,
                isPrimary = true,
                modifier = Modifier.weight(1f),
                onClick = {
                    if (detailedAlbum.tracks.isNotEmpty()) {
                        container.playerViewModel.loadQueue(detailedAlbum.tracks)
                        onDismiss()
                    }
                }
            )
            ObsidianTactileButton(
                label = strings.tr("lib_download_album"),
                icon = Icons.Rounded.Download,
                modifier = Modifier.weight(1f),
                onClick = {
                    if (detailedAlbum.tracks.isNotEmpty()) {
                        container.downloadViewModel.enqueueDownloads(detailedAlbum.tracks, detailedAlbum.name)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        if (isLoading) {
            ObsidianLoadingPanel(skeletonCount = 3)
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(detailedAlbum.tracks, key = { _, t -> t.id }) { idx, track ->
                    ObsidianTrackRow(
                        track = track,
                        index = idx,
                        repository = container.repository,
                        isActive = playerState.currentTrack?.id == track.id,
                        isPlaying = playerState.isPlaying,
                        isOfflineReady = downloadState.isTrackOffline(track.id),
                        downloadJob = downloadState.jobsByTrackId[track.id],
                        onTap = {
                            container.playerViewModel.loadQueue(detailedAlbum.tracks, initialIndex = idx)
                            onDismiss()
                        },
                        onDownloadTap = {
                            container.downloadViewModel.enqueueDownloads(listOf(track), detailedAlbum.name)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun QuickQrShareSheet(
    container: AppContainer,
    onDismiss: () -> Unit,
    profileOverride: com.sonicwave.sonic_wave.domain.models.ServerProfile? = null
) {
    val palette = ObsidianTheme.palette
    val strings = AppLocalizations.current()
    val context = LocalContext.current
    val profile = profileOverride ?: container.repository.currentProfile
    var includePassword by remember { mutableStateOf(false) }
    val payload = remember(profile, includePassword) {
        ServerQrPayloadCodec.encodeProfile(profile, includePassword = includePassword)
    }

    ObsidianModalSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = strings.tr("home_qr_share_title"), style = ObsidianTypography.headingMd(palette))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = profile.name, style = ObsidianTypography.bodySm(palette))
            Spacer(modifier = Modifier.height(16.dp))
            QrCodeView(data = payload, size = 210.dp)
            Spacer(modifier = Modifier.height(14.dp))
            ObsidianSwitchRow(
                title = strings.tr("home_include_password"),
                value = includePassword,
                onChanged = { includePassword = it }
            )
            Spacer(modifier = Modifier.height(10.dp))
            ObsidianTactileButton(
                label = strings.tr("home_copy_link"),
                icon = Icons.Rounded.ContentCopy,
                isPrimary = true,
                fullWidth = true,
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("SonicWave Server", payload))
                    Toast.makeText(context, strings.tr("common_copied"), Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            )
        }
    }
}
