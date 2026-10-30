package com.moneymanager.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import com.moneymanager.app.data.local.entity.TransactionType
import com.moneymanager.app.domain.CurrencyFormatter
import com.moneymanager.app.domain.MonthlyMoneyStoryEngine
import com.moneymanager.app.domain.StoryInsightCard
import com.moneymanager.app.domain.StoryTrendDirection
import com.moneymanager.app.ui.theme.OneUiTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneUiStoryDrillDownSheet(
    insight: StoryInsightCard,
    onDismiss: () -> Unit
) {
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
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = insight.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = OneUiTheme.colors.textPrimary,
                            fontSize = 18.sp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = insight.subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = OneUiTheme.colors.textSecondary,
                            fontSize = 13.sp
                        )
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(OneUiTheme.colors.surfaceElevated)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = OneUiTheme.colors.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            OneUiInsetDivider(startIndent = 0.dp)
            Spacer(modifier = Modifier.height(14.dp))

            // Transaction Summary Header
            val txCount = insight.underlyingTransactions.size
            val totalAmount = insight.underlyingTransactions.sumOf { abs(it.transaction.baseAmount) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Transactions ($txCount)",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = OneUiTheme.colors.textSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                )

                Text(
                    text = "Total: ₹" + MonthlyMoneyStoryEngine.formatStoryAmount(totalAmount),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = OneUiTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (insight.underlyingTransactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No direct transactions available for this period.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OneUiTheme.colors.textSecondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(insight.underlyingTransactions, key = { it.transaction.uid }) { txItem ->
                        val dateStr = Instant.ofEpochMilli(txItem.transaction.timestamp)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                            .format(DateTimeFormatter.ofPattern("dd MMM yyyy"))

                        val title = txItem.transaction.note.ifBlank {
                            txItem.category?.name ?: txItem.transaction.type.displayName
                        }

                        val accentColor = when (txItem.transaction.type) {
                            TransactionType.INCOME -> Color(0xFF34C759)
                            TransactionType.EXPENSE -> Color(0xFFFF5252)
                            TransactionType.TRANSFER -> OneUiTheme.colors.primaryAccent
                            TransactionType.ADJUSTMENT -> Color(0xFF8E8E93)
                        }

                        val amountPrefix = when (txItem.transaction.type) {
                            TransactionType.INCOME -> "+₹"
                            TransactionType.EXPENSE -> "-₹"
                            TransactionType.TRANSFER -> "₹"
                            TransactionType.ADJUSTMENT -> "₹"
                        }

                        OneUiVerticalAccentRow(
                            title = title,
                            subtitle = "$dateStr • ${txItem.account.name}" + (txItem.toAccount?.let { " → ${it.name}" } ?: ""),
                            valueText = amountPrefix + MonthlyMoneyStoryEngine.formatStoryAmount(abs(txItem.transaction.baseAmount)),
                            accentColor = accentColor
                        )

                        OneUiInsetDivider(startIndent = 16.dp)
                    }
                }
            }
        }
    }
}
