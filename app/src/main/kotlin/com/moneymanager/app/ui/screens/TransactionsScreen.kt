package com.moneymanager.app.ui.screens

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
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
import com.moneymanager.app.ui.main.DateFilterPreset
import com.moneymanager.app.ui.main.MainViewModel
import com.moneymanager.app.ui.main.TransactionFilterState
import com.moneymanager.app.ui.theme.*
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
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
    val filterState by viewModel.filterState.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    val allAccounts by viewModel.accounts.collectAsState()
    val categoriesMap by viewModel.categoriesMap.collectAsState()

    var activeSubTab by remember { mutableStateOf(DailySubTab.DAILY) }
    var isSearchScreenOpen by remember { mutableStateOf(false) }
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

    // Apply 7-day Date Filter if user clicked a specific day on the strip
    val displayedTransactions = remember(transactions, selectedDateFilter) {
        if (selectedDateFilter == null) {
            transactions
        } else {
            transactions.filter { item ->
                val txDate = Instant.ofEpochMilli(item.transaction.timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                txDate == selectedDateFilter
            }
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
            viewModel = viewModel,
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
                    tint = if (filterState.query.isNotEmpty()) {
                        OneUiTheme.colors.primaryAccent
                    } else {
                        OneUiTheme.colors.textPrimary
                    },
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(
                onClick = { isSearchScreenOpen = true },
                modifier = Modifier.size(38.dp)
            ) {
                Box {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Filter",
                        tint = if (filterState.hasActiveFilters) {
                            OneUiTheme.colors.primaryAccent
                        } else {
                            OneUiTheme.colors.textPrimary
                        },
                        modifier = Modifier.size(22.dp)
                    )
                    if (filterState.hasActiveFilters) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(OneUiTheme.colors.primaryAccent)
                                .align(Alignment.TopEnd)
                        )
                    }
                }
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
            if (filterState.hasActiveFilters) {
                item(key = "active_filters_bar") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (filterState.datePreset != null) {
                            val dateLabel = if (filterState.datePreset == DateFilterPreset.CUSTOM) {
                                val s = filterState.customStartDate?.format(DateTimeFormatter.ofPattern("dd MMM")) ?: ""
                                val e = filterState.customEndDate?.format(DateTimeFormatter.ofPattern("dd MMM")) ?: ""
                                "$s - $e"
                            } else {
                                filterState.datePreset!!.label
                            }
                            OneUiCapsuleChip(
                                label = dateLabel,
                                selected = true,
                                onClick = { viewModel.setDatePreset(null) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            )
                        }
                        if (filterState.query.isNotEmpty()) {
                            OneUiCapsuleChip(
                                label = "\"${filterState.query}\"",
                                selected = true,
                                onClick = { viewModel.setSearchQuery("") },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            )
                        }
                        filterState.selectedTypes.forEach { type ->
                            OneUiCapsuleChip(
                                label = type.displayName,
                                selected = true,
                                onClick = { viewModel.toggleFilterType(type) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            )
                        }
                        if (filterState.selectedAccountUids.isNotEmpty()) {
                            val names = filterState.selectedAccountUids.mapNotNull { uid ->
                                allAccounts.firstOrNull { it.account.uid == uid }?.account?.name
                            }
                            val label = FilterSummaryFormatter.compactChipLabel(names, "account", "accounts")
                            OneUiCapsuleChip(
                                label = label,
                                selected = true,
                                onClick = { viewModel.setFilterAccounts(emptySet()) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear accounts",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            )
                        }
                        if (filterState.selectedCategoryUids.isNotEmpty()) {
                            val names = filterState.selectedCategoryUids.mapNotNull { uid ->
                                categoriesMap[uid]?.name
                            }
                            val label = FilterSummaryFormatter.compactChipLabel(names, "category", "categories")
                            OneUiCapsuleChip(
                                label = label,
                                selected = true,
                                onClick = { viewModel.setFilterCategories(emptySet()) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear categories",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            )
                        }
                        if (filterState.selectedTagUids.isNotEmpty()) {
                            val names = filterState.selectedTagUids.mapNotNull { uid ->
                                allTags.firstOrNull { it.uid == uid }?.name ?: uid
                            }
                            val label = if (names.size == 1) "#${names.first()}" else "${names.size} tags"
                            OneUiCapsuleChip(
                                label = label,
                                selected = true,
                                onClick = { viewModel.setFilterTags(emptySet()) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear tags",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            )
                        }
                        if (filterState.activeFilterCount > 1) {
                            OneUiTextActionLink(
                                text = "Clear all",
                                onClick = { viewModel.clearAllFilters() }
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
                                    notedTxs.take(25).forEachIndexed { idx, item ->
                                        OneUiTransactionRowItem(
                                            item = item,
                                            categoriesMap = categoriesMap,
                                            colorIndex = idx,
                                            showDate = true,
                                            onClick = { onEditTransaction(item) }
                                        )
                                        if (idx < notedTxs.take(25).lastIndex) {
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

private enum class FilterSheetType {
    ACCOUNTS,
    CATEGORIES,
    TAGS
}

/**
 * Dedicated One UI 9 Multi-Criteria Search & Filter Screen.
 * Strictly adheres to Samsung One UI 9 design specifications.
 * Allows filtering simultaneously by Date Range, Type, Account, Category/Subcategory, Tags, and Note query.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OneUiSearchAndFilterScreen(
    viewModel: MainViewModel,
    onTransactionClick: (TransactionWithDetails) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val filterState by viewModel.filterState.collectAsState()
    val matchingTransactions by viewModel.transactions.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    val allAccounts by viewModel.accounts.collectAsState()
    val categoriesMap by viewModel.categoriesMap.collectAsState()
    val expenseCategories by viewModel.expenseCategories.collectAsState()
    val incomeCategories by viewModel.incomeCategories.collectAsState()

    var filtersExpanded by remember { mutableStateOf(true) }
    var activeFilterSheet by remember { mutableStateOf<FilterSheetType?>(null) }
    val focusRequester = remember { FocusRequester() }

    BackHandler(onBack = onBack)

    val selectedAccountNames = remember(filterState.selectedAccountUids, allAccounts) {
        filterState.selectedAccountUids.mapNotNull { uid ->
            allAccounts.firstOrNull { it.account.uid == uid }?.account?.name
        }
    }

    val selectedCategoryNames = remember(filterState.selectedCategoryUids, categoriesMap) {
        filterState.selectedCategoryUids.mapNotNull { uid ->
            categoriesMap[uid]?.name
        }
    }

    val selectedTagNames = remember(filterState.selectedTagUids, allTags) {
        filterState.selectedTagUids.mapNotNull { uid ->
            allTags.firstOrNull { it.uid == uid }?.name ?: uid
        }
    }

    val groupedByDay = remember(matchingTransactions) {
        matchingTransactions.groupBy { item ->
            Instant.ofEpochMilli(item.transaction.timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        }.toSortedMap(compareByDescending { it })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OneUiTheme.colors.background)
            .statusBarsPadding()
            .imePadding()
    ) {
        // Top Bar matching Screenshot 3 ("< Filters" + Clear all)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Back",
                    tint = OneUiTheme.colors.textPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { filtersExpanded = !filtersExpanded }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Filters",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = OneUiTheme.colors.textPrimary,
                        fontSize = 20.sp
                    )
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (filtersExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (filtersExpanded) "Collapse filters" else "Expand filters",
                    tint = OneUiTheme.colors.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (filterState.hasActiveFilters) {
                OneUiTextActionLink(
                    text = "Reset",
                    onClick = { viewModel.clearAllFilters() }
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)
        ) {
            // Section 1: Collapsible One UI 9 Filter Island
            item(key = "filters_card") {
                AnimatedVisibility(
                    visible = filtersExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        // Card 1: Time & Type Quick Selectors
                        OneUiCard {
                            Text(
                                text = "Time",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = OneUiTheme.colors.textSecondary,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val timePresets = listOf(
                                    DateFilterPreset.YESTERDAY,
                                    DateFilterPreset.PAST_7_DAYS,
                                    DateFilterPreset.PAST_30_DAYS,
                                    DateFilterPreset.THIS_MONTH,
                                    DateFilterPreset.CUSTOM
                                )
                                timePresets.forEach { preset ->
                                    val isSelected = filterState.datePreset == preset
                                    OneUiCapsuleChip(
                                        label = preset.label,
                                        selected = isSelected,
                                        onClick = {
                                            viewModel.setDatePreset(if (isSelected) null else preset)
                                        }
                                    )
                                }
                            }

                            // Custom Date Range Pickers (if Custom is selected)
                            if (filterState.datePreset == DateFilterPreset.CUSTOM) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val cal = Calendar.getInstance()
                                    val startStr = filterState.customStartDate?.format(DateTimeFormatter.ofPattern("dd MMM yyyy")) ?: "Start Date"
                                    val endStr = filterState.customEndDate?.format(DateTimeFormatter.ofPattern("dd MMM yyyy")) ?: "End Date"

                                    OneUiPillButton(
                                        text = startStr,
                                        onClick = {
                                            DatePickerDialog(
                                                context,
                                                { _, y, m, d ->
                                                    viewModel.setCustomDateRange(LocalDate.of(y, m + 1, d), filterState.customEndDate)
                                                },
                                                cal.get(Calendar.YEAR),
                                                cal.get(Calendar.MONTH),
                                                cal.get(Calendar.DAY_OF_MONTH)
                                            ).show()
                                        },
                                        containerColor = OneUiTheme.colors.surfaceElevated,
                                        contentColor = OneUiTheme.colors.textPrimary,
                                        modifier = Modifier.weight(1f)
                                    )

                                    Text("to", color = OneUiTheme.colors.textSecondary, fontSize = 12.sp)

                                    OneUiPillButton(
                                        text = endStr,
                                        onClick = {
                                            DatePickerDialog(
                                                context,
                                                { _, y, m, d ->
                                                    viewModel.setCustomDateRange(filterState.customStartDate, LocalDate.of(y, m + 1, d))
                                                },
                                                cal.get(Calendar.YEAR),
                                                cal.get(Calendar.MONTH),
                                                cal.get(Calendar.DAY_OF_MONTH)
                                            ).show()
                                        },
                                        containerColor = OneUiTheme.colors.surfaceElevated,
                                        contentColor = OneUiTheme.colors.textPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            OneUiInsetDivider(startIndent = 0.dp)
                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Type",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = OneUiTheme.colors.textSecondary,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OneUiTypeToggleRow(
                                selectedTypes = filterState.selectedTypes,
                                onToggle = { viewModel.toggleFilterType(it) }
                            )
                        }

                        // Card 2: Detail Criteria (Accounts, Categories, Tags, Note Only)
                        OneUiCard {
                            OneUiFilterSummaryRow(
                                icon = Icons.Default.AccountBalance,
                                badgeColor = Color(0xFF2B75FF),
                                title = "Accounts",
                                summary = FilterSummaryFormatter.summarizeSelection(selectedAccountNames, "All accounts"),
                                count = filterState.selectedAccountUids.size,
                                onClick = { activeFilterSheet = FilterSheetType.ACCOUNTS }
                            )

                            OneUiInsetDivider(startIndent = 52.dp)

                            OneUiFilterSummaryRow(
                                icon = Icons.Default.Category,
                                badgeColor = Color(0xFF5E5CE6),
                                title = "Categories",
                                summary = FilterSummaryFormatter.summarizeSelection(selectedCategoryNames, "All categories"),
                                count = filterState.selectedCategoryUids.size,
                                onClick = { activeFilterSheet = FilterSheetType.CATEGORIES }
                            )

                            if (allTags.isNotEmpty()) {
                                OneUiInsetDivider(startIndent = 52.dp)

                                OneUiFilterSummaryRow(
                                    icon = Icons.Default.LocalOffer,
                                    badgeColor = Color(0xFF00B4D8),
                                    title = "Tags",
                                    summary = FilterSummaryFormatter.summarizeSelection(selectedTagNames, "Any tag"),
                                    count = filterState.selectedTagUids.size,
                                    onClick = { activeFilterSheet = FilterSheetType.TAGS }
                                )
                            }

                            OneUiInsetDivider(startIndent = 0.dp)
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setSearchNoteOnly(!filterState.searchNoteOnly) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Search by note only",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = OneUiTheme.colors.textPrimary,
                                        fontSize = 15.sp
                                    )
                                )
                                OneUiSwitch(
                                    checked = filterState.searchNoteOnly,
                                    onCheckedChange = { viewModel.setSearchNoteOnly(it) }
                                )
                            }
                        }
                    }
                }
            }

            // Compact Active Filter Indicator Bar when filters island is collapsed
            if (!filtersExpanded && filterState.hasActiveFilters) {
                item(key = "collapsed_active_filters_bar") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (filterState.datePreset != null) {
                            val dateLabel = if (filterState.datePreset == DateFilterPreset.CUSTOM) {
                                val s = filterState.customStartDate?.format(DateTimeFormatter.ofPattern("dd MMM")) ?: ""
                                val e = filterState.customEndDate?.format(DateTimeFormatter.ofPattern("dd MMM")) ?: ""
                                "$s - $e"
                            } else {
                                filterState.datePreset!!.label
                            }
                            OneUiCapsuleChip(
                                label = dateLabel,
                                selected = true,
                                onClick = { viewModel.setDatePreset(null) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            )
                        }

                        filterState.selectedTypes.forEach { type ->
                            OneUiCapsuleChip(
                                label = type.displayName,
                                selected = true,
                                onClick = { viewModel.toggleFilterType(type) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            )
                        }

                        if (filterState.selectedAccountUids.isNotEmpty()) {
                            val label = FilterSummaryFormatter.compactChipLabel(selectedAccountNames, "account", "accounts")
                            OneUiCapsuleChip(
                                label = label,
                                selected = true,
                                onClick = { viewModel.setFilterAccounts(emptySet()) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            )
                        }

                        if (filterState.selectedCategoryUids.isNotEmpty()) {
                            val label = FilterSummaryFormatter.compactChipLabel(selectedCategoryNames, "category", "categories")
                            OneUiCapsuleChip(
                                label = label,
                                selected = true,
                                onClick = { viewModel.setFilterCategories(emptySet()) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            )
                        }

                        if (filterState.selectedTagUids.isNotEmpty()) {
                            val label = if (selectedTagNames.size == 1) "#${selectedTagNames.first()}" else "${selectedTagNames.size} tags"
                            OneUiCapsuleChip(
                                label = label,
                                selected = true,
                                onClick = { viewModel.setFilterTags(emptySet()) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // Section 2: Matching Records Section
            item(key = "matching_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Matching records (${matchingTransactions.size})",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = OneUiTheme.colors.textPrimary,
                            fontSize = 16.sp
                        )
                    )
                }
            }

            if (matchingTransactions.isEmpty()) {
                item(key = "empty_matching_state") {
                    OneUiCard {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No transactions match your active filters.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OneUiTheme.colors.textSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                groupedByDay.forEach { (date, dayTxs) ->
                    item(key = "search_day_$date") {
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
                                    onClick = { onTransactionClick(item) }
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

        // Bottom Search Input Pill above Keyboard (Exact match to Screenshot 3 & 5)
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
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (filterState.activeFilterCount > 0) {
                        Surface(
                            shape = CircleShape,
                            color = OneUiTheme.colors.primaryAccent,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { viewModel.clearAllFilters() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${filterState.activeFilterCount} active",
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear filters",
                                    tint = Color.White,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    BasicTextField(
                        value = filterState.query,
                        onValueChange = { viewModel.setSearchQuery(it) },
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
                            if (filterState.query.isEmpty()) {
                                Text(
                                    text = if (filterState.searchNoteOnly) "Search note only..." else "Search transactions...",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = OneUiTheme.colors.textSecondary,
                                        fontSize = 15.sp
                                    )
                                )
                            }
                            innerTextField()
                        }
                    )

                    if (filterState.query.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.setSearchQuery("") },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
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
                        Spacer(modifier = Modifier.width(10.dp))
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

        when (activeFilterSheet) {
            FilterSheetType.ACCOUNTS -> {
                AccountFilterSheet(
                    accounts = allAccounts,
                    initialSelectedUids = filterState.selectedAccountUids,
                    onApply = { uids ->
                        viewModel.setFilterAccounts(uids)
                        activeFilterSheet = null
                    },
                    onDismissRequest = { activeFilterSheet = null }
                )
            }
            FilterSheetType.CATEGORIES -> {
                CategoryFilterSheet(
                    expenseCategories = expenseCategories,
                    incomeCategories = incomeCategories,
                    initialSelectedCategoryUids = filterState.selectedCategoryUids,
                    onApply = { uids ->
                        viewModel.setFilterCategories(uids)
                        activeFilterSheet = null
                    },
                    onDismissRequest = { activeFilterSheet = null }
                )
            }
            FilterSheetType.TAGS -> {
                TagFilterSheet(
                    tags = allTags,
                    initialSelectedTagUids = filterState.selectedTagUids,
                    onApply = { uids ->
                        viewModel.setFilterTags(uids)
                        activeFilterSheet = null
                    },
                    onDismissRequest = { activeFilterSheet = null }
                )
            }
            null -> {}
        }
    }
}
