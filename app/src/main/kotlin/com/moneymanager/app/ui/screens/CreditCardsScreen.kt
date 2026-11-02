package com.moneymanager.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.AccountType
import com.moneymanager.app.data.local.entity.CardStatementSummaryRecord
import com.moneymanager.app.data.local.entity.CardStatementTransactionEntity
import com.moneymanager.app.data.local.entity.TransactionWithDetails
import com.moneymanager.app.domain.CreditCardCycleSummary
import com.moneymanager.app.domain.CurrencyFormatter
import com.moneymanager.app.domain.statement.reconciliation.StatementReconciliationReport
import com.moneymanager.app.ui.components.*
import com.moneymanager.app.ui.main.MainViewModel
import com.moneymanager.app.ui.screens.cards.CreditCardBrandEmblem
import com.moneymanager.app.ui.screens.cards.RewardsSummaryDialog
import com.moneymanager.app.ui.screens.cards.StatementDetailBottomSheet
import com.moneymanager.app.ui.theme.OneUiTheme
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

enum class CredCardsTab(val title: String) {
    TOTAL_DUE("Total due"),
    RECENT_SPENDS("Recent spends")
}

@Composable
fun CreditCardsScreen(
    viewModel: MainViewModel,
    onEditTransaction: ((TransactionWithDetails) -> Unit)? = null,
    onOpenGmailImporter: (() -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(CredCardsTab.TOTAL_DUE) }

    val statementSummaries by viewModel.statementSummaries.collectAsState()
    val unbilledSpends by viewModel.unbilledCardSpends.collectAsState()
    val manualCardSummaries by viewModel.creditCards.collectAsState()
    val allAccounts by viewModel.accounts.collectAsState()
    val allCurrencies by viewModel.allCurrencies.collectAsState()

    // Dialog & Sheet States
    var showRewardsDialog by remember { mutableStateOf(false) }
    var selectedStatementForDetail by remember { mutableStateOf<CardStatementSummaryRecord?>(null) }
    var statementTransactionsForDetail by remember { mutableStateOf<List<CardStatementTransactionEntity>>(emptyList()) }
    var statementReconReportForDetail by remember { mutableStateOf<StatementReconciliationReport?>(null) }

    var cardToPayStatement by remember { mutableStateOf<CardStatementSummaryRecord?>(null) }
    var cardToPayManual by remember { mutableStateOf<CreditCardCycleSummary?>(null) }
    var cardToEdit by remember { mutableStateOf<AccountEntity?>(null) }
    var showAddCardDialog by remember { mutableStateOf(false) }

    // Selected filter chip for Recent Spends
    var selectedFilterCardUid by remember { mutableStateOf<String?>(null) }

    // Date Formatters
    val shortDateFormatter = DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault())
    val fullDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())

    // Cache reconciliation reports for statements to show red badges on cards
    var reconReportsMap by remember { mutableStateOf<Map<String, StatementReconciliationReport>>(emptyMap()) }

    LaunchedEffect(statementSummaries, unbilledSpends) {
        val newMap = mutableMapOf<String, StatementReconciliationReport>()
        for (stmt in statementSummaries) {
            try {
                val report = viewModel.getReconciliationReport(stmt)
                newMap[stmt.statementId] = report
            } catch (_: Exception) {}
        }
        reconReportsMap = newMap
    }

    // Load transactions and report when a statement is selected
    LaunchedEffect(selectedStatementForDetail) {
        val stmt = selectedStatementForDetail
        if (stmt != null) {
            coroutineScope.launch {
                viewModel.getStatementTransactions(stmt.statementId).collect { txns ->
                    statementTransactionsForDetail = txns
                }
            }
            try {
                statementReconReportForDetail = viewModel.getReconciliationReport(stmt)
            } catch (_: Exception) {
                statementReconReportForDetail = null
            }
        } else {
            statementTransactionsForDetail = emptyList()
            statementReconReportForDetail = null
        }
    }

    // Consolidated Total Due Calculation
    val unpaidStatements = statementSummaries.filter { !it.isReconciled && it.statementTotalDue > 0.01 }
    val totalStatementDue = unpaidStatements.sumOf { it.statementTotalDue }

    // Combine with manual cards that don't have imported statements
    val manualCardsWithoutStatements = manualCardSummaries.filter { manualCard ->
        statementSummaries.none { stmt ->
            stmt.accountOrCardNumberMasked.takeLast(4) == manualCard.cardName.takeLast(4) ||
                stmt.bankName.contains(manualCard.cardName, ignoreCase = true)
        }
    }
    val totalManualDue = manualCardsWithoutStatements.sumOf { it.remainingStatementDue }
    val consolidatedTotalDue = totalStatementDue + totalManualDue

    val totalActiveCardsCount = (unpaidStatements.size + manualCardsWithoutStatements.count { it.remainingStatementDue > 0.01 })
        .coerceAtLeast(statementSummaries.size)

    // Unbilled Spends calculations for Recent Spends tab
    val creditCardAccounts = remember(allAccounts) {
        allAccounts.filter { it.account.accountType == AccountType.CREDIT_CARD }
    }

    val filteredSpends = remember(unbilledSpends, selectedFilterCardUid) {
        if (selectedFilterCardUid == null) {
            unbilledSpends
        } else {
            unbilledSpends.filter { it.account.uid == selectedFilterCardUid }
        }
    }

    val totalUnbilledAmount = remember(filteredSpends) {
        filteredSpends.sumOf { it.transaction.accountAmount }
    }

    val groupedSpends = remember(filteredSpends) {
        filteredSpends.groupBy { tx ->
            Instant.ofEpochMilli(tx.transaction.timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        }
    }

    // Earliest upcoming due date
    val earliestDueDateText = remember(unpaidStatements, manualCardsWithoutStatements) {
        val statementDates = unpaidStatements.mapNotNull { it.paymentDueDateEpochDay?.let { day -> LocalDate.ofEpochDay(day) } }
        val manualDates = manualCardsWithoutStatements.filter { it.remainingStatementDue > 0.01 }.map { it.paymentDueDate }
        val allDates = (statementDates + manualDates).sorted()
        if (allDates.isNotEmpty()) {
            val earliest = allDates.first()
            "Pay by ${earliest.format(shortDateFormatter)}"
        } else {
            "All bills clear"
        }
    }

    val subtitleText = if (consolidatedTotalDue > 0.01) {
        "Total due: ₹${CurrencyFormatter.formatIndianNumber(consolidatedTotalDue)} • $earliestDueDateText"
    } else {
        "All statements clear • $earliestDueDateText"
    }

    OneUiCollapsibleScaffold(
        title = "Cards",
        subtitle = subtitleText,
        expandedHeight = 165.dp,
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // =========================================================================
            // 1. HERO TOTAL DUE CARD (Matches One UI 9 Squircle Island style)
            // =========================================================================
            item(key = "hero_total_due_card") {
                OneUiCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text(
                                text = "Total due",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = OneUiTheme.colors.textSecondary,
                                    fontSize = 13.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "₹${CurrencyFormatter.formatIndianNumber(consolidatedTotalDue)}",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = OneUiTheme.colors.textPrimary,
                                    fontSize = 32.sp
                                )
                            )
                        }

                        if (consolidatedTotalDue > 0.01) {
                            OneUiPillButton(
                                text = "Pay all",
                                onClick = {
                                    val firstUnpaid = unpaidStatements.firstOrNull()
                                    if (firstUnpaid != null) {
                                        cardToPayStatement = firstUnpaid
                                    } else {
                                        cardToPayManual = manualCardsWithoutStatements.firstOrNull()
                                    }
                                },
                                containerColor = OneUiTheme.colors.primaryAccent,
                                contentColor = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (consolidatedTotalDue > 0.01) {
                            "Due across $totalActiveCardsCount cards • $earliestDueDateText"
                        } else {
                            "All statements clear • $earliestDueDateText"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = OneUiTheme.colors.textSecondary,
                            fontSize = 12.5.sp
                        )
                    )
                }
            }

            // =========================================================================
            // 2. SUB-TAB SEGMENTED CONTROL (Total due | Recent spends)
            // =========================================================================
            item(key = "cards_sub_tabs_pill") {
                OneUiPillSegmentedControl(
                    items = CredCardsTab.values().toList(),
                    selectedItem = selectedTab,
                    onItemSelected = { selectedTab = it },
                    labelProvider = { it.title }
                )
            }

            // =========================================================================
            // 3. TAB CONTENT
            // =========================================================================
            when (selectedTab) {
                CredCardsTab.TOTAL_DUE -> {
                    // Empty State if no statements and no manual cards
                    if (statementSummaries.isEmpty() && manualCardsWithoutStatements.isEmpty()) {
                        item(key = "empty_cards_state") {
                            OneUiCard {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CreditCard,
                                        contentDescription = null,
                                        tint = OneUiTheme.colors.primaryAccent,
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Text(
                                        text = "No credit cards or statements yet",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = OneUiTheme.colors.textPrimary
                                        )
                                    )
                                    Text(
                                        text = "Connect Gmail to auto-import your bank statement PDFs on-device, or add a credit card manually.",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = OneUiTheme.colors.textSecondary,
                                            textAlign = TextAlign.Center
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        if (onOpenGmailImporter != null) {
                                            OneUiPillButton(
                                                text = "Gmail Importer",
                                                onClick = { onOpenGmailImporter() },
                                                containerColor = OneUiTheme.colors.primaryAccent,
                                                contentColor = Color.White
                                            )
                                        }
                                        OneUiPillButton(
                                            text = "Add Card",
                                            onClick = { showAddCardDialog = true },
                                            containerColor = OneUiTheme.colors.surfaceElevated,
                                            contentColor = OneUiTheme.colors.textPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // List of Imported Statements
                    items(statementSummaries, key = { it.statementId }) { stmt ->
                        val reconReport = reconReportsMap[stmt.statementId]
                        val hasDiscrepancy = reconReport?.hasDiscrepancy == true

                        val isPaid = stmt.isReconciled || stmt.statementTotalDue <= 0.01
                        val statusLabel = when {
                            isPaid -> "✔ No due"
                            stmt.paymentDueDateEpochDay != null -> {
                                val dueLocalDate = LocalDate.ofEpochDay(stmt.paymentDueDateEpochDay)
                                "Due by ${dueLocalDate.format(shortDateFormatter)}"
                            }
                            else -> "Due"
                        }

                        val statusBgColor = when {
                            isPaid -> OneUiTheme.colors.incomeContainer
                            else -> OneUiTheme.colors.surfaceElevated
                        }
                        val statusTextColor = when {
                            isPaid -> OneUiTheme.colors.income
                            else -> OneUiTheme.colors.textSecondary
                        }

                        OneUiCard(
                            onClick = { selectedStatementForDetail = stmt },
                            modifier = if (hasDiscrepancy) {
                                Modifier.border(1.dp, OneUiTheme.colors.expense, OneUiCardShape)
                            } else {
                                Modifier
                            }
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Bank Brand Emblem
                                    CreditCardBrandEmblem(
                                        bankName = stmt.bankName,
                                        size = 40.dp
                                    )

                                    Spacer(modifier = Modifier.width(14.dp))

                                    // Bank Name & Masked Digits
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stmt.bankName,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                color = OneUiTheme.colors.textPrimary,
                                                fontSize = 16.sp
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "•• ${stmt.accountOrCardNumberMasked.takeLast(4)}",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = OneUiTheme.colors.textSecondary,
                                                fontSize = 13.sp
                                            )
                                        )
                                    }

                                    // Due Amount & Chevron
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Text(
                                            text = "₹${CurrencyFormatter.formatIndianNumber(if (isPaid) 0.0 else stmt.statementTotalDue)}",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                color = OneUiTheme.colors.textPrimary,
                                                fontSize = 17.sp
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = null,
                                            tint = OneUiTheme.colors.textMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                // Status Pill Row (with RED Discrepancy Alert when discrepancy detected)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (hasDiscrepancy) {
                                        Surface(
                                            color = OneUiTheme.colors.expenseContainer,
                                            shape = CircleShape,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, OneUiTheme.colors.expense)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Warning,
                                                    contentDescription = null,
                                                    tint = OneUiTheme.colors.expense,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                val unrecordedCount = reconReport.unrecordedStatementTransactions.size
                                                val variance = reconReport.varianceAmount
                                                Text(
                                                    text = if (unrecordedCount > 0) {
                                                        "⚠ $unrecordedCount unrecorded (₹${CurrencyFormatter.formatIndianNumber(abs(variance))})"
                                                    } else {
                                                        "⚠ Discrepancy"
                                                    },
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = OneUiTheme.colors.expense,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp
                                                    )
                                                )
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = "Cycle ${stmt.statementYearMonth}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = OneUiTheme.colors.textMuted,
                                                fontSize = 11.5.sp
                                            )
                                        )
                                    }

                                    // Right: Status Capsule
                                    Surface(
                                        color = statusBgColor,
                                        shape = CircleShape
                                    ) {
                                        Text(
                                            text = statusLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = statusTextColor,
                                                fontSize = 11.sp
                                            ),
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Manual Cards Section
                    if (manualCardsWithoutStatements.isNotEmpty()) {
                        item(key = "manual_cards_island") {
                            OneUiCard {
                                Text(
                                    text = "Manually Tracked Cards",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = OneUiTheme.colors.textSecondary,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                manualCardsWithoutStatements.forEachIndexed { index, manualCard ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable {
                                                cardToEdit = allAccounts.firstOrNull { it.account.uid == manualCard.accountUid }?.account
                                            }
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CreditCardBrandEmblem(
                                            bankName = manualCard.cardName,
                                            size = 40.dp
                                        )
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = manualCard.cardName,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontWeight = FontWeight.Normal,
                                                    color = OneUiTheme.colors.textPrimary,
                                                    fontSize = 15.sp
                                                )
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Manual Ledger Tracker",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = OneUiTheme.colors.textSecondary,
                                                    fontSize = 12.5.sp
                                                )
                                            )
                                        }
                                        Text(
                                            text = "₹${CurrencyFormatter.formatIndianNumber(manualCard.remainingStatementDue)}",
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontWeight = FontWeight.Normal,
                                                color = if (manualCard.remainingStatementDue > 0) OneUiTheme.colors.expense else OneUiTheme.colors.textSecondary,
                                                fontSize = 14.5.sp
                                            )
                                        )
                                    }

                                    if (index < manualCardsWithoutStatements.lastIndex) {
                                        OneUiInsetDivider(startIndent = 54.dp)
                                    }
                                }
                            }
                        }
                    }
                }

                CredCardsTab.RECENT_SPENDS -> {
                    // Filter Chips Row
                    item(key = "spends_filter_chips") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item(key = "filter_all") {
                                val isAll = selectedFilterCardUid == null
                                OneUiCapsuleChip(
                                    label = "All Cards (${unbilledSpends.size})",
                                    selected = isAll,
                                    onClick = { selectedFilterCardUid = null }
                                )
                            }

                            items(creditCardAccounts, key = { it.account.uid }) { acc ->
                                val isSelected = selectedFilterCardUid == acc.account.uid
                                OneUiCapsuleChip(
                                    label = acc.account.name,
                                    selected = isSelected,
                                    onClick = { selectedFilterCardUid = acc.account.uid }
                                )
                            }
                        }
                    }

                    // Summary Banner
                    item(key = "spends_summary_banner") {
                        OneUiCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Live Unbilled Spends",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = OneUiTheme.colors.textSecondary,
                                            fontSize = 12.5.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "₹${CurrencyFormatter.formatIndianNumber(totalUnbilledAmount)}",
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = OneUiTheme.colors.textPrimary
                                        )
                                    )
                                }
                                Text(
                                    text = "${filteredSpends.size} unbilled txs",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = OneUiTheme.colors.primaryAccent,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }

                    if (groupedSpends.isEmpty()) {
                        item(key = "empty_unbilled_spends") {
                            OneUiCard {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                        contentDescription = null,
                                        tint = OneUiTheme.colors.textMuted,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Text(
                                        text = "No unbilled card spends recorded",
                                        style = MaterialTheme.typography.bodyMedium.copy(color = OneUiTheme.colors.textSecondary)
                                    )
                                }
                            }
                        }
                    } else {
                        groupedSpends.forEach { (date, txList) ->
                            item(key = "day_group_$date") {
                                OneUiCard {
                                    val dateLabel = when (date) {
                                        LocalDate.now() -> "Today"
                                        LocalDate.now().minusDays(1) -> "Yesterday"
                                        else -> date.format(fullDateFormatter)
                                    }
                                    Text(
                                        text = dateLabel,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = OneUiTheme.colors.textSecondary,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 13.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OneUiInsetDivider(startIndent = 0.dp)

                                    txList.forEachIndexed { index, item ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .clickable { onEditTransaction?.invoke(item) }
                                                .padding(vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .background(OneUiTheme.colors.surfaceElevated),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = item.category?.icon ?: "💳",
                                                    fontSize = 18.sp
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(14.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = if (item.transaction.note.isNotBlank()) item.transaction.note else item.category?.name ?: "Card Spend",
                                                    style = MaterialTheme.typography.bodyLarge.copy(
                                                        fontWeight = FontWeight.Normal,
                                                        color = OneUiTheme.colors.textPrimary,
                                                        fontSize = 15.sp
                                                    ),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "${item.account.name} • ${item.category?.name ?: "Expense"}",
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        color = OneUiTheme.colors.textSecondary,
                                                        fontSize = 12.5.sp
                                                    ),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Text(
                                                text = "-₹${CurrencyFormatter.formatIndianNumber(item.transaction.accountAmount)}",
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontWeight = FontWeight.Normal,
                                                    color = OneUiTheme.colors.expense,
                                                    fontSize = 14.5.sp
                                                )
                                            )
                                        }

                                        if (index < txList.lastIndex) {
                                            OneUiInsetDivider(startIndent = 52.dp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // 4. DIALOGS & DETAIL BOTTOM SHEETS
    // =========================================================================

    // Statement Detail Bottom Sheet (shows PDF statement line items & RED discrepancies)
    selectedStatementForDetail?.let { stmt ->
        StatementDetailBottomSheet(
            summary = stmt,
            transactions = statementTransactionsForDetail,
            reconciliationReport = statementReconReportForDetail,
            onDismiss = { selectedStatementForDetail = null },
            onPayBill = {
                cardToPayStatement = stmt
                selectedStatementForDetail = null
            },
            onTogglePaid = { isPaid ->
                viewModel.markStatementPaid(stmt.statementId, isPaid)
                selectedStatementForDetail = null
            },
            onAddTxToLedger = { stmtTx ->
                val matchingAccount = allAccounts.firstOrNull {
                    it.account.accountType == AccountType.CREDIT_CARD &&
                        (it.account.name.contains(stmt.accountOrCardNumberMasked.takeLast(4)) ||
                            it.account.name.contains(stmt.bankName, ignoreCase = true))
                }?.account ?: allAccounts.firstOrNull { it.account.accountType == AccountType.CREDIT_CARD }?.account

                if (matchingAccount != null) {
                    viewModel.addStatementTransactionToManualLedger(stmtTx, matchingAccount.uid)
                } else {
                    showAddCardDialog = true
                }
            }
        )
    }

    // Rewards & Cashback Dialog
    if (showRewardsDialog) {
        RewardsSummaryDialog(
            statements = statementSummaries,
            onDismiss = { showRewardsDialog = false }
        )
    }

    // Bill Payment Dialog for Statement
    cardToPayStatement?.let { stmt ->
        val bankAccounts = allAccounts.filter {
            it.account.accountType in listOf(AccountType.SAVINGS, AccountType.CASH, AccountType.UPI_DEBIT)
        }
        var selectedFromUid by remember {
            mutableStateOf(bankAccounts.firstOrNull()?.account?.uid ?: "")
        }
        var paymentAmountStr by remember {
            mutableStateOf(String.format(Locale.getDefault(), "%.2f", stmt.statementTotalDue))
        }

        OneUiBottomDialog(
            onDismissRequest = { cardToPayStatement = null },
            title = "Pay ${stmt.bankName} Bill",
            confirmButtonText = "Confirm Payment",
            onConfirm = {
                val amt = paymentAmountStr.toDoubleOrNull()
                if (amt != null && amt > 0 && selectedFromUid.isNotBlank()) {
                    viewModel.payStatementBill(stmt, selectedFromUid, amt)
                    cardToPayStatement = null
                }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Deduct from Account",
                    style = MaterialTheme.typography.bodySmall.copy(color = OneUiTheme.colors.textSecondary)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(OneUiTheme.colors.surfaceElevated)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    bankAccounts.forEachIndexed { idx, acc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedFromUid = acc.account.uid }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OneUiRadioButton(
                                selected = selectedFromUid == acc.account.uid,
                                onClick = { selectedFromUid = acc.account.uid }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = acc.account.name,
                                style = MaterialTheme.typography.bodyMedium.copy(color = OneUiTheme.colors.textPrimary)
                            )
                        }
                        if (idx < bankAccounts.lastIndex) {
                            OneUiInsetDivider(startIndent = 34.dp)
                        }
                    }
                }

                TextField(
                    value = paymentAmountStr,
                    onValueChange = { paymentAmountStr = it },
                    label = { Text("Payment Amount (₹)") },
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

    // Bill Payment Dialog for Manual Card
    cardToPayManual?.let { manualCycle ->
        val bankAccounts = allAccounts.filter {
            it.account.accountType in listOf(AccountType.SAVINGS, AccountType.CASH, AccountType.UPI_DEBIT)
        }
        OneUiPayBillDialog(
            summary = manualCycle,
            availableBankAccounts = bankAccounts.map { it.account },
            onDismiss = { cardToPayManual = null },
            onConfirm = { fromAccountUid, amount ->
                viewModel.payCreditCard(manualCycle, fromAccountUid, amount)
                cardToPayManual = null
            }
        )
    }

    // Add / Edit Card Dialogs
    if (showAddCardDialog) {
        OneUiAddEditAccountDialog(
            existingAccount = null,
            defaultAccountType = AccountType.CREDIT_CARD,
            currencies = allCurrencies,
            existingAccounts = allAccounts.map { it.account },
            onDismiss = { showAddCardDialog = false },
            onSave = { newCard ->
                viewModel.saveAccount(newCard.copy(accountType = AccountType.CREDIT_CARD))
                showAddCardDialog = false
            }
        )
    }

    cardToEdit?.let { card ->
        OneUiAddEditAccountDialog(
            existingAccount = card,
            currencies = allCurrencies,
            existingAccounts = allAccounts.map { it.account },
            onDismiss = { cardToEdit = null },
            onSave = { updatedCard ->
                viewModel.saveAccount(updatedCard)
                cardToEdit = null
            },
            onDelete = { cardToDelete ->
                viewModel.deleteAccount(cardToDelete)
                cardToEdit = null
            }
        )
    }
}

@Composable
fun OneUiPayBillDialog(
    summary: CreditCardCycleSummary,
    availableBankAccounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onConfirm: (fromAccountUid: String, amount: Double) -> Unit
) {
    var selectedFromAccountUid by remember {
        mutableStateOf(summary.settlementAccountUid ?: availableBankAccounts.firstOrNull()?.uid ?: "")
    }

    val maxPayable = maxOf(summary.remainingStatementDue, summary.totalOutstanding, 1000.0)
    var sliderValue by remember {
        mutableFloatStateOf(summary.remainingStatementDue.coerceAtLeast(0.0).toFloat())
    }
    var paymentAmountStr by remember {
        mutableStateOf(String.format(Locale.getDefault(), "%.2f", summary.remainingStatementDue.coerceAtLeast(0.0)))
    }

    OneUiBottomDialog(
        onDismissRequest = onDismiss,
        title = "Pay ${summary.cardName}",
        confirmButtonText = "Pay Bill",
        onConfirm = {
            val amt = paymentAmountStr.toDoubleOrNull()
            if (amt != null && amt > 0 && selectedFromAccountUid.isNotBlank()) {
                onConfirm(selectedFromAccountUid, amt)
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Pay From Account",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = OneUiTheme.colors.textSecondary,
                    fontWeight = FontWeight.Medium
                )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(OneUiTheme.colors.surfaceElevated)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                availableBankAccounts.forEachIndexed { idx, acc ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedFromAccountUid = acc.uid }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OneUiRadioButton(
                            selected = selectedFromAccountUid == acc.uid,
                            onClick = { selectedFromAccountUid = acc.uid }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = acc.name,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = OneUiTheme.colors.textPrimary,
                                fontWeight = FontWeight.Normal,
                                fontSize = 15.sp
                            )
                        )
                    }
                    if (idx < availableBankAccounts.lastIndex) {
                        OneUiInsetDivider(startIndent = 34.dp)
                    }
                }
            }

            TextField(
                value = paymentAmountStr,
                onValueChange = {
                    paymentAmountStr = it
                    it.toFloatOrNull()?.let { f ->
                        sliderValue = f.coerceIn(0f, maxPayable.toFloat())
                    }
                },
                label = { Text("Payment Amount (₹)") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                ),
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

            OneUiSlider(
                value = sliderValue,
                onValueChange = { f ->
                    sliderValue = f
                    paymentAmountStr = String.format(Locale.getDefault(), "%.2f", f)
                },
                valueRange = 0f..maxPayable.toFloat(),
                activeColor = OneUiTheme.colors.primaryAccent
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OneUiCapsuleChip(
                    label = "Statement Due",
                    selected = abs(sliderValue - summary.remainingStatementDue.toFloat()) < 1f,
                    onClick = {
                        val due = summary.remainingStatementDue.toFloat()
                        sliderValue = due
                        paymentAmountStr = String.format(Locale.getDefault(), "%.2f", due)
                    },
                    modifier = Modifier.weight(1f)
                )

                OneUiCapsuleChip(
                    label = "Total Outstanding",
                    selected = abs(sliderValue - summary.totalOutstanding.toFloat()) < 1f,
                    onClick = {
                        val total = summary.totalOutstanding.toFloat()
                        sliderValue = total
                        paymentAmountStr = String.format(Locale.getDefault(), "%.2f", total)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
