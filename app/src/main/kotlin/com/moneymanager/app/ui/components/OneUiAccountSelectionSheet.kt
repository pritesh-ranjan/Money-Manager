package com.moneymanager.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.AccountType
import com.moneymanager.app.data.local.entity.AccountTypeEntity
import com.moneymanager.app.data.local.entity.AccountWithCurrency
import com.moneymanager.app.data.local.entity.TransactionWithDetails
import com.moneymanager.app.data.local.entity.resolvedAccountTypeUid
import com.moneymanager.app.ui.theme.OneUiTheme
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneUiAccountSelectionDialog(
    title: String = "Select account",
    accounts: List<AccountWithCurrency>,
    selectedUid: String = "",
    allTransactions: List<TransactionWithDetails> = emptyList(),
    accountTypesMap: Map<String, AccountTypeEntity> = emptyMap(),
    typeColor: Color = OneUiTheme.colors.primaryAccent,
    initialFilter: String = "All",
    allowedAccountTypes: Set<AccountType>? = null,
    allowNoneOption: Boolean = false,
    noneOptionLabel: String = "None (Auto-match by name)",
    onDismissRequest: () -> Unit,
    onSelect: (uid: String) -> Unit,
    onAddNewAccount: (defaultType: AccountType) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = OneUiTheme.colors.surface,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        OneUiAccountSelectionSheet(
            title = title,
            accounts = accounts,
            selectedUid = selectedUid,
            allTransactions = allTransactions,
            accountTypesMap = accountTypesMap,
            typeColor = typeColor,
            initialFilter = initialFilter,
            allowedAccountTypes = allowedAccountTypes,
            allowNoneOption = allowNoneOption,
            noneOptionLabel = noneOptionLabel,
            onClose = onDismissRequest,
            onSelect = onSelect,
            onAddNewAccount = onAddNewAccount
        )
    }
}

@Composable
fun OneUiAccountSelectionSheet(
    title: String,
    accounts: List<AccountWithCurrency>,
    selectedUid: String,
    allTransactions: List<TransactionWithDetails> = emptyList(),
    accountTypesMap: Map<String, AccountTypeEntity> = emptyMap(),
    typeColor: Color = OneUiTheme.colors.primaryAccent,
    initialFilter: String = "All",
    allowedAccountTypes: Set<AccountType>? = null,
    allowNoneOption: Boolean = false,
    noneOptionLabel: String = "None (Auto-match by name)",
    onClose: () -> Unit,
    onSelect: (uid: String) -> Unit,
    onAddNewAccount: (defaultType: AccountType) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(initialFilter) }

    val filterChips = remember {
        listOf("All", "Cards", "Cash", "Accounts", "Investments", "Wallets")
    }

    val recentAccounts = remember(allTransactions, accounts) {
        val accMap = accounts.associateBy { it.account.uid }
        val found = allTransactions
            .asSequence()
            .map { it.transaction.accountUid }
            .distinct()
            .mapNotNull { accMap[it] }
            .take(2)
            .toList()

        if (found.isNotEmpty()) {
            found
        } else {
            accounts.take(2)
        }
    }

    val filteredAccounts = remember(accounts, selectedFilter, searchQuery) {
        accounts.filter { acc ->
            val matchesFilter = when (selectedFilter) {
                "All" -> true
                "Cards" -> acc.account.accountType == AccountType.CREDIT_CARD
                "Cash" -> acc.account.accountType == AccountType.CASH
                "Accounts" -> acc.account.accountType in listOf(AccountType.SAVINGS, AccountType.UPI_DEBIT)
                "Investments" -> acc.account.accountType in listOf(AccountType.INVESTMENT, AccountType.MUTUAL_FUNDS, AccountType.EQUITY)
                "Wallets" -> acc.account.accountType == AccountType.WALLET
                "Other" -> acc.account.accountType in listOf(AccountType.LOAN, AccountType.OTHER)
                else -> true
            }
            val matchesSearch = searchQuery.isBlank() || acc.account.name.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OneUiTheme.colors.surface)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = OneUiTheme.colors.textPrimary,
                    fontSize = 18.sp
                )
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = OneUiTheme.colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Search Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(CircleShape)
                .background(OneUiTheme.colors.surfaceElevated)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = OneUiTheme.colors.textMuted,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = OneUiTheme.colors.textPrimary,
                        fontSize = 15.sp
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(typeColor),
                    decorationBox = { inner ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search accounts...",
                                color = OneUiTheme.colors.textMuted,
                                fontSize = 15.sp
                            )
                        }
                        inner()
                    }
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { searchQuery = "" },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = OneUiTheme.colors.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Filter Chips Row
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filterChips) { chip ->
                val isSelected = chip == selectedFilter
                val chipBg = if (isSelected) {
                    OneUiTheme.colors.badgePurple
                } else {
                    OneUiTheme.colors.surfaceElevated
                }
                val chipText = if (isSelected) Color.White else OneUiTheme.colors.textPrimary

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(chipBg)
                        .clickable { selectedFilter = chip }
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = chip,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = chipText,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Scrollable Accounts List
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Optional None / Auto-Match Row
            if (allowNoneOption) {
                item(key = "option_none_auto_match") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect("") }
                            .padding(horizontal = 4.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(OneUiTheme.colors.surfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LinkOff,
                                contentDescription = null,
                                tint = OneUiTheme.colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = noneOptionLabel,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = OneUiTheme.colors.textPrimary,
                                    fontSize = 15.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Use default name/digits matching",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = OneUiTheme.colors.textSecondary,
                                    fontSize = 13.sp
                                )
                            )
                        }
                        OneUiSelectionCircle(
                            selected = selectedUid.isBlank(),
                            typeColor = typeColor
                        )
                    }
                    OneUiInsetDivider(startIndent = 44.dp)
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            // RECENTLY USED
            if (searchQuery.isBlank() && recentAccounts.isNotEmpty()) {
                item(key = "section_recent_accounts") {
                    Text(
                        text = "RECENTLY USED",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = OneUiTheme.colors.textSecondary,
                            letterSpacing = 0.8.sp,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp, start = 4.dp)
                    )
                }

                items(recentAccounts, key = { "recent_${it.account.uid}" }) { accWithCurr ->
                    val isAllowed = allowedAccountTypes == null || accWithCurr.account.accountType in allowedAccountTypes
                    val (cleanName, baseSubtitle) = getAccountDisplayInfo(accWithCurr.account, accountTypesMap)
                    val subtitle = if (!isAllowed) "$baseSubtitle • Disabled" else baseSubtitle
                    val isSelected = accWithCurr.account.uid == selectedUid

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .then(
                                if (isAllowed) {
                                    Modifier.clickable { onSelect(accWithCurr.account.uid) }
                                } else {
                                    Modifier
                                }
                            )
                            .padding(horizontal = 4.dp, vertical = 12.dp)
                            .alpha(if (isAllowed) 1f else 0.38f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OneUiAccountTypeIcon(
                            accountType = accWithCurr.account.accountType,
                            modifier = Modifier.size(24.dp),
                            tint = if (isAllowed) OneUiTheme.colors.textPrimary else OneUiTheme.colors.textMuted
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cleanName,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isAllowed) OneUiTheme.colors.textPrimary else OneUiTheme.colors.textMuted,
                                    fontSize = 15.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isAllowed) OneUiTheme.colors.textSecondary else OneUiTheme.colors.textMuted,
                                    fontSize = 13.sp
                                )
                            )
                        }
                        if (isAllowed) {
                            OneUiSelectionCircle(
                                selected = isSelected,
                                typeColor = typeColor
                            )
                        }
                    }
                }

                item(key = "spacer_recent") {
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }

            // ALL ACCOUNTS · [FILTER]
            val sectionTitle = if (selectedFilter == "All") "ALL ACCOUNTS" else "ALL ACCOUNTS · ${selectedFilter.uppercase(Locale.getDefault())}"
            item(key = "section_all_accounts") {
                Text(
                    text = sectionTitle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = OneUiTheme.colors.textSecondary,
                        letterSpacing = 0.8.sp,
                        fontSize = 11.sp
                    ),
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp, start = 4.dp)
                )
            }

            if (filteredAccounts.isEmpty()) {
                item(key = "empty_accounts") {
                    Text(
                        text = "No accounts found",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = OneUiTheme.colors.textMuted,
                            fontSize = 14.sp
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp)
                    )
                }
            } else {
                items(filteredAccounts, key = { it.account.uid }) { accWithCurr ->
                    val isAllowed = allowedAccountTypes == null || accWithCurr.account.accountType in allowedAccountTypes
                    val (cleanName, baseSubtitle) = getAccountDisplayInfo(accWithCurr.account, accountTypesMap)
                    val subtitle = if (!isAllowed) "$baseSubtitle • Disabled" else baseSubtitle
                    val isSelected = accWithCurr.account.uid == selectedUid

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .then(
                                if (isAllowed) {
                                    Modifier.clickable { onSelect(accWithCurr.account.uid) }
                                } else {
                                    Modifier
                                }
                            )
                            .padding(horizontal = 4.dp, vertical = 12.dp)
                            .alpha(if (isAllowed) 1f else 0.38f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OneUiAccountTypeIcon(
                            accountType = accWithCurr.account.accountType,
                            modifier = Modifier.size(24.dp),
                            tint = if (isAllowed) OneUiTheme.colors.textPrimary else OneUiTheme.colors.textMuted
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cleanName,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isAllowed) OneUiTheme.colors.textPrimary else OneUiTheme.colors.textMuted,
                                    fontSize = 15.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isAllowed) OneUiTheme.colors.textSecondary else OneUiTheme.colors.textMuted,
                                    fontSize = 13.sp
                                )
                            )
                        }
                        if (isAllowed) {
                            OneUiSelectionCircle(
                                selected = isSelected,
                                typeColor = typeColor
                            )
                        }
                    }
                }
            }

            // New account button
            item(key = "add_new_account") {
                val defaultTypeForFilter = when (selectedFilter) {
                    "Cards" -> AccountType.CREDIT_CARD
                    "Cash" -> AccountType.CASH
                    "Investments" -> AccountType.MUTUAL_FUNDS
                    "Wallets" -> AccountType.WALLET
                    else -> if (allowedAccountTypes?.contains(AccountType.CREDIT_CARD) == true) AccountType.CREDIT_CARD else AccountType.SAVINGS
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onAddNewAccount(defaultTypeForFilter) }
                        .padding(horizontal = 4.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New account",
                        tint = typeColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = "New account",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = typeColor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun OneUiSelectionCircle(
    selected: Boolean,
    typeColor: Color,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Box(
            modifier = modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(typeColor),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    } else {
        Box(
            modifier = modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(OneUiTheme.colors.surfaceElevated.copy(alpha = 0.8f))
                .border(1.5.dp, OneUiTheme.colors.textMuted.copy(alpha = 0.45f), CircleShape)
        )
    }
}

@Composable
fun OneUiAccountTypeIcon(
    accountType: AccountType,
    modifier: Modifier = Modifier,
    tint: Color = OneUiTheme.colors.textPrimary
) {
    val icon = when (accountType) {
        AccountType.CREDIT_CARD -> Icons.Default.CreditCard
        AccountType.CASH -> Icons.Default.AccountBalanceWallet
        AccountType.SAVINGS, AccountType.UPI_DEBIT -> Icons.Default.AccountBalance
        AccountType.INVESTMENT, AccountType.MUTUAL_FUNDS, AccountType.EQUITY -> Icons.AutoMirrored.Filled.TrendingUp
        AccountType.WALLET -> Icons.Default.AccountBalanceWallet
        AccountType.LOAN, AccountType.OTHER -> Icons.Default.Savings
    }
    Icon(
        imageVector = icon,
        contentDescription = accountType.displayName,
        tint = tint,
        modifier = modifier
    )
}

fun getAccountDisplayInfo(
    account: AccountEntity,
    accountTypesMap: Map<String, AccountTypeEntity> = emptyMap()
): Pair<String, String> {
    val fourDigitsRegex = Regex("^(.*?)[\\s\\-–—\\(]*[•\\*xX]*(\\d{4})\\)?$")
    val match = fourDigitsRegex.find(account.name.trim())
    return if (match != null && match.groupValues[1].isNotBlank()) {
        val cleanName = match.groupValues[1].trim()
        val digits = match.groupValues[2]
        Pair(cleanName, "•••• $digits")
    } else {
        val customName = accountTypesMap[account.resolvedAccountTypeUid]?.name
        val subtitle = customName ?: when (account.accountType) {
            AccountType.CREDIT_CARD -> "Credit card"
            AccountType.CASH -> "Cash"
            AccountType.SAVINGS -> "Bank / Savings"
            AccountType.UPI_DEBIT -> "UPI / Debit"
            AccountType.WALLET -> "Wallet"
            AccountType.INVESTMENT -> "Investment"
            AccountType.MUTUAL_FUNDS -> "Mutual funds"
            AccountType.EQUITY -> "Equity"
            AccountType.LOAN -> "Loan"
            AccountType.OTHER -> "Other"
        }
        Pair(account.name, subtitle)
    }
}
