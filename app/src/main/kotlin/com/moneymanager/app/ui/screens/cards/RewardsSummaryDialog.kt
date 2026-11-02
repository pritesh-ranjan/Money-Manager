package com.moneymanager.app.ui.screens.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymanager.app.data.local.entity.CardStatementSummaryRecord
import com.moneymanager.app.domain.CurrencyFormatter
import com.moneymanager.app.ui.components.OneUiBottomDialog
import com.moneymanager.app.ui.theme.OneUiTheme

@Composable
fun RewardsSummaryDialog(
    statements: List<CardStatementSummaryRecord>,
    onDismiss: () -> Unit
) {
    OneUiBottomDialog(
        onDismissRequest = onDismiss,
        title = "Cashback & Card Rewards",
        confirmButtonText = "Done",
        onConfirm = onDismiss
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Hero Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(OneUiTheme.colors.surfaceElevated)
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(OneUiTheme.colors.primaryAccentContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CardGiftcard,
                            contentDescription = null,
                            tint = OneUiTheme.colors.warning,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Imported Statement Rewards",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = OneUiTheme.colors.textPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Rewards & cashback balances detected on your official statements",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = OneUiTheme.colors.textSecondary,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }

            if (statements.isEmpty()) {
                Text(
                    text = "No statements imported yet. Connect your Gmail account to auto-extract reward points and cashbacks.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = OneUiTheme.colors.textSecondary
                    ),
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                statements.forEach { stmt ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(OneUiTheme.colors.surfaceElevated)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CreditCardBrandEmblem(
                            bankName = stmt.bankName,
                            size = 36.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stmt.bankName,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = OneUiTheme.colors.textPrimary
                                )
                            )
                            Text(
                                text = "•• ${stmt.accountOrCardNumberMasked.takeLast(4)} • Cycle ${stmt.statementYearMonth}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = OneUiTheme.colors.textSecondary,
                                    fontSize = 12.sp
                                )
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Statement Billed",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = OneUiTheme.colors.textSecondary,
                                    fontSize = 11.sp
                                )
                            )
                            Text(
                                text = "₹${CurrencyFormatter.formatIndianNumber(stmt.statementTotalDue)}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = OneUiTheme.colors.textPrimary
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
