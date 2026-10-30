package com.moneymanager.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.AccountType
import com.moneymanager.app.data.local.entity.CurrencyEntity
import com.moneymanager.app.domain.AccountBalanceItem
import com.moneymanager.app.domain.CurrencyFormatter
import com.moneymanager.app.ui.components.*
import com.moneymanager.app.ui.main.MainViewModel
import com.moneymanager.app.ui.theme.*
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

@Composable
fun AccountsScreen(
    viewModel: MainViewModel
) {
    val netWorthSummary by viewModel.netWorth.collectAsState()
    val allCurrencies by viewModel.allCurrencies.collectAsState()
    val allAccounts by viewModel.accounts.collectAsState()

    var showAdjustDialogFor by remember { mutableStateOf<AccountBalanceItem?>(null) }
    var showAddAccountDialog by remember { mutableStateOf(false) }
    var accountToEdit by remember { mutableStateOf<AccountEntity?>(null) }

    val nw = netWorthSummary
    val netWorthValue = nw?.netWorthBase ?: 0.0
    val netWorthSubtitle = "Net Worth: " + (if (netWorthValue >= 0) "₹" else "-₹") +
            CurrencyFormatter.formatIndianNumber(abs(netWorthValue))

    OneUiCollapsibleScaffold(
        title = "Accounts",
        subtitle = netWorthSubtitle,
        expandedHeight = 175.dp,
        centeredExpandedTitle = true,
        actions = {
            IconButton(
                onClick = { showAddAccountDialog = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Account",
                    tint = OneUiTheme.colors.textPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(OneUiTheme.colors.background),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding() + 6.dp,
                bottom = 96.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Net Worth Hero Card (Screenshot 4 style with Vertical Colored Accent Bars)
            item(key = "net_worth_hero_card") {
                nw?.let { summary ->
                    OneUiCard {
                        Text(
                            text = "Total net worth",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = OneUiTheme.colors.textSecondary,
                                fontSize = 13.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = (if (summary.netWorthBase >= 0) "₹" else "-₹") +
                                    CurrencyFormatter.formatIndianNumber(abs(summary.netWorthBase)),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = OneUiTheme.colors.textPrimary,
                                fontSize = 32.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        OneUiInsetDivider(startIndent = 0.dp)
                        Spacer(modifier = Modifier.height(6.dp))

                        OneUiVerticalAccentRow(
                            title = "Total Assets",
                            subtitle = "Savings, Cash, Mutual Funds, Equity",
                            valueText = "+₹" + CurrencyFormatter.formatIndianNumber(summary.totalAssetsBase),
                            valueColor = OneUiTheme.colors.income,
                            accentColor = OneUiTheme.colors.income
                        )

                        OneUiInsetDivider(startIndent = 16.dp)

                        OneUiVerticalAccentRow(
                            title = "Total Liabilities",
                            subtitle = "Credit Cards & Loans",
                            valueText = "-₹" + CurrencyFormatter.formatIndianNumber(summary.totalLiabilitiesBase),
                            valueColor = OneUiTheme.colors.expense,
                            accentColor = OneUiTheme.colors.expense
                        )
                    }
                }
            }

            // 2. Grouped Account Islands (Screenshots 1 & 2 style with Circular Icon Badges & Inset Dividers)
            val balances = nw?.accountBalances ?: emptyList()

            val groups = listOf(
                Triple("Bank & Savings", Color(0xFF2B75FF) to Icons.Default.AccountBalance, balances.filter { it.account.accountType in listOf(AccountType.SAVINGS, AccountType.UPI_DEBIT) }),
                Triple("Cash & Wallets", Color(0xFF30B050) to Icons.Default.AccountBalanceWallet, balances.filter { it.account.accountType in listOf(AccountType.CASH, AccountType.WALLET) && !it.currency.isCustom }),
                Triple("Credit Cards", Color(0xFF5E5CE6) to Icons.Default.CreditCard, balances.filter { it.account.accountType == AccountType.CREDIT_CARD }),
                Triple("Investments & Equity", Color(0xFF7B61FF) to Icons.Default.TrendingUp, balances.filter { it.account.accountType in listOf(AccountType.MUTUAL_FUNDS, AccountType.EQUITY, AccountType.INVESTMENT) }),
                Triple("Loyalty Points & Miles", Color(0xFFF26419) to Icons.Default.Stars, balances.filter { it.currency.isCustom }),
                Triple("Loans & Other", Color(0xFF00B4D8) to Icons.Default.Savings, balances.filter { it.account.accountType in listOf(AccountType.LOAN, AccountType.OTHER) })
            ).filter { it.third.isNotEmpty() }

            groups.forEach { (sectionTitle, badgePair, items) ->
                item(key = "section_card_$sectionTitle") {
                    OneUiCard {
                        Text(
                            text = sectionTitle,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = OneUiTheme.colors.textSecondary,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        items.forEachIndexed { index, balanceItem ->
                            OneUiAccountBalanceRow(
                                item = balanceItem,
                                badgeColor = badgePair.first,
                                badgeIcon = badgePair.second,
                                onClick = { accountToEdit = balanceItem.account },
                                onAdjustBalance = { showAdjustDialogFor = balanceItem }
                            )

                            if (index < items.lastIndex) {
                                OneUiInsetDivider(startIndent = 52.dp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Revaluation Adjustment Bottom Dialog
    showAdjustDialogFor?.let { item ->
        OneUiRevaluationAdjustmentDialog(
            item = item,
            onDismiss = { showAdjustDialogFor = null },
            onConfirm = { newBalance ->
                viewModel.adjustBalance(item.account, item.currency, item.nativeBalance, newBalance)
                showAdjustDialogFor = null
            }
        )
    }

    // Add Account Dialog
    if (showAddAccountDialog) {
        OneUiAddEditAccountDialog(
            existingAccount = null,
            currencies = allCurrencies,
            existingAccounts = allAccounts.map { it.account },
            onDismiss = { showAddAccountDialog = false },
            onSave = { newAccount ->
                viewModel.saveAccount(newAccount)
                showAddAccountDialog = false
            }
        )
    }

    // Edit Account Dialog
    accountToEdit?.let { acc ->
        OneUiAddEditAccountDialog(
            existingAccount = acc,
            currencies = allCurrencies,
            existingAccounts = allAccounts.map { it.account },
            onDismiss = { accountToEdit = null },
            onSave = { updatedAccount ->
                viewModel.saveAccount(updatedAccount)
                accountToEdit = null
            },
            onDelete = { accToDelete ->
                viewModel.deleteAccount(accToDelete)
                accountToEdit = null
            }
        )
    }
}

/**
 * Account row matching Screenshot 1 (Circular Colored Badge) & Screenshot 2 (Blue active subtext + '|' separator).
 */
@Composable
fun OneUiAccountBalanceRow(
    item: AccountBalanceItem,
    badgeColor: Color,
    badgeIcon: ImageVector,
    onClick: () -> Unit,
    onAdjustBalance: () -> Unit
) {
    val acc = item.account
    val curr = item.currency

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Circular Colored Icon Badge (Screenshot 5)
        OneUiIconBadge(
            icon = badgeIcon,
            backgroundColor = badgeColor
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Account Name & Blue/Muted Subtext (Screenshot 4 "Wi-Fi / LordOfThePings")
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = acc.name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Normal,
                    color = OneUiTheme.colors.textPrimary,
                    fontSize = 15.sp
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            val subText = if (curr.isCustom) {
                "${acc.accountType.displayName} • 1 ${curr.symbol} = ₹${curr.rateToBase}"
            } else {
                acc.accountType.displayName
            }
            Text(
                text = subText,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = OneUiTheme.colors.primaryAccent,
                    fontSize = 12.5.sp
                )
            )
        }

        // Balance Column
        Column(horizontalAlignment = Alignment.End) {
            val isLiability = acc.accountType.isLiability
            val balanceColor = if (isLiability) {
                if (item.nativeBalance > 0) OneUiTheme.colors.expense else OneUiTheme.colors.textSecondary
            } else {
                if (item.nativeBalance >= 0) OneUiTheme.colors.textPrimary else OneUiTheme.colors.expense
            }

            val sign = if (item.nativeBalance < 0 && !isLiability) "-" else ""

            Text(
                text = "$sign${curr.symbol} " + CurrencyFormatter.formatIndianNumber(abs(item.nativeBalance)),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Normal,
                    color = balanceColor,
                    fontSize = 14.5.sp
                )
            )

            if (!curr.isMainCurrency) {
                Text(
                    text = "≈ ₹" + CurrencyFormatter.formatIndianNumber(abs(item.baseBalance)),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = OneUiTheme.colors.textMuted,
                        fontSize = 11.sp
                    )
                )
            }
        }

        // Vertical Divider '|' + Adjust Action (Screenshot 4 style)
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .width(0.8.dp)
                .height(20.dp)
                .background(OneUiTheme.colors.divider)
        )
        Spacer(modifier = Modifier.width(10.dp))

        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(OneUiTheme.colors.surfaceElevated)
                .clickable(onClick = onAdjustBalance),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Adjust Balance",
                tint = OneUiTheme.colors.textSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun OneUiRevaluationAdjustmentDialog(
    item: AccountBalanceItem,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var newBalanceStr by remember { mutableStateOf(String.format(Locale.getDefault(), "%.2f", item.nativeBalance)) }

    OneUiBottomDialog(
        onDismissRequest = onDismiss,
        title = "Adjust Balance",
        confirmButtonText = "Save",
        onConfirm = {
            val target = newBalanceStr.toDoubleOrNull()
            if (target != null) {
                onConfirm(target)
            }
        }
    ) {
        Text(
            text = "Update the current balance for '${item.account.name}'. An automatic adjustment entry will be recorded.",
            style = MaterialTheme.typography.bodySmall,
            color = OneUiTheme.colors.textSecondary
        )
        Spacer(modifier = Modifier.height(14.dp))

        TextField(
            value = newBalanceStr,
            onValueChange = { newBalanceStr = it },
            label = { Text("Actual Balance (${item.currency.symbol})") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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

        val target = newBalanceStr.toDoubleOrNull() ?: item.nativeBalance
        val diff = target - item.nativeBalance
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Adjustment: " + (if (diff >= 0) "+" else "") + String.format("%.2f", diff) + " ${item.currency.symbol}",
            style = MaterialTheme.typography.bodySmall.copy(
                color = if (diff >= 0) OneUiTheme.colors.income else OneUiTheme.colors.expense,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneUiAddEditAccountDialog(
    existingAccount: AccountEntity? = null,
    defaultAccountType: AccountType = AccountType.SAVINGS,
    currencies: List<CurrencyEntity>,
    existingAccounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onSave: (AccountEntity) -> Unit,
    onDelete: ((AccountEntity) -> Unit)? = null
) {
    var name by remember { mutableStateOf(existingAccount?.name ?: "") }
    var selectedType by remember { mutableStateOf(existingAccount?.accountType ?: defaultAccountType) }
    var selectedCurrencyUid by remember {
        mutableStateOf(existingAccount?.currencyUid ?: currencies.firstOrNull { it.isMainCurrency }?.uid ?: currencies.firstOrNull()?.uid ?: "")
    }
    var openingBalanceStr by remember {
        mutableStateOf(existingAccount?.let { String.format(Locale.US, "%.2f", it.openingBalance) } ?: "0.0")
    }
    var statementDayStr by remember {
        mutableStateOf(existingAccount?.statementDay?.toString() ?: if (defaultAccountType == AccountType.CREDIT_CARD) "15" else "")
    }
    var dueDayStr by remember {
        mutableStateOf(existingAccount?.paymentDueDay?.toString() ?: if (defaultAccountType == AccountType.CREDIT_CARD) "5" else "")
    }
    var creditLimitStr by remember {
        mutableStateOf(existingAccount?.creditLimit?.let { String.format(Locale.US, "%.0f", it) } ?: if (defaultAccountType == AccountType.CREDIT_CARD) "100000" else "")
    }
    var settlementAccountUid by remember { mutableStateOf(existingAccount?.settlementAccountUid) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val textFieldColors = TextFieldDefaults.colors(
        focusedTextColor = OneUiTheme.colors.textPrimary,
        unfocusedTextColor = OneUiTheme.colors.textPrimary,
        focusedContainerColor = OneUiTheme.colors.surfaceElevated,
        unfocusedContainerColor = OneUiTheme.colors.surfaceElevated,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent
    )

    OneUiBottomDialog(
        onDismissRequest = onDismiss,
        title = if (existingAccount != null) "Edit Account" else if (defaultAccountType == AccountType.CREDIT_CARD) "New Credit Card" else "New Account",
        confirmButtonText = if (existingAccount != null) "Update" else "Create",
        onConfirm = {
            val cleanName = name.trim()
            if (cleanName.isEmpty()) {
                validationError = "Account name cannot be empty"
                return@OneUiBottomDialog
            }

            val duplicate = existingAccounts.any {
                it.uid != (existingAccount?.uid ?: "") &&
                        !it.isArchived &&
                        it.name.trim().equals(cleanName, ignoreCase = true)
            }
            if (duplicate) {
                validationError = "An active account with name '$cleanName' already exists"
                return@OneUiBottomDialog
            }

            val stmtDay = statementDayStr.toIntOrNull()
            val dueDay = dueDayStr.toIntOrNull()
            val limit = creditLimitStr.toDoubleOrNull()

            if (selectedType == AccountType.CREDIT_CARD) {
                if (stmtDay != null && stmtDay !in 1..31) {
                    validationError = "Statement day must be between 1 and 31"
                    return@OneUiBottomDialog
                }
                if (dueDay != null && dueDay !in 1..31) {
                    validationError = "Payment due day must be between 1 and 31"
                    return@OneUiBottomDialog
                }
                if (limit != null && limit < 0.0) {
                    validationError = "Credit limit cannot be negative"
                    return@OneUiBottomDialog
                }
            }

            onSave(
                AccountEntity(
                    uid = existingAccount?.uid ?: UUID.randomUUID().toString(),
                    name = cleanName,
                    accountType = selectedType,
                    currencyUid = selectedCurrencyUid,
                    openingBalance = openingBalanceStr.toDoubleOrNull() ?: 0.0,
                    statementDay = if (selectedType == AccountType.CREDIT_CARD) stmtDay else null,
                    paymentDueDay = if (selectedType == AccountType.CREDIT_CARD) dueDay else null,
                    creditLimit = if (selectedType == AccountType.CREDIT_CARD) limit else null,
                    settlementAccountUid = if (selectedType == AccountType.CREDIT_CARD) settlementAccountUid else null,
                    includeInNetWorth = existingAccount?.includeInNetWorth ?: true,
                    sortOrder = existingAccount?.sortOrder ?: 0,
                    isArchived = existingAccount?.isArchived ?: false
                )
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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
                label = { Text("Account Name") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = textFieldColors,
                modifier = Modifier.fillMaxWidth()
            )

            // Account Type Selector
            var typeExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = typeExpanded,
                onExpandedChange = { typeExpanded = !typeExpanded }
            ) {
                TextField(
                    value = selectedType.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Account Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                    shape = RoundedCornerShape(18.dp),
                    colors = textFieldColors,
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = typeExpanded,
                    onDismissRequest = { typeExpanded = false },
                    modifier = Modifier.background(OneUiTheme.colors.surfaceElevated)
                ) {
                    AccountType.values().forEach { type ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OneUiRadioButton(
                                        selected = selectedType == type,
                                        onClick = {
                                            selectedType = type
                                            typeExpanded = false
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(type.displayName, color = OneUiTheme.colors.textPrimary)
                                }
                            },
                            onClick = {
                                selectedType = type
                                typeExpanded = false
                            }
                        )
                    }
                }
            }

            // Currency Selector
            var currExpanded by remember { mutableStateOf(false) }
            val currentCurr = currencies.firstOrNull { it.uid == selectedCurrencyUid }
            ExposedDropdownMenuBox(
                expanded = currExpanded,
                onExpandedChange = { currExpanded = !currExpanded }
            ) {
                TextField(
                    value = "${currentCurr?.name ?: ""} (${currentCurr?.symbol ?: "₹"})",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Currency") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currExpanded) },
                    shape = RoundedCornerShape(18.dp),
                    colors = textFieldColors,
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = currExpanded,
                    onDismissRequest = { currExpanded = false },
                    modifier = Modifier.background(OneUiTheme.colors.surfaceElevated)
                ) {
                    currencies.forEach { curr ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OneUiRadioButton(
                                        selected = selectedCurrencyUid == curr.uid,
                                        onClick = {
                                            selectedCurrencyUid = curr.uid
                                            currExpanded = false
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("${curr.name} (${curr.symbol})", color = OneUiTheme.colors.textPrimary)
                                }
                            },
                            onClick = {
                                selectedCurrencyUid = curr.uid
                                currExpanded = false
                            }
                        )
                    }
                }
            }

            if (selectedType == AccountType.CREDIT_CARD) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = statementDayStr,
                        onValueChange = {
                            statementDayStr = it
                            validationError = null
                        },
                        label = { Text("Stmt Day (1-31)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = textFieldColors
                    )
                    TextField(
                        value = dueDayStr,
                        onValueChange = {
                            dueDayStr = it
                            validationError = null
                        },
                        label = { Text("Due Day (1-31)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = textFieldColors
                    )
                }

                TextField(
                    value = creditLimitStr,
                    onValueChange = {
                        creditLimitStr = it
                        validationError = null
                    },
                    label = { Text("Credit Limit") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            TextField(
                value = openingBalanceStr,
                onValueChange = { openingBalanceStr = it },
                label = { Text("Opening Balance") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = textFieldColors,
                modifier = Modifier.fillMaxWidth()
            )

            if (existingAccount != null && onDelete != null) {
                Spacer(modifier = Modifier.height(6.dp))
                OneUiPillButton(
                    text = "Delete or Archive Account",
                    onClick = { showDeleteConfirmDialog = true },
                    containerColor = OneUiTheme.colors.expenseContainer,
                    contentColor = OneUiTheme.colors.expense,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showDeleteConfirmDialog && existingAccount != null && onDelete != null) {
        OneUiBottomDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = "Remove Account",
            confirmButtonText = "Remove",
            confirmButtonColor = OneUiTheme.colors.expense,
            onConfirm = {
                showDeleteConfirmDialog = false
                onDelete(existingAccount)
            }
        ) {
            Text(
                "Are you sure you want to remove '${existingAccount.name}'? If it has transactions, it will be archived safely.",
                color = OneUiTheme.colors.textSecondary
            )
        }
    }
}
