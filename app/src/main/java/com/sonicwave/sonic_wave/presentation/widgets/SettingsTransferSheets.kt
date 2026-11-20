package com.sonicwave.sonic_wave.presentation.widgets

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sonicwave.sonic_wave.AppContainer
import com.sonicwave.sonic_wave.core.l10n.AppLocalizations
import com.sonicwave.sonic_wave.designsystem.components.ObsidianModalSheet
import com.sonicwave.sonic_wave.designsystem.components.ObsidianSwitchRow
import com.sonicwave.sonic_wave.designsystem.components.ObsidianTactileButton
import com.sonicwave.sonic_wave.designsystem.components.ObsidianTextField
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianSpacing
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianTheme
import com.sonicwave.sonic_wave.designsystem.tokens.ObsidianTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ExportSettingsSheet(
    container: AppContainer,
    onDismiss: () -> Unit
) {
    val palette = ObsidianTheme.palette
    val strings = AppLocalizations.current()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var includePasswords by remember { mutableStateOf(true) }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val json = container.settingsService.exportToJsonString(includePasswords)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    }
                }
                Toast.makeText(context, strings.tr("common_saved"), Toast.LENGTH_SHORT).show()
                onDismiss()
            }
        }
    }

    ObsidianModalSheet(onDismiss = onDismiss) {
        Text(text = strings.tr("transfer_export_title"), style = ObsidianTypography.headingMd(palette))
        Spacer(modifier = Modifier.height(ObsidianSpacing.sm))

        ObsidianSwitchRow(
            title = strings.tr("transfer_include_passwords"),
            value = includePasswords,
            onChanged = { includePasswords = it }
        )

        Spacer(modifier = Modifier.height(ObsidianSpacing.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ObsidianTactileButton(
                label = strings.tr("transfer_save_file"),
                icon = Icons.Rounded.Save,
                isPrimary = true,
                modifier = Modifier.weight(1f),
                onClick = { saveLauncher.launch("sonicwave_backup.json") }
            )
            ObsidianTactileButton(
                label = strings.tr("transfer_copy_json"),
                icon = Icons.Rounded.ContentCopy,
                modifier = Modifier.weight(1f),
                onClick = {
                    scope.launch {
                        val json = container.settingsService.exportToJsonString(includePasswords)
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("SonicWave Settings", json))
                        Toast.makeText(context, strings.tr("common_copied"), Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                }
            )
        }
    }
}

@Composable
fun ImportSettingsSheet(
    container: AppContainer,
    onDismiss: () -> Unit
) {
    val palette = ObsidianTheme.palette
    val strings = AppLocalizations.current()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rawJson by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val openLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText().orEmpty()
                }
                if (text.isNotBlank()) rawJson = text
            }
        }
    }

    ObsidianModalSheet(onDismiss = onDismiss) {
        Text(text = strings.tr("transfer_import_title"), style = ObsidianTypography.headingMd(palette))
        Spacer(modifier = Modifier.height(ObsidianSpacing.sm))

        ObsidianTactileButton(
            label = strings.tr("transfer_pick_file"),
            icon = Icons.Rounded.FileOpen,
            fullWidth = true,
            onClick = { openLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
        )

        Spacer(modifier = Modifier.height(ObsidianSpacing.sm))
        ObsidianTextField(
            value = rawJson,
            onValueChange = { rawJson = it },
            hint = "{ \"version\": 1, ... }",
            maxLines = 6,
            isMonospace = true,
            modifier = Modifier.verticalScroll(rememberScrollState())
        )

        Spacer(modifier = Modifier.height(ObsidianSpacing.md))
        ObsidianTactileButton(
            label = strings.tr("transfer_apply_json"),
            isPrimary = true,
            isLoading = isLoading,
            fullWidth = true,
            onClick = {
                if (rawJson.isBlank()) return@ObsidianTactileButton
                scope.launch {
                    isLoading = true
                    val res = runCatching {
                        container.settingsService.importFromJsonString(rawJson, container.repository)
                    }
                    isLoading = false
                    res.onSuccess { (srvCount, folderCount) ->
                        if (srvCount > 0 || folderCount > 0) {
                            container.hasConfiguredServer.value = true
                        }
                        Toast.makeText(context, strings.tr("common_saved"), Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }.onFailure {
                        Toast.makeText(context, strings.tr("common_error"), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}

@Composable
fun AddLocalFolderByPathSheet(
    container: AppContainer,
    onDismiss: () -> Unit
) {
    val palette = ObsidianTheme.palette
    val strings = AppLocalizations.current()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pathText by remember { mutableStateOf("/storage/emulated/0/Music") }
    var isScanning by remember { mutableStateOf(false) }

    ObsidianModalSheet(onDismiss = onDismiss) {
        Text(text = strings.tr("transfer_path_title"), style = ObsidianTypography.headingMd(palette))
        Spacer(modifier = Modifier.height(ObsidianSpacing.sm))

        ObsidianTextField(
            value = pathText,
            onValueChange = { pathText = it },
            hint = strings.tr("transfer_path_hint"),
            prefixIcon = Icons.Rounded.Folder,
            isMonospace = true
        )

        Spacer(modifier = Modifier.height(ObsidianSpacing.md))
        ObsidianTactileButton(
            label = strings.tr("common_confirm"),
            isPrimary = true,
            isLoading = isScanning,
            fullWidth = true,
            onClick = {
                if (pathText.isBlank()) return@ObsidianTactileButton
                scope.launch {
                    isScanning = true
                    container.localAudioService.addFolderByPath(pathText)
                    container.hasConfiguredServer.value = true
                    isScanning = false
                    Toast.makeText(context, strings.tr("common_saved"), Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            }
        )
    }
}
