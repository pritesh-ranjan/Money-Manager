package com.moneymanager.app.domain

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.moneymanager.app.data.local.AppDatabase
import com.moneymanager.app.data.local.dao.MoneyManagerDao
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.AccountType
import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.data.local.entity.CategoryType
import com.moneymanager.app.data.local.entity.CurrencyEntity
import com.moneymanager.app.data.local.entity.TagEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.local.entity.TransactionTagCrossRef
import com.moneymanager.app.data.local.entity.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

data class BackupRestoreResult(
    val success: Boolean,
    val message: String,
    val accountsCount: Int = 0,
    val categoriesCount: Int = 0,
    val transactionsCount: Int = 0
)

object SqliteBackupManager {

    suspend fun exportDatabase(context: Context, destinationUri: Uri): BackupRestoreResult = withContext(Dispatchers.IO) {
        try {
            // Checkpoint WAL so all uncommitted frames are synced into the main db file
            val db = AppDatabase.getDatabase(context)
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()

            val dbFile = context.getDatabasePath("money_manager.db")
            if (!dbFile.exists()) {
                return@withContext BackupRestoreResult(
                    success = false,
                    message = "Database file not found on device."
                )
            }

            context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                FileInputStream(dbFile).use { input ->
                    input.copyTo(output)
                }
            } ?: return@withContext BackupRestoreResult(
                success = false,
                message = "Unable to open output stream for destination."
            )

            BackupRestoreResult(
                success = true,
                message = "Database successfully backed up to SQLite file."
            )
        } catch (e: Exception) {
            BackupRestoreResult(
                success = false,
                message = "Backup failed: ${e.localizedMessage ?: e.message}"
            )
        }
    }

    fun isValidSqliteFile(file: File): Boolean {
        if (!file.exists() || file.length() < 16) return false
        val header = ByteArray(16)
        return try {
            FileInputStream(file).use { input ->
                val read = input.read(header)
                if (read < 16) return false
                val expected = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
                header.contentEquals(expected)
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun restoreFromSqliteFile(
        context: Context,
        sourceUri: Uri,
        dao: MoneyManagerDao
    ): BackupRestoreResult = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "restore_${System.currentTimeMillis()}.sqlite")
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext BackupRestoreResult(
                success = false,
                message = "Unable to read backup file."
            )

            if (!isValidSqliteFile(tempFile)) {
                return@withContext BackupRestoreResult(
                    success = false,
                    message = "Invalid SQLite backup file: missing valid SQLite3 header."
                )
            }

            val sqlite = SQLiteDatabase.openDatabase(tempFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val result = sqlite.use { db ->
                val tableNames = mutableSetOf<String>()
                db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { cursor ->
                    while (cursor.moveToNext()) {
                        tableNames.add(cursor.getString(0))
                    }
                }

                when {
                    tableNames.contains("transactions") -> restoreNativeSchema(db, dao)
                    tableNames.contains("INOUTCOME") -> restoreLegacySchema(db, dao)
                    else -> BackupRestoreResult(
                        success = false,
                        message = "Unrecognized SQLite file format. No compatible tables found."
                    )
                }
            }
            result
        } catch (e: Exception) {
            BackupRestoreResult(
                success = false,
                message = "Restore failed: ${e.localizedMessage ?: e.message}"
            )
        } finally {
            if (tempFile.exists()) tempFile.delete()
            val wal = File("${tempFile.absolutePath}-wal")
            if (wal.exists()) wal.delete()
            val shm = File("${tempFile.absolutePath}-shm")
            if (shm.exists()) shm.delete()
        }
    }

    // Dynamic column-by-column restore of native Room tables for forward/backward compatibility
    private suspend fun restoreNativeSchema(
        db: SQLiteDatabase,
        dao: MoneyManagerDao
    ): BackupRestoreResult {
        // 1. Currencies
        val currencies = mutableListOf<CurrencyEntity>()
        if (hasTable(db, "currencies")) {
            db.rawQuery("SELECT * FROM currencies", null).use { c ->
                while (c.moveToNext()) {
                    val uid = c.getStringOrDefault("uid", UUID.randomUUID().toString())
                    val name = c.getStringOrDefault("name", "Currency")
                    val isoCode = c.getStringOrDefault("isoCode", "INR")
                    val symbol = c.getStringOrDefault("symbol", "₹")
                    val rate = c.getDoubleOrDefault("rateToBase", 1.0)
                    val isMain = c.getIntOrDefault("isMainCurrency", 0) == 1
                    val isCustom = c.getIntOrDefault("isCustom", 0) == 1
                    val dec = c.getIntOrDefault("decimalPlaces", 2)
                    currencies.add(CurrencyEntity(uid, name, isoCode, symbol, rate, isMain, isCustom, dec))
                }
            }
        }

        // 2. Accounts
        val accounts = mutableListOf<AccountEntity>()
        if (hasTable(db, "accounts")) {
            db.rawQuery("SELECT * FROM accounts", null).use { c ->
                while (c.moveToNext()) {
                    val uid = c.getStringOrDefault("uid", UUID.randomUUID().toString())
                    val name = c.getStringOrDefault("name", "Account")
                    val typeStr = c.getStringOrDefault("accountType", AccountType.SAVINGS.name)
                    val accountType = try { AccountType.valueOf(typeStr) } catch (_: Exception) { AccountType.SAVINGS }
                    val currencyUid = c.getStringOrDefault("currencyUid", currencies.firstOrNull()?.uid ?: "CURRENCY_INR")
                    val openBal = c.getDoubleOrDefault("openingBalance", 0.0)
                    val stmtDay = c.getIntOrNull("statementDay")
                    val dueDay = c.getIntOrNull("paymentDueDay")
                    val limit = c.getDoubleOrNull("creditLimit")
                    val settlementUid = c.getStringOrNull("settlementAccountUid")
                    val linkedUid = c.getStringOrNull("linkedParentAccountUid")
                    val incNw = c.getIntOrDefault("includeInNetWorth", 1) == 1
                    val order = c.getIntOrDefault("sortOrder", 0)
                    val isArchived = c.getIntOrDefault("isArchived", 0) == 1

                    accounts.add(
                        AccountEntity(
                            uid = uid,
                            name = name,
                            accountType = accountType,
                            currencyUid = currencyUid,
                            openingBalance = openBal,
                            statementDay = stmtDay,
                            paymentDueDay = dueDay,
                            creditLimit = limit,
                            settlementAccountUid = settlementUid,
                            linkedParentAccountUid = linkedUid,
                            includeInNetWorth = incNw,
                            sortOrder = order,
                            isArchived = isArchived
                        )
                    )
                }
            }
        }

        // 3. Categories
        val categories = mutableListOf<CategoryEntity>()
        if (hasTable(db, "categories")) {
            db.rawQuery("SELECT * FROM categories", null).use { c ->
                while (c.moveToNext()) {
                    val uid = c.getStringOrDefault("uid", UUID.randomUUID().toString())
                    val name = c.getStringOrDefault("name", "Category")
                    val typeStr = c.getStringOrDefault("type", CategoryType.EXPENSE.name)
                    val type = try { CategoryType.valueOf(typeStr) } catch (_: Exception) { CategoryType.EXPENSE }
                    val parentUid = c.getStringOrNull("parentUid")
                    val icon = c.getStringOrDefault("icon", if (parentUid == null) "📁" else "🏷️")
                    val order = c.getIntOrDefault("sortOrder", 0)
                    val isDeleted = c.getIntOrDefault("isDeleted", 0) == 1

                    categories.add(CategoryEntity(uid, name, type, parentUid, icon, order, isDeleted))
                }
            }
        }

        // 4. Tags
        val tags = mutableListOf<TagEntity>()
        if (hasTable(db, "tags")) {
            db.rawQuery("SELECT * FROM tags", null).use { c ->
                while (c.moveToNext()) {
                    val uid = c.getStringOrDefault("uid", UUID.randomUUID().toString())
                    val name = c.getStringOrDefault("name", "")
                    val order = c.getIntOrDefault("sortOrder", 0)
                    if (name.isNotEmpty()) {
                        tags.add(TagEntity(uid, name, order))
                    }
                }
            }
        }

        // 5. Transactions
        val transactions = mutableListOf<TransactionEntity>()
        if (hasTable(db, "transactions")) {
            db.rawQuery("SELECT * FROM transactions", null).use { c ->
                while (c.moveToNext()) {
                    val uid = c.getStringOrDefault("uid", UUID.randomUUID().toString())
                    val typeStr = c.getStringOrDefault("type", TransactionType.EXPENSE.name)
                    val type = try { TransactionType.valueOf(typeStr) } catch (_: Exception) { TransactionType.EXPENSE }
                    val accAmt = c.getDoubleOrDefault("accountAmount", 0.0)
                    val baseAmt = c.getDoubleOrDefault("baseAmount", accAmt)
                    val rate = c.getDoubleOrDefault("exchangeRate", 1.0)
                    val currUid = c.getStringOrDefault("currencyUid", "CURRENCY_INR")
                    val accUid = c.getStringOrDefault("accountUid", "")
                    val toAccUid = c.getStringOrNull("toAccountUid")
                    val toAccAmt = c.getDoubleOrNull("toAccountAmount")
                    val catUid = c.getStringOrNull("categoryUid")
                    val note = c.getStringOrDefault("note", "")
                    val desc = c.getStringOrDefault("description", "")
                    val timestamp = c.getLongOrDefault("timestamp", System.currentTimeMillis())
                    val isDeleted = c.getIntOrDefault("isDeleted", 0) == 1

                    if (accUid.isNotEmpty()) {
                        transactions.add(
                            TransactionEntity(
                                uid = uid,
                                type = type,
                                accountAmount = accAmt,
                                baseAmount = baseAmt,
                                exchangeRate = rate,
                                currencyUid = currUid,
                                accountUid = accUid,
                                toAccountUid = toAccUid,
                                toAccountAmount = toAccAmt,
                                categoryUid = catUid,
                                note = note,
                                description = desc,
                                timestamp = timestamp,
                                isDeleted = isDeleted
                            )
                        )
                    }
                }
            }
        }

        // 6. Transaction Tags CrossRef
        val crossRefs = mutableListOf<TransactionTagCrossRef>()
        if (hasTable(db, "transaction_tags")) {
            db.rawQuery("SELECT * FROM transaction_tags", null).use { c ->
                while (c.moveToNext()) {
                    val txUid = c.getStringOrNull("transactionUid")
                    val tagUid = c.getStringOrNull("tagUid")
                    if (!txUid.isNullOrEmpty() && !tagUid.isNullOrEmpty()) {
                        crossRefs.add(TransactionTagCrossRef(txUid, tagUid))
                    }
                }
            }
        }

        // Atomic clear and insert
        dao.clearAllTransactionTags()
        dao.clearAllTransactions()
        dao.clearAllAccounts()
        dao.clearAllCategories()
        dao.clearAllTags()
        dao.clearAllCurrencies()

        if (currencies.isNotEmpty()) dao.insertCurrencies(currencies)
        if (accounts.isNotEmpty()) dao.insertAccounts(accounts)
        if (categories.isNotEmpty()) dao.insertCategories(categories)
        if (tags.isNotEmpty()) dao.insertTags(tags)
        if (transactions.isNotEmpty()) dao.insertTransactions(transactions)
        if (crossRefs.isNotEmpty()) dao.insertTransactionTagCrossRefs(crossRefs)

        return BackupRestoreResult(
            success = true,
            message = "Restored ${transactions.size} transactions, ${accounts.size} accounts, and ${categories.size} categories.",
            accountsCount = accounts.size,
            categoriesCount = categories.size,
            transactionsCount = transactions.size
        )
    }

    // Seamless fallback for older SQLite backups
    private suspend fun restoreLegacySchema(
        db: SQLiteDatabase,
        dao: MoneyManagerDao
    ): BackupRestoreResult {
        // Currencies
        val currencies = mutableListOf<CurrencyEntity>()
        if (hasTable(db, "CURRENCY")) {
            db.rawQuery("SELECT uid, NAME, ISO, RATE, SYMBOL, IS_MAIN_CURRENCY, DECIMAL_POINT FROM CURRENCY", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val uid = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: "Currency"
                    val iso = cursor.getString(2) ?: ""
                    val rate = cursor.getDouble(3).takeIf { it > 0 } ?: 1.0
                    val symbol = cursor.getString(4) ?: (if (iso.isNotEmpty()) iso else "¤")
                    val isMain = cursor.getInt(5) == 1
                    val dec = cursor.getInt(6).coerceIn(0, 4)

                    currencies.add(
                        CurrencyEntity(
                            uid = uid,
                            name = name,
                            isoCode = if (iso.isNotEmpty()) iso else name.take(6),
                            symbol = symbol,
                            rateToBase = rate,
                            isMainCurrency = isMain,
                            isCustom = iso.isEmpty(),
                            decimalPlaces = dec
                        )
                    )
                }
            }
        }

        if (currencies.none { it.isMainCurrency } && currencies.isNotEmpty()) {
            val inrOrFirst = currencies.firstOrNull { it.isoCode.equals("INR", ignoreCase = true) } ?: currencies.first()
            val updated = inrOrFirst.copy(isMainCurrency = true, rateToBase = 1.0)
            currencies.remove(inrOrFirst)
            currencies.add(0, updated)
        }

        val mainCurrencyUid = currencies.firstOrNull { it.isMainCurrency }?.uid ?: currencies.firstOrNull()?.uid ?: "CURRENCY_INR"

        // Asset Groups
        val groupTypeMap = mutableMapOf<String, Int>()
        val groupNameMap = mutableMapOf<String, String>()
        if (hasTable(db, "ASSETGROUP")) {
            db.rawQuery("SELECT uid, ACC_GROUP_NAME, TYPE FROM ASSETGROUP", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val gUid = cursor.getString(0) ?: continue
                    groupNameMap[gUid] = cursor.getString(1) ?: ""
                    groupTypeMap[gUid] = cursor.getInt(2)
                }
            }
        }

        // Accounts
        val accounts = mutableListOf<AccountEntity>()
        if (hasTable(db, "ASSETS")) {
            val queryCols = if (hasColumn(db, "ASSETS", "ZDATA")) {
                "SELECT uid, NIC_NAME, groupUid, currencyUid, CARD_DAY_FIN, CARD_DAY_PAY, cardAssetUid, ORDERSEQ, ZDATA FROM ASSETS WHERE A_UTIME IS NOT NULL"
            } else {
                "SELECT uid, NIC_NAME, groupUid, currencyUid, CARD_DAY_FIN, CARD_DAY_PAY, cardAssetUid, ORDERSEQ FROM ASSETS WHERE A_UTIME IS NOT NULL"
            }
            db.rawQuery(queryCols, null).use { cursor ->
                val zdataIdx = cursor.getColumnIndex("ZDATA")
                while (cursor.moveToNext()) {
                    val uid = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: "Account"
                    val groupUid = cursor.getString(2) ?: ""
                    val currUid = cursor.getString(3) ?: mainCurrencyUid
                    val cardDayFin = cursor.getString(4)?.toIntOrNull()
                    val cardDayPay = cursor.getString(5)?.toIntOrNull()
                    val cardAssetUid = cursor.getString(6)
                    val order = cursor.getInt(7)
                    val isHiddenOrClosed = if (zdataIdx >= 0) cursor.getString(zdataIdx) == "1" else false

                    val groupType = groupTypeMap[groupUid] ?: 0
                    val groupName = groupNameMap[groupUid] ?: ""

                    val accountType = when {
                        name.contains("Equity", ignoreCase = true) || name.contains("Stock", ignoreCase = true) -> AccountType.EQUITY
                        name.contains("Mutual Fund", ignoreCase = true) -> AccountType.MUTUAL_FUNDS
                        groupType == 2 || groupName.equals("Card", ignoreCase = true) -> AccountType.CREDIT_CARD
                        groupType == 3 || groupName.contains("UPI", ignoreCase = true) -> AccountType.UPI_DEBIT
                        groupType == 4 -> AccountType.SAVINGS
                        groupType == 8 -> AccountType.INVESTMENT
                        groupType == 1 -> AccountType.SAVINGS
                        groupType == 11 -> AccountType.CASH
                        groupType == 5 -> AccountType.LOAN
                        else -> AccountType.WALLET
                    }

                    accounts.add(
                        AccountEntity(
                            uid = uid,
                            name = name,
                            accountType = accountType,
                            currencyUid = if (currencies.any { it.uid == currUid }) currUid else mainCurrencyUid,
                            openingBalance = 0.0,
                            statementDay = if (accountType == AccountType.CREDIT_CARD) cardDayFin else null,
                            paymentDueDay = if (accountType == AccountType.CREDIT_CARD) cardDayPay else null,
                            settlementAccountUid = cardAssetUid,
                            linkedParentAccountUid = cardAssetUid,
                            isArchived = isHiddenOrClosed,
                            sortOrder = order
                        )
                    )
                }
            }
        }

        // Categories
        val categories = mutableListOf<CategoryEntity>()
        if (hasTable(db, "ZCATEGORY")) {
            db.rawQuery("SELECT uid, NAME, TYPE, pUid, ORDERSEQ FROM ZCATEGORY WHERE C_IS_DEL IS NULL OR C_IS_DEL = 0", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val uid = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: "Category"
                    val typeInt = cursor.getInt(2)
                    val pUid = cursor.getString(3)
                    val order = cursor.getInt(4)

                    val parentUid = if (pUid == null || pUid == "0" || pUid.isEmpty()) null else pUid
                    val catType = if (typeInt == 0) CategoryType.INCOME else CategoryType.EXPENSE

                    categories.add(
                        CategoryEntity(
                            uid = uid,
                            name = name,
                            type = catType,
                            parentUid = parentUid,
                            icon = if (parentUid == null) "📁" else "🏷️",
                            sortOrder = order
                        )
                    )
                }
            }
        }

        // Tags
        val tags = mutableListOf<TagEntity>()
        if (hasTable(db, "TAG")) {
            db.rawQuery("SELECT uid, name, orderSeq FROM TAG WHERE isDel IS NULL OR isDel = 0", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val uid = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    val order = cursor.getInt(2)
                    tags.add(TagEntity(uid = uid, name = name, sortOrder = order))
                }
            }
        }

        // Transactions
        val transactions = mutableListOf<TransactionEntity>()
        val processedTransferLinks = mutableSetOf<String>()

        if (hasTable(db, "INOUTCOME")) {
            db.rawQuery("""
                SELECT uid, DO_TYPE, assetUid, toAssetUid, txUidTrans, ZMONEY, AMOUNT_ACCOUNT, 
                       currencyUid, ctgUid, ZCONTENT, ZDATA, ZDATE 
                FROM INOUTCOME 
                WHERE IS_DEL IS NULL OR IS_DEL = 0
            """, null).use { cursor ->
                while (cursor.moveToNext()) {
                    val uid = cursor.getString(0) ?: continue
                    val doType = cursor.getString(1) ?: "1"
                    val assetUid = cursor.getString(2) ?: continue
                    val toAssetUid = cursor.getString(3)
                    val txUidTrans = cursor.getString(4)
                    val zmoney = cursor.getString(5)?.toDoubleOrNull() ?: 0.0
                    val amountAccount = cursor.getString(6)?.toDoubleOrNull() ?: zmoney
                    val currUid = cursor.getString(7) ?: mainCurrencyUid
                    val ctgUid = cursor.getString(8)
                    val note = cursor.getString(9) ?: ""
                    val description = cursor.getString(10) ?: ""
                    val zdate = cursor.getString(11)?.toLongOrNull() ?: System.currentTimeMillis()

                    val targetCurrUid = if (currencies.any { it.uid == currUid }) currUid else mainCurrencyUid

                    when (doType) {
                        "3", "4" -> {
                            val linkKey = if (!txUidTrans.isNullOrEmpty()) txUidTrans else "$assetUid-$toAssetUid-$zdate"
                            if (processedTransferLinks.contains(linkKey)) continue
                            processedTransferLinks.add(linkKey)

                            val fromUid = if (doType == "3") assetUid else (toAssetUid ?: assetUid)
                            val toUid = if (doType == "3") (toAssetUid ?: assetUid) else assetUid

                            transactions.add(
                                TransactionEntity(
                                    uid = uid,
                                    type = TransactionType.TRANSFER,
                                    accountAmount = amountAccount,
                                    baseAmount = zmoney,
                                    exchangeRate = if (amountAccount > 0) zmoney / amountAccount else 1.0,
                                    currencyUid = targetCurrUid,
                                    accountUid = fromUid,
                                    toAccountUid = toUid,
                                    toAccountAmount = amountAccount,
                                    categoryUid = ctgUid,
                                    note = note,
                                    description = description,
                                    timestamp = zdate
                                )
                            )
                        }
                        "0" -> {
                            transactions.add(
                                TransactionEntity(
                                    uid = uid,
                                    type = TransactionType.INCOME,
                                    accountAmount = amountAccount,
                                    baseAmount = zmoney,
                                    exchangeRate = if (amountAccount > 0) zmoney / amountAccount else 1.0,
                                    currencyUid = targetCurrUid,
                                    accountUid = assetUid,
                                    categoryUid = ctgUid,
                                    note = note,
                                    description = description,
                                    timestamp = zdate
                                )
                            )
                        }
                        "7", "8" -> {
                            val diff = if (doType == "8") -amountAccount else amountAccount
                            val baseDiff = if (doType == "8") -zmoney else zmoney
                            transactions.add(
                                TransactionEntity(
                                    uid = uid,
                                    type = TransactionType.ADJUSTMENT,
                                    accountAmount = diff,
                                    baseAmount = baseDiff,
                                    exchangeRate = 1.0,
                                    currencyUid = targetCurrUid,
                                    accountUid = assetUid,
                                    categoryUid = ctgUid,
                                    note = if (note.isEmpty()) "Balance Adjustment" else note,
                                    description = description,
                                    timestamp = zdate
                                )
                            )
                        }
                        else -> {
                            transactions.add(
                                TransactionEntity(
                                    uid = uid,
                                    type = TransactionType.EXPENSE,
                                    accountAmount = amountAccount,
                                    baseAmount = zmoney,
                                    exchangeRate = if (amountAccount > 0) zmoney / amountAccount else 1.0,
                                    currencyUid = targetCurrUid,
                                    accountUid = assetUid,
                                    categoryUid = ctgUid,
                                    note = note,
                                    description = description,
                                    timestamp = zdate
                                )
                            )
                        }
                    }
                }
            }
        }

        // Tag CrossRefs
        val crossRefs = mutableListOf<TransactionTagCrossRef>()
        val existingTxUids = transactions.map { it.uid }.toSet()
        val existingTagUids = tags.map { it.uid }.toSet()

        if (hasTable(db, "TX_TAG")) {
            db.rawQuery("SELECT txUid, tagUid FROM TX_TAG WHERE isDel IS NULL OR isDel = 0", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val txUid = cursor.getString(0) ?: continue
                    val tagUid = cursor.getString(1) ?: continue
                    if (existingTxUids.contains(txUid) && existingTagUids.contains(tagUid)) {
                        crossRefs.add(TransactionTagCrossRef(txUid, tagUid))
                    }
                }
            }
        }

        // Commit to Room
        dao.clearAllTransactionTags()
        dao.clearAllTransactions()
        dao.clearAllAccounts()
        dao.clearAllCategories()
        dao.clearAllTags()
        dao.clearAllCurrencies()

        if (currencies.isNotEmpty()) dao.insertCurrencies(currencies)
        if (accounts.isNotEmpty()) dao.insertAccounts(accounts)
        if (categories.isNotEmpty()) dao.insertCategories(categories)
        if (tags.isNotEmpty()) dao.insertTags(tags)
        if (transactions.isNotEmpty()) dao.insertTransactions(transactions)
        if (crossRefs.isNotEmpty()) dao.insertTransactionTagCrossRefs(crossRefs)

        return BackupRestoreResult(
            success = true,
            message = "Restored ${transactions.size} transactions, ${accounts.size} accounts, and ${categories.size} categories.",
            accountsCount = accounts.size,
            categoriesCount = categories.size,
            transactionsCount = transactions.size
        )
    }

    private fun hasTable(db: SQLiteDatabase, tableName: String): Boolean {
        db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name = ?", arrayOf(tableName)).use {
            return it.moveToFirst()
        }
    }

    private fun hasColumn(db: SQLiteDatabase, tableName: String, columnName: String): Boolean {
        return try {
            db.rawQuery("PRAGMA table_info($tableName)", null).use { cursor ->
                val nameIdx = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIdx >= 0 && cursor.getString(nameIdx).equals(columnName, ignoreCase = true)) {
                        return true
                    }
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun Cursor.getStringOrDefault(col: String, default: String): String {
        val idx = getColumnIndex(col)
        return if (idx >= 0 && !isNull(idx)) getString(idx) else default
    }

    private fun Cursor.getStringOrNull(col: String): String? {
        val idx = getColumnIndex(col)
        return if (idx >= 0 && !isNull(idx)) getString(idx) else null
    }

    private fun Cursor.getIntOrDefault(col: String, default: Int): Int {
        val idx = getColumnIndex(col)
        return if (idx >= 0 && !isNull(idx)) getInt(idx) else default
    }

    private fun Cursor.getIntOrNull(col: String): Int? {
        val idx = getColumnIndex(col)
        return if (idx >= 0 && !isNull(idx)) getInt(idx) else null
    }

    private fun Cursor.getLongOrDefault(col: String, default: Long): Long {
        val idx = getColumnIndex(col)
        return if (idx >= 0 && !isNull(idx)) getLong(idx) else default
    }

    private fun Cursor.getDoubleOrDefault(col: String, default: Double): Double {
        val idx = getColumnIndex(col)
        return if (idx >= 0 && !isNull(idx)) getDouble(idx) else default
    }

    private fun Cursor.getDoubleOrNull(col: String): Double? {
        val idx = getColumnIndex(col)
        return if (idx >= 0 && !isNull(idx)) getDouble(idx) else null
    }
}
