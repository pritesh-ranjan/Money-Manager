package com.moneymanager.app.domain.insights

import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.data.local.entity.CategoryType
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.local.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class BaselineCalculatorTest {

    private val zone = ZoneId.systemDefault()

    private val foodCategory = CategoryEntity(
        uid = "CAT_FOOD",
        name = "Food",
        type = CategoryType.EXPENSE
    )

    private val diningSubcategory = CategoryEntity(
        uid = "CAT_DINING",
        name = "Dining",
        type = CategoryType.EXPENSE,
        parentUid = "CAT_FOOD"
    )

    private val categoriesMap = mapOf(
        foodCategory.uid to foodCategory,
        diningSubcategory.uid to diningSubcategory
    )

    @Test
    fun testResolveRootCategoryUid_rollsUpSubcategory() {
        assertEquals("CAT_FOOD", BaselineCalculator.resolveRootCategoryUid("CAT_DINING", categoriesMap))
        assertEquals("CAT_FOOD", BaselineCalculator.resolveRootCategoryUid("CAT_FOOD", categoriesMap))
        assertEquals("UNKNOWN", BaselineCalculator.resolveRootCategoryUid("UNKNOWN", categoriesMap))
    }

    @Test
    fun testCalculateBaselineWindow_strictlyExcludesEvaluationPeriod() {
        val evalDate = LocalDate.of(2026, 10, 15)
        val evalStartMillis = evalDate.atStartOfDay(zone).toInstant().toEpochMilli()

        val (baseStart, baseEnd) = BaselineCalculator.calculateBaselineWindow(
            evaluationStartMillis = evalStartMillis,
            lookbackMonths = 6,
            zone = zone
        )

        assertEquals(evalStartMillis, baseEnd)
        val startDate = LocalDate.of(2026, 4, 15)
        assertEquals(startDate.atStartOfDay(zone).toInstant().toEpochMilli(), baseStart)
    }

    @Test
    fun testCategoryBaseline_hasSufficientHistoryLogic() {
        val config = DetectorConfig(minBaselineSamples = 5)
        val now = LocalDate.of(2026, 10, 1)

        // 6 transactions spread over 3 distinct months
        val txs = listOf(
            createTx(200.0, LocalDate.of(2026, 5, 10)),
            createTx(220.0, LocalDate.of(2026, 5, 20)),
            createTx(210.0, LocalDate.of(2026, 6, 15)),
            createTx(190.0, LocalDate.of(2026, 6, 25)),
            createTx(205.0, LocalDate.of(2026, 7, 10)),
            createTx(215.0, LocalDate.of(2026, 7, 20))
        )

        val startMillis = LocalDate.of(2026, 4, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = LocalDate.of(2026, 10, 1).atStartOfDay(zone).toInstant().toEpochMilli()

        val baselines = BaselineCalculator.buildCategoryBaselines(
            transactions = txs,
            categoriesMap = categoriesMap,
            baselineStartMillis = startMillis,
            baselineEndMillis = endMillis,
            config = config,
            zone = zone
        )

        val foodBaseline = baselines["CAT_FOOD"]
        assertTrue(foodBaseline != null)
        assertTrue(foodBaseline!!.hasSufficientHistory)
        assertEquals(6, foodBaseline.sampleCount)
        assertEquals(3, foodBaseline.monthsCovered)

        // Only 2 transactions in 1 month -> insufficient history
        val sparseTxs = listOf(
            createTx(200.0, LocalDate.of(2026, 5, 10)),
            createTx(220.0, LocalDate.of(2026, 5, 20))
        )
        val sparseBaselines = BaselineCalculator.buildCategoryBaselines(
            transactions = sparseTxs,
            categoriesMap = categoriesMap,
            baselineStartMillis = startMillis,
            baselineEndMillis = endMillis,
            config = config,
            zone = zone
        )
        val sparseFood = sparseBaselines["CAT_FOOD"]
        assertFalse(sparseFood!!.hasSufficientHistory)
    }

    private fun createTx(amount: Double, date: LocalDate): TransactionEntity {
        return TransactionEntity(
            uid = java.util.UUID.randomUUID().toString(),
            type = TransactionType.EXPENSE,
            accountAmount = amount,
            baseAmount = amount,
            currencyUid = "CURRENCY_INR",
            accountUid = "ACC_1",
            categoryUid = "CAT_DINING",
            note = "Dining Out",
            timestamp = date.atStartOfDay(zone).toInstant().toEpochMilli()
        )
    }
}
