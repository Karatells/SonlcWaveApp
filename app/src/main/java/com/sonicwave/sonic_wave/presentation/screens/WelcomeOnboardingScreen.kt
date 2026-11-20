package com.sonicwave.sonic_wave.presentation.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sonicwave.sonic_wave.AppContainer
import com.sonicwave.sonic_wave.core.l10n.AppLocalizations
import com.sonicwave.sonic_wave.core.utils.ServerQrPayloadCodec
import com.sonicwave.sonic_wave.data.remote.OpenSubsonicMediaServerRepository
import com.sonicwave.sonic_wave.designsystem.components.ObsidianAmbientScaffold
import com.sonicwave.sonic_wave.designsystem.components.ObsidianConsoleCard
import com.sonicwave.sonic_wave.designsystem.components.ObsidianFilterCapsule
import com.sonicwave.sonic_wave.designsystem.components.ObsidianIconTrigger
import com.sonicwave.sonic_wave.designsystem.components.ObsidianModalSheet
import com.sonicwave.sonic_wave.designsystem.components.ObsidianSwitchRow
import com.sonicwave.sonic_wave.designsystem.components.ObsidianTactileButton
import com.sonicwave.sonic_wave.designsystem.components.ObsidianTextField
import com.sonicwave.sonic_wave.designsystem.painters.VinylGrooveDisc
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianRadius
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianSpacing
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianTheme
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianTypography
import com.sonicwave.sonic_wave.domain.models.ServerProfile
import com.sonicwave.sonic_wave.domain.models.ServerProtocol
import com.sonicwave.sonic_wave.presentation.widgets.ImportSettingsSheet
import kotlinx.coroutines.launch
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WelcomeOnboardingScreen(
    container: AppContainer
) {
    val palette = ObsidianTheme.palette
    val strings = AppLocalizations.current()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showServerForm by remember { mutableStateOf(false) }
    var showImportSheet by remember { mutableStateOf(false) }
    var showQrSheet by remember { mutableStateOf(false) }

    // Form state
    var protocol by remember { mutableStateOf(ServerProtocol.NAVIDROME) }
    var name by remember { mutableStateOf("Home Navidrome") }
    var primaryUrl by remember { mutableStateOf("") }
    var lanUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var obscurePassword by remember { mutableStateOf(true) }
    var allowSelfSigned by remember { mutableStateOf(true) }
    var isTesting by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Boolean?>(null) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            scope.launch {
                container.localAudioService.addFolderByUri(uri)
                container.hasConfiguredServer.value = true
            }
        }
    }

    fun buildCandidateProfile(): ServerProfile {
        return ServerProfile(
            id = "srv_${System.currentTimeMillis()}",
            name = name.trim().ifEmpty { protocol.displayName },
            protocol = protocol,
            primaryUrl = primaryUrl.trim(),
            lanUrl = lanUrl.trim().takeIf { it.isNotEmpty() },
            username = username.trim(),
            secretOrPassword = password,
            allowSelfSignedSsl = allowSelfSigned,
            isActive = true
        )
    }

    ObsidianAmbientScaffold(glowIntensity = 0.42f) {
        AnimatedContent(
            targetState = showServerForm,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            label = "welcomeSwitch"
        ) { isForm ->
            if (!isForm) {
                // Welcome Hero
                val transition = rememberInfiniteTransition(label = "heroSpin")
                val spin by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart),
                    label = "spin"
                )
                val eqPhase by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = (2f * Math.PI).toFloat(),
                    animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
                    label = "eq"
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = ObsidianSpacing.xl, vertical = ObsidianSpacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(contentAlignment = Alignment.Center) {
                        VinylGrooveDisc(
                            modifier = Modifier.size(168.dp),
                            accentColor = palette.accentPrimary,
                            isDark = palette.isDark,
                            rotationDegrees = spin
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 7-bar studio wave
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.height(26.dp)
                    ) {
                        for (i in 0 until 7) {
                            val h = 8f + 16f * ((sin(eqPhase + i * 0.85f) + 1f) / 2f)
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(h.dp)
                                    .clip(CircleShape)
                                    .background(palette.accentPrimary)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = strings.tr("welcome_studio_tag"),
                        style = ObsidianTypography.studioLabel(palette, palette.accentPrimary)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = strings.tr("welcome_title"),
                        style = ObsidianTypography.displayHero(palette),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = strings.tr("welcome_subtitle"),
                        style = ObsidianTypography.bodyMd(palette),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(18.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            strings.tr("welcome_badge_hires"),
                            strings.tr("welcome_badge_local"),
                            strings.tr("welcome_badge_dsp")
                        ).forEach { badge ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(ObsidianRadius.capsule))
                                    .background(palette.surfaceConsole)
                                    .border(1.dp, palette.hairlineBorder, RoundedCornerShape(ObsidianRadius.capsule))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = badge,
                                    style = ObsidianTypography.technicalMono(palette, 11f, palette.accentPrimary)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))
                    ObsidianTactileButton(
                        label = strings.tr("welcome_connect_server"),
                        icon = Icons.Rounded.Dns,
                        isPrimary = true,
                        fullWidth = true,
                        onClick = { showServerForm = true }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ObsidianTactileButton(
                        label = strings.tr("welcome_pick_local"),
                        icon = Icons.Rounded.FolderOpen,
                        fullWidth = true,
                        onClick = { folderPicker.launch(null) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ObsidianTactileButton(
                        label = strings.tr("welcome_import_settings"),
                        icon = Icons.Rounded.FileUpload,
                        fullWidth = true,
                        onClick = { showImportSheet = true }
                    )
                }
            } else {
                // Server Setup Form
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = ObsidianSpacing.md, vertical = ObsidianSpacing.md)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ObsidianIconTrigger(
                                icon = Icons.Rounded.ArrowBack,
                                onClick = { showServerForm = false }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = strings.tr("welcome_setup_title"),
                                style = ObsidianTypography.headingMd(palette)
                            )
                        }
                        ObsidianTactileButton(
                            label = "QR",
                            icon = Icons.Rounded.QrCode2,
                            padding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            onClick = { showQrSheet = true }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = strings.tr("welcome_protocol_label"),
                        style = ObsidianTypography.studioLabel(palette)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ServerProtocol.entries.forEach { p ->
                            ObsidianFilterCapsule(
                                label = p.displayName,
                                selected = protocol == p,
                                onClick = { protocol = p }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    ObsidianConsoleCard {
                        ObsidianTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = strings.tr("welcome_name_label"),
                            hint = strings.tr("welcome_name_hint"),
                            prefixIcon = Icons.Rounded.Dns
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        ObsidianTextField(
                            value = primaryUrl,
                            onValueChange = { primaryUrl = it },
                            label = strings.tr("welcome_url_label"),
                            hint = strings.tr("welcome_url_hint"),
                            prefixIcon = Icons.Rounded.Link,
                            keyboardType = KeyboardType.Uri,
                            isMonospace = true
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        ObsidianTextField(
                            value = lanUrl,
                            onValueChange = { lanUrl = it },
                            label = strings.tr("welcome_lan_label"),
                            hint = strings.tr("welcome_lan_hint"),
                            prefixIcon = Icons.Rounded.Wifi,
                            keyboardType = KeyboardType.Uri,
                            isMonospace = true
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        ObsidianTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = strings.tr("welcome_user_label"),
                            prefixIcon = Icons.Rounded.Person
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        ObsidianTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = strings.tr("welcome_pass_label"),
                            prefixIcon = Icons.Rounded.Lock,
                            obscureText = obscurePassword,
                            trailing = {
                                IconButton(
                                    onClick = { obscurePassword = !obscurePassword },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = if (obscurePassword) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                        contentDescription = null,
                                        tint = palette.textMuted
                                    )
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ObsidianSwitchRow(
                            title = strings.tr("welcome_ssl_title"),
                            subtitle = strings.tr("welcome_ssl_sub"),
                            value = allowSelfSigned,
                            onChanged = { allowSelfSigned = it }
                        )
                    }

                    if (testResult != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val ok = testResult == true
                        ObsidianConsoleCard(
                            borderColor = if (ok) palette.statusOnline else palette.statusDanger
                        ) {
                            Text(
                                text = if (ok) strings.tr("welcome_ping_ok") else strings.tr("welcome_ping_fail"),
                                style = ObsidianTypography.bodySm(palette).copy(
                                    color = if (ok) palette.statusOnline else palette.statusDanger
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ObsidianTactileButton(
                            label = strings.tr("welcome_test_btn"),
                            isLoading = isTesting,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (primaryUrl.isBlank()) return@ObsidianTactileButton
                                scope.launch {
                                    isTesting = true
                                    val probe = OpenSubsonicMediaServerRepository(context, buildCandidateProfile())
                                    testResult = probe.pingServer()
                                    isTesting = false
                                }
                            }
                        )
                        ObsidianTactileButton(
                            label = strings.tr("welcome_enter_btn"),
                            isPrimary = true,
                            isLoading = isSaving,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (primaryUrl.isBlank()) return@ObsidianTactileButton
                                scope.launch {
                                    isSaving = true
                                    val profile = buildCandidateProfile()
                                    container.database.upsertServerProfile(profile)
                                    container.database.setActiveServerProfile(profile.id)
                                    container.repository.updateServerProfile(profile)
                                    container.syncWorker.startListening(profile.id)
                                    isSaving = false
                                    container.hasConfiguredServer.value = true
                                }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    if (showImportSheet) {
        ImportSettingsSheet(container = container, onDismiss = { showImportSheet = false })
    }

    if (showQrSheet) {
        var qrInput by remember { mutableStateOf("") }
        ObsidianModalSheet(onDismiss = { showQrSheet = false }) {
            Text(text = strings.tr("welcome_qr_paste_title"), style = ObsidianTypography.headingMd(palette))
            Spacer(modifier = Modifier.height(10.dp))
            ObsidianTextField(
                value = qrInput,
                onValueChange = { qrInput = it },
                hint = strings.tr("welcome_qr_paste_hint"),
                maxLines = 4,
                isMonospace = true
            )
            Spacer(modifier = Modifier.height(14.dp))
            ObsidianTactileButton(
                label = strings.tr("welcome_qr_apply"),
                isPrimary = true,
                fullWidth = true,
                onClick = {
                    val decoded = ServerQrPayloadCodec.tryDecodeProfile(qrInput)
                    if (decoded == null) {
                        Toast.makeText(context, strings.tr("welcome_qr_invalid"), Toast.LENGTH_SHORT).show()
                    } else {
                        protocol = decoded.protocol
                        name = decoded.name
                        primaryUrl = decoded.primaryUrl
                        lanUrl = decoded.lanUrl.orEmpty()
                        username = decoded.username
                        password = decoded.secretOrPassword
                        allowSelfSigned = decoded.allowSelfSignedSsl
                        showQrSheet = false
                    }
                }
            )
        }
    }
}
