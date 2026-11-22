package com.moneymanager.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymanager.app.data.local.entity.*
import com.moneymanager.app.ui.theme.OneUiTheme
import java.util.Locale

/**
 * Universal category icon renderer with heuristic keyword matching.
 */
@Composable
fun OneUiCategoryIcon(
    icon: String,
    name: String,
    modifier: Modifier = Modifier,
    tint: Color = OneUiTheme.colors.textPrimary
) {
    val lowerName = name.lowercase(Locale.getDefault())
    val vectorIcon = when {
        lowerName.contains("kolkata") || lowerName.contains("city") || lowerName.contains("location") -> Icons.Default.LocationOn
        lowerName.contains("food") || lowerName.contains("dining") || lowerName.contains("restaurant") -> Icons.Default.Restaurant
        lowerName.contains("transport") || lowerName.contains("cab") || lowerName.contains("car") || lowerName.contains("fuel") -> Icons.Default.DirectionsCar
        lowerName.contains("health") || lowerName.contains("fitness") || lowerName.contains("medical") -> Icons.Default.FavoriteBorder
        lowerName.contains("lifestyle") || lowerName.contains("leisure") || lowerName.contains("entertainment") || lowerName.contains("game") -> Icons.Default.SportsEsports
        lowerName.contains("shopping") || lowerName.contains("cloth") || lowerName.contains("electronic") -> Icons.Default.ShoppingBag
        lowerName.contains("grocer") -> Icons.Default.ShoppingBasket
        lowerName.contains("bill") || lowerName.contains("admin") -> Icons.AutoMirrored.Filled.ReceiptLong
        lowerName.contains("salary") || lowerName.contains("bonus") -> Icons.Default.AccountBalance
        lowerName.contains("invest") -> Icons.AutoMirrored.Filled.TrendingUp
        else -> null
    }

    if (vectorIcon != null) {
        Icon(
            imageVector = vectorIcon,
            contentDescription = name,
            tint = tint,
            modifier = modifier
        )
    } else if (icon.isNotBlank() && icon != "📁") {
        Text(text = icon, fontSize = 20.sp)
    } else {
        Icon(
            imageVector = Icons.Default.Category,
            contentDescription = name,
            tint = tint,
            modifier = modifier
        )
    }
}

/**
 * Multi-select Check Circle (filled accent circle with white check icon, or subtle ring).
 */
@Composable
fun OneUiCheckCircle(
    checked: Boolean,
    enabled: Boolean = true,
    activeColor: Color = OneUiTheme.colors.primaryAccent,
    modifier: Modifier = Modifier
) {
    val alpha = if (enabled) 1f else 0.4f
    if (checked) {
        Box(
            modifier = modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(activeColor.copy(alpha = alpha)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    } else {
        Box(
            modifier = modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(OneUiTheme.colors.surfaceElevated.copy(alpha = alpha))
                .border(1.5.dp, OneUiTheme.colors.textMuted.copy(alpha = 0.45f * alpha), CircleShape)
        )
    }
}

/**
 * One UI 9 Settings-Style Summary Row for Filter Dimensions.
 */
@Composable
fun OneUiFilterSummaryRow(
    icon: ImageVector,
    badgeColor: Color,
    title: String,
    summary: String,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OneUiIconBadge(
            icon = icon,
            backgroundColor = badgeColor,
            size = 38.dp,
            iconSize = 20.dp
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = OneUiTheme.colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (count > 0) OneUiTheme.colors.primaryAccent else OneUiTheme.colors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (count > 0) FontWeight.Medium else FontWeight.Normal
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        if (count > 0) {
            Surface(
                shape = CircleShape,
                color = OneUiTheme.colors.primaryAccent
            ) {
                Text(
                    text = count.toString(),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = OneUiTheme.colors.textMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * One UI 9 Type Toggle Row (Single-row 4 equal-width pills with semantic colors).
 */
@Composable
fun OneUiTypeToggleRow(
    selectedTypes: Set<TransactionType>,
    onToggle: (TransactionType) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        Triple(TransactionType.EXPENSE, "Expense", OneUiTheme.colors.expense),
        Triple(TransactionType.INCOME, "Income", OneUiTheme.colors.income),
        Triple(TransactionType.TRANSFER, "Transfer", OneUiTheme.colors.transfer),
        Triple(TransactionType.ADJUSTMENT, "Adjustment", OneUiTheme.colors.warning)
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { (type, label, semanticColor) ->
            val isSelected = selectedTypes.contains(type)
            val bgColor = if (isSelected) semanticColor else OneUiTheme.colors.surfaceElevated
            val textColor = if (isSelected) Color.White else OneUiTheme.colors.textPrimary

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(CircleShape)
                    .clickable { onToggle(type) },
                shape = CircleShape,
                color = bgColor
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = textColor,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 12.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Shared Bottom Sheet Scaffold for Multi-Criteria Selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneUiMultiSelectSheetScaffold(
    title: String,
    searchHint: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedCount: Int,
    onClearAll: () -> Unit,
    onApply: () -> Unit,
    onDismissRequest: () -> Unit,
    subHeader: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = OneUiTheme.colors.surface,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(OneUiTheme.colors.surface)
        ) {
            // Sheet Header
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
                    onClick = onDismissRequest,
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
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = OneUiTheme.colors.textPrimary,
                            fontSize = 15.sp
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(OneUiTheme.colors.primaryAccent),
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = searchHint,
                                    color = OneUiTheme.colors.textMuted,
                                    fontSize = 15.sp
                                )
                            }
                            inner()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
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

            if (subHeader != null) {
                Spacer(modifier = Modifier.height(6.dp))
                subHeader()
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Scrollable Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                content()
            }

            // Sticky Bottom Pill Bar (Clear & Apply buttons)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = OneUiTheme.colors.surface,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onClearAll),
                            shape = CircleShape,
                            color = OneUiTheme.colors.surfaceElevated
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Clear",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = OneUiTheme.colors.textPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                )
                            }
                        }

                        val applyText = if (selectedCount > 0) "Apply ($selectedCount)" else "Apply"
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onApply),
                            shape = CircleShape,
                            color = OneUiTheme.colors.primaryAccent
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = applyText,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
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

/**
 * Filter Bottom Sheet for Multi-Selecting Accounts.
 * Features categorized banking groups, Select All / Deselect All, and quick filter chips.
 * Archived accounts are hidden per user preference.
 */
@Composable
fun AccountFilterSheet(
    accounts: List<AccountWithCurrency>,
    initialSelectedUids: Set<String>,
    accountTypesMap: Map<String, AccountTypeEntity> = emptyMap(),
    onApply: (Set<String>) -> Unit,
    onDismissRequest: () -> Unit
) {
    var stagedUids by remember { mutableStateOf(initialSelectedUids) }
    var searchQuery by remember { mutableStateOf("") }
    var activeGroupFilter by remember { mutableStateOf("All") }

    val filterChips = remember {
        listOf("All", "Bank", "Cards", "Cash", "Investments", "Other")
    }

    val groupedAccounts = remember(accounts) {
        FilterSummaryFormatter.groupAccountsForFilter(accounts)
    }

    val filteredGroupEntries = remember(groupedAccounts, activeGroupFilter, searchQuery) {
        val query = searchQuery.trim().lowercase(Locale.getDefault())
        groupedAccounts.mapNotNull { (group, list) ->
            val matchesChip = when (activeGroupFilter) {
                "All" -> true
                "Bank" -> group == AccountFilterGroup.BANK
                "Cards" -> group == AccountFilterGroup.CARDS
                "Cash" -> group == AccountFilterGroup.CASH
                "Investments" -> group == AccountFilterGroup.INVEST
                "Other" -> group == AccountFilterGroup.OTHER
                else -> true
            }
            if (!matchesChip) return@mapNotNull null

            val matchingAccounts = if (query.isEmpty()) {
                list
            } else {
                list.filter { it.account.name.lowercase(Locale.getDefault()).contains(query) }
            }

            if (matchingAccounts.isNotEmpty()) {
                group to matchingAccounts
            } else {
                null
            }
        }
    }

    OneUiMultiSelectSheetScaffold(
        title = "Accounts",
        searchHint = "Search accounts...",
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        selectedCount = stagedUids.size,
        onClearAll = { stagedUids = emptySet() },
        onApply = { onApply(stagedUids) },
        onDismissRequest = onDismissRequest,
        subHeader = {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filterChips) { chip ->
                    val isSelected = chip == activeGroupFilter
                    val chipBg = if (isSelected) OneUiTheme.colors.primaryAccent else OneUiTheme.colors.surfaceElevated
                    val chipText = if (isSelected) Color.White else OneUiTheme.colors.textPrimary

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(chipBg)
                            .clickable { activeGroupFilter = chip }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = chip,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = chipText,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 12.5.sp
                            )
                        )
                    }
                }
            }
        }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (filteredGroupEntries.isEmpty()) {
                item(key = "empty_account_state") {
                    Text(
                        text = "No accounts match \"$searchQuery\"",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = OneUiTheme.colors.textMuted,
                            fontSize = 14.sp
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 24.dp)
                    )
                }
            } else {
                filteredGroupEntries.forEach { (group, groupAccs) ->
                    item(key = "header_${group.name}") {
                        val allInGroupSelected = groupAccs.all { stagedUids.contains(it.account.uid) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = group.label.uppercase(Locale.getDefault()),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = OneUiTheme.colors.textSecondary,
                                    letterSpacing = 0.8.sp,
                                    fontSize = 11.sp
                                )
                            )
                            OneUiTextActionLink(
                                text = if (allInGroupSelected) "Deselect all" else "Select all",
                                onClick = {
                                    val groupUids = groupAccs.map { it.account.uid }.toSet()
                                    stagedUids = if (allInGroupSelected) {
                                        stagedUids - groupUids
                                    } else {
                                        stagedUids + groupUids
                                    }
                                }
                            )
                        }
                    }

                    items(groupAccs, key = { it.account.uid }) { accWithCurr ->
                        val isChecked = stagedUids.contains(accWithCurr.account.uid)
                        val (cleanName, baseSubtitle) = getAccountDisplayInfo(accWithCurr.account, accountTypesMap)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    stagedUids = if (isChecked) {
                                        stagedUids - accWithCurr.account.uid
                                    } else {
                                        stagedUids + accWithCurr.account.uid
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OneUiAccountTypeIcon(
                                accountType = accWithCurr.account.accountType,
                                modifier = Modifier.size(24.dp),
                                tint = OneUiTheme.colors.textPrimary
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = cleanName,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = OneUiTheme.colors.textPrimary,
                                        fontSize = 15.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = baseSubtitle,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = OneUiTheme.colors.textSecondary,
                                        fontSize = 12.5.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            OneUiCheckCircle(checked = isChecked)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Filter Bottom Sheet for Multi-Selecting Categories and Subcategories.
 * Features Expense / Income segmentation, expandable subcategories, and search.
 */
@Composable
fun CategoryFilterSheet(
    expenseCategories: List<CategoryWithSubcategories>,
    incomeCategories: List<CategoryWithSubcategories>,
    initialSelectedCategoryUids: Set<String>,
    onApply: (Set<String>) -> Unit,
    onDismissRequest: () -> Unit
) {
    var stagedUids by remember { mutableStateOf(initialSelectedCategoryUids) }
    var searchQuery by remember { mutableStateOf("") }
    var activeType by remember { mutableStateOf(CategoryType.EXPENSE) }
    val expandedParentUids = remember { mutableStateMapOf<String, Boolean>() }

    val currentCategories = if (activeType == CategoryType.EXPENSE) expenseCategories else incomeCategories

    OneUiMultiSelectSheetScaffold(
        title = "Categories",
        searchHint = "Search categories...",
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        selectedCount = stagedUids.size,
        onClearAll = { stagedUids = emptySet() },
        onApply = { onApply(stagedUids) },
        onDismissRequest = onDismissRequest,
        subHeader = {
            if (searchQuery.isBlank()) {
                OneUiPillSegmentedControl(
                    items = listOf(CategoryType.EXPENSE, CategoryType.INCOME),
                    selectedItem = activeType,
                    onItemSelected = { activeType = it },
                    labelProvider = { if (it == CategoryType.EXPENSE) "Expense" else "Income" },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }
        }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (searchQuery.isNotBlank()) {
                // Flattened search results across all categories
                val query = searchQuery.trim().lowercase(Locale.getDefault())
                val allCats = expenseCategories + incomeCategories

                val searchMatches = buildList {
                    allCats.forEach { parentWithSubs ->
                        val pMatches = parentWithSubs.category.name.lowercase(Locale.getDefault()).contains(query)
                        if (pMatches) {
                            add(parentWithSubs.category to null)
                        }
                        parentWithSubs.subcategories.forEach { sub ->
                            if (sub.name.lowercase(Locale.getDefault()).contains(query) || pMatches) {
                                add(sub to parentWithSubs.category.name)
                            }
                        }
                    }
                }.distinctBy { it.first.uid }

                if (searchMatches.isEmpty()) {
                    item(key = "empty_cat_search") {
                        Text(
                            text = "No categories match \"$searchQuery\"",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = OneUiTheme.colors.textMuted,
                                fontSize = 14.sp
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 24.dp)
                        )
                    }
                } else {
                    items(searchMatches, key = { it.first.uid }) { (cat, parentName) ->
                        val isChecked = stagedUids.contains(cat.uid)
                        val title = if (parentName != null) "$parentName / ${cat.name}" else cat.name

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    stagedUids = if (isChecked) stagedUids - cat.uid else stagedUids + cat.uid
                                }
                                .padding(horizontal = 6.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OneUiCategoryIcon(
                                icon = cat.icon,
                                name = cat.name,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = OneUiTheme.colors.textPrimary,
                                    fontSize = 15.sp
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            OneUiCheckCircle(checked = isChecked)
                        }
                    }
                }
            } else {
                // Hierarchical view with inline expandable subcategories
                items(currentCategories, key = { it.category.uid }) { parentWithSubs ->
                    val parent = parentWithSubs.category
                    val subs = parentWithSubs.subcategories
                    val isParentChecked = stagedUids.contains(parent.uid)
                    val isExpanded = expandedParentUids[parent.uid] == true

                    val subtitleText = remember(subs) {
                        when {
                            subs.isEmpty() -> ""
                            subs.size >= 5 -> "${subs.size} subcategories"
                            else -> subs.joinToString(", ") { it.name }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(OneUiTheme.colors.surfaceElevated.copy(alpha = 0.45f))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Category Icon
                            OneUiCategoryIcon(
                                icon = parent.icon,
                                name = parent.name,
                                modifier = Modifier.size(24.dp)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            // Name & Subtitle
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        stagedUids = if (isParentChecked) stagedUids - parent.uid else stagedUids + parent.uid
                                    }
                            ) {
                                Text(
                                    text = parent.name,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = OneUiTheme.colors.textPrimary,
                                        fontSize = 15.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (subtitleText.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = subtitleText,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = OneUiTheme.colors.textSecondary,
                                            fontSize = 12.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Checkbox
                            OneUiCheckCircle(
                                checked = isParentChecked,
                                modifier = Modifier.clickable {
                                    stagedUids = if (isParentChecked) stagedUids - parent.uid else stagedUids + parent.uid
                                }
                            )

                            // Chevron for subcategories
                            if (subs.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = { expandedParentUids[parent.uid] = !isExpanded },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (isExpanded) "Collapse subcategories" else "Expand subcategories",
                                        tint = OneUiTheme.colors.textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Inline Subcategories
                        if (subs.isNotEmpty()) {
                            AnimatedVisibility(
                                visible = isExpanded,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 36.dp, top = 8.dp, bottom = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    subs.forEach { sub ->
                                        val isSubExplicitlyChecked = stagedUids.contains(sub.uid)
                                        val isSubImplicitlyChecked = isParentChecked

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable(enabled = !isParentChecked) {
                                                    stagedUids = if (isSubExplicitlyChecked) {
                                                        stagedUids - sub.uid
                                                    } else {
                                                        stagedUids + sub.uid
                                                    }
                                                }
                                                .padding(vertical = 6.dp, horizontal = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = sub.name,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        color = if (isParentChecked) OneUiTheme.colors.textMuted else OneUiTheme.colors.textPrimary,
                                                        fontSize = 14.sp
                                                    )
                                                )
                                                if (isParentChecked) {
                                                    Text(
                                                        text = "Included via ${parent.name}",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            color = OneUiTheme.colors.primaryAccent,
                                                            fontSize = 11.sp
                                                        )
                                                    )
                                                }
                                            }

                                            OneUiCheckCircle(
                                                checked = isSubExplicitlyChecked || isSubImplicitlyChecked,
                                                enabled = !isParentChecked
                                            )
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
}

/**
 * Filter Bottom Sheet for Multi-Selecting Tags.
 */
@Composable
fun TagFilterSheet(
    tags: List<TagEntity>,
    initialSelectedTagUids: Set<String>,
    onApply: (Set<String>) -> Unit,
    onDismissRequest: () -> Unit
) {
    var stagedUids by remember { mutableStateOf(initialSelectedTagUids) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredTags = remember(tags, searchQuery) {
        val query = searchQuery.trim().lowercase(Locale.getDefault())
        if (query.isEmpty()) {
            tags
        } else {
            tags.filter { it.name.lowercase(Locale.getDefault()).contains(query) }
        }
    }

    OneUiMultiSelectSheetScaffold(
        title = "Tags",
        searchHint = "Search tags...",
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        selectedCount = stagedUids.size,
        onClearAll = { stagedUids = emptySet() },
        onApply = { onApply(stagedUids) },
        onDismissRequest = onDismissRequest
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (filteredTags.isEmpty()) {
                item(key = "empty_tag_state") {
                    Text(
                        text = "No tags found",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = OneUiTheme.colors.textMuted,
                            fontSize = 14.sp
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 24.dp)
                    )
                }
            } else {
                items(filteredTags, key = { it.uid }) { tag ->
                    val isChecked = stagedUids.contains(tag.uid)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                stagedUids = if (isChecked) stagedUids - tag.uid else stagedUids + tag.uid
                            }
                            .padding(horizontal = 6.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "#${tag.name}",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = OneUiTheme.colors.textPrimary,
                                fontSize = 15.sp
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        OneUiCheckCircle(checked = isChecked)
                    }
                }
            }
        }
    }
}
