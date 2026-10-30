package com.moneymanager.app.ui.main

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.AccountDeleteOutcome
import com.moneymanager.app.data.MoneyManagerRepository
import com.moneymanager.app.data.local.AppDatabase
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.AccountWithCurrency
import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.data.local.entity.CategoryType
import com.moneymanager.app.data.local.entity.CategoryWithSubcategories
import com.moneymanager.app.data.local.entity.CurrencyEntity
import com.moneymanager.app.data.local.entity.TagEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.local.entity.TransactionType
import com.moneymanager.app.data.local.entity.TransactionWithDetails
import com.moneymanager.app.domain.BackupRestoreResult
import com.moneymanager.app.domain.CreditCardCycleSummary
import com.moneymanager.app.domain.MonthlyMoneyStory
import com.moneymanager.app.domain.MonthlyMoneyStoryEngine
import com.moneymanager.app.domain.NetWorthSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

sealed class UiMessage {
    data class Success(val message: String) : UiMessage()
    data class Error(val message: String) : UiMessage()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application, viewModelScope)
    private val repository = MoneyManagerRepository(db.moneyManagerDao())

    // Current selected month for ledger
    private val _selectedYearMonth = MutableStateFlow(YearMonth.now())
    val selectedYearMonth: StateFlow<YearMonth> = _selectedYearMonth.asStateFlow()

    // Search query for ledger
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Filter by tag
    private val _selectedTag = MutableStateFlow<String?>(null)
    val selectedTag: StateFlow<String?> = _selectedTag.asStateFlow()

    // UI Feedback Message
    private val _uiMessage = MutableStateFlow<UiMessage?>(null)
    val uiMessage: StateFlow<UiMessage?> = _uiMessage.asStateFlow()

    val accounts: StateFlow<List<AccountWithCurrency>> = repository.accountsWithCurrency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCurrencies: StateFlow<List<CurrencyEntity>> = repository.allCurrencies
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenseCategories: StateFlow<List<CategoryWithSubcategories>> = repository.getCategoriesWithSubcategories(CategoryType.EXPENSE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incomeCategories: StateFlow<List<CategoryWithSubcategories>> = repository.getCategoriesWithSubcategories(CategoryType.INCOME)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCategories: StateFlow<List<CategoryEntity>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categoriesMap: StateFlow<Map<String, CategoryEntity>> = repository.allCategories
        .map { list -> list.associateBy { it.uid } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val allTransactionsUnfiltered: StateFlow<List<TransactionWithDetails>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTags: StateFlow<List<TagEntity>> = repository.allTags
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val netWorth: StateFlow<NetWorthSummary?> = repository.netWorthSummary
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val creditCards: StateFlow<List<CreditCardCycleSummary>> = repository.creditCardSummaries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monthlyMoneyStory: StateFlow<MonthlyMoneyStory?> = combine(
        allTransactionsUnfiltered,
        categoriesMap,
        _selectedYearMonth
    ) { allTxs, catMap, ym ->
        val today = LocalDate.now()
        // Defaults to last completed month when viewing current month, or selected month when navigating
        val targetMonth = if (ym == YearMonth.from(today)) ym.minusMonths(1) else ym
        val comparisonMonth = targetMonth.minusMonths(1)

        MonthlyMoneyStoryEngine.generateStory(
            targetYm = targetMonth,
            comparisonYm = comparisonMonth,
            allTransactions = allTxs,
            categoriesMap = catMap,
            referenceDate = today
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Month & Search filtered transactions
    val transactions: StateFlow<List<TransactionWithDetails>> = combine(
        repository.allTransactions,
        _selectedYearMonth,
        _searchQuery,
        _selectedTag
    ) { allTxs, ym, query, tagFilter ->
        val zone = ZoneId.systemDefault()
        val startMillis = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = ym.atEndOfMonth().atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()

        allTxs.filter { item ->
            val matchesMonth = item.transaction.timestamp in startMillis..endMillis
            val matchesQuery = if (query.isBlank()) true else {
                item.transaction.note.contains(query, ignoreCase = true) ||
                item.transaction.description.contains(query, ignoreCase = true) ||
                item.category?.name?.contains(query, ignoreCase = true) == true ||
                item.account.name.contains(query, ignoreCase = true) ||
                item.tags.any { it.name.contains(query, ignoreCase = true) }
            }
            val matchesTag = if (tagFilter == null) true else {
                item.tags.any { it.uid == tagFilter || it.name.equals(tagFilter, ignoreCase = true) }
            }

            matchesMonth && matchesQuery && matchesTag
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSelectedMonth(ym: YearMonth) {
        _selectedYearMonth.value = ym
    }

    fun previousMonth() {
        _selectedYearMonth.value = _selectedYearMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        _selectedYearMonth.value = _selectedYearMonth.value.plusMonths(1)
    }

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
    }

    fun setSelectedTag(tag: String?) {
        _selectedTag.value = tag
    }

    fun clearUiMessage() {
        _uiMessage.value = null
    }

    // --- Transaction Actions ---
    fun saveTransaction(
        type: TransactionType,
        accountUid: String,
        amount: Double,
        currencyUid: String,
        categoryUid: String?,
        toAccountUid: String? = null,
        note: String = "",
        description: String = "",
        timestamp: Long = System.currentTimeMillis(),
        tags: List<TagEntity> = emptyList(),
        existingUid: String? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currencies = allCurrencies.value
                val currency = currencies.firstOrNull { it.uid == currencyUid }
                val rate = currency?.rateToBase ?: 1.0
                val baseAmt = amount * rate

                val tx = TransactionEntity(
                    uid = existingUid ?: UUID.randomUUID().toString(),
                    type = type,
                    accountAmount = amount,
                    baseAmount = baseAmt,
                    exchangeRate = rate,
                    currencyUid = currencyUid,
                    accountUid = accountUid,
                    toAccountUid = toAccountUid,
                    toAccountAmount = if (type == TransactionType.TRANSFER) amount else null,
                    categoryUid = categoryUid,
                    note = note,
                    description = description,
                    timestamp = timestamp
                )
                repository.saveTransaction(tx, tags)
                _uiMessage.value = UiMessage.Success("Transaction saved")
            } catch (e: Exception) {
                _uiMessage.value = UiMessage.Error("Failed to save transaction: ${e.localizedMessage}")
            }
        }
    }

    fun deleteTransaction(uid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deleteTransaction(uid)
                _uiMessage.value = UiMessage.Success("Transaction deleted")
            } catch (e: Exception) {
                _uiMessage.value = UiMessage.Error("Failed to delete transaction: ${e.localizedMessage ?: e.message}")
            }
        }
    }

    // --- Approach A: Revaluation Adjustment ---
    fun adjustBalance(account: AccountEntity, currency: CurrencyEntity, currentBalance: Double, targetBalance: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.adjustAccountBalance(account, currency, currentBalance, targetBalance)
                _uiMessage.value = UiMessage.Success("Balance updated for ${account.name}")
            } catch (e: Exception) {
                _uiMessage.value = UiMessage.Error("Failed to adjust balance: ${e.localizedMessage}")
            }
        }
    }

    // --- 1-Tap Credit Card Bill Payment ---
    fun payCreditCard(cardSummary: CreditCardCycleSummary, fromAccountUid: String, amount: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cardAcc = accounts.value.firstOrNull { it.account.uid == cardSummary.accountUid }
                val currUid = cardAcc?.currency?.uid ?: "CURRENCY_INR"
                val rate = cardAcc?.currency?.rateToBase ?: 1.0

                repository.payCreditCardBill(
                    fromAccountUid = fromAccountUid,
                    creditCardUid = cardSummary.accountUid,
                    amount = amount,
                    currencyUid = currUid,
                    rateToBase = rate,
                    note = "Payment: ${cardSummary.cardName}"
                )
                _uiMessage.value = UiMessage.Success("Paid ${String.format("%.2f", amount)} towards ${cardSummary.cardName}")
            } catch (e: Exception) {
                _uiMessage.value = UiMessage.Error("Payment failed: ${e.localizedMessage}")
            }
        }
    }

    // --- Account Management ---
    fun saveAccount(account: AccountEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.saveAccount(account)
                _uiMessage.value = UiMessage.Success("Account '${account.name}' saved")
            } catch (e: Exception) {
                _uiMessage.value = UiMessage.Error("Failed to save account: ${e.localizedMessage}")
            }
        }
    }

    fun deleteAccount(account: AccountEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val outcome = repository.deleteAccount(account)
                val msg = when (outcome) {
                    AccountDeleteOutcome.ARCHIVED -> "Account '${account.name}' archived to preserve transaction history"
                    AccountDeleteOutcome.DELETED -> "Account '${account.name}' deleted"
                }
                _uiMessage.value = UiMessage.Success(msg)
            } catch (e: Exception) {
                _uiMessage.value = UiMessage.Error("Failed to delete account: ${e.localizedMessage ?: e.message}")
            }
        }
    }

    // --- Category Management ---
    fun saveCategory(category: CategoryEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.saveCategory(category)
                _uiMessage.value = UiMessage.Success("Category saved")
            } catch (e: Exception) {
                _uiMessage.value = UiMessage.Error("Failed to save category")
            }
        }
    }

    fun deleteCategory(uid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deleteCategory(uid)
                _uiMessage.value = UiMessage.Success("Category deleted")
            } catch (e: Exception) {
                _uiMessage.value = UiMessage.Error("Failed to delete category: ${e.localizedMessage ?: e.message}")
            }
        }
    }

    // --- Currency Management ---
    fun saveCurrency(currency: CurrencyEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.saveCurrency(currency)
                _uiMessage.value = UiMessage.Success("Currency '${currency.name}' saved")
            } catch (e: Exception) {
                _uiMessage.value = UiMessage.Error("Failed to save currency: ${e.localizedMessage ?: e.message}")
            }
        }
    }

    fun deleteCurrency(currency: CurrencyEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deleteCurrency(currency)
                _uiMessage.value = UiMessage.Success("Currency '${currency.name}' deleted")
            } catch (e: Exception) {
                _uiMessage.value = UiMessage.Error("Failed to delete currency: ${e.localizedMessage ?: e.message}")
            }
        }
    }

    // --- Tag Management ---
    fun saveTag(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cleanName = if (name.startsWith("#")) name.substring(1).trim() else name.trim()
                if (cleanName.isNotEmpty()) {
                    repository.saveTag(TagEntity(uid = UUID.randomUUID().toString(), name = cleanName))
                    _uiMessage.value = UiMessage.Success("Tag #$cleanName added")
                }
            } catch (e: Exception) {
                _uiMessage.value = UiMessage.Error("Failed to add tag: ${e.localizedMessage ?: e.message}")
            }
        }
    }

    fun deleteTag(tag: TagEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deleteTag(tag)
                _uiMessage.value = UiMessage.Success("Tag deleted")
            } catch (e: Exception) {
                _uiMessage.value = UiMessage.Error("Failed to delete tag: ${e.localizedMessage ?: e.message}")
            }
        }
    }

    // --- SQLite Database Backup & Restore ---
    fun exportDatabaseToUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.exportDatabase(getApplication(), uri)
            _uiMessage.value = if (result.success) UiMessage.Success(result.message) else UiMessage.Error(result.message)
        }
    }

    fun restoreDatabaseFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.restoreDatabase(getApplication(), uri)
            _uiMessage.value = if (result.success) UiMessage.Success(result.message) else UiMessage.Error(result.message)
        }
    }
}
