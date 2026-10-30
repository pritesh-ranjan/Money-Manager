package com.moneymanager.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.AccountType
import com.moneymanager.app.domain.CreditCardCycleSummary
import com.moneymanager.app.domain.CurrencyFormatter
import com.moneymanager.app.domain.StatementStatus
import com.moneymanager.app.ui.components.*
import com.moneymanager.app.ui.main.MainViewModel
import com.moneymanager.app.ui.theme.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable
fun CreditCardsScreen(
    viewModel: MainViewModel
) {
    val creditCardSummaries by viewModel.creditCards.collectAsState()
    val allAccounts by viewModel.accounts.collectAsState()
    val allCurrencies by viewModel.allCurrencies.collectAsState()

    var cardToPay by remember { mutableStateOf<CreditCardCycleSummary?>(null) }
    var cardToEdit by remember { mutableStateOf<AccountEntity?>(null) }
    var showAddCardDialog by remember { mutableStateOf(false) }

    val totalStatementDue = creditCardSummaries.sumOf { it.remainingStatementDue }
    val subtitleText = "${creditCardSummaries.size} cards • Statement due: ₹${CurrencyFormatter.formatIndianNumber(totalStatementDue)}"

    OneUiCollapsibleScaffold(
        title = "Cards",
        subtitle = subtitleText,
        expandedHeight = 175.dp,
        centeredExpandedTitle = true,
        actions = {
            IconButton(
                onClick = { showAddCardDialog = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Credit Card",
                    tint = OneUiTheme.colors.textPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(OneUiTheme.colors.background),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding() + 6.dp,
                bottom = 96.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (creditCardSummaries.isEmpty()) {
                item(key = "empty_cards_card") {
                    OneUiCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp, horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            OneUiIconBadge(
                                icon = Icons.Default.CreditCard,
                                backgroundColor = OneUiTheme.colors.badgeIndigo,
                                size = 48.dp,
                                iconSize = 24.dp
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "No credit cards configured",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = OneUiTheme.colors.textPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Add a credit card with statement & payment due dates to track billing cycles and credit utilization.",
                                style = MaterialTheme.typography.bodySmall,
                                color = OneUiTheme.colors.textSecondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            OneUiPillButton(
                                text = "Add credit card",
                                onClick = { showAddCardDialog = true },
                                containerColor = OneUiTheme.colors.primaryAccent,
                                contentColor = Color.White
                            )
                        }
                    }
                }
            } else {
                // 1. Overview "App Timers" Style Card (Direct match to Screenshot 4 "App timers" card)
                item(key = "card_utilization_overview") {
                    OneUiCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Credit utilization & cycle status",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = OneUiTheme.colors.textSecondary,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            )

                            OneUiTextActionLink(
                                text = "Add card",
                                onClick = { showAddCardDialog = true },
                                showChevron = true,
                                color = OneUiTheme.colors.textPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        creditCardSummaries.forEachIndexed { index, summary ->
                            val utilPct = summary.creditUtilizationPercent ?: 0
                            val progress = (utilPct / 100f).coerceIn(0.03f, 1f)
                            val statusLabel = when {
                                summary.remainingStatementDue <= 0.01 -> "No bill due today"
                                summary.daysUntilDue >= 0 -> "Due in ${summary.daysUntilDue}d • ₹${CurrencyFormatter.formatIndianNumber(summary.remainingStatementDue)}"
                                else -> "Overdue ${abs(summary.daysUntilDue)}d"
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        cardToEdit = allAccounts.firstOrNull { it.account.uid == summary.accountUid }?.account
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Monochrome/Tinted Circle Icon (Screenshot 4 "App timers" left icon)
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(OneUiTheme.colors.surfaceElevated),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CreditCard,
                                        contentDescription = null,
                                        tint = OneUiTheme.colors.textPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Text(
                                    text = summary.cardName,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = OneUiTheme.colors.textSecondary,
                                        fontSize = 15.sp
                                    ),
                                    modifier = Modifier.weight(0.45f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                // Right Column: Status Text + Thin Progress Track (Screenshot 4)
                                Column(
                                    modifier = Modifier.weight(0.55f),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = statusLabel,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            color = OneUiTheme.colors.textPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Normal
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(5.dp))
                                    OneUiProgressBar(
                                        progress = progress,
                                        height = 3.5.dp,
                                        fillColor = if (utilPct > 70) OneUiTheme.colors.expense else OneUiTheme.colors.primaryAccent
                                    )
                                }
                            }

                            if (index < creditCardSummaries.lastIndex) {
                                OneUiInsetDivider(startIndent = 48.dp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Bottom Action Row ("+ Add new credit card" + "Pay bill" pill button, matching Screenshot 4)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { showAddCardDialog = true }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(OneUiTheme.colors.surfaceElevated),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = OneUiTheme.colors.textPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Add new card",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = OneUiTheme.colors.textSecondary,
                                        fontSize = 13.sp
                                    )
                                )
                            }

                            val firstPayable = creditCardSummaries.firstOrNull { it.remainingStatementDue > 0.01 }
                                ?: creditCardSummaries.firstOrNull()
                            if (firstPayable != null) {
                                OneUiPillButton(
                                    text = "Pay bill",
                                    onClick = { cardToPay = firstPayable }
                                )
                            }
                        }
                    }
                }

                // 2. Individual Card Statement Breakdown Cards
                items(creditCardSummaries, key = { it.accountUid }) { summary ->
                    OneUiCreditCardStatementCard(
                        summary = summary,
                        onPayBill = { cardToPay = summary },
                        onEditCard = {
                            cardToEdit = allAccounts.firstOrNull { it.account.uid == summary.accountUid }?.account
                        }
                    )
                }
            }
        }
    }

    // Add New Card Dialog (pre-selected as CREDIT_CARD)
    if (showAddCardDialog) {
        OneUiAddEditAccountDialog(
            existingAccount = null,
            defaultAccountType = AccountType.CREDIT_CARD,
            currencies = allCurrencies,
            existingAccounts = allAccounts.map { it.account },
            onDismiss = { showAddCardDialog = false },
            onSave = { newCard ->
                viewModel.saveAccount(newCard.copy(accountType = AccountType.CREDIT_CARD))
                showAddCardDialog = false
            }
        )
    }

    // Edit Card Dialog
    cardToEdit?.let { card ->
        OneUiAddEditAccountDialog(
            existingAccount = card,
            currencies = allCurrencies,
            existingAccounts = allAccounts.map { it.account },
            onDismiss = { cardToEdit = null },
            onSave = { updatedCard ->
                viewModel.saveAccount(updatedCard)
                cardToEdit = null
            },
            onDelete = { cardToDelete ->
                viewModel.deleteAccount(cardToDelete)
                cardToEdit = null
            }
        )
    }

    // Bill Payment Dialog with Radio Buttons & Chunky Slider
    cardToPay?.let { summary ->
        val bankAccounts = allAccounts.filter { it.account.accountType in listOf(AccountType.SAVINGS, AccountType.CASH, AccountType.UPI_DEBIT) }
        OneUiPayBillDialog(
            summary = summary,
            availableBankAccounts = bankAccounts.map { it.account },
            onDismiss = { cardToPay = null },
            onConfirm = { fromAccountUid, amount ->
                viewModel.payCreditCard(summary, fromAccountUid, amount)
                cardToPay = null
            }
        )
    }
}

@Composable
fun OneUiCreditCardStatementCard(
    summary: CreditCardCycleSummary,
    onPayBill: () -> Unit,
    onEditCard: () -> Unit
) {
    val dateFormatter = DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault())
    val statusColor = when (summary.status) {
        StatementStatus.PAID -> OneUiTheme.colors.income
        StatementStatus.DUE_SOON -> OneUiTheme.colors.warning
        StatementStatus.OVERDUE -> OneUiTheme.colors.expense
        StatementStatus.UNPAID -> OneUiTheme.colors.primaryAccent
        StatementStatus.NO_BILL -> OneUiTheme.colors.textSecondary
    }

    OneUiCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = summary.cardName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = OneUiTheme.colors.textPrimary,
                            fontSize = 18.sp
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = onEditCard,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Card",
                            tint = OneUiTheme.colors.textMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Text(
                    text = "Statement: ${summary.lastStatementDate.format(dateFormatter)} • Due: ${summary.paymentDueDate.format(dateFormatter)}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = OneUiTheme.colors.primaryAccent,
                        fontSize = 12.sp
                    )
                )
            }

            // Capsule Status Badge
            Surface(
                color = OneUiTheme.colors.surfaceElevated,
                shape = CircleShape
            ) {
                Text(
                    text = summary.status.label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        fontSize = 11.sp
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        OneUiInsetDivider()

        // Vertical Colored Accent Rows (Screenshot 4 style)
        OneUiVerticalAccentRow(
            title = "Statement Remaining Due",
            subtitle = if (summary.daysUntilDue >= 0) "${summary.daysUntilDue} days left to pay" else "Overdue by ${abs(summary.daysUntilDue)} days",
            valueText = "₹" + CurrencyFormatter.formatIndianNumber(summary.remainingStatementDue),
            valueColor = if (summary.remainingStatementDue > 0) OneUiTheme.colors.expense else OneUiTheme.colors.income,
            accentColor = OneUiTheme.colors.primaryAccent
        )

        OneUiInsetDivider(startIndent = 18.dp)

        OneUiVerticalAccentRow(
            title = "Unbilled Spends",
            subtitle = "Current cycle transactions",
            valueText = "₹" + CurrencyFormatter.formatIndianNumber(summary.unbilledAmount),
            accentColor = OneUiTheme.colors.secondaryCyan
        )

        OneUiInsetDivider(startIndent = 18.dp)

        OneUiVerticalAccentRow(
            title = "Total Outstanding",
            subtitle = summary.creditLimit?.let { "Limit: ₹${CurrencyFormatter.formatIndianNumber(it)} (${summary.creditUtilizationPercent ?: 0}% used)" } ?: "Combined statement & unbilled",
            valueText = "₹" + CurrencyFormatter.formatIndianNumber(summary.totalOutstanding),
            accentColor = OneUiTheme.colors.income
        )

        if (summary.remainingStatementDue > 0.01 || summary.totalOutstanding > 0.01) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OneUiPillButton(
                    text = "Pay bill",
                    onClick = onPayBill,
                    containerColor = OneUiTheme.colors.primaryAccent,
                    contentColor = Color.White
                )
            }
        }
    }
}

@Composable
fun OneUiPayBillDialog(
    summary: CreditCardCycleSummary,
    availableBankAccounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onConfirm: (fromAccountUid: String, amount: Double) -> Unit
) {
    var selectedFromAccountUid by remember {
        mutableStateOf(summary.settlementAccountUid ?: availableBankAccounts.firstOrNull()?.uid ?: "")
    }

    val maxPayable = maxOf(summary.remainingStatementDue, summary.totalOutstanding, 1000.0)
    var sliderValue by remember {
        mutableFloatStateOf(summary.remainingStatementDue.coerceAtLeast(0.0).toFloat())
    }
    var paymentAmountStr by remember {
        mutableStateOf(String.format(Locale.getDefault(), "%.2f", summary.remainingStatementDue.coerceAtLeast(0.0)))
    }

    OneUiBottomDialog(
        onDismissRequest = onDismiss,
        title = "Pay ${summary.cardName}",
        confirmButtonText = "Pay Bill",
        onConfirm = {
            val amt = paymentAmountStr.toDoubleOrNull()
            if (amt != null && amt > 0 && selectedFromAccountUid.isNotBlank()) {
                onConfirm(selectedFromAccountUid, amt)
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Pay From Account",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = OneUiTheme.colors.textSecondary,
                    fontWeight = FontWeight.Medium
                )
            )

            // One UI Radio Button Account Selection List
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(OneUiTheme.colors.surfaceElevated)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                availableBankAccounts.forEachIndexed { idx, acc ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedFromAccountUid = acc.uid }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OneUiRadioButton(
                            selected = selectedFromAccountUid == acc.uid,
                            onClick = { selectedFromAccountUid = acc.uid }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = acc.name,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = OneUiTheme.colors.textPrimary,
                                fontWeight = FontWeight.Normal,
                                fontSize = 15.sp
                            )
                        )
                    }
                    if (idx < availableBankAccounts.lastIndex) {
                        OneUiInsetDivider(startIndent = 34.dp)
                    }
                }
            }

            // Payment Amount Field
            TextField(
                value = paymentAmountStr,
                onValueChange = {
                    paymentAmountStr = it
                    it.toFloatOrNull()?.let { f ->
                        sliderValue = f.coerceIn(0f, maxPayable.toFloat())
                    }
                },
                label = { Text("Payment Amount (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = OneUiTheme.colors.textPrimary,
                    unfocusedTextColor = OneUiTheme.colors.textPrimary,
                    focusedContainerColor = OneUiTheme.colors.surfaceElevated,
                    unfocusedContainerColor = OneUiTheme.colors.surfaceElevated,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // One UI Sleek Slider
            OneUiSlider(
                value = sliderValue,
                onValueChange = { f ->
                    sliderValue = f
                    paymentAmountStr = String.format(Locale.getDefault(), "%.2f", f)
                },
                valueRange = 0f..maxPayable.toFloat(),
                activeColor = OneUiTheme.colors.primaryAccent
            )

            // Quick-Snap Capsule Chips (Screenshot 5 style)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OneUiCapsuleChip(
                    label = "Statement Due",
                    selected = abs(sliderValue - summary.remainingStatementDue.toFloat()) < 1f,
                    onClick = {
                        val due = summary.remainingStatementDue.toFloat()
                        sliderValue = due
                        paymentAmountStr = String.format(Locale.getDefault(), "%.2f", due)
                    },
                    modifier = Modifier.weight(1f)
                )

                OneUiCapsuleChip(
                    label = "Total Outstanding",
                    selected = abs(sliderValue - summary.totalOutstanding.toFloat()) < 1f,
                    onClick = {
                        val total = summary.totalOutstanding.toFloat()
                        sliderValue = total
                        paymentAmountStr = String.format(Locale.getDefault(), "%.2f", total)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
