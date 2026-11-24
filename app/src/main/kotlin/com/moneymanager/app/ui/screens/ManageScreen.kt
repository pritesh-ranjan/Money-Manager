package com.moneymanager.app.ui.screens

import android.app.Activity
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.moneymanager.app.data.local.entity.AccountType
import com.moneymanager.app.data.local.entity.AccountTypeEntity
import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.data.local.entity.CategoryType
import com.moneymanager.app.data.local.entity.CurrencyEntity
import com.moneymanager.app.data.local.entity.InstitutionEntity
import com.moneymanager.app.data.local.entity.InstitutionKind
import com.moneymanager.app.data.local.entity.resolvedAccountTypeUid
import com.moneymanager.app.domain.AppLockManager
import com.moneymanager.app.domain.DomainValidator
import com.moneymanager.app.ui.components.*
import com.moneymanager.app.ui.main.MainViewModel
import com.moneymanager.app.ui.theme.*
import java.time.LocalDate
import java.util.UUID

enum class SettingsSubScreen {
    ROOT,
    CATEGORIES,
    ACCOUNT_TYPES,
    BANKS,
    PLATFORMS,
    CURRENCIES,
    APPEARANCE,
    SECURITY,
    TAGS,
    BACKUP,
    INSIGHTS_CONFIG,
    GMAIL_IMPORTER,
    PDF_PASSWORDS
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManageScreen(
    viewModel: MainViewModel,
    externalSubScreen: SettingsSubScreen? = null,
    onExternalSubScreenConsumed: () -> Unit = {}
) {
    val expenseCategories by viewModel.expenseCategories.collectAsState()
    val incomeCategories by viewModel.incomeCategories.collectAsState()
    val allAccountTypes by viewModel.accountTypes.collectAsState()
    val banks by viewModel.banks.collectAsState()
    val platforms by viewModel.platforms.collectAsState()
    val allAccounts by viewModel.accounts.collectAsState()
    val allCurrencies by viewModel.allCurrencies.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    val currentThemeMode by ThemePreferenceManager.themeMode.collectAsState()
    val gmailSyncState by viewModel.gmailSyncState.collectAsState()

    var currentSubScreen by remember { mutableStateOf(externalSubScreen ?: SettingsSubScreen.ROOT) }
    LaunchedEffect(externalSubScreen) {
        if (externalSubScreen != null) {
            currentSubScreen = externalSubScreen
            onExternalSubScreenConsumed()
        }
    }
    var selectedCategoryTab by remember { mutableStateOf(CategoryType.EXPENSE) }

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var parentUidForNewSubcategory by remember { mutableStateOf<String?>(null) }
    var showAddEditAccountTypeDialog by remember { mutableStateOf(false) }
    var accountTypeToEdit by remember { mutableStateOf<AccountTypeEntity?>(null) }
    var accountTypeToDelete by remember { mutableStateOf<AccountTypeEntity?>(null) }
    var institutionDialogKind by remember { mutableStateOf<InstitutionKind?>(null) }
    var institutionToEdit by remember { mutableStateOf<InstitutionEntity?>(null) }
    var institutionToDelete by remember { mutableStateOf<InstitutionEntity?>(null) }
    var showAddCurrencyDialog by remember { mutableStateOf(false) }
    var showAddTagDialog by remember { mutableStateOf(false) }

    // Security & App Lock State
    val isAppLocked by AppLockManager.isLocked.collectAsState()
    var isLockConfigured by remember { mutableStateOf(AppLockManager.isLockConfigured()) }
    var isBiometricEnabled by remember { mutableStateOf(AppLockManager.isBiometricEnabled()) }
    var isPrivacyShieldEnabled by remember { mutableStateOf(AppLockManager.isPrivacyShieldEnabled()) }
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showVerifyDialog by remember { mutableStateOf(false) }
    var pendingSecurityAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // SQLite Backup & Restore Launchers
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/x-sqlite3")
    ) { uri: Uri? ->
        uri?.let { viewModel.exportDatabaseToUri(it) }
    }

    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            pendingRestoreUri = it
            showRestoreConfirmDialog = true
        }
    }

    if (currentSubScreen != SettingsSubScreen.ROOT) {
        BackHandler { currentSubScreen = SettingsSubScreen.ROOT }
    }

    when (currentSubScreen) {
        SettingsSubScreen.ROOT -> {
            // Direct 1:1 Match to Screenshot 1 ("Settings")
            OneUiCollapsibleScaffold(
                title = "Settings",
                expandedHeight = 205.dp,
                centeredExpandedTitle = true
            ) { contentPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(OneUiTheme.colors.background),
                    contentPadding = PaddingValues(
                        top = contentPadding.calculateTopPadding() + 4.dp,
                        bottom = 96.dp,
                        start = 16.dp,
                        end = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Top Profile / Account Card (Matches User Profile / Account design in One UI)
                    item(key = "profile_banner_card") {
                        OneUiCard(
                            onClick = { currentSubScreen = SettingsSubScreen.SECURITY },
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Personal Vault",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = OneUiTheme.colors.textPrimary,
                                            fontSize = 19.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "Money Manager account",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = OneUiTheme.colors.textSecondary,
                                            fontSize = 13.sp
                                        )
                                    )
                                }

                                // Circular Avatar Badge on the Right (Screenshot 5)
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE5A970)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF1C1C1E),
                                        modifier = Modifier.size(46.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 2. Group 1: Categories & Currencies (Matches "Connections / Connected devices" with Blue Badges in Screenshot 5)
                    item(key = "group_ledger_structure") {
                        OneUiCard(
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)
                        ) {
                            OneUiListRow(
                                title = "Categories",
                                leadingContent = {
                                    OneUiIconBadge(
                                        icon = Icons.Default.Category,
                                        backgroundColor = OneUiTheme.colors.badgeBlue
                                    )
                                },
                                onClick = { currentSubScreen = SettingsSubScreen.CATEGORIES }
                            )

                            OneUiInsetDivider(startIndent = 52.dp)

                            OneUiListRow(
                                title = "Account types",
                                subtitle = "${allAccountTypes.size} types configured",
                                leadingContent = {
                                    OneUiIconBadge(
                                        icon = Icons.Default.AccountBalanceWallet,
                                        backgroundColor = OneUiTheme.colors.badgeBlue
                                    )
                                },
                                onClick = { currentSubScreen = SettingsSubScreen.ACCOUNT_TYPES }
                            )

                            OneUiInsetDivider(startIndent = 52.dp)

                            OneUiListRow(
                                title = "Banks",
                                subtitle = if (banks.isEmpty()) "No banks added yet" else "${banks.size} bank${if (banks.size == 1) "" else "s"} configured",
                                leadingContent = {
                                    OneUiIconBadge(
                                        icon = Icons.Default.AccountBalance,
                                        backgroundColor = OneUiTheme.colors.badgeBlue
                                    )
                                },
                                onClick = { currentSubScreen = SettingsSubScreen.BANKS }
                            )

                            OneUiInsetDivider(startIndent = 52.dp)

                            OneUiListRow(
                                title = "Platforms",
                                subtitle = if (platforms.isEmpty()) "No platforms added yet" else "${platforms.size} platform${if (platforms.size == 1) "" else "s"} configured",
                                leadingContent = {
                                    OneUiIconBadge(
                                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                                        backgroundColor = OneUiTheme.colors.badgePurple
                                    )
                                },
                                onClick = { currentSubScreen = SettingsSubScreen.PLATFORMS }
                            )

                            OneUiInsetDivider(startIndent = 52.dp)

                            OneUiListRow(
                                title = "Currencies and points",
                                leadingContent = {
                                    OneUiIconBadge(
                                        icon = Icons.Default.CurrencyExchange,
                                        backgroundColor = OneUiTheme.colors.badgeBlue
                                    )
                                },
                                onClick = { currentSubScreen = SettingsSubScreen.CURRENCIES }
                            )
                        }
                    }

                    // 3. Group 2: Modes, Security, Tags (Matches "Modes and Routines / Sounds / Notifications" in Screenshot 5)
                    item(key = "group_modes_security_tags") {
                        OneUiCard(
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)
                        ) {
                            OneUiListRow(
                                title = "Modes and appearance",
                                subtitle = "${currentThemeMode.label} mode",
                                subtitleColor = OneUiTheme.colors.primaryAccent,
                                leadingContent = {
                                    OneUiIconBadge(
                                        icon = Icons.Default.Palette,
                                        backgroundColor = OneUiTheme.colors.badgeIndigo
                                    )
                                },
                                onClick = { currentSubScreen = SettingsSubScreen.APPEARANCE }
                            )

                            OneUiInsetDivider(startIndent = 52.dp)

                            OneUiListRow(
                                title = "Security and app lock",
                                leadingContent = {
                                    OneUiIconBadge(
                                        icon = Icons.Default.Security,
                                        backgroundColor = OneUiTheme.colors.badgePurple
                                    )
                                },
                                trailingContent = {
                                    OneUiSwitch(
                                        checked = isLockConfigured,
                                        onCheckedChange = { enable ->
                                            if (enable) {
                                                showPinSetupDialog = true
                                            } else {
                                                if (isLockConfigured) {
                                                    pendingSecurityAction = {
                                                        AppLockManager.disableLock()
                                                        isLockConfigured = false
                                                    }
                                                    showVerifyDialog = true
                                                } else {
                                                    AppLockManager.disableLock()
                                                    isLockConfigured = false
                                                }
                                            }
                                        },
                                        showVerticalDivider = true
                                    )
                                },
                                onClick = { currentSubScreen = SettingsSubScreen.SECURITY }
                            )

                            OneUiInsetDivider(startIndent = 52.dp)

                            OneUiListRow(
                                title = "Tags and quick filters",
                                leadingContent = {
                                    OneUiIconBadge(
                                        icon = Icons.Default.Notifications,
                                        backgroundColor = OneUiTheme.colors.badgeOrange
                                    )
                                },
                                onClick = { currentSubScreen = SettingsSubScreen.TAGS }
                            )

                            OneUiInsetDivider(startIndent = 52.dp)

                            OneUiListRow(
                                title = "Insights and detection",
                                subtitle = "Anomaly sensitivity, subscriptions & auto-tagging",
                                leadingContent = {
                                    OneUiIconBadge(
                                        icon = Icons.Default.Analytics,
                                        backgroundColor = OneUiTheme.colors.badgeTeal
                                    )
                                },
                                onClick = { currentSubScreen = SettingsSubScreen.INSIGHTS_CONFIG }
                            )
                        }
                    }

                    // 4. Group 3: Gmail Statement Importer & Passwords
                    item(key = "group_gmail_importer") {
                        OneUiCard(
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)
                        ) {
                            val isConnected = !gmailSyncState?.accountEmail.isNullOrBlank()
                            OneUiListRow(
                                title = "Gmail statement importer",
                                subtitle = if (isConnected) "Connected: ${gmailSyncState?.accountEmail}" else "Auto-import PDF statements on-device",
                                subtitleColor = if (isConnected) OneUiTheme.colors.income else OneUiTheme.colors.textSecondary,
                                leadingContent = {
                                    OneUiIconBadge(
                                        icon = Icons.Default.Email,
                                        backgroundColor = OneUiTheme.colors.badgeOrange
                                    )
                                },
                                onClick = { currentSubScreen = SettingsSubScreen.GMAIL_IMPORTER }
                            )
                        }
                    }

                    // 5. Group 4: Database Backup & Restore (Green Badge at bottom of Screenshot 5)
                    item(key = "group_backup_storage") {
                        OneUiCard(
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)
                        ) {
                            OneUiListRow(
                                title = "Database and backup",
                                leadingContent = {
                                    OneUiIconBadge(
                                        icon = Icons.Default.Save,
                                        backgroundColor = OneUiTheme.colors.badgeGreen
                                    )
                                },
                                onClick = { currentSubScreen = SettingsSubScreen.BACKUP }
                            )
                        }
                    }

                    // 5. Floating Bottom Search Pill (Screenshot 1)
                    item(key = "settings_search_pill") {
                        Spacer(modifier = Modifier.height(6.dp))
                        OneUiFloatingSearchPill(
                            hint = "Search",
                            onClick = { currentSubScreen = SettingsSubScreen.TAGS }
                        )
                    }
                }
            }
        }

        SettingsSubScreen.SECURITY -> {
            // Direct 1:1 Match to Screenshot 2 ("< Connections")
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(OneUiTheme.colors.background)
                    .statusBarsPadding()
            ) {
                OneUiSubScreenTopBar(
                    title = "Security and app lock",
                    onBack = { currentSubScreen = SettingsSubScreen.ROOT }
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Card 1: Toggle Switches with '|' vertical separator (Screenshot 2)
                    item {
                        OneUiCard(
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)
                        ) {
                            OneUiListRow(
                                title = "PIN Lock",
                                subtitle = if (isLockConfigured) "Protected by 4-digit PIN" else null,
                                subtitleColor = OneUiTheme.colors.primaryAccent,
                                trailingContent = {
                                    OneUiSwitch(
                                        checked = isLockConfigured,
                                        onCheckedChange = { enable ->
                                            if (enable) {
                                                showPinSetupDialog = true
                                            } else {
                                                if (isLockConfigured) {
                                                    pendingSecurityAction = {
                                                        AppLockManager.disableLock()
                                                        isLockConfigured = false
                                                    }
                                                    showVerifyDialog = true
                                                } else {
                                                    AppLockManager.disableLock()
                                                    isLockConfigured = false
                                                }
                                            }
                                        },
                                        showVerticalDivider = true
                                    )
                                },
                                onClick = {
                                    if (isLockConfigured) {
                                        pendingSecurityAction = { showPinSetupDialog = true }
                                        showVerifyDialog = true
                                    } else {
                                        showPinSetupDialog = true
                                    }
                                }
                            )

                            OneUiInsetDivider()

                            OneUiListRow(
                                title = "Biometric Unlock",
                                subtitle = if (isBiometricEnabled && isLockConfigured) "Fingerprint & Face active" else null,
                                subtitleColor = OneUiTheme.colors.primaryAccent,
                                trailingContent = {
                                    OneUiSwitch(
                                        checked = isLockConfigured && isBiometricEnabled,
                                        onCheckedChange = { enable ->
                                            if (!isLockConfigured && enable) {
                                                showPinSetupDialog = true
                                            } else {
                                                AppLockManager.setBiometricEnabled(enable)
                                                isBiometricEnabled = enable
                                            }
                                        },
                                        showVerticalDivider = true
                                    )
                                }
                            )

                            OneUiInsetDivider()

                            val currentContext = androidx.compose.ui.platform.LocalContext.current
                            OneUiListRow(
                                title = "Privacy Screen Shield",
                                subtitle = "Hide preview in recent apps",
                                subtitleColor = OneUiTheme.colors.textSecondary,
                                trailingContent = {
                                    OneUiSwitch(
                                        checked = isPrivacyShieldEnabled,
                                        onCheckedChange = { enable ->
                                            val activity = currentContext as? Activity
                                            if (!enable && isLockConfigured) {
                                                pendingSecurityAction = {
                                                    AppLockManager.setPrivacyShieldEnabled(false)
                                                    isPrivacyShieldEnabled = false
                                                    activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                                                }
                                                showVerifyDialog = true
                                            } else {
                                                AppLockManager.setPrivacyShieldEnabled(enable)
                                                isPrivacyShieldEnabled = enable
                                                if (enable) {
                                                    activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                                                } else {
                                                    activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                                                }
                                            }
                                        },
                                        showVerticalDivider = true
                                    )
                                }
                            )
                        }
                    }

                    // Card 2: Standalone Action Row (Matches "Flight mode" / "More connection settings" in Screenshot 2)
                    item {
                        OneUiCard(
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)
                        ) {
                            OneUiListRow(
                                title = if (isLockConfigured) "Change 4-digit PIN" else "Set up 4-digit PIN",
                                onClick = {
                                    if (isLockConfigured) {
                                        pendingSecurityAction = { showPinSetupDialog = true }
                                        showVerifyDialog = true
                                    } else {
                                        showPinSetupDialog = true
                                    }
                                }
                            )
                        }
                    }

                    // Bottom Navy Tinted Card: "Looking for something else?" (Exact match to Screenshot 2)
                    item {
                        OneUiCard(
                            containerColor = OneUiTheme.colors.bannerSurface,
                            onClick = { currentSubScreen = SettingsSubScreen.BACKUP }
                        ) {
                            Text(
                                text = "Looking for something else?",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = OneUiTheme.colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Export or restore your SQLite database vault",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = OneUiTheme.colors.primaryAccent,
                                    fontSize = 13.sp
                                )
                            )
                        }
                    }
                }
            }
        }

        SettingsSubScreen.APPEARANCE -> {
            // One UI Radio Button List Card for Theme Selection
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(OneUiTheme.colors.background)
                    .statusBarsPadding()
            ) {
                OneUiSubScreenTopBar(
                    title = "Modes and appearance",
                    onBack = { currentSubScreen = SettingsSubScreen.ROOT }
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        OneUiCard(
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)
                        ) {
                            ThemeMode.values().forEachIndexed { index, mode ->
                                val isSelected = currentThemeMode == mode
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { ThemePreferenceManager.setThemeMode(mode) }
                                        .padding(vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OneUiRadioButton(
                                        selected = isSelected,
                                        onClick = { ThemePreferenceManager.setThemeMode(mode) }
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            text = mode.label,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                color = OneUiTheme.colors.textPrimary,
                                                fontSize = 16.sp,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                        )
                                        val desc = when (mode) {
                                            ThemeMode.SYSTEM -> "Follow device dark/light schedule"
                                            ThemeMode.DARK -> "Pure OLED pitch black (#000000)"
                                            ThemeMode.LIGHT -> "Soft daylight neutral canvas"
                                        }
                                        Text(
                                            text = desc,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = if (isSelected) OneUiTheme.colors.primaryAccent else OneUiTheme.colors.textSecondary,
                                                fontSize = 13.sp
                                            )
                                        )
                                    }
                                }

                                if (index < ThemeMode.values().lastIndex) {
                                    OneUiInsetDivider(startIndent = 38.dp)
                                }
                            }
                        }
                    }
                }
            }
        }

        SettingsSubScreen.CATEGORIES -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(OneUiTheme.colors.background)
                    .statusBarsPadding()
            ) {
                OneUiSubScreenTopBar(
                    title = "Categories",
                    onBack = { currentSubScreen = SettingsSubScreen.ROOT },
                    actions = {
                        IconButton(
                            onClick = {
                                parentUidForNewSubcategory = null
                                showAddCategoryDialog = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Category",
                                tint = OneUiTheme.colors.textPrimary
                            )
                        }
                    }
                )

                val catList = if (selectedCategoryTab == CategoryType.EXPENSE) expenseCategories else incomeCategories

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        OneUiPillSegmentedControl(
                            items = listOf(CategoryType.EXPENSE, CategoryType.INCOME),
                            selectedItem = selectedCategoryTab,
                            onItemSelected = { selectedCategoryTab = it },
                            labelProvider = { if (it == CategoryType.EXPENSE) "Expenses" else "Income" }
                        )
                    }

                    items(catList, key = { it.category.uid }) { parentWithSubs ->
                        val parent = parentWithSubs.category
                        OneUiCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OneUiEmojiBadge(
                                    emoji = parent.icon,
                                    backgroundColor = OneUiTheme.colors.surfaceElevated
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = parent.name,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = OneUiTheme.colors.textPrimary,
                                            fontSize = 16.sp
                                        )
                                    )
                                    Text(
                                        text = "${parentWithSubs.subcategories.size} subcategories",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = OneUiTheme.colors.primaryAccent,
                                            fontSize = 12.sp
                                        )
                                    )
                                }

                                OneUiPillButton(
                                    text = "+ Sub",
                                    onClick = {
                                        parentUidForNewSubcategory = parent.uid
                                        showAddCategoryDialog = true
                                    }
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                IconButton(
                                    onClick = { viewModel.deleteCategory(parent.uid) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = OneUiTheme.colors.textMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            if (parentWithSubs.subcategories.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                OneUiInsetDivider(startIndent = 50.dp)
                                Spacer(modifier = Modifier.height(8.dp))

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(start = 50.dp)
                                ) {
                                    parentWithSubs.subcategories.forEach { sub ->
                                        OneUiCapsuleChip(
                                            label = sub.name,
                                            onClick = { viewModel.deleteCategory(sub.uid) },
                                            trailingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Remove",
                                                    tint = OneUiTheme.colors.textMuted,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        SettingsSubScreen.ACCOUNT_TYPES -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(OneUiTheme.colors.background)
                    .statusBarsPadding()
            ) {
                OneUiSubScreenTopBar(
                    title = "Account types",
                    onBack = { currentSubScreen = SettingsSubScreen.ROOT },
                    actions = {
                        IconButton(
                            onClick = {
                                accountTypeToEdit = null
                                showAddEditAccountTypeDialog = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Account Type",
                                tint = OneUiTheme.colors.textPrimary
                            )
                        }
                    }
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 10.dp,
                        bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        OneUiCard(
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)
                        ) {
                            allAccountTypes.forEachIndexed { index, type ->
                                val usageCount = allAccounts.count {
                                    !it.account.isArchived && it.account.resolvedAccountTypeUid == type.uid
                                }
                                val (badgeIcon, badgeColor) = accountTypeVisuals(type.baseType)
                                val behaviorSubtitle = buildString {
                                    append(if (type.isLiability) "Liability" else "Asset")
                                    append(" • ")
                                    append(type.baseType.displayName)
                                    if (usageCount > 0) {
                                        append(" • ")
                                        append(usageCount)
                                        append(if (usageCount == 1) " account" else " accounts")
                                    }
                                }

                                OneUiListRow(
                                    title = type.name,
                                    subtitle = behaviorSubtitle,
                                    subtitleColor = if (type.isLiability) OneUiTheme.colors.expense else OneUiTheme.colors.primaryAccent,
                                    leadingContent = {
                                        OneUiIconBadge(
                                            icon = badgeIcon,
                                            backgroundColor = badgeColor
                                        )
                                    },
                                    trailingContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    accountTypeToEdit = type
                                                    showAddEditAccountTypeDialog = true
                                                },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Edit account type",
                                                    tint = OneUiTheme.colors.textSecondary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            if (type.uid != AccountType.CREDIT_CARD.name) {
                                                IconButton(
                                                    onClick = {
                                                        if (usageCount > 0) {
                                                            viewModel.deleteAccountType(type)
                                                        } else {
                                                            accountTypeToDelete = type
                                                        }
                                                    },
                                                    modifier = Modifier.size(34.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Delete account type",
                                                        tint = OneUiTheme.colors.textMuted,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    onClick = {
                                        accountTypeToEdit = type
                                        showAddEditAccountTypeDialog = true
                                    }
                                )

                                if (index < allAccountTypes.lastIndex) {
                                    OneUiInsetDivider(startIndent = 52.dp)
                                }
                            }
                        }
                    }
                }
            }
        }

        SettingsSubScreen.BANKS -> {
            InstitutionManagementSubScreen(
                kind = InstitutionKind.BANK,
                institutions = banks,
                allAccounts = allAccounts,
                onBack = { currentSubScreen = SettingsSubScreen.ROOT },
                onAdd = {
                    institutionToEdit = null
                    institutionDialogKind = InstitutionKind.BANK
                },
                onEdit = { inst ->
                    institutionToEdit = inst
                    institutionDialogKind = InstitutionKind.BANK
                },
                onDelete = { inst, usageCount ->
                    if (usageCount > 0) {
                        viewModel.deleteInstitution(inst)
                    } else {
                        institutionToDelete = inst
                    }
                }
            )
        }

        SettingsSubScreen.PLATFORMS -> {
            InstitutionManagementSubScreen(
                kind = InstitutionKind.PLATFORM,
                institutions = platforms,
                allAccounts = allAccounts,
                onBack = { currentSubScreen = SettingsSubScreen.ROOT },
                onAdd = {
                    institutionToEdit = null
                    institutionDialogKind = InstitutionKind.PLATFORM
                },
                onEdit = { inst ->
                    institutionToEdit = inst
                    institutionDialogKind = InstitutionKind.PLATFORM
                },
                onDelete = { inst, usageCount ->
                    if (usageCount > 0) {
                        viewModel.deleteInstitution(inst)
                    } else {
                        institutionToDelete = inst
                    }
                }
            )
        }

        SettingsSubScreen.CURRENCIES -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(OneUiTheme.colors.background)
                    .statusBarsPadding()
            ) {
                OneUiSubScreenTopBar(
                    title = "Currencies and points",
                    onBack = { currentSubScreen = SettingsSubScreen.ROOT },
                    actions = {
                        IconButton(onClick = { showAddCurrencyDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Currency",
                                tint = OneUiTheme.colors.textPrimary
                            )
                        }
                    }
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        OneUiCard(
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)
                        ) {
                            allCurrencies.forEachIndexed { index, curr ->
                                val currTitle = if (curr.name.contains("(${curr.symbol})")) curr.name else "${curr.name} (${curr.symbol})"
                                OneUiListRow(
                                    title = currTitle,
                                    subtitle = if (curr.isMainCurrency) "Primary Base Currency" else "1 ${curr.symbol} = ₹${curr.rateToBase}",
                                    subtitleColor = OneUiTheme.colors.primaryAccent,
                                    leadingContent = {
                                        OneUiEmojiBadge(
                                            emoji = curr.symbol,
                                            backgroundColor = if (curr.isCustom) OneUiTheme.colors.badgeOrange else OneUiTheme.colors.badgeBlue
                                        )
                                    },
                                    trailingContent = {
                                        if (curr.isCustom) {
                                            OneUiPillButton(
                                                text = "Points",
                                                onClick = {}
                                            )
                                        }
                                    }
                                )

                                if (index < allCurrencies.lastIndex) {
                                    OneUiInsetDivider(startIndent = 52.dp)
                                }
                            }
                        }
                    }
                }
            }
        }

        SettingsSubScreen.TAGS -> {
            // Direct Match to Screenshot 5 ("Recently used settings" Capsule Chips)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(OneUiTheme.colors.background)
                    .statusBarsPadding()
            ) {
                OneUiSubScreenTopBar(
                    title = "",
                    onBack = { currentSubScreen = SettingsSubScreen.ROOT }
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recently used tags",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = OneUiTheme.colors.textPrimary,
                                fontSize = 20.sp
                            )
                        )

                        OneUiPillButton(
                            text = "+ Add tag",
                            onClick = { showAddTagDialog = true },
                            containerColor = OneUiTheme.colors.primaryAccent,
                            contentColor = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        allTags.forEach { tag ->
                            OneUiCapsuleChip(
                                label = tag.name,
                                onClick = { viewModel.deleteTag(tag) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete tag",
                                        tint = OneUiTheme.colors.textSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        SettingsSubScreen.BACKUP -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(OneUiTheme.colors.background)
                    .statusBarsPadding()
            ) {
                OneUiSubScreenTopBar(
                    title = "Database and backup",
                    onBack = { currentSubScreen = SettingsSubScreen.ROOT }
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        OneUiBannerCard(
                            title = "Portable SQLite Vault",
                            subtitle = "Export your entire ledger to a standard SQLite file or restore from an existing backup."
                        )
                    }

                    item {
                        OneUiCard(
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)
                        ) {
                            OneUiListRow(
                                title = "Back up data to SQLite file",
                                subtitle = "Export transactions, accounts, categories & tags",
                                subtitleColor = OneUiTheme.colors.primaryAccent,
                                leadingContent = {
                                    OneUiIconBadge(
                                        icon = Icons.Default.Save,
                                        backgroundColor = OneUiTheme.colors.badgeBlue
                                    )
                                },
                                onClick = {
                                    AppLockManager.beginExternalActivity()
                                    val defaultName = "money_manager_backup_${LocalDate.now()}.sqlite"
                                    exportLauncher.launch(defaultName)
                                }
                            )

                            OneUiInsetDivider(startIndent = 52.dp)

                            OneUiListRow(
                                title = "Restore data from SQLite file",
                                subtitle = "Import and replace current ledger",
                                leadingContent = {
                                    OneUiIconBadge(
                                        icon = Icons.Default.FolderOpen,
                                        backgroundColor = OneUiTheme.colors.badgeGreen
                                    )
                                },
                                onClick = {
                                    AppLockManager.beginExternalActivity()
                                    restoreLauncher.launch("*/*")
                                }
                            )
                        }
                    }
                }
            }
        }

        SettingsSubScreen.INSIGHTS_CONFIG -> {
            val detectorConfig by viewModel.detectorConfig.collectAsState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(OneUiTheme.colors.background)
                    .statusBarsPadding()
            ) {
                OneUiSubScreenTopBar(
                    title = "Insights & Detection",
                    onBack = { currentSubScreen = SettingsSubScreen.ROOT }
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Card 1: Approach A Automatic Tagging
                    item {
                        OneUiCard(
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            OneUiListRow(
                                title = "Auto-tag recurring transactions",
                                subtitle = "Automatically attach #Subscription, #SIP, #Recurring Deposit, or #EMI tags when confidence ≥ 80%",
                                subtitleColor = OneUiTheme.colors.textSecondary,
                                trailingContent = {
                                    OneUiSwitch(
                                        checked = detectorConfig.autoTagEnabled,
                                        onCheckedChange = {
                                            viewModel.updateDetectorConfig(detectorConfig.copy(autoTagEnabled = it))
                                        },
                                        showVerticalDivider = true
                                    )
                                }
                            )
                        }
                    }

                    // Card 2: Anomaly Sensitivity (MAD Robust Z-Score)
                    item {
                        OneUiCard(
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            Text(
                                text = "ANOMALY SENSITIVITY (MAD ROBUST Z)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = OneUiTheme.colors.textMuted
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Outlier Cutoff (Z-Score)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = OneUiTheme.colors.textPrimary
                                )
                                Text(
                                    text = "${String.format("%.1f", detectorConfig.robustZThreshold)}σ",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OneUiTheme.colors.primaryAccent
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            OneUiSlider(
                                value = detectorConfig.robustZThreshold.toFloat(),
                                onValueChange = {
                                    val rounded = (Math.round(it * 10.0) / 10.0)
                                    viewModel.updateDetectorConfig(detectorConfig.copy(robustZThreshold = rounded))
                                },
                                valueRange = 2.0f..6.0f
                            )

                            Spacer(modifier = Modifier.height(14.dp))
                            OneUiInsetDivider()
                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Historical Lookback Window",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = OneUiTheme.colors.textPrimary
                                )
                                Text(
                                    text = "${detectorConfig.lookbackMonths} Months",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OneUiTheme.colors.primaryAccent
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            OneUiSlider(
                                value = detectorConfig.lookbackMonths.toFloat(),
                                onValueChange = {
                                    viewModel.updateDetectorConfig(detectorConfig.copy(lookbackMonths = it.toInt()))
                                },
                                valueRange = 3.0f..6.0f
                            )
                        }
                    }

                    // Card 3: Spikes & Duplicates
                    item {
                        OneUiCard(
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            Text(
                                text = "CATEGORY SPIKES & DUPLICATES",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = OneUiTheme.colors.textMuted
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Category Surge Spike %",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = OneUiTheme.colors.textPrimary
                                )
                                Text(
                                    text = "+${detectorConfig.spikeThresholdPercent.toInt()}%",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OneUiTheme.colors.primaryAccent
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            OneUiSlider(
                                value = detectorConfig.spikeThresholdPercent.toFloat(),
                                onValueChange = {
                                    val rounded = Math.round(it / 5.0) * 5.0
                                    viewModel.updateDetectorConfig(detectorConfig.copy(spikeThresholdPercent = rounded))
                                },
                                valueRange = 20.0f..200.0f
                            )

                            Spacer(modifier = Modifier.height(14.dp))
                            OneUiInsetDivider()
                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Duplicate Charge Time Window",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = OneUiTheme.colors.textPrimary
                                )
                                Text(
                                    text = "${detectorConfig.duplicateExactWindowMinutes} Mins",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OneUiTheme.colors.primaryAccent
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            OneUiSlider(
                                value = detectorConfig.duplicateExactWindowMinutes.toFloat(),
                                onValueChange = {
                                    viewModel.updateDetectorConfig(detectorConfig.copy(duplicateExactWindowMinutes = it.toInt()))
                                },
                                valueRange = 1.0f..60.0f
                            )
                        }
                    }

                    // Card 4: Recurrence Tolerances
                    item {
                        OneUiCard(
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            Text(
                                text = "RECURRENT PAYMENT PATTERNS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = OneUiTheme.colors.textMuted
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Amount Variation Tolerance",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = OneUiTheme.colors.textPrimary
                                )
                                Text(
                                    text = "±${detectorConfig.recurrenceAmountVariationPercent.toInt()}%",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OneUiTheme.colors.primaryAccent
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            OneUiSlider(
                                value = detectorConfig.recurrenceAmountVariationPercent.toFloat(),
                                onValueChange = {
                                    val rounded = Math.round(it / 2.0) * 2.0
                                    viewModel.updateDetectorConfig(detectorConfig.copy(recurrenceAmountVariationPercent = rounded))
                                },
                                valueRange = 0.0f..30.0f
                            )
                        }
                    }

                    // Card 5: Manual Trigger Button
                    item {
                        OneUiCard(
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Reconcile Insights",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = OneUiTheme.colors.textPrimary
                                    )
                                    Text(
                                        text = "Immediately scan your ledger for anomalies, duplicates & subscriptions",
                                        fontSize = 12.sp,
                                        color = OneUiTheme.colors.textSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Button(
                                    onClick = { viewModel.runAnalysisNow() },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(containerColor = OneUiTheme.colors.primaryAccent)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Run Now", color = Color.White, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        SettingsSubScreen.GMAIL_IMPORTER -> {
            com.moneymanager.app.ui.screens.gmail.GmailImporterScreen(
                viewModel = viewModel,
                onBack = { currentSubScreen = SettingsSubScreen.ROOT },
                onNavigateToPasswords = { currentSubScreen = SettingsSubScreen.PDF_PASSWORDS }
            )
        }

        SettingsSubScreen.PDF_PASSWORDS -> {
            PdfPasswordsScreen(
                viewModel = viewModel,
                onBack = { currentSubScreen = SettingsSubScreen.GMAIL_IMPORTER }
            )
        }
    }

    // Set / Change PIN Dialog
    if (showPinSetupDialog) {
        Dialog(
            onDismissRequest = { showPinSetupDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            AppLockScreen(
                mode = LockScreenMode.SET_PIN,
                onSuccess = {
                    isLockConfigured = true
                    isBiometricEnabled = AppLockManager.isBiometricEnabled()
                    showPinSetupDialog = false
                },
                onDismiss = { showPinSetupDialog = false }
            )
        }
    }

    // Verify PIN Dialog before modifying sensitive security settings
    if (showVerifyDialog) {
        Dialog(
            onDismissRequest = {
                showVerifyDialog = false
                pendingSecurityAction = null
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            AppLockScreen(
                mode = LockScreenMode.VERIFY,
                onSuccess = {
                    showVerifyDialog = false
                    val action = pendingSecurityAction
                    pendingSecurityAction = null
                    action?.invoke()
                },
                onDismiss = {
                    showVerifyDialog = false
                    pendingSecurityAction = null
                }
            )
        }
    }

    // Restore Confirmation Bottom Dialog
    if (showRestoreConfirmDialog && pendingRestoreUri != null && !isAppLocked) {
        OneUiBottomDialog(
            onDismissRequest = {
                showRestoreConfirmDialog = false
                pendingRestoreUri = null
            },
            title = "Restore Database",
            confirmButtonText = "Restore",
            confirmButtonColor = OneUiTheme.colors.primaryAccent,
            onConfirm = {
                pendingRestoreUri?.let { viewModel.restoreDatabaseFromUri(it) }
                showRestoreConfirmDialog = false
                pendingRestoreUri = null
            }
        ) {
            Text(
                text = "Restoring an SQLite backup will replace your current transactions, accounts, categories, and tags with the selected file.",
                color = OneUiTheme.colors.textSecondary
            )
        }
    }

    // Add / Edit Account Type Dialog
    if (showAddEditAccountTypeDialog) {
        val editing = accountTypeToEdit
        val usageCount = editing?.let { target ->
            allAccounts.count { !it.account.isArchived && it.account.resolvedAccountTypeUid == target.uid }
        } ?: 0

        OneUiAddEditAccountTypeDialog(
            existingType = editing,
            existingAccountTypes = allAccountTypes,
            usageCount = usageCount,
            onDismiss = {
                showAddEditAccountTypeDialog = false
                accountTypeToEdit = null
            },
            onSave = { savedType ->
                viewModel.saveAccountType(savedType)
                showAddEditAccountTypeDialog = false
                accountTypeToEdit = null
            },
            onDelete = if (editing != null && editing.uid != AccountType.CREDIT_CARD.name) {
                { typeToDelete ->
                    showAddEditAccountTypeDialog = false
                    accountTypeToEdit = null
                    if (usageCount > 0) {
                        viewModel.deleteAccountType(typeToDelete)
                    } else {
                        accountTypeToDelete = typeToDelete
                    }
                }
            } else null
        )
    }

    // Confirm Delete Account Type Dialog
    accountTypeToDelete?.let { typeToDelete ->
        OneUiBottomDialog(
            onDismissRequest = { accountTypeToDelete = null },
            title = "Delete Account Type",
            confirmButtonText = "Delete",
            confirmButtonColor = OneUiTheme.colors.expense,
            onConfirm = {
                viewModel.deleteAccountType(typeToDelete)
                accountTypeToDelete = null
            }
        ) {
            Text(
                text = "Are you sure you want to delete '${typeToDelete.name}'?",
                color = OneUiTheme.colors.textSecondary
            )
        }
    }

    // Add / Edit Institution (Bank or Platform) Dialog
    institutionDialogKind?.let { activeKind ->
        val editing = institutionToEdit
        val existingOfKind = if (activeKind == InstitutionKind.BANK) banks else platforms
        val usageCount = editing?.let { target ->
            allAccounts.count { !it.account.isArchived && it.account.institutionUid == target.uid }
        } ?: 0

        OneUiAddEditInstitutionDialog(
            kind = activeKind,
            existingInstitution = editing,
            existingInstitutions = existingOfKind,
            usageCount = usageCount,
            onDismiss = {
                institutionDialogKind = null
                institutionToEdit = null
            },
            onSave = { saved ->
                viewModel.saveInstitution(saved)
                institutionDialogKind = null
                institutionToEdit = null
            },
            onDelete = if (editing != null) {
                { toDelete ->
                    institutionDialogKind = null
                    institutionToEdit = null
                    if (usageCount > 0) {
                        viewModel.deleteInstitution(toDelete)
                    } else {
                        institutionToDelete = toDelete
                    }
                }
            } else null
        )
    }

    // Confirm Delete Institution Dialog
    institutionToDelete?.let { instToDelete ->
        OneUiBottomDialog(
            onDismissRequest = { institutionToDelete = null },
            title = "Delete ${instToDelete.kind.displayName}",
            confirmButtonText = "Delete",
            confirmButtonColor = OneUiTheme.colors.expense,
            onConfirm = {
                viewModel.deleteInstitution(instToDelete)
                institutionToDelete = null
            }
        ) {
            Text(
                text = "Are you sure you want to delete '${instToDelete.name}'?",
                color = OneUiTheme.colors.textSecondary
            )
        }
    }

    // Add Category Dialog
    if (showAddCategoryDialog) {
        val currentCats = if (selectedCategoryTab == CategoryType.EXPENSE) expenseCategories else incomeCategories
        val flatCats = remember(currentCats) {
            currentCats.flatMap { listOf(it.category) + it.subcategories }
        }
        val parentCat = parentUidForNewSubcategory?.let { pUid -> currentCats.firstOrNull { it.category.uid == pUid }?.category }

        OneUiAddCategoryDialog(
            parentUid = parentUidForNewSubcategory,
            parentName = parentCat?.name,
            categoryType = selectedCategoryTab,
            existingCategories = flatCats,
            onDismiss = { showAddCategoryDialog = false },
            onSave = { newCategory ->
                viewModel.saveCategory(newCategory)
                showAddCategoryDialog = false
            }
        )
    }

    // Add Currency Dialog
    if (showAddCurrencyDialog) {
        var currName by remember { mutableStateOf("") }
        var currIso by remember { mutableStateOf("") }
        var currSymbol by remember { mutableStateOf("🪙") }
        var rateStr by remember { mutableStateOf("0.20") }
        var isCustom by remember { mutableStateOf(true) }
        var validationError by remember { mutableStateOf<String?>(null) }

        val tfColors = TextFieldDefaults.colors(
            focusedTextColor = OneUiTheme.colors.textPrimary,
            unfocusedTextColor = OneUiTheme.colors.textPrimary,
            focusedContainerColor = OneUiTheme.colors.surfaceElevated,
            unfocusedContainerColor = OneUiTheme.colors.surfaceElevated,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )

        OneUiBottomDialog(
            onDismissRequest = { showAddCurrencyDialog = false },
            title = "Add Currency / Points",
            confirmButtonText = "Save",
            onConfirm = {
                val parsedRate = rateStr.trim().toDoubleOrNull()
                if (parsedRate == null || !parsedRate.isFinite() || parsedRate <= 0.0) {
                    validationError = "Enter a valid positive exchange rate"
                    return@OneUiBottomDialog
                }
                val candidate = CurrencyEntity(
                    uid = UUID.randomUUID().toString(),
                    name = currName.trim(),
                    isoCode = currIso.trim().uppercase(),
                    symbol = currSymbol.trim(),
                    rateToBase = parsedRate,
                    isCustom = isCustom,
                    decimalPlaces = if (isCustom) 0 else 2
                )
                try {
                    DomainValidator.validateCurrency(candidate, allCurrencies)
                    viewModel.saveCurrency(candidate)
                    showAddCurrencyDialog = false
                } catch (e: IllegalArgumentException) {
                    validationError = e.message ?: "Invalid currency details"
                }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (validationError != null) {
                    Text(
                        text = validationError!!,
                        color = OneUiTheme.colors.expense,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                TextField(
                    value = currName,
                    onValueChange = {
                        currName = it
                        validationError = null
                    },
                    label = { Text("Name (e.g. Scapia Coins, AED)") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = tfColors,
                    modifier = Modifier.fillMaxWidth()
                )

                TextField(
                    value = currIso,
                    onValueChange = {
                        currIso = it.uppercase()
                        validationError = null
                    },
                    label = { Text("Code (e.g. AED, PTS)") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = tfColors,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = currSymbol,
                        onValueChange = {
                            currSymbol = it
                            validationError = null
                        },
                        label = { Text("Symbol") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = tfColors
                    )

                    TextField(
                        value = rateStr,
                        onValueChange = {
                            rateStr = it
                            validationError = null
                        },
                        label = { Text("Value in ₹") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = tfColors
                    )
                }

                OneUiListRow(
                    title = "Loyalty Points / Miles",
                    trailingContent = {
                        OneUiSwitch(
                            checked = isCustom,
                            onCheckedChange = { isCustom = it }
                        )
                    }
                )
            }
        }
    }

    // Add Tag Dialog
    if (showAddTagDialog) {
        var newTagName by remember { mutableStateOf("") }
        OneUiBottomDialog(
            onDismissRequest = { showAddTagDialog = false },
            title = "New Tag",
            confirmButtonText = "Add",
            onConfirm = {
                if (newTagName.isNotBlank()) {
                    viewModel.saveTag(newTagName.trim())
                    showAddTagDialog = false
                }
            }
        ) {
            TextField(
                value = newTagName,
                onValueChange = { newTagName = it },
                label = { Text("Tag Name") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = OneUiTheme.colors.textPrimary,
                    unfocusedTextColor = OneUiTheme.colors.textPrimary,
                    focusedContainerColor = OneUiTheme.colors.surfaceElevated,
                    unfocusedContainerColor = OneUiTheme.colors.surfaceElevated,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun OneUiAddCategoryDialog(
    parentUid: String? = null,
    parentName: String? = null,
    categoryType: CategoryType = CategoryType.EXPENSE,
    existingCategories: List<CategoryEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (CategoryEntity) -> Unit
) {
    var categoryName by remember { mutableStateOf("") }
    var categoryIcon by remember { mutableStateOf(if (parentUid == null) "📁" else "🏷️") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val tfColors = TextFieldDefaults.colors(
        focusedTextColor = OneUiTheme.colors.textPrimary,
        unfocusedTextColor = OneUiTheme.colors.textPrimary,
        focusedContainerColor = OneUiTheme.colors.surfaceElevated,
        unfocusedContainerColor = OneUiTheme.colors.surfaceElevated,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent
    )

    val dialogTitle = if (parentUid == null) {
        "New Category"
    } else if (!parentName.isNullOrBlank()) {
        "New Subcategory in $parentName"
    } else {
        "New Subcategory"
    }

    OneUiBottomDialog(
        onDismissRequest = onDismiss,
        title = dialogTitle,
        confirmButtonText = "Save",
        onConfirm = {
            val cleanName = categoryName.trim()
            if (cleanName.isBlank()) {
                validationError = "Category name cannot be empty"
                return@OneUiBottomDialog
            }

            val duplicate = existingCategories.any {
                !it.isDeleted &&
                it.type == categoryType &&
                it.parentUid == parentUid &&
                it.name.trim().equals(cleanName, ignoreCase = true)
            }
            if (duplicate) {
                validationError = "A category with name '$cleanName' already exists here"
                return@OneUiBottomDialog
            }

            onSave(
                CategoryEntity(
                    uid = UUID.randomUUID().toString(),
                    name = cleanName,
                    type = categoryType,
                    parentUid = parentUid,
                    icon = categoryIcon.trim().ifEmpty { if (parentUid == null) "📁" else "🏷️" }
                )
            )
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (validationError != null) {
                Text(
                    text = validationError!!,
                    color = OneUiTheme.colors.expense,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            TextField(
                value = categoryName,
                onValueChange = {
                    categoryName = it
                    validationError = null
                },
                label = { Text(if (parentUid == null) "Category Name" else "Subcategory Name") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = tfColors,
                modifier = Modifier.fillMaxWidth()
            )

            TextField(
                value = categoryIcon,
                onValueChange = {
                    categoryIcon = it
                    validationError = null
                },
                label = { Text("Emoji / Icon") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = tfColors,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun InstitutionManagementSubScreen(
    kind: InstitutionKind,
    institutions: List<InstitutionEntity>,
    allAccounts: List<com.moneymanager.app.data.local.entity.AccountWithCurrency>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (InstitutionEntity) -> Unit,
    onDelete: (InstitutionEntity, Int) -> Unit
) {
    val badgeIcon = if (kind == InstitutionKind.BANK) {
        Icons.Default.AccountBalance
    } else {
        Icons.AutoMirrored.Filled.TrendingUp
    }
    val badgeColor = if (kind == InstitutionKind.BANK) {
        OneUiTheme.colors.badgeBlue
    } else {
        OneUiTheme.colors.badgePurple
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OneUiTheme.colors.background)
            .statusBarsPadding()
    ) {
        OneUiSubScreenTopBar(
            title = kind.pluralName,
            onBack = onBack,
            actions = {
                IconButton(onClick = onAdd) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add ${kind.displayName}",
                        tint = OneUiTheme.colors.textPrimary
                    )
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 10.dp,
                bottom = 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (institutions.isEmpty()) {
                item {
                    OneUiCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OneUiIconBadge(
                                icon = badgeIcon,
                                backgroundColor = badgeColor,
                                size = 48.dp,
                                iconSize = 24.dp
                            )
                            Text(
                                text = "No ${kind.pluralName.lowercase()} added yet",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = OneUiTheme.colors.textPrimary
                                )
                            )
                            Text(
                                text = if (kind == InstitutionKind.BANK) {
                                    "Add banks you hold savings, credit card, loan, or wallet accounts with."
                                } else {
                                    "Add investment platforms (e.g. brokers or fund apps) for your equity and mutual fund accounts."
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = OneUiTheme.colors.textSecondary
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OneUiPillButton(
                                text = "Add ${kind.displayName}",
                                onClick = onAdd,
                                containerColor = OneUiTheme.colors.primaryAccent,
                                contentColor = Color.White,
                                leadingIcon = Icons.Default.Add
                            )
                        }
                    }
                }
            } else {
                item {
                    OneUiCard(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)
                    ) {
                        institutions.forEachIndexed { index, inst ->
                            val usageCount = allAccounts.count {
                                !it.account.isArchived && it.account.institutionUid == inst.uid
                            }
                            OneUiListRow(
                                title = inst.name,
                                subtitle = if (usageCount > 0) {
                                    "Used by $usageCount active account${if (usageCount == 1) "" else "s"}"
                                } else {
                                    "Not linked to any active account"
                                },
                                subtitleColor = if (usageCount > 0) OneUiTheme.colors.primaryAccent else OneUiTheme.colors.textSecondary,
                                leadingContent = {
                                    OneUiIconBadge(
                                        icon = badgeIcon,
                                        backgroundColor = badgeColor
                                    )
                                },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { onEdit(inst) },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit ${kind.displayName}",
                                                tint = OneUiTheme.colors.textSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { onDelete(inst, usageCount) },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete ${kind.displayName}",
                                                tint = OneUiTheme.colors.textMuted,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = { onEdit(inst) }
                            )

                            if (index < institutions.lastIndex) {
                                OneUiInsetDivider(startIndent = 52.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OneUiAddEditInstitutionDialog(
    kind: InstitutionKind,
    existingInstitution: InstitutionEntity? = null,
    existingInstitutions: List<InstitutionEntity> = emptyList(),
    usageCount: Int = 0,
    onDismiss: () -> Unit,
    onSave: (InstitutionEntity) -> Unit,
    onDelete: ((InstitutionEntity) -> Unit)? = null
) {
    var name by remember(existingInstitution) { mutableStateOf(existingInstitution?.name ?: "") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val tfColors = TextFieldDefaults.colors(
        focusedTextColor = OneUiTheme.colors.textPrimary,
        unfocusedTextColor = OneUiTheme.colors.textPrimary,
        focusedContainerColor = OneUiTheme.colors.surfaceElevated,
        unfocusedContainerColor = OneUiTheme.colors.surfaceElevated,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent
    )

    OneUiBottomDialog(
        onDismissRequest = onDismiss,
        title = if (existingInstitution != null) "Edit ${kind.displayName}" else "New ${kind.displayName}",
        confirmButtonText = if (existingInstitution != null) "Update" else "Add",
        onConfirm = {
            val cleanName = name.trim()
            if (cleanName.isBlank()) {
                validationError = "${kind.displayName} name cannot be empty"
                return@OneUiBottomDialog
            }

            val duplicate = existingInstitutions.any {
                it.uid != (existingInstitution?.uid ?: "") &&
                    !it.isDeleted &&
                    it.kind == kind &&
                    it.name.trim().equals(cleanName, ignoreCase = true)
            }
            if (duplicate) {
                validationError = "A ${kind.displayName.lowercase()} named '$cleanName' already exists"
                return@OneUiBottomDialog
            }

            onSave(
                InstitutionEntity(
                    uid = existingInstitution?.uid ?: UUID.randomUUID().toString(),
                    name = cleanName,
                    kind = kind,
                    sortOrder = existingInstitution?.sortOrder ?: 0,
                    isDeleted = false
                )
            )
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (validationError != null) {
                Text(
                    text = validationError!!,
                    color = OneUiTheme.colors.expense,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            TextField(
                value = name,
                onValueChange = {
                    name = it
                    validationError = null
                },
                label = { Text("${kind.displayName} Name") },
                placeholder = {
                    Text(
                        if (kind == InstitutionKind.BANK) "e.g. HDFC Bank, ICICI Bank" else "e.g. Zerodha, Groww",
                        color = OneUiTheme.colors.textMuted
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = tfColors,
                modifier = Modifier.fillMaxWidth()
            )

            if (existingInstitution != null && usageCount > 0) {
                Text(
                    text = "Updating this name will automatically update all $usageCount linked account${if (usageCount == 1) "" else "s"}.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = OneUiTheme.colors.textSecondary,
                        fontSize = 12.sp
                    )
                )
            }

            if (existingInstitution != null && onDelete != null) {
                OneUiPillButton(
                    text = "Delete ${kind.displayName}",
                    onClick = { onDelete(existingInstitution) },
                    containerColor = OneUiTheme.colors.expenseContainer,
                    contentColor = OneUiTheme.colors.expense,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneUiAddEditAccountTypeDialog(
    existingType: AccountTypeEntity? = null,
    existingAccountTypes: List<AccountTypeEntity> = emptyList(),
    usageCount: Int = 0,
    onDismiss: () -> Unit,
    onSave: (AccountTypeEntity) -> Unit,
    onDelete: ((AccountTypeEntity) -> Unit)? = null
) {
    val isCreditCardSystemType = existingType?.uid == AccountType.CREDIT_CARD.name
    var name by remember(existingType) { mutableStateOf(existingType?.name ?: "") }
    var baseType by remember(existingType) { mutableStateOf(existingType?.baseType ?: AccountType.OTHER) }
    var isLiability by remember(existingType) {
        mutableStateOf(existingType?.isLiability ?: AccountType.OTHER.isLiability)
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    val tfColors = TextFieldDefaults.colors(
        focusedTextColor = OneUiTheme.colors.textPrimary,
        unfocusedTextColor = OneUiTheme.colors.textPrimary,
        focusedContainerColor = OneUiTheme.colors.surfaceElevated,
        unfocusedContainerColor = OneUiTheme.colors.surfaceElevated,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent
    )

    OneUiBottomDialog(
        onDismissRequest = onDismiss,
        title = if (existingType != null) "Edit Account Type" else "New Account Type",
        confirmButtonText = if (existingType != null) "Update" else "Add",
        onConfirm = {
            val cleanName = name.trim()
            if (cleanName.isBlank()) {
                validationError = "Account type name cannot be empty"
                return@OneUiBottomDialog
            }

            val duplicate = existingAccountTypes.any {
                it.uid != (existingType?.uid ?: "") &&
                    !it.isDeleted &&
                    it.name.trim().equals(cleanName, ignoreCase = true)
            }
            if (duplicate) {
                validationError = "An account type named '$cleanName' already exists"
                return@OneUiBottomDialog
            }

            val effectiveLiability = if (baseType == AccountType.CREDIT_CARD) true else isLiability
            onSave(
                AccountTypeEntity(
                    uid = existingType?.uid ?: UUID.randomUUID().toString(),
                    name = cleanName,
                    baseType = baseType,
                    isLiability = effectiveLiability,
                    isSystemDefault = existingType?.isSystemDefault ?: false,
                    sortOrder = existingType?.sortOrder ?: 0,
                    isDeleted = false
                )
            )
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (validationError != null) {
                Text(
                    text = validationError!!,
                    color = OneUiTheme.colors.expense,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            TextField(
                value = name,
                onValueChange = {
                    name = it
                    validationError = null
                },
                label = { Text("Account Type Name") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = tfColors,
                modifier = Modifier.fillMaxWidth()
            )

            var behaviorExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = behaviorExpanded && !isCreditCardSystemType,
                onExpandedChange = {
                    if (!isCreditCardSystemType) {
                        behaviorExpanded = !behaviorExpanded
                    }
                }
            ) {
                TextField(
                    value = baseType.displayName,
                    onValueChange = {},
                    readOnly = true,
                    enabled = !isCreditCardSystemType,
                    label = { Text("Category / Behavior") },
                    trailingIcon = {
                        if (!isCreditCardSystemType) {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = behaviorExpanded)
                        }
                    },
                    shape = RoundedCornerShape(18.dp),
                    colors = tfColors,
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = behaviorExpanded && !isCreditCardSystemType,
                    onDismissRequest = { behaviorExpanded = false },
                    modifier = Modifier.background(OneUiTheme.colors.surfaceElevated)
                ) {
                    AccountType.values().forEach { candidate ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OneUiRadioButton(
                                        selected = baseType == candidate,
                                        onClick = {
                                            baseType = candidate
                                            if (existingType == null) {
                                                isLiability = candidate.isLiability
                                            } else if (candidate == AccountType.CREDIT_CARD) {
                                                isLiability = true
                                            }
                                            behaviorExpanded = false
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(candidate.displayName, color = OneUiTheme.colors.textPrimary)
                                }
                            },
                            onClick = {
                                baseType = candidate
                                if (existingType == null) {
                                    isLiability = candidate.isLiability
                                } else if (candidate == AccountType.CREDIT_CARD) {
                                    isLiability = true
                                }
                                behaviorExpanded = false
                            }
                        )
                    }
                }
            }

            if (isCreditCardSystemType) {
                Text(
                    text = "Built-in Credit Card behavior is locked for billing & statement tracking",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = OneUiTheme.colors.textSecondary,
                        fontSize = 12.sp
                    )
                )
            }

            val liabilityLocked = baseType == AccountType.CREDIT_CARD
            OneUiListRow(
                title = "Count as Liability",
                subtitle = if (liabilityLocked) {
                    "Deducted from Net Worth as debt (required for Credit Cards)"
                } else {
                    "Deducted from Net Worth as debt"
                },
                trailingContent = {
                    OneUiSwitch(
                        checked = if (liabilityLocked) true else isLiability,
                        onCheckedChange = { checked ->
                            if (!liabilityLocked) {
                                isLiability = checked
                            }
                        },
                        modifier = Modifier.alpha(if (liabilityLocked) 0.55f else 1f)
                    )
                }
            )

            if (existingType != null && onDelete != null) {
                if (usageCount > 0) {
                    Text(
                        text = "In use by $usageCount active account${if (usageCount == 1) "" else "s"} — reassign accounts before deleting",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = OneUiTheme.colors.textSecondary,
                            fontSize = 12.sp
                        )
                    )
                }
                OneUiPillButton(
                    text = "Delete Account Type",
                    onClick = { onDelete(existingType) },
                    containerColor = OneUiTheme.colors.expenseContainer,
                    contentColor = OneUiTheme.colors.expense,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

