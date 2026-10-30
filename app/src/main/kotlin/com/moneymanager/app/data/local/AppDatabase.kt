package com.moneymanager.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.moneymanager.app.data.local.dao.MoneyManagerDao
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.data.local.entity.AccountType
import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.data.local.entity.CategoryType
import com.moneymanager.app.data.local.entity.CurrencyEntity
import com.moneymanager.app.data.local.entity.TagEntity
import com.moneymanager.app.data.local.entity.TransactionEntity
import com.moneymanager.app.data.local.entity.TransactionTagCrossRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

@Database(
    entities = [
        CurrencyEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        TagEntity::class,
        TransactionEntity::class,
        TransactionTagCrossRef::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun moneyManagerDao(): MoneyManagerDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope = CoroutineScope(Dispatchers.IO)): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "money_manager.db"
                )
                    .addCallback(DatabaseCallback(scope))
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        seedInitialData(database.moneyManagerDao())
                    }
                }
            }
        }

        private suspend fun seedInitialData(dao: MoneyManagerDao) {
            // Seed Currencies
            val inr = CurrencyEntity(
                uid = "CURRENCY_INR",
                name = "INR - India (₹)",
                isoCode = "INR",
                symbol = "₹",
                rateToBase = 1.0,
                isMainCurrency = true,
                isCustom = false,
                decimalPlaces = 2
            )
            val usd = CurrencyEntity(
                uid = "CURRENCY_USD",
                name = "USD - US Dollar ($)",
                isoCode = "USD",
                symbol = "$",
                rateToBase = 84.0,
                isMainCurrency = false,
                isCustom = false,
                decimalPlaces = 2
            )
            val eur = CurrencyEntity(
                uid = "CURRENCY_EUR",
                name = "EUR - Euro (€)",
                isoCode = "EUR",
                symbol = "€",
                rateToBase = 91.0,
                isMainCurrency = false,
                isCustom = false,
                decimalPlaces = 2
            )
            // Example custom currency (loyalty points)
            val scapiaCoins = CurrencyEntity(
                uid = "CURRENCY_SCAPIA",
                name = "Scapia Coins",
                isoCode = "SCAPIA",
                symbol = "🪙",
                rateToBase = 0.20,
                isMainCurrency = false,
                isCustom = true,
                decimalPlaces = 0
            )

            dao.insertCurrencies(listOf(inr, usd, eur, scapiaCoins))

            // Seed Default Accounts
            val cashAcc = AccountEntity(
                uid = "ACC_CASH",
                name = "Cash Wallet",
                accountType = AccountType.CASH,
                currencyUid = inr.uid,
                openingBalance = 0.0,
                sortOrder = 1
            )
            val savingsAcc = AccountEntity(
                uid = "ACC_SAVINGS",
                name = "Primary Savings Bank",
                accountType = AccountType.SAVINGS,
                currencyUid = inr.uid,
                openingBalance = 0.0,
                sortOrder = 2
            )
            val creditCard = AccountEntity(
                uid = "ACC_CC_DEMO",
                name = "Credit Card",
                accountType = AccountType.CREDIT_CARD,
                currencyUid = inr.uid,
                openingBalance = 0.0,
                statementDay = 14,
                paymentDueDay = 3,
                creditLimit = 150000.0,
                settlementAccountUid = savingsAcc.uid,
                sortOrder = 3
            )
            dao.insertAccounts(listOf(cashAcc, savingsAcc, creditCard))

            // Seed Standard Categories & Subcategories
            val foodCat = CategoryEntity("CAT_FOOD", "Food & Dining", CategoryType.EXPENSE, null, "🍜", 1)
            val transportCat = CategoryEntity("CAT_TRANSPORT", "Transport", CategoryType.EXPENSE, null, "🚖", 2)
            val shoppingCat = CategoryEntity("CAT_SHOPPING", "Shopping", CategoryType.EXPENSE, null, "🛍️", 3)
            val healthCat = CategoryEntity("CAT_HEALTH", "Healthcare", CategoryType.EXPENSE, null, "🧘🏼", 4)
            val billsCat = CategoryEntity("CAT_BILLS", "Bills & Admin", CategoryType.EXPENSE, null, "📄", 5)
            val leisureCat = CategoryEntity("CAT_LEISURE", "Lifestyle & Leisure", CategoryType.EXPENSE, null, "🎬", 6)

            val salaryCat = CategoryEntity("CAT_SALARY", "Salary", CategoryType.INCOME, null, "💰", 1)
            val bonusCat = CategoryEntity("CAT_BONUS", "Bonus & Allowance", CategoryType.INCOME, null, "🏅", 2)
            val investRedeemCat = CategoryEntity("CAT_INVEST_INC", "Investment Returns", CategoryType.INCOME, null, "📈", 3)
            val rewardsCat = CategoryEntity("CAT_REWARDS", "Cashback & Rewards", CategoryType.INCOME, null, "🎁", 4)

            val parentCategories = listOf(foodCat, transportCat, shoppingCat, healthCat, billsCat, leisureCat, salaryCat, bonusCat, investRedeemCat, rewardsCat)
            dao.insertCategories(parentCategories)

            val subCategories = listOf(
                CategoryEntity(UUID.randomUUID().toString(), "Dining Out", CategoryType.EXPENSE, foodCat.uid, "🍽️", 1),
                CategoryEntity(UUID.randomUUID().toString(), "Food Delivery", CategoryType.EXPENSE, foodCat.uid, "🛵", 2),
                CategoryEntity(UUID.randomUUID().toString(), "Groceries", CategoryType.EXPENSE, foodCat.uid, "🛒", 3),

                CategoryEntity(UUID.randomUUID().toString(), "Cab / Auto", CategoryType.EXPENSE, transportCat.uid, "🚕", 1),
                CategoryEntity(UUID.randomUUID().toString(), "Metro / Train", CategoryType.EXPENSE, transportCat.uid, "🚇", 2),
                CategoryEntity(UUID.randomUUID().toString(), "Flight", CategoryType.EXPENSE, transportCat.uid, "✈️", 3),

                CategoryEntity(UUID.randomUUID().toString(), "Clothes", CategoryType.EXPENSE, shoppingCat.uid, "👕", 1),
                CategoryEntity(UUID.randomUUID().toString(), "Electronics", CategoryType.EXPENSE, shoppingCat.uid, "📱", 2),

                CategoryEntity(UUID.randomUUID().toString(), "Medicines", CategoryType.EXPENSE, healthCat.uid, "💊", 1),
                CategoryEntity(UUID.randomUUID().toString(), "Doctor Consultation", CategoryType.EXPENSE, healthCat.uid, "🩺", 2),

                CategoryEntity(UUID.randomUUID().toString(), "Mobile Recharge", CategoryType.EXPENSE, billsCat.uid, "📲", 1),
                CategoryEntity(UUID.randomUUID().toString(), "Electricity & Wi-Fi", CategoryType.EXPENSE, billsCat.uid, "💡", 2),

                CategoryEntity(UUID.randomUUID().toString(), "Mutual Funds", CategoryType.INCOME, investRedeemCat.uid, "📊", 1),
                CategoryEntity(UUID.randomUUID().toString(), "Dividends & Interest", CategoryType.INCOME, investRedeemCat.uid, "💵", 2)
            )
            dao.insertCategories(subCategories)
        }
    }
}
