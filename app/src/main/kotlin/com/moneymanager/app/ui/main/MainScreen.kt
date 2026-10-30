package com.moneymanager.app.ui.main

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moneymanager.app.data.local.entity.TransactionType
import com.moneymanager.app.data.local.entity.TransactionWithDetails
import com.moneymanager.app.ui.screens.AccountsScreen
import com.moneymanager.app.ui.screens.AddEditTransactionSheet
import com.moneymanager.app.ui.screens.CreditCardsScreen
import com.moneymanager.app.ui.screens.ManageScreen
import com.moneymanager.app.ui.screens.StatisticsScreen
import com.moneymanager.app.ui.screens.TransactionsScreen
import com.moneymanager.app.ui.theme.*

enum class BottomTab(val title: String, val icon: ImageVector) {
    TRANSACTIONS("Dashboard", Icons.Default.MenuBook),
    STATS("Stats", Icons.Default.BarChart),
    ACCOUNTS("Accounts", Icons.Default.AccountBalance),
    CREDIT_CARDS("Cards", Icons.Default.CreditCard),
    MANAGE("Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(),
    quickAddType: TransactionType? = null,
    onQuickAddConsumed: () -> Unit = {}
) {
    var currentTab by remember { mutableStateOf(BottomTab.TRANSACTIONS) }
    var showAddTransactionSheet by remember { mutableStateOf(false) }
    var initialSheetType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var editingTransaction by remember { mutableStateOf<TransactionWithDetails?>(null) }

    LaunchedEffect(quickAddType) {
        if (quickAddType != null) {
            currentTab = BottomTab.TRANSACTIONS
            editingTransaction = null
            initialSheetType = quickAddType
            showAddTransactionSheet = true
            onQuickAddConsumed()
        }
    }

    val uiMessage by viewModel.uiMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiMessage) {
        uiMessage?.let { msg ->
            val text = when (msg) {
                is UiMessage.Success -> msg.message
                is UiMessage.Error -> msg.message
            }
            snackbarHostState.showSnackbar(text)
            viewModel.clearUiMessage()
        }
    }

    Scaffold(
        containerColor = OneUiTheme.colors.background,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = OneUiTheme.colors.surfaceElevated,
                    contentColor = OneUiTheme.colors.textPrimary,
                    shape = CircleShape
                )
            }
        },
        bottomBar = {
            // Standard Samsung One UI 9 Edge-to-Edge Bottom Navigation Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OneUiTheme.colors.background)
                    .navigationBarsPadding()
            ) {
                HorizontalDivider(
                    color = OneUiTheme.colors.divider,
                    thickness = 0.5.dp
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BottomTab.values().forEach { tab ->
                        val isSelected = currentTab == tab
                        val interactionSource = remember { MutableInteractionSource() }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null
                                ) { currentTab = tab },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                tint = if (isSelected) OneUiTheme.colors.textPrimary else OneUiTheme.colors.textMuted,
                                modifier = Modifier.size(21.dp)
                            )

                            Spacer(modifier = Modifier.height(2.5.dp))

                            Text(
                                text = tab.title,
                                color = if (isSelected) OneUiTheme.colors.textPrimary else OneUiTheme.colors.textMuted,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            // Subtle One UI bottom indicator pill under active tab
                            Box(
                                modifier = Modifier
                                    .width(if (isSelected) 14.dp else 0.dp)
                                    .height(2.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) OneUiTheme.colors.primaryAccent else Color.Transparent
                                    )
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentTab == BottomTab.TRANSACTIONS) {
                FloatingActionButton(
                    onClick = {
                        editingTransaction = null
                        initialSheetType = TransactionType.EXPENSE
                        showAddTransactionSheet = true
                    },
                    containerColor = OneUiTheme.colors.primaryAccent,
                    contentColor = Color.White,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Transaction",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
                .background(OneUiTheme.colors.background)
        ) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(180)) togetherWith
                            fadeOut(animationSpec = tween(140))
                },
                label = "OneUiTabTransition"
            ) { tab ->
                when (tab) {
                    BottomTab.TRANSACTIONS -> TransactionsScreen(
                        viewModel = viewModel,
                        onEditTransaction = { item ->
                            editingTransaction = item
                            initialSheetType = item.transaction.type
                            showAddTransactionSheet = true
                        }
                    )

                    BottomTab.STATS -> StatisticsScreen(
                        viewModel = viewModel
                    )

                    BottomTab.ACCOUNTS -> AccountsScreen(
                        viewModel = viewModel
                    )

                    BottomTab.CREDIT_CARDS -> CreditCardsScreen(
                        viewModel = viewModel
                    )

                    BottomTab.MANAGE -> ManageScreen(
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    if (showAddTransactionSheet) {
        AddEditTransactionSheet(
            viewModel = viewModel,
            existingTx = editingTransaction,
            initialType = initialSheetType,
            onDismiss = {
                showAddTransactionSheet = false
                editingTransaction = null
            }
        )
    }
}
