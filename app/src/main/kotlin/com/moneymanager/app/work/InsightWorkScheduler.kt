package com.moneymanager.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object InsightWorkScheduler {

    private const val UNIQUE_PERIODIC_WORK = "periodic_insight_reconciliation"
    private const val UNIQUE_IMMEDIATE_WORK = "immediate_insight_reconciliation"

    fun schedulePeriodic(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<InsightReconciliationWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )
    }

    fun runNow(context: Context) {
        val oneTimeRequest = OneTimeWorkRequestBuilder<InsightReconciliationWorker>()
            .setInitialDelay(3, TimeUnit.SECONDS) // 3-second debounce for batch edits
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_IMMEDIATE_WORK,
            ExistingWorkPolicy.REPLACE,
            oneTimeRequest
        )
    }
}
