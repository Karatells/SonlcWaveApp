package com.sonicwave.sonic_wave.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sonicwave.sonic_wave.AppContainer
import com.sonicwave.sonic_wave.core.l10n.AppLocalizations
import com.sonicwave.sonic_wave.designsystem.components.ObsidianAlbumShowcaseCard
import com.sonicwave.sonic_wave.designsystem.components.ObsidianConsoleCard
import com.sonicwave.sonic_wave.designsystem.components.ObsidianFilterCapsule
import com.sonicwave.sonic_wave.designsystem.components.ObsidianIconTrigger
import com.sonicwave.sonic_wave.designsystem.components.ObsidianLoadingPanel
import com.sonicwave.sonic_wave.designsystem.components.ObsidianTextField
import com.sonicwave.sonic_wave.designsystem.components.ObsidianTopBar
import com.sonicwave.sonic_wave.designsystem.components.ObsidianTrackRow
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianSpacing
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianTheme
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianTypography
import com.sonicwave.sonic_wave.domain.models.AlbumEntity
import com.sonicwave.sonic_wave.domain.models.MediaContentType
import com.sonicwave.sonic_wave.domain.repository.SearchFilter
import com.sonicwave.sonic_wave.domain.repository.SearchResultBundle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(container: AppContainer) {
    val palette = ObsidianTheme.palette
    val strings = AppLocalizations.current()
    val scope = rememberCoroutineScope()

    val playerState by container.playerViewModel.state.collectAsState()
    val downloadState by container.downloadViewModel.state.collectAsState()
    val localTracks by container.localAudioService.tracks.collectAsState()

    var query by remember { mutableStateOf("") }
    var filterIdx by remember { mutableIntStateOf(0) }
    var isSearching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf(SearchResultBundle()) }
    var selectedAlbum by remember { mutableStateOf<AlbumEntity?>(null) }

    val filterLabels = listOf(
        strings.tr("search_filter_all"),
        strings.tr("search_filter_music"),
        strings.tr("search_filter_podcasts"),
        strings.tr("search_filter_audiobooks"),
        strings.tr("search_filter_2020")
    )

    LaunchedEffect(query, filterIdx) {
        val q = query.trim()
        if (q.isEmpty()) {
            results = SearchResultBundle()
            isSearching = false
            return@LaunchedEffect
        }
        delay(260L)
        isSearching = true
        val contentType = when (filterIdx) {
            1 -> MediaContentType.MUSIC
            2 -> MediaContentType.PODCAST
            3 -> MediaContentType.AUDIOBOOK
            else -> null
        }
        val fromYear = if (filterIdx == 4) 2020 else null
        val toYear = if (filterIdx == 4) 2030 else null

        val remote = if (container.repository.currentProfile.id != "unconfigured") {
            runCatching {
                container.repository.search(
                    SearchFilter(
                        query = q,
                        contentType = contentType,
                        fromYear = fromYear,
                        toYear = toYear
                    )
                )
            }.getOrDefault(SearchResultBundle())
        } else {
            SearchResultBundle()
        }

        val matchingLocal = localTracks.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.artist.contains(q, ignoreCase = true) ||
                it.album.contains(q, ignoreCase = true)
        }

        results = remote.copy(tracks = (remote.tracks + matchingLocal).distinctBy { it.id })
        isSearching = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ObsidianTopBar(
            title = strings.tr("search_title"),
            subtitleLabel = strings.tr("search_subtitle"),
            actions = {
                if (query.isNotEmpty()) {
                    ObsidianIconTrigger(
                        icon = Icons.Rounded.Close,
                        onClick = { query = "" }
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ObsidianSpacing.md, vertical = ObsidianSpacing.sm)
        ) {
            ObsidianTextField(
                value = query,
                onValueChange = { query = it },
                hint = strings.tr("search_hint"),
                prefixIcon = Icons.Rounded.Search
            )
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(filterLabels) { idx, label ->
                    ObsidianFilterCapsule(
                        label = label,
                        selected = filterIdx == idx,
                        onClick = { filterIdx = idx }
                    )
                }
            }
        }

        when {
            query.isBlank() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(ObsidianSpacing.md)
                ) {
                    ObsidianConsoleCard {
                        Text(text = strings.tr("search_empty_prompt"), style = ObsidianTypography.bodyMd(palette))
                    }
                }
            }
            isSearching -> {
                ObsidianLoadingPanel(skeletonCount = 4)
            }
            results.albums.isEmpty() && results.tracks.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(ObsidianSpacing.md)
                ) {
                    ObsidianConsoleCard {
                        Text(text = strings.tr("search_no_results"), style = ObsidianTypography.bodyMd(palette))
                    }
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = ObsidianSpacing.md,
                        end = ObsidianSpacing.md,
                        top = ObsidianSpacing.xs,
                        bottom = ObsidianSpacing.dockClearance
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (results.albums.isNotEmpty()) {
                        item {
                            Text(
                                text = strings.tr("search_matched_albums"),
                                style = ObsidianTypography.studioLabel(palette, palette.accentPrimary)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(results.albums, key = { it.id }) { album ->
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
                    }

                    if (results.tracks.isNotEmpty()) {
                        item {
                            Text(
                                text = strings.tr("search_matched_tracks"),
                                style = ObsidianTypography.studioLabel(palette, palette.accentPrimary)
                            )
                        }
                        itemsIndexed(results.tracks, key = { _, t -> t.id }) { idx, track ->
                            ObsidianTrackRow(
                                track = track,
                                index = idx,
                                repository = container.repository,
                                isActive = playerState.currentTrack?.id == track.id,
                                isPlaying = playerState.isPlaying,
                                isOfflineReady = downloadState.isTrackOffline(track.id),
                                downloadJob = downloadState.jobsByTrackId[track.id],
                                onTap = { container.playerViewModel.loadQueue(results.tracks, initialIndex = idx) },
                                onDownloadTap = { container.downloadViewModel.enqueueDownloads(listOf(track), track.album) }
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
}
