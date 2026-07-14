package com.example.presentation.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import com.example.R
import com.example.data.security.BiometricAuthManager
import com.example.data.security.CryptoEngine
import com.example.presentation.VaultViewModel
import java.text.DateFormat
import java.util.Date
import javax.crypto.Cipher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: VaultViewModel) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scrollState = rememberScrollState()

    val currentTheme by viewModel.themeState.collectAsState()
    val currentLanguage by viewModel.languageState.collectAsState()
    val biometricEnabled by viewModel.biometricEnabledState.collectAsState()
    val driveSyncEnabled by viewModel.driveBackupEnabledState.collectAsState()
    val autolockTimeout by viewModel.autolockTimeoutState.collectAsState()
    val lastBackupTime by viewModel.lastBackupTimeState.collectAsState()
    val backupStatus by viewModel.backupStatusMessage.collectAsState()

    // Dialog control states
    var showPasswordChangeDialog by remember { mutableStateOf(false) }
    var showTimeoutMenu by remember { mutableStateOf(false) }

    // Display backup status toasts
    LaunchedEffect(backupStatus) {
        if (backupStatus != null) {
            Toast.makeText(context, backupStatus, Toast.LENGTH_LONG).show()
            viewModel.clearBackupMessage()
        }
    }

    // File Selector launcher for import
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.performLocalImport(uri) { err ->
                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- CATEGORY: SECURITY ---
        Text(
            text = stringResource(R.string.security_settings_category),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                // Change Master Password Row
                ListItem(
                    headlineContent = { Text(stringResource(R.string.change_master_password)) },
                    leadingContent = { Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { showPasswordChangeDialog = true }
                )

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                // Toggle Biometrics Row
                ListItem(
                    headlineContent = { Text(stringResource(R.string.enable_biometric)) },
                    trailingContent = {
                        Switch(
                            checked = biometricEnabled,
                            onCheckedChange = { checked ->
                                if (checked && activity != null) {
                                    // Authorize wrapping key via biometric prompt
                                    try {
                                        val cipher = CryptoEngine.getBiometricCipher(Cipher.ENCRYPT_MODE)
                                        BiometricAuthManager.showBiometricPrompt(
                                            activity = activity,
                                            cipher = cipher,
                                            title = activity.getString(R.string.biometric_prompt_title),
                                            subtitle = "Authorize biometric wrapping",
                                            negativeButtonText = activity.getString(R.string.biometric_prompt_negative),
                                            onSuccess = { authorizedCipher ->
                                                viewModel.setupBiometricKey(authorizedCipher)
                                                Toast.makeText(context, "Biometrics set up successfully", Toast.LENGTH_SHORT).show()
                                            },
                                            onError = { err ->
                                                Toast.makeText(context, "Setup failed: $err", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Biometrics setup error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    viewModel.disableBiometrics()
                                }
                            }
                        )
                    }
                )

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                // Configure Auto-lock timeout
                ListItem(
                    headlineContent = { Text(stringResource(R.string.autolock_timeout)) },
                    trailingContent = {
                        Box {
                            val timeoutLabel = when (autolockTimeout) {
                                30000L -> stringResource(R.string.autolock_30s)
                                60000L -> stringResource(R.string.autolock_1m)
                                300000L -> stringResource(R.string.autolock_5m)
                                900000L -> stringResource(R.string.autolock_15m)
                                else -> stringResource(R.string.autolock_1m)
                            }

                            TextButton(onClick = { showTimeoutMenu = true }) {
                                Text(timeoutLabel, fontWeight = FontWeight.Bold)
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                            }

                            DropdownMenu(
                                expanded = showTimeoutMenu,
                                onDismissRequest = { showTimeoutMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.autolock_30s)) },
                                    onClick = {
                                        viewModel.updateAutolockTimeout(30000L)
                                        showTimeoutMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.autolock_1m)) },
                                    onClick = {
                                        viewModel.updateAutolockTimeout(60000L)
                                        showTimeoutMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.autolock_5m)) },
                                    onClick = {
                                        viewModel.updateAutolockTimeout(300000L)
                                        showTimeoutMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.autolock_15m)) },
                                    onClick = {
                                        viewModel.updateAutolockTimeout(900000L)
                                        showTimeoutMenu = false
                                    }
                                )
                            }
                        }
                    }
                )
            }
        }

        // --- CATEGORY: BACKUP & SYNC ---
        Text(
            text = stringResource(R.string.backup_settings_category),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Local Export/Import section
                Text(
                    text = stringResource(R.string.local_export),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.local_export_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Export
                    Button(
                        onClick = {
                            viewModel.performLocalExport(
                                onSuccess = { file ->
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/octet-stream"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Save IronVault Backup"))
                                },
                                onFailure = { err ->
                                    Toast.makeText(context, "Export failed: $err", Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export")
                    }

                    // Import
                    OutlinedButton(
                        onClick = { importLauncher.launch("*/*") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Import")
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))

                // Google Drive Sync section
                Text(
                    text = stringResource(R.string.google_drive_sync),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.google_drive_sync_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Enable Cloud Sync",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = driveSyncEnabled,
                        onCheckedChange = { viewModel.setDriveBackupEnabled(it) }
                    )
                }

                AnimatedVisibility(visible = driveSyncEnabled) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val backupTimeStr = if (lastBackupTime > 0) {
                            stringResource(R.string.last_backup_time, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(lastBackupTime)))
                        } else {
                            stringResource(R.string.never_backed_up)
                        }

                        Text(
                            text = backupTimeStr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { viewModel.performGoogleDriveBackup("TEMP_USER_DRIVE_OAUTH_TOKEN") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Backup")
                            }

                            OutlinedButton(
                                onClick = { viewModel.performGoogleDriveRestore("TEMP_USER_DRIVE_OAUTH_TOKEN") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Restore")
                            }
                        }
                    }
                }
            }
        }

        // --- CATEGORY: APPEARANCE & LOCALIZATION ---
        Text(
            text = stringResource(R.string.appearance_settings_category),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Theme selection
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.theme_selection),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("LIGHT", "DARK", "SYSTEM").forEach { theme ->
                            val isSelected = currentTheme == theme
                            val label = when (theme) {
                                "LIGHT" -> stringResource(R.string.theme_light)
                                "DARK" -> stringResource(R.string.theme_dark)
                                "SYSTEM" -> stringResource(R.string.theme_system)
                                else -> theme
                            }

                            ElevatedFilterChip(
                                selected = isSelected,
                                onClick = { viewModel.updateTheme(theme) },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                // Language selection
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.language_selection),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("en", "fa").forEach { lang ->
                            val isSelected = currentLanguage == lang
                            val label = when (lang) {
                                "en" -> stringResource(R.string.language_en)
                                "fa" -> stringResource(R.string.language_fa)
                                else -> lang
                            }

                            ElevatedFilterChip(
                                selected = isSelected,
                                onClick = { viewModel.updateLanguage(lang) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        }

        // --- ABOUT / SECURITY INFO SECTION ---
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                contentColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.security_info_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = stringResource(R.string.security_info_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = TextAlign.Start,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.25
                )
            }
        }
    }

    // Change Master Password Dialog
    if (showPasswordChangeDialog) {
        var newPassword by remember { mutableStateOf("") }
        var confirmNewPassword by remember { mutableStateOf("") }
        var isSubmitting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showPasswordChangeDialog = false },
            title = { Text(stringResource(R.string.change_master_password), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Set a strong new Master Password. This will trigger a fully automated database re-encryption process.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = confirmNewPassword,
                        onValueChange = { confirmNewPassword = it },
                        label = { Text("Confirm New Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPassword == confirmNewPassword && newPassword.isNotEmpty()) {
                            isSubmitting = true
                            viewModel.changeMasterPassword(
                                newPassword = newPassword,
                                onSuccess = {
                                    isSubmitting = false
                                    showPasswordChangeDialog = false
                                    Toast.makeText(context, "Master Password changed successfully!", Toast.LENGTH_SHORT).show()
                                },
                                onFailure = { err ->
                                    isSubmitting = false
                                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                }
                            )
                        } else {
                            Toast.makeText(context, "Passwords must match and not be empty", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isSubmitting && newPassword.isNotEmpty() && confirmNewPassword.isNotEmpty()
                ) {
                    Text("Change")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordChangeDialog = false }) {
                    Text(stringResource(R.string.cancel_button))
                }
            }
        )
    }
}
