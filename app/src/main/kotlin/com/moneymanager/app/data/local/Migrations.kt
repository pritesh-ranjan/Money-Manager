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

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `card_statement_transactions` (
                    `uid` TEXT NOT NULL,
                    `statementId` TEXT NOT NULL,
                    `bankId` TEXT NOT NULL,
                    `bankName` TEXT NOT NULL,
                    `accountOrCardNumberMasked` TEXT NOT NULL,
                    `accountHolderName` TEXT NOT NULL,
                    `statementYearMonth` TEXT NOT NULL,
                    `statementDateEpochDay` INTEGER NOT NULL,
                    `paymentDueDateEpochDay` INTEGER,
                    `billingPeriodStartEpochDay` INTEGER,
                    `billingPeriodEndEpochDay` INTEGER,
                    `creditLimit` REAL,
                    `availableCredit` REAL,
                    `cashLimit` REAL,
                    `previousBalance` REAL NOT NULL,
                    `statementPurchasesOrDebits` REAL NOT NULL,
                    `statementPaymentsOrCredits` REAL NOT NULL,
                    `statementTotalDue` REAL NOT NULL,
                    `statementMinDue` REAL,
                    `isReconciled` INTEGER NOT NULL,
                    `rewardType` TEXT,
                    `rewardEarned` REAL,
                    `rewardRedeemedOrCredited` REAL,
                    `rewardClosingBalance` REAL,
                    `rewardProgramName` TEXT,
                    `serialNumber` INTEGER NOT NULL,
                    `transactionDateEpochDay` INTEGER NOT NULL,
                    `referenceNumber` TEXT,
                    `rawNarration` TEXT NOT NULL,
                    `cleanNarration` TEXT NOT NULL,
                    `amount` REAL NOT NULL,
                    `isCredit` INTEGER NOT NULL,
                    `debit` REAL,
                    `credit` REAL,
                    `rewardPoints` INTEGER,
                    `pageNumber` INTEGER NOT NULL,
                    `importedAtTimestamp` INTEGER NOT NULL,
                    PRIMARY KEY(`uid`)
                )
            """.trimIndent())

            db.execSQL("CREATE INDEX IF NOT EXISTS `index_card_statement_transactions_statementId` ON `card_statement_transactions` (`statementId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_card_statement_transactions_accountOrCardNumberMasked` ON `card_statement_transactions` (`accountOrCardNumberMasked`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_card_statement_transactions_statementYearMonth` ON `card_statement_transactions` (`statementYearMonth`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_card_statement_transactions_accountOrCardNumberMasked_statementYearMonth` ON `card_statement_transactions` (`accountOrCardNumberMasked`, `statementYearMonth`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_card_statement_transactions_transactionDateEpochDay` ON `card_statement_transactions` (`transactionDateEpochDay`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_card_statement_transactions_bankId` ON `card_statement_transactions` (`bankId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_card_statement_transactions_referenceNumber` ON `card_statement_transactions` (`referenceNumber`)")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `gmail_import_history` (
                    `messageId` TEXT NOT NULL,
                    `sender` TEXT NOT NULL,
                    `subject` TEXT NOT NULL,
                    `receivedEpochMillis` INTEGER NOT NULL,
                    `bankName` TEXT NOT NULL,
                    `fileName` TEXT NOT NULL,
                    `importStatus` TEXT NOT NULL,
                    `statementId` TEXT,
                    `errorMessage` TEXT,
                    `importedAtEpochMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`messageId`)
                )
            """.trimIndent())

            db.execSQL("CREATE INDEX IF NOT EXISTS `index_gmail_import_history_messageId` ON `gmail_import_history` (`messageId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_gmail_import_history_receivedEpochMillis` ON `gmail_import_history` (`receivedEpochMillis`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_gmail_import_history_bankName` ON `gmail_import_history` (`bankName`)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `gmail_sync_state` (
                    `id` INTEGER NOT NULL,
                    `accountEmail` TEXT,
                    `lastHistoryId` TEXT,
                    `lastSyncTimestamp` INTEGER NOT NULL,
                    `lastSyncStatus` TEXT NOT NULL,
                    `lastSyncError` TEXT,
                    `isAutoSyncEnabled` INTEGER NOT NULL,
                    `defaultPdfPasswordEncrypted` TEXT,
                    PRIMARY KEY(`id`)
                )
            """.trimIndent())
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `account_types` (
                    `uid` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `baseType` TEXT NOT NULL,
                    `isLiability` INTEGER NOT NULL,
                    `isSystemDefault` INTEGER NOT NULL,
                    `sortOrder` INTEGER NOT NULL,
                    `isDeleted` INTEGER NOT NULL,
                    PRIMARY KEY(`uid`)
                )
            """.trimIndent())

            db.execSQL("CREATE INDEX IF NOT EXISTS `index_account_types_sortOrder` ON `account_types` (`sortOrder`)")

            val defaults = listOf(
                arrayOf<Any>("CASH", "Cash", "CASH", 0, 1, 1, 0),
                arrayOf<Any>("SAVINGS", "Bank / Savings", "SAVINGS", 0, 1, 2, 0),
                arrayOf<Any>("WALLET", "Wallet / Prepaid", "WALLET", 0, 1, 3, 0),
                arrayOf<Any>("CREDIT_CARD", "Credit Card", "CREDIT_CARD", 1, 1, 4, 0),
                arrayOf<Any>("INVESTMENT", "Fixed Deposit / PPF / SGB", "INVESTMENT", 0, 1, 5, 0),
                arrayOf<Any>("EQUITY", "Equity / Stocks", "EQUITY", 0, 1, 6, 0),
                arrayOf<Any>("MUTUAL_FUNDS", "Mutual Funds", "MUTUAL_FUNDS", 0, 1, 7, 0),
                arrayOf<Any>("UPI_DEBIT", "Linked UPI / Debit", "UPI_DEBIT", 0, 1, 8, 0),
                arrayOf<Any>("LOAN", "Loan / Liability", "LOAN", 1, 1, 9, 0),
                arrayOf<Any>("OTHER", "Other", "OTHER", 0, 1, 10, 0)
            )
            defaults.forEach { row ->
                db.execSQL(
                    "INSERT OR IGNORE INTO `account_types` (`uid`, `name`, `baseType`, `isLiability`, `isSystemDefault`, `sortOrder`, `isDeleted`) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    row
                )
            }

            db.execSQL("ALTER TABLE `accounts` ADD COLUMN `accountTypeUid` TEXT")
            db.execSQL("UPDATE `accounts` SET `accountTypeUid` = `accountType` WHERE `accountTypeUid` IS NULL")
        }
    }
}
