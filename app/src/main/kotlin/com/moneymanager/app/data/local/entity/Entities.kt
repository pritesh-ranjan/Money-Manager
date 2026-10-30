package com.moneymanager.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

enum class AccountType(val displayName: String, val isLiability: Boolean) {
    CASH("Cash", false),
    SAVINGS("Bank / Savings", false),
    WALLET("Wallet / Prepaid", false),
    CREDIT_CARD("Credit Card", true),
    INVESTMENT("Fixed Deposit / PPF / SGB", false),
    EQUITY("Equity / Stocks", false),
    MUTUAL_FUNDS("Mutual Funds", false),
    UPI_DEBIT("Linked UPI / Debit", false),
    LOAN("Loan / Liability", true),
    OTHER("Other", false)
}

enum class TransactionType(val displayName: String) {
    EXPENSE("Expense"),
    INCOME("Income"),
    TRANSFER("Transfer"),
    ADJUSTMENT("Balance Adjustment")
}

enum class CategoryType {
    EXPENSE,
    INCOME
}

@Entity(
    tableName = "currencies",
    indices = [Index(value = ["isoCode"])]
)
data class CurrencyEntity(
    @PrimaryKey
    val uid: String,
    val name: String,
    val isoCode: String,
    val symbol: String,
    val rateToBase: Double, // 1.0 for main base currency
    val isMainCurrency: Boolean = false,
    val isCustom: Boolean = false,
    val decimalPlaces: Int = 2
)

@Entity(
    tableName = "accounts",
    foreignKeys = [
        ForeignKey(
            entity = CurrencyEntity::class,
            parentColumns = ["uid"],
            childColumns = ["currencyUid"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("currencyUid"),
        Index("linkedParentAccountUid")
    ]
)
data class AccountEntity(
    @PrimaryKey
    val uid: String,
    val name: String,
    val accountType: AccountType,
    val currencyUid: String,
    val openingBalance: Double = 0.0,
    // Credit card billing engine attributes
    val statementDay: Int? = null, // Cutoff day of the month (1-31)
    val paymentDueDay: Int? = null, // Payment due day of the month (1-31)
    val creditLimit: Double? = null,
    val settlementAccountUid: String? = null, // Default bank account to pay this credit card from
    // Linked accounts (e.g. Bank UPI drawing from parent Savings account)
    val linkedParentAccountUid: String? = null,
    val includeInNetWorth: Boolean = true,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false
)

@Entity(
    tableName = "categories",
    indices = [Index("parentUid"), Index("type")]
)
data class CategoryEntity(
    @PrimaryKey
    val uid: String,
    val name: String,
    val type: CategoryType,
    val parentUid: String? = null, // null for root category, non-null for subcategory
    val icon: String = "📁",
    val sortOrder: Int = 0,
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)]
)
data class TagEntity(
    @PrimaryKey
    val uid: String,
    val name: String,
    val sortOrder: Int = 0
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["uid"],
            childColumns = ["accountUid"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("accountUid"),
        Index("toAccountUid"),
        Index("categoryUid"),
        Index("timestamp"),
        Index("type")
    ]
)
data class TransactionEntity(
    @PrimaryKey
    val uid: String,
    val type: TransactionType,
    val accountAmount: Double, // in account's native currency
    val baseAmount: Double, // snapshot in primary currency
    val exchangeRate: Double = 1.0,
    val currencyUid: String,
    val accountUid: String,
    val toAccountUid: String? = null, // used for TRANSFER
    val toAccountAmount: Double? = null, // native amount received in destination account
    val categoryUid: String? = null, // root category or subcategory
    val note: String = "", // short title / payee (e.g. ZCONTENT)
    val description: String = "", // detailed memo (e.g. ZDATA)
    val timestamp: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "transaction_tags",
    primaryKeys = ["transactionUid", "tagUid"],
    indices = [Index("tagUid"), Index("transactionUid")]
)
data class TransactionTagCrossRef(
    val transactionUid: String,
    val tagUid: String
)

// Domain Relation Models
data class TransactionWithDetails(
    @Embedded
    val transaction: TransactionEntity,

    @Relation(
        parentColumn = "accountUid",
        entityColumn = "uid"
    )
    val account: AccountEntity,

    @Relation(
        parentColumn = "toAccountUid",
        entityColumn = "uid"
    )
    val toAccount: AccountEntity? = null,

    @Relation(
        parentColumn = "categoryUid",
        entityColumn = "uid"
    )
    val category: CategoryEntity? = null,

    @Relation(
        parentColumn = "currencyUid",
        entityColumn = "uid"
    )
    val currency: CurrencyEntity,

    @Relation(
        parentColumn = "uid",
        entityColumn = "uid",
        associateBy = Junction(
            value = TransactionTagCrossRef::class,
            parentColumn = "transactionUid",
            entityColumn = "tagUid"
        )
    )
    val tags: List<TagEntity> = emptyList()
)

data class CategoryWithSubcategories(
    @Embedded
    val category: CategoryEntity,

    @Relation(
        parentColumn = "uid",
        entityColumn = "parentUid"
    )
    val subcategories: List<CategoryEntity> = emptyList()
)

data class AccountWithCurrency(
    @Embedded
    val account: AccountEntity,

    @Relation(
        parentColumn = "currencyUid",
        entityColumn = "uid"
    )
    val currency: CurrencyEntity,

    @Relation(
        parentColumn = "linkedParentAccountUid",
        entityColumn = "uid"
    )
    val parentAccount: AccountEntity? = null
)
