package com.moneymanager.app.ui.main

import android.app.Activity
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
import com.moneymanager.app.data.local.entity.InsightEntity
import com.moneymanager.app.data.local.entity.InsightType
import com.moneymanager.app.data.local.entity.InsightWithTransactions
import com.moneymanager.app.data.local.entity.RecurrenceDecision
import com.moneymanager.app.data.local.entity.RecurrenceFrequency
import com.moneymanager.app.data.local.entity.TagEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.local.entity.TransactionType
import com.moneymanager.app.data.local.entity.TransactionWithDetails
import com.moneymanager.app.domain.BackupRestoreResult
import com.moneymanager.app.domain.CreditCardCycleSummary
import com.moneymanager.app.domain.MonthlyMoneyStory
import com.moneymanager.app.domain.MonthlyMoneyStoryEngine
import com.moneymanager.app.domain.NetWorthSummary
import com.moneymanager.app.domain.insights.DetectorConfig
import com.moneymanager.app.domain.insights.InsightPreferencesManager
import com.moneymanager.app.work.InsightWorkScheduler
import com.moneymanager.app.data.StatementRepository
import com.moneymanager.app.data.local.entity.GmailImportHistoryEntity
import com.moneymanager.app.data.local.entity.GmailSyncStateEntity
import com.moneymanager.app.data.local.entity.GmailSyncStatus
import com.moneymanager.app.data.local.entity.AccountType
import com.moneymanager.app.data.local.entity.CardStatementSummaryRecord
import com.moneymanager.app.data.local.entity.CardStatementTransactionEntity
import com.moneymanager.app.domain.cards.CreditCardMappingStorage
import com.moneymanager.app.domain.gmail.filter.GmailSyncRules
import com.moneymanager.app.domain.gmail.filter.GmailSyncRulesStorage
import com.moneymanager.app.domain.gmail.sync.PasswordUnlockOutcome
import com.moneymanager.app.domain.statement.reconciliation.CardStatementReconciler
import com.moneymanager.app.domain.statement.reconciliation.StatementReconciliationReport
import com.moneymanager.app.domain.statement.core.StatementParserRegistry
import com.moneymanager.app.domain.statement.core.StatementSecurityException
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import com.moneymanager.app.domain.gmail.auth.GmailOAuthManager
import com.moneymanager.app.domain.gmail.auth.GmailTokenStorage
import com.moneymanager.app.domain.gmail.auth.GoogleOAuthCredentialsParser
import com.moneymanager.app.domain.gmail.auth.OAuthResult
import com.moneymanager.app.domain.gmail.sync.GmailSyncEngine
import com.moneymanager.app.domain.gmail.sync.SyncOutcome
import com.moneymanager.app.work.GmailWorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class UiMessage {
    data class Success(val message: String) : UiMessage()
    data class Error(val message: String) : UiMessage()
}

enum class DateFilterPreset(val label: String) {
    YESTERDAY("Yesterday"),
    PAST_7_DAYS("Past 7 days"),
    PAST_30_DAYS("Past 30 days"),
    THIS_MONTH("This month"),
    CUSTOM("Custom range")
}

data class TransactionFilterState(
    val query: String = "",
    val searchNoteOnly: Boolean = false,
    val selectedTypes: Set<TransactionType> = emptySet(),
    val selectedAccountUids: Set<String> = emptySet(),
    val selectedCategoryUids: Set<String> = emptySet(),
    val selectedTagUids: Set<String> = emptySet(),
    val datePreset: DateFilterPreset? = null,
    val customStartDate: LocalDate? = null,
    val customEndDate: LocalDate? = null
) {
    val hasActiveFilters: Boolean
        get() = query.isNotBlank() ||
            selectedTypes.isNotEmpty() ||
            selectedAccountUids.isNotEmpty() ||
            selectedCategoryUids.isNotEmpty() ||
            selectedTagUids.isNotEmpty() ||
            datePreset != null

    val activeFilterCount: Int
        get() {
            var count = 0
            if (query.isNotBlank()) count++
            if (selectedTypes.isNotEmpty()) count += selectedTypes.size
            if (selectedAccountUids.isNotEmpty()) count += selectedAccountUids.size
            if (selectedCategoryUids.isNotEmpty()) count += selectedCategoryUids.size
            if (selectedTagUids.isNotEmpty()) count += selectedTagUids.size
            if (datePreset != null) count++
            return count
        }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application, viewModelScope)
    private val repository = MoneyManagerRepository(db.moneyManagerDao(), db.insightsDao())
    val statementRepository = StatementRepository(db.statementDao())
    val gmailImportDao = db.gmailImportDao()
    val gmailSyncDao = db.gmailSyncDao()
    val gmailOAuthManager = GmailOAuthManager()
    val gmailSyncEngine = GmailSyncEngine(oauthManager = gmailOAuthManager)

    val gmailSyncState: StateFlow<GmailSyncStateEntity?> = gmailSyncDao.getSyncState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val gmailImportHistory: StateFlow<List<GmailImportHistoryEntity>> = gmailImportDao.getAllHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isGmailSyncing = MutableStateFlow(false)
    val isGmailSyncing: StateFlow<Boolean> = _isGmailSyncing.asStateFlow()

    // Current selected month for ledger
    private val _selectedYearMonth = MutableStateFlow(YearMonth.now())
    val selectedYearMonth: StateFlow<YearMonth> = _selectedYearMonth.asStateFlow()

    // Comprehensive Transaction Multi-Criteria Filter State
    private val _filterState = MutableStateFlow(TransactionFilterState())
    val filterState: StateFlow<TransactionFilterState> = _filterState.asStateFlow()

    // Search query for ledger (synchronized with filterState)
    val searchQuery: StateFlow<String> = _filterState
        .map { it.query }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    // Filter by tag (synchronized with filterState)
    val selectedTag: StateFlow<String?> = _filterState
        .map { it.selectedTagUids.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // UI Feedback Message
    private val _uiMessage = MutableStateFlow<UiMessage?>(null)
    val uiMessage: StateFlow<UiMessage?> = _uiMessage.asStateFlow()

    // Statement PDF Passwords
    private val _pdfPasswords = MutableStateFlow<List<String>>(emptyList())
    val pdfPasswords: StateFlow<List<String>> = _pdfPasswords.asStateFlow()

    // Google OAuth Custom Credentials Configured State (Boolean only - zero credentials exposed)
    private val _hasCustomOAuthCredentials = MutableStateFlow<Boolean>(false)
    val hasCustomOAuthCredentials: StateFlow<Boolean> = _hasCustomOAuthCredentials.asStateFlow()

    // Sync Statements Since (Month-Year)
    private val _syncSinceYearMonth = MutableStateFlow<YearMonth>(YearMonth.now().minusMonths(3))
    val syncSinceYearMonth: StateFlow<YearMonth> = _syncSinceYearMonth.asStateFlow()

    // Credit Card Mappings (card key -> account uid)
    private val _cardMappings = MutableStateFlow<Map<String, String>>(emptyMap())
    val cardMappings: StateFlow<Map<String, String>> = _cardMappings.asStateFlow()

    // Gmail Sync Rules & Filters
    private val _gmailSyncRules = MutableStateFlow<GmailSyncRules>(GmailSyncRules())
    val gmailSyncRules: StateFlow<GmailSyncRules> = _gmailSyncRules.asStateFlow()

    init {
        loadPdfPasswords()
        loadOAuthCredentialsState()
        loadSyncSinceState()
        loadCardMappings()
        loadGmailSyncRules()
    }

    fun loadSyncSinceState() {
        val context = getApplication<Application>()
        _syncSinceYearMonth.value = GmailTokenStorage.getSyncSinceYearMonth(context)
    }

    fun updateSyncSinceYearMonth(yearMonth: YearMonth) {
        val context = getApplication<Application>()
        GmailTokenStorage.setSyncSinceYearMonth(context, yearMonth)
        _syncSinceYearMonth.value = yearMonth
        if (GmailTokenStorage.isConnected(context)) {
            triggerManualGmailSync()
        }
    }

    fun loadOAuthCredentialsState() {
        val context = getApplication<Application>()
        _hasCustomOAuthCredentials.value = GmailTokenStorage.hasCustomCredentials(context)
    }

    fun loadPdfPasswords() {
        val context = getApplication<Application>()
        _pdfPasswords.value = GmailTokenStorage.getPdfPasswords(context)
    }

    fun addPdfPassword(password: String) {
        val clean = password.trim()
        if (clean.isNotBlank()) {
            val context = getApplication<Application>()
            GmailTokenStorage.addPdfPassword(context, clean)
            loadPdfPasswords()
        }
    }

    fun removePdfPassword(password: String) {
        val context = getApplication<Application>()
        GmailTokenStorage.removePdfPassword(context, password)
        loadPdfPasswords()
    }

    fun loadCardMappings() {
        val context = getApplication<Application>()
        _cardMappings.value = CreditCardMappingStorage.getAllMappings(context)
    }

    fun setCardMapping(bankId: String, cardMasked: String, accountUid: String) {
        val context = getApplication<Application>()
        CreditCardMappingStorage.saveCardMapping(context, bankId, cardMasked, accountUid)
        loadCardMappings()
        _uiMessage.value = UiMessage.Success("Card mapped successfully")
    }

    fun removeCardMapping(bankId: String, cardMasked: String) {
        val context = getApplication<Application>()
        CreditCardMappingStorage.removeCardMapping(context, bankId, cardMasked)
        loadCardMappings()
        _uiMessage.value = UiMessage.Success("Card mapping removed")
    }

    fun loadGmailSyncRules() {
        val context = getApplication<Application>()
        _gmailSyncRules.value = GmailSyncRulesStorage.getRules(context)
    }

    fun updateGmailSyncRules(rules: GmailSyncRules) {
        val context = getApplication<Application>()
        GmailSyncRulesStorage.saveRules(context, rules)
        _gmailSyncRules.value = rules
        _uiMessage.value = UiMessage.Success("Sync rules updated")
    }

    fun ignoreEmailSubjectAndCleanHistory(subjectKeyword: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val updated = GmailSyncRulesStorage.addIgnoredSubject(context, subjectKeyword)
            _gmailSyncRules.value = updated

            val history = gmailImportDao.getAllHistoryRaw()
            val toDelete = history.filter {
                it.subject.contains(subjectKeyword, ignoreCase = true) ||
                    it.fileName.contains(subjectKeyword, ignoreCase = true)
            }
            for (del in toDelete) {
                gmailImportDao.deleteByMessageId(del.messageId)
                gmailSyncEngine.getPendingPdfFile(context, del.messageId).delete()
            }
            _uiMessage.value = UiMessage.Success("Added rule to ignore '${subjectKeyword}' and cleaned matching items")
        }
    }

    fun deleteImportHistoryItem(messageId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            gmailImportDao.deleteByMessageId(messageId)
            gmailSyncEngine.getPendingPdfFile(context, messageId).delete()
            _uiMessage.value = UiMessage.Success("Statement removed from history")
        }
    }

    fun unlockGmailHistoryStatement(
        item: GmailImportHistoryEntity,
        password: String,
        onResult: (PasswordUnlockOutcome) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val outcome = gmailSyncEngine.unlockHistoryItemWithPassword(
                context = context,
                item = item,
                password = password,
                statementRepository = statementRepository,
                importDao = gmailImportDao
            )
            withContext(Dispatchers.Main) {
                if (outcome is PasswordUnlockOutcome.Success) {
                    loadPdfPasswords()
                    _uiMessage.value = UiMessage.Success(outcome.message)
                }
                onResult(outcome)
            }
        }
    }

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

    val statementSummaries: StateFlow<List<CardStatementSummaryRecord>> = statementRepository.getStatementSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unbilledCardSpends: StateFlow<List<TransactionWithDetails>> = allTransactionsUnfiltered.map { list ->
        list.filter { it.account.accountType == AccountType.CREDIT_CARD && it.transaction.type == TransactionType.EXPENSE }
            .sortedByDescending { it.transaction.timestamp }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    // --- Insights Engine Flows ---
    val activeInsights: StateFlow<List<InsightWithTransactions>> = repository.activeInsights
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val detectorConfig: StateFlow<DetectorConfig> = InsightPreferencesManager.config

    val monthlySubscriptionsTotal: StateFlow<Double> = activeInsights.map { list ->
        list.filter {
            it.insight.type in setOf(
                InsightType.SUBSCRIPTION,
                InsightType.FORGOTTEN_SUBSCRIPTION,
                InsightType.PRICE_INCREASE
            )
        }.sumOf { item ->
            val amt = item.insight.amount ?: 0.0
            val freq = item.insight.frequency ?: RecurrenceFrequency.MONTHLY
            when (freq) {
                RecurrenceFrequency.WEEKLY -> amt * 4.33
                RecurrenceFrequency.FORTNIGHTLY -> amt * 2.16
                RecurrenceFrequency.MONTHLY -> amt
                RecurrenceFrequency.QUARTERLY -> amt / 3.0
                RecurrenceFrequency.YEARLY -> amt / 12.0
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val monthlyRecurringTransfersTotal: StateFlow<Double> = activeInsights.map { list ->
        list.filter {
            it.insight.type == InsightType.RECURRING_TRANSFER
        }.sumOf { item ->
            val amt = item.insight.amount ?: 0.0
            val freq = item.insight.frequency ?: RecurrenceFrequency.MONTHLY
            when (freq) {
                RecurrenceFrequency.WEEKLY -> amt * 4.33
                RecurrenceFrequency.FORTNIGHTLY -> amt * 2.16
                RecurrenceFrequency.MONTHLY -> amt
                RecurrenceFrequency.QUARTERLY -> amt / 3.0
                RecurrenceFrequency.YEARLY -> amt / 12.0
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Multi-criteria & Month filtered transactions
    val transactions: StateFlow<List<TransactionWithDetails>> = combine(
        repository.allTransactions,
        _selectedYearMonth,
        _filterState
    ) { allTxs, ym, filter ->
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()

        val (startMillis, endMillis) = when (filter.datePreset) {
            DateFilterPreset.YESTERDAY -> {
                val y = today.minusDays(1)
                Pair(
                    y.atStartOfDay(zone).toInstant().toEpochMilli(),
                    y.atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
                )
            }
            DateFilterPreset.PAST_7_DAYS -> {
                val start = today.minusDays(6)
                Pair(
                    start.atStartOfDay(zone).toInstant().toEpochMilli(),
                    today.atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
                )
            }
            DateFilterPreset.PAST_30_DAYS -> {
                val start = today.minusDays(29)
                Pair(
                    start.atStartOfDay(zone).toInstant().toEpochMilli(),
                    today.atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
                )
            }
            DateFilterPreset.THIS_MONTH -> {
                val currentYm = YearMonth.now()
                Pair(
                    currentYm.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
                    currentYm.atEndOfMonth().atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
                )
            }
            DateFilterPreset.CUSTOM -> {
                val start = filter.customStartDate ?: today.minusMonths(1)
                val end = filter.customEndDate ?: today
                Pair(
                    start.atStartOfDay(zone).toInstant().toEpochMilli(),
                    end.atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
                )
            }
            null -> {
                Pair(
                    ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
                    ym.atEndOfMonth().atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
                )
            }
        }

        allTxs.filter { item ->
            val matchesDate = item.transaction.timestamp in startMillis..endMillis
            if (!matchesDate) return@filter false

            if (filter.selectedTypes.isNotEmpty() && !filter.selectedTypes.contains(item.transaction.type)) {
                return@filter false
            }

            if (filter.selectedAccountUids.isNotEmpty()) {
                val inAccount = filter.selectedAccountUids.contains(item.account.uid) ||
                    (item.toAccount != null && filter.selectedAccountUids.contains(item.toAccount.uid))
                if (!inAccount) return@filter false
            }

            if (filter.selectedCategoryUids.isNotEmpty()) {
                val cat = item.category
                val catUid = cat?.uid
                val parentCatUid = cat?.parentUid
                val matchesCategory = (catUid != null && filter.selectedCategoryUids.contains(catUid)) ||
                    (parentCatUid != null && filter.selectedCategoryUids.contains(parentCatUid))
                if (!matchesCategory) return@filter false
            }

            if (filter.selectedTagUids.isNotEmpty()) {
                val hasTag = item.tags.any {
                    filter.selectedTagUids.contains(it.uid) || filter.selectedTagUids.contains(it.name)
                }
                if (!hasTag) return@filter false
            }

            if (filter.query.isNotBlank()) {
                val q = filter.query.trim()
                if (filter.searchNoteOnly) {
                    if (!item.transaction.note.contains(q, ignoreCase = true)) return@filter false
                } else {
                    val matchesQuery = item.transaction.note.contains(q, ignoreCase = true) ||
                        item.transaction.description.contains(q, ignoreCase = true) ||
                        item.category?.name?.contains(q, ignoreCase = true) == true ||
                        item.account.name.contains(q, ignoreCase = true) ||
                        (item.toAccount?.name?.contains(q, ignoreCase = true) == true) ||
                        item.tags.any { it.name.contains(q, ignoreCase = true) }
                    if (!matchesQuery) return@filter false
                }
            }

            true
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
        _filterState.value = _filterState.value.copy(query = q)
    }

    fun setSelectedTag(tag: String?) {
        _filterState.value = _filterState.value.copy(
            selectedTagUids = if (tag != null) setOf(tag) else emptySet()
        )
    }

    fun setFilterState(filter: TransactionFilterState) {
        _filterState.value = filter
    }

    fun updateFilterState(block: (TransactionFilterState) -> TransactionFilterState) {
        _filterState.value = block(_filterState.value)
    }

    fun clearAllFilters() {
        _filterState.value = TransactionFilterState()
    }

    fun toggleFilterType(type: TransactionType) {
        updateFilterState { state ->
            val newTypes = if (state.selectedTypes.contains(type)) {
                state.selectedTypes - type
            } else {
                state.selectedTypes + type
            }
            state.copy(selectedTypes = newTypes)
        }
    }

    fun setFilterTypes(types: Set<TransactionType>) {
        updateFilterState { it.copy(selectedTypes = types) }
    }

    fun toggleFilterAccount(accountUid: String) {
        updateFilterState { state ->
            val newAccounts = if (state.selectedAccountUids.contains(accountUid)) {
                state.selectedAccountUids - accountUid
            } else {
                state.selectedAccountUids + accountUid
            }
            state.copy(selectedAccountUids = newAccounts)
        }
    }

    fun setFilterAccounts(accountUids: Set<String>) {
        updateFilterState { it.copy(selectedAccountUids = accountUids) }
    }

    fun toggleFilterCategory(categoryUid: String) {
        updateFilterState { state ->
            val newCats = if (state.selectedCategoryUids.contains(categoryUid)) {
                state.selectedCategoryUids - categoryUid
            } else {
                state.selectedCategoryUids + categoryUid
            }
            state.copy(selectedCategoryUids = newCats)
        }
    }

    fun setFilterCategories(categoryUids: Set<String>) {
        updateFilterState { it.copy(selectedCategoryUids = categoryUids) }
    }

    fun toggleFilterTag(tagUid: String) {
        updateFilterState { state ->
            val newTags = if (state.selectedTagUids.contains(tagUid)) {
                state.selectedTagUids - tagUid
            } else {
                state.selectedTagUids + tagUid
            }
            state.copy(selectedTagUids = newTags)
        }
    }

    fun setFilterTags(tagUids: Set<String>) {
        updateFilterState { it.copy(selectedTagUids = tagUids) }
    }

    fun setDatePreset(preset: DateFilterPreset?) {
        updateFilterState { it.copy(datePreset = preset) }
    }

    fun setCustomDateRange(start: LocalDate?, end: LocalDate?) {
        updateFilterState {
            it.copy(datePreset = DateFilterPreset.CUSTOM, customStartDate = start, customEndDate = end)
        }
    }

    fun setSearchNoteOnly(noteOnly: Boolean) {
        updateFilterState { it.copy(searchNoteOnly = noteOnly) }
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
                runAnalysisNow()
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
                runAnalysisNow()
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
            if (result.success) {
                runAnalysisNow()
            }
            _uiMessage.value = if (result.success) UiMessage.Success(result.message) else UiMessage.Error(result.message)
        }
    }

    // --- Insights Engine User Actions ---
    fun confirmRecurrence(insight: InsightEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val targetKey = insight.targetKey ?: return@launch
            repository.saveRecurrenceDecision(targetKey, RecurrenceDecision.CONFIRMED)
            runAnalysisNow()
            _uiMessage.value = UiMessage.Success("Confirmed recurrence: ${insight.title}")
        }
    }

    fun rejectRecurrence(insight: InsightEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val targetKey = insight.targetKey ?: return@launch
            repository.saveRecurrenceDecision(targetKey, RecurrenceDecision.REJECTED)
            runAnalysisNow()
            _uiMessage.value = UiMessage.Success("Marked not recurring: ${insight.title}")
        }
    }

    fun deleteDuplicate(transactionUid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deleteTransaction(transactionUid)
                runAnalysisNow()
                _uiMessage.value = UiMessage.Success("Duplicate transaction deleted")
            } catch (e: Exception) {
                _uiMessage.value = UiMessage.Error("Failed to delete duplicate: ${e.localizedMessage}")
            }
        }
    }

    fun runAnalysisNow() {
        InsightWorkScheduler.runNow(getApplication())
    }

    fun updateDetectorConfig(config: DetectorConfig) {
        InsightPreferencesManager.updateConfig(config)
        runAnalysisNow()
    }

    // --- Gmail Statement Importer Actions ---

    fun startGmailOAuth(activity: Activity) {
        viewModelScope.launch(Dispatchers.IO) {
            _isGmailSyncing.value = true
            when (val result = gmailOAuthManager.startLoopbackOAuthFlow(activity)) {
                is OAuthResult.Success -> {
                    val email = gmailSyncEngine.connectAndInitializeBaseline(
                        context = getApplication(),
                        importDao = gmailImportDao,
                        syncDao = gmailSyncDao
                    )
                    GmailWorkScheduler.schedulePeriodic(getApplication())
                    _uiMessage.value = UiMessage.Success("Connected to Gmail: ${email ?: "Account linked"}")
                }
                is OAuthResult.Error -> {
                    _uiMessage.value = UiMessage.Error("Connection failed: ${result.message}")
                }
            }
            _isGmailSyncing.value = false
        }
    }

    fun handleGmailOAuthRedirect(uri: Uri) {
        if (uri.getQueryParameter("status") == "done") {
            // Returned from loopback browser confirmation
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _isGmailSyncing.value = true
            when (val result = gmailOAuthManager.handleRedirectUri(getApplication(), uri)) {
                is OAuthResult.Success -> {
                    val email = gmailSyncEngine.connectAndInitializeBaseline(
                        context = getApplication(),
                        importDao = gmailImportDao,
                        syncDao = gmailSyncDao
                    )
                    GmailWorkScheduler.schedulePeriodic(getApplication())
                    _uiMessage.value = UiMessage.Success("Connected to Gmail: ${email ?: "Account linked"}")
                }
                is OAuthResult.Error -> {
                    _uiMessage.value = UiMessage.Error("Connection failed: ${result.message}")
                }
            }
            _isGmailSyncing.value = false
        }
    }

    fun triggerManualGmailSync() {
        viewModelScope.launch(Dispatchers.IO) {
            _isGmailSyncing.value = true
            when (val outcome = gmailSyncEngine.syncIncremental(
                context = getApplication(),
                importDao = gmailImportDao,
                syncDao = gmailSyncDao,
                statementRepository = statementRepository
            )) {
                is SyncOutcome.Success -> {
                    if (outcome.newStatementsCount > 0) {
                        _uiMessage.value = UiMessage.Success("Imported ${outcome.newStatementsCount} new statement(s)")
                    } else {
                        _uiMessage.value = UiMessage.Success("Sync complete. No new bank statements found.")
                    }
                }
                is SyncOutcome.Error -> {
                    _uiMessage.value = UiMessage.Error("Sync error: ${outcome.message}")
                }
            }
            _isGmailSyncing.value = false
        }
    }

    fun disconnectGmail() {
        viewModelScope.launch(Dispatchers.IO) {
            gmailOAuthManager.disconnectAndRevoke(getApplication())
            gmailSyncDao.clearSyncState()
            GmailWorkScheduler.cancelPeriodic(getApplication())
            _uiMessage.value = UiMessage.Success("Disconnected and revoked Gmail access")
        }
    }

    fun updateGmailAutoSyncEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            gmailSyncDao.setAutoSyncEnabled(enabled)
            if (enabled) {
                GmailWorkScheduler.schedulePeriodic(getApplication())
            } else {
                GmailWorkScheduler.cancelPeriodic(getApplication())
            }
            _uiMessage.value = UiMessage.Success(if (enabled) "Daily background sync enabled" else "Daily background sync disabled")
        }
    }

    fun saveDefaultPdfPassword(password: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            GmailTokenStorage.saveDefaultPdfPassword(getApplication(), password)
            gmailSyncDao.setDefaultPdfPassword(password)
            loadPdfPasswords()
            _uiMessage.value = UiMessage.Success("Statement password saved")
        }
    }

    fun saveCustomOAuthCredentials(clientId: String?, clientSecret: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            GmailTokenStorage.saveClientCredentials(getApplication(), clientId, clientSecret)
            loadOAuthCredentialsState()
            _uiMessage.value = UiMessage.Success("OAuth credentials updated and encrypted")
        }
    }

    fun clearCustomOAuthCredentials() {
        viewModelScope.launch(Dispatchers.IO) {
            GmailTokenStorage.clearClientCredentials(getApplication())
            loadOAuthCredentialsState()
            _uiMessage.value = UiMessage.Success("OAuth credentials removed")
        }
    }

    fun importOAuthCredentialsFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val content = getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } ?: throw IllegalArgumentException("Could not read credentials file from storage.")

                val parsed = GoogleOAuthCredentialsParser.parse(content)
                GmailTokenStorage.saveClientCredentials(getApplication(), parsed.clientId, parsed.clientSecret)
                loadOAuthCredentialsState()
                _uiMessage.value = UiMessage.Success("Google OAuth credentials imported and encrypted successfully")
            } catch (e: Exception) {
                _uiMessage.value = UiMessage.Error("Failed to import credentials: ${e.message}")
            }
        }
    }

    fun clearGmailImportHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            gmailImportDao.clearHistory()
            _uiMessage.value = UiMessage.Success("Statement import history cleared")
        }
    }

    fun getStatementTransactions(statementId: String): Flow<List<CardStatementTransactionEntity>> {
        return statementRepository.getTransactionsByStatementId(statementId)
    }

    fun markStatementPaid(statementId: String, isPaid: Boolean) {
        viewModelScope.launch {
            statementRepository.setStatementPaidStatus(statementId, isPaid)
            _uiMessage.value = UiMessage.Success(if (isPaid) "Statement marked as paid" else "Statement marked as unpaid")
        }
    }

    fun payStatementBill(
        summary: CardStatementSummaryRecord,
        fromAccountUid: String,
        amount: Double
    ) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val mappedAccountUid = CreditCardMappingStorage.getMappedAccountUid(
                context,
                summary.bankId,
                summary.accountOrCardNumberMasked
            )
            val toAccount = if (!mappedAccountUid.isNullOrBlank()) {
                accounts.value.firstOrNull { it.account.uid == mappedAccountUid }?.account
            } else {
                accounts.value.firstOrNull {
                    it.account.accountType == AccountType.CREDIT_CARD &&
                        (it.account.name.contains(summary.accountOrCardNumberMasked.takeLast(4)) ||
                            summary.bankName.contains(it.account.name, ignoreCase = true) ||
                            it.account.name.contains(summary.bankName, ignoreCase = true))
                }?.account
            }

            if (toAccount != null) {
                val paymentTx = TransactionEntity(
                    uid = UUID.randomUUID().toString(),
                    type = TransactionType.TRANSFER,
                    accountAmount = amount,
                    baseAmount = amount,
                    currencyUid = toAccount.currencyUid,
                    accountUid = fromAccountUid,
                    toAccountUid = toAccount.uid,
                    toAccountAmount = amount,
                    note = "Credit Card Bill Payment",
                    description = "${summary.bankName} (${summary.accountOrCardNumberMasked.takeLast(4)}) Statement ${summary.statementYearMonth}",
                    timestamp = System.currentTimeMillis()
                )
                repository.saveTransaction(paymentTx)
            } else {
                val fromAccount = accounts.value.firstOrNull { it.account.uid == fromAccountUid }?.account
                if (fromAccount != null) {
                    val paymentTx = TransactionEntity(
                        uid = UUID.randomUUID().toString(),
                        type = TransactionType.EXPENSE,
                        accountAmount = amount,
                        baseAmount = amount,
                        currencyUid = fromAccount.currencyUid,
                        accountUid = fromAccountUid,
                        note = "Bill Payment: ${summary.bankName}",
                        description = "Statement ${summary.statementYearMonth} for ${summary.accountOrCardNumberMasked}",
                        timestamp = System.currentTimeMillis()
                    )
                    repository.saveTransaction(paymentTx)
                }
            }
            statementRepository.setStatementPaidStatus(summary.statementId, isPaid = true)
            _uiMessage.value = UiMessage.Success("Paid bill of ₹${String.format(Locale.getDefault(), "%.2f", amount)} for ${summary.bankName}")
        }
    }

    suspend fun getReconciliationReport(summary: CardStatementSummaryRecord): StatementReconciliationReport {
        val context = getApplication<Application>()
        val stmtTxs = statementRepository.getTransactionsByStatementIdRaw(summary.statementId)
        val mappedAccountUid = CreditCardMappingStorage.getMappedAccountUid(
            context,
            summary.bankId,
            summary.accountOrCardNumberMasked
        )

        val manualCardTxs = if (!mappedAccountUid.isNullOrBlank()) {
            allTransactionsUnfiltered.value.filter { it.account.uid == mappedAccountUid }
        } else {
            val last4 = summary.accountOrCardNumberMasked.replace("[^0-9]".toRegex(), "").takeLast(4)
            val bankLower = summary.bankName.lowercase()
            allTransactionsUnfiltered.value.filter {
                it.account.accountType == AccountType.CREDIT_CARD && (
                    (last4.isNotEmpty() && it.account.name.contains(last4)) ||
                    summary.bankName.contains(it.account.name, ignoreCase = true) ||
                    it.account.name.contains(summary.bankName, ignoreCase = true) ||
                    (bankLower.contains("hdfc") && it.account.name.contains("hdfc", ignoreCase = true)) ||
                    (bankLower.contains("icici") && it.account.name.contains("icici", ignoreCase = true)) ||
                    (bankLower.contains("axis") && it.account.name.contains("axis", ignoreCase = true)) ||
                    (bankLower.contains("baroda") && it.account.name.contains("baroda", ignoreCase = true)) ||
                    (bankLower.contains("federal") && it.account.name.contains("federal", ignoreCase = true))
                )
            }
        }

        val firstTx = stmtTxs.firstOrNull()
        val periodStart = summary.billingPeriodStartEpochDay ?: firstTx?.billingPeriodStartEpochDay ?: stmtTxs.minOfOrNull { it.transactionDateEpochDay }
        val periodEnd = summary.billingPeriodEndEpochDay ?: firstTx?.billingPeriodEndEpochDay ?: stmtTxs.maxOfOrNull { it.transactionDateEpochDay }

        return CardStatementReconciler.reconcile(
            statementId = summary.statementId,
            cardMasked = summary.accountOrCardNumberMasked,
            statementTransactions = stmtTxs,
            manualTransactions = manualCardTxs,
            periodStartEpochDay = periodStart,
            periodEndEpochDay = periodEnd
        )
    }

    fun addStatementTransactionToManualLedger(
        stmtTx: CardStatementTransactionEntity,
        accountUid: String,
        categoryUid: String? = null
    ) {
        viewModelScope.launch {
            val acc = accounts.value.firstOrNull { it.account.uid == accountUid }?.account
            val currencyUid = acc?.currencyUid ?: "curr_inr"
            val tx = TransactionEntity(
                uid = UUID.randomUUID().toString(),
                type = if (stmtTx.isCredit) TransactionType.INCOME else TransactionType.EXPENSE,
                accountAmount = stmtTx.amount,
                baseAmount = stmtTx.amount,
                currencyUid = currencyUid,
                accountUid = accountUid,
                categoryUid = categoryUid,
                note = stmtTx.cleanNarration,
                description = "Ref: ${stmtTx.referenceNumber ?: ""} (From Statement)",
                timestamp = LocalDate.ofEpochDay(stmtTx.transactionDateEpochDay)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            )
            repository.saveTransaction(tx)
            _uiMessage.value = UiMessage.Success("Added to ledger: ${stmtTx.cleanNarration}")
        }
    }

    fun importStatementFromUri(
        uri: Uri,
        password: String? = null,
        onPasswordRequired: (isInvalidPassword: Boolean) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val candidatePasswords = if (!password.isNullOrBlank()) {
                listOf(password.trim())
            } else {
                GmailTokenStorage.getPdfPasswords(context)
            }

            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: run {
                        _uiMessage.value = UiMessage.Error("Unable to open PDF statement file")
                        return@launch
                    }

                val multiResult = StatementParserRegistry().parseWithPasswords(bytes.inputStream(), candidatePasswords)
                val parseResult = multiResult.result

                // If a password unlocked it, automatically persist it into the saved passwords list
                if (!multiResult.workingPassword.isNullOrBlank()) {
                    addPdfPassword(multiResult.workingPassword)
                }

                val statementId = statementRepository.importStatement(parseResult)
                if (statementId.isNotBlank()) {
                    _uiMessage.value = UiMessage.Success("Imported ${parseResult.metadata.bankName} statement (${parseResult.transactions.size} transactions)")
                } else {
                    _uiMessage.value = UiMessage.Error("Failed to parse statement transactions")
                }
            } catch (_: StatementSecurityException.PasswordRequiredException) {
                withContext(Dispatchers.Main) {
                    onPasswordRequired(false)
                }
            } catch (_: StatementSecurityException.InvalidPasswordException) {
                withContext(Dispatchers.Main) {
                    onPasswordRequired(true)
                }
            } catch (e: Exception) {
                _uiMessage.value = UiMessage.Error("Failed to import PDF statement: ${e.message}")
            }
        }
    }
}
