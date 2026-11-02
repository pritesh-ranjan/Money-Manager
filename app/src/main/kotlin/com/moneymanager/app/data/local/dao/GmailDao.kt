package com.moneymanager.app.data.local.dao

import androidx.room.*
import com.moneymanager.app.data.local.entity.GmailImportHistoryEntity
import com.moneymanager.app.data.local.entity.GmailSyncStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GmailImportDao {

    @Query("SELECT * FROM gmail_import_history ORDER BY receivedEpochMillis DESC, importedAtEpochMillis DESC")
    fun getAllHistory(): Flow<List<GmailImportHistoryEntity>>

    @Query("SELECT * FROM gmail_import_history ORDER BY receivedEpochMillis DESC, importedAtEpochMillis DESC")
    suspend fun getAllHistoryRaw(): List<GmailImportHistoryEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM gmail_import_history WHERE messageId = :messageId)")
    suspend fun hasMessage(messageId: String): Boolean

    @Query("SELECT * FROM gmail_import_history WHERE messageId = :messageId LIMIT 1")
    suspend fun getByMessageId(messageId: String): GmailImportHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(record: GmailImportHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<GmailImportHistoryEntity>)

    @Query("DELETE FROM gmail_import_history WHERE messageId = :messageId")
    suspend fun deleteByMessageId(messageId: String)

    @Query("DELETE FROM gmail_import_history")
    suspend fun clearHistory()
}

@Dao
interface GmailSyncDao {

    @Query("SELECT * FROM gmail_sync_state WHERE id = 1 LIMIT 1")
    fun getSyncState(): Flow<GmailSyncStateEntity?>

    @Query("SELECT * FROM gmail_sync_state WHERE id = 1 LIMIT 1")
    suspend fun getSyncStateRaw(): GmailSyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateSyncState(state: GmailSyncStateEntity)

    @Query("UPDATE gmail_sync_state SET lastHistoryId = :historyId, lastSyncTimestamp = :timestamp, lastSyncStatus = :status, lastSyncError = :error WHERE id = 1")
    suspend fun updateSyncProgress(historyId: String?, timestamp: Long, status: String, error: String? = null)

    @Query("UPDATE gmail_sync_state SET isAutoSyncEnabled = :enabled WHERE id = 1")
    suspend fun setAutoSyncEnabled(enabled: Boolean)

    @Query("UPDATE gmail_sync_state SET defaultPdfPasswordEncrypted = :passwordEncrypted WHERE id = 1")
    suspend fun setDefaultPdfPassword(passwordEncrypted: String?)

    @Query("DELETE FROM gmail_sync_state")
    suspend fun clearSyncState()
}
