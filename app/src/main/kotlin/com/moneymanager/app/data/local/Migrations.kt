package com.moneymanager.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. Create insights table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `insights` (
                    `id` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `severity` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `summary` TEXT NOT NULL,
                    `targetKey` TEXT,
                    `categoryUid` TEXT,
                    `accountUid` TEXT,
                    `toAccountUid` TEXT,
                    `transferKind` TEXT,
                    `amount` REAL,
                    `historicalAverage` REAL,
                    `historicalMedian` REAL,
                    `percentChange` REAL,
                    `robustZ` REAL,
                    `confidence` REAL,
                    `confidenceBreakdown` TEXT,
                    `frequency` TEXT,
                    `expectedNextDate` INTEGER,
                    `autoAssignedTag` TEXT,
                    `firstDetectedAt` INTEGER NOT NULL,
                    `lastEvaluatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
            """.trimIndent())

            db.execSQL("CREATE INDEX IF NOT EXISTS `index_insights_type` ON `insights` (`type`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_insights_status` ON `insights` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_insights_targetKey` ON `insights` (`targetKey`)")

            // 2. Create insight_transactions cross-ref table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `insight_transactions` (
                    `insightId` TEXT NOT NULL,
                    `transactionUid` TEXT NOT NULL,
                    PRIMARY KEY(`insightId`, `transactionUid`),
                    FOREIGN KEY(`insightId`) REFERENCES `insights`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())

            db.execSQL("CREATE INDEX IF NOT EXISTS `index_insight_transactions_transactionUid` ON `insight_transactions` (`transactionUid`)")

            // 3. Create recurrence_decisions table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `recurrence_decisions` (
                    `targetKey` TEXT NOT NULL,
                    `decision` TEXT NOT NULL,
                    `decidedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`targetKey`)
                )
            """.trimIndent())
        }
    }
}
