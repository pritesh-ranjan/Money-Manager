package com.moneymanager.app.ui.screens.cards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymanager.app.data.local.entity.CardStatementSummaryRecord
import com.moneymanager.app.data.local.entity.CardStatementTransactionEntity
import com.moneymanager.app.domain.CurrencyFormatter
import com.moneymanager.app.domain.statement.reconciliation.StatementReconciliationReport
import com.moneymanager.app.ui.components.OneUiBottomDialog
import com.moneymanager.app.ui.components.OneUiInsetDivider
import com.moneymanager.app.ui.components.OneUiPillButton
import com.moneymanager.app.ui.theme.OneUiTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

enum class StmtTxFilter(val label: String) {
    ALL("All"),
    UNRECORDED("Discrepancies"),
    DEBITS("Debits"),
    CREDITS("Credits")
}

@Composable
fun StatementDetailBottomSheet(
    summary: CardStatementSummaryRecord,
    transactions: List<CardStatementTransactionEntity>,
    reconciliationReport: StatementReconciliationReport?,
    onDismiss: () -> Unit,
    onPayBill: () -> Unit,
    onTogglePaid: (Boolean) -> Unit,
    onAddTxToLedger: (CardStatementTransactionEntity) -> Unit
) {
    val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())
    val shortDateFormatter = DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault())

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(StmtTxFilter.ALL) }

    val unrecordedUids = remember(reconciliationReport) {
        reconciliationReport?.unrecordedStatementTransactions?.map { it.uid }?.toSet() ?: emptySet()
    }

    val filteredTransactions = remember(transactions, searchQuery, selectedFilter, unrecordedUids) {
        transactions.filter { tx ->
            val matchesSearch = searchQuery.isBlank() ||
                tx.cleanNarration.contains(searchQuery, ignoreCase = true) ||
                tx.rawNarration.contains(searchQuery, ignoreCase = true) ||
                (tx.referenceNumber?.contains(searchQuery, ignoreCase = true) == true)

            val matchesFilter = when (selectedFilter) {
                StmtTxFilter.ALL -> true
                StmtTxFilter.UNRECORDED -> unrecordedUids.contains(tx.uid)
                StmtTxFilter.DEBITS -> !tx.isCredit
                StmtTxFilter.CREDITS -> tx.isCredit
            }
            matchesSearch && matchesFilter
        }
    }

    OneUiBottomDialog(
        onDismissRequest = onDismiss,
        title = "${summary.bankName} Statement",
        confirmButtonText = if (summary.isReconciled) "Mark Unpaid" else "Pay Bill",
        dismissButtonText = "Close",
        onConfirm = {
            if (summary.isReconciled) {
                onTogglePaid(false)
            } else {
                onPayBill()
            }
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Header Card with Brand Emblem & Cycle Dates
            item(key = "header_card") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(OneUiTheme.colors.surfaceElevated)
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CreditCardBrandEmblem(
                                bankName = summary.bankName,
                                size = 44.dp
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = summary.bankName,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = OneUiTheme.colors.textPrimary,
                                        fontSize = 18.sp
                                    )
                                )
                                val periodStr = if (summary.billingPeriodStartEpochDay != null && summary.billingPeriodEndEpochDay != null) {
                                    val s = LocalDate.ofEpochDay(summary.billingPeriodStartEpochDay)
                                    val e = LocalDate.ofEpochDay(summary.billingPeriodEndEpochDay)
                                    "${s.format(shortDateFormatter)} – ${e.format(shortDateFormatter)}"
                                } else {
                                    "Cycle ${summary.statementYearMonth}"
                                }
                                val stmtDateStr = LocalDate.ofEpochDay(summary.statementDateEpochDay).format(shortDateFormatter)

                                Text(
                                    text = "•• ${summary.accountOrCardNumberMasked.takeLast(4)} • Period: $periodStr",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = OneUiTheme.colors.textSecondary,
                                        fontSize = 12.sp
                                    )
                                )
                                Text(
                                    text = "Statement Date: $stmtDateStr",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = OneUiTheme.colors.textMuted,
                                        fontSize = 11.sp
                                    )
                                )
                            }

                            // Paid / Unpaid Status Badge
                            Surface(
                                color = if (summary.isReconciled) OneUiTheme.colors.incomeContainer else OneUiTheme.colors.primaryAccentContainer,
                                shape = CircleShape
                            ) {
                                Text(
                                    text = if (summary.isReconciled) "✔ Paid" else "Pending Due",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (summary.isReconciled) OneUiTheme.colors.income else OneUiTheme.colors.primaryAccent,
                                        fontSize = 11.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }

                        OneUiInsetDivider()

                        // Financial Grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Total Amount Due",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = OneUiTheme.colors.textSecondary,
                                        fontSize = 11.sp
                                    )
                                )
                                Text(
                                    text = "₹${CurrencyFormatter.formatIndianNumber(summary.statementTotalDue)}",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = OneUiTheme.colors.textPrimary
                                    )
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Payment Due Date",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = OneUiTheme.colors.textSecondary,
                                        fontSize = 11.sp
                                    )
                                )
                                val dueText = summary.paymentDueDateEpochDay?.let {
                                    LocalDate.ofEpochDay(it).format(dateFormatter)
                                } ?: "On Statement"
                                Text(
                                    text = dueText,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = OneUiTheme.colors.primaryAccent
                                    )
                                )
                            }
                        }

                        if (summary.statementMinDue != null && summary.statementMinDue > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Minimum Due: ₹${CurrencyFormatter.formatIndianNumber(summary.statementMinDue)}",
                                    style = MaterialTheme.typography.bodySmall.copy(color = OneUiTheme.colors.textSecondary)
                                )
                                Text(
                                    text = "${summary.transactionCount} transactions billed",
                                    style = MaterialTheme.typography.bodySmall.copy(color = OneUiTheme.colors.textSecondary)
                                )
                            }
                        }
                    }
                }
            }

            // 2. AUTOMATIC RECONCILIATION CARD (HIGHLIGHTED IN RED IF DISCREPANT)
            item(key = "reconciliation_card") {
                val report = reconciliationReport
                if (report != null) {
                    val hasDiscrepancy = report.hasDiscrepancy
                    val borderColor = if (hasDiscrepancy) OneUiTheme.colors.expense else OneUiTheme.colors.income
                    val bgColor = if (hasDiscrepancy) OneUiTheme.colors.expenseContainer else OneUiTheme.colors.incomeContainer

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(bgColor)
                            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (hasDiscrepancy) Icons.Default.Warning else Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = if (hasDiscrepancy) OneUiTheme.colors.expense else OneUiTheme.colors.income,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (hasDiscrepancy) "Statement Discrepancy Detected" else "Statement Reconciled",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (hasDiscrepancy) OneUiTheme.colors.expense else OneUiTheme.colors.income
                                        )
                                    )
                                }

                                if (hasDiscrepancy && report.unrecordedStatementTransactions.isNotEmpty()) {
                                    Surface(
                                        color = OneUiTheme.colors.expenseContainer,
                                        shape = CircleShape,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, OneUiTheme.colors.expense)
                                    ) {
                                        Text(
                                            text = "${report.unrecordedStatementTransactions.size} Unrecorded",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = OneUiTheme.colors.expense,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = if (hasDiscrepancy) {
                                    "Transactions in this statement differ from your manual app ledger for this period. Missing expenses are highlighted in red below."
                                } else {
                                    "All statement debits and credits match the transactions recorded in your app ledger for this billing period."
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = OneUiTheme.colors.textSecondary,
                                    fontSize = 12.sp
                                )
                            )

                            // Comparison Numbers
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(OneUiTheme.colors.surfaceElevated)
                                    .padding(vertical = 10.dp, horizontal = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Statement Billed",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = OneUiTheme.colors.textSecondary,
                                            fontSize = 11.sp
                                        )
                                    )
                                    Text(
                                        text = "₹${CurrencyFormatter.formatIndianNumber(report.statementTotalDebits)}",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = OneUiTheme.colors.textPrimary
                                        )
                                    )
                                }

                                Column {
                                    Text(
                                        text = "App Tracked",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = OneUiTheme.colors.textSecondary,
                                            fontSize = 11.sp
                                        )
                                    )
                                    Text(
                                        text = "₹${CurrencyFormatter.formatIndianNumber(report.manualTotalDebits)}",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = OneUiTheme.colors.textPrimary
                                        )
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Variance",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = OneUiTheme.colors.textSecondary,
                                            fontSize = 11.sp
                                        )
                                    )
                                    val sign = if (report.varianceAmount > 0) "+" else ""
                                    Text(
                                        text = "$sign₹${CurrencyFormatter.formatIndianNumber(report.varianceAmount)}",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (abs(report.varianceAmount) > 0.01) OneUiTheme.colors.expense else OneUiTheme.colors.income
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Transactions Section Header & Search / Filter
            item(key = "transactions_filter_header") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Itemized Statement Transactions (${filteredTransactions.size})",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = OneUiTheme.colors.textPrimary
                        )
                    )

                    // Search TextField
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search transactions...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = OneUiTheme.colors.textMuted
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = OneUiTheme.colors.textMuted
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = OneUiTheme.colors.surfaceElevated,
                            unfocusedContainerColor = OneUiTheme.colors.surfaceElevated,
                            focusedBorderColor = OneUiTheme.colors.primaryAccent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Filter Chips Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StmtTxFilter.values().forEach { filter ->
                            val isSelected = selectedFilter == filter
                            val chipBg = if (isSelected) {
                                if (filter == StmtTxFilter.UNRECORDED) OneUiTheme.colors.expense else OneUiTheme.colors.primaryAccent
                            } else {
                                OneUiTheme.colors.surfaceElevated
                            }
                            val chipTextColor = if (isSelected) Color.White else OneUiTheme.colors.textSecondary

                            Surface(
                                color = chipBg,
                                shape = CircleShape,
                                modifier = Modifier.clickable { selectedFilter = filter }
                            ) {
                                Text(
                                    text = if (filter == StmtTxFilter.UNRECORDED && unrecordedUids.isNotEmpty()) {
                                        "Discrepancies (${unrecordedUids.size})"
                                    } else {
                                        filter.label
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = chipTextColor
                                    ),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 4. List of Statement Transactions
            if (filteredTransactions.isEmpty()) {
                item(key = "empty_transactions") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No matching transactions found",
                            style = MaterialTheme.typography.bodyMedium.copy(color = OneUiTheme.colors.textMuted)
                        )
                    }
                }
            } else {
                items(filteredTransactions, key = { it.uid }) { stmtTx ->
                    val isUnrecorded = unrecordedUids.contains(stmtTx.uid)

                    // Card with red border and container background if unrecorded!
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isUnrecorded) OneUiTheme.colors.expenseContainer else OneUiTheme.colors.surfaceElevated)
                            .then(
                                if (isUnrecorded) {
                                    Modifier.border(1.dp, OneUiTheme.colors.expense, RoundedCornerShape(16.dp))
                                } else {
                                    Modifier
                                }
                            )
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stmtTx.cleanNarration,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = OneUiTheme.colors.textPrimary
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val txDate = LocalDate.ofEpochDay(stmtTx.transactionDateEpochDay)
                                    Text(
                                        text = "${txDate.format(shortDateFormatter)} • ${stmtTx.rawNarration}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = OneUiTheme.colors.textSecondary,
                                            fontSize = 11.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    val sign = if (stmtTx.isCredit) "+" else "-"
                                    val amountColor = when {
                                        stmtTx.isCredit -> OneUiTheme.colors.income
                                        isUnrecorded -> OneUiTheme.colors.expense
                                        else -> OneUiTheme.colors.textPrimary
                                    }
                                    Text(
                                        text = "$sign₹${CurrencyFormatter.formatIndianNumber(stmtTx.amount)}",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = amountColor
                                        )
                                    )
                                }
                            }

                            // Discrepancy Banner + 1-Tap Add to Ledger Button
                            if (isUnrecorded) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = OneUiTheme.colors.expense,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Missing in app ledger",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = OneUiTheme.colors.expense,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }

                                    Surface(
                                        color = OneUiTheme.colors.expense,
                                        shape = CircleShape,
                                        modifier = Modifier.clickable { onAddTxToLedger(stmtTx) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Add to ledger",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
