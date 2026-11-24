package com.moneymanager.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class Migration5To6SchemaTest {

    @Test
    fun testMigration5To6_versions() {
        assertEquals(5, Migrations.MIGRATION_5_6.startVersion)
        assertEquals(6, Migrations.MIGRATION_5_6.endVersion)
    }

    @Test
    fun testMigration5To6_createsInstitutionsTableAndAddsAccountColumns() {
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

        Migrations.MIGRATION_5_6.migrate(fakeDb)

        val fullScript = capturedSqls.joinToString("\n")

        assertTrue(
            "Must create institutions table",
            fullScript.contains("CREATE TABLE IF NOT EXISTS `institutions`")
        )
        assertTrue("Must define uid", fullScript.contains("`uid` TEXT NOT NULL"))
        assertTrue("Must define name", fullScript.contains("`name` TEXT NOT NULL"))
        assertTrue("Must define kind", fullScript.contains("`kind` TEXT NOT NULL"))
        assertTrue("Must define sortOrder", fullScript.contains("`sortOrder` INTEGER NOT NULL"))
        assertTrue("Must define isDeleted", fullScript.contains("`isDeleted` INTEGER NOT NULL"))
        assertTrue(
            "Must create kind index",
            fullScript.contains("CREATE INDEX IF NOT EXISTS `index_institutions_kind`")
        )
        assertTrue(
            "Must create sortOrder index",
            fullScript.contains("CREATE INDEX IF NOT EXISTS `index_institutions_sortOrder`")
        )

        val alterSqls = capturedSqls.filter { it.startsWith("ALTER TABLE `accounts`") }
        assertEquals(
            listOf(
                "ALTER TABLE `accounts` ADD COLUMN `accountNumber` TEXT",
                "ALTER TABLE `accounts` ADD COLUMN `institutionUid` TEXT",
                "ALTER TABLE `accounts` ADD COLUMN `cardNetwork` TEXT"
            ),
            alterSqls
        )
        alterSqls.forEach { sql ->
            assertFalse(
                "Nullable column ALTER TABLE must not specify a DEFAULT clause for Room validation: $sql",
                sql.contains("DEFAULT", ignoreCase = true)
            )
        }
    }
}
