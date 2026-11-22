package com.moneymanager.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import com.moneymanager.app.data.local.entity.AccountType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class Migration4To5SchemaTest {

    @Test
    fun testMigration4To5_versions() {
        assertEquals(4, Migrations.MIGRATION_4_5.startVersion)
        assertEquals(5, Migrations.MIGRATION_4_5.endVersion)
    }

    @Test
    fun testMigration4To5_createsAccountTypesTableAndMigratesAccounts() {
        val capturedSqls = mutableListOf<String>()
        val capturedInserts = mutableListOf<Pair<String, List<Any?>>>()

        val fakeDb = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                val sql = (args[0] as String).trim()
                capturedSqls.add(sql)
                if (args.size >= 2 && args[1] is Array<*>) {
                    @Suppress("UNCHECKED_CAST")
                    val bindArgs = (args[1] as Array<Any?>).toList()
                    capturedInserts.add(sql to bindArgs)
                }
            }
            null
        } as SupportSQLiteDatabase

        Migrations.MIGRATION_4_5.migrate(fakeDb)

        val fullScript = capturedSqls.joinToString("\n")

        assertTrue(
            "Must create account_types table",
            fullScript.contains("CREATE TABLE IF NOT EXISTS `account_types`")
        )
        assertTrue("Must define uid", fullScript.contains("`uid` TEXT NOT NULL"))
        assertTrue("Must define name", fullScript.contains("`name` TEXT NOT NULL"))
        assertTrue("Must define baseType", fullScript.contains("`baseType` TEXT NOT NULL"))
        assertTrue("Must define isLiability", fullScript.contains("`isLiability` INTEGER NOT NULL"))
        assertTrue("Must define isSystemDefault", fullScript.contains("`isSystemDefault` INTEGER NOT NULL"))
        assertTrue("Must define sortOrder", fullScript.contains("`sortOrder` INTEGER NOT NULL"))
        assertTrue("Must define isDeleted", fullScript.contains("`isDeleted` INTEGER NOT NULL"))
        assertTrue(
            "Must create sortOrder index",
            fullScript.contains("CREATE INDEX IF NOT EXISTS `index_account_types_sortOrder`")
        )

        val alterSql = capturedSqls.firstOrNull { it.startsWith("ALTER TABLE `accounts`") }
        assertEquals("ALTER TABLE `accounts` ADD COLUMN `accountTypeUid` TEXT", alterSql)
        assertFalse(
            "Nullable column ALTER TABLE must not specify a DEFAULT clause for Room validation",
            alterSql!!.contains("DEFAULT", ignoreCase = true)
        )

        assertTrue(
            "Must backfill accountTypeUid from existing accountType",
            fullScript.contains("UPDATE `accounts` SET `accountTypeUid` = `accountType` WHERE `accountTypeUid` IS NULL")
        )

        val seededUids = capturedInserts.map { it.second.first() }.toSet()
        assertEquals(AccountType.values().map { it.name }.toSet(), seededUids)
    }
}
