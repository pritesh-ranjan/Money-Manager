package com.moneymanager.app.ui.screens.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.moneymanager.app.data.local.entity.AccountType
import com.moneymanager.app.data.local.entity.AccountWithCurrency
import com.moneymanager.app.data.local.entity.CardStatementSummaryRecord
import com.moneymanager.app.data.local.entity.InstitutionEntity
import com.moneymanager.app.domain.cards.CreditCardMappingStorage
import com.moneymanager.app.ui.components.*
import com.moneymanager.app.ui.main.MainViewModel
import com.moneymanager.app.ui.theme.OneUiTheme

data class SyncedCardItem(
    val bankId: String,
    val bankName: String,
    val cardMasked: String,
    val statementCount: Int,
    val latestYearMonth: String,
    val mappedAccountUid: String?
)

@Composable
fun CardMappingDialog(
    viewModel: MainViewModel,
    statements: List<CardStatementSummaryRecord>,
    allAccounts: List<AccountWithCurrency>,
    institutionsMap: Map<String, InstitutionEntity> = emptyMap(),
    initialSelectedCardKey: String? = null,
    onDismiss: () -> Unit,
    onAddNewCardAccount: (SyncedCardItem) -> Unit
) {
    val context = LocalContext.current
    val cardMappings by viewModel.cardMappings.collectAsState()
    val accountTypesMap by viewModel.accountTypesMap.collectAsState()
    val creditCardAccounts = remember(allAccounts) {
        allAccounts.filter { it.account.accountType == AccountType.CREDIT_CARD }
    }

    // Group statements by unique card: (bankId, cardMasked)
    val syncedCards = remember(statements, cardMappings) {
        statements.groupBy { Pair(it.bankId, it.accountOrCardNumberMasked) }
            .map { (pair, stmtList) ->
                val (bankId, cardMasked) = pair
                val latest = stmtList.maxByOrNull { it.statementDateEpochDay }
                val mappedUid = CreditCardMappingStorage.getMappedAccountUid(
                    context,
                    bankId,
                    cardMasked
                )
                SyncedCardItem(
                    bankId = bankId,
                    bankName = latest?.bankName ?: bankId,
                    cardMasked = cardMasked,
                    statementCount = stmtList.size,
                    latestYearMonth = latest?.statementYearMonth ?: "",
                    mappedAccountUid = mappedUid
                )
            }
            .sortedByDescending { it.statementCount }
    }

    // Selected card to map/link
    var cardToPickAccountFor by remember {
        mutableStateOf(
            if (initialSelectedCardKey != null) {
                syncedCards.firstOrNull {
                    CreditCardMappingStorage.buildCardKey(it.bankId, it.cardMasked) == initialSelectedCardKey ||
                        it.cardMasked == initialSelectedCardKey
                }
            } else null
        )
    }

    OneUiBottomDialog(
        onDismissRequest = onDismiss,
        title = "Card Account Mappings",
        confirmButtonText = "Done",
        onConfirm = onDismiss,
        dismissButtonText = "Close"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Map cards found via Gmail statement sync to your app credit card accounts so statement transactions reconcile against your recorded ledger.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = OneUiTheme.colors.textSecondary,
                    fontSize = 12.5.sp
                )
            )

            if (syncedCards.isEmpty()) {
                OneUiCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreditCard,
                            contentDescription = null,
                            tint = OneUiTheme.colors.textMuted,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "No synced cards found yet",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = OneUiTheme.colors.textPrimary
                            )
                        )
                        Text(
                            text = "Import statement PDFs or connect Gmail to detect card statements.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = OneUiTheme.colors.textSecondary,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(syncedCards, key = { "${it.bankId}_${it.cardMasked}" }) { item ->
                        val isMapped = !item.mappedAccountUid.isNullOrBlank()
                        val mappedAccount = if (isMapped) {
                            creditCardAccounts.firstOrNull { it.account.uid == item.mappedAccountUid }
                        } else null

                        OneUiCard(
                            onClick = { cardToPickAccountFor = item },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CreditCardBrandEmblem(
                                    bankName = item.bankName,
                                    size = 40.dp
                                )

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.bankName,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = OneUiTheme.colors.textPrimary,
                                            fontSize = 15.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    val cleanDigits = item.cardMasked.replace("[^0-9]".toRegex(), "")
                                    val last4Text = if (cleanDigits.length >= 4) "•• ${cleanDigits.takeLast(4)}" else item.cardMasked
                                    Text(
                                        text = "$last4Text • ${item.statementCount} statement${if (item.statementCount > 1) "s" else ""}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = OneUiTheme.colors.textSecondary,
                                            fontSize = 12.sp
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Linked Account Status Badge / Selector
                                    Surface(
                                        color = if (mappedAccount != null) {
                                            OneUiTheme.colors.incomeContainer
                                        } else {
                                            OneUiTheme.colors.surfaceElevated
                                        },
                                        shape = CircleShape
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (mappedAccount != null) Icons.Default.Link else Icons.Default.CreditCard,
                                                contentDescription = null,
                                                tint = if (mappedAccount != null) OneUiTheme.colors.income else OneUiTheme.colors.textSecondary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = mappedAccount?.account?.name?.let { "Mapped: $it" } ?: "Tap to map credit card",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = if (mappedAccount != null) OneUiTheme.colors.income else OneUiTheme.colors.primaryAccent,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 11.5.sp
                                                )
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                OneUiPillButton(
                                    text = if (isMapped) "Change" else "Link",
                                    onClick = { cardToPickAccountFor = item },
                                    containerColor = if (isMapped) OneUiTheme.colors.surfaceElevated else OneUiTheme.colors.primaryAccent,
                                    contentColor = if (isMapped) OneUiTheme.colors.textPrimary else Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Account Picker Sheet when a card is selected
    cardToPickAccountFor?.let { targetCard ->
        val cleanDigits = targetCard.cardMasked.replace("[^0-9]".toRegex(), "")
        val last4Text = if (cleanDigits.length >= 4) "•• ${cleanDigits.takeLast(4)}" else targetCard.cardMasked

        OneUiAccountSelectionDialog(
            title = "Map ${targetCard.bankName} Card ($last4Text)",
            accounts = allAccounts,
            selectedUid = targetCard.mappedAccountUid ?: "",
            accountTypesMap = accountTypesMap,
            institutionsMap = institutionsMap,
            typeColor = OneUiTheme.colors.primaryAccent,
            initialFilter = "Cards",
            allowedAccountTypes = setOf(AccountType.CREDIT_CARD),
            allowNoneOption = true,
            noneOptionLabel = "None (Auto-match by name)",
            onDismissRequest = { cardToPickAccountFor = null },
            onSelect = { uid ->
                if (uid.isBlank()) {
                    viewModel.removeCardMapping(targetCard.bankId, targetCard.cardMasked)
                } else {
                    viewModel.setCardMapping(targetCard.bankId, targetCard.cardMasked, uid)
                }
                cardToPickAccountFor = null
            },
            onAddNewAccount = { _ ->
                cardToPickAccountFor = null
                onAddNewCardAccount(targetCard)
            }
        )
    }
}
