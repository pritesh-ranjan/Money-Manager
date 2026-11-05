package com.moneymanager.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.moneymanager.app.data.local.entity.*
import com.moneymanager.app.domain.CurrencyFormatter
import com.moneymanager.app.ui.components.*
import com.moneymanager.app.ui.main.MainViewModel
import com.moneymanager.app.ui.theme.OneUiTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class InsightsFilter(val label: String) {
    ALL("All"),
    SUBSCRIPTIONS("Subscriptions"),
    TRANSFERS("SIP & Transfers"),
    ANOMALIES("Unusual"),
    DUPLICATES("Duplicates"),
    SPIKES("Spikes")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onEditTransaction: (TransactionWithDetails) -> Unit
) {
    BackHandler(onBack = onBack)

    val activeInsights by viewModel.activeInsights.collectAsState()
    val monthlySubsTotal by viewModel.monthlySubscriptionsTotal.collectAsState()
    val monthlyTransfersTotal by viewModel.monthlyRecurringTransfersTotal.collectAsState()
    val allTransactions by viewModel.allTransactionsUnfiltered.collectAsState()
    val categoriesMap by viewModel.categoriesMap.collectAsState()

    var selectedFilter by remember { mutableStateOf(InsightsFilter.ALL) }
    var drillDownInsight by remember { mutableStateOf<InsightWithTransactions?>(null) }
    var confidenceExplanationInsight by remember { mutableStateOf<InsightEntity?>(null) }
    var duplicateToDelete by remember { mutableStateOf<Pair<InsightEntity, String>?>(null) }

    val filteredInsights = remember(activeInsights, selectedFilter) {
        when (selectedFilter) {
            InsightsFilter.ALL -> activeInsights
            InsightsFilter.SUBSCRIPTIONS -> activeInsights.filter {
                it.insight.type in setOf(InsightType.SUBSCRIPTION, InsightType.FORGOTTEN_SUBSCRIPTION, InsightType.PRICE_INCREASE)
            }
            InsightsFilter.TRANSFERS -> activeInsights.filter {
                it.insight.type == InsightType.RECURRING_TRANSFER
            }
            InsightsFilter.ANOMALIES -> activeInsights.filter {
                it.insight.type == InsightType.LARGE_TRANSACTION
            }
            InsightsFilter.DUPLICATES -> activeInsights.filter {
                it.insight.type == InsightType.DUPLICATE
            }
            InsightsFilter.SPIKES -> activeInsights.filter {
                it.insight.type == InsightType.CATEGORY_SPIKE
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        containerColor = OneUiTheme.colors.background,
        topBar = {
            OneUiSubScreenTopBar(
                title = "Financial Insights",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { viewModel.runAnalysisNow() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Insights",
                            tint = OneUiTheme.colors.textPrimary
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Dual Monthly Commitments Header
            item {
                DualCommitmentsHeader(
                    subscriptionsMonthly = monthlySubsTotal,
                    transfersMonthly = monthlyTransfersTotal
                )
            }

            // Filter Chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(InsightsFilter.values()) { filter ->
                        val isSelected = selectedFilter == filter
                        val count = remember(activeInsights, filter) {
                            when (filter) {
                                InsightsFilter.ALL -> activeInsights.size
                                InsightsFilter.SUBSCRIPTIONS -> activeInsights.count { it.insight.type in setOf(InsightType.SUBSCRIPTION, InsightType.FORGOTTEN_SUBSCRIPTION, InsightType.PRICE_INCREASE) }
                                InsightsFilter.TRANSFERS -> activeInsights.count { it.insight.type == InsightType.RECURRING_TRANSFER }
                                InsightsFilter.ANOMALIES -> activeInsights.count { it.insight.type == InsightType.LARGE_TRANSACTION }
                                InsightsFilter.DUPLICATES -> activeInsights.count { it.insight.type == InsightType.DUPLICATE }
                                InsightsFilter.SPIKES -> activeInsights.count { it.insight.type == InsightType.CATEGORY_SPIKE }
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) OneUiTheme.colors.primaryAccent else OneUiTheme.colors.surface,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { selectedFilter = filter }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = filter.label,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else OneUiTheme.colors.textSecondary
                                )
                                if (count > 0) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) Color.White.copy(alpha = 0.25f)
                                                else OneUiTheme.colors.surfaceElevated
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = count.toString(),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else OneUiTheme.colors.textMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Empty State
            if (filteredInsights.isEmpty()) {
                item {
                    EmptyInsightsState(filter = selectedFilter)
                }
            } else {
                items(filteredInsights, key = { it.insight.id }) { item ->
                    InsightCardView(
                        item = item,
                        onViewTransactions = { drillDownInsight = item },
                        onExplainConfidence = { confidenceExplanationInsight = item.insight },
                        onConfirm = { viewModel.confirmRecurrence(item.insight) },
                        onReject = { viewModel.rejectRecurrence(item.insight) },
                        onDeleteDuplicate = { duplicateTxUid ->
                            duplicateToDelete = Pair(item.insight, duplicateTxUid)
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Drilldown Transactions Sheet
    drillDownInsight?.let { item ->
        InsightTransactionsSheet(
            item = item,
            allTransactions = allTransactions,
            categoriesMap = categoriesMap,
            onDismiss = { drillDownInsight = null },
            onEditTransaction = { tx ->
                drillDownInsight = null
                onEditTransaction(tx)
            }
        )
    }

    // Confidence Breakdown Explanation Dialog
    confidenceExplanationInsight?.let { insight ->
        ConfidenceBreakdownDialog(
            insight = insight,
            onDismiss = { confidenceExplanationInsight = null }
        )
    }

    // Delete Duplicate Confirmation Dialog
    duplicateToDelete?.let { (insight, txUid) ->
        AlertDialog(
            onDismissRequest = { duplicateToDelete = null },
            title = {
                Text(
                    text = "Delete Duplicate?",
                    fontWeight = FontWeight.Bold,
                    color = OneUiTheme.colors.textPrimary
                )
            },
            text = {
                Text(
                    text = "This will delete the duplicate transaction from your ledger. The original entry will be safely preserved.",
                    color = OneUiTheme.colors.textSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDuplicate(txUid)
                        duplicateToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OneUiTheme.colors.expense)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { duplicateToDelete = null }) {
                    Text("Cancel", color = OneUiTheme.colors.textSecondary)
                }
            },
            containerColor = OneUiTheme.colors.surfaceElevated,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun DualCommitmentsHeader(
    subscriptionsMonthly: Double,
    transfersMonthly: Double
) {
    OneUiCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = OneUiTheme.colors.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Subscriptions
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "MONTHLY BILLS & SUBS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OneUiTheme.colors.textMuted
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "₹${CurrencyFormatter.formatIndianNumber(subscriptionsMonthly, decimals = 0)}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = OneUiTheme.colors.textPrimary
                )
                Text(
                    text = "Recurring expenses",
                    fontSize = 12.sp,
                    color = OneUiTheme.colors.textSecondary
                )
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(48.dp)
                    .background(OneUiTheme.colors.divider)
            )

            // Transfers & SIPs
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            ) {
                Text(
                    text = "MONTHLY SIPS & RDS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OneUiTheme.colors.textMuted
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "₹${CurrencyFormatter.formatIndianNumber(transfersMonthly, decimals = 0)}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = OneUiTheme.colors.income
                )
                Text(
                    text = "Recurring investments",
                    fontSize = 12.sp,
                    color = OneUiTheme.colors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun InsightCardView(
    item: InsightWithTransactions,
    onViewTransactions: () -> Unit,
    onExplainConfidence: () -> Unit,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
    onDeleteDuplicate: (String) -> Unit
) {
    val insight = item.insight

    val (badgeIcon, badgeBg) = when (insight.type) {
        InsightType.SUBSCRIPTION -> Pair(Icons.Default.Repeat, OneUiTheme.colors.primaryAccent.copy(alpha = 0.15f))
        InsightType.RECURRING_TRANSFER -> Pair(Icons.Default.Savings, OneUiTheme.colors.income.copy(alpha = 0.15f))
        InsightType.LARGE_TRANSACTION -> Pair(Icons.Default.Warning, OneUiTheme.colors.expense.copy(alpha = 0.15f))
        InsightType.CATEGORY_SPIKE -> Pair(Icons.Default.TrendingUp, OneUiTheme.colors.secondaryCyan.copy(alpha = 0.15f))
        InsightType.DUPLICATE -> Pair(Icons.Default.ContentCopy, Color(0xFFF59E0B).copy(alpha = 0.15f))
        InsightType.PRICE_INCREASE -> Pair(Icons.Default.ArrowUpward, OneUiTheme.colors.expense.copy(alpha = 0.15f))
        InsightType.FORGOTTEN_SUBSCRIPTION -> Pair(Icons.Default.HelpOutline, OneUiTheme.colors.textMuted.copy(alpha = 0.15f))
    }

    val iconColor = when (insight.type) {
        InsightType.SUBSCRIPTION -> OneUiTheme.colors.primaryAccent
        InsightType.RECURRING_TRANSFER -> OneUiTheme.colors.income
        InsightType.LARGE_TRANSACTION, InsightType.PRICE_INCREASE -> OneUiTheme.colors.expense
        InsightType.CATEGORY_SPIKE -> OneUiTheme.colors.secondaryCyan
        InsightType.DUPLICATE -> Color(0xFFF59E0B)
        InsightType.FORGOTTEN_SUBSCRIPTION -> OneUiTheme.colors.textMuted
    }

    OneUiCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = OneUiTheme.colors.surface
    ) {
        // Header Row: Icon + Title + Severity/Tag
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(badgeBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = badgeIcon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = insight.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = OneUiTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (insight.autoAssignedTag != null) {
                    Text(
                        text = "#${insight.autoAssignedTag}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = OneUiTheme.colors.primaryAccent
                    )
                }
            }

            if (insight.severity == InsightSeverity.HIGH) {
                Surface(
                    shape = CircleShape,
                    color = OneUiTheme.colors.expense.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "HIGH",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = OneUiTheme.colors.expense,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Summary Description
        Text(
            text = insight.summary,
            fontSize = 13.sp,
            color = OneUiTheme.colors.textSecondary,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Metrics Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Confidence Badge (clickable)
            if (insight.confidence != null && insight.confidenceBreakdown != null) {
                Surface(
                    shape = CircleShape,
                    color = OneUiTheme.colors.surfaceElevated,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onExplainConfidence() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${(insight.confidence * 100).toInt()}% Confidence",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OneUiTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Confidence Details",
                            tint = OneUiTheme.colors.textMuted,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            // Expected Next Date
            if (insight.expectedNextDate != null) {
                val fmt = DateTimeFormatter.ofPattern("dd MMM yyyy")
                val nextDateStr = Instant.ofEpochMilli(insight.expectedNextDate)
                    .atZone(ZoneId.systemDefault())
                    .format(fmt)

                Surface(
                    shape = CircleShape,
                    color = OneUiTheme.colors.surfaceElevated
                ) {
                    Text(
                        text = "Next: $nextDateStr",
                        fontSize = 11.sp,
                        color = OneUiTheme.colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Historical Median / Avg
            if (insight.historicalMedian != null && insight.type == InsightType.LARGE_TRANSACTION) {
                Surface(
                    shape = CircleShape,
                    color = OneUiTheme.colors.surfaceElevated
                ) {
                    Text(
                        text = "Median: ₹${CurrencyFormatter.formatIndianNumber(insight.historicalMedian, decimals = 0)}",
                        fontSize = 11.sp,
                        color = OneUiTheme.colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = OneUiTheme.colors.divider, thickness = 0.5.dp)
        Spacer(modifier = Modifier.height(10.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // View Transactions
            if (item.transactions.isNotEmpty()) {
                TextButton(
                    onClick = onViewTransactions,
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = OneUiTheme.colors.primaryAccent
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "View ${item.transactions.size} transactions",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = OneUiTheme.colors.primaryAccent
                    )
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            // Specific Card Actions
            when (insight.type) {
                InsightType.SUBSCRIPTION,
                InsightType.RECURRING_TRANSFER,
                InsightType.FORGOTTEN_SUBSCRIPTION,
                InsightType.PRICE_INCREASE -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onReject,
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Not Recurring", fontSize = 11.sp, color = OneUiTheme.colors.textSecondary)
                        }

                        Button(
                            onClick = onConfirm,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = OneUiTheme.colors.primaryAccent),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Confirm", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }

                InsightType.DUPLICATE -> {
                    val duplicateUid = item.transactions.sortedBy { it.timestamp }.lastOrNull()?.uid
                    if (duplicateUid != null) {
                        Button(
                            onClick = { onDeleteDuplicate(duplicateUid) },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = OneUiTheme.colors.expense),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete Duplicate", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }

                else -> {}
            }
        }
    }
}

@Composable
private fun EmptyInsightsState(filter: InsightsFilter) {
    OneUiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        containerColor = OneUiTheme.colors.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(OneUiTheme.colors.surfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = OneUiTheme.colors.income,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "No ${filter.label} Insights",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = OneUiTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Everything looks clean. As you log transactions, new patterns, duplicates, and recurring baselines will appear here.",
                fontSize = 13.sp,
                color = OneUiTheme.colors.textMuted,
                modifier = Modifier.padding(horizontal = 24.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun ConfidenceBreakdownDialog(
    insight: InsightEntity,
    onDismiss: () -> Unit
) {
    val breakdown = insight.confidenceBreakdown ?: return

    val pairs = breakdown.split(";").mapNotNull { part ->
        val kv = part.split("=")
        if (kv.size == 2) kv[0] to (kv[1].toDoubleOrNull() ?: 0.0) else null
    }.toMap()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Why ${(insight.confidence?.times(100))?.toInt() ?: 0}% Confidence?",
                fontWeight = FontWeight.Bold,
                color = OneUiTheme.colors.textPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Our explainable detection engine scores four weighted statistical pillars:",
                    fontSize = 13.sp,
                    color = OneUiTheme.colors.textSecondary
                )

                ConfidencePillarRow("Interval Consistency (40%)", pairs["interval"] ?: 0.0)
                ConfidencePillarRow("Amount Consistency (25%)", pairs["amount"] ?: 0.0)
                ConfidencePillarRow("Merchant / Target Similarity (15%)", pairs["target"] ?: pairs["merchant"] ?: 0.0)
                ConfidencePillarRow("Payment History Depth (20%)", pairs["history"] ?: 0.0)
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = OneUiTheme.colors.primaryAccent)
            ) {
                Text("Understood", color = Color.White)
            }
        },
        containerColor = OneUiTheme.colors.surfaceElevated,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun ConfidencePillarRow(name: String, score: Double) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = name, fontSize = 12.sp, color = OneUiTheme.colors.textPrimary)
            Text(
                text = "${(score * 100).toInt()}%",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = OneUiTheme.colors.primaryAccent
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        LinearProgressIndicator(
            progress = { score.toFloat().coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape),
            color = OneUiTheme.colors.primaryAccent,
            trackColor = OneUiTheme.colors.surface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InsightTransactionsSheet(
    item: InsightWithTransactions,
    allTransactions: List<TransactionWithDetails>,
    categoriesMap: Map<String, CategoryEntity> = emptyMap(),
    onDismiss: () -> Unit,
    onEditTransaction: (TransactionWithDetails) -> Unit
) {
    val txDetailsMap = remember(allTransactions) {
        allTransactions.associateBy { it.transaction.uid }
    }

    val linkedTransactions = remember(item, txDetailsMap) {
        item.transactions.mapNotNull { txDetailsMap[it.uid] }
            .sortedByDescending { it.transaction.timestamp }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = OneUiTheme.colors.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(OneUiTheme.colors.divider)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.insight.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = OneUiTheme.colors.textPrimary,
                            fontSize = 18.sp
                        )
                    )
                    Text(
                        text = "${linkedTransactions.size} matching transactions",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = OneUiTheme.colors.textSecondary,
                            fontSize = 13.sp
                        )
                    )
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = OneUiTheme.colors.textMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(linkedTransactions, key = { it.transaction.uid }) { txWithDetails ->
                    OneUiTransactionRowItem(
                        item = txWithDetails,
                        categoriesMap = categoriesMap,
                        showDate = true,
                        onClick = { onEditTransaction(txWithDetails) }
                    )
                    OneUiInsetDivider(startIndent = 18.dp)
                }
            }
        }
    }
}
