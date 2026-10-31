package com.moneymanager.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class Migration1To2SchemaTest {

    @Test
    fun testMigration1To2_versions() {
        assertEquals(1, Migrations.MIGRATION_1_2.startVersion)
        assertEquals(2, Migrations.MIGRATION_1_2.endVersion)
    }

    @Test
    fun testMigration1To2_createsExpectedTablesAndIndices() {
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

        Migrations.MIGRATION_1_2.migrate(fakeDb)

        val fullScript = capturedSqls.joinToString("\n")

        // 1. Table: insights
        assertTrue("Must create insights table", fullScript.contains("CREATE TABLE IF NOT EXISTS `insights`"))
        assertTrue("insights must have id primary key", fullScript.contains("`id` TEXT NOT NULL"))
        assertTrue("insights must have severity", fullScript.contains("`severity` TEXT NOT NULL"))
        assertTrue("insights must have confidence", fullScript.contains("`confidence` REAL"))
        assertTrue("insights must have robustZ", fullScript.contains("`robustZ` REAL"))
        assertTrue("insights must have autoAssignedTag", fullScript.contains("`autoAssignedTag` TEXT"))

        // 2. Table: insight_transactions
        assertTrue("Must create insight_transactions table", fullScript.contains("CREATE TABLE IF NOT EXISTS `insight_transactions`"))
        assertTrue("insight_transactions must have foreign key cascade", fullScript.contains("FOREIGN KEY(`insightId`) REFERENCES `insights`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE"))

        // 3. Table: recurrence_decisions
        assertTrue("Must create recurrence_decisions table", fullScript.contains("CREATE TABLE IF NOT EXISTS `recurrence_decisions`"))

        // 4. Indices
        assertTrue("Must create index_insights_type", fullScript.contains("`index_insights_type`"))
        assertTrue("Must create index_insights_status", fullScript.contains("`index_insights_status`"))
        assertTrue("Must create index_insights_targetKey", fullScript.contains("`index_insights_targetKey`"))
        assertTrue("Must create index_insight_transactions_transactionUid", fullScript.contains("`index_insight_transactions_transactionUid`"))
    }
}
