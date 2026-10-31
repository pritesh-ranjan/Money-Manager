package com.moneymanager.app.domain.insights

import com.moneymanager.app.data.local.entity.InsightSeverity
import com.moneymanager.app.data.local.entity.InsightType
import com.moneymanager.app.data.local.entity.RecurrenceDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoTaggingEngineTest {

    @Test
    fun testEvaluate_autoTagsHighConfidenceRecurrences() {
        val subscriptionInsight = DetectedInsight(
            id = "SUB:netflix",
            type = InsightType.SUBSCRIPTION,
            severity = InsightSeverity.INFO,
            title = "Recurring Subscription: Netflix",
            summary = "Charges ₹649 monthly",
            targetKey = "netflix",
            amount = 649.0,
            confidence = 0.88, // >= 0.80
            autoAssignedTag = "Subscription",
            transactionUids = listOf("TX_1", "TX_2", "TX_3")
        )

        val sipInsight = DetectedInsight(
            id = "TXF:parag_parikh",
            type = InsightType.RECURRING_TRANSFER,
            severity = InsightSeverity.INFO,
            title = "Recurring SIP",
            summary = "Charges ₹10,000 monthly",
            targetKey = "parag_parikh",
            amount = 10000.0,
            confidence = 0.92, // >= 0.80
            autoAssignedTag = "SIP",
            transactionUids = listOf("TX_4", "TX_5")
        )

        val instructions = AutoTaggingEngine.evaluate(
            detectedInsights = listOf(subscriptionInsight, sipInsight),
            decisionsMap = emptyMap(),
            config = DetectorConfig(autoTagMinConfidence = 0.80, autoTagEnabled = true)
        )

        assertEquals(2, instructions.size)
        val subInstruction = instructions.first { it.tagName == "Subscription" }
        assertFalse(subInstruction.isRemoval)
        assertEquals(listOf("TX_1", "TX_2", "TX_3"), subInstruction.transactionUids)

        val sipInstruction = instructions.first { it.tagName == "SIP" }
        assertFalse(sipInstruction.isRemoval)
        assertEquals(listOf("TX_4", "TX_5"), sipInstruction.transactionUids)
    }

    @Test
    fun testEvaluate_removesTagWhenRejected() {
        val subscriptionInsight = DetectedInsight(
            id = "SUB:gym",
            type = InsightType.SUBSCRIPTION,
            severity = InsightSeverity.INFO,
            title = "Recurring Subscription: Gym",
            summary = "Charges ₹1,500 monthly",
            targetKey = "gym",
            amount = 1500.0,
            confidence = 0.85,
            autoAssignedTag = "Subscription",
            transactionUids = listOf("TX_GYM_1", "TX_GYM_2")
        )

        val decisions = mapOf("gym" to RecurrenceDecision.REJECTED)

        val instructions = AutoTaggingEngine.evaluate(
            detectedInsights = listOf(subscriptionInsight),
            decisionsMap = decisions,
            config = DetectorConfig()
        )

        assertEquals(1, instructions.size)
        val instruction = instructions.first()
        assertTrue("Must be removal instruction", instruction.isRemoval)
        assertEquals("Subscription", instruction.tagName)
        assertEquals(listOf("TX_GYM_1", "TX_GYM_2"), instruction.transactionUids)
    }

    @Test
    fun testEvaluate_doesNothingWhenAutoTaggingDisabled() {
        val subscriptionInsight = DetectedInsight(
            id = "SUB:netflix",
            type = InsightType.SUBSCRIPTION,
            severity = InsightSeverity.INFO,
            title = "Netflix",
            summary = "Netflix",
            targetKey = "netflix",
            amount = 649.0,
            confidence = 0.95,
            autoAssignedTag = "Subscription",
            transactionUids = listOf("TX_1")
        )

        val instructions = AutoTaggingEngine.evaluate(
            detectedInsights = listOf(subscriptionInsight),
            decisionsMap = emptyMap(),
            config = DetectorConfig(autoTagEnabled = false)
        )

        assertTrue(instructions.isEmpty())
    }
}
