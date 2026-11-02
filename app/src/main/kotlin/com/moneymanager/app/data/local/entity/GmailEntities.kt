package com.moneymanager.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class GmailImportStatus(val label: String) {
    SUCCESS("Imported"),
    PASSWORD_REQUIRED("Password Required"),
    NO_PARSER_MATCH("Unsupported Parser"),
    PARSE_FAILED("Parse Failed"),
    SKIPPED("Skipped")
}

@Entity(
    tableName = "gmail_import_history",
    indices = [
        Index("messageId"),
        Index("receivedEpochMillis"),
        Index("bankName")
    ]
)
data class GmailImportHistoryEntity(
    @PrimaryKey
    val messageId: String,
    val sender: String,
    val subject: String,
    val receivedEpochMillis: Long,
    val bankName: String,
    val fileName: String,
    val importStatus: String, // from GmailImportStatus.name
    val statementId: String? = null,
    val errorMessage: String? = null,
    val importedAtEpochMillis: Long = System.currentTimeMillis()
)

enum class GmailSyncStatus(val label: String) {
    IDLE("Idle"),
    SYNCING("Syncing..."),
    SUCCESS("Success"),
    ERROR("Error")
}

@Entity(tableName = "gmail_sync_state")
data class GmailSyncStateEntity(
    @PrimaryKey
    val id: Int = 1,
    val accountEmail: String? = null,
    val lastHistoryId: String? = null,
    val lastSyncTimestamp: Long = 0L,
    val lastSyncStatus: String = GmailSyncStatus.IDLE.name,
    val lastSyncError: String? = null,
    val isAutoSyncEnabled: Boolean = true,
    val defaultPdfPasswordEncrypted: String? = null
)
