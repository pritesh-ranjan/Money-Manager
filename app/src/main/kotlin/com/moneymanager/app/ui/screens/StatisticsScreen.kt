package com.moneymanager.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymanager.app.data.local.entity.TransactionType
import com.moneymanager.app.data.local.entity.TransactionWithDetails
import com.moneymanager.app.domain.CurrencyFormatter
import com.moneymanager.app.domain.StoryInsightCard
import com.moneymanager.app.ui.components.*
import com.moneymanager.app.ui.main.MainViewModel
import com.moneymanager.app.ui.theme.*
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

enum class StatsPeriodType(val displayName: String) {
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    ANNUALLY("Annually"),
    TOTAL("Total")
}

data class CategoryStatItem(
    val rootCategoryUid: String,
    val rootCategoryName: String,
    val rootCategoryIcon: String,
    val totalAmount: Double,
    val percentage: Double,
    val color: Color,
    val subtitlePreview: String,
    val subcategories: Map<String, Double> = emptyMap(),
    val transactions: List<TransactionWithDetails> = emptyList()
)

data class BarDayBucket(
    val label: String,
    val isHighlighted: Boolean,
    val total: Double,
    val segments: List<Pair<Color, Double>>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: MainViewModel
) {
    val selectedYm by viewModel.selectedYearMonth.collectAsState()
    val allTransactions by viewModel.allTransactionsUnfiltered.collectAsState()
    val categoriesMap by viewModel.categoriesMap.collectAsState()

    var activeType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var periodType by remember { mutableStateOf(StatsPeriodType.MONTHLY) }
    var periodMenuExpanded by remember { mutableStateOf(false) }
    var periodOffset by remember { mutableIntStateOf(0) }
    var selectedCategoryForDetail by remember { mutableStateOf<CategoryStatItem?>(null) }
    var showAllCategoriesInCard by remember { mutableStateOf(false) }

    val monthlyMoneyStory by viewModel.monthlyMoneyStory.collectAsState()
    var isStoryExpanded by remember { mutableStateOf(false) }
    var selectedStoryInsightForDrillDown by remember { mutableStateOf<StoryInsightCard?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            isStoryExpanded = false
        }
    }

    val pullDownNestedScrollConnection = remember(isStoryExpanded) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // When pulling down at the top of the scrollable list
                if (available.y > 25f && !isStoryExpanded) {
                    isStoryExpanded = true
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
        }
    }

    val zone = ZoneId.systemDefault()
    val (periodLabel, startMillis, endMillis) = remember(selectedYm, periodType, periodOffset) {
        when (periodType) {
            StatsPeriodType.MONTHLY -> {
                val targetYm = selectedYm.plusMonths(periodOffset.toLong())
                val start = targetYm.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val end = targetYm.atEndOfMonth().atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
                Triple(targetYm.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())), start, end)
            }
            StatsPeriodType.ANNUALLY -> {
                val baseYear = selectedYm.year + periodOffset
                val start = LocalDate.of(baseYear, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
                val end = LocalDate.of(baseYear, 12, 31).atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
                Triple("Year $baseYear", start, end)
            }
            StatsPeriodType.WEEKLY -> {
                val today = LocalDate.now().plusWeeks(periodOffset.toLong())
                val startOfWeek = today.minusDays((today.dayOfWeek.value % 7).toLong())
                val endOfWeek = startOfWeek.plusDays(6)
                val start = startOfWeek.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = endOfWeek.atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
                val fmt = DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault())
                Triple("${startOfWeek.format(fmt)} – ${endOfWeek.format(fmt)}", start, end)
            }
            StatsPeriodType.TOTAL -> {
                Triple("All Time", 0L, Long.MAX_VALUE)
            }
        }
    }

    val windowTxs = remember(allTransactions, startMillis, endMillis) {
        allTransactions.filter { it.transaction.timestamp in startMillis..endMillis }
    }

    val totalIncome = remember(windowTxs) {
        windowTxs.filter { it.transaction.type == TransactionType.INCOME }.sumOf { it.transaction.baseAmount }
    }
    val totalExpense = remember(windowTxs) {
        windowTxs.filter { it.transaction.type == TransactionType.EXPENSE }.sumOf { it.transaction.baseAmount }
    }

    val categoryStats = remember(windowTxs, activeType, categoriesMap) {
        val targetTxs = windowTxs.filter { it.transaction.type == activeType }
        val grandTotal = targetTxs.sumOf { it.transaction.baseAmount }

        if (grandTotal <= 0) {
            emptyList()
        } else {
            val grouped = targetTxs.groupBy { item ->
                val cat = item.category
                val parentCat = if (cat?.parentUid != null) categoriesMap[cat.parentUid] else null
                when {
                    parentCat != null -> Triple(parentCat.uid, parentCat.name, parentCat.icon)
                    cat != null -> Triple(cat.uid, cat.name, cat.icon)
                    else -> Triple("uncategorized", "Uncategorized", "📁")
                }
            }

            grouped.entries
                .map { (catTriple, txs) ->
                    val sum = txs.sumOf { it.transaction.baseAmount }
                    val pct = (sum / grandTotal) * 100.0

                    val subMap = txs.groupBy { item ->
                        val cat = item.category
                        if (cat?.parentUid != null) cat.name else (item.transaction.note.ifBlank { "General" })
                    }.mapValues { entry -> entry.value.sumOf { it.transaction.baseAmount } }

                    // Build Screenshot 4 style preview ("Chrome, Facebook, Messages, Meet...")
                    val previewItems = txs.mapNotNull { tx ->
                        tx.transaction.note.takeIf { it.isNotBlank() }
                            ?: tx.category?.name?.takeIf { it != catTriple.second }
                            ?: tx.account.name
                    }.distinct().take(4)
                    val previewText = if (previewItems.isNotEmpty()) {
                        previewItems.joinToString(", ")
                    } else {
                        "${txs.size} transactions"
                    }

                    Triple(catTriple, sum to pct, Triple(subMap, txs, previewText))
                }
                .sortedByDescending { it.second.first }
                .mapIndexed { index, (catTriple, sumPct, details) ->
                    val color = StatsPieColors[index % StatsPieColors.size]
                    CategoryStatItem(
                        rootCategoryUid = catTriple.first,
                        rootCategoryName = catTriple.second,
                        rootCategoryIcon = catTriple.third,
                        totalAmount = sumPct.first,
                        percentage = sumPct.second,
                        color = color,
                        subtitlePreview = details.third,
                        subcategories = details.first,
                        transactions = details.second
                    )
                }
        }
    }

    // Build 7-Day Stacked Bar Buckets (Screenshot 3 "Dashboard" style: S M T W (T) F S)
    val barBuckets = remember(windowTxs, categoryStats, activeType) {
        val topCatColorMap = categoryStats.associate { it.rootCategoryUid to it.color }
        val today = LocalDate.now()
        val weekStart = today.minusDays((today.dayOfWeek.value % 7).toLong()) // Sunday start

        (0..6).map { dayOffset ->
            val dayDate = weekStart.plusDays(dayOffset.toLong())
            val dayLetter = dayDate.dayOfWeek.name.first().toString()
            val dayTxs = windowTxs.filter { item ->
                item.transaction.type == activeType &&
                        Instant.ofEpochMilli(item.transaction.timestamp).atZone(zone).toLocalDate() == dayDate
            }
            val dayTotal = dayTxs.sumOf { it.transaction.baseAmount }

            val byCat = dayTxs.groupBy { item ->
                val cat = item.category
                val parentCat = if (cat?.parentUid != null) categoriesMap[cat.parentUid] else null
                parentCat?.uid ?: cat?.uid ?: "uncategorized"
            }.map { (uid, list) ->
                val color = topCatColorMap[uid] ?: Color(0xFF8E8E93)
                color to list.sumOf { it.transaction.baseAmount }
            }.sortedByDescending { it.second }

            BarDayBucket(
                label = dayLetter,
                isHighlighted = dayDate == today,
                total = dayTotal,
                segments = byCat
            )
        }
    }

    val activeTotal = if (activeType == TransactionType.EXPENSE) totalExpense else totalIncome

    OneUiCollapsibleScaffold(
        title = "Financial Wellbeing",
        subtitle = periodLabel,
        expandedHeight = 56.dp, // Sub-screen style top bar matching Screenshot 3 & 4
        collapsedHeight = 56.dp,
        actions = {
            IconButton(
                onClick = {
                    if (periodType == StatsPeriodType.MONTHLY) viewModel.previousMonth() else periodOffset--
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous",
                    tint = OneUiTheme.colors.textPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(
                onClick = {
                    if (periodType == StatsPeriodType.MONTHLY) viewModel.nextMonth() else periodOffset++
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next",
                    tint = OneUiTheme.colors.textPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(
                onClick = {
                    val nextOrd = (periodType.ordinal + 1) % StatsPeriodType.values().size
                    periodType = StatsPeriodType.values()[nextOrd]
                    periodOffset = 0
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = "Toggle Period",
                    tint = OneUiTheme.colors.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Box {
                IconButton(
                    onClick = { periodMenuExpanded = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More Options",
                        tint = OneUiTheme.colors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = periodMenuExpanded,
                    onDismissRequest = { periodMenuExpanded = false },
                    modifier = Modifier.background(OneUiTheme.colors.surfaceElevated)
                ) {
                    StatsPeriodType.values().forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.displayName, color = OneUiTheme.colors.textPrimary) },
                            onClick = {
                                periodType = type
                                periodOffset = 0
                                periodMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(pullDownNestedScrollConnection)
                .background(OneUiTheme.colors.background),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = 96.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 0. Monthly Money Story Reveal Card (Only appears when user pulls down)
            if (isStoryExpanded) {
                monthlyMoneyStory?.let { story ->
                    item(key = "monthly_money_story_card_item") {
                        MonthlyMoneyStoryCard(
                            story = story,
                            onCollapse = { isStoryExpanded = false },
                            onInsightClick = { card ->
                                selectedStoryInsightForDrillDown = card
                            }
                        )
                    }
                }
            }

            // 1. Gradient Insight Banner Card (Screenshot 4 "Build healthy digital habits")
            item(key = "wellbeing_banner_card") {
                OneUiBannerCard(
                    title = "Build healthy financial habits",
                    subtitle = "You'll get category feedback and trends to keep your budget on track."
                )
            }

            // 2. Subheader Row (Screenshot 3: "Today" on left, "Show categories" blue link on right)
            item(key = "period_subheader_row") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$periodLabel (${periodType.displayName})",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = OneUiTheme.colors.textSecondary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    )

                    OneUiTextActionLink(
                        text = if (activeType == TransactionType.EXPENSE) "Show income" else "Show expenses",
                        onClick = {
                            activeType = if (activeType == TransactionType.EXPENSE) {
                                TransactionType.INCOME
                            } else {
                                TransactionType.EXPENSE
                            }
                        }
                    )
                }
            }

            if (categoryStats.isEmpty()) {
                item(key = "empty_stats_card") {
                    OneUiCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No ${activeType.displayName.lowercase()} records for $periodLabel.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OneUiTheme.colors.textSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                // 3. Side-by-Side Hero & Sleek Donut Chart Card (Screenshot 2 "Screen time today")
                item(key = "side_by_side_donut_card") {
                    OneUiCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left Column: Label + Huge Hero Amount (Screenshot 2)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${activeType.displayName} total",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = OneUiTheme.colors.textSecondary,
                                        fontSize = 13.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "₹" + CurrencyFormatter.formatIndianNumber(activeTotal),
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = OneUiTheme.colors.textPrimary,
                                        fontSize = 32.sp
                                    )
                                )
                            }

                            // Right Column: Sleek Segmented Ring / Donut Chart (Screenshot 2)
                            OneUiThickRingChart(
                                categoryStats = categoryStats,
                                modifier = Modifier.size(112.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Top 3 Categories with Vertical Color Bar + Emoji Badge + Name + Amount (Screenshot 2)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            categoryStats.take(3).forEach { catItem ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { selectedCategoryForDetail = catItem }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Vertical colored indicator pill (Screenshot 2)
                                    Box(
                                        modifier = Modifier
                                            .width(3.2.dp)
                                            .height(16.dp)
                                            .clip(CircleShape)
                                            .background(catItem.color)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Rounded square/circle mini icon badge
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(catItem.color.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(catItem.rootCategoryIcon, fontSize = 11.sp)
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Text(
                                        text = catItem.rootCategoryName,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = OneUiTheme.colors.textPrimary,
                                            fontSize = 13.5.sp
                                        ),
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Text(
                                        text = "₹" + CurrencyFormatter.formatIndianNumber(catItem.totalAmount),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = OneUiTheme.colors.textPrimary,
                                            fontWeight = FontWeight.Normal,
                                            fontSize = 13.5.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Segmented Vertical Bar Chart Card (Screenshot 3 "Dashboard" card)
                item(key = "segmented_bar_chart_card") {
                    OneUiCard {
                        Text(
                            text = "₹" + CurrencyFormatter.formatIndianNumber(activeTotal),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = OneUiTheme.colors.textPrimary,
                                fontSize = 30.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Weekly day-by-day breakdown",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = OneUiTheme.colors.textSecondary,
                                fontSize = 13.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Stacked Segmented Rounded Bar Chart with Reference Lines
                        OneUiSegmentedBarChart(
                            buckets = barBuckets,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(175.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Legend Rows with Colored Dots (Screenshot 3)
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            val displayedLegend = if (showAllCategoriesInCard) categoryStats else categoryStats.take(3)
                            displayedLegend.forEach { catItem ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedCategoryForDetail = catItem },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(catItem.color)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = catItem.rootCategoryName,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = OneUiTheme.colors.textSecondary,
                                            fontSize = 13.5.sp
                                        ),
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "₹" + CurrencyFormatter.formatIndianNumber(catItem.totalAmount),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = OneUiTheme.colors.textPrimary,
                                            fontWeight = FontWeight.Normal,
                                            fontSize = 13.5.sp
                                        )
                                    )
                                }
                            }
                        }

                        if (categoryStats.size > 3) {
                            Spacer(modifier = Modifier.height(14.dp))
                            OneUiInsetDivider(startIndent = 0.dp)
                            Spacer(modifier = Modifier.height(8.dp))

                            // Centered "View all" footer button (Screenshot 3)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { showAllCategoriesInCard = !showAllCategoriesInCard }
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (showAllCategoriesInCard) "Show less" else "View all",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = OneUiTheme.colors.textPrimary,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.5.sp
                                    )
                                )
                            }
                        }
                    }
                }

                // 5. "Most used categories" Card with Vertical Colored Bars (Screenshot 2)
                item(key = "most_used_categories_card") {
                    OneUiCard {
                        Text(
                            text = "Most used categories",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = OneUiTheme.colors.textSecondary,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        categoryStats.forEachIndexed { index, item ->
                            OneUiVerticalAccentRow(
                                title = item.rootCategoryName,
                                subtitle = item.subtitlePreview,
                                valueText = "₹" + CurrencyFormatter.formatIndianNumber(item.totalAmount),
                                accentColor = item.color,
                                onClick = { selectedCategoryForDetail = item }
                            )

                            if (index < categoryStats.lastIndex) {
                                OneUiInsetDivider(startIndent = 16.dp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Drill-Down Bottom Sheet
    selectedCategoryForDetail?.let { statItem ->
        OneUiCategoryDrillDownSheet(
            item = statItem,
            onDismiss = { selectedCategoryForDetail = null }
        )
    }

    // Money Story Insight Drill-Down Bottom Sheet
    selectedStoryInsightForDrillDown?.let { insight ->
        OneUiStoryDrillDownSheet(
            insight = insight,
            onDismiss = { selectedStoryInsightForDrillDown = null }
        )
    }
}

/**
 * Clean, Sleek One UI Ring / Donut Chart (Screenshot 2 "Screen time today").
 */
@Composable
fun OneUiThickRingChart(
    categoryStats: List<CategoryStatItem>,
    modifier: Modifier = Modifier
) {
    val trackColor = OneUiTheme.colors.surfaceElevated

    Canvas(modifier = modifier.padding(8.dp)) {
        val strokeWidth = 16.dp.toPx()
        val diameter = size.minDimension - strokeWidth
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)

        // Base ring track
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth)
        )

        var currentAngle = -90f
        categoryStats.forEach { item ->
            val sweep = ((item.percentage.toFloat() / 100f) * 360f).coerceAtLeast(2f)
            drawArc(
                color = item.color,
                startAngle = currentAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
            currentAngle += sweep
        }
    }
}

/**
 * One UI Stacked Segmented Rounded Bar Chart with Reference Lines and Active Day Pill (Screenshot 3).
 */
@Composable
fun OneUiSegmentedBarChart(
    buckets: List<BarDayBucket>,
    modifier: Modifier = Modifier
) {
    val maxVal = remember(buckets) {
        (buckets.maxOfOrNull { it.total } ?: 0.0).coerceAtLeast(1000.0)
    }
    val gridColor = OneUiTheme.colors.divider
    val emptyBarColor = OneUiTheme.colors.surfaceElevated

    Column(modifier = modifier) {
        // Chart Area with Horizontal Reference Lines & Right Labels
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Reference Gridlines (Top & Middle)
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top guideline (maxVal)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = gridColor,
                        thickness = 0.5.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "₹" + CurrencyFormatter.formatIndianNumber(maxVal),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = OneUiTheme.colors.textMuted,
                            fontSize = 10.5.sp
                        )
                    )
                }

                // Middle guideline (maxVal / 2)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = gridColor,
                        thickness = 0.5.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "₹" + CurrencyFormatter.formatIndianNumber(maxVal / 2.0),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = OneUiTheme.colors.textMuted,
                            fontSize = 10.5.sp
                        )
                    )
                }

                // Bottom baseline
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = gridColor,
                        thickness = 0.5.dp
                    )
                    Spacer(modifier = Modifier.width(42.dp))
                }
            }

            // 7 Segmented Rounded Bars (Screenshot 3)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 46.dp, top = 8.dp, bottom = 1.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.Bottom
            ) {
                buckets.forEach { bucket ->
                    val heightFraction = (bucket.total / maxVal).toFloat().coerceIn(0f, 1f)

                    Box(
                        modifier = Modifier
                            .width(14.dp)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        if (heightFraction > 0.02f) {
                            Canvas(
                                modifier = Modifier
                                    .width(14.dp)
                                    .fillMaxHeight(heightFraction.coerceAtLeast(0.08f))
                            ) {
                                val radius = CornerRadius(size.width / 2f, size.width / 2f)
                                val clipCapsule = Path().apply {
                                    addRoundRect(
                                        RoundRect(
                                            rect = Rect(0f, 0f, size.width, size.height),
                                            topLeft = radius,
                                            topRight = radius,
                                            bottomLeft = radius,
                                            bottomRight = radius
                                        )
                                    )
                                }

                                clipPath(clipCapsule) {
                                    var currentBottom = size.height
                                    val bucketSum = bucket.total.coerceAtLeast(1.0)
                                    bucket.segments.forEach { (segColor, segAmt) ->
                                        val segH = ((segAmt / bucketSum).toFloat() * size.height)
                                        drawRect(
                                            color = segColor,
                                            topLeft = Offset(0f, currentBottom - segH),
                                            size = Size(size.width, segH + 1f)
                                        )
                                        currentBottom -= segH
                                    }
                                }
                            }
                        } else {
                            // Subtle baseline stub
                            Box(
                                modifier = Modifier
                                    .width(14.dp)
                                    .height(3.5.dp)
                                    .clip(CircleShape)
                                    .background(emptyBarColor)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // X-Axis Day Labels: S M T W (T) F S (Screenshot 3 signature inverted active day circle)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 46.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            buckets.forEach { bucket ->
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            if (bucket.isHighlighted) OneUiTheme.colors.textPrimary else Color.Transparent
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = bucket.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (bucket.isHighlighted) OneUiTheme.colors.background else OneUiTheme.colors.textMuted,
                            fontWeight = if (bucket.isHighlighted) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.5.sp
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneUiCategoryDrillDownSheet(
    item: CategoryStatItem,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = OneUiDialogShape,
        containerColor = OneUiTheme.colors.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = OneUiTheme.colors.divider) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.rootCategoryName,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = OneUiTheme.colors.textPrimary,
                            fontSize = 22.sp
                        )
                    )
                    Text(
                        text = "${item.transactions.size} transactions • ${String.format(Locale.US, "%.1f%%", item.percentage)} of total",
                        style = MaterialTheme.typography.bodySmall.copy(color = OneUiTheme.colors.textSecondary)
                    )
                }

                Text(
                    text = "₹" + CurrencyFormatter.formatIndianNumber(item.totalAmount),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = item.color,
                        fontSize = 20.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            OneUiInsetDivider()

            if (item.subcategories.size > 1) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Subcategories",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = OneUiTheme.colors.textSecondary,
                        fontWeight = FontWeight.Medium
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                item.subcategories.entries.sortedByDescending { it.value }.forEach { (subName, subAmount) ->
                    val subPct = (subAmount / item.totalAmount) * 100.0
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "$subName (${String.format(Locale.US, "%.0f%%", subPct)})",
                                style = MaterialTheme.typography.bodySmall.copy(color = OneUiTheme.colors.textPrimary)
                            )
                            Text(
                                text = "₹" + CurrencyFormatter.formatIndianNumber(subAmount),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = OneUiTheme.colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        OneUiProgressBar(
                            progress = (subPct / 100.0).toFloat(),
                            height = 3.5.dp,
                            fillColor = item.color
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                OneUiInsetDivider()
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Transactions",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = OneUiTheme.colors.textSecondary,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
            ) {
                items(item.transactions, key = { it.transaction.uid }) { txItem ->
                    val dateStr = Instant.ofEpochMilli(txItem.transaction.timestamp)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                        .format(DateTimeFormatter.ofPattern("dd MMM yyyy"))

                    OneUiVerticalAccentRow(
                        title = txItem.transaction.note.ifBlank { item.rootCategoryName },
                        subtitle = "$dateStr • ${txItem.account.name}",
                        valueText = "₹" + CurrencyFormatter.formatIndianNumber(abs(txItem.transaction.baseAmount)),
                        accentColor = item.color
                    )
                    OneUiInsetDivider(startIndent = 18.dp)
                }
            }
        }
    }
}
