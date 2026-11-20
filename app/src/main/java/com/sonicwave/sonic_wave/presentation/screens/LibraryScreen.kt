package com.sonicwave.sonic_wave.presentation.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonicwave.sonic_wave.AppContainer
import com.sonicwave.sonic_wave.core.l10n.AppLocalizations
import com.sonicwave.sonic_wave.designsystem.components.ObsidianAlbumShowcaseCard
import com.sonicwave.sonic_wave.designsystem.components.ObsidianArtworkFrame
import com.sonicwave.sonic_wave.designsystem.components.ObsidianConsoleCard
import com.sonicwave.sonic_wave.designsystem.components.ObsidianFilterCapsule
import com.sonicwave.sonic_wave.designsystem.components.ObsidianIconTrigger
import com.sonicwave.sonic_wave.designsystem.components.ObsidianLoadingPanel
import com.sonicwave.sonic_wave.designsystem.components.ObsidianModalSheet
import com.sonicwave.sonic_wave.designsystem.components.ObsidianPlaylistMosaicCard
import com.sonicwave.sonic_wave.designsystem.components.ObsidianTactileButton
import com.sonicwave.sonic_wave.designsystem.components.ObsidianTopBar
import com.sonicwave.sonic_wave.designsystem.components.ObsidianTrackRow
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianRadius
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianSpacing
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianTheme
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianTypography
import com.sonicwave.sonic_wave.domain.models.AlbumEntity
import com.sonicwave.sonic_wave.domain.models.ArtistEntity
import com.sonicwave.sonic_wave.domain.models.GenreEntity
import com.sonicwave.sonic_wave.domain.models.MediaItemEntity
import com.sonicwave.sonic_wave.domain.models.MusicFolderNodeEntity
import com.sonicwave.sonic_wave.domain.models.PlaylistEntity
import com.sonicwave.sonic_wave.domain.repository.AlbumListType
import com.sonicwave.sonic_wave.presentation.widgets.AddLocalFolderByPathSheet
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(container: AppContainer) {
    val palette = ObsidianTheme.palette
    val strings = AppLocalizations.current()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val playerState by container.playerViewModel.state.collectAsState()
    val downloadState by container.downloadViewModel.state.collectAsState()
    val localFolders by container.localAudioService.folders.collectAsState()
    val localTracks by container.localAudioService.tracks.collectAsState()
    val isScanningLocal by container.localAudioService.isScanning.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }

    var playlists by remember { mutableStateOf<List<PlaylistEntity>>(emptyList()) }
    var albums by remember { mutableStateOf<List<AlbumEntity>>(emptyList()) }
    var tracks by remember { mutableStateOf<List<MediaItemEntity>>(emptyList()) }
    var artists by remember { mutableStateOf<List<ArtistEntity>>(emptyList()) }
    var genres by remember { mutableStateOf<List<GenreEntity>>(emptyList()) }
    var folderNodes by remember { mutableStateOf<List<MusicFolderNodeEntity>>(emptyList()) }
    val folderBreadcrumbs = remember { mutableStateListOf<Pair<String, String>>() }
    var podcasts by remember { mutableStateOf<List<MediaItemEntity>>(emptyList()) }
    var audiobooks by remember { mutableStateOf<List<MediaItemEntity>>(emptyList()) }

    var selectedAlbum by remember { mutableStateOf<AlbumEntity?>(null) }
    var selectedPlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }
    var selectedArtist by remember { mutableStateOf<ArtistEntity?>(null) }
    var showAddPathSheet by remember { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            scope.launch { container.localAudioService.addFolderByUri(uri) }
        }
    }

    fun loadTab(tabIndex: Int, forceRefresh: Boolean = false) {
        if (tabIndex == 8) return
        if (container.repository.currentProfile.id == "unconfigured") return
        scope.launch {
            isLoading = true
            runCatching {
                when (tabIndex) {
                    0 -> playlists = container.repository.getPlaylists(forceRefresh)
                    1 -> albums = container.repository.getAlbumList(AlbumListType.ALPHABETICAL_BY_NAME, size = 60, forceRefresh = forceRefresh)
                    2 -> tracks = container.repository.getAllTracks(count = 150, forceRefresh = forceRefresh)
                    3 -> artists = container.repository.getArtists(forceRefresh)
                    4 -> genres = container.repository.getGenres(forceRefresh)
                    5 -> {
                        val currentId = folderBreadcrumbs.lastOrNull()?.first
                        folderNodes = if (currentId == null) {
                            container.repository.getMusicRootEntries(forceRefresh)
                        } else {
                            container.repository.getMusicDirectory(currentId, forceRefresh)
                        }
                    }
                    6 -> podcasts = container.repository.getPodcastEpisodes(forceRefresh = forceRefresh)
                    7 -> audiobooks = container.repository.getAudiobooks(forceRefresh)
                }
            }
            isLoading = false
        }
    }

    LaunchedEffect(selectedTab) {
        loadTab(selectedTab, forceRefresh = false)
    }

    val tabLabels = listOf(
        strings.tr("sec_playlists") to playlists.size,
        strings.tr("sec_albums") to albums.size,
        strings.tr("sec_tracks") to tracks.size,
        strings.tr("sec_artists") to artists.size,
        strings.tr("sec_genres") to genres.size,
        strings.tr("sec_folders") to folderNodes.size,
        strings.tr("sec_podcasts") to podcasts.size,
        strings.tr("sec_audiobooks") to audiobooks.size,
        strings.tr("sec_local") to localTracks.size
    )

    Column(modifier = Modifier.fillMaxSize()) {
        ObsidianTopBar(
            title = strings.tr("library_title"),
            subtitleLabel = strings.tr("library_subtitle"),
            actions = {
                ObsidianIconTrigger(
                    icon = Icons.Rounded.Refresh,
                    onClick = {
                        if (selectedTab == 8) {
                            scope.launch { container.localAudioService.rescanAll() }
                        } else {
                            loadTab(selectedTab, forceRefresh = true)
                        }
                    }
                )
            },
            bottomBar = {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = ObsidianSpacing.md, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(tabLabels) { idx, (label, count) ->
                        ObsidianFilterCapsule(
                            label = label,
                            selected = selectedTab == idx,
                            badgeText = if (count > 0) "$count" else null,
                            onClick = { selectedTab = idx }
                        )
                    }
                }
            }
        )

        // Active Download Banner
        if (downloadState.activeJobsCount > 0) {
            ObsidianConsoleCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ObsidianSpacing.md, vertical = 6.dp),
                padding = PaddingValues(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = strings.tr("lib_downloading_banner", mapOf("count" to "${downloadState.activeJobsCount}")),
                        style = ObsidianTypography.titleSm(palette)
                    )
                    ObsidianTactileButton(
                        label = strings.tr("lib_lru_clean"),
                        padding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                        onClick = { container.downloadViewModel.clearAutoCache() }
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { downloadState.overallProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = palette.accentPrimary,
                    trackColor = palette.surfaceElevated
                )
            }
        }

        if (isLoading && selectedTab != 8) {
            ObsidianLoadingPanel(skeletonCount = 5)
        } else {
            when (selectedTab) {
                0 -> { // Playlists
                    if (playlists.isEmpty()) EmptyLibraryState(strings.tr("lib_empty"))
                    else LazyColumn(
                        contentPadding = PaddingValues(
                            start = ObsidianSpacing.md, end = ObsidianSpacing.md,
                            top = ObsidianSpacing.sm, bottom = ObsidianSpacing.dockClearance
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(playlists, key = { it.id }) { pl ->
                            ObsidianPlaylistMosaicCard(
                                playlist = pl,
                                repository = container.repository,
                                onTap = { selectedPlaylist = pl },
                                onPlay = {
                                    scope.launch {
                                        val details = runCatching { container.repository.getPlaylistDetails(pl.id) }.getOrNull()
                                        if (details != null && details.tracks.isNotEmpty()) {
                                            container.playerViewModel.loadQueue(details.tracks)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
                1 -> { // Albums
                    if (albums.isEmpty()) EmptyLibraryState(strings.tr("lib_empty"))
                    else LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(
                            start = ObsidianSpacing.md, end = ObsidianSpacing.md,
                            top = ObsidianSpacing.sm, bottom = ObsidianSpacing.dockClearance
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(albums, key = { it.id }) { album ->
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
                                onTapDetails = { selectedAlbum = album }
                            )
                        }
                    }
                }
                2 -> { // Tracks
                    if (tracks.isEmpty()) EmptyLibraryState(strings.tr("lib_empty"))
                    else LazyColumn(
                        contentPadding = PaddingValues(
                            start = ObsidianSpacing.md, end = ObsidianSpacing.md,
                            top = ObsidianSpacing.sm, bottom = ObsidianSpacing.dockClearance
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(tracks, key = { _, t -> t.id }) { idx, track ->
                            ObsidianTrackRow(
                                track = track,
                                index = idx,
                                repository = container.repository,
                                isActive = playerState.currentTrack?.id == track.id,
                                isPlaying = playerState.isPlaying,
                                isOfflineReady = downloadState.isTrackOffline(track.id),
                                downloadJob = downloadState.jobsByTrackId[track.id],
                                onTap = {
                                    container.playerViewModel.loadQueue(
                                        tracks,
                                        initialIndex = idx,
                                        initialPositionMs = track.bookmarkPositionMs ?: 0L
                                    )
                                },
                                onDownloadTap = { container.downloadViewModel.enqueueDownloads(listOf(track), track.album) }
                            )
                        }
                    }
                }
                3 -> { // Artists
                    if (artists.isEmpty()) EmptyLibraryState(strings.tr("lib_empty"))
                    else LazyColumn(
                        contentPadding = PaddingValues(
                            start = ObsidianSpacing.md, end = ObsidianSpacing.md,
                            top = ObsidianSpacing.sm, bottom = ObsidianSpacing.dockClearance
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(artists, key = { it.id }) { artist ->
                            ObsidianConsoleCard(
                                padding = PaddingValues(12.dp),
                                onTap = { selectedArtist = artist }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ObsidianArtworkFrame(
                                        repository = container.repository,
                                        coverArtId = artist.coverArtId,
                                        size = 48.dp,
                                        cornerRadius = ObsidianRadius.capsule
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = artist.name, style = ObsidianTypography.titleSm(palette))
                                        Text(
                                            text = "${artist.albumCount} albums",
                                            style = ObsidianTypography.technicalMono(palette, 11f, palette.textMuted)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                4 -> { // Genres
                    if (genres.isEmpty()) EmptyLibraryState(strings.tr("lib_empty"))
                    else LazyColumn(
                        contentPadding = PaddingValues(
                            start = ObsidianSpacing.md, end = ObsidianSpacing.md,
                            top = ObsidianSpacing.sm, bottom = ObsidianSpacing.dockClearance
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(genres, key = { it.name }) { genre ->
                            ObsidianConsoleCard(
                                padding = PaddingValues(14.dp),
                                onTap = {
                                    scope.launch {
                                        val songs = runCatching { container.repository.getSongsByGenre(genre.name) }.getOrDefault(emptyList())
                                        if (songs.isNotEmpty()) {
                                            container.playerViewModel.loadQueue(songs)
                                        }
                                    }
                                }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(text = genre.name, style = ObsidianTypography.titleSm(palette))
                                        Text(
                                            text = "${genre.songCount} ${strings.tr("common_tracks")} • ${genre.albumCount} albums",
                                            style = ObsidianTypography.technicalMono(palette, 11f, palette.textMuted)
                                        )
                                    }
                                    ObsidianIconTrigger(icon = Icons.Rounded.PlayArrow, onClick = null, size = 34.dp, isPrimaryFilled = true)
                                }
                            }
                        }
                    }
                }
                5 -> { // Folders
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = ObsidianSpacing.md, end = ObsidianSpacing.md,
                            top = ObsidianSpacing.sm, bottom = ObsidianSpacing.dockClearance
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (folderBreadcrumbs.isNotEmpty()) {
                            item {
                                ObsidianConsoleCard(
                                    padding = PaddingValues(12.dp),
                                    onTap = {
                                        folderBreadcrumbs.removeLastOrNull()
                                        loadTab(5)
                                    }
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        ObsidianIconTrigger(icon = Icons.Rounded.ArrowUpward, onClick = null, size = 34.dp)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = "${strings.tr("lib_up_level")} (${folderBreadcrumbs.lastOrNull()?.second ?: strings.tr("lib_root_dir")})",
                                            style = ObsidianTypography.titleSm(palette)
                                        )
                                    }
                                }
                            }
                        }
                        itemsIndexed(folderNodes, key = { idx, n -> "${n.id}_$idx" }) { _, node ->
                            if (node.isDirectory) {
                                ObsidianConsoleCard(
                                    padding = PaddingValues(12.dp),
                                    onTap = {
                                        folderBreadcrumbs.add(node.id to node.title)
                                        loadTab(5)
                                    }
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        ObsidianIconTrigger(icon = Icons.Rounded.Folder, onClick = null, size = 38.dp, isActive = true)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = node.title,
                                            style = ObsidianTypography.titleSm(palette),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            } else if (node.track != null) {
                                ObsidianTrackRow(
                                    track = node.track,
                                    index = 0,
                                    repository = container.repository,
                                    isActive = playerState.currentTrack?.id == node.track.id,
                                    isPlaying = playerState.isPlaying,
                                    isOfflineReady = downloadState.isTrackOffline(node.track.id),
                                    onTap = {
                                        val dirTracks = folderNodes.mapNotNull { it.track }
                                        val idx = dirTracks.indexOfFirst { it.id == node.track.id }.coerceAtLeast(0)
                                        container.playerViewModel.loadQueue(dirTracks, initialIndex = idx)
                                    }
                                )
                            }
                        }
                    }
                }
                6 -> { // Podcasts
                    if (podcasts.isEmpty()) EmptyLibraryState(strings.tr("lib_empty"))
                    else LazyColumn(
                        contentPadding = PaddingValues(
                            start = ObsidianSpacing.md, end = ObsidianSpacing.md,
                            top = ObsidianSpacing.sm, bottom = ObsidianSpacing.dockClearance
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(podcasts, key = { _, t -> t.id }) { idx, track ->
                            ObsidianTrackRow(
                                track = track,
                                index = idx,
                                repository = container.repository,
                                isActive = playerState.currentTrack?.id == track.id,
                                isPlaying = playerState.isPlaying,
                                isOfflineReady = downloadState.isTrackOffline(track.id),
                                onTap = {
                                    container.playerViewModel.loadQueue(
                                        podcasts,
                                        initialIndex = idx,
                                        initialPositionMs = track.bookmarkPositionMs ?: 0L
                                    )
                                },
                                onDownloadTap = { container.downloadViewModel.enqueueDownloads(listOf(track), "Podcasts") }
                            )
                        }
                    }
                }
                7 -> { // Audiobooks
                    if (audiobooks.isEmpty()) EmptyLibraryState(strings.tr("lib_empty"))
                    else LazyColumn(
                        contentPadding = PaddingValues(
                            start = ObsidianSpacing.md, end = ObsidianSpacing.md,
                            top = ObsidianSpacing.sm, bottom = ObsidianSpacing.dockClearance
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(audiobooks, key = { _, t -> t.id }) { idx, track ->
                            ObsidianTrackRow(
                                track = track,
                                index = idx,
                                repository = container.repository,
                                isActive = playerState.currentTrack?.id == track.id,
                                isPlaying = playerState.isPlaying,
                                isOfflineReady = downloadState.isTrackOffline(track.id),
                                onTap = {
                                    container.playerViewModel.loadQueue(
                                        audiobooks,
                                        initialIndex = idx,
                                        initialPositionMs = track.bookmarkPositionMs ?: 0L
                                    )
                                },
                                onDownloadTap = { container.downloadViewModel.enqueueDownloads(listOf(track), "Audiobooks") }
                            )
                        }
                    }
                }
                8 -> { // Local Audio
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = ObsidianSpacing.md, end = ObsidianSpacing.md,
                            top = ObsidianSpacing.sm, bottom = ObsidianSpacing.dockClearance
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            ObsidianConsoleCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ObsidianTactileButton(
                                        label = strings.tr("lib_add_folder"),
                                        icon = Icons.Rounded.FolderOpen,
                                        isPrimary = true,
                                        modifier = Modifier.weight(1f),
                                        onClick = { folderPicker.launch(null) }
                                    )
                                    ObsidianTactileButton(
                                        label = strings.tr("lib_add_path"),
                                        icon = Icons.Rounded.CreateNewFolder,
                                        modifier = Modifier.weight(1f),
                                        onClick = { showAddPathSheet = true }
                                    )
                                }
                                if (localTracks.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        ObsidianTactileButton(
                                            label = strings.tr("lib_play_all"),
                                            icon = Icons.Rounded.PlayArrow,
                                            modifier = Modifier.weight(1f),
                                            onClick = { container.playerViewModel.loadQueue(localTracks) }
                                        )
                                        ObsidianTactileButton(
                                            label = strings.tr("lib_shuffle_all"),
                                            icon = Icons.Rounded.Shuffle,
                                            modifier = Modifier.weight(1f),
                                            onClick = { container.playerViewModel.loadQueue(localTracks.shuffled()) }
                                        )
                                    }
                                }
                                if (isScanningLocal) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = palette.accentPrimary)
                                }
                                if (localFolders.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    localFolders.forEach { folder ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(text = folder.name, style = ObsidianTypography.titleSm(palette))
                                                Text(
                                                    text = "${folder.trackCount} ${strings.tr("common_tracks")}",
                                                    style = ObsidianTypography.technicalMono(palette, 10.5f, palette.textMuted)
                                                )
                                            }
                                            ObsidianIconTrigger(
                                                icon = Icons.Rounded.Refresh,
                                                size = 32.dp,
                                                onClick = { scope.launch { container.localAudioService.rescanFolder(folder.id) } }
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            ObsidianIconTrigger(
                                                icon = Icons.Rounded.Delete,
                                                size = 32.dp,
                                                activeColor = palette.statusDanger,
                                                onClick = { scope.launch { container.localAudioService.removeFolder(folder.id) } }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        itemsIndexed(localTracks, key = { _, t -> t.id }) { idx, track ->
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
        }
    }

    selectedAlbum?.let { alb ->
        AlbumDetailsModalSheet(album = alb, container = container, onDismiss = { selectedAlbum = null })
    }

    selectedPlaylist?.let { pl ->
        PlaylistDetailsSheet(playlist = pl, container = container, onDismiss = { selectedPlaylist = null })
    }

    selectedArtist?.let { art ->
        ArtistAlbumsSheet(
            artist = art,
            container = container,
            onAlbumSelected = {
                selectedArtist = null
                selectedAlbum = it
            },
            onDismiss = { selectedArtist = null }
        )
    }

    if (showAddPathSheet) {
        AddLocalFolderByPathSheet(container = container, onDismiss = { showAddPathSheet = false })
    }
}

@Composable
private fun EmptyLibraryState(message: String) {
    val palette = ObsidianTheme.palette
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(ObsidianSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ObsidianConsoleCard {
            Text(text = message, style = ObsidianTypography.bodyMd(palette))
        }
    }
}

@Composable
private fun PlaylistDetailsSheet(
    playlist: PlaylistEntity,
    container: AppContainer,
    onDismiss: () -> Unit
) {
    val palette = ObsidianTheme.palette
    val strings = AppLocalizations.current()
    val playerState by container.playerViewModel.state.collectAsState()
    val downloadState by container.downloadViewModel.state.collectAsState()
    var detailed by remember(playlist.id) { mutableStateOf(playlist) }
    var isLoading by remember(playlist.id) { mutableStateOf(playlist.tracks.isEmpty()) }

    LaunchedEffect(playlist.id) {
        if (playlist.tracks.isEmpty()) {
            runCatching { container.repository.getPlaylistDetails(playlist.id) }.onSuccess { detailed = it }
            isLoading = false
        }
    }

    ObsidianModalSheet(onDismiss = onDismiss) {
        Text(text = detailed.name, style = ObsidianTypography.headingMd(palette))
        Spacer(modifier = Modifier.height(10.dp))
        ObsidianTactileButton(
            label = strings.tr("lib_play_all"),
            icon = Icons.Rounded.PlayArrow,
            isPrimary = true,
            fullWidth = true,
            onClick = {
                if (detailed.tracks.isNotEmpty()) {
                    container.playerViewModel.loadQueue(detailed.tracks)
                    onDismiss()
                }
            }
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (isLoading) {
            ObsidianLoadingPanel(skeletonCount = 3)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(detailed.tracks, key = { idx, t -> "${t.id}_$idx" }) { idx, track ->
                    ObsidianTrackRow(
                        track = track,
                        index = idx,
                        repository = container.repository,
                        isActive = playerState.currentTrack?.id == track.id,
                        isPlaying = playerState.isPlaying,
                        isOfflineReady = downloadState.isTrackOffline(track.id),
                        onTap = {
                            container.playerViewModel.loadQueue(detailed.tracks, initialIndex = idx)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistAlbumsSheet(
    artist: ArtistEntity,
    container: AppContainer,
    onAlbumSelected: (AlbumEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = ObsidianTheme.palette
    var albums by remember(artist.id) { mutableStateOf<List<AlbumEntity>>(emptyList()) }
    var isLoading by remember(artist.id) { mutableStateOf(true) }

    LaunchedEffect(artist.id) {
        runCatching { container.repository.getArtistAlbums(artist.id) }.onSuccess { albums = it }
        isLoading = false
    }

    ObsidianModalSheet(onDismiss = onDismiss) {
        Text(text = artist.name, style = ObsidianTypography.headingMd(palette))
        Spacer(modifier = Modifier.height(12.dp))
        if (isLoading) {
            ObsidianLoadingPanel(skeletonCount = 3)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(albums, key = { it.id }) { album ->
                    ObsidianConsoleCard(
                        padding = PaddingValues(12.dp),
                        onTap = { onAlbumSelected(album) }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ObsidianArtworkFrame(
                                repository = container.repository,
                                coverArtId = album.coverArtId,
                                size = 48.dp,
                                cornerRadius = ObsidianRadius.sm
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = album.name, style = ObsidianTypography.titleSm(palette))
                                Text(
                                    text = "${album.year ?: ""} • ${album.songCount} TRK",
                                    style = ObsidianTypography.technicalMono(palette, 11f, palette.textMuted)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
