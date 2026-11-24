package com.moneymanager.app.ui.main

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneymanager.app.data.local.AppDatabase
import com.moneymanager.app.data.local.entity.*
import com.moneymanager.app.domain.insights.InsightPreferencesManager
import com.moneymanager.app.ui.theme.MoneyManagerTheme
import com.moneymanager.app.ui.theme.ThemePreferenceManager
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** UI tests for [com.moneymanager.app.ui.main.MainScreen]. */
@RunWith(AndroidJUnit4::class)
class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private lateinit var viewModel: MainViewModel

  @Before
  fun setup() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    ThemePreferenceManager.init(app)
    InsightPreferencesManager.init(app)

    val database = AppDatabase.getDatabase(app)
    database.clearAllTables()
    runBlocking {
      val dao = database.moneyManagerDao()
      dao.insertAccountTypes(AccountTypeDefaults.defaultEntities())
      dao.insertCurrencies(
        listOf(
          CurrencyEntity(
            uid = "CURRENCY_INR",
            name = "INR - India (₹)",
            isoCode = "INR",
            symbol = "₹",
            rateToBase = 1.0,
            isMainCurrency = true
          )
        )
      )
      dao.insertAccounts(
        listOf(
          AccountEntity(
            uid = "ACC_SAVINGS",
            name = "Primary Savings Bank",
            accountType = AccountType.SAVINGS,
            currencyUid = "CURRENCY_INR",
            openingBalance = 0.0,
            sortOrder = 2,
            accountTypeUid = AccountType.SAVINGS.name
          )
        )
      )
      dao.insertCategories(
        listOf(
          CategoryEntity("CAT_FOOD", "Food & Dining", CategoryType.EXPENSE, null, "🍜", 1)
        )
      )
    }

    composeTestRule.runOnUiThread {
      viewModel = ViewModelProvider(
        composeTestRule.activity,
        ViewModelProvider.AndroidViewModelFactory.getInstance(app)
      )[MainViewModel::class.java]
    }

    composeTestRule.setContent {
      MoneyManagerTheme {
        MainScreen(viewModel = viewModel, quickAddType = TransactionType.EXPENSE)
      }
    }
    composeTestRule.waitForIdle()
  }

  @Test
  fun quickAddExpense_opensNewTransactionSheet() {
    composeTestRule.waitUntil(5_000) {
      composeTestRule.onAllNodes(androidx.compose.ui.test.hasText("New transaction"))
        .fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithText("New transaction").assertIsDisplayed()
  }
}
