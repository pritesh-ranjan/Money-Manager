package com.moneymanager.app.ui.screens.gmail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymanager.app.domain.gmail.filter.GmailSyncRules
import com.moneymanager.app.domain.gmail.filter.SupportedBank
import com.moneymanager.app.ui.components.*
import com.moneymanager.app.ui.theme.OneUiTheme

@Composable
fun GmailSyncRulesDialog(
    initialRules: GmailSyncRules,
    onSaveRules: (GmailSyncRules) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf(initialRules.baseSearchQuery) }
    var enabledBanks by remember { mutableStateOf(initialRules.enabledBanks.toSet()) }
    var enabledCardParsers by remember { mutableStateOf(initialRules.enabledCardParserIds.toSet()) }
    var ignoredSubjects by remember { mutableStateOf(initialRules.ignoredSubjectKeywords.toMutableList()) }
    var newIgnoreKeywordInput by remember { mutableStateOf("") }

    val bankDisplayNames = remember {
        mapOf(
            SupportedBank.HDFC.name to "HDFC Bank",
            SupportedBank.ICICI.name to "ICICI Bank",
            SupportedBank.AXIS.name to "Axis Bank",
            SupportedBank.BANK_OF_BARODA.name to "Bank of Baroda",
            SupportedBank.FEDERAL_BANK.name to "Federal Bank"
        )
    }

    val cardParserDisplayNames = remember {
        mapOf(
            "HDFC_SWIGGY_CC" to "HDFC Swiggy Credit Card",
            "ICICI_AMAZON_PAY_CC" to "ICICI Amazon Pay Credit Card",
            "AXIS_BANK_CC" to "Axis Bank Credit Card"
        )
    }

    OneUiBottomDialog(
        onDismissRequest = onDismiss,
        title = "Sync Rules & Filters",
        confirmButtonText = "Save Rules",
        onConfirm = {
            val updated = initialRules.copy(
                baseSearchQuery = searchQuery.trim().ifBlank { GmailSyncRules.DEFAULT_SEARCH_QUERY },
                enabledBanks = enabledBanks,
                enabledCardParserIds = enabledCardParsers,
                ignoredSubjectKeywords = ignoredSubjects.map { it.trim() }.filter { it.isNotBlank() }.distinct()
            )
            onSaveRules(updated)
            onDismiss()
        },
        dismissButtonText = "Cancel"
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Search Criteria
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Search Query Criteria",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = OneUiTheme.colors.textPrimary
                            )
                        )
                        TextButton(
                            onClick = { searchQuery = GmailSyncRules.DEFAULT_SEARCH_QUERY },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = OneUiTheme.colors.primaryAccent
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset", fontSize = 12.sp, color = OneUiTheme.colors.primaryAccent)
                        }
                    }

                    Text(
                        text = "Base query used when querying the Gmail API. 'after:YYYY/MM/01' is appended automatically based on your starting month.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = OneUiTheme.colors.textSecondary,
                            fontSize = 11.5.sp
                        )
                    )

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = OneUiTheme.colors.primaryAccent,
                            unfocusedBorderColor = OneUiTheme.colors.surfaceElevated,
                            focusedContainerColor = OneUiTheme.colors.surfaceElevated,
                            unfocusedContainerColor = OneUiTheme.colors.surfaceElevated,
                            focusedTextColor = OneUiTheme.colors.textPrimary,
                            unfocusedTextColor = OneUiTheme.colors.textPrimary
                        ),
                        singleLine = true
                    )
                }
            }

            // 2. Supported Banks Toggles
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Supported Banks (${enabledBanks.size}/${bankDisplayNames.size})",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = OneUiTheme.colors.textPrimary
                        )
                    )

                    OneUiCard(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Column {
                            bankDisplayNames.entries.forEachIndexed { index, (bankKey, bankName) ->
                                val isChecked = enabledBanks.contains(bankKey)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = bankName,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = OneUiTheme.colors.textPrimary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                    OneUiSwitch(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            enabledBanks = if (checked) {
                                                enabledBanks + bankKey
                                            } else {
                                                enabledBanks - bankKey
                                            }
                                        }
                                    )
                                }
                                if (index < bankDisplayNames.size - 1) {
                                    OneUiInsetDivider(startIndent = 0.dp)
                                }
                            }
                        }
                    }
                }
            }

            // 3. Supported Card Statement Parsers
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Supported Card Parsers",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = OneUiTheme.colors.textPrimary
                        )
                    )

                    OneUiCard(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Column {
                            cardParserDisplayNames.entries.forEachIndexed { index, (parserId, cardName) ->
                                val isChecked = enabledCardParsers.contains(parserId)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = cardName,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = OneUiTheme.colors.textPrimary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                    OneUiSwitch(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            enabledCardParsers = if (checked) {
                                                enabledCardParsers + parserId
                                            } else {
                                                enabledCardParsers - parserId
                                            }
                                        }
                                    )
                                }
                                if (index < cardParserDisplayNames.size - 1) {
                                    OneUiInsetDivider(startIndent = 0.dp)
                                }
                            }
                        }
                    }
                }
            }

            // 4. Ignored Email Subjects / Skip Rules
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Ignored Email Subjects",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = OneUiTheme.colors.textPrimary
                        )
                    )
                    Text(
                        text = "Emails with subjects matching any of these keywords will be ignored and not imported into history (e.g. Combined statements or savings accounts).",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = OneUiTheme.colors.textSecondary,
                            fontSize = 11.5.sp
                        )
                    )

                    // Add new ignore rule input
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newIgnoreKeywordInput,
                            onValueChange = { newIgnoreKeywordInput = it },
                            placeholder = { Text("e.g. Combined, Savings...") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OneUiTheme.colors.primaryAccent,
                                unfocusedBorderColor = OneUiTheme.colors.surfaceElevated,
                                focusedContainerColor = OneUiTheme.colors.surfaceElevated,
                                unfocusedContainerColor = OneUiTheme.colors.surfaceElevated,
                                focusedTextColor = OneUiTheme.colors.textPrimary,
                                unfocusedTextColor = OneUiTheme.colors.textPrimary
                            )
                        )
                        OneUiPillButton(
                            text = "Add",
                            onClick = {
                                val clean = newIgnoreKeywordInput.trim()
                                if (clean.isNotBlank() && !ignoredSubjects.any { it.equals(clean, ignoreCase = true) }) {
                                    ignoredSubjects.add(clean)
                                    newIgnoreKeywordInput = ""
                                }
                            },
                            containerColor = OneUiTheme.colors.primaryAccent,
                            contentColor = Color.White
                        )
                    }

                    // Ignored Keywords Chips
                    if (ignoredSubjects.isEmpty()) {
                        Text(
                            text = "No ignored subject rules configured.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = OneUiTheme.colors.textMuted,
                                fontSize = 12.sp
                            )
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ignoredSubjects.forEach { keyword ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = OneUiTheme.colors.surfaceElevated
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Contains \"$keyword\"",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = OneUiTheme.colors.textPrimary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
                                        IconButton(
                                            onClick = { ignoredSubjects.remove(keyword) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove rule",
                                                tint = OneUiTheme.colors.textSecondary,
                                                modifier = Modifier.size(16.dp)
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
