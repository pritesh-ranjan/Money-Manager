package com.moneymanager.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymanager.app.domain.MonthlyMoneyStory
import com.moneymanager.app.domain.StoryInsightCard
import com.moneymanager.app.domain.StoryInsightType
import com.moneymanager.app.domain.StoryTrendDirection
import com.moneymanager.app.ui.theme.OneUiTheme

private val PositiveGreen = Color(0xFF34C759)
private val NegativeCoral = Color(0xFFFF6353)
private val NeutralMuted = Color(0xFF8E8E93)

/**
 * Compact One UI pull-down indicator pill at the top of the Statistics page.
 */
@Composable
fun MonthlyMoneyStoryPill(
    monthLabel: String,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(OneUiTheme.colors.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "✨",
                    fontSize = 14.sp
                )
                Text(
                    text = if (isExpanded) "$monthLabel (Tap to hide)" else "$monthLabel • Pull down to view",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = OneUiTheme.colors.textPrimary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.5.sp
                    )
                )
            }

            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse story" else "Expand story",
                tint = OneUiTheme.colors.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Full One UI Monthly Money Story Card matching the design mock.
 */
@Composable
fun MonthlyMoneyStoryCard(
    story: MonthlyMoneyStory,
    onCollapse: () -> Unit,
    onInsightClick: (StoryInsightCard) -> Unit,
    modifier: Modifier = Modifier
) {
    OneUiCard(
        modifier = modifier.fillMaxWidth(),
        shape = OneUiCardShape,
        containerColor = OneUiTheme.colors.surfaceElevated
    ) {
        // Header Row: Title + Subtitle + Collapse button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = story.targetMonthLabel,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = OneUiTheme.colors.textPrimary,
                        fontSize = 22.sp,
                        letterSpacing = (-0.3).sp
                    )
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = story.comparisonSubtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = OneUiTheme.colors.textSecondary,
                        fontSize = 14.sp
                    )
                )
            }

            IconButton(
                onClick = onCollapse,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(OneUiTheme.colors.surface)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Collapse Story",
                    tint = OneUiTheme.colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Insight Rows (3-5 items)
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            story.cards.forEach { card ->
                MoneyStoryRow(
                    card = card,
                    onClick = { onInsightClick(card) }
                )
            }
        }
    }
}

@Composable
private fun MoneyStoryRow(
    card: StoryInsightCard,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Icon
        StoryCardIcon(card = card)

        Spacer(modifier = Modifier.width(14.dp))

        // Text Column: Headline + Subtitle
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = card.title,
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
                text = card.subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Normal,
                    color = OneUiTheme.colors.textSecondary,
                    fontSize = 13.5.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StoryCardIcon(card: StoryInsightCard) {
    when (card.type) {
        StoryInsightType.EXPENSE_TREND -> {
            val (icon, tint) = when (card.trendDirection) {
                StoryTrendDirection.POSITIVE -> Pair(Icons.AutoMirrored.Filled.TrendingDown, PositiveGreen)
                StoryTrendDirection.NEGATIVE -> Pair(Icons.AutoMirrored.Filled.TrendingUp, NegativeCoral)
                StoryTrendDirection.NEUTRAL -> Pair(Icons.AutoMirrored.Filled.TrendingFlat, NeutralMuted)
            }
            Icon(
                imageVector = icon,
                contentDescription = "Expense Trend",
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
        }

        StoryInsightType.CATEGORY_TREND -> {
            if (!card.iconEmoji.isNullOrBlank()) {
                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = card.iconEmoji,
                        fontSize = 19.sp
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.ShoppingCart,
                    contentDescription = "Category Spend",
                    tint = OneUiTheme.colors.textPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        StoryInsightType.SAVINGS_RATE -> {
            Icon(
                imageVector = Icons.Default.AccountBalanceWallet,
                contentDescription = "Savings Rate",
                tint = OneUiTheme.colors.textPrimary,
                modifier = Modifier.size(24.dp)
            )
        }

        StoryInsightType.INVESTMENT_RATE -> {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                contentDescription = "Investment Rate",
                tint = OneUiTheme.colors.textPrimary,
                modifier = Modifier.size(24.dp)
            )
        }

        StoryInsightType.INCOME_GROWTH -> {
            val (icon, tint) = when (card.trendDirection) {
                StoryTrendDirection.POSITIVE -> Pair(Icons.AutoMirrored.Filled.TrendingUp, PositiveGreen)
                StoryTrendDirection.NEGATIVE -> Pair(Icons.AutoMirrored.Filled.TrendingDown, NegativeCoral)
                StoryTrendDirection.NEUTRAL -> Pair(Icons.AutoMirrored.Filled.TrendingFlat, NeutralMuted)
            }
            Icon(
                imageVector = icon,
                contentDescription = "Income Growth",
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
