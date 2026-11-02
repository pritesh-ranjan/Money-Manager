package com.moneymanager.app.domain.gmail

import com.moneymanager.app.data.local.dao.GmailImportDao
import com.moneymanager.app.data.local.dao.GmailSyncDao
import com.moneymanager.app.data.local.entity.GmailImportHistoryEntity
import com.moneymanager.app.data.local.entity.GmailSyncStateEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeGmailImportDao : GmailImportDao {
    val historyMap = mutableMapOf<String, GmailImportHistoryEntity>()
    private val _historyFlow = MutableStateFlow<List<GmailImportHistoryEntity>>(emptyList())

    private fun emit() {
        _historyFlow.value = historyMap.values.sortedByDescending { it.receivedEpochMillis }
    }

    override fun getAllHistory(): Flow<List<GmailImportHistoryEntity>> = _historyFlow.asStateFlow()

    override suspend fun getAllHistoryRaw(): List<GmailImportHistoryEntity> =
        historyMap.values.sortedByDescending { it.receivedEpochMillis }

    override suspend fun hasMessage(messageId: String): Boolean = historyMap.containsKey(messageId)

    override suspend fun getByMessageId(messageId: String): GmailImportHistoryEntity? = historyMap[messageId]

    override suspend fun insertOrUpdate(record: GmailImportHistoryEntity) {
        historyMap[record.messageId] = record
        emit()
    }

    override suspend fun insertAll(records: List<GmailImportHistoryEntity>) {
        records.forEach { historyMap[it.messageId] = it }
        emit()
    }

    override suspend fun deleteByMessageId(messageId: String) {
        historyMap.remove(messageId)
        emit()
    }

    override suspend fun clearHistory() {
        historyMap.clear()
        emit()
    }
}

class FakeGmailSyncDao : GmailSyncDao {
    var state: GmailSyncStateEntity? = null
    private val _stateFlow = MutableStateFlow<GmailSyncStateEntity?>(null)

    private fun emit() {
        _stateFlow.value = state
    }

    override fun getSyncState(): Flow<GmailSyncStateEntity?> = _stateFlow.asStateFlow()

    override suspend fun getSyncStateRaw(): GmailSyncStateEntity? = state

    override suspend fun updateSyncState(state: GmailSyncStateEntity) {
        this.state = state
        emit()
    }

    override suspend fun updateSyncProgress(historyId: String?, timestamp: Long, status: String, error: String?) {
        state = state?.copy(
            lastHistoryId = historyId,
            lastSyncTimestamp = timestamp,
            lastSyncStatus = status,
            lastSyncError = error
        ) ?: GmailSyncStateEntity(
            id = 1,
            lastHistoryId = historyId,
            lastSyncTimestamp = timestamp,
            lastSyncStatus = status,
            lastSyncError = error
        )
        emit()
    }

    override suspend fun setAutoSyncEnabled(enabled: Boolean) {
        state = state?.copy(isAutoSyncEnabled = enabled)
        emit()
    }

    override suspend fun setDefaultPdfPassword(passwordEncrypted: String?) {
        state = state?.copy(defaultPdfPasswordEncrypted = passwordEncrypted)
        emit()
    }

    override suspend fun clearSyncState() {
        state = null
        emit()
    }
}
