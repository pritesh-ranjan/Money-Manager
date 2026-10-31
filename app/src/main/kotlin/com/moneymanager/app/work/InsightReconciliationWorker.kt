package com.moneymanager.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.moneymanager.app.data.local.AppDatabase
import com.moneymanager.app.domain.insights.InsightPreferencesManager
import com.moneymanager.app.domain.insights.InsightReconciler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InsightReconciliationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(applicationContext)
            val config = InsightPreferencesManager.config.value
            val reconciler = InsightReconciler(
                insightsDao = db.insightsDao(),
                moneyManagerDao = db.moneyManagerDao()
            )
            reconciler.reconcile(config = config)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
