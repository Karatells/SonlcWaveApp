package com.sonicwave.sonic_wave.presentation.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Wifi
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonicwave.sonic_wave.AppContainer
import com.sonicwave.sonic_wave.core.l10n.AppLocalizations
import com.sonicwave.sonic_wave.core.security.SecureCredentialsStore
import com.sonicwave.sonic_wave.core.utils.ServerQrPayloadCodec
import com.sonicwave.sonic_wave.data.remote.OpenSubsonicMediaServerRepository
import com.sonicwave.sonic_wave.designsystem.components.ObsidianConfirmDialog
import com.sonicwave.sonic_wave.designsystem.components.ObsidianConsoleCard
import com.sonicwave.sonic_wave.designsystem.components.ObsidianFilterCapsule
import com.sonicwave.sonic_wave.designsystem.components.ObsidianIconTrigger
import com.sonicwave.sonic_wave.designsystem.components.ObsidianModalSheet
import com.sonicwave.sonic_wave.designsystem.components.ObsidianPrecisionFader
import com.sonicwave.sonic_wave.designsystem.components.ObsidianSwitchRow
import com.sonicwave.sonic_wave.designsystem.components.ObsidianTactileButton
import com.sonicwave.sonic_wave.designsystem.components.ObsidianTextField
import com.sonicwave.sonic_wave.designsystem.components.ObsidianTopBar
import com.sonicwave.sonic_wave.designsystem.painters.StorageAllocationBar
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianRadius
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianSpacing
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianTheme
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianTypography
import com.sonicwave.sonic_wave.domain.models.AudioCodec
import com.sonicwave.sonic_wave.domain.models.ServerProfile
import com.sonicwave.sonic_wave.domain.models.ServerProtocol
import com.sonicwave.sonic_wave.domain.services.AppSettingsService
import com.sonicwave.sonic_wave.domain.services.ObsidianThemeMode
import com.sonicwave.sonic_wave.presentation.widgets.AboutAppSection
import com.sonicwave.sonic_wave.presentation.widgets.AddLocalFolderByPathSheet
import com.sonicwave.sonic_wave.presentation.widgets.ExportSettingsSheet
import com.sonicwave.sonic_wave.presentation.widgets.ImportSettingsSheet
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServerProfilesScreen(container: AppContainer) {
    val palette = ObsidianTheme.palette
    val strings = AppLocalizations.current()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val settings by container.settingsService.settings.collectAsState()
    val downloadState by container.downloadViewModel.state.collectAsState()
    val localFolders by container.localAudioService.folders.collectAsState()
    val credentialsStore = remember { SecureCredentialsStore.getInstance(context) }

    var selectedCat by remember { mutableIntStateOf(0) }
    var servers by remember { mutableStateOf<List<ServerProfile>>(emptyList()) }
    var storageFilter by remember { mutableIntStateOf(0) } // 0=All, 1=Pinned, 2=Auto

    var editingServer by remember { mutableStateOf<ServerProfile?>(null) }
    var showAddServerSheet by remember { mutableStateOf(false) }
    var qrShareServer by remember { mutableStateOf<ServerProfile?>(null) }
    var showQrImportSheet by remember { mutableStateOf(false) }
    var deleteConfirmServer by remember { mutableStateOf<ServerProfile?>(null) }
    var certChangedServer by remember { mutableStateOf<ServerProfile?>(null) }
    var showClearPinnedConfirm by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    var showImportSheet by remember { mutableStateOf(false) }
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

    fun reloadServers() {
        scope.launch {
            servers = container.database.getAllServerProfiles()
        }
    }

    LaunchedEffect(Unit) { reloadServers() }

    val categories = listOf(
        strings.tr("cat_all"),
        strings.tr("cat_sources"),
        strings.tr("cat_audio"),
        strings.tr("cat_downloads"),
        strings.tr("cat_network"),
        strings.tr("cat_interface"),
        strings.tr("cat_backup"),
        strings.tr("cat_about")
    )

    fun showCat(catIdx: Int) = selectedCat == 0 || selectedCat == catIdx

    Column(modifier = Modifier.fillMaxSize()) {
        ObsidianTopBar(
            title = strings.tr("settings_title"),
            subtitleLabel = strings.tr("settings_subtitle"),
            actions = {
                ObsidianIconTrigger(icon = Icons.Rounded.QrCode2, onClick = { showQrImportSheet = true })
                ObsidianIconTrigger(icon = Icons.Rounded.Add, isPrimaryFilled = true, onClick = {
                    editingServer = null
                    showAddServerSheet = true
                })
            },
            bottomBar = {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = ObsidianSpacing.md, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(categories) { idx, label ->
                        ObsidianFilterCapsule(
                            label = label,
                            selected = selectedCat == idx,
                            onClick = { selectedCat = idx }
                        )
                    }
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 01 // SOURCES
            if (showCat(1)) {
                item {
                    Text(text = strings.tr("srv_sec_sources"), style = ObsidianTypography.studioLabel(palette, palette.accentPrimary))
                }
                itemsIndexed(servers, key = { _, s -> s.id }) { _, srv ->
                    ObsidianConsoleCard(elevated = srv.isActive) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(if (srv.isActive) palette.statusOnline else palette.textMuted)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(text = srv.name, style = ObsidianTypography.titleSm(palette))
                                    if (srv.isActive) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(ObsidianRadius.xs))
                                                .background(palette.statusOnline.copy(alpha = 0.16f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = strings.tr("srv_active_badge"),
                                                style = ObsidianTypography.technicalMono(palette, 9.5f, palette.statusOnline)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "${srv.protocol.displayName} • ${srv.primaryUrl}",
                                    style = ObsidianTypography.technicalMono(palette, 11f, palette.textMuted),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!srv.isActive) {
                                ObsidianTactileButton(
                                    label = strings.tr("srv_activate"),
                                    icon = Icons.Rounded.Check,
                                    isPrimary = true,
                                    padding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                                    onClick = {
                                        scope.launch {
                                            container.database.setActiveServerProfile(srv.id)
                                            container.repository.updateServerProfile(srv.copy(isActive = true))
                                            container.syncWorker.updateActiveServer(srv.id)
                                            reloadServers()
                                        }
                                    }
                                )
                            }
                            ObsidianTactileButton(
                                label = strings.tr("srv_ping"),
                                padding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                                onClick = {
                                    scope.launch {
                                        val probe = OpenSubsonicMediaServerRepository(context, srv)
                                        val ok = probe.pingServer()
                                        if (!ok && credentialsStore.getLastRejectedCertFingerprint(srv.id) != null) {
                                            certChangedServer = srv
                                        } else {
                                            Toast.makeText(
                                                context,
                                                if (ok) strings.tr("welcome_ping_ok") else strings.tr("welcome_ping_fail"),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            )
                            ObsidianIconTrigger(icon = Icons.Rounded.QrCode2, size = 34.dp, onClick = { qrShareServer = srv })
                            ObsidianIconTrigger(icon = Icons.Rounded.Edit, size = 34.dp, onClick = {
                                editingServer = srv
                                showAddServerSheet = true
                            })
                            ObsidianIconTrigger(
                                icon = Icons.Rounded.Delete,
                                size = 34.dp,
                                activeColor = palette.statusDanger,
                                onClick = { deleteConfirmServer = srv }
                            )
                        }
                    }
                }

                // Local Folders Card
                item {
                    ObsidianConsoleCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ObsidianTactileButton(
                                label = strings.tr("lib_add_folder"),
                                icon = Icons.Rounded.FolderOpen,
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
                        if (localFolders.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
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
            }

            // 02 // AUDIO & DSP
            if (showCat(2)) {
                item {
                    Text(text = strings.tr("srv_sec_audio"), style = ObsidianTypography.studioLabel(palette, palette.accentPrimary))
                }
                item {
                    ObsidianConsoleCard {
                        ObsidianSwitchRow(
                            title = strings.tr("player_eq_enable"),
                            value = settings.equalizerEnabled,
                            onChanged = { en -> scope.launch { container.settingsService.updateSettings { it.copy(equalizerEnabled = en) } } }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AppSettingsService.EQ_PRESETS.forEach { (presetKey, bands) ->
                                ObsidianFilterCapsule(
                                    label = presetKey.uppercase(),
                                    selected = settings.equalizerPreset == presetKey,
                                    onClick = {
                                        scope.launch {
                                            container.settingsService.updateSettings {
                                                it.copy(equalizerEnabled = true, equalizerPreset = presetKey, equalizerBandsDb = bands)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        ObsidianSwitchRow(
                            title = strings.tr("player_bass_boost"),
                            value = settings.bassBoostEnabled,
                            onChanged = { en -> scope.launch { container.settingsService.updateSettings { it.copy(bassBoostEnabled = en) } } }
                        )
                        Text(
                            text = "${settings.bassBoostDb.toInt()} dB",
                            style = ObsidianTypography.technicalMono(palette, 11f, palette.accentPrimary)
                        )
                        ObsidianPrecisionFader(
                            value = settings.bassBoostDb.toFloat(),
                            min = 0f,
                            max = 12f,
                            divisions = 12,
                            onValueChange = { v -> scope.launch { container.settingsService.updateSettings { it.copy(bassBoostDb = v.toDouble()) } } }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ObsidianSwitchRow(
                            title = strings.tr("srv_replaygain"),
                            value = settings.replayGainEnabled,
                            onChanged = { en -> scope.launch { container.settingsService.updateSettings { it.copy(replayGainEnabled = en) } } }
                        )
                        ObsidianSwitchRow(
                            title = strings.tr("srv_album_gain"),
                            value = settings.preferAlbumGain,
                            onChanged = { en -> scope.launch { container.settingsService.updateSettings { it.copy(preferAlbumGain = en) } } }
                        )
                        Text(
                            text = "${strings.tr("srv_preamp")}: ${"%.1f".format(settings.replayGainPreampDb)} dB",
                            style = ObsidianTypography.technicalMono(palette, 11f, palette.textSecondary)
                        )
                        ObsidianPrecisionFader(
                            value = settings.replayGainPreampDb.toFloat(),
                            min = -12f,
                            max = 12f,
                            divisions = 24,
                            onValueChange = { v -> scope.launch { container.settingsService.updateSettings { it.copy(replayGainPreampDb = v.toDouble()) } } }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${strings.tr("srv_crossfade")}: ${settings.crossfadeSeconds.toInt()}s",
                            style = ObsidianTypography.technicalMono(palette, 11f, palette.textSecondary)
                        )
                        ObsidianPrecisionFader(
                            value = settings.crossfadeSeconds.toFloat(),
                            min = 0f,
                            max = 12f,
                            divisions = 12,
                            onValueChange = { v -> scope.launch { container.settingsService.updateSettings { it.copy(crossfadeSeconds = v.toDouble()) } } }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = strings.tr("srv_playback_speed"), style = ObsidianTypography.studioLabel(palette))
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0.75, 1.0, 1.25, 1.5, 2.0).forEach { spd ->
                                ObsidianFilterCapsule(
                                    label = "${spd}x",
                                    selected = kotlin.math.abs(settings.playbackSpeed - spd) < 0.05,
                                    onClick = { scope.launch { container.settingsService.updateSettings { it.copy(playbackSpeed = spd) } } }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Pitch: ${"%.2f".format(settings.playbackPitch)}x",
                            style = ObsidianTypography.technicalMono(palette, 11f, palette.textSecondary)
                        )
                        ObsidianPrecisionFader(
                            value = settings.playbackPitch.toFloat(),
                            min = 0.5f,
                            max = 1.5f,
                            divisions = 20,
                            onValueChange = { v -> scope.launch { container.settingsService.updateSettings { it.copy(playbackPitch = v.toDouble()) } } }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ObsidianSwitchRow(
                            title = "Virtualizer (Stereo Width)",
                            value = settings.virtualizerEnabled,
                            onChanged = { en -> scope.launch { container.settingsService.updateSettings { it.copy(virtualizerEnabled = en) } } }
                        )
                        ObsidianPrecisionFader(
                            value = settings.virtualizerStrength.toFloat(),
                            min = 0f,
                            max = 1000f,
                            divisions = 20,
                            onValueChange = { v -> scope.launch { container.settingsService.updateSettings { it.copy(virtualizerStrength = v.toInt()) } } }
                        )
                        ObsidianSwitchRow(
                            title = "Skip Silence (Podcasts & Audiobooks)",
                            value = settings.skipSilenceForSpoken,
                            onChanged = { en -> scope.launch { container.settingsService.updateSettings { it.copy(skipSilenceForSpoken = en) } } }
                        )
                    }
                }
            }

            // 03 // DOWNLOADS & CACHE
            if (showCat(3)) {
                item {
                    Text(text = strings.tr("srv_sec_downloads"), style = ObsidianTypography.studioLabel(palette, palette.accentPrimary))
                }
                item {
                    val maxBytes = (settings.maxCacheGb * 1024 * 1024 * 1024).toFloat().coerceAtLeast(1f)
                    val totalCap = (maxBytes + downloadState.pinnedCacheSizeBytes).coerceAtLeast(1f)
                    val pinnedRatio = (downloadState.pinnedCacheSizeBytes / totalCap).coerceIn(0f, 1f)
                    val autoRatio = (downloadState.autoCacheSizeBytes / totalCap).coerceIn(0f, 1f - pinnedRatio)

                    ObsidianConsoleCard {
                        StorageAllocationBar(usedRatio = autoRatio, pinnedRatio = pinnedRatio)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = strings.tr("srv_storage_pinned", mapOf("size" to formatMb(downloadState.pinnedCacheSizeBytes))),
                                style = ObsidianTypography.technicalMono(palette, 11f, palette.accentPrimary)
                            )
                            Text(
                                text = strings.tr("srv_storage_auto", mapOf("size" to formatMb(downloadState.autoCacheSizeBytes))),
                                style = ObsidianTypography.technicalMono(palette, 11f, palette.accentSecondary)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        ObsidianSwitchRow(
                            title = strings.tr("srv_auto_cache"),
                            value = settings.autoCacheEnabled,
                            onChanged = { en -> scope.launch { container.settingsService.updateSettings { it.copy(autoCacheEnabled = en) } } }
                        )
                        ObsidianSwitchRow(
                            title = strings.tr("srv_wifi_only"),
                            value = settings.wifiOnlyDownloads,
                            onChanged = { en -> scope.launch { container.settingsService.updateSettings { it.copy(wifiOnlyDownloads = en) } } }
                        )
                        Text(
                            text = "${strings.tr("srv_cache_limit")}: ${"%.1f".format(settings.maxCacheGb)} GB",
                            style = ObsidianTypography.technicalMono(palette, 11f, palette.textSecondary)
                        )
                        ObsidianPrecisionFader(
                            value = settings.maxCacheGb.toFloat(),
                            min = 0.5f,
                            max = 16f,
                            divisions = 31,
                            onValueChange = { v -> scope.launch { container.settingsService.updateSettings { it.copy(maxCacheGb = v.toDouble()) } } }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ObsidianTactileButton(
                                label = strings.tr("srv_clear_auto"),
                                modifier = Modifier.weight(1f),
                                onClick = { container.downloadViewModel.clearAutoCache() }
                            )
                            ObsidianTactileButton(
                                label = strings.tr("srv_clear_pinned"),
                                isDanger = true,
                                modifier = Modifier.weight(1f),
                                onClick = { showClearPinnedConfirm = true }
                            )
                        }
                        if (downloadState.failedJobsCount > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            ObsidianTactileButton(
                                label = "${strings.tr("srv_retry_failed")} (${downloadState.failedJobsCount})",
                                isPrimary = true,
                                fullWidth = true,
                                onClick = { container.downloadViewModel.retryFailed() }
                            )
                        }

                        // Cached tracks list
                        val allCached = when (storageFilter) {
                            1 -> downloadState.pinnedTracks
                            2 -> downloadState.autoCachedTracks
                            else -> downloadState.pinnedTracks + downloadState.autoCachedTracks
                        }
                        if (downloadState.pinnedTracks.isNotEmpty() || downloadState.autoCachedTracks.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    strings.tr("srv_filter_all"),
                                    strings.tr("srv_filter_pinned"),
                                    strings.tr("srv_filter_auto")
                                ).forEachIndexed { idx, lbl ->
                                    ObsidianFilterCapsule(
                                        label = lbl,
                                        selected = storageFilter == idx,
                                        onClick = { storageFilter = idx }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ObsidianTactileButton(
                                    label = strings.tr("lib_play_all"),
                                    icon = Icons.Rounded.PlayArrow,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        val entities = allCached.map { container.database.mapCachedRowToEntity(it) }
                                        if (entities.isNotEmpty()) container.playerViewModel.loadQueue(entities)
                                    }
                                )
                                ObsidianTactileButton(
                                    label = strings.tr("lib_shuffle_all"),
                                    icon = Icons.Rounded.Shuffle,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        val entities = allCached.map { container.database.mapCachedRowToEntity(it) }
                                        if (entities.isNotEmpty()) container.playerViewModel.loadQueue(entities.shuffled())
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            allCached.take(20).forEach { row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = row.title, style = ObsidianTypography.titleSm(palette), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            text = "${row.artist} • ${formatMb(row.fileSizeBytes)}",
                                            style = ObsidianTypography.technicalMono(palette, 10.5f, palette.textMuted),
                                            maxLines = 1
                                        )
                                    }
                                    ObsidianIconTrigger(
                                        icon = Icons.Rounded.PushPin,
                                        size = 32.dp,
                                        isActive = row.isPinnedDownload,
                                        onClick = {
                                            container.downloadViewModel.togglePin(row.trackId, row.serverId, !row.isPinnedDownload)
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    ObsidianIconTrigger(
                                        icon = Icons.Rounded.Delete,
                                        size = 32.dp,
                                        activeColor = palette.statusDanger,
                                        onClick = {
                                            container.downloadViewModel.deleteTrack(row.trackId, row.serverId)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 04 // NETWORK & CODECS
            if (showCat(4)) {
                item {
                    Text(text = strings.tr("srv_sec_network"), style = ObsidianTypography.studioLabel(palette, palette.accentPrimary))
                }
                item {
                    ObsidianConsoleCard {
                        Text(text = strings.tr("srv_wifi_codec"), style = ObsidianTypography.studioLabel(palette))
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AudioCodec.entries.forEach { codec ->
                                ObsidianFilterCapsule(
                                    label = codec.wireName.uppercase(),
                                    selected = settings.wifiCodec == codec,
                                    onClick = { scope.launch { container.settingsService.updateSettings { it.copy(wifiCodec = codec) } } }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = strings.tr("srv_mobile_codec"), style = ObsidianTypography.studioLabel(palette))
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AudioCodec.entries.forEach { codec ->
                                ObsidianFilterCapsule(
                                    label = codec.wireName.uppercase(),
                                    selected = settings.mobileCodec == codec,
                                    onClick = { scope.launch { container.settingsService.updateSettings { it.copy(mobileCodec = codec) } } }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = strings.tr("srv_mobile_bitrate"), style = ObsidianTypography.studioLabel(palette))
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(96, 128, 160, 256, 320).forEach { br ->
                                ObsidianFilterCapsule(
                                    label = "$br KBPS",
                                    selected = settings.mobileMaxBitrateKbps == br,
                                    onClick = { scope.launch { container.settingsService.updateSettings { it.copy(mobileMaxBitrateKbps = br) } } }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        ObsidianSwitchRow(
                            title = strings.tr("srv_scrobble"),
                            value = settings.scrobbleEnabled,
                            onChanged = { en -> scope.launch { container.settingsService.updateSettings { it.copy(scrobbleEnabled = en) } } }
                        )
                    }
                }
            }

            // 05 // INTERFACE & PLAYBACK
            if (showCat(5)) {
                item {
                    Text(text = strings.tr("srv_sec_interface"), style = ObsidianTypography.studioLabel(palette, palette.accentPrimary))
                }
                item {
                    ObsidianConsoleCard {
                        Text(text = strings.tr("srv_light_theme"), style = ObsidianTypography.studioLabel(palette))
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                ObsidianThemeMode.OBSIDIAN_DARK to "Obsidian Dark",
                                ObsidianThemeMode.PURE_OLED to "Pure OLED",
                                ObsidianThemeMode.WARM_ALABASTER to "Warm Alabaster"
                            ).forEach { (mode, label) ->
                                ObsidianFilterCapsule(
                                    label = label,
                                    selected = settings.themeMode == mode,
                                    onClick = {
                                        scope.launch {
                                            container.settingsService.updateSettings { it.copy(themeMode = mode) }
                                        }
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        ObsidianSwitchRow(
                            title = strings.tr("srv_dynamic_colors"),
                            value = settings.dynamicColorsEnabled,
                            onChanged = { en -> scope.launch { container.settingsService.updateSettings { it.copy(dynamicColorsEnabled = en) } } }
                        )
                        ObsidianSwitchRow(
                            title = strings.tr("srv_vinyl_waves"),
                            subtitle = strings.tr("srv_vinyl_waves_sub"),
                            value = settings.vinylPulseWavesEnabled,
                            onChanged = { en -> scope.launch { container.settingsService.updateSettings { it.copy(vinylPulseWavesEnabled = en) } } }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = strings.tr("srv_default_repeat"), style = ObsidianTypography.studioLabel(palette))
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("off", "all", "one").forEach { mode ->
                                ObsidianFilterCapsule(
                                    label = mode.uppercase(),
                                    selected = settings.defaultRepeatMode == mode,
                                    onClick = { scope.launch { container.settingsService.updateSettings { it.copy(defaultRepeatMode = mode) } } }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = strings.tr("srv_language"), style = ObsidianTypography.studioLabel(palette))
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppLocalizations.languageDisplayNames.forEach { (code, displayName) ->
                                ObsidianFilterCapsule(
                                    label = displayName,
                                    selected = settings.languageCode == code,
                                    onClick = { scope.launch { container.settingsService.updateSettings { it.copy(languageCode = code) } } }
                                )
                            }
                        }
                    }
                }
            }

            // 06 // BACKUP
            if (showCat(6)) {
                item {
                    Text(text = strings.tr("srv_sec_backup"), style = ObsidianTypography.studioLabel(palette, palette.accentPrimary))
                }
                item {
                    ObsidianConsoleCard {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ObsidianTactileButton(
                                label = strings.tr("srv_export_btn"),
                                icon = Icons.Rounded.FileDownload,
                                modifier = Modifier.weight(1f),
                                onClick = { showExportSheet = true }
                            )
                            ObsidianTactileButton(
                                label = strings.tr("srv_import_btn"),
                                icon = Icons.Rounded.FileUpload,
                                isPrimary = true,
                                modifier = Modifier.weight(1f),
                                onClick = { showImportSheet = true }
                            )
                        }
                    }
                }
            }

            // 07 // ABOUT & LICENSES
            if (showCat(7)) {
                item {
                    Text(text = strings.tr("srv_sec_about"), style = ObsidianTypography.studioLabel(palette, palette.accentPrimary))
                }
                item {
                    AboutAppSection()
                }
            }
        }
    }

    // Dialogs & Sheets
    deleteConfirmServer?.let { srv ->
        ObsidianConfirmDialog(
            title = strings.tr("srv_delete_title"),
            message = strings.tr("srv_delete_msg"),
            isDanger = true,
            onConfirm = {
                scope.launch {
                    container.database.deleteServerProfile(srv.id)
                    reloadServers()
                }
            },
            onDismiss = { deleteConfirmServer = null }
        )
    }

    certChangedServer?.let { srv ->
        ObsidianConfirmDialog(
            title = strings.tr("srv_cert_changed_title"),
            message = strings.tr("srv_cert_changed_msg"),
            confirmLabel = strings.tr("srv_trust_cert"),
            onConfirm = {
                scope.launch {
                    credentialsStore.trustRejectedCertFingerprint(srv.id)
                    Toast.makeText(context, strings.tr("common_saved"), Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { certChangedServer = null }
        )
    }

    if (showClearPinnedConfirm) {
        ObsidianConfirmDialog(
            title = strings.tr("srv_clear_pinned"),
            message = strings.tr("srv_clear_pinned_confirm"),
            isDanger = true,
            onConfirm = { container.downloadViewModel.clearAllPinned() },
            onDismiss = { showClearPinnedConfirm = false }
        )
    }

    qrShareServer?.let { srv ->
        QuickQrShareSheet(container = container, profileOverride = srv, onDismiss = { qrShareServer = null })
    }

    if (showAddServerSheet) {
        AddOrEditServerSheet(
            initial = editingServer,
            container = container,
            onSaved = {
                showAddServerSheet = false
                reloadServers()
            },
            onDismiss = { showAddServerSheet = false }
        )
    }

    if (showQrImportSheet) {
        var qrInput by remember { mutableStateOf("") }
        ObsidianModalSheet(onDismiss = { showQrImportSheet = false }) {
            Text(text = strings.tr("welcome_qr_paste_title"), style = ObsidianTypography.headingMd(palette))
            Spacer(modifier = Modifier.height(10.dp))
            ObsidianTextField(
                value = qrInput,
                onValueChange = { qrInput = it },
                hint = strings.tr("welcome_qr_paste_hint"),
                maxLines = 4,
                isMonospace = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            ObsidianTactileButton(
                label = strings.tr("welcome_qr_apply"),
                isPrimary = true,
                fullWidth = true,
                onClick = {
                    val decoded = ServerQrPayloadCodec.tryDecodeProfile(qrInput)
                    if (decoded == null) {
                        Toast.makeText(context, strings.tr("welcome_qr_invalid"), Toast.LENGTH_SHORT).show()
                    } else {
                        scope.launch {
                            container.database.upsertServerProfile(decoded)
                            container.database.setActiveServerProfile(decoded.id)
                            container.repository.updateServerProfile(decoded)
                            container.syncWorker.updateActiveServer(decoded.id)
                            reloadServers()
                            showQrImportSheet = false
                        }
                    }
                }
            )
        }
    }

    if (showExportSheet) ExportSettingsSheet(container = container, onDismiss = { showExportSheet = false })
    if (showImportSheet) ImportSettingsSheet(container = container, onDismiss = { showImportSheet = false; reloadServers() })
    if (showAddPathSheet) AddLocalFolderByPathSheet(container = container, onDismiss = { showAddPathSheet = false })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddOrEditServerSheet(
    initial: ServerProfile?,
    container: AppContainer,
    onSaved: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = ObsidianTheme.palette
    val strings = AppLocalizations.current()
    val scope = rememberCoroutineScope()

    var protocol by remember { mutableStateOf(initial?.protocol ?: ServerProtocol.NAVIDROME) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var primaryUrl by remember { mutableStateOf(initial?.primaryUrl ?: "") }
    var lanUrl by remember { mutableStateOf(initial?.lanUrl ?: "") }
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var password by remember { mutableStateOf(initial?.secretOrPassword ?: "") }
    var allowSsl by remember { mutableStateOf(initial?.allowSelfSignedSsl ?: true) }

    ObsidianModalSheet(onDismiss = onDismiss) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(
                text = if (initial == null) strings.tr("srv_add_server") else strings.tr("srv_edit_server"),
                style = ObsidianTypography.headingMd(palette)
            )
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ServerProtocol.entries.forEach { p ->
                    ObsidianFilterCapsule(label = p.displayName, selected = protocol == p, onClick = { protocol = p })
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            ObsidianTextField(value = name, onValueChange = { name = it }, label = strings.tr("welcome_name_label"), prefixIcon = Icons.Rounded.Dns)
            Spacer(modifier = Modifier.height(10.dp))
            ObsidianTextField(value = primaryUrl, onValueChange = { primaryUrl = it }, label = strings.tr("welcome_url_label"), prefixIcon = Icons.Rounded.Link, keyboardType = KeyboardType.Uri, isMonospace = true)
            Spacer(modifier = Modifier.height(10.dp))
            ObsidianTextField(value = lanUrl, onValueChange = { lanUrl = it }, label = strings.tr("welcome_lan_label"), prefixIcon = Icons.Rounded.Wifi, keyboardType = KeyboardType.Uri, isMonospace = true)
            Spacer(modifier = Modifier.height(10.dp))
            ObsidianTextField(value = username, onValueChange = { username = it }, label = strings.tr("welcome_user_label"), prefixIcon = Icons.Rounded.Person)
            Spacer(modifier = Modifier.height(10.dp))
            ObsidianTextField(value = password, onValueChange = { password = it }, label = strings.tr("welcome_pass_label"), prefixIcon = Icons.Rounded.Lock, obscureText = true)
            Spacer(modifier = Modifier.height(8.dp))
            ObsidianSwitchRow(title = strings.tr("welcome_ssl_title"), value = allowSsl, onChanged = { allowSsl = it })
            Spacer(modifier = Modifier.height(14.dp))
            ObsidianTactileButton(
                label = strings.tr("common_save"),
                isPrimary = true,
                fullWidth = true,
                onClick = {
                    if (primaryUrl.isBlank()) return@ObsidianTactileButton
                    scope.launch {
                        val profile = ServerProfile(
                            id = initial?.id ?: "srv_${System.currentTimeMillis()}",
                            name = name.trim().ifEmpty { protocol.displayName },
                            protocol = protocol,
                            primaryUrl = primaryUrl.trim(),
                            lanUrl = lanUrl.trim().takeIf { it.isNotEmpty() },
                            username = username.trim(),
                            secretOrPassword = password,
                            allowSelfSignedSsl = allowSsl,
                            isActive = initial?.isActive ?: true
                        )
                        container.database.upsertServerProfile(profile)
                        if (profile.isActive) {
                            container.database.setActiveServerProfile(profile.id)
                            container.repository.updateServerProfile(profile)
                            container.syncWorker.updateActiveServer(profile.id)
                        }
                        onSaved()
                    }
                }
            )
        }
    }
}

private fun formatMb(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) "%.2f GB".format(mb / 1024.0) else "%.1f MB".format(mb)
}
