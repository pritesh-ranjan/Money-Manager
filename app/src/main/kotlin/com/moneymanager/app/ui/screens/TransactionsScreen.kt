package com.moneymanager.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.data.local.entity.TransactionType
import com.moneymanager.app.data.local.entity.TransactionWithDetails
import com.moneymanager.app.domain.CurrencyFormatter
import com.moneymanager.app.ui.components.*
import com.moneymanager.app.ui.main.MainViewModel
import com.moneymanager.app.ui.theme.*
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

enum class DailySubTab(val title: String) {
    DAILY("Daily"),
    CALENDAR("Calendar"),
    MONTHLY("Monthly"),
    NOTE("Notes")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionsScreen(
    viewModel: MainViewModel,
    onEditTransaction: (TransactionWithDetails) -> Unit
) {
    val selectedYm by viewModel.selectedYearMonth.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    val selectedTag by viewModel.selectedTag.collectAsState()
    val categoriesMap by viewModel.categoriesMap.collectAsState()

    var activeSubTab by remember { mutableStateOf(DailySubTab.DAILY) }
    var isSearchScreenOpen by remember { mutableStateOf(false) }
    var selectedTypeFilter by remember { mutableStateOf<TransactionType?>(null) }
    var selectedDateFilter by remember(selectedYm) { mutableStateOf<LocalDate?>(null) }

    // 7-day strip centered around today (if current month) or end/middle of selected month
    val today = remember { LocalDate.now() }
    val referenceDate = remember(selectedYm, selectedDateFilter) {
        selectedDateFilter ?: if (selectedYm == YearMonth.from(today)) {
            today
        } else {
            selectedYm.atDay(minOf(today.dayOfMonth, selectedYm.lengthOfMonth()))
        }
    }

    val sevenDayStrip = remember(referenceDate) {
        (-3..3).map { offset -> referenceDate.plusDays(offset.toLong()) }
    }

    // Monthly Totals
    val totalIncome = remember(transactions) {
        transactions.filter { it.transaction.type == TransactionType.INCOME }.sumOf { it.transaction.baseAmount }
    }
    val totalExpense = remember(transactions) {
        transactions.filter { it.transaction.type == TransactionType.EXPENSE }.sumOf { it.transaction.baseAmount }
    }
    val netTotal = totalIncome - totalExpense

    // Apply Date Filter & Type Filter
    val displayedTransactions = remember(transactions, selectedDateFilter, selectedTypeFilter) {
        transactions.filter { item ->
            val txDate = Instant.ofEpochMilli(item.transaction.timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            val matchesDate = selectedDateFilter == null || txDate == selectedDateFilter
            val matchesType = selectedTypeFilter == null || item.transaction.type == selectedTypeFilter
            matchesDate && matchesType
        }
    }

    val groupedByDay = remember(displayedTransactions) {
        displayedTransactions.groupBy { item ->
            Instant.ofEpochMilli(item.transaction.timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        }.toSortedMap(compareByDescending { it })
    }

    if (isSearchScreenOpen) {
        OneUiSearchAndFilterScreen(
            searchQuery = searchQuery,
            onSearchQueryChange = { viewModel.setSearchQuery(it) },
            allTags = allTags.map { it.uid to it.name },
            selectedTag = selectedTag,
            onTagSelected = { viewModel.setSelectedTag(it) },
            selectedTypeFilter = selectedTypeFilter,
            onTypeFilterSelected = { selectedTypeFilter = it },
            matchingTransactions = displayedTransactions,
            categoriesMap = categoriesMap,
            onTransactionClick = { tx ->
                isSearchScreenOpen = false
                onEditTransaction(tx)
            },
            onBack = {
                isSearchScreenOpen = false
            }
        )
        return
    }

    val monthTitle = selectedYm.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
    val subtitleText = "Net: " + (if (netTotal >= 0) "+₹" else "-₹") +
            CurrencyFormatter.formatIndianNumber(abs(netTotal))

    OneUiCollapsibleScaffold(
        title = monthTitle,
        subtitle = subtitleText,
        expandedHeight = 165.dp,
        centeredExpandedTitle = true,
        actions = {
            IconButton(
                onClick = {
                    selectedDateFilter = null
                    viewModel.previousMonth()
                },
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous Month",
                    tint = OneUiTheme.colors.textPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(
                onClick = {
                    selectedDateFilter = null
                    viewModel.nextMonth()
                },
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next Month",
                    tint = OneUiTheme.colors.textPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(
                onClick = { isSearchScreenOpen = true },
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = if (searchQuery.isNotEmpty() || selectedTag != null || selectedTypeFilter != null) {
                        OneUiTheme.colors.primaryAccent
                    } else {
                        OneUiTheme.colors.textPrimary
                    },
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
                top = contentPadding.calculateTopPadding() + 4.dp,
                bottom = 96.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Samsung One UI 9 Dashboard 7-Day Date Strip (Screenshot 3)
            item(key = "one_ui_date_strip") {
                val leftText = when {
                    selectedDateFilter == today -> "Today"
                    selectedDateFilter != null -> selectedDateFilter!!.format(DateTimeFormatter.ofPattern("EEE, dd MMM", Locale.getDefault()))
                    else -> "All of ${selectedYm.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()))}"
                }
                val rightLinkText = if (selectedDateFilter != null) "Show all month" else "Jump to today"

                OneUiDateStrip(
                    dates = sevenDayStrip,
                    selectedDate = selectedDateFilter ?: if (selectedYm == YearMonth.from(today)) today else null,
                    onDateSelected = { clickedDate ->
                        selectedDateFilter = if (selectedDateFilter == clickedDate) null else clickedDate
                    },
                    leftLabel = leftText,
                    rightActionLabel = rightLinkText,
                    onRightActionClick = {
                        if (selectedDateFilter != null) {
                            selectedDateFilter = null
                        } else {
                            viewModel.setSelectedMonth(YearMonth.from(today))
                            selectedDateFilter = today
                        }
                    }
                )
            }

            // Active Filter Indicator Chip Bar (if search/tag/type filter is active)
            if (searchQuery.isNotEmpty() || selectedTag != null || selectedTypeFilter != null) {
                item(key = "active_filters_bar") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (searchQuery.isNotEmpty()) {
                            OneUiCapsuleChip(
                                label = "\"$searchQuery\"",
                                selected = true,
                                onClick = { viewModel.setSearchQuery("") }
                            )
                        }
                        if (selectedTag != null) {
                            val tagName = allTags.firstOrNull { it.uid == selectedTag }?.name ?: selectedTag!!
                            OneUiCapsuleChip(
                                label = "#$tagName",
                                selected = true,
                                onClick = { viewModel.setSelectedTag(null) }
                            )
                        }
                        if (selectedTypeFilter != null) {
                            OneUiCapsuleChip(
                                label = selectedTypeFilter!!.displayName,
                                selected = true,
                                onClick = { selectedTypeFilter = null }
                            )
                        }
                    }
                }
            }

            // 2. One UI 9 Dashboard Hero Card (Screenshot 3 & 4 style)
            item(key = "dashboard_hero_card") {
                OneUiCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text(
                                text = "Total expenses",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = OneUiTheme.colors.textSecondary,
                                    fontSize = 13.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "₹" + CurrencyFormatter.formatIndianNumber(totalExpense),
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = OneUiTheme.colors.textPrimary,
                                    fontSize = 32.sp
                                )
                            )
                        }

                        // Compact Pill Switcher for View Mode
                        OneUiPillButton(
                            text = activeSubTab.title,
                            onClick = {
                                val nextOrd = (activeSubTab.ordinal + 1) % DailySubTab.values().size
                                activeSubTab = DailySubTab.values()[nextOrd]
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Slender Horizontal Cashflow Bar (Blue / Green / Coral)
                    val totalFlow = (totalIncome + totalExpense).coerceAtLeast(1.0)
                    val expenseRatio = (totalExpense / totalFlow).toFloat().coerceIn(0.08f, 0.92f)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(OneUiTheme.colors.surfaceElevated),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        if (totalExpense > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(expenseRatio)
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(OneUiTheme.colors.primaryAccent)
                            )
                        }
                        if (totalIncome > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(1f - expenseRatio)
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(OneUiTheme.colors.income)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Legend Rows matching Screenshot 3 ("Instagram 56 m", "Chrome 24 m")
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        OneUiLegendMetricRow(
                            dotColor = OneUiTheme.colors.income,
                            label = "Income",
                            value = "+₹" + CurrencyFormatter.formatIndianNumber(totalIncome),
                            valueColor = OneUiTheme.colors.income
                        )
                        OneUiLegendMetricRow(
                            dotColor = OneUiTheme.colors.primaryAccent,
                            label = "Expenses",
                            value = "-₹" + CurrencyFormatter.formatIndianNumber(totalExpense),
                            valueColor = OneUiTheme.colors.textPrimary
                        )
                        OneUiLegendMetricRow(
                            dotColor = OneUiTheme.colors.secondaryCyan,
                            label = "Net Balance",
                            value = (if (netTotal >= 0) "+₹" else "-₹") + CurrencyFormatter.formatIndianNumber(abs(netTotal)),
                            valueColor = if (netTotal >= 0) OneUiTheme.colors.textPrimary else OneUiTheme.colors.expense
                        )
                    }
                }
            }

            // 3. Sub-Tab Switcher
            item(key = "sub_tabs_pill") {
                OneUiPillSegmentedControl(
                    items = DailySubTab.values().toList(),
                    selectedItem = activeSubTab,
                    onItemSelected = { activeSubTab = it },
                    labelProvider = { it.title }
                )
            }

            // 4. Transactions List / Sub-Tab Views
            when (activeSubTab) {
                DailySubTab.DAILY -> {
                    if (displayedTransactions.isEmpty()) {
                        item(key = "empty_transactions") {
                            OneUiCard {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = if (selectedDateFilter != null) {
                                            "No transactions recorded on ${selectedDateFilter!!.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}."
                                        } else {
                                            "No transactions for this period."
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = OneUiTheme.colors.textSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                    if (selectedDateFilter != null) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        OneUiPillButton(
                                            text = "Show full month",
                                            onClick = { selectedDateFilter = null },
                                            containerColor = OneUiTheme.colors.primaryAccent,
                                            contentColor = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        groupedByDay.forEach { (date, dayTxs) ->
                            item(key = "day_group_$date") {
                                val dayIncome = dayTxs.filter { it.transaction.type == TransactionType.INCOME }.sumOf { it.transaction.baseAmount }
                                val dayExpense = dayTxs.filter { it.transaction.type == TransactionType.EXPENSE }.sumOf { it.transaction.baseAmount }

                                OneUiCard {
                                    OneUiDayHeader(
                                        date = date,
                                        dayIncome = dayIncome,
                                        dayExpense = dayExpense
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))
                                    OneUiInsetDivider(startIndent = 0.dp)

                                    dayTxs.forEachIndexed { index, item ->
                                        OneUiTransactionRowItem(
                                            item = item,
                                            categoriesMap = categoriesMap,
                                            colorIndex = index,
                                            onClick = { onEditTransaction(item) }
                                        )

                                        if (index < dayTxs.lastIndex) {
                                            OneUiInsetDivider(startIndent = 18.dp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                DailySubTab.CALENDAR, DailySubTab.MONTHLY, DailySubTab.NOTE -> {
                    item(key = "summary_subtab") {
                        OneUiCard {
                            Text(
                                text = "${activeSubTab.title} Summary",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = OneUiTheme.colors.textPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            if (activeSubTab == DailySubTab.NOTE) {
                                val notedTxs = displayedTransactions.filter { it.transaction.note.isNotBlank() }
                                if (notedTxs.isEmpty()) {
                                    Text(
                                        text = "No notes recorded in this period.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OneUiTheme.colors.textSecondary
                                    )
                                } else {
                                    notedTxs.take(15).forEachIndexed { idx, item ->
                                        OneUiVerticalAccentRow(
                                            title = item.transaction.note,
                                            subtitle = "${item.category?.name ?: item.transaction.type.displayName} • ${item.account.name}",
                                            valueText = "₹" + CurrencyFormatter.formatIndianNumber(abs(item.transaction.baseAmount)),
                                            accentColor = StatsPieColors[idx % StatsPieColors.size],
                                            onClick = { onEditTransaction(item) }
                                        )
                                        if (idx < notedTxs.take(15).lastIndex) {
                                            OneUiInsetDivider(startIndent = 18.dp)
                                        }
                                    }
                                }
                            } else {
                                OneUiVerticalAccentRow(
                                    title = "Monthly Income",
                                    subtitle = "${displayedTransactions.count { it.transaction.type == TransactionType.INCOME }} entries",
                                    valueText = "+₹" + CurrencyFormatter.formatIndianNumber(totalIncome),
                                    valueColor = OneUiTheme.colors.income,
                                    accentColor = OneUiTheme.colors.income
                                )
                                OneUiInsetDivider(startIndent = 18.dp)
                                OneUiVerticalAccentRow(
                                    title = "Monthly Expenses",
                                    subtitle = "${displayedTransactions.count { it.transaction.type == TransactionType.EXPENSE }} entries",
                                    valueText = "-₹" + CurrencyFormatter.formatIndianNumber(totalExpense),
                                    valueColor = OneUiTheme.colors.expense,
                                    accentColor = OneUiTheme.colors.expense
                                )
                                OneUiInsetDivider(startIndent = 18.dp)
                                OneUiVerticalAccentRow(
                                    title = "Net Cashflow",
                                    subtitle = "${displayedTransactions.size} total transactions",
                                    valueText = (if (netTotal >= 0) "+₹" else "-₹") + CurrencyFormatter.formatIndianNumber(abs(netTotal)),
                                    accentColor = OneUiTheme.colors.primaryAccent
                                )
                            }
                        }
                    }
                }
            }

            // 5. Bottom Floating Search Pill (Screenshot 1)
            item(key = "bottom_search_pill") {
                Spacer(modifier = Modifier.height(4.dp))
                OneUiFloatingSearchPill(
                    hint = "Search transactions, tags, notes",
                    onClick = { isSearchScreenOpen = true }
                )
            }
        }
    }
}

@Composable
private fun OneUiLegendMetricRow(
    dotColor: Color,
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = OneUiTheme.colors.textSecondary,
                fontSize = 13.5.sp
            ),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = valueColor,
                fontWeight = FontWeight.Medium,
                fontSize = 13.5.sp
            )
        )
    }
}

@Composable
fun OneUiDayHeader(
    date: LocalDate,
    dayIncome: Double,
    dayExpense: Double
) {
    val dayOfWeek = date.dayOfWeek
    val dayLetter = dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    val isToday = date == LocalDate.now()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Day of month pill (Screenshot 3 style)
        Box(
            modifier = Modifier
                .height(26.dp)
                .clip(CircleShape)
                .background(
                    if (isToday) OneUiTheme.colors.primaryAccent else OneUiTheme.colors.surfaceElevated
                )
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${date.dayOfMonth} $dayLetter",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Medium,
                    color = if (isToday) Color.White else OneUiTheme.colors.textPrimary,
                    fontSize = 12.5.sp
                )
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = if (isToday) "Today" else date.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())),
            style = MaterialTheme.typography.bodySmall.copy(
                color = OneUiTheme.colors.textMuted,
                fontSize = 12.sp
            )
        )

        Spacer(modifier = Modifier.weight(1f))

        if (dayIncome > 0) {
            Text(
                text = "+₹" + CurrencyFormatter.formatIndianNumber(dayIncome),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Normal,
                    color = OneUiTheme.colors.income,
                    fontSize = 12.5.sp
                ),
                modifier = Modifier.padding(end = 8.dp)
            )
        }

        if (dayExpense > 0) {
            Text(
                text = "-₹" + CurrencyFormatter.formatIndianNumber(dayExpense),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Normal,
                    color = OneUiTheme.colors.textPrimary,
                    fontSize = 12.5.sp
                )
            )
        }
    }
}

/**
 * Transaction Row styled with Screenshot 2's Vertical Colored Accent Bar.
 */
@Composable
fun OneUiTransactionRowItem(
    item: TransactionWithDetails,
    categoriesMap: Map<String, CategoryEntity>,
    colorIndex: Int = 0,
    onClick: Unit? = null,
    onClickAction: () -> Unit
) {
    val tx = item.transaction
    val cat = item.category
    val parentCat = if (cat?.parentUid != null) categoriesMap[cat.parentUid] else null
    val rootCategoryName = when {
        parentCat != null -> parentCat.name
        cat != null -> cat.name
        tx.type == TransactionType.TRANSFER -> "Transfer"
        tx.type == TransactionType.ADJUSTMENT -> "Adjustment"
        else -> "Uncategorized"
    }
    val subCategoryName = if (parentCat != null) cat?.name else null

    val barColor = when (tx.type) {
        TransactionType.INCOME -> OneUiTheme.colors.income
        TransactionType.TRANSFER -> OneUiTheme.colors.secondaryCyan
        TransactionType.ADJUSTMENT -> OneUiTheme.colors.warning
        TransactionType.EXPENSE -> StatsPieColors[abs(rootCategoryName.hashCode()) % StatsPieColors.size]
    }

    val amountColor = when (tx.type) {
        TransactionType.INCOME -> OneUiTheme.colors.income
        TransactionType.EXPENSE -> OneUiTheme.colors.textPrimary
        TransactionType.TRANSFER -> OneUiTheme.colors.primaryAccent
        TransactionType.ADJUSTMENT -> OneUiTheme.colors.warning
    }

    val titleText = if (tx.note.isNotBlank()) tx.note else rootCategoryName
    val accountLabel = if (tx.type == TransactionType.TRANSFER && item.toAccount != null) {
        "${item.account.name} → ${item.toAccount.name}"
    } else {
        item.account.name
    }
    val catHierarchy = if (!subCategoryName.isNullOrBlank()) "$rootCategoryName ($subCategoryName)" else rootCategoryName
    val tagsLabel = if (item.tags.isNotEmpty()) " • " + item.tags.joinToString(" ") { "#${it.name}" } else ""
    val subtitleText = "$catHierarchy • $accountLabel$tagsLabel"

    val prefix = when (tx.type) {
        TransactionType.INCOME -> "+₹"
        TransactionType.EXPENSE -> "-₹"
        else -> "₹"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClickAction)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Vertical Colored Accent Pill (3.2.dp wide x 24.dp tall, matching Screenshot 2)
        Box(
            modifier = Modifier
                .width(3.2.dp)
                .height(24.dp)
                .clip(CircleShape)
                .background(barColor)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Title & Subtitle
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = titleText,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = OneUiTheme.colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = OneUiTheme.colors.textSecondary,
                    fontSize = 12.5.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Right-Aligned Amount
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = prefix + CurrencyFormatter.formatIndianNumber(abs(tx.baseAmount)),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Normal,
                    color = amountColor,
                    fontSize = 14.5.sp
                )
            )
            if (!item.currency.isMainCurrency) {
                Text(
                    text = "${item.currency.symbol} ${CurrencyFormatter.formatIndianNumber(abs(tx.accountAmount))}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = OneUiTheme.colors.textMuted,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

@Composable
fun OneUiTransactionRowItem(
    item: TransactionWithDetails,
    categoriesMap: Map<String, CategoryEntity>,
    colorIndex: Int = 0,
    onClick: () -> Unit
) {
    OneUiTransactionRowItem(
        item = item,
        categoriesMap = categoriesMap,
        colorIndex = colorIndex,
        onClick = null,
        onClickAction = onClick
    )
}

/**
 * Dedicated One UI 9 Search & Filter Screen (Direct match to Screenshot 5 "Recently used settings").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OneUiSearchAndFilterScreen(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    allTags: List<Pair<String, String>>,
    selectedTag: String?,
    onTagSelected: (String?) -> Unit,
    selectedTypeFilter: TransactionType?,
    onTypeFilterSelected: (TransactionType?) -> Unit,
    matchingTransactions: List<TransactionWithDetails>,
    categoriesMap: Map<String, CategoryEntity>,
    onTransactionClick: (TransactionWithDetails) -> Unit,
    onBack: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    BackHandler(onBack = onBack)

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OneUiTheme.colors.background)
            .statusBarsPadding()
            .imePadding()
    ) {
        // Top Bar with '<' Back Chevron (Screenshot 5)
        OneUiSubScreenTopBar(
            title = "",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
        ) {
            // Section 1: "Recently used filters" with Wrapped Capsule Chips (Screenshot 5)
            item(key = "filter_chips_section") {
                Text(
                    text = "Quick filters & tags",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = OneUiTheme.colors.textPrimary,
                        fontSize = 19.sp
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Transaction Type Capsules
                    TransactionType.values().forEach { type ->
                        val isSelected = selectedTypeFilter == type
                        OneUiCapsuleChip(
                            label = type.displayName,
                            selected = isSelected,
                            onClick = {
                                onTypeFilterSelected(if (isSelected) null else type)
                            }
                        )
                    }

                    // Tag Capsules
                    allTags.forEach { (uid, name) ->
                        val isSelected = selectedTag == uid
                        OneUiCapsuleChip(
                            label = "#$name",
                            selected = isSelected,
                            onClick = {
                                onTagSelected(if (isSelected) null else uid)
                            }
                        )
                    }
                }
            }

            // Section 2: Matching Transactions Preview
            if (searchQuery.isNotBlank() || selectedTag != null || selectedTypeFilter != null) {
                item(key = "search_results_card") {
                    OneUiCard {
                        Text(
                            text = "Matching records (${matchingTransactions.size})",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = OneUiTheme.colors.textSecondary
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (matchingTransactions.isEmpty()) {
                            Text(
                                text = "No matching transactions found.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OneUiTheme.colors.textMuted,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        } else {
                            matchingTransactions.take(25).forEachIndexed { idx, tx ->
                                OneUiTransactionRowItem(
                                    item = tx,
                                    categoriesMap = categoriesMap,
                                    colorIndex = idx,
                                    onClick = { onTransactionClick(tx) }
                                )
                                if (idx < matchingTransactions.take(25).lastIndex) {
                                    OneUiInsetDivider(startIndent = 18.dp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Search Input Pill above Keyboard (Exact match to Screenshot 5)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = CircleShape,
                color = OneUiTheme.colors.surfaceElevated
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = OneUiTheme.colors.textPrimary,
                            fontSize = 15.sp
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(OneUiTheme.colors.primaryAccent),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = OneUiTheme.colors.textSecondary,
                                        fontSize = 15.sp
                                    )
                                )
                            }
                            innerTextField()
                        }
                    )

                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = OneUiTheme.colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice Search",
                            tint = OneUiTheme.colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = OneUiTheme.colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
