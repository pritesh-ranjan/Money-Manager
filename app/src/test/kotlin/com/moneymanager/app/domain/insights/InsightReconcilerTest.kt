package com.moneymanager.app.domain.insights

import com.moneymanager.app.data.FakeInsightsDao
import com.moneymanager.app.data.FakeMoneyManagerDao
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.AccountType
import com.moneymanager.app.data.local.entity.CurrencyEntity
import com.moneymanager.app.data.local.entity.InsightStatus
import com.moneymanager.app.data.local.entity.RecurrenceDecision
import com.moneymanager.app.data.local.entity.RecurrenceDecisionEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.local.entity.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class InsightReconcilerTest {

    private val zone = ZoneId.systemDefault()
    private lateinit var moneyDao: FakeMoneyManagerDao
    private lateinit var insightsDao: FakeInsightsDao
    private lateinit var reconciler: InsightReconciler

    private val defaultCurrency = CurrencyEntity("INR", "INR", "INR", "₹", 1.0, true)
    private val savingsAccount = AccountEntity(uid = "ACC_SAVINGS", name = "Savings", accountType = AccountType.SAVINGS, currencyUid = "INR")

    @Before
    fun setUp() = runBlocking {
        moneyDao = FakeMoneyManagerDao()
        insightsDao = FakeInsightsDao()
        reconciler = InsightReconciler(insightsDao, moneyDao, zone)

        moneyDao.insertCurrency(defaultCurrency)
        moneyDao.insertAccount(savingsAccount)
    }

    @Test
    fun testReconcile_firstRunInsertsInsightsAndAutoTags() = runBlocking {
        val now = LocalDate.of(2026, 10, 20)

        // Add 4 monthly Netflix charges
        val txs = listOf(
            createExpense("TX_1", 649.0, LocalDate.of(2026, 7, 5), "Netflix"),
            createExpense("TX_2", 649.0, LocalDate.of(2026, 8, 5), "Netflix"),
            createExpense("TX_3", 649.0, LocalDate.of(2026, 9, 5), "Netflix"),
            createExpense("TX_4", 649.0, LocalDate.of(2026, 10, 5), "Netflix")
        )
        moneyDao.insertTransactions(txs)

        val result = reconciler.reconcile(referenceDate = now)

        assertEquals(1, result.activeCount)
        assertEquals(0, result.resolvedCount)

        val insight = insightsDao.insightsMap["SUB:netflix"]
        assertNotNull(insight)
        assertEquals(InsightStatus.ACTIVE, insight!!.status)

        // Verify Approach A auto-tag was applied
        val subscriptionTag = insightsDao.tagsMap.values.firstOrNull { it.name == "Subscription" }
        assertNotNull("Tag 'Subscription' must be created", subscriptionTag)
        val taggedTxs = insightsDao.transactionTagsList.filter { it.tagUid == subscriptionTag!!.uid }.map { it.transactionUid }
        assertTrue(taggedTxs.containsAll(listOf("TX_1", "TX_2", "TX_3", "TX_4")))
    }

    @Test
    fun testReconcile_isStrictlyIdempotent() = runBlocking {
        val now = LocalDate.of(2026, 10, 20)
        val txs = listOf(
            createExpense("TX_1", 649.0, LocalDate.of(2026, 7, 5), "Netflix"),
            createExpense("TX_2", 649.0, LocalDate.of(2026, 8, 5), "Netflix"),
            createExpense("TX_3", 649.0, LocalDate.of(2026, 9, 5), "Netflix"),
            createExpense("TX_4", 649.0, LocalDate.of(2026, 10, 5), "Netflix")
        )
        moneyDao.insertTransactions(txs)

        // First pass
        val firstResult = reconciler.reconcile(referenceDate = now, currentTimeMillis = 1000L)
        assertEquals(1, firstResult.activeCount)
        val initialFirstDetectedAt = insightsDao.insightsMap["SUB:netflix"]!!.firstDetectedAt
        assertEquals(1000L, initialFirstDetectedAt)

        // Second pass with same data (later time)
        val secondResult = reconciler.reconcile(referenceDate = now, currentTimeMillis = 2000L)
        assertEquals(1, secondResult.activeCount)
        assertEquals(0, secondResult.resolvedCount)

        val postReconcile = insightsDao.insightsMap["SUB:netflix"]!!
        // firstDetectedAt must be preserved!
        assertEquals(initialFirstDetectedAt, postReconcile.firstDetectedAt)
        assertEquals(2000L, postReconcile.lastEvaluatedAt)
    }

    @Test
    fun testReconcile_resolvesInsightWhenDeleted() = runBlocking {
        val now = LocalDate.of(2026, 10, 20)
        val t = now.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

        // Two identical charges (duplicate)
        val tx1 = createExpense("DUP_1", 300.0, now, "Swiggy", t)
        val tx2 = createExpense("DUP_2", 300.0, now, "Swiggy", t + 60000)
        moneyDao.insertTransactions(listOf(tx1, tx2))

        val result1 = reconciler.reconcile(referenceDate = now)
        assertEquals(1, result1.activeCount)
        val dupId = insightsDao.insightsMap.keys.first { it.startsWith("DUP:") }

        // User deletes one duplicate transaction
        moneyDao.softDeleteTransaction(tx2.uid)

        val result2 = reconciler.reconcile(referenceDate = now)
        assertEquals(1, result2.resolvedCount)
        val dupInsight = insightsDao.insightsMap[dupId]
        assertEquals(InsightStatus.RESOLVED, dupInsight!!.status)
    }

    @Test
    fun testReconcile_rejectingRecurrenceRemovesAutoTag() = runBlocking {
        val now = LocalDate.of(2026, 10, 20)
        val txs = listOf(
            createExpense("TX_1", 1500.0, LocalDate.of(2026, 7, 5), "Gym"),
            createExpense("TX_2", 1500.0, LocalDate.of(2026, 8, 5), "Gym"),
            createExpense("TX_3", 1500.0, LocalDate.of(2026, 9, 5), "Gym"),
            createExpense("TX_4", 1500.0, LocalDate.of(2026, 10, 5), "Gym")
        )
        moneyDao.insertTransactions(txs)

        // First pass -> auto-tagged
        reconciler.reconcile(referenceDate = now)
        val gymTag = insightsDao.tagsMap.values.first { it.name == "Subscription" }
        assertEquals(4, insightsDao.transactionTagsList.count { it.tagUid == gymTag.uid })

        // User marks "Not Recurring" (REJECTED)
        insightsDao.saveRecurrenceDecision(
            RecurrenceDecisionEntity(targetKey = "gym", decision = RecurrenceDecision.REJECTED, decidedAt = System.currentTimeMillis())
        )

        // Next reconciliation pass
        reconciler.reconcile(referenceDate = now)

        // Tag must be removed from transactions
        val remainingTags = insightsDao.transactionTagsList.filter { it.tagUid == gymTag.uid }
        assertEquals(0, remainingTags.size)
    }

    private fun createExpense(
        uid: String,
        amount: Double,
        date: LocalDate,
        note: String,
        timestampOverride: Long? = null
    ): TransactionEntity {
        return TransactionEntity(
            uid = uid,
            type = TransactionType.EXPENSE,
            accountAmount = amount,
            baseAmount = amount,
            currencyUid = "INR",
            accountUid = "ACC_SAVINGS",
            note = note,
            timestamp = timestampOverride ?: date.atStartOfDay(zone).toInstant().toEpochMilli()
        )
    }
}
