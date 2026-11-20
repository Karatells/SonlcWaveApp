package com.sonicwave.sonic_wave

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.sonicwave.sonic_wave.core.utils.ServerQrPayloadCodec
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.sonicwave.sonic_wave.core.l10n.AppLocalizations
import com.sonicwave.sonic_wave.core.l10n.LocalAppStrings
import com.sonicwave.sonic_wave.designsystem.components.ObsidianAmbientScaffold
import com.sonicwave.sonic_wave.designsystem.components.ObsidianFloatingDock
import com.sonicwave.sonic_wave.designsystem.components.ObsidianNavigationRail
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianPalette
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianSpacing
import com.sonicwave.sonic_wave.designsystem.tokens.SonicWaveTheme
import com.sonicwave.sonic_wave.domain.services.ObsidianThemeMode
import com.sonicwave.sonic_wave.presentation.audio.SonicMediaService
import com.sonicwave.sonic_wave.presentation.screens.FullPlayerScreen
import com.sonicwave.sonic_wave.presentation.screens.HomeScreen
import com.sonicwave.sonic_wave.presentation.screens.LibraryScreen
import com.sonicwave.sonic_wave.presentation.screens.MiniPlayerBar
import com.sonicwave.sonic_wave.presentation.screens.SearchScreen
import com.sonicwave.sonic_wave.presentation.screens.ServerProfilesScreen
import com.sonicwave.sonic_wave.presentation.screens.WelcomeOnboardingScreen

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val perms = buildList {
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        if (perms.isNotEmpty()) {
            permissionLauncher.launch(perms.toTypedArray())
        }

        // Connect MediaController to SonicMediaService so foreground media notification & Android Auto stay active
        val sessionToken = SessionToken(this, ComponentName(this, SonicMediaService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({}, MoreExecutors.directExecutor())

        val container = (application as SonicWaveApplication).container
        handleDeepLink(intent, container)

        setContent {
            SonicWaveRoot(container = container)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val container = (application as SonicWaveApplication).container
        handleDeepLink(intent, container)
    }

    private fun handleDeepLink(intent: Intent?, container: AppContainer) {
        val rawUri = intent?.data?.toString() ?: return
        if (!rawUri.startsWith("sonicwave://")) return
        val decoded = ServerQrPayloadCodec.tryDecodeProfile(rawUri) ?: return
        lifecycleScope.launch {
            container.database.upsertServerProfile(decoded)
            container.database.setActiveServerProfile(decoded.id)
            container.repository.updateServerProfile(decoded)
            container.syncWorker.startListening(decoded.id)
            container.hasConfiguredServer.value = true
            Toast.makeText(this@MainActivity, "Imported: ${decoded.name}", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun SonicWaveRoot(container: AppContainer) {
    val isInitialized by container.isInitialized.collectAsState()
    val hasConfiguredServer by container.hasConfiguredServer.collectAsState()
    val settings by container.settingsService.settings.collectAsState()
    val playerState by container.playerViewModel.state.collectAsState()
    val localeMode by AppLocalizations.localeModeFlow.collectAsState()

    val strings = remember(localeMode) { AppLocalizations.forLanguage(localeMode) }
    val defaultSeed = if (settings.themeMode == ObsidianThemeMode.WARM_ALABASTER) {
        ObsidianPalette.DefaultLightSeed
    } else {
        ObsidianPalette.DefaultDarkSeed
    }
    val seedColor = if (settings.dynamicColorsEnabled && playerState.queue.isNotEmpty()) {
        playerState.dynamicSeedColor
    } else {
        defaultSeed
    }

    CompositionLocalProvider(LocalAppStrings provides strings) {
        SonicWaveTheme(
            themeMode = settings.themeMode,
            seedColor = seedColor
        ) {
            if (!isInitialized) {
                ObsidianAmbientScaffold {}
            } else if (!hasConfiguredServer) {
                WelcomeOnboardingScreen(container = container)
            } else {
                MainShellScaffold(container = container)
            }
        }
    }
}

@Composable
private fun MainTabContent(
    selectedTab: Int,
    container: AppContainer,
    onNavigateToServers: () -> Unit
) {
    when (selectedTab) {
        0 -> HomeScreen(
            container = container,
            onNavigateToServers = onNavigateToServers
        )
        1 -> LibraryScreen(container = container)
        2 -> SearchScreen(container = container)
        3 -> ServerProfilesScreen(container = container)
    }
}

@Composable
private fun MainShellScaffold(container: AppContainer) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showFullPlayer by remember { mutableStateOf(false) }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    ObsidianAmbientScaffold {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isLandscape) {
                Row(modifier = Modifier.fillMaxSize()) {
                    ObsidianNavigationRail(
                        selectedIndex = selectedTab,
                        onDestinationSelected = { selectedTab = it }
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        MainTabContent(
                            selectedTab = selectedTab,
                            container = container,
                            onNavigateToServers = { selectedTab = 3 }
                        )
                        MiniPlayerBar(
                            container = container,
                            onExpand = { showFullPlayer = true },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(bottom = ObsidianSpacing.xs)
                        )
                    }
                }
            } else {
                MainTabContent(
                    selectedTab = selectedTab,
                    container = container,
                    onNavigateToServers = { selectedTab = 3 }
                )

                Column(
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    MiniPlayerBar(
                        container = container,
                        onExpand = { showFullPlayer = true }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    ObsidianFloatingDock(
                        selectedIndex = selectedTab,
                        onDestinationSelected = { selectedTab = it }
                    )
                }
            }

            AnimatedVisibility(
                visible = showFullPlayer,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                FullPlayerScreen(
                    container = container,
                    onDismiss = { showFullPlayer = false }
                )
            }
        }
    }
}
