package com.moneymanager.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.moneymanager.app.data.StatementRepository
import com.moneymanager.app.data.local.AppDatabase
import com.moneymanager.app.domain.gmail.auth.GmailTokenStorage
import com.moneymanager.app.domain.gmail.sync.GmailSyncEngine
import com.moneymanager.app.domain.gmail.sync.SyncOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GmailSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // If not connected to Gmail, exit early
        if (!GmailTokenStorage.isConnected(applicationContext)) {
            return@withContext Result.success()
        }

        val db = AppDatabase.getDatabase(applicationContext)
        val syncDao = db.gmailSyncDao()
        val syncState = syncDao.getSyncStateRaw()

        // Respect user setting if daily auto-sync is paused
        if (syncState != null && !syncState.isAutoSyncEnabled) {
            return@withContext Result.success()
        }

        val importDao = db.gmailImportDao()
        val statementRepo = StatementRepository(db.statementDao())
        val engine = GmailSyncEngine()

        when (val outcome = engine.syncIncremental(applicationContext, importDao, syncDao, statementRepo)) {
            is SyncOutcome.Success -> Result.success()
            is SyncOutcome.Error -> {
                if (runAttemptCount < 3) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        }
    }
}
