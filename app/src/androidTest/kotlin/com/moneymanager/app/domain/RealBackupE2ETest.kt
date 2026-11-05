package com.moneymanager.app.domain

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.moneymanager.app.data.MoneyManagerRepository
import com.moneymanager.app.data.local.AppDatabase
import com.moneymanager.app.data.local.entity.AccountType
import com.moneymanager.app.data.local.entity.CategoryType
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.local.entity.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RealBackupE2ETest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: MoneyManagerRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = AppDatabase.getDatabase(context)
        repository = MoneyManagerRepository(database.moneyManagerDao(), database.insightsDao())
    }

    @Test
    fun testRealSqliteBackupRestoreAndLifecycle() = runBlocking {
        // 1. Extract the real sqlite backup file from test assets
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val assetFileName = "money_manager_backup.sqlite"
        val tempBackupFile = File(context.cacheDir, "test_restore_input.sqlite")

        val inputStream = try {
            testContext.assets.open(assetFileName)
        } catch (_: Exception) {
            val fallback = File("/sdcard/Download/money_manager_backup.sqlite")
            fallback.inputStream()
        }

        inputStream.use { input ->
            FileOutputStream(tempBackupFile).use { output ->
                input.copyTo(output)
            }
        }

        assertTrue("Backup file must exist", tempBackupFile.exists())
        assertTrue("Backup file size must be > 100KB", tempBackupFile.length() > 100_000)

        // 2. Perform SQLite restore
        val restoreResult = SqliteBackupManager.restoreFromSqliteFile(
            context = context,
            sourceUri = Uri.fromFile(tempBackupFile),
            dao = database.moneyManagerDao()
        )

        assertTrue("Restore must succeed: ${restoreResult.message}", restoreResult.success)
        assertTrue("Accounts must be restored (got ${restoreResult.accountsCount})", restoreResult.accountsCount > 0)
        assertTrue("Categories must be restored (got ${restoreResult.categoriesCount})", restoreResult.categoriesCount > 0)
        assertTrue("Transactions must be restored (got ${restoreResult.transactionsCount})", restoreResult.transactionsCount > 0)

        // 3. Verify accounts and transactions in the restored repository
        val accounts = repository.accountsWithCurrency.first()
        val allTransactions = repository.allTransactions.first()
        val categories = repository.allCategories.first()

        assertFalse("Restored accounts must not be empty", accounts.isEmpty())
        assertFalse("Restored transactions must not be empty", allTransactions.isEmpty())
        assertFalse("Restored categories must not be empty", categories.isEmpty())

        // 4. Test ADDING a new transaction to an existing restored account
        val targetAccount = accounts.first { !it.account.isArchived && it.account.accountType == AccountType.SAVINGS }
        val category = categories.first { it.type == CategoryType.EXPENSE }

        val initialNetWorth = repository.netWorthSummary.first()
        assertNotNull(initialNetWorth)
        val initialAccountBalance = initialNetWorth!!.accountBalances
            .first { it.account.uid == targetAccount.account.uid }.nativeBalance

        val testTxUid = "test_add_tx_" + UUID.randomUUID().toString()
        val newTx = TransactionEntity(
            uid = testTxUid,
            type = TransactionType.EXPENSE,
            accountAmount = 450.0,
            baseAmount = 450.0,
            currencyUid = targetAccount.account.currencyUid,
            accountUid = targetAccount.account.uid,
            categoryUid = category.uid,
            note = "Automated test expense entry",
            description = "E2E verification",
            timestamp = System.currentTimeMillis()
        )

        repository.saveTransaction(newTx)

        // Verify transaction is in active transactions
        val txsAfterAdd = repository.allTransactions.first()
        val addedTxItem = txsAfterAdd.firstOrNull { it.transaction.uid == testTxUid }
        assertNotNull("Newly added transaction must exist in repository", addedTxItem)
        assertEquals(450.0, addedTxItem!!.transaction.accountAmount, 0.001)
        assertEquals("Automated test expense entry", addedTxItem.transaction.note)

        // Verify account balance updated (-450)
        val netWorthAfterAdd = repository.netWorthSummary.first()
        val balanceAfterAdd = netWorthAfterAdd!!.accountBalances
            .first { it.account.uid == targetAccount.account.uid }.nativeBalance
        assertEquals(initialAccountBalance - 450.0, balanceAfterAdd, 0.01)

        // 5. Test EDITING the transaction
        val editedTx = newTx.copy(
            accountAmount = 600.0,
            baseAmount = 600.0,
            note = "Automated test expense entry - updated"
        )
        repository.saveTransaction(editedTx)

        val txsAfterEdit = repository.allTransactions.first()
        val editedTxItem = txsAfterEdit.firstOrNull { it.transaction.uid == testTxUid }
        assertNotNull("Edited transaction must exist", editedTxItem)
        assertEquals(600.0, editedTxItem!!.transaction.accountAmount, 0.001)
        assertEquals("Automated test expense entry - updated", editedTxItem.transaction.note)

        // Verify account balance updated for edit (-600 instead of -450)
        val netWorthAfterEdit = repository.netWorthSummary.first()
        val balanceAfterEdit = netWorthAfterEdit!!.accountBalances
            .first { it.account.uid == targetAccount.account.uid }.nativeBalance
        assertEquals(initialAccountBalance - 600.0, balanceAfterEdit, 0.01)

        // 6. Test REMOVING the transaction
        repository.deleteTransaction(testTxUid)

        val txsAfterDelete = repository.allTransactions.first()
        val deletedTxItem = txsAfterDelete.firstOrNull { it.transaction.uid == testTxUid }
        assertNull("Deleted transaction must not appear in active transactions", deletedTxItem)

        // Verify balance rolled back cleanly to initial balance
        val netWorthAfterDelete = repository.netWorthSummary.first()
        val balanceAfterDelete = netWorthAfterDelete!!.accountBalances
            .first { it.account.uid == targetAccount.account.uid }.nativeBalance
        assertEquals(initialAccountBalance, balanceAfterDelete, 0.01)

        // 7. Test MODIFYING an account
        val originalName = targetAccount.account.name
        val updatedName = "Salary Account Verified"
        val modifiedAccount = targetAccount.account.copy(
            name = updatedName,
            openingBalance = targetAccount.account.openingBalance + 1000.0
        )
        repository.saveAccount(modifiedAccount)

        val accountsAfterUpdate = repository.accountsWithCurrency.first()
        val updatedAccountEntity = accountsAfterUpdate.firstOrNull { it.account.uid == targetAccount.account.uid }?.account
        assertNotNull("Modified account must exist", updatedAccountEntity)
        assertEquals(updatedName, updatedAccountEntity!!.name)
        assertEquals(targetAccount.account.openingBalance + 1000.0, updatedAccountEntity.openingBalance, 0.01)

        // Rollback name
        repository.saveAccount(targetAccount.account)
        val finalAccount = repository.accountsWithCurrency.first().first { it.account.uid == targetAccount.account.uid }.account
        assertEquals(originalName, finalAccount.name)

        // Clean up temp file
        if (tempBackupFile.exists()) tempBackupFile.delete()
    }
}
