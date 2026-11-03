package com.moneymanager.app.ui.screens.gmail

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.moneymanager.app.data.local.entity.GmailImportHistoryEntity
import com.moneymanager.app.data.local.entity.GmailImportStatus
import com.moneymanager.app.data.local.entity.GmailSyncStatus
import com.moneymanager.app.domain.gmail.auth.GmailAuthConfig
import com.moneymanager.app.domain.gmail.auth.GmailTokenStorage
import com.moneymanager.app.domain.gmail.sync.PasswordUnlockOutcome
import com.moneymanager.app.ui.components.*
import com.moneymanager.app.ui.main.MainViewModel
import com.moneymanager.app.ui.screens.cards.CardMappingDialog
import com.moneymanager.app.ui.theme.OneUiTheme
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun GmailImporterScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateToPasswords: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = remember(context) {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is Activity) return@remember ctx
            ctx = ctx.baseContext
        }
        ctx as? Activity
    }

    val syncState by viewModel.gmailSyncState.collectAsState()
    val importHistory by viewModel.gmailImportHistory.collectAsState()
    val isSyncing by viewModel.isGmailSyncing.collectAsState()
    val hasCustomCredentials by viewModel.hasCustomOAuthCredentials.collectAsState()
    val syncSinceYearMonth by viewModel.syncSinceYearMonth.collectAsState()
    val syncRules by viewModel.gmailSyncRules.collectAsState()
    val statementSummaries by viewModel.statementSummaries.collectAsState()
    val allAccounts by viewModel.accounts.collectAsState()

    val isConnected = !syncState?.accountEmail.isNullOrBlank() || GmailTokenStorage.isConnected(context)
    val connectedEmail = syncState?.accountEmail ?: GmailTokenStorage.getConnectedEmail(context)

    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showCredentialsDialog by remember { mutableStateOf(false) }
    var showMonthYearDialog by remember { mutableStateOf(false) }
    var showSyncRulesDialog by remember { mutableStateOf(false) }
    var showCardMappingDialog by remember { mutableStateOf(false) }
    var itemToUnlock by remember { mutableStateOf<GmailImportHistoryEntity?>(null) }
    var itemForOptions by remember { mutableStateOf<GmailImportHistoryEntity?>(null) }

    val credentialsFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importOAuthCredentialsFromUri(uri)
            showCredentialsDialog = false
        }
    }

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
                                        viewModel.startGmailOAuth(activity)
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
                        title = "Sync Statements Since",
                        subtitle = "Import statements from ${syncSinceYearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US))} onwards",
                        subtitleColor = OneUiTheme.colors.primaryAccent,
                        leadingContent = {
                            OneUiIconBadge(
                                icon = Icons.Default.CalendarMonth,
                                backgroundColor = OneUiTheme.colors.badgeTeal
                            )
                        },
                        onClick = { showMonthYearDialog = true }
                    )

                    OneUiInsetDivider(startIndent = 52.dp)

                    OneUiListRow(
                        title = "Sync Rules & Filters",
                        subtitle = "Search criteria, supported banks & cards, ignored subjects",
                        subtitleColor = OneUiTheme.colors.textSecondary,
                        leadingContent = {
                            OneUiIconBadge(
                                icon = Icons.Default.FilterList,
                                backgroundColor = OneUiTheme.colors.badgeTeal
                            )
                        },
                        onClick = { showSyncRulesDialog = true }
                    )

                    OneUiInsetDivider(startIndent = 52.dp)

                    OneUiListRow(
                        title = "Card Account Mappings",
                        subtitle = "Map synced cards to your app credit cards",
                        subtitleColor = OneUiTheme.colors.primaryAccent,
                        leadingContent = {
                            OneUiIconBadge(
                                icon = Icons.Default.CreditCard,
                                backgroundColor = OneUiTheme.colors.badgeIndigo
                            )
                        },
                        onClick = { showCardMappingDialog = true }
                    )

                    OneUiInsetDivider(startIndent = 52.dp)

                    OneUiListRow(
                        title = "OAuth Credentials",
                        subtitle = if (hasCustomCredentials || GmailAuthConfig.isConfigured(context)) "Configured & encrypted on-device" else "Default placeholders • Tap to set up",
                        subtitleColor = if (hasCustomCredentials || GmailAuthConfig.isConfigured(context)) OneUiTheme.colors.income else OneUiTheme.colors.warning,
                        leadingContent = {
                            OneUiIconBadge(
                                icon = Icons.Default.Key,
                                backgroundColor = OneUiTheme.colors.badgeBlue
                            )
                        },
                        onClick = { showCredentialsDialog = true }
                    )

                    OneUiInsetDivider(startIndent = 52.dp)

                    val pdfPasswords by viewModel.pdfPasswords.collectAsState()
                    OneUiListRow(
                        title = "Statement Passwords",
                        subtitle = if (pdfPasswords.isNotEmpty()) "${pdfPasswords.size} passwords configured for auto-decrypt" else "Optional passwords for encrypted PDFs",
                        subtitleColor = if (pdfPasswords.isNotEmpty()) OneUiTheme.colors.income else OneUiTheme.colors.textSecondary,
                        leadingContent = {
                            OneUiIconBadge(
                                icon = Icons.Default.Lock,
                                backgroundColor = OneUiTheme.colors.badgePurple
                            )
                        },
                        onClick = {
                            onNavigateToPasswords?.invoke()
                        }
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
                    ImportHistoryRow(
                        item = item,
                        onUnlockClick = { itemToUnlock = it },
                        onOptionsClick = { itemForOptions = it }
                    )
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
        val isConfigured = hasCustomCredentials || GmailTokenStorage.hasCustomCredentials(context)
        var enterManually by remember { mutableStateOf(!isConfigured) }
        var clientIdInput by remember { mutableStateOf("") }
        var clientSecretInput by remember { mutableStateOf("") }
        var showSecretText by remember { mutableStateOf(false) }
        var showClearConfirm by remember { mutableStateOf(false) }

        if (showClearConfirm) {
            AlertDialog(
                onDismissRequest = { showClearConfirm = false },
                title = { Text("Remove Credentials?") },
                text = { Text("This will delete your custom Google Client ID and Secret from secure on-device storage.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.clearCustomOAuthCredentials()
                            showClearConfirm = false
                            showCredentialsDialog = false
                        }
                    ) {
                        Text("Remove", color = OneUiTheme.colors.expense)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirm = false }) {
                        Text("Cancel")
                    }
                },
                containerColor = OneUiTheme.colors.surfaceElevated,
                shape = OneUiDialogShape
            )
        }

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

                    if (isConfigured && !enterManually) {
                        // --- Configured State (Zero Exposure / Hidden Credentials) ---
                        Text(
                            text = "Your Google Cloud credentials are saved and encrypted with hardware-backed AES-256-GCM. For your security, raw credentials are never displayed on screen.",
                            style = MaterialTheme.typography.bodySmall.copy(color = OneUiTheme.colors.textSecondary)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = OneUiTheme.colors.income.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = OneUiTheme.colors.income,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Status: Encrypted & Active",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = OneUiTheme.colors.income,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Text(
                                        text = "Zero-exposure storage via AndroidKeyStore",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = OneUiTheme.colors.textSecondary,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Action 1: Import new JSON File
                        OneUiPillButton(
                            text = "Import New credentials.json",
                            onClick = {
                                credentialsFilePicker.launch(arrayOf("application/json", "text/*", "*/*"))
                            },
                            leadingIcon = Icons.Default.UploadFile,
                            containerColor = OneUiTheme.colors.primaryAccent,
                            contentColor = Color.White,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Action 2: Replace Manually
                        OneUiPillButton(
                            text = "Replace Manually",
                            onClick = {
                                clientIdInput = ""
                                clientSecretInput = ""
                                enterManually = true
                            },
                            leadingIcon = Icons.Default.Edit,
                            containerColor = OneUiTheme.colors.surfaceVariant,
                            contentColor = OneUiTheme.colors.textPrimary,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Action 3: Clear Credentials
                        OneUiPillButton(
                            text = "Clear Credentials",
                            onClick = { showClearConfirm = true },
                            leadingIcon = Icons.Default.Delete,
                            containerColor = OneUiTheme.colors.expense.copy(alpha = 0.12f),
                            contentColor = OneUiTheme.colors.expense,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showCredentialsDialog = false }) {
                                Text("Close")
                            }
                        }
                    } else {
                        // --- First Setup or Replace Manually (Zero Pre-filling) ---
                        Text(
                            text = "Import your Google Cloud credentials.json file or enter credentials manually. Saved securely in hardware storage.",
                            style = MaterialTheme.typography.bodySmall.copy(color = OneUiTheme.colors.textSecondary)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Quick File Picker Action Button
                        OneUiPillButton(
                            text = "Import credentials.json File",
                            onClick = {
                                credentialsFilePicker.launch(arrayOf("application/json", "text/*", "*/*"))
                            },
                            leadingIcon = Icons.Default.UploadFile,
                            containerColor = OneUiTheme.colors.primaryAccent,
                            contentColor = Color.White,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = OneUiTheme.colors.divider)
                            Text(
                                text = "  OR ENTER MANUALLY  ",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = OneUiTheme.colors.textMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f), color = OneUiTheme.colors.divider)
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = clientIdInput,
                            onValueChange = { clientIdInput = it },
                            label = { Text("Client ID") },
                            placeholder = { Text("YOUR_CLIENT_ID.apps.googleusercontent.com") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = clientSecretInput,
                            onValueChange = { clientSecretInput = it },
                            label = { Text("Client Secret (optional)") },
                            singleLine = true,
                            visualTransformation = if (showSecretText) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showSecretText = !showSecretText }) {
                                    Icon(
                                        imageVector = if (showSecretText) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (showSecretText) "Hide Secret" else "Show Secret",
                                        tint = OneUiTheme.colors.textMuted
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (isConfigured) {
                                TextButton(onClick = { enterManually = false }) {
                                    Text("Back")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
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
                                    clientIdInput = ""
                                    clientSecretInput = ""
                                    showCredentialsDialog = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showMonthYearDialog) {
        MonthYearPickerDialog(
            initialYearMonth = syncSinceYearMonth,
            onDismissRequest = { showMonthYearDialog = false },
            onYearMonthSelected = { selectedYearMonth ->
                viewModel.updateSyncSinceYearMonth(selectedYearMonth)
            }
        )
    }

    if (showSyncRulesDialog) {
        GmailSyncRulesDialog(
            initialRules = syncRules,
            onSaveRules = { viewModel.updateGmailSyncRules(it) },
            onDismiss = { showSyncRulesDialog = false }
        )
    }

    if (showCardMappingDialog) {
        CardMappingDialog(
            viewModel = viewModel,
            statements = statementSummaries,
            allAccounts = allAccounts,
            onDismiss = { showCardMappingDialog = false },
            onAddNewCardAccount = { showCardMappingDialog = false }
        )
    }

    itemToUnlock?.let { lockedItem ->
        UnlockStatementDialog(
            item = lockedItem,
            onUnlock = { password, onResult ->
                viewModel.unlockGmailHistoryStatement(lockedItem, password, onResult)
            },
            onDismiss = { itemToUnlock = null }
        )
    }

    itemForOptions?.let { optionsItem ->
        StatementHistoryOptionsDialog(
            item = optionsItem,
            onIgnoreSubject = { keyword ->
                viewModel.ignoreEmailSubjectAndCleanHistory(keyword)
            },
            onDeleteHistoryItem = {
                viewModel.deleteImportHistoryItem(optionsItem.messageId)
            },
            onDismiss = { itemForOptions = null }
        )
    }
}

@Composable
fun MonthYearPickerDialog(
    initialYearMonth: YearMonth,
    onDismissRequest: () -> Unit,
    onYearMonthSelected: (YearMonth) -> Unit
) {
    val currentYearMonth = remember { YearMonth.now() }
    var selectedYear by remember { mutableIntStateOf(initialYearMonth.year) }
    var selectedMonth by remember { mutableIntStateOf(initialYearMonth.monthValue) }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = OneUiDialogShape,
            color = OneUiTheme.colors.surfaceElevated,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Sync Statements Since",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = OneUiTheme.colors.textPrimary
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Select the starting month to scan for bank & card statements.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = OneUiTheme.colors.textSecondary
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Year selector with < Year >
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { selectedYear-- }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Previous Year",
                            tint = OneUiTheme.colors.textPrimary
                        )
                    }

                    Text(
                        text = selectedYear.toString(),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = OneUiTheme.colors.primaryAccent
                        )
                    )

                    IconButton(
                        onClick = {
                            if (selectedYear < currentYearMonth.year) {
                                selectedYear++
                                if (selectedYear == currentYearMonth.year && selectedMonth > currentYearMonth.monthValue) {
                                    selectedMonth = currentYearMonth.monthValue
                                }
                            }
                        },
                        enabled = selectedYear < currentYearMonth.year
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Next Year",
                            tint = if (selectedYear < currentYearMonth.year) OneUiTheme.colors.textPrimary else OneUiTheme.colors.textMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 4x3 Months Grid
                val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                for (row in 0 until 4) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (col in 0 until 3) {
                            val monthNum = row * 3 + col + 1
                            val isFuture = selectedYear == currentYearMonth.year && monthNum > currentYearMonth.monthValue
                            val isSelected = (selectedMonth == monthNum)

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        when {
                                            isSelected -> OneUiTheme.colors.primaryAccent
                                            isFuture -> OneUiTheme.colors.surfaceElevated.copy(alpha = 0.5f)
                                            else -> OneUiTheme.colors.surface
                                        }
                                    )
                                    .clickable(enabled = !isFuture) {
                                        selectedMonth = monthNum
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = monthNames[monthNum - 1],
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = when {
                                            isSelected -> Color.White
                                            isFuture -> OneUiTheme.colors.textMuted
                                            else -> OneUiTheme.colors.textPrimary
                                        }
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onDismissRequest),
                        shape = CircleShape,
                        color = OneUiTheme.colors.surface
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Cancel",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = OneUiTheme.colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp
                                )
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(CircleShape)
                            .clickable {
                                onYearMonthSelected(YearMonth.of(selectedYear, selectedMonth))
                                onDismissRequest()
                            },
                        shape = CircleShape,
                        color = OneUiTheme.colors.primaryAccent
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Set & Sync",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportHistoryRow(
    item: GmailImportHistoryEntity,
    onUnlockClick: (GmailImportHistoryEntity) -> Unit,
    onOptionsClick: (GmailImportHistoryEntity) -> Unit
) {
    val isPasswordRequired = item.importStatus == GmailImportStatus.PASSWORD_REQUIRED.name
    val isUnsupportedOrFailed = item.importStatus in listOf(
        GmailImportStatus.NO_PARSER_MATCH.name,
        GmailImportStatus.PARSE_FAILED.name
    )

    OneUiCard(
        onClick = {
            if (isPasswordRequired) {
                onUnlockClick(item)
            } else if (isUnsupportedOrFailed) {
                onOptionsClick(item)
            }
        },
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

                if (isPasswordRequired) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = OneUiTheme.colors.warning,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Tap to enter password • Saves to list",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = OneUiTheme.colors.warning,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        )
                    }
                } else if (isUnsupportedOrFailed) {
                    Text(
                        text = "Tap for options • Ignore similar emails",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = OneUiTheme.colors.textMuted,
                            fontSize = 10.5.sp
                        ),
                        modifier = Modifier.padding(top = 2.dp)
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

@Composable
private fun UnlockStatementDialog(
    item: GmailImportHistoryEntity,
    onUnlock: (password: String, onResult: (PasswordUnlockOutcome) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    OneUiBottomDialog(
        onDismissRequest = { if (!isVerifying) onDismiss() },
        title = "Unlock Statement PDF",
        confirmButtonText = if (isVerifying) "Verifying..." else "Unlock & Save",
        confirmButtonColor = OneUiTheme.colors.primaryAccent,
        onConfirm = {
            if (passwordInput.isBlank()) {
                errorMessage = "Please enter a password."
                return@OneUiBottomDialog
            }
            isVerifying = true
            errorMessage = null
            onUnlock(passwordInput) { outcome ->
                isVerifying = false
                when (outcome) {
                    is PasswordUnlockOutcome.Success -> {
                        onDismiss()
                    }
                    is PasswordUnlockOutcome.InvalidPassword -> {
                        errorMessage = outcome.message
                    }
                    is PasswordUnlockOutcome.Error -> {
                        errorMessage = outcome.message
                    }
                }
            }
        },
        dismissButtonText = "Cancel"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Enter password to decrypt '${item.fileName.ifBlank { item.subject }}'.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = OneUiTheme.colors.textPrimary,
                    fontWeight = FontWeight.Medium
                )
            )

            Text(
                text = "If correct, the statement will be imported and the password will be saved to your Statement Passwords list for all future imports.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = OneUiTheme.colors.textSecondary,
                    fontSize = 12.sp
                )
            )

            OutlinedTextField(
                value = passwordInput,
                onValueChange = {
                    passwordInput = it
                    errorMessage = null
                },
                placeholder = { Text("Statement password") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = OneUiTheme.colors.textSecondary
                        )
                    }
                },
                isError = errorMessage != null,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OneUiTheme.colors.primaryAccent,
                    unfocusedBorderColor = OneUiTheme.colors.surfaceElevated,
                    focusedContainerColor = OneUiTheme.colors.surfaceElevated,
                    unfocusedContainerColor = OneUiTheme.colors.surfaceElevated,
                    errorContainerColor = OneUiTheme.colors.surfaceElevated,
                    focusedTextColor = OneUiTheme.colors.textPrimary,
                    unfocusedTextColor = OneUiTheme.colors.textPrimary
                )
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage ?: "",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = OneUiTheme.colors.expense,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                )
            }

            if (isVerifying) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = OneUiTheme.colors.primaryAccent
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Verifying password and decrypting PDF...",
                        style = MaterialTheme.typography.bodySmall.copy(color = OneUiTheme.colors.textSecondary)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatementHistoryOptionsDialog(
    item: GmailImportHistoryEntity,
    onIgnoreSubject: (keyword: String) -> Unit,
    onDeleteHistoryItem: () -> Unit,
    onDismiss: () -> Unit
) {
    val candidateKeyword = remember(item.subject, item.fileName) {
        val s = item.subject.trim()
        when {
            s.contains("Combined", ignoreCase = true) -> "Combined"
            s.contains("Savings", ignoreCase = true) -> "Savings"
            s.contains("Smart Statement", ignoreCase = true) -> "Smart Statement"
            s.contains("e-statement for your", ignoreCase = true) -> item.bankName
            else -> s.take(28)
        }
    }

    OneUiBottomDialog(
        onDismissRequest = onDismiss,
        title = "Statement Options",
        confirmButtonText = "Close",
        onConfirm = onDismiss,
        dismissButtonText = "Cancel"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "${item.bankName} • ${item.subject}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = OneUiTheme.colors.textPrimary
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (!item.errorMessage.isNullOrBlank()) {
                Text(
                    text = item.errorMessage,
                    style = MaterialTheme.typography.bodySmall.copy(color = OneUiTheme.colors.textSecondary)
                )
            }

            OneUiInsetDivider()

            // Option 1: Ignore similar emails
            OneUiCard(
                onClick = {
                    onIgnoreSubject(candidateKeyword)
                    onDismiss()
                },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OneUiIconBadge(
                        icon = Icons.Default.FilterList,
                        backgroundColor = OneUiTheme.colors.badgeTeal
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ignore Similar Emails",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = OneUiTheme.colors.textPrimary
                            )
                        )
                        Text(
                            text = "Skip emails with subjects containing \"$candidateKeyword\" and remove matching entries from history.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = OneUiTheme.colors.textSecondary,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                }
            }

            // Option 2: Delete from history
            OneUiCard(
                onClick = {
                    onDeleteHistoryItem()
                    onDismiss()
                },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OneUiIconBadge(
                        icon = Icons.Default.Delete,
                        backgroundColor = OneUiTheme.colors.badgeOrange
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Remove From History",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = OneUiTheme.colors.expense
                            )
                        )
                        Text(
                            text = "Delete this statement item from import history.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = OneUiTheme.colors.textSecondary,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
