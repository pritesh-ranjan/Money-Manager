package com.moneymanager.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.AccountType
import com.moneymanager.app.data.local.entity.AccountTypeDefaults
import com.moneymanager.app.data.local.entity.AccountTypeEntity
import com.moneymanager.app.data.local.entity.CardNetwork
import com.moneymanager.app.data.local.entity.CurrencyEntity
import com.moneymanager.app.data.local.entity.InstitutionEntity
import com.moneymanager.app.data.local.entity.InstitutionKind
import com.moneymanager.app.data.local.entity.InstitutionRequirement
import com.moneymanager.app.data.local.entity.TransactionType
import com.moneymanager.app.data.local.entity.TransactionWithDetails
import com.moneymanager.app.data.local.entity.institutionRequirement
import com.moneymanager.app.data.local.entity.requiredInstitutionKind
import com.moneymanager.app.data.local.entity.resolvedAccountTypeUid
import com.moneymanager.app.data.local.entity.resolvedInstitutionName
import com.moneymanager.app.data.local.entity.resolvedIsLiability
import com.moneymanager.app.data.local.entity.resolvedTypeName
import com.moneymanager.app.domain.AccountBalanceItem
import com.moneymanager.app.domain.CurrencyFormatter
import com.moneymanager.app.domain.DomainValidator
import com.moneymanager.app.ui.components.*
import com.moneymanager.app.ui.main.MainViewModel
import com.moneymanager.app.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

@Composable
fun AccountsScreen(
    viewModel: MainViewModel,
    onEditTransaction: (TransactionWithDetails) -> Unit = {},
    onNavigateToAccountTypes: () -> Unit = {},
    onNavigateToInstitutions: (InstitutionKind) -> Unit = {}
) {
    val netWorthSummary by viewModel.netWorth.collectAsState()
    val allCurrencies by viewModel.allCurrencies.collectAsState()
    val allAccounts by viewModel.accounts.collectAsState()
    val allAccountTypes by viewModel.accountTypes.collectAsState()
    val accountTypesMap by viewModel.accountTypesMap.collectAsState()
    val banks by viewModel.banks.collectAsState()
    val platforms by viewModel.platforms.collectAsState()
    val institutionsMap by viewModel.institutionsMap.collectAsState()

    var selectedAccountUid by remember { mutableStateOf<String?>(null) }
    var showAdjustDialogFor by remember { mutableStateOf<AccountBalanceItem?>(null) }
    var showAddAccountDialog by remember { mutableStateOf(false) }
    var accountToEdit by remember { mutableStateOf<AccountEntity?>(null) }

    // If an account is selected, render the dedicated Account Detail view with quick edit + scrollable transactions
    val activeSelectedAccount = selectedAccountUid?.let { uid ->
        allAccounts.firstOrNull { it.account.uid == uid }?.account
    }

    if (activeSelectedAccount != null) {
        OneUiAccountDetailScreen(
            account = activeSelectedAccount,
            viewModel = viewModel,
            onEditTransaction = onEditTransaction,
            onNavigateToInstitutions = onNavigateToInstitutions,
            onBack = { selectedAccountUid = null }
        )
        return
    }

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
                Triple("Bank & Savings", Color(0xFF2B75FF) to Icons.Default.AccountBalance, balances.filter { it.account.accountType in listOf(AccountType.SAVINGS, AccountType.UPI_DEBIT) && !it.currency.isCustom }),
                Triple("Cash & Wallets", Color(0xFF30B050) to Icons.Default.AccountBalanceWallet, balances.filter { it.account.accountType in listOf(AccountType.CASH, AccountType.WALLET) && !it.currency.isCustom }),
                Triple("Credit Cards", Color(0xFF5E5CE6) to Icons.Default.CreditCard, balances.filter { it.account.accountType == AccountType.CREDIT_CARD && !it.currency.isCustom }),
                Triple("Investments & Equity", Color(0xFF7B61FF) to Icons.Default.TrendingUp, balances.filter { it.account.accountType in listOf(AccountType.MUTUAL_FUNDS, AccountType.EQUITY, AccountType.INVESTMENT) && !it.currency.isCustom }),
                Triple("Loyalty Points & Miles", Color(0xFFF26419) to Icons.Default.Stars, balances.filter { it.currency.isCustom }),
                Triple("Loans & Other", Color(0xFF00B4D8) to Icons.Default.Savings, balances.filter { it.account.accountType in listOf(AccountType.LOAN, AccountType.OTHER) && !it.currency.isCustom })
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
                                typeDisplayName = balanceItem.account.resolvedTypeName(accountTypesMap),
                                institutionName = balanceItem.account.resolvedInstitutionName(institutionsMap),
                                isLiability = balanceItem.account.resolvedIsLiability(accountTypesMap),
                                onClick = { selectedAccountUid = balanceItem.account.uid },
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
            isLiability = item.account.resolvedIsLiability(accountTypesMap),
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
            skipTypeSelection = false,
            accountTypes = allAccountTypes,
            banks = banks,
            platforms = platforms,
            currencies = allCurrencies,
            existingAccounts = allAccounts.map { it.account },
            onDismiss = { showAddAccountDialog = false },
            onSave = { newAccount ->
                viewModel.saveAccount(newAccount)
                showAddAccountDialog = false
            },
            onSaveAccountType = { newType ->
                viewModel.saveAccountType(newType)
            },
            onSaveInstitution = { newInst ->
                viewModel.saveInstitution(newInst)
            },
            onNavigateToAccountTypes = {
                showAddAccountDialog = false
                onNavigateToAccountTypes()
            },
            onNavigateToInstitutions = { kind ->
                showAddAccountDialog = false
                onNavigateToInstitutions(kind)
            }
        )
    }

    // Edit Account Dialog fallback
    accountToEdit?.let { acc ->
        OneUiAddEditAccountDialog(
            existingAccount = acc,
            skipTypeSelection = true,
            accountTypes = allAccountTypes,
            banks = banks,
            platforms = platforms,
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
            },
            onSaveAccountType = { newType ->
                viewModel.saveAccountType(newType)
            },
            onSaveInstitution = { newInst ->
                viewModel.saveInstitution(newInst)
            },
            onNavigateToInstitutions = { kind ->
                accountToEdit = null
                onNavigateToInstitutions(kind)
            }
        )
    }
}

/**
 * Account Detail Sub-Screen featuring:
 * 1. Quick Edit section on top (Account name, type, balances, credit card params, save button).
 * 2. Balance hero with quick "Adjust" button.
 * 3. Scrollable list of all transactions belonging specifically to this account, grouped by date with OneUiDayHeader & OneUiTransactionRowItem.
 */
@Composable
fun OneUiAccountDetailScreen(
    account: AccountEntity,
    viewModel: MainViewModel,
    onEditTransaction: (TransactionWithDetails) -> Unit = {},
    onNavigateToInstitutions: (InstitutionKind) -> Unit = {},
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val allTransactions by viewModel.allTransactionsUnfiltered.collectAsState()
    val allAccounts by viewModel.accounts.collectAsState()
    val categoriesMap by viewModel.categoriesMap.collectAsState()
    val allCurrencies by viewModel.allCurrencies.collectAsState()
    val netWorthSummary by viewModel.netWorth.collectAsState()
    val accountTypes by viewModel.accountTypes.collectAsState()
    val accountTypesMap by viewModel.accountTypesMap.collectAsState()
    val banks by viewModel.banks.collectAsState()
    val platforms by viewModel.platforms.collectAsState()
    val institutionsMap by viewModel.institutionsMap.collectAsState()

    val balanceItem = netWorthSummary?.accountBalances?.firstOrNull { it.account.uid == account.uid }
    val nativeBalance = balanceItem?.nativeBalance ?: account.openingBalance
    val currency = allCurrencies.firstOrNull { it.uid == account.currencyUid }
        ?: balanceItem?.currency
        ?: allCurrencies.firstOrNull { it.isMainCurrency }
        ?: CurrencyEntity(uid = "INR", name = "Indian Rupee", isoCode = "INR", symbol = "₹", rateToBase = 1.0, isMainCurrency = true)

    // Filter transactions belonging to this account specifically
    val accountTransactions = remember(allTransactions, account.uid) {
        allTransactions.filter {
            it.account.uid == account.uid || it.toAccount?.uid == account.uid
        }.sortedByDescending { it.transaction.timestamp }
    }

    // Group transactions by date for day headers
    val groupedByDay = remember(accountTransactions) {
        accountTransactions.groupBy { item ->
            Instant.ofEpochMilli(item.transaction.timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        }
    }

    // Quick Edit Local State
    var quickName by remember(account) { mutableStateOf(account.name) }
    var quickAccountNumber by remember(account) { mutableStateOf(account.accountNumber ?: "") }
    var quickTypeUid by remember(account) { mutableStateOf(account.resolvedAccountTypeUid) }
    val selectedTypeEntity = accountTypes.firstOrNull { it.uid == quickTypeUid } ?: accountTypesMap[quickTypeUid]
    val selectedBaseType = selectedTypeEntity?.baseType ?: account.accountType
    var quickInstitutionUid by remember(account) { mutableStateOf(account.institutionUid) }
    var quickCardNetwork by remember(account) { mutableStateOf(account.cardNetwork) }
    var quickStatementDay by remember(account) { mutableStateOf(account.statementDay?.toString() ?: "") }
    var quickDueDay by remember(account) { mutableStateOf(account.paymentDueDay?.toString() ?: "") }
    var quickLimit by remember(account) { mutableStateOf(account.creditLimit?.let { String.format(Locale.US, "%.0f", it) } ?: "") }
    var quickOpeningBalance by remember(account) { mutableStateOf(String.format(Locale.US, "%.2f", account.openingBalance)) }
    var quickIncludeInNetWorth by remember(account) { mutableStateOf(account.includeInNetWorth) }

    var isQuickEditExpanded by remember { mutableStateOf(true) }
    var showAdjustDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var showSaveSuccess by remember { mutableStateOf(false) }

    val textFieldColors = TextFieldDefaults.colors(
        focusedTextColor = OneUiTheme.colors.textPrimary,
        unfocusedTextColor = OneUiTheme.colors.textPrimary,
        focusedContainerColor = OneUiTheme.colors.surfaceElevated,
        unfocusedContainerColor = OneUiTheme.colors.surfaceElevated,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent
    )

    val detailInstName = account.resolvedInstitutionName(institutionsMap)
    val detailSubtitle = listOfNotNull(
        account.resolvedTypeName(accountTypesMap),
        detailInstName,
        account.cardNetwork?.displayName,
        currency.isoCode
    ).joinToString(" • ")

    OneUiCollapsibleScaffold(
        title = account.name,
        subtitle = detailSubtitle,
        expandedHeight = 155.dp,
        centeredExpandedTitle = true,
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = OneUiTheme.colors.textPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        actions = {
            IconButton(
                onClick = { showDeleteConfirmDialog = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Account",
                    tint = OneUiTheme.colors.expense,
                    modifier = Modifier.size(22.dp)
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
            // Card 1: Balance Hero & Quick Edit Section
            item(key = "account_balance_and_quick_edit") {
                OneUiCard {
                    // Balance Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val isLiability = account.resolvedIsLiability(accountTypesMap)
                            Text(
                                text = if (isLiability && nativeBalance < -0.005) "Credit surplus" else "Current balance",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = OneUiTheme.colors.textSecondary,
                                    fontSize = 13.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))

                            val balanceColor = accountBalanceColor(nativeBalance, isLiability)

                            Text(
                                text = formatAccountBalanceText(nativeBalance, currency.symbol, isLiability),
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = balanceColor,
                                    fontSize = 28.sp
                                )
                            )
                        }

                        // Adjust balance button
                        OneUiCapsuleChip(
                            label = "Adjust",
                            selected = false,
                            onClick = { showAdjustDialog = true }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    OneUiInsetDivider(startIndent = 0.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick Edit Accordion Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { isQuickEditExpanded = !isQuickEditExpanded }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = OneUiTheme.colors.primaryAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Quick account edit",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = OneUiTheme.colors.textPrimary,
                                    fontSize = 14.sp
                                )
                            )
                        }

                        Icon(
                            imageVector = if (isQuickEditExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isQuickEditExpanded) "Collapse" else "Expand",
                            tint = OneUiTheme.colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Collapsible Quick Edit Fields
                    AnimatedVisibility(visible = isQuickEditExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Account Name Field
                            TextField(
                                value = quickName,
                                onValueChange = {
                                    quickName = it
                                    validationError = null
                                    showSaveSuccess = false
                                },
                                label = { Text("Account Name") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = textFieldColors,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Account Number Field (Optional)
                            TextField(
                                value = quickAccountNumber,
                                onValueChange = {
                                    quickAccountNumber = it
                                    validationError = null
                                    showSaveSuccess = false
                                },
                                label = { Text(accountNumberFieldLabel(selectedBaseType)) },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = textFieldColors,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Account Type Selector (same unified options as Create/Edit Account)
                            OneUiAccountTypeDropdown(
                                accountTypes = accountTypes,
                                selectedTypeUid = quickTypeUid,
                                fallbackDisplayName = account.resolvedTypeName(accountTypesMap),
                                onSelect = { type ->
                                    val previousKind = selectedBaseType.requiredInstitutionKind()
                                    val newKind = type.baseType.requiredInstitutionKind()
                                    quickTypeUid = type.uid
                                    if (previousKind != newKind) {
                                        quickInstitutionUid = null
                                    }
                                    if (type.baseType != AccountType.CREDIT_CARD) {
                                        quickCardNetwork = null
                                    }
                                    validationError = null
                                    showSaveSuccess = false
                                },
                                textFieldColors = textFieldColors,
                                cornerRadius = 16.dp
                            )

                            // Centralized Bank / Platform Selector
                            val quickRequiredKind = selectedBaseType.requiredInstitutionKind()
                            val quickInstitutions = if (quickRequiredKind == InstitutionKind.PLATFORM) platforms else banks
                            val quickInstOptional = selectedBaseType.institutionRequirement() == InstitutionRequirement.BANK_OPTIONAL
                            OneUiInstitutionSelector(
                                kind = quickRequiredKind,
                                institutions = quickInstitutions,
                                selectedInstitutionUid = quickInstitutionUid,
                                isOptional = quickInstOptional,
                                onSelect = { uid ->
                                    quickInstitutionUid = uid
                                    validationError = null
                                    showSaveSuccess = false
                                },
                                onManageInSettings = {
                                    onNavigateToInstitutions(quickRequiredKind)
                                },
                                textFieldColors = textFieldColors,
                                cornerRadius = 16.dp
                            )

                            // Card Network for Credit Cards
                            if (selectedBaseType == AccountType.CREDIT_CARD) {
                                OneUiCardNetworkDropdown(
                                    selectedNetwork = quickCardNetwork,
                                    onSelect = { net ->
                                        quickCardNetwork = net
                                        validationError = null
                                        showSaveSuccess = false
                                    },
                                    textFieldColors = textFieldColors,
                                    cornerRadius = 16.dp
                                )
                            }

                            // Credit Card specifics
                            if (selectedBaseType == AccountType.CREDIT_CARD) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextField(
                                        value = quickStatementDay,
                                        onValueChange = { quickStatementDay = it; validationError = null; showSaveSuccess = false },
                                        label = { Text("Stmt Day (1-31)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(16.dp),
                                        colors = textFieldColors
                                    )
                                    TextField(
                                        value = quickDueDay,
                                        onValueChange = { quickDueDay = it; validationError = null; showSaveSuccess = false },
                                        label = { Text("Due Day (1-31)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(16.dp),
                                        colors = textFieldColors
                                    )
                                }

                                TextField(
                                    value = quickLimit,
                                    onValueChange = { quickLimit = it; validationError = null; showSaveSuccess = false },
                                    label = { Text("Credit Limit") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = textFieldColors,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Opening Balance / Outstanding Amount Field
                            val isQuickCreditCard = selectedBaseType == AccountType.CREDIT_CARD
                            val isQuickLiability = selectedTypeEntity?.isLiability ?: selectedBaseType.isLiability
                            TextField(
                                value = quickOpeningBalance,
                                onValueChange = {
                                    quickOpeningBalance = it
                                    validationError = null
                                    showSaveSuccess = false
                                },
                                label = {
                                    Text(
                                        if (isQuickCreditCard) {
                                            "Outstanding Amount (${currency.symbol})"
                                        } else {
                                            "Opening Balance (${currency.symbol})"
                                        }
                                    )
                                },
                                placeholder = {
                                    Text(
                                        if (isQuickCreditCard) {
                                            "Enter outstanding amount (e.g. 0.00)"
                                        } else {
                                            "Enter opening balance (e.g. 0.00)"
                                        },
                                        color = OneUiTheme.colors.textMuted
                                    )
                                },
                                supportingText = when {
                                    isQuickCreditCard -> {
                                        {
                                            Text(
                                                "Enter positive amount currently owed (0 if paid off)",
                                                color = OneUiTheme.colors.textMuted
                                            )
                                        }
                                    }
                                    isQuickLiability -> {
                                        {
                                            Text(
                                                "Enter positive amount owed (0 if none)",
                                                color = OneUiTheme.colors.textMuted
                                            )
                                        }
                                    }
                                    else -> null
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = textFieldColors,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Include in Net Worth Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Include in Net Worth",
                                    style = MaterialTheme.typography.bodyMedium.copy(color = OneUiTheme.colors.textPrimary)
                                )
                                Switch(
                                    checked = quickIncludeInNetWorth,
                                    onCheckedChange = {
                                        quickIncludeInNetWorth = it
                                        showSaveSuccess = false
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = OneUiTheme.colors.primaryAccent,
                                        uncheckedThumbColor = OneUiTheme.colors.textMuted,
                                        uncheckedTrackColor = OneUiTheme.colors.surfaceElevated
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            if (validationError != null) {
                                Text(
                                    text = validationError!!,
                                    color = OneUiTheme.colors.expense,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }

                            // Save Changes Button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OneUiPillButton(
                                    text = if (showSaveSuccess) "Changes Saved!" else "Save Changes",
                                    onClick = {
                                        val cleanName = quickName.trim()
                                        if (cleanName.isEmpty()) {
                                            validationError = "Account name cannot be empty"
                                            return@OneUiPillButton
                                        }

                                        val duplicate = allAccounts.any {
                                            it.account.uid != account.uid &&
                                                !it.account.isArchived &&
                                                it.account.name.trim().equals(cleanName, ignoreCase = true)
                                        }
                                        if (duplicate) {
                                            validationError = "An active account with name '$cleanName' already exists"
                                            return@OneUiPillButton
                                        }

                                        val req = selectedBaseType.institutionRequirement()
                                        if (req != InstitutionRequirement.BANK_OPTIONAL && quickInstitutionUid.isNullOrBlank()) {
                                            val kindLabel = selectedBaseType.requiredInstitutionKind().displayName.lowercase()
                                            validationError = "Please select a $kindLabel (add one in Settings if needed)"
                                            return@OneUiPillButton
                                        }
                                        if (selectedBaseType == AccountType.CREDIT_CARD && quickCardNetwork == null) {
                                            validationError = "Please select a card network"
                                            return@OneUiPillButton
                                        }

                                        val stmtText = quickStatementDay.trim()
                                        val dueText = quickDueDay.trim()
                                        val limitText = quickLimit.trim()

                                        val stmtDay = if (stmtText.isEmpty()) null else stmtText.toIntOrNull()
                                        val dueDay = if (dueText.isEmpty()) null else dueText.toIntOrNull()
                                        val limit = if (limitText.isEmpty()) null else limitText.toDoubleOrNull()

                                        val openingBal = try {
                                            DomainValidator.parseMandatoryOpeningBalance(
                                                quickOpeningBalance,
                                                fieldLabel = if (selectedBaseType == AccountType.CREDIT_CARD) "Outstanding amount" else "Opening balance"
                                            )
                                        } catch (e: IllegalArgumentException) {
                                            validationError = e.message
                                            return@OneUiPillButton
                                        }

                                        if (selectedBaseType == AccountType.CREDIT_CARD) {
                                            if (stmtText.isNotEmpty() && (stmtDay == null || stmtDay !in 1..31)) {
                                                validationError = "Statement day must be between 1 and 31"
                                                return@OneUiPillButton
                                            }
                                            if (dueText.isNotEmpty() && (dueDay == null || dueDay !in 1..31)) {
                                                validationError = "Payment due day must be between 1 and 31"
                                                return@OneUiPillButton
                                            }
                                            if (limitText.isNotEmpty() && (limit == null || !limit.isFinite() || limit < 0.0)) {
                                                validationError = "Credit limit must be a non-negative number"
                                                return@OneUiPillButton
                                            }
                                        }

                                        val updated = account.copy(
                                            name = cleanName,
                                            accountType = selectedBaseType,
                                            accountTypeUid = selectedTypeEntity?.uid ?: quickTypeUid,
                                            openingBalance = openingBal,
                                            statementDay = if (selectedBaseType == AccountType.CREDIT_CARD) stmtDay else null,
                                            paymentDueDay = if (selectedBaseType == AccountType.CREDIT_CARD) dueDay else null,
                                            creditLimit = if (selectedBaseType == AccountType.CREDIT_CARD) limit else null,
                                            includeInNetWorth = quickIncludeInNetWorth,
                                            accountNumber = quickAccountNumber.trim().takeIf { it.isNotEmpty() },
                                            institutionUid = quickInstitutionUid?.takeIf { it.isNotBlank() },
                                            cardNetwork = if (selectedBaseType == AccountType.CREDIT_CARD) quickCardNetwork else null
                                        )

                                        viewModel.saveAccount(updated) { result ->
                                            result.onSuccess {
                                                validationError = null
                                                showSaveSuccess = true
                                            }.onFailure { err ->
                                                showSaveSuccess = false
                                                validationError = err.message ?: "Failed to save account"
                                            }
                                        }
                                    },
                                    containerColor = if (showSaveSuccess) OneUiTheme.colors.income else OneUiTheme.colors.primaryAccent,
                                    contentColor = Color.White,
                                    leadingIcon = if (showSaveSuccess) Icons.Default.Check else null,
                                    modifier = Modifier.defaultMinSize(minWidth = 128.dp, minHeight = 38.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Card 2: Transactions Header
            item(key = "account_txs_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Transactions (${accountTransactions.size})",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = OneUiTheme.colors.textPrimary,
                            fontSize = 16.sp
                        )
                    )
                    Text(
                        text = "All Time",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = OneUiTheme.colors.textSecondary,
                            fontSize = 12.sp
                        )
                    )
                }
            }

            // Card 3: Scrollable Transaction List
            if (accountTransactions.isEmpty()) {
                item(key = "account_no_txs") {
                    OneUiCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                contentDescription = null,
                                tint = OneUiTheme.colors.textMuted,
                                modifier = Modifier.size(42.dp)
                            )
                            Text(
                                text = "No transactions found for this account",
                                style = MaterialTheme.typography.bodyMedium.copy(color = OneUiTheme.colors.textSecondary)
                            )
                        }
                    }
                }
            } else {
                groupedByDay.forEach { (date, dailyItems) ->
                    val rateDiv = if (currency.rateToBase > 0.0) currency.rateToBase else 1.0
                    fun nativeAmountForAccount(item: TransactionWithDetails, useDestination: Boolean = false): Double {
                        val tx = item.transaction
                        return when {
                            useDestination -> tx.toAccountAmount
                                ?: if (tx.currencyUid == currency.uid) tx.accountAmount else tx.baseAmount / rateDiv
                            tx.currencyUid == currency.uid -> tx.accountAmount
                            else -> tx.baseAmount / rateDiv
                        }
                    }

                    val dayIncome = dailyItems.sumOf { item ->
                        val tx = item.transaction
                        when {
                            tx.type == TransactionType.INCOME -> nativeAmountForAccount(item)
                            tx.type == TransactionType.TRANSFER && item.toAccount?.uid == account.uid ->
                                nativeAmountForAccount(item, useDestination = true)
                            tx.type == TransactionType.ADJUSTMENT && tx.accountAmount > 0.0 ->
                                nativeAmountForAccount(item)
                            else -> 0.0
                        }
                    }

                    val dayExpense = dailyItems.sumOf { item ->
                        val tx = item.transaction
                        when {
                            tx.type == TransactionType.EXPENSE -> nativeAmountForAccount(item)
                            tx.type == TransactionType.TRANSFER && item.account.uid == account.uid ->
                                nativeAmountForAccount(item)
                            tx.type == TransactionType.ADJUSTMENT && tx.accountAmount < 0.0 ->
                                abs(nativeAmountForAccount(item))
                            else -> 0.0
                        }
                    }

                    item(key = "day_header_${date}_${account.uid}") {
                        OneUiDayHeader(
                            date = date,
                            dayIncome = dayIncome,
                            dayExpense = dayExpense,
                            currencySymbol = currency.symbol
                        )
                    }

                    item(key = "day_card_${date}_${account.uid}") {
                        OneUiCard {
                            dailyItems.forEachIndexed { index, item ->
                                OneUiTransactionRowItem(
                                    item = item,
                                    categoriesMap = categoriesMap,
                                    colorIndex = index,
                                    showDate = false,
                                    focusAccountUid = account.uid,
                                    focusCurrencySymbol = currency.symbol,
                                    onClick = { onEditTransaction(item) }
                                )

                                if (index < dailyItems.lastIndex) {
                                    OneUiInsetDivider(startIndent = 18.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Revaluation Adjustment Dialog
    if (showAdjustDialog && balanceItem != null) {
        OneUiRevaluationAdjustmentDialog(
            item = balanceItem,
            isLiability = account.resolvedIsLiability(accountTypesMap),
            onDismiss = { showAdjustDialog = false },
            onConfirm = { newBalance ->
                viewModel.adjustBalance(account, currency, nativeBalance, newBalance)
                showAdjustDialog = false
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        OneUiBottomDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = "Delete Account",
            confirmButtonText = "Delete",
            confirmButtonColor = OneUiTheme.colors.expense,
            onConfirm = {
                showDeleteConfirmDialog = false
                viewModel.deleteAccount(account)
                onBack()
            }
        ) {
            Text(
                "Are you sure you want to delete '${account.name}'? If it has linked transactions, it will be safely archived.",
                color = OneUiTheme.colors.textSecondary
            )
        }
    }
}

internal fun formatAccountBalanceText(
    balance: Double,
    symbol: String,
    isLiability: Boolean
): String {
    val epsilon = 0.005
    val formatted = CurrencyFormatter.formatIndianNumber(abs(balance))
    return if (isLiability) {
        when {
            balance < -epsilon -> "+$symbol $formatted Cr"
            balance > epsilon -> "$symbol $formatted"
            else -> "$symbol ${CurrencyFormatter.formatIndianNumber(0.0)}"
        }
    } else {
        when {
            balance < -epsilon -> "-$symbol $formatted"
            balance > epsilon -> "$symbol $formatted"
            else -> "$symbol ${CurrencyFormatter.formatIndianNumber(0.0)}"
        }
    }
}

@Composable
private fun accountBalanceColor(balance: Double, isLiability: Boolean): Color {
    val epsilon = 0.005
    return if (isLiability) {
        when {
            balance > epsilon -> OneUiTheme.colors.expense
            balance < -epsilon -> OneUiTheme.colors.income
            else -> OneUiTheme.colors.textSecondary
        }
    } else {
        if (balance < -epsilon) OneUiTheme.colors.expense else OneUiTheme.colors.textPrimary
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
    typeDisplayName: String = item.account.accountType.displayName,
    institutionName: String? = null,
    isLiability: Boolean = item.account.accountType.isLiability,
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
            val maskedNumber = acc.accountNumber?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
                if (raw.length > 4) "••${raw.takeLast(4)}" else raw
            }
            val subTextParts = buildList {
                if (!institutionName.isNullOrBlank()) add(institutionName)
                acc.cardNetwork?.let { add(it.displayName) }
                if (maskedNumber != null) add(maskedNumber)
                if (isEmpty()) add(typeDisplayName)
                if (curr.isCustom) add("1 ${curr.symbol} = ₹${curr.rateToBase}")
            }
            Text(
                text = subTextParts.joinToString(" • "),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = OneUiTheme.colors.primaryAccent,
                    fontSize = 12.5.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Balance Column
        Column(horizontalAlignment = Alignment.End) {
            val balanceColor = accountBalanceColor(item.nativeBalance, isLiability)

            Text(
                text = formatAccountBalanceText(item.nativeBalance, curr.symbol, isLiability),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Normal,
                    color = balanceColor,
                    fontSize = 14.5.sp
                )
            )

            if (!curr.isMainCurrency) {
                val baseFormatted = CurrencyFormatter.formatIndianNumber(abs(item.baseBalance))
                val baseText = when {
                    isLiability && item.baseBalance < -0.005 -> "≈ +₹$baseFormatted Cr"
                    !isLiability && item.baseBalance < -0.005 -> "≈ -₹$baseFormatted"
                    else -> "≈ ₹$baseFormatted"
                }
                Text(
                    text = baseText,
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
    isLiability: Boolean = item.account.accountType.isLiability,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var newBalanceStr by remember { mutableStateOf(String.format(Locale.US, "%.2f", item.nativeBalance)) }

    OneUiBottomDialog(
        onDismissRequest = onDismiss,
        title = "Adjust Balance",
        confirmButtonText = "Save",
        onConfirm = {
            val target = newBalanceStr.toDoubleOrNull()
            if (target != null && target.isFinite()) {
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
            label = { Text(if (isLiability) "Actual Outstanding (${item.currency.symbol})" else "Actual Balance (${item.currency.symbol})") },
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
        val netWorthDiff = if (isLiability) item.nativeBalance - target else target - item.nativeBalance
        val rawDisplayDiff = target - item.nativeBalance
        val displayDiff = if (abs(rawDisplayDiff) < 0.005) 0.0 else rawDisplayDiff
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = (if (isLiability) "Debt change: " else "Adjustment: ") +
                (if (displayDiff >= 0) "+" else "-") + item.currency.symbol + String.format(Locale.US, "%.2f", abs(displayDiff)),
            style = MaterialTheme.typography.bodySmall.copy(
                color = if (netWorthDiff >= 0) OneUiTheme.colors.income else OneUiTheme.colors.expense,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneUiAccountTypeDropdown(
    accountTypes: List<AccountTypeEntity>,
    selectedTypeUid: String,
    fallbackDisplayName: String,
    onSelect: (AccountTypeEntity) -> Unit,
    textFieldColors: TextFieldColors,
    cornerRadius: Dp = 18.dp,
    modifier: Modifier = Modifier
) {
    var typeExpanded by remember { mutableStateOf(false) }
    val selectedType = accountTypes.firstOrNull { it.uid == selectedTypeUid }
    val displayText = selectedType?.name ?: fallbackDisplayName

    ExposedDropdownMenuBox(
        expanded = typeExpanded,
        onExpandedChange = { typeExpanded = !typeExpanded },
        modifier = modifier
    ) {
        TextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            label = { Text("Account Type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
            shape = RoundedCornerShape(cornerRadius),
            colors = textFieldColors,
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = typeExpanded,
            onDismissRequest = { typeExpanded = false },
            modifier = Modifier.background(OneUiTheme.colors.surfaceElevated)
        ) {
            accountTypes.forEach { type ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OneUiRadioButton(
                                selected = selectedTypeUid == type.uid,
                                onClick = {
                                    onSelect(type)
                                    typeExpanded = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = type.name,
                                color = OneUiTheme.colors.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            if (type.isLiability) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Liability",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = OneUiTheme.colors.expense,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    },
                    onClick = {
                        onSelect(type)
                        typeExpanded = false
                    }
                )
            }
        }
    }
}

private enum class AccountDialogStep {
    SELECT_TYPE,
    FORM
}

internal fun accountNumberFieldLabel(baseType: AccountType): String = when (baseType) {
    AccountType.CREDIT_CARD -> "Card / Account Number (Optional)"
    AccountType.EQUITY, AccountType.MUTUAL_FUNDS -> "Client ID / Folio Number (Optional)"
    else -> "Account Number (Optional)"
}

@Composable
fun AccountTypeGridPicker(
    accountTypes: List<AccountTypeEntity>,
    onSelectType: (AccountTypeEntity) -> Unit,
    onAddAccountType: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gridEntries: List<AccountTypeEntity?> = remember(accountTypes) {
        accountTypes + listOf<AccountTypeEntity?>(null)
    }
    val rows = remember(gridEntries) { gridEntries.chunked(3) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEach { typeEntity ->
                    if (typeEntity != null) {
                        val (badgeIcon, badgeColor) = accountTypeVisuals(typeEntity.baseType)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 96.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(OneUiTheme.colors.surfaceElevated)
                                .clickable { onSelectType(typeEntity) }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            OneUiIconBadge(
                                icon = badgeIcon,
                                backgroundColor = badgeColor,
                                size = 42.dp,
                                iconSize = 21.dp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = typeEntity.name,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = OneUiTheme.colors.textPrimary,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp
                                ),
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 96.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .border(
                                    width = 1.dp,
                                    color = OneUiTheme.colors.primaryAccent.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .background(OneUiTheme.colors.surfaceElevated.copy(alpha = 0.7f))
                                .clickable(onClick = onAddAccountType)
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(OneUiTheme.colors.primaryAccent.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Manage Account Types",
                                    tint = OneUiTheme.colors.primaryAccent,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Add Type",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = OneUiTheme.colors.primaryAccent,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                ),
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                repeat(3 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneUiInstitutionSelector(
    kind: InstitutionKind,
    institutions: List<InstitutionEntity>,
    selectedInstitutionUid: String?,
    isOptional: Boolean,
    onSelect: (String?) -> Unit,
    onManageInSettings: (() -> Unit)? = null,
    textFieldColors: TextFieldColors,
    cornerRadius: Dp = 18.dp,
    modifier: Modifier = Modifier
) {
    val selectedInst = institutions.firstOrNull { it.uid == selectedInstitutionUid }
    val fieldLabel = if (isOptional) "${kind.displayName} (Optional)" else kind.displayName

    if (institutions.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(cornerRadius))
                .background(OneUiTheme.colors.surfaceElevated)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = fieldLabel,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = OneUiTheme.colors.textSecondary,
                    fontSize = 12.sp
                )
            )
            Text(
                text = if (isOptional) {
                    "No ${kind.pluralName.lowercase()} added in Settings (optional for Cash)."
                } else {
                    "No ${kind.pluralName.lowercase()} configured yet. Add one in Settings to continue."
                },
                style = MaterialTheme.typography.bodySmall.copy(
                    color = OneUiTheme.colors.textSecondary,
                    fontSize = 12.5.sp
                )
            )
            if (onManageInSettings != null) {
                OneUiTextActionLink(
                    text = "+ Add ${kind.displayName} in Settings",
                    onClick = onManageInSettings,
                    showChevron = true
                )
            }
        }
        return
    }

    var expanded by remember { mutableStateOf(false) }
    val displayValue = when {
        selectedInst != null -> selectedInst.name
        isOptional -> "None"
        else -> ""
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        TextField(
            value = displayValue,
            onValueChange = {},
            readOnly = true,
            label = { Text(fieldLabel) },
            placeholder = { Text("Select ${kind.displayName.lowercase()}", color = OneUiTheme.colors.textMuted) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(cornerRadius),
            colors = textFieldColors,
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(OneUiTheme.colors.surfaceElevated)
        ) {
            if (isOptional) {
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OneUiRadioButton(
                                selected = selectedInstitutionUid.isNullOrBlank(),
                                onClick = {
                                    onSelect(null)
                                    expanded = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("None", color = OneUiTheme.colors.textSecondary)
                        }
                    },
                    onClick = {
                        onSelect(null)
                        expanded = false
                    }
                )
            }
            institutions.forEach { inst ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OneUiRadioButton(
                                selected = selectedInstitutionUid == inst.uid,
                                onClick = {
                                    onSelect(inst.uid)
                                    expanded = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(inst.name, color = OneUiTheme.colors.textPrimary)
                        }
                    },
                    onClick = {
                        onSelect(inst.uid)
                        expanded = false
                    }
                )
            }
            if (onManageInSettings != null) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "+ Manage ${kind.pluralName} in Settings",
                            color = OneUiTheme.colors.primaryAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    onClick = {
                        expanded = false
                        onManageInSettings()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneUiCardNetworkDropdown(
    selectedNetwork: CardNetwork?,
    onSelect: (CardNetwork) -> Unit,
    textFieldColors: TextFieldColors,
    cornerRadius: Dp = 18.dp,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        TextField(
            value = selectedNetwork?.displayName ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Card Network") },
            placeholder = { Text("Select card network", color = OneUiTheme.colors.textMuted) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(cornerRadius),
            colors = textFieldColors,
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(OneUiTheme.colors.surfaceElevated)
        ) {
            CardNetwork.values().forEach { network ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OneUiRadioButton(
                                selected = selectedNetwork == network,
                                onClick = {
                                    onSelect(network)
                                    expanded = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(network.displayName, color = OneUiTheme.colors.textPrimary)
                        }
                    },
                    onClick = {
                        onSelect(network)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneUiAddEditAccountDialog(
    existingAccount: AccountEntity? = null,
    defaultAccountType: AccountType = AccountType.SAVINGS,
    skipTypeSelection: Boolean = existingAccount != null,
    initialName: String? = null,
    initialAccountNumber: String? = null,
    initialInstitutionUid: String? = null,
    accountTypes: List<AccountTypeEntity> = AccountTypeDefaults.defaultEntities(),
    banks: List<InstitutionEntity> = emptyList(),
    platforms: List<InstitutionEntity> = emptyList(),
    currencies: List<CurrencyEntity>,
    existingAccounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onSave: (AccountEntity) -> Unit,
    onDelete: ((AccountEntity) -> Unit)? = null,
    onSaveAccountType: ((AccountTypeEntity) -> Unit)? = null,
    onSaveInstitution: ((InstitutionEntity) -> Unit)? = null,
    onNavigateToAccountTypes: (() -> Unit)? = null,
    onNavigateToInstitutions: ((InstitutionKind) -> Unit)? = null
) {
    val resolvedInitialTypeUid = remember(existingAccount, defaultAccountType, accountTypes) {
        existingAccount?.resolvedAccountTypeUid?.takeIf { uid ->
            accountTypes.any { it.uid == uid }
        }
            ?: accountTypes.firstOrNull { it.uid == defaultAccountType.name }?.uid
            ?: accountTypes.firstOrNull { it.baseType == defaultAccountType }?.uid
            ?: accountTypes.firstOrNull()?.uid
            ?: defaultAccountType.name
    }
    var currentStep by remember(existingAccount, skipTypeSelection) {
        mutableStateOf(
            if (existingAccount != null || skipTypeSelection) {
                AccountDialogStep.FORM
            } else {
                AccountDialogStep.SELECT_TYPE
            }
        )
    }
    var name by remember { mutableStateOf(existingAccount?.name ?: initialName ?: "") }
    var accountNumberStr by remember { mutableStateOf(existingAccount?.accountNumber ?: initialAccountNumber ?: "") }
    var selectedTypeUid by remember { mutableStateOf(resolvedInitialTypeUid) }
    val selectedTypeEntity = accountTypes.firstOrNull { it.uid == selectedTypeUid }
    val selectedBaseType = selectedTypeEntity?.baseType ?: existingAccount?.accountType ?: defaultAccountType
    var selectedInstitutionUid by remember { mutableStateOf(existingAccount?.institutionUid ?: initialInstitutionUid) }
    var selectedCardNetwork by remember { mutableStateOf(existingAccount?.cardNetwork) }
    var selectedCurrencyUid by remember {
        mutableStateOf(existingAccount?.currencyUid ?: currencies.firstOrNull { it.isMainCurrency }?.uid ?: currencies.firstOrNull()?.uid ?: "")
    }
    var openingBalanceStr by remember {
        mutableStateOf(existingAccount?.let { String.format(Locale.US, "%.2f", it.openingBalance) } ?: "")
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
    var showInlineAccountTypeDialog by remember { mutableStateOf(false) }
    var inlineInstitutionKind by remember { mutableStateOf<InstitutionKind?>(null) }

    val textFieldColors = TextFieldDefaults.colors(
        focusedTextColor = OneUiTheme.colors.textPrimary,
        unfocusedTextColor = OneUiTheme.colors.textPrimary,
        focusedContainerColor = OneUiTheme.colors.surfaceElevated,
        unfocusedContainerColor = OneUiTheme.colors.surfaceElevated,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent
    )

    val dialogTitle = when {
        currentStep == AccountDialogStep.SELECT_TYPE -> "Select Account Type"
        existingAccount != null -> "Edit Account"
        else -> "New ${selectedTypeEntity?.name ?: selectedBaseType.displayName}"
    }

    OneUiBottomDialog(
        onDismissRequest = onDismiss,
        title = dialogTitle,
        confirmButtonText = if (existingAccount != null) "Update" else "Create",
        showConfirmButton = currentStep == AccountDialogStep.FORM,
        headerNavigationIcon = if (currentStep == AccountDialogStep.FORM && existingAccount == null && !skipTypeSelection) {
            {
                IconButton(
                    onClick = {
                        currentStep = AccountDialogStep.SELECT_TYPE
                        validationError = null
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to account types",
                        tint = OneUiTheme.colors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else null,
        onConfirm = {
            if (currentStep != AccountDialogStep.FORM) return@OneUiBottomDialog

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

            val instRequirement = selectedBaseType.institutionRequirement()
            if (instRequirement != InstitutionRequirement.BANK_OPTIONAL && selectedInstitutionUid.isNullOrBlank()) {
                val kindLabel = selectedBaseType.requiredInstitutionKind().displayName.lowercase()
                validationError = "Please select a $kindLabel (add one in Settings if needed)"
                return@OneUiBottomDialog
            }

            if (selectedBaseType == AccountType.CREDIT_CARD && selectedCardNetwork == null) {
                validationError = "Please select a card network"
                return@OneUiBottomDialog
            }

            val stmtText = statementDayStr.trim()
            val dueText = dueDayStr.trim()
            val limitText = creditLimitStr.trim()

            val stmtDay = if (stmtText.isEmpty()) null else stmtText.toIntOrNull()
            val dueDay = if (dueText.isEmpty()) null else dueText.toIntOrNull()
            val limit = if (limitText.isEmpty()) null else limitText.toDoubleOrNull()

            if (selectedBaseType == AccountType.CREDIT_CARD) {
                if (stmtText.isNotEmpty() && (stmtDay == null || stmtDay !in 1..31)) {
                    validationError = "Statement day must be between 1 and 31"
                    return@OneUiBottomDialog
                }
                if (dueText.isNotEmpty() && (dueDay == null || dueDay !in 1..31)) {
                    validationError = "Payment due day must be between 1 and 31"
                    return@OneUiBottomDialog
                }
                if (limitText.isNotEmpty() && (limit == null || !limit.isFinite() || limit < 0.0)) {
                    validationError = "Credit limit must be a non-negative number"
                    return@OneUiBottomDialog
                }
            }

            val openingBal = try {
                DomainValidator.parseMandatoryOpeningBalance(
                    openingBalanceStr,
                    fieldLabel = if (selectedBaseType == AccountType.CREDIT_CARD) "Outstanding amount" else "Opening balance"
                )
            } catch (e: IllegalArgumentException) {
                validationError = e.message
                return@OneUiBottomDialog
            }

            onSave(
                AccountEntity(
                    uid = existingAccount?.uid ?: UUID.randomUUID().toString(),
                    name = cleanName,
                    accountType = selectedBaseType,
                    currencyUid = selectedCurrencyUid,
                    openingBalance = openingBal,
                    statementDay = if (selectedBaseType == AccountType.CREDIT_CARD) stmtDay else null,
                    paymentDueDay = if (selectedBaseType == AccountType.CREDIT_CARD) dueDay else null,
                    creditLimit = if (selectedBaseType == AccountType.CREDIT_CARD) limit else null,
                    settlementAccountUid = if (selectedBaseType == AccountType.CREDIT_CARD) settlementAccountUid else null,
                    includeInNetWorth = existingAccount?.includeInNetWorth ?: true,
                    sortOrder = existingAccount?.sortOrder ?: 0,
                    isArchived = existingAccount?.isArchived ?: false,
                    accountTypeUid = selectedTypeEntity?.uid ?: selectedTypeUid,
                    accountNumber = accountNumberStr.trim().takeIf { it.isNotEmpty() },
                    institutionUid = selectedInstitutionUid?.takeIf { it.isNotBlank() },
                    cardNetwork = if (selectedBaseType == AccountType.CREDIT_CARD) selectedCardNetwork else null
                )
            )
        }
    ) {
        if (currentStep == AccountDialogStep.SELECT_TYPE) {
            AccountTypeGridPicker(
                accountTypes = accountTypes,
                onSelectType = { type ->
                    val prevKind = selectedBaseType.requiredInstitutionKind()
                    val newKind = type.baseType.requiredInstitutionKind()
                    selectedTypeUid = type.uid
                    if (prevKind != newKind) {
                        selectedInstitutionUid = null
                    }
                    if (type.baseType == AccountType.CREDIT_CARD) {
                        if (statementDayStr.isBlank()) statementDayStr = "15"
                        if (dueDayStr.isBlank()) dueDayStr = "5"
                        if (creditLimitStr.isBlank()) creditLimitStr = "100000"
                    } else {
                        selectedCardNetwork = null
                    }
                    validationError = null
                    currentStep = AccountDialogStep.FORM
                },
                onAddAccountType = {
                    if (onSaveAccountType != null) {
                        showInlineAccountTypeDialog = true
                    } else {
                        onDismiss()
                        onNavigateToAccountTypes?.invoke()
                    }
                }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (validationError != null) {
                    Text(
                        text = validationError!!,
                        color = OneUiTheme.colors.expense,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                // Selected Type Pill (or dropdown when editing / skipTypeSelection)
                if (existingAccount == null && !skipTypeSelection && selectedTypeEntity != null) {
                    val (badgeIcon, badgeColor) = accountTypeVisuals(selectedTypeEntity.baseType)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(OneUiTheme.colors.surfaceElevated)
                            .clickable {
                                currentStep = AccountDialogStep.SELECT_TYPE
                                validationError = null
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OneUiIconBadge(
                            icon = badgeIcon,
                            backgroundColor = badgeColor,
                            size = 32.dp,
                            iconSize = 17.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Account Type",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = OneUiTheme.colors.textSecondary,
                                    fontSize = 11.sp
                                )
                            )
                            Text(
                                text = selectedTypeEntity.name,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = OneUiTheme.colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                        Text(
                            text = "Change",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = OneUiTheme.colors.primaryAccent,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                } else {
                    OneUiAccountTypeDropdown(
                        accountTypes = accountTypes,
                        selectedTypeUid = selectedTypeUid,
                        fallbackDisplayName = selectedBaseType.displayName,
                        onSelect = { type ->
                            val prevKind = selectedBaseType.requiredInstitutionKind()
                            val newKind = type.baseType.requiredInstitutionKind()
                            selectedTypeUid = type.uid
                            if (prevKind != newKind) {
                                selectedInstitutionUid = null
                            }
                            if (type.baseType != AccountType.CREDIT_CARD) {
                                selectedCardNetwork = null
                            }
                            validationError = null
                        },
                        textFieldColors = textFieldColors,
                        cornerRadius = 18.dp
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

                TextField(
                    value = accountNumberStr,
                    onValueChange = {
                        accountNumberStr = it
                        validationError = null
                    },
                    label = { Text(accountNumberFieldLabel(selectedBaseType)) },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                // Centralized Bank / Platform Selector
                val requiredKind = selectedBaseType.requiredInstitutionKind()
                val availableInstitutions = if (requiredKind == InstitutionKind.PLATFORM) platforms else banks
                val isInstOptional = selectedBaseType.institutionRequirement() == InstitutionRequirement.BANK_OPTIONAL

                OneUiInstitutionSelector(
                    kind = requiredKind,
                    institutions = availableInstitutions,
                    selectedInstitutionUid = selectedInstitutionUid,
                    isOptional = isInstOptional,
                    onSelect = { uid ->
                        selectedInstitutionUid = uid
                        validationError = null
                    },
                    onManageInSettings = if (onSaveInstitution != null) {
                        { inlineInstitutionKind = requiredKind }
                    } else {
                        onNavigateToInstitutions?.let { navigate ->
                            {
                                onDismiss()
                                navigate(requiredKind)
                            }
                        }
                    },
                    textFieldColors = textFieldColors,
                    cornerRadius = 18.dp
                )

                // Card Network for Credit Cards
                if (selectedBaseType == AccountType.CREDIT_CARD) {
                    OneUiCardNetworkDropdown(
                        selectedNetwork = selectedCardNetwork,
                        onSelect = { net ->
                            selectedCardNetwork = net
                            validationError = null
                        },
                        textFieldColors = textFieldColors,
                        cornerRadius = 18.dp
                    )
                }

                // Currency Selector (locked after creation so historical ledger stays consistent)
                val isCurrencyLocked = existingAccount != null
                var currExpanded by remember { mutableStateOf(false) }
                val currentCurr = currencies.firstOrNull { it.uid == selectedCurrencyUid }
                val formatCurrLabel: (CurrencyEntity) -> String = { curr ->
                    if (curr.name.contains("(${curr.symbol})")) curr.name else "${curr.name} (${curr.symbol})"
                }
                ExposedDropdownMenuBox(
                    expanded = currExpanded && !isCurrencyLocked,
                    onExpandedChange = { if (!isCurrencyLocked) currExpanded = !currExpanded }
                ) {
                    TextField(
                        value = currentCurr?.let(formatCurrLabel) ?: "",
                        onValueChange = {},
                        readOnly = true,
                        enabled = !isCurrencyLocked,
                        label = { Text(if (isCurrencyLocked) "Currency (locked)" else "Currency") },
                        trailingIcon = {
                            if (!isCurrencyLocked) {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = currExpanded)
                            }
                        },
                        shape = RoundedCornerShape(18.dp),
                        colors = textFieldColors,
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = currExpanded && !isCurrencyLocked,
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
                                        Text(formatCurrLabel(curr), color = OneUiTheme.colors.textPrimary)
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

                if (selectedBaseType == AccountType.CREDIT_CARD) {
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

                val isDialogCreditCard = selectedBaseType == AccountType.CREDIT_CARD
                val isDialogLiability = selectedTypeEntity?.isLiability ?: selectedBaseType.isLiability
                TextField(
                    value = openingBalanceStr,
                    onValueChange = {
                        openingBalanceStr = it
                        validationError = null
                    },
                    label = { Text(if (isDialogCreditCard) "Outstanding Amount" else "Opening Balance") },
                    placeholder = {
                        Text(
                            if (isDialogCreditCard) {
                                "Enter outstanding amount (e.g. 0.00)"
                            } else {
                                "Enter opening balance (e.g. 0.00)"
                            },
                            color = OneUiTheme.colors.textMuted
                        )
                    },
                    supportingText = when {
                        isDialogCreditCard -> {
                            {
                                Text(
                                    "Enter positive amount currently owed (0 if paid off)",
                                    color = OneUiTheme.colors.textMuted
                                )
                            }
                        }
                        isDialogLiability -> {
                            {
                                Text(
                                    "Enter positive amount owed (0 if none)",
                                    color = OneUiTheme.colors.textMuted
                                )
                            }
                        }
                        else -> null
                    },
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

    if (showInlineAccountTypeDialog && onSaveAccountType != null) {
        OneUiAddEditAccountTypeDialog(
            existingAccountTypes = accountTypes,
            onDismiss = { showInlineAccountTypeDialog = false },
            onSave = { newType ->
                onSaveAccountType(newType)
                selectedTypeUid = newType.uid
                if (newType.baseType != AccountType.CREDIT_CARD) {
                    selectedCardNetwork = null
                }
                validationError = null
                showInlineAccountTypeDialog = false
                currentStep = AccountDialogStep.FORM
            }
        )
    }

    inlineInstitutionKind?.let { kind ->
        if (onSaveInstitution != null) {
            val listForKind = if (kind == InstitutionKind.PLATFORM) platforms else banks
            OneUiAddEditInstitutionDialog(
                kind = kind,
                existingInstitutions = listForKind,
                onDismiss = { inlineInstitutionKind = null },
                onSave = { newInst ->
                    onSaveInstitution(newInst)
                    selectedInstitutionUid = newInst.uid
                    validationError = null
                    inlineInstitutionKind = null
                }
            )
        }
    }
}
