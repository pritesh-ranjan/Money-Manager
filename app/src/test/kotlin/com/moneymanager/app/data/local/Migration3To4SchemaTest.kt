package com.moneymanager.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class Migration3To4SchemaTest {

    @Test
    fun testMigration3To4_versions() {
        assertEquals(3, Migrations.MIGRATION_3_4.startVersion)
        assertEquals(4, Migrations.MIGRATION_3_4.endVersion)
    }

    @Test
    fun testMigration3To4_createsExpectedTablesAndIndices() {
        val capturedSqls = mutableListOf<String>()

        val fakeDb = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                capturedSqls.add((args[0] as String).trim())
            }
            null
        } as SupportSQLiteDatabase

        Migrations.MIGRATION_3_4.migrate(fakeDb)

        val fullScript = capturedSqls.joinToString("\n")

        // 1. Table: gmail_import_history
        assertTrue("Must create gmail_import_history table", fullScript.contains("CREATE TABLE IF NOT EXISTS `gmail_import_history`"))
        assertTrue("Must have messageId as primary key", fullScript.contains("`messageId` TEXT NOT NULL"))
        assertTrue("Must have sender", fullScript.contains("`sender` TEXT NOT NULL"))
        assertTrue("Must have subject", fullScript.contains("`subject` TEXT NOT NULL"))
        assertTrue("Must have receivedEpochMillis", fullScript.contains("`receivedEpochMillis` INTEGER NOT NULL"))
        assertTrue("Must have bankName", fullScript.contains("`bankName` TEXT NOT NULL"))
        assertTrue("Must have fileName", fullScript.contains("`fileName` TEXT NOT NULL"))
        assertTrue("Must have importStatus", fullScript.contains("`importStatus` TEXT NOT NULL"))
        assertTrue("Must have statementId", fullScript.contains("`statementId` TEXT"))
        assertTrue("Must have errorMessage", fullScript.contains("`errorMessage` TEXT"))
        assertTrue("Must have importedAtEpochMillis", fullScript.contains("`importedAtEpochMillis` INTEGER NOT NULL"))

        // Indices on gmail_import_history
        assertTrue("Must create index on messageId", fullScript.contains("`index_gmail_import_history_messageId`"))
        assertTrue("Must create index on receivedEpochMillis", fullScript.contains("`index_gmail_import_history_receivedEpochMillis`"))
        assertTrue("Must create index on bankName", fullScript.contains("`index_gmail_import_history_bankName`"))

        // 2. Table: gmail_sync_state
        assertTrue("Must create gmail_sync_state table", fullScript.contains("CREATE TABLE IF NOT EXISTS `gmail_sync_state`"))
        assertTrue("Must have id as primary key", fullScript.contains("`id` INTEGER NOT NULL"))
        assertTrue("Must have accountEmail", fullScript.contains("`accountEmail` TEXT"))
        assertTrue("Must have lastHistoryId", fullScript.contains("`lastHistoryId` TEXT"))
        assertTrue("Must have lastSyncTimestamp", fullScript.contains("`lastSyncTimestamp` INTEGER NOT NULL"))
        assertTrue("Must have lastSyncStatus", fullScript.contains("`lastSyncStatus` TEXT NOT NULL"))
        assertTrue("Must have isAutoSyncEnabled", fullScript.contains("`isAutoSyncEnabled` INTEGER NOT NULL"))
    }
}
