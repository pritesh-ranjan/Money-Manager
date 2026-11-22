package com.moneymanager.app.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.moneymanager.app.data.local.entity.*
import com.moneymanager.app.domain.CurrencyFormatter
import com.moneymanager.app.domain.NoteSuggestion
import com.moneymanager.app.domain.NoteSuggestionEngine
import com.moneymanager.app.ui.components.*
import com.moneymanager.app.ui.main.MainViewModel
import com.moneymanager.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

enum class ActiveTransactionField {
    NONE,
    AMOUNT,
    CATEGORY,
    ACCOUNT,
    TO_ACCOUNT,
    NOTE,
    DESCRIPTION
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditTransactionSheet(
    viewModel: MainViewModel,
    existingTx: TransactionWithDetails? = null,
    initialType: TransactionType = TransactionType.EXPENSE,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val allAccounts by viewModel.accounts.collectAsState()
    val allAccountTypes by viewModel.accountTypes.collectAsState()
    val accountTypesMap by viewModel.accountTypesMap.collectAsState()
    val allCurrencies by viewModel.allCurrencies.collectAsState()
    val expenseCategories by viewModel.expenseCategories.collectAsState()
    val incomeCategories by viewModel.incomeCategories.collectAsState()
    val categoriesMap by viewModel.categoriesMap.collectAsState()
    val allTransactions by viewModel.allTransactionsUnfiltered.collectAsState()
    val allTags by viewModel.allTags.collectAsState()

    var selectedType by remember(existingTx, initialType) {
        mutableStateOf(existingTx?.transaction?.type ?: initialType)
    }

    var isAdjustmentNegative by remember {
        mutableStateOf(
            existingTx?.let { it.transaction.type == TransactionType.ADJUSTMENT && it.transaction.accountAmount < 0 } ?: false
        )
    }

    var amountStr by remember {
        mutableStateOf(
            existingTx?.let { String.format(Locale.US, "%.2f", abs(it.transaction.accountAmount)) } ?: ""
        )
    }

    var selectedAccountUid by remember {
        mutableStateOf(existingTx?.account?.uid ?: "")
    }
    var selectedToAccountUid by remember {
        mutableStateOf(existingTx?.toAccount?.uid ?: "")
    }

    var selectedCategoryUid by remember { mutableStateOf(existingTx?.category?.uid) }
    var selectedCategoryName by remember {
        mutableStateOf(
            existingTx?.category?.let { cat ->
                val parent = if (cat.parentUid != null) categoriesMap[cat.parentUid] else null
                if (parent != null) "${parent.name} / ${cat.name}" else cat.name
            } ?: ""
        )
    }
    var selectedCategoryIcon by remember { mutableStateOf(existingTx?.category?.icon ?: "") }

    var note by remember { mutableStateOf(existingTx?.transaction?.note ?: "") }
    var description by remember { mutableStateOf(existingTx?.transaction?.description ?: "") }
    var timestamp by remember { mutableStateOf(existingTx?.transaction?.timestamp ?: System.currentTimeMillis()) }

    var selectedTagUids by remember {
        mutableStateOf(existingTx?.tags?.map { it.uid }?.toSet() ?: emptySet())
    }
    var showAddTagDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var parentUidForNewCategory by remember { mutableStateOf<String?>(null) }
    var parentNameForNewCategory by remember { mutableStateOf<String?>(null) }
    var categoryDrillDownParent by remember { mutableStateOf<CategoryWithSubcategories?>(null) }

    var showAddAccountDialog by remember { mutableStateOf(false) }
    var addAccountDefaultType by remember { mutableStateOf(AccountType.SAVINGS) }
    var lastCreatedAccount by remember { mutableStateOf<AccountEntity?>(null) }

    var activeField by remember {
        mutableStateOf(if (existingTx == null) ActiveTransactionField.AMOUNT else ActiveTransactionField.NONE)
    }

    val amountFocusRequester = remember { FocusRequester() }
    val noteFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (existingTx == null) {
            activeField = ActiveTransactionField.AMOUNT
            try {
                amountFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    val typeColor = when (selectedType) {
        TransactionType.EXPENSE -> OneUiTheme.colors.expense
        TransactionType.INCOME -> OneUiTheme.colors.income
        TransactionType.TRANSFER -> OneUiTheme.colors.primaryAccent
        TransactionType.ADJUSTMENT -> OneUiTheme.colors.warning
    }

    val currentAccountWithCurr = allAccounts.firstOrNull { it.account.uid == selectedAccountUid }
    val currentCurrency = currentAccountWithCurr?.currency
        ?: lastCreatedAccount?.let { lca -> if (lca.uid == selectedAccountUid) allCurrencies.firstOrNull { it.uid == lca.currencyUid } else null }
        ?: allCurrencies.firstOrNull { it.isMainCurrency }
        ?: allCurrencies.firstOrNull()

    val noteSuggestions = remember(allTransactions, note, activeField) {
        if (activeField == ActiveTransactionField.NOTE && note.trim().isNotEmpty()) {
            NoteSuggestionEngine.getSuggestions(allTransactions, note)
        } else {
            emptyList()
        }
    }

    fun advanceFromAmount() {
        if (selectedType == TransactionType.EXPENSE || selectedType == TransactionType.INCOME) {
            if (selectedCategoryUid == null) {
                focusManager.clearFocus()
                keyboardController?.hide()
                activeField = ActiveTransactionField.CATEGORY
            } else if (selectedAccountUid.isBlank()) {
                focusManager.clearFocus()
                keyboardController?.hide()
                activeField = ActiveTransactionField.ACCOUNT
            } else {
                activeField = ActiveTransactionField.NOTE
                noteFocusRequester.requestFocus()
            }
        } else {
            if (selectedAccountUid.isBlank()) {
                focusManager.clearFocus()
                keyboardController?.hide()
                activeField = ActiveTransactionField.ACCOUNT
            } else if (selectedType == TransactionType.TRANSFER && selectedToAccountUid.isBlank()) {
                focusManager.clearFocus()
                keyboardController?.hide()
                activeField = ActiveTransactionField.TO_ACCOUNT
            } else {
                activeField = ActiveTransactionField.NOTE
                noteFocusRequester.requestFocus()
            }
        }
    }

    fun executeSave(isContinue: Boolean): Boolean {
        val amt = amountStr.toDoubleOrNull()
        if (amt == null || amt <= 0.0) {
            validationError = "Please enter an amount greater than 0"
            activeField = ActiveTransactionField.AMOUNT
            amountFocusRequester.requestFocus()
            return false
        }
        if (selectedAccountUid.isBlank()) {
            validationError = "Please select an account"
            focusManager.clearFocus()
            keyboardController?.hide()
            activeField = ActiveTransactionField.ACCOUNT
            return false
        }
        if (selectedType == TransactionType.TRANSFER) {
            if (selectedToAccountUid.isBlank() || selectedToAccountUid == selectedAccountUid) {
                validationError = "Please select a different destination account"
                focusManager.clearFocus()
                keyboardController?.hide()
                activeField = ActiveTransactionField.TO_ACCOUNT
                return false
            }
        }

        validationError = null
        val pickedTags = allTags.filter { selectedTagUids.contains(it.uid) }
        val finalAmt = if (selectedType == TransactionType.ADJUSTMENT && isAdjustmentNegative) -amt else amt

        viewModel.saveTransaction(
            type = selectedType,
            accountUid = selectedAccountUid,
            amount = finalAmt,
            currencyUid = currentCurrency?.uid ?: "CURRENCY_INR",
            categoryUid = selectedCategoryUid,
            toAccountUid = if (selectedType == TransactionType.TRANSFER) selectedToAccountUid else null,
            note = note.trim(),
            description = description.trim(),
            timestamp = timestamp,
            tags = pickedTags,
            existingUid = existingTx?.transaction?.uid
        )

        if (isContinue) {
            amountStr = ""
            note = ""
            description = ""
            selectedCategoryUid = null
            selectedCategoryName = ""
            selectedCategoryIcon = ""
            selectedTagUids = emptySet()
            activeField = ActiveTransactionField.AMOUNT
            amountFocusRequester.requestFocus()
            return true
        } else {
            onDismiss()
            return true
        }
    }

    Dialog(
        onDismissRequest = {
            if (activeField == ActiveTransactionField.CATEGORY && categoryDrillDownParent != null) {
                categoryDrillDownParent = null
            } else if (activeField in listOf(ActiveTransactionField.CATEGORY, ActiveTransactionField.ACCOUNT, ActiveTransactionField.TO_ACCOUNT, ActiveTransactionField.NOTE)) {
                focusManager.clearFocus()
                keyboardController?.hide()
                categoryDrillDownParent = null
                activeField = ActiveTransactionField.NONE
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BackHandler {
            if (activeField == ActiveTransactionField.CATEGORY && categoryDrillDownParent != null) {
                categoryDrillDownParent = null
            } else if (activeField in listOf(ActiveTransactionField.CATEGORY, ActiveTransactionField.ACCOUNT, ActiveTransactionField.TO_ACCOUNT, ActiveTransactionField.NOTE)) {
                focusManager.clearFocus()
                keyboardController?.hide()
                categoryDrillDownParent = null
                activeField = ActiveTransactionField.NONE
            } else {
                onDismiss()
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(OneUiTheme.colors.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            color = OneUiTheme.colors.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(OneUiTheme.colors.background)
                ) {
                // One UI Sub-Screen Top Bar (Screenshot 2 "< Title")
                OneUiSubScreenTopBar(
                    title = if (existingTx != null) "Edit transaction" else "New transaction",
                    onBack = onDismiss,
                    actions = {
                        if (existingTx != null) {
                            IconButton(onClick = { showDeleteConfirmDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Transaction",
                                    tint = OneUiTheme.colors.expense
                                )
                            }
                        }
                    }
                )

                // Type Selector Pill Bar
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    val types = remember(existingTx) {
                        buildList {
                            add(TransactionType.EXPENSE)
                            add(TransactionType.INCOME)
                            add(TransactionType.TRANSFER)
                            if (existingTx?.transaction?.type == TransactionType.ADJUSTMENT) {
                                add(TransactionType.ADJUSTMENT)
                            }
                        }
                    }

                    OneUiPillSegmentedControl(
                        items = types,
                        selectedItem = selectedType,
                        onItemSelected = { type ->
                            val previousType = selectedType
                            selectedType = type
                            validationError = null
                            categoryDrillDownParent = null
                            if (type == TransactionType.TRANSFER || type == TransactionType.ADJUSTMENT) {
                                selectedCategoryUid = null
                                selectedCategoryName = ""
                                selectedCategoryIcon = ""
                            } else if (previousType != type) {
                                val currentCat = selectedCategoryUid?.let { categoriesMap[it] }
                                val expectedCatType = if (type == TransactionType.EXPENSE) CategoryType.EXPENSE else CategoryType.INCOME
                                if (currentCat == null || currentCat.type != expectedCatType) {
                                    selectedCategoryUid = null
                                    selectedCategoryName = ""
                                    selectedCategoryIcon = ""
                                }
                            }

                            // Bug 5: keep the open picker in sync with the new transaction type
                            activeField = when (activeField) {
                                ActiveTransactionField.CATEGORY -> {
                                    if (type == TransactionType.EXPENSE || type == TransactionType.INCOME) {
                                        ActiveTransactionField.CATEGORY
                                    } else if (selectedAccountUid.isBlank()) {
                                        ActiveTransactionField.ACCOUNT
                                    } else {
                                        ActiveTransactionField.NONE
                                    }
                                }
                                ActiveTransactionField.TO_ACCOUNT -> {
                                    if (type == TransactionType.TRANSFER) {
                                        ActiveTransactionField.TO_ACCOUNT
                                    } else if ((type == TransactionType.EXPENSE || type == TransactionType.INCOME) && selectedCategoryUid == null) {
                                        ActiveTransactionField.CATEGORY
                                    } else {
                                        ActiveTransactionField.NONE
                                    }
                                }
                                ActiveTransactionField.ACCOUNT -> {
                                    ActiveTransactionField.ACCOUNT
                                }
                                else -> activeField
                            }
                        },
                        labelProvider = { it.displayName },
                        activeColor = typeColor
                    )
                }

                // Scrollable Form Body
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(top = 6.dp, bottom = 16.dp)
                ) {
                    // 1. Borderless Hero Amount Card
                    item {
                        OneUiCard {
                            Text(
                                text = "Amount",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = OneUiTheme.colors.textSecondary,
                                    fontSize = 13.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        activeField = ActiveTransactionField.AMOUNT
                                        amountFocusRequester.requestFocus()
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = currentCurrency?.symbol ?: "₹",
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (amountStr.isNotBlank()) typeColor else OneUiTheme.colors.textMuted,
                                        fontSize = 30.sp
                                    ),
                                    modifier = Modifier.padding(end = 8.dp)
                                )

                                BasicTextField(
                                    value = amountStr,
                                    onValueChange = { amountStr = it },
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(amountFocusRequester)
                                        .onFocusChanged {
                                            if (it.isFocused) activeField = ActiveTransactionField.AMOUNT
                                        },
                                    textStyle = MaterialTheme.typography.headlineLarge.copy(
                                        color = OneUiTheme.colors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 30.sp
                                    ),
                                    singleLine = true,
                                    cursorBrush = SolidColor(typeColor),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Decimal,
                                        imeAction = ImeAction.Next
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onNext = { advanceFromAmount() }
                                    ),
                                    decorationBox = { innerTextField ->
                                        if (amountStr.isEmpty()) {
                                            Text(
                                                "0.00",
                                                color = OneUiTheme.colors.textMuted,
                                                fontSize = 30.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        innerTextField()
                                    }
                                )

                                if (amountStr.isNotEmpty()) {
                                    IconButton(
                                        onClick = { amountStr = "" },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Cancel,
                                            contentDescription = "Clear amount",
                                            tint = OneUiTheme.colors.textMuted,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                if (selectedType == TransactionType.ADJUSTMENT) {
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Row(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(OneUiTheme.colors.surfaceElevated)
                                            .padding(2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(if (!isAdjustmentNegative) OneUiTheme.colors.incomeContainer else Color.Transparent)
                                                .clickable { isAdjustmentNegative = false }
                                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Text("+", color = if (!isAdjustmentNegative) OneUiTheme.colors.income else OneUiTheme.colors.textMuted, fontWeight = FontWeight.Bold)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(if (isAdjustmentNegative) OneUiTheme.colors.expenseContainer else Color.Transparent)
                                                .clickable { isAdjustmentNegative = true }
                                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Text("-", color = if (isAdjustmentNegative) OneUiTheme.colors.expense else OneUiTheme.colors.textMuted, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            if (currentCurrency != null && !currentCurrency.isMainCurrency) {
                                val parsedAmt = amountStr.toDoubleOrNull() ?: 0.0
                                val inrPreview = parsedAmt * currentCurrency.rateToBase
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "≈ ₹" + CurrencyFormatter.formatIndianNumber(inrPreview),
                                    style = MaterialTheme.typography.bodySmall.copy(color = OneUiTheme.colors.primaryAccent)
                                )
                            }
                        }
                    }

                    // 2. Grouped Details Card (Screenshot 2 style with Inset Dividers)
                    item {
                        OneUiCard(
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
                            OneUiFormDetailRow(
                                label = "Date",
                                onClick = {
                                    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
                                    DatePickerDialog(
                                        context,
                                        { _, year, month, dayOfMonth ->
                                            val updatedCal = Calendar.getInstance().apply {
                                                timeInMillis = timestamp
                                                set(Calendar.YEAR, year)
                                                set(Calendar.MONTH, month)
                                                set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                            }
                                            TimePickerDialog(
                                                context,
                                                { _, hourOfDay, minute ->
                                                    updatedCal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                                    updatedCal.set(Calendar.MINUTE, minute)
                                                    timestamp = updatedCal.timeInMillis
                                                },
                                                cal.get(Calendar.HOUR_OF_DAY),
                                                cal.get(Calendar.MINUTE),
                                                false
                                            ).show()
                                        },
                                        cal.get(Calendar.YEAR),
                                        cal.get(Calendar.MONTH),
                                        cal.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                }
                            ) {
                                Text(
                                    text = dateFormat.format(timestamp),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = OneUiTheme.colors.primaryAccent,
                                        fontSize = 15.sp
                                    )
                                )
                            }

                            OneUiInsetDivider(startIndent = 86.dp)

                            if (selectedType == TransactionType.EXPENSE || selectedType == TransactionType.INCOME) {
                                OneUiFormDetailRow(
                                    label = "Category",
                                    onClick = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        activeField = if (activeField == ActiveTransactionField.CATEGORY) ActiveTransactionField.NONE else ActiveTransactionField.CATEGORY
                                    }
                                ) {
                                    Text(
                                        text = if (selectedCategoryName.isNotBlank()) {
                                            if (selectedCategoryIcon.isNotBlank()) "$selectedCategoryIcon  $selectedCategoryName" else selectedCategoryName
                                        } else "Select category",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            color = if (selectedCategoryName.isNotBlank()) OneUiTheme.colors.textPrimary else OneUiTheme.colors.textMuted,
                                            fontSize = 15.sp
                                        )
                                    )
                                }

                                OneUiInsetDivider(startIndent = 86.dp)
                            }

                            if (selectedType != TransactionType.TRANSFER) {
                                val selectedAccName = allAccounts.firstOrNull { it.account.uid == selectedAccountUid }?.account?.name
                                    ?: if (lastCreatedAccount?.uid == selectedAccountUid) lastCreatedAccount?.name ?: "" else ""
                                OneUiFormDetailRow(
                                    label = "Account",
                                    onClick = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        activeField = if (activeField == ActiveTransactionField.ACCOUNT) ActiveTransactionField.NONE else ActiveTransactionField.ACCOUNT
                                    }
                                ) {
                                    Text(
                                        text = selectedAccName.ifBlank { "Select account" },
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            color = if (selectedAccName.isNotBlank()) OneUiTheme.colors.textPrimary else OneUiTheme.colors.textMuted,
                                            fontSize = 15.sp
                                        )
                                    )
                                }
                            } else {
                                val fromAccName = allAccounts.firstOrNull { it.account.uid == selectedAccountUid }?.account?.name
                                    ?: if (lastCreatedAccount?.uid == selectedAccountUid) lastCreatedAccount?.name ?: "" else ""
                                OneUiFormDetailRow(
                                    label = "From",
                                    onClick = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        activeField = if (activeField == ActiveTransactionField.ACCOUNT) ActiveTransactionField.NONE else ActiveTransactionField.ACCOUNT
                                    }
                                ) {
                                    Text(
                                        text = fromAccName.ifBlank { "Source account" },
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            color = if (fromAccName.isNotBlank()) OneUiTheme.colors.textPrimary else OneUiTheme.colors.textMuted,
                                            fontSize = 15.sp
                                        )
                                    )
                                }

                                OneUiInsetDivider(startIndent = 86.dp)

                                val toAccName = allAccounts.firstOrNull { it.account.uid == selectedToAccountUid }?.account?.name
                                    ?: if (lastCreatedAccount?.uid == selectedToAccountUid) lastCreatedAccount?.name ?: "" else ""
                                OneUiFormDetailRow(
                                    label = "To",
                                    onClick = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        activeField = if (activeField == ActiveTransactionField.TO_ACCOUNT) ActiveTransactionField.NONE else ActiveTransactionField.TO_ACCOUNT
                                    }
                                ) {
                                    Text(
                                        text = toAccName.ifBlank { "Destination account" },
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            color = if (toAccName.isNotBlank()) OneUiTheme.colors.textPrimary else OneUiTheme.colors.textMuted,
                                            fontSize = 15.sp
                                        )
                                    )
                                }
                            }

                            OneUiInsetDivider(startIndent = 86.dp)

                            // Note Row
                            OneUiFormDetailRow(
                                label = "Note",
                                onClick = {
                                    activeField = ActiveTransactionField.NOTE
                                    noteFocusRequester.requestFocus()
                                }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    BasicTextField(
                                        value = note,
                                        onValueChange = { note = it },
                                        modifier = Modifier
                                            .weight(1f)
                                            .focusRequester(noteFocusRequester)
                                            .onFocusChanged {
                                                if (it.isFocused) activeField = ActiveTransactionField.NOTE
                                            },
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                                            color = OneUiTheme.colors.textPrimary,
                                            fontSize = 15.sp
                                        ),
                                        singleLine = true,
                                        cursorBrush = SolidColor(typeColor),
                                        decorationBox = { inner ->
                                            if (note.isEmpty()) {
                                                Text("Payee or short note", color = OneUiTheme.colors.textMuted, fontSize = 15.sp)
                                            }
                                            inner()
                                        }
                                    )

                                    if (note.isNotEmpty() && activeField == ActiveTransactionField.NOTE) {
                                        IconButton(
                                            onClick = { note = "" },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Cancel,
                                                contentDescription = "Clear note",
                                                tint = OneUiTheme.colors.textMuted,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            OneUiInsetDivider(startIndent = 86.dp)

                            OneUiFormDetailRow(
                                label = "Memo",
                                onClick = { activeField = ActiveTransactionField.DESCRIPTION }
                            ) {
                                BasicTextField(
                                    value = description,
                                    onValueChange = { description = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged {
                                            if (it.isFocused) activeField = ActiveTransactionField.DESCRIPTION
                                        },
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        color = OneUiTheme.colors.textPrimary,
                                        fontSize = 15.sp
                                    ),
                                    maxLines = 3,
                                    cursorBrush = SolidColor(typeColor),
                                    decorationBox = { innerTextField ->
                                        if (description.isEmpty()) {
                                            Text("Additional details...", color = OneUiTheme.colors.textMuted, fontSize = 15.sp)
                                        }
                                        innerTextField()
                                    }
                                )
                            }
                        }
                    }

                    // 3. Borderless Capsule Tag Chips (Screenshot 5 style)
                    item {
                        OneUiCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Tags",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = OneUiTheme.colors.textSecondary,
                                        fontSize = 13.sp
                                    )
                                )
                                OneUiTextActionLink(
                                    text = "+ New tag",
                                    onClick = { showAddTagDialog = true }
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                allTags.forEach { tag ->
                                    val isSelected = selectedTagUids.contains(tag.uid)
                                    OneUiCapsuleChip(
                                        label = tag.name,
                                        selected = isSelected,
                                        onClick = {
                                            selectedTagUids = if (isSelected) {
                                                selectedTagUids - tag.uid
                                            } else {
                                                selectedTagUids + tag.uid
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (validationError != null) {
                        item {
                            Text(
                                text = validationError!!,
                                color = OneUiTheme.colors.expense,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }

                    // 4. Bottom Action Pill Buttons (Borderless One UI Capsules)
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (existingTx == null) {
                                Surface(
                                    modifier = Modifier
                                        .weight(0.38f)
                                        .height(44.dp)
                                        .clip(CircleShape)
                                        .clickable { executeSave(isContinue = true) },
                                    shape = CircleShape,
                                    color = OneUiTheme.colors.surfaceElevated
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "Continue",
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                color = OneUiTheme.colors.textPrimary,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp
                                            )
                                        )
                                    }
                                }
                            }

                            Surface(
                                modifier = Modifier
                                    .weight(if (existingTx == null) 0.62f else 1f)
                                    .height(44.dp)
                                    .clip(CircleShape)
                                    .clickable { executeSave(isContinue = false) },
                                shape = CircleShape,
                                color = OneUiTheme.colors.primaryAccent
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = if (existingTx != null) "Update" else "Save",
                                        style = MaterialTheme.typography.bodyLarge.copy(
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

                // Docked Note Suggestions Panel (Directly above keyboard via imePadding)
                AnimatedVisibility(
                    visible = activeField == ActiveTransactionField.NOTE && noteSuggestions.isNotEmpty(),
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    OneUiNoteSuggestionsDockedPanel(
                        suggestions = noteSuggestions,
                        query = note,
                        typeColor = typeColor,
                        onClose = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            activeField = ActiveTransactionField.NONE
                        },
                        onPick = { suggestion ->
                            note = suggestion.note
                            if (selectedCategoryUid == null && (selectedType == TransactionType.EXPENSE || selectedType == TransactionType.INCOME)) {
                                val catUid = if (selectedType == TransactionType.EXPENSE) {
                                    suggestion.lastExpenseCategoryUid
                                } else {
                                    suggestion.lastIncomeCategoryUid
                                }
                                if (catUid != null && categoriesMap.containsKey(catUid)) {
                                    val cat = categoriesMap[catUid]!!
                                    selectedCategoryUid = cat.uid
                                    val parent = if (cat.parentUid != null) categoriesMap[cat.parentUid] else null
                                    selectedCategoryName = if (parent != null) "${parent.name} / ${cat.name}" else cat.name
                                    selectedCategoryIcon = cat.icon
                                }
                            }
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            activeField = ActiveTransactionField.NONE
                        }
                    )
                }
            }

            // Overlay Picker Panel with Backdrop Scrim (Screenshots 1, 2, 3)
            AnimatedVisibility(
                visible = activeField in listOf(
                    ActiveTransactionField.CATEGORY,
                    ActiveTransactionField.ACCOUNT,
                    ActiveTransactionField.TO_ACCOUNT
                ),
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (activeField == ActiveTransactionField.CATEGORY && categoryDrillDownParent != null) {
                                    categoryDrillDownParent = null
                                } else {
                                    categoryDrillDownParent = null
                                    activeField = ActiveTransactionField.NONE
                                }
                            }
                        ),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.85f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { /* prevent click-through */ }
                            ),
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        color = OneUiTheme.colors.surface,
                        shadowElevation = 16.dp
                    ) {
                        key(selectedType) {
                            when (activeField) {
                                ActiveTransactionField.CATEGORY -> {
                                    val categoriesList = if (selectedType == TransactionType.INCOME) incomeCategories else expenseCategories
                                    OneUiCategorySelectionSheet(
                                        categories = categoriesList,
                                        selectedUid = selectedCategoryUid,
                                        selectedType = selectedType,
                                        allTransactions = allTransactions,
                                        typeColor = typeColor,
                                        drillDownParent = categoryDrillDownParent,
                                        onDrillDown = { categoryDrillDownParent = it },
                                        onBackFromDrillDown = { categoryDrillDownParent = null },
                                        onClose = {
                                            categoryDrillDownParent = null
                                            activeField = ActiveTransactionField.NONE
                                        },
                                        onSelect = { uid, name, icon ->
                                            selectedCategoryUid = uid
                                            selectedCategoryName = name
                                            selectedCategoryIcon = icon
                                            categoryDrillDownParent = null

                                            if (selectedAccountUid.isBlank()) {
                                                activeField = ActiveTransactionField.ACCOUNT
                                            } else {
                                                activeField = ActiveTransactionField.NOTE
                                                noteFocusRequester.requestFocus()
                                            }
                                        },
                                        onAddNewCategory = {
                                            parentUidForNewCategory = null
                                            parentNameForNewCategory = null
                                            showAddCategoryDialog = true
                                        },
                                        onAddNewSubcategory = { parentUid, parentName ->
                                            parentUidForNewCategory = parentUid
                                            parentNameForNewCategory = parentName
                                            showAddCategoryDialog = true
                                        }
                                    )
                                }

                                ActiveTransactionField.ACCOUNT -> {
                                    OneUiAccountSelectionSheet(
                                        title = if (selectedType == TransactionType.TRANSFER) "From account" else "Select account",
                                        accounts = allAccounts,
                                        selectedUid = selectedAccountUid,
                                        allTransactions = allTransactions,
                                        accountTypesMap = accountTypesMap,
                                        typeColor = typeColor,
                                        onClose = { activeField = ActiveTransactionField.NONE },
                                        onSelect = { uid ->
                                            selectedAccountUid = uid

                                            if (selectedType == TransactionType.TRANSFER && selectedToAccountUid.isBlank()) {
                                                activeField = ActiveTransactionField.TO_ACCOUNT
                                            } else if ((selectedType == TransactionType.EXPENSE || selectedType == TransactionType.INCOME) && selectedCategoryUid == null) {
                                                activeField = ActiveTransactionField.CATEGORY
                                            } else {
                                                activeField = ActiveTransactionField.NOTE
                                                noteFocusRequester.requestFocus()
                                            }
                                        },
                                        onAddNewAccount = { defaultType ->
                                            addAccountDefaultType = defaultType
                                            showAddAccountDialog = true
                                        }
                                    )
                                }

                                ActiveTransactionField.TO_ACCOUNT -> {
                                    val destinationAccounts = allAccounts.filter { it.account.uid != selectedAccountUid }
                                    OneUiAccountSelectionSheet(
                                        title = "To account",
                                        accounts = destinationAccounts,
                                        selectedUid = selectedToAccountUid,
                                        allTransactions = allTransactions,
                                        accountTypesMap = accountTypesMap,
                                        typeColor = typeColor,
                                        onClose = { activeField = ActiveTransactionField.NONE },
                                        onSelect = { uid ->
                                            selectedToAccountUid = uid
                                            activeField = ActiveTransactionField.NOTE
                                            noteFocusRequester.requestFocus()
                                        },
                                        onAddNewAccount = { defaultType ->
                                            addAccountDefaultType = defaultType
                                            showAddAccountDialog = true
                                        }
                                    )
                                }

                                else -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}

    if (showAddCategoryDialog) {
        val currentCatType = if (selectedType == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
        val allCatsList = if (currentCatType == CategoryType.INCOME) incomeCategories else expenseCategories
        val flatCats = remember(allCatsList) {
            allCatsList.flatMap { listOf(it.category) + it.subcategories }
        }

        OneUiAddCategoryDialog(
            parentUid = parentUidForNewCategory,
            parentName = parentNameForNewCategory,
            categoryType = currentCatType,
            existingCategories = flatCats,
            onDismiss = { showAddCategoryDialog = false },
            onSave = { newCat ->
                viewModel.saveCategory(newCat)
                selectedCategoryUid = newCat.uid
                if (newCat.parentUid != null) {
                    val parent = categoriesMap[newCat.parentUid]
                    val pName = parent?.name ?: parentNameForNewCategory ?: ""
                    selectedCategoryName = if (pName.isNotEmpty()) "$pName / ${newCat.name}" else newCat.name
                } else {
                    selectedCategoryName = newCat.name
                }
                selectedCategoryIcon = newCat.icon

                if (selectedAccountUid.isBlank()) {
                    activeField = ActiveTransactionField.ACCOUNT
                } else {
                    activeField = ActiveTransactionField.NOTE
                    noteFocusRequester.requestFocus()
                }
                showAddCategoryDialog = false
            }
        )
    }

    if (showAddAccountDialog) {
        OneUiAddEditAccountDialog(
            existingAccount = null,
            defaultAccountType = addAccountDefaultType,
            accountTypes = allAccountTypes,
            currencies = allCurrencies,
            existingAccounts = allAccounts.map { it.account },
            onDismiss = { showAddAccountDialog = false },
            onSave = { newAccount ->
                viewModel.saveAccount(newAccount)
                lastCreatedAccount = newAccount

                if (activeField == ActiveTransactionField.TO_ACCOUNT) {
                    selectedToAccountUid = newAccount.uid
                    activeField = ActiveTransactionField.NOTE
                    noteFocusRequester.requestFocus()
                } else {
                    selectedAccountUid = newAccount.uid
                    if (selectedType == TransactionType.TRANSFER && selectedToAccountUid.isBlank()) {
                        activeField = ActiveTransactionField.TO_ACCOUNT
                    } else if ((selectedType == TransactionType.EXPENSE || selectedType == TransactionType.INCOME) && selectedCategoryUid == null) {
                        activeField = ActiveTransactionField.CATEGORY
                    } else {
                        activeField = ActiveTransactionField.NOTE
                        noteFocusRequester.requestFocus()
                    }
                }
                showAddAccountDialog = false
            }
        )
    }

    if (showAddTagDialog) {
        var newTagName by remember { mutableStateOf("") }
        OneUiBottomDialog(
            onDismissRequest = { showAddTagDialog = false },
            title = "New Tag",
            confirmButtonText = "Add",
            onConfirm = {
                if (newTagName.isNotBlank()) {
                    viewModel.saveTag(newTagName.trim())
                    showAddTagDialog = false
                }
            }
        ) {
            TextField(
                value = newTagName,
                onValueChange = { newTagName = it },
                label = { Text("Tag Name") },
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
        }
    }

    if (showDeleteConfirmDialog && existingTx != null) {
        OneUiBottomDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = "Delete Transaction",
            confirmButtonText = "Delete",
            confirmButtonColor = OneUiTheme.colors.expense,
            onConfirm = {
                viewModel.deleteTransaction(existingTx.transaction.uid)
                showDeleteConfirmDialog = false
                onDismiss()
            }
        ) {
            Text(
                "Are you sure you want to delete this transaction record?",
                color = OneUiTheme.colors.textSecondary
            )
        }
    }
}

@Composable
private fun OneUiFormDetailRow(
    label: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = OneUiTheme.colors.textSecondary,
                fontSize = 15.sp
            ),
            modifier = Modifier.width(86.dp)
        )

        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

@Composable
private fun OneUiSelectionCircle(
    selected: Boolean,
    typeColor: Color,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Box(
            modifier = modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(typeColor),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    } else {
        Box(
            modifier = modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(OneUiTheme.colors.surfaceElevated.copy(alpha = 0.8f))
                .border(1.5.dp, OneUiTheme.colors.textMuted.copy(alpha = 0.45f), CircleShape)
        )
    }
}

// OneUiCategoryIcon is now shared in com.moneymanager.app.ui.components.OneUiCategoryIcon

@Composable
private fun OneUiAccountTypeIcon(
    accountType: AccountType,
    modifier: Modifier = Modifier,
    tint: Color = OneUiTheme.colors.textPrimary
) {
    val icon = when (accountType) {
        AccountType.CREDIT_CARD -> Icons.Default.CreditCard
        AccountType.CASH -> Icons.Default.AccountBalanceWallet
        AccountType.SAVINGS, AccountType.UPI_DEBIT -> Icons.Default.AccountBalance
        AccountType.INVESTMENT, AccountType.MUTUAL_FUNDS, AccountType.EQUITY -> Icons.AutoMirrored.Filled.TrendingUp
        AccountType.WALLET -> Icons.Default.AccountBalanceWallet
        AccountType.LOAN, AccountType.OTHER -> Icons.Default.Savings
    }
    Icon(
        imageVector = icon,
        contentDescription = accountType.displayName,
        tint = tint,
        modifier = modifier
    )
}

private fun getAccountDisplayInfo(account: AccountEntity): Pair<String, String> {
    val fourDigitsRegex = Regex("^(.*?)[\\s\\-–—\\(]*[•\\*xX]*(\\d{4})\\)?$")
    val match = fourDigitsRegex.find(account.name.trim())
    return if (match != null && match.groupValues[1].isNotBlank()) {
        val cleanName = match.groupValues[1].trim()
        val digits = match.groupValues[2]
        Pair(cleanName, "•••• $digits")
    } else {
        val subtitle = when (account.accountType) {
            AccountType.CREDIT_CARD -> "Credit card"
            AccountType.CASH -> "Cash"
            AccountType.SAVINGS -> "Bank / Savings"
            AccountType.UPI_DEBIT -> "UPI / Debit"
            AccountType.WALLET -> "Wallet"
            AccountType.INVESTMENT -> "Investment"
            AccountType.MUTUAL_FUNDS -> "Mutual funds"
            AccountType.EQUITY -> "Equity"
            AccountType.LOAN -> "Loan"
            AccountType.OTHER -> "Other"
        }
        Pair(account.name, subtitle)
    }
}

private fun getCategorySubtitle(parentWithSubs: CategoryWithSubcategories): String {
    val subs = parentWithSubs.subcategories
    if (subs.isEmpty()) return ""
    return if (subs.size >= 6) {
        "${subs.size} categories"
    } else {
        subs.joinToString(", ") { it.name }
    }
}

private data class CategorySearchResult(
    val uid: String,
    val displayName: String,
    val icon: String,
    val isSubcategory: Boolean,
    val parentName: String?
)

@Composable
private fun OneUiCategorySelectionSheet(
    categories: List<CategoryWithSubcategories>,
    selectedUid: String?,
    selectedType: TransactionType,
    allTransactions: List<TransactionWithDetails>,
    typeColor: Color,
    drillDownParent: CategoryWithSubcategories?,
    onDrillDown: (CategoryWithSubcategories) -> Unit,
    onBackFromDrillDown: () -> Unit,
    onClose: () -> Unit,
    onSelect: (uid: String, name: String, icon: String) -> Unit,
    onAddNewCategory: () -> Unit,
    onAddNewSubcategory: (parentUid: String, parentName: String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val recentCategories = remember(allTransactions, categories, selectedType) {
        val targetType = if (selectedType == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
        val flatCats = categories.flatMap { listOf(it.category) + it.subcategories }
        val flatMap = flatCats.associateBy { it.uid }

        val found = allTransactions
            .asSequence()
            .filter { it.transaction.type == selectedType && it.category != null && it.category.type == targetType }
            .mapNotNull { it.category?.uid }
            .distinct()
            .mapNotNull { flatMap[it] }
            .take(3)
            .toList()

        if (found.isNotEmpty()) {
            found
        } else {
            flatCats.take(3)
        }
    }

    if (drillDownParent != null) {
        // Screenshot 2: Subcategory Drill-Down View
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(OneUiTheme.colors.surface)
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackFromDrillDown,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = OneUiTheme.colors.textPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = drillDownParent.category.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = OneUiTheme.colors.textPrimary,
                        fontSize = 18.sp
                    ),
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = OneUiTheme.colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Breadcrumb
            Text(
                text = "CATEGORIES / ${drillDownParent.category.name.uppercase(Locale.getDefault())}",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = OneUiTheme.colors.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                ),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Subcategories List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                val isParentSelected = selectedUid == drillDownParent.category.uid
                item(key = "general_option") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onSelect(
                                    drillDownParent.category.uid,
                                    drillDownParent.category.name,
                                    drillDownParent.category.icon
                                )
                            }
                            .padding(horizontal = 8.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OneUiSelectionCircle(
                            selected = isParentSelected,
                            typeColor = typeColor
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "${drillDownParent.category.name} (General)",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = if (isParentSelected) typeColor else OneUiTheme.colors.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = if (isParentSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        )
                    }
                }

                items(drillDownParent.subcategories, key = { it.uid }) { sub ->
                    val isSelected = selectedUid == sub.uid
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onSelect(
                                    sub.uid,
                                    "${drillDownParent.category.name} / ${sub.name}",
                                    sub.icon
                                )
                            }
                            .padding(horizontal = 8.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OneUiSelectionCircle(
                            selected = isSelected,
                            typeColor = typeColor
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = sub.name,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = if (isSelected) typeColor else OneUiTheme.colors.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        )
                    }
                }

                item(key = "add_new_subcategory") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onAddNewSubcategory(
                                    drillDownParent.category.uid,
                                    drillDownParent.category.name
                                )
                            }
                            .padding(horizontal = 8.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New subcategory",
                            tint = typeColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "New subcategory",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = typeColor,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        )
                    }
                }
            }
        }
    } else {
        // Screenshot 1: Main Category Selection View
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(OneUiTheme.colors.surface)
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Select category",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = OneUiTheme.colors.textPrimary,
                        fontSize = 18.sp
                    )
                )
                IconButton(
                    onClick = onClose,
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
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = OneUiTheme.colors.textPrimary,
                            fontSize = 15.sp
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(typeColor),
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search categories...",
                                    color = OneUiTheme.colors.textMuted,
                                    fontSize = 15.sp
                                )
                            }
                            inner()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
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

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                if (searchQuery.isNotBlank()) {
                    val query = searchQuery.trim().lowercase(Locale.getDefault())
                    val matchingItems = buildList {
                        categories.forEach { parentWithSubs ->
                            val parentMatches = parentWithSubs.category.name.lowercase(Locale.getDefault()).contains(query)
                            if (parentMatches) {
                                add(
                                    CategorySearchResult(
                                        uid = parentWithSubs.category.uid,
                                        displayName = parentWithSubs.category.name,
                                        icon = parentWithSubs.category.icon,
                                        isSubcategory = false,
                                        parentName = null
                                    )
                                )
                            }
                            parentWithSubs.subcategories.forEach { sub ->
                                if (sub.name.lowercase(Locale.getDefault()).contains(query) || parentMatches) {
                                    add(
                                        CategorySearchResult(
                                            uid = sub.uid,
                                            displayName = sub.name,
                                            icon = sub.icon,
                                            isSubcategory = true,
                                            parentName = parentWithSubs.category.name
                                        )
                                    )
                                }
                            }
                        }
                    }

                    if (matchingItems.isEmpty()) {
                        item(key = "empty_category_search") {
                            Text(
                                text = "No categories found for \"$searchQuery\"",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = OneUiTheme.colors.textMuted,
                                    fontSize = 14.sp
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 20.dp)
                            )
                        }
                    } else {
                        items(matchingItems, key = { it.uid }) { res ->
                            val isSelected = selectedUid == res.uid
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        val fullName = if (res.isSubcategory && res.parentName != null) {
                                            "${res.parentName} / ${res.displayName}"
                                        } else {
                                            res.displayName
                                        }
                                        onSelect(res.uid, fullName, res.icon)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OneUiSelectionCircle(selected = isSelected, typeColor = typeColor)
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = res.displayName,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isSelected) typeColor else OneUiTheme.colors.textPrimary,
                                            fontSize = 15.sp
                                        )
                                    )
                                    if (res.isSubcategory && res.parentName != null) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = res.parentName,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = OneUiTheme.colors.textSecondary,
                                                fontSize = 12.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // RECENTLY USED
                    if (recentCategories.isNotEmpty()) {
                        item(key = "section_recent") {
                            Text(
                                text = "RECENTLY USED",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = OneUiTheme.colors.textSecondary,
                                    letterSpacing = 0.8.sp,
                                    fontSize = 11.sp
                                ),
                                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp, start = 4.dp)
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                recentCategories.forEach { cat ->
                                    Column(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                onSelect(cat.uid, cat.name, cat.icon)
                                            }
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        OneUiCategoryIcon(
                                            icon = cat.icon,
                                            name = cat.name,
                                            modifier = Modifier.size(26.dp),
                                            tint = OneUiTheme.colors.textPrimary
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = cat.name,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = OneUiTheme.colors.textPrimary,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // All categories Header
                    item(key = "section_all_categories") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "All categories",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = OneUiTheme.colors.textPrimary,
                                    fontSize = 16.sp
                                )
                            )
                            Text(
                                text = selectedType.displayName,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = OneUiTheme.colors.textSecondary,
                                    fontSize = 13.sp
                                )
                            )
                        }
                    }

                    // Categories List
                    items(categories, key = { it.category.uid }) { parentWithSubs ->
                        val parentCat = parentWithSubs.category
                        val subtitle = getCategorySubtitle(parentWithSubs)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    if (parentWithSubs.subcategories.isNotEmpty()) {
                                        onDrillDown(parentWithSubs)
                                    } else {
                                        onSelect(parentCat.uid, parentCat.name, parentCat.icon)
                                    }
                                }
                                .padding(horizontal = 4.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OneUiCategoryIcon(
                                icon = parentCat.icon,
                                name = parentCat.name,
                                modifier = Modifier.size(24.dp),
                                tint = OneUiTheme.colors.textPrimary
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = parentCat.name,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = OneUiTheme.colors.textPrimary,
                                        fontSize = 15.sp
                                    )
                                )
                                if (subtitle.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = OneUiTheme.colors.textSecondary,
                                            fontSize = 12.5.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Expand",
                                tint = OneUiTheme.colors.textMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Add new category
                    item(key = "add_new_category") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onAddNewCategory() }
                                .padding(horizontal = 4.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New category",
                                tint = typeColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = "New category",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = typeColor,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HighlightedSubstringText(
    fullText: String,
    query: String,
    highlightColor: Color,
    defaultColor: Color
) {
    val annotated = remember(fullText, query, highlightColor, defaultColor) {
        buildAnnotatedString {
            val lowerFull = fullText.lowercase(Locale.getDefault())
            val lowerQuery = query.lowercase(Locale.getDefault()).trim()
            var startIndex = 0

            if (lowerQuery.isNotEmpty()) {
                while (startIndex < fullText.length) {
                    val matchIndex = lowerFull.indexOf(lowerQuery, startIndex)
                    if (matchIndex == -1) {
                        append(fullText.substring(startIndex))
                        break
                    }
                    if (matchIndex > startIndex) {
                        append(fullText.substring(startIndex, matchIndex))
                    }
                    withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.SemiBold)) {
                        append(fullText.substring(matchIndex, matchIndex + lowerQuery.length))
                    }
                    startIndex = matchIndex + lowerQuery.length
                }
            } else {
                append(fullText)
            }
        }
    }

    Text(
        text = annotated,
        color = defaultColor,
        fontSize = 14.sp
    )
}

@Composable
private fun OneUiNoteSuggestionsDockedPanel(
    suggestions: List<NoteSuggestion>,
    query: String,
    typeColor: Color,
    onPick: (NoteSuggestion) -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 210.dp),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = OneUiTheme.colors.surfaceElevated,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Suggested notes",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = OneUiTheme.colors.textPrimary,
                        fontSize = 14.sp
                    )
                )

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close suggestions",
                        tint = OneUiTheme.colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            HorizontalDivider(color = OneUiTheme.colors.divider, thickness = 0.5.dp)

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(suggestions, key = { it.note }) { suggestion ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(suggestion) }
                            .padding(horizontal = 20.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HighlightedSubstringText(
                            fullText = suggestion.note,
                            query = query,
                            highlightColor = typeColor,
                            defaultColor = OneUiTheme.colors.textPrimary
                        )
                    }
                    OneUiInsetDivider(startIndent = 20.dp)
                }
            }
        }
    }
}
