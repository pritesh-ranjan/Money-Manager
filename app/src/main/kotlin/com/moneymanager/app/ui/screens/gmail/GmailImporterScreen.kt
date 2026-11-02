package com.moneymanager.app.ui.screens.gmail

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.moneymanager.app.data.local.entity.GmailImportHistoryEntity
import com.moneymanager.app.data.local.entity.GmailImportStatus
import com.moneymanager.app.data.local.entity.GmailSyncStatus
import com.moneymanager.app.domain.gmail.auth.GmailAuthConfig
import com.moneymanager.app.domain.gmail.auth.GmailTokenStorage
import com.moneymanager.app.ui.components.*
import com.moneymanager.app.ui.main.MainViewModel
import com.moneymanager.app.ui.theme.OneUiTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun GmailImporterScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val syncState by viewModel.gmailSyncState.collectAsState()
    val importHistory by viewModel.gmailImportHistory.collectAsState()
    val isSyncing by viewModel.isGmailSyncing.collectAsState()

    val isConnected = !syncState?.accountEmail.isNullOrBlank() || GmailTokenStorage.isConnected(context)
    val connectedEmail = syncState?.accountEmail ?: GmailTokenStorage.getConnectedEmail(context)

    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showCredentialsDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OneUiTheme.colors.background)
            .statusBarsPadding()
    ) {
        OneUiSubScreenTopBar(
            title = "Gmail Statement Importer",
            onBack = onBack,
            actions = {
                if (importHistory.isNotEmpty()) {
                    IconButton(onClick = { viewModel.clearGmailImportHistory() }) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear History",
                            tint = OneUiTheme.colors.textMuted
                        )
                    }
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Privacy Banner Card
            item {
                OneUiBannerCard(
                    title = "Automated On-Device Importer",
                    subtitle = "Securely imports PDF bank statements from ICICI, Axis, HDFC, Bank of Baroda & Federal Bank directly on-device. No cloud intermediary."
                )
            }

            // 2. Connection Status Card
            item {
                OneUiCard(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isSyncing -> OneUiTheme.colors.warning
                                                isConnected -> OneUiTheme.colors.income
                                                else -> OneUiTheme.colors.textMuted
                                            }
                                        )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when {
                                        isSyncing -> "Syncing with Gmail..."
                                        isConnected -> "Connected & Active"
                                        else -> "Not Connected"
                                    },
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = when {
                                            isSyncing -> OneUiTheme.colors.warning
                                            isConnected -> OneUiTheme.colors.income
                                            else -> OneUiTheme.colors.textMuted
                                        },
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isConnected) connectedEmail ?: "Google Account" else "Connect your Gmail account",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = OneUiTheme.colors.textPrimary,
                                    fontSize = 17.sp
                                )
                            )

                            if (isConnected && syncState?.lastSyncTimestamp != null && syncState!!.lastSyncTimestamp > 0) {
                                val lastSyncStr = formatTimestamp(syncState!!.lastSyncTimestamp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Last synced: $lastSyncStr",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = OneUiTheme.colors.textSecondary,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }

                        // Right Action Button
                        if (!isConnected) {
                            OneUiPillButton(
                                text = "Connect",
                                onClick = {
                                    if (activity != null) {
                                        viewModel.gmailOAuthManager.startOAuthFlow(activity)
                                    }
                                },
                                containerColor = OneUiTheme.colors.primaryAccent,
                                contentColor = Color.White
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = OneUiTheme.colors.primaryAccent,
                                        strokeWidth = 2.5.dp
                                    )
                                } else {
                                    IconButton(onClick = { viewModel.triggerManualGmailSync() }) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Sync Now",
                                            tint = OneUiTheme.colors.primaryAccent
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                IconButton(onClick = { showDisconnectDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Default.LinkOff,
                                        contentDescription = "Disconnect",
                                        tint = OneUiTheme.colors.expense
                                    )
                                }
                            }
                        }
                    }

                    if (syncState?.lastSyncError != null && syncState!!.lastSyncStatus == GmailSyncStatus.ERROR.name) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OneUiInsetDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Sync Notice: ${syncState?.lastSyncError}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = OneUiTheme.colors.expense,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }

            // 3. Automation & Credentials Settings Card
            item {
                OneUiCard(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    OneUiListRow(
                        title = "Daily Auto-Sync",
                        subtitle = "Runs background sync once every 24 hours with WorkManager",
                        subtitleColor = OneUiTheme.colors.textSecondary,
                        trailingContent = {
                            OneUiSwitch(
                                checked = syncState?.isAutoSyncEnabled ?: true,
                                onCheckedChange = { viewModel.updateGmailAutoSyncEnabled(it) },
                                showVerticalDivider = true
                            )
                        }
                    )

                    OneUiInsetDivider(startIndent = 52.dp)

                    OneUiListRow(
                        title = "OAuth Credentials",
                        subtitle = if (GmailAuthConfig.isConfigured(context)) "Client ID configured" else "Default placeholders • Tap to customize",
                        subtitleColor = if (GmailAuthConfig.isConfigured(context)) OneUiTheme.colors.income else OneUiTheme.colors.warning,
                        leadingContent = {
                            OneUiIconBadge(
                                icon = Icons.Default.Key,
                                backgroundColor = OneUiTheme.colors.badgeBlue
                            )
                        },
                        onClick = { showCredentialsDialog = true }
                    )

                    OneUiInsetDivider(startIndent = 52.dp)

                    OneUiListRow(
                        title = "Statement Password",
                        subtitle = if (!GmailTokenStorage.getDefaultPdfPassword(context).isNullOrBlank()) "Password configured" else "Optional default password for encrypted PDFs",
                        subtitleColor = OneUiTheme.colors.textSecondary,
                        leadingContent = {
                            OneUiIconBadge(
                                icon = Icons.Default.Lock,
                                backgroundColor = OneUiTheme.colors.badgePurple
                            )
                        },
                        onClick = { showPasswordDialog = true }
                    )
                }
            }

            // 4. Import History Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Import History (${importHistory.size})",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = OneUiTheme.colors.textPrimary
                        )
                    )

                    if (isConnected) {
                        OneUiPillButton(
                            text = "Sync Now",
                            onClick = { viewModel.triggerManualGmailSync() },
                            containerColor = OneUiTheme.colors.surfaceElevated,
                            contentColor = OneUiTheme.colors.primaryAccent
                        )
                    }
                }
            }

            // 5. Import History Items
            if (importHistory.isEmpty()) {
                item {
                    OneUiCard(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.MarkEmailRead,
                                contentDescription = null,
                                tint = OneUiTheme.colors.textMuted,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No bank statements detected yet",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = OneUiTheme.colors.textSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "New monthly statement PDFs from ICICI, Axis, HDFC, BOB or Federal Bank will appear here automatically.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = OneUiTheme.colors.textMuted,
                                    fontSize = 12.sp
                                ),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(importHistory, key = { it.messageId }) { item ->
                    ImportHistoryRow(item)
                }
            }
        }
    }

    // Disconnect Confirmation Dialog
    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("Disconnect Gmail?") },
            text = { Text("This will revoke access on Google and stop background statement imports. Local statements already imported will remain safe.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDisconnectDialog = false
                        viewModel.disconnectGmail()
                    }
                ) {
                    Text("Disconnect", color = OneUiTheme.colors.expense)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = OneUiTheme.colors.surfaceElevated,
            shape = OneUiDialogShape
        )
    }

    // OAuth Credentials Dialog
    if (showCredentialsDialog) {
        var clientIdInput by remember { mutableStateOf(GmailTokenStorage.getClientId(context) ?: "") }
        var clientSecretInput by remember { mutableStateOf(GmailTokenStorage.getClientSecret(context) ?: "") }

        Dialog(onDismissRequest = { showCredentialsDialog = false }) {
            Surface(
                shape = OneUiDialogShape,
                color = OneUiTheme.colors.surfaceElevated,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Google OAuth Credentials",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = OneUiTheme.colors.textPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Enter your Google Cloud Console OAuth 2.0 Client ID and Secret (Web or Android application type with gmail.readonly scope).",
                        style = MaterialTheme.typography.bodySmall.copy(color = OneUiTheme.colors.textSecondary)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = clientIdInput,
                        onValueChange = { clientIdInput = it },
                        label = { Text("Client ID") },
                        placeholder = { Text("YOUR_CLIENT_ID.apps.googleusercontent.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = clientSecretInput,
                        onValueChange = { clientSecretInput = it },
                        label = { Text("Client Secret (optional for installed)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showCredentialsDialog = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OneUiPillButton(
                            text = "Save",
                            onClick = {
                                viewModel.saveCustomOAuthCredentials(
                                    clientId = clientIdInput.ifBlank { null },
                                    clientSecret = clientSecretInput.ifBlank { null }
                                )
                                showCredentialsDialog = false
                            }
                        )
                    }
                }
            }
        }
    }

    // Default Statement Password Dialog
    if (showPasswordDialog) {
        var passwordInput by remember { mutableStateOf(GmailTokenStorage.getDefaultPdfPassword(context) ?: "") }

        Dialog(onDismissRequest = { showPasswordDialog = false }) {
            Surface(
                shape = OneUiDialogShape,
                color = OneUiTheme.colors.surfaceElevated,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Statement PDF Password",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = OneUiTheme.colors.textPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Most Indian banks password-protect statements (e.g. first 4 letters of name + DOB DDMM, or PAN). Saved securely on-device.",
                        style = MaterialTheme.typography.bodySmall.copy(color = OneUiTheme.colors.textSecondary)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Default Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                viewModel.saveDefaultPdfPassword(null)
                                showPasswordDialog = false
                            }
                        ) {
                            Text("Clear")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = { showPasswordDialog = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OneUiPillButton(
                            text = "Save",
                            onClick = {
                                viewModel.saveDefaultPdfPassword(passwordInput.ifBlank { null })
                                showPasswordDialog = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportHistoryRow(item: GmailImportHistoryEntity) {
    OneUiCard(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val statusColor = when (item.importStatus) {
                GmailImportStatus.SUCCESS.name -> OneUiTheme.colors.income
                GmailImportStatus.PASSWORD_REQUIRED.name -> OneUiTheme.colors.warning
                GmailImportStatus.NO_PARSER_MATCH.name -> OneUiTheme.colors.textSecondary
                else -> OneUiTheme.colors.expense
            }

            val bankEmoji = when {
                item.bankName.contains("ICICI", ignoreCase = true) -> "🟠"
                item.bankName.contains("Axis", ignoreCase = true) -> "🔴"
                item.bankName.contains("HDFC", ignoreCase = true) -> "🔵"
                item.bankName.contains("Baroda", ignoreCase = true) -> "🟧"
                item.bankName.contains("Federal", ignoreCase = true) -> "🟡"
                else -> "🏦"
            }

            OneUiEmojiBadge(
                emoji = bankEmoji,
                backgroundColor = OneUiTheme.colors.surfaceElevated
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.subject.ifBlank { item.fileName },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = OneUiTheme.colors.textPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${item.bankName} • ${formatTimestamp(item.receivedEpochMillis)}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = OneUiTheme.colors.textSecondary,
                        fontSize = 12.sp
                    )
                )

                if (!item.statementId.isNullOrBlank()) {
                    Text(
                        text = "Statement: ${item.statementId}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = OneUiTheme.colors.primaryAccent,
                            fontSize = 11.sp
                        )
                    )
                } else if (!item.errorMessage.isNullOrBlank()) {
                    Text(
                        text = item.errorMessage,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = statusColor,
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Status Badge
            Surface(
                shape = CircleShape,
                color = statusColor.copy(alpha = 0.15f)
            ) {
                Text(
                    text = when (item.importStatus) {
                        GmailImportStatus.SUCCESS.name -> "Imported"
                        GmailImportStatus.PASSWORD_REQUIRED.name -> "Password"
                        GmailImportStatus.NO_PARSER_MATCH.name -> "Unsupported"
                        else -> "Failed"
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

private fun formatTimestamp(millis: Long): String {
    if (millis <= 0) return "Never"
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy • h:mm a", Locale.ENGLISH)
    return Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}
