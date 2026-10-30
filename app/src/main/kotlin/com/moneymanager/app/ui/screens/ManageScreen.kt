package com.moneymanager.app.ui.screens

import android.net.Uri
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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.data.local.entity.CategoryType
import com.moneymanager.app.data.local.entity.CurrencyEntity
import com.moneymanager.app.domain.AppLockManager
import com.moneymanager.app.ui.components.*
import com.moneymanager.app.ui.main.MainViewModel
import com.moneymanager.app.ui.theme.*
import java.time.LocalDate
import java.util.UUID

enum class SettingsSubScreen {
    ROOT,
    CATEGORIES,
    CURRENCIES,
    APPEARANCE,
    SECURITY,
    TAGS,
    BACKUP
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManageScreen(
    viewModel: MainViewModel
) {
    val expenseCategories by viewModel.expenseCategories.collectAsState()
    val incomeCategories by viewModel.incomeCategories.collectAsState()
    val allCurrencies by viewModel.allCurrencies.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    val currentThemeMode by ThemePreferenceManager.themeMode.collectAsState()

    var currentSubScreen by remember { mutableStateOf(SettingsSubScreen.ROOT) }
    var selectedCategoryTab by remember { mutableStateOf(CategoryType.EXPENSE) }

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var parentUidForNewSubcategory by remember { mutableStateOf<String?>(null) }
    var showAddCurrencyDialog by remember { mutableStateOf(false) }
    var showAddTagDialog by remember { mutableStateOf(false) }

    // Security & App Lock State
    val isAppLocked by AppLockManager.isLocked.collectAsState()
    var isLockConfigured by remember { mutableStateOf(AppLockManager.isLockConfigured()) }
    var isBiometricEnabled by remember { mutableStateOf(AppLockManager.isBiometricEnabled()) }
    var showPinSetupDialog by remember { mutableStateOf(false) }

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
                    // 1. Top Profile / Account Card (Matches "Pritesh Ranjan / Samsung account" in Screenshot 5)
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
                                                AppLockManager.disableLock()
                                                isLockConfigured = false
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
                        }
                    }

                    // 4. Group 3: Database Backup & Restore (Green Badge at bottom of Screenshot 5)
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
                                                AppLockManager.disableLock()
                                                isLockConfigured = false
                                            }
                                        },
                                        showVerticalDivider = true
                                    )
                                },
                                onClick = { showPinSetupDialog = true }
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

                            OneUiListRow(
                                title = "Privacy Screen Shield",
                                subtitle = "Hide preview in recent apps",
                                subtitleColor = OneUiTheme.colors.textSecondary,
                                trailingContent = {
                                    OneUiSwitch(
                                        checked = true,
                                        onCheckedChange = {},
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
                                onClick = { showPinSetupDialog = true }
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
                                OneUiListRow(
                                    title = "${curr.name} (${curr.symbol})",
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

    // Add Category Dialog
    if (showAddCategoryDialog) {
        var categoryName by remember { mutableStateOf("") }
        var categoryIcon by remember { mutableStateOf(if (parentUidForNewSubcategory == null) "📁" else "🏷️") }

        val tfColors = TextFieldDefaults.colors(
            focusedTextColor = OneUiTheme.colors.textPrimary,
            unfocusedTextColor = OneUiTheme.colors.textPrimary,
            focusedContainerColor = OneUiTheme.colors.surfaceElevated,
            unfocusedContainerColor = OneUiTheme.colors.surfaceElevated,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )

        OneUiBottomDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = if (parentUidForNewSubcategory == null) "New Category" else "New Subcategory",
            confirmButtonText = "Save",
            onConfirm = {
                if (categoryName.isNotBlank()) {
                    viewModel.saveCategory(
                        CategoryEntity(
                            uid = UUID.randomUUID().toString(),
                            name = categoryName.trim(),
                            type = selectedCategoryTab,
                            parentUid = parentUidForNewSubcategory,
                            icon = categoryIcon.trim().ifEmpty { "📁" }
                        )
                    )
                    showAddCategoryDialog = false
                }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(
                    value = categoryName,
                    onValueChange = { categoryName = it },
                    label = { Text("Category Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = tfColors,
                    modifier = Modifier.fillMaxWidth()
                )

                TextField(
                    value = categoryIcon,
                    onValueChange = { categoryIcon = it },
                    label = { Text("Emoji / Icon") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = tfColors,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // Add Currency Dialog
    if (showAddCurrencyDialog) {
        var currName by remember { mutableStateOf("") }
        var currIso by remember { mutableStateOf("") }
        var currSymbol by remember { mutableStateOf("🪙") }
        var rateStr by remember { mutableStateOf("0.20") }
        var isCustom by remember { mutableStateOf(true) }

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
                val rate = rateStr.toDoubleOrNull() ?: 1.0
                if (currName.isNotBlank()) {
                    viewModel.saveCurrency(
                        CurrencyEntity(
                            uid = UUID.randomUUID().toString(),
                            name = currName.trim(),
                            isoCode = currIso.trim().ifEmpty { currName.take(6) },
                            symbol = currSymbol.trim().ifEmpty { "¤" },
                            rateToBase = rate,
                            isCustom = isCustom,
                            decimalPlaces = if (isCustom) 0 else 2
                        )
                    )
                    showAddCurrencyDialog = false
                }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(
                    value = currName,
                    onValueChange = { currName = it },
                    label = { Text("Name (e.g. Scapia Coins, USD)") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = tfColors,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = currSymbol,
                        onValueChange = { currSymbol = it },
                        label = { Text("Symbol") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = tfColors
                    )

                    TextField(
                        value = rateStr,
                        onValueChange = { rateStr = it },
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
