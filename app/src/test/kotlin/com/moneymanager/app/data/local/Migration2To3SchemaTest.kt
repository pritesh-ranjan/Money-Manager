package com.moneymanager.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class Migration2To3SchemaTest {

    @Test
    fun testMigration2To3_versions() {
        assertEquals(2, Migrations.MIGRATION_2_3.startVersion)
        assertEquals(3, Migrations.MIGRATION_2_3.endVersion)
    }

    @Test
    fun testMigration2To3_createsExpectedTableAndIndices() {
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

        Migrations.MIGRATION_2_3.migrate(fakeDb)

        val fullScript = capturedSqls.joinToString("\n")

        // 1. Table: card_statement_transactions
        assertTrue("Must create card_statement_transactions table", fullScript.contains("CREATE TABLE IF NOT EXISTS `card_statement_transactions`"))
        assertTrue("Must have uid as primary key", fullScript.contains("`uid` TEXT NOT NULL"))
        assertTrue("Must have statementId", fullScript.contains("`statementId` TEXT NOT NULL"))
        assertTrue("Must have bankId", fullScript.contains("`bankId` TEXT NOT NULL"))
        assertTrue("Must have accountOrCardNumberMasked", fullScript.contains("`accountOrCardNumberMasked` TEXT NOT NULL"))
        assertTrue("Must have statementYearMonth", fullScript.contains("`statementYearMonth` TEXT NOT NULL"))
        assertTrue("Must have statementTotalDue", fullScript.contains("`statementTotalDue` REAL NOT NULL"))
        assertTrue("Must have statementMinDue", fullScript.contains("`statementMinDue` REAL"))
        assertTrue("Must have isReconciled", fullScript.contains("`isReconciled` INTEGER NOT NULL"))
        assertTrue("Must have rewardType", fullScript.contains("`rewardType` TEXT"))
        assertTrue("Must have serialNumber", fullScript.contains("`serialNumber` INTEGER NOT NULL"))
        assertTrue("Must have amount", fullScript.contains("`amount` REAL NOT NULL"))
        assertTrue("Must have isCredit", fullScript.contains("`isCredit` INTEGER NOT NULL"))
        assertTrue("Must have cleanNarration", fullScript.contains("`cleanNarration` TEXT NOT NULL"))

        // 2. Indices
        assertTrue("Must create index on statementId", fullScript.contains("`index_card_statement_transactions_statementId`"))
        assertTrue("Must create index on accountOrCardNumberMasked", fullScript.contains("`index_card_statement_transactions_accountOrCardNumberMasked`"))
        assertTrue("Must create index on statementYearMonth", fullScript.contains("`index_card_statement_transactions_statementYearMonth`"))
        assertTrue("Must create compound index on card + month", fullScript.contains("`index_card_statement_transactions_accountOrCardNumberMasked_statementYearMonth`"))
        assertTrue("Must create index on transactionDateEpochDay", fullScript.contains("`index_card_statement_transactions_transactionDateEpochDay`"))
        assertTrue("Must create index on bankId", fullScript.contains("`index_card_statement_transactions_bankId`"))
        assertTrue("Must create index on referenceNumber", fullScript.contains("`index_card_statement_transactions_referenceNumber`"))
    }
}
