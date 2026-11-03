package com.moneymanager.app.ui.main

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.moneymanager.app.data.local.entity.TransactionType
import com.moneymanager.app.domain.insights.InsightPreferencesManager
import com.moneymanager.app.domain.statement.core.StatementParserRegistry
import com.moneymanager.app.ui.theme.MoneyManagerTheme
import com.moneymanager.app.ui.theme.ThemePreferenceManager
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainScreenUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        PDFBoxResourceLoader.init(app)
        ThemePreferenceManager.init(app)
        InsightPreferencesManager.init(app)

        composeTestRule.runOnUiThread {
            viewModel = ViewModelProvider(
                composeTestRule.activity,
                ViewModelProvider.AndroidViewModelFactory.getInstance(app)
            )[MainViewModel::class.java]
        }
    }

    private fun setMainContent() {
        composeTestRule.setContent {
            MoneyManagerTheme {
                MainScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun testTabNavigationAndUiStability() {
        setMainContent()

        // 1. Dashboard is active by default
        composeTestRule.onNodeWithText("Dashboard").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Add Transaction").assertIsDisplayed()

        // 2. Navigate to Stats tab
        composeTestRule.onNodeWithText("Stats").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Stats").assertIsDisplayed()

        // 3. Navigate to Accounts tab
        composeTestRule.onNodeWithText("Accounts").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Total net worth").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Add Account").assertIsDisplayed()

        // 4. Navigate to Cards tab
        composeTestRule.onNodeWithText("Cards").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithText("Total due")[0].assertIsDisplayed()
        composeTestRule.onNodeWithText("Recent spends").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Import Statement PDF").assertIsDisplayed()

        // 5. Navigate to Settings (Manage) tab
        composeTestRule.onAllNodesWithText("Settings")[0].performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Personal Vault").assertIsDisplayed()

        // 6. Return back to Dashboard
        composeTestRule.onNodeWithText("Dashboard").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Add Transaction").assertIsDisplayed()
    }

    @Test
    fun testAccountsCreationAndEditingWorkflow() {
        setMainContent()

        // 1. Navigate to Accounts tab
        composeTestRule.onNodeWithText("Accounts").performClick()
        composeTestRule.waitForIdle()

        // 2. Click Add Account
        composeTestRule.onNodeWithContentDescription("Add Account").performClick()
        composeTestRule.waitForIdle()

        // 3. Verify Dialog opened
        composeTestRule.onNodeWithText("New Account").assertIsDisplayed()

        // 4. Fill in Account Name and Opening Balance
        composeTestRule.onNodeWithText("Account Name").performTextInput("HDFC Main Salary")
        composeTestRule.onNodeWithText("Opening Balance").performTextReplacement("65000.00")

        // 5. Click Create
        composeTestRule.onNodeWithText("Create").performClick()
        composeTestRule.waitForIdle()

        // 6. Verify account appears in list and Net Worth updates
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("HDFC Main Salary").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("HDFC Main Salary").assertIsDisplayed()
        composeTestRule.onNodeWithText("Total net worth").assertIsDisplayed()

        // 7. Click on the created account to open Edit Account dialog
        composeTestRule.onNodeWithText("HDFC Main Salary").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Edit Account").assertIsDisplayed()

        // 8. Edit name and update
        composeTestRule.onNodeWithText("Account Name").performTextReplacement("HDFC Premium Salary")
        composeTestRule.onNodeWithText("Update").performClick()
        composeTestRule.waitForIdle()

        // 9. Verify updated name appears
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("HDFC Premium Salary").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("HDFC Premium Salary").assertIsDisplayed()
    }

    @Test
    fun testTransactionCreationAndEditingWorkflow() {
        // Pre-create an expense transaction
        viewModel.saveTransaction(
            type = TransactionType.EXPENSE,
            accountUid = "ACC_SAVINGS",
            amount = 1450.0,
            currencyUid = "CURRENCY_INR",
            categoryUid = "CAT_FOOD",
            note = "Team Dinner at Social"
        )

        setMainContent()

        // Verify transaction is visible on the Dashboard ledger
        composeTestRule.waitUntil(10_000) {
            composeTestRule.onAllNodes(hasText("Team Dinner at Social") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNode(hasText("Team Dinner at Social") and hasClickAction()).assertIsDisplayed()

        // Tap the transaction row in the ledger to open AddEditTransactionSheet
        composeTestRule.onNode(hasText("Team Dinner at Social") and hasClickAction()).performClick()
        composeTestRule.waitForIdle()

        // Verify AddEditTransactionSheet opens in update mode
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Edit transaction").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Edit transaction").assertIsDisplayed()

        // Scroll the form to reveal the Update button
        composeTestRule.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText("Update"))
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Update").assertIsDisplayed()
        composeTestRule.onNodeWithText("Update").performClick()
        composeTestRule.waitForIdle()

        // Verify sheet dismissed and transaction remains on dashboard
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Edit transaction").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNode(hasText("Team Dinner at Social") and hasClickAction()).assertIsDisplayed()
    }

    @Test
    fun testCardsTabWithImportedStatements() = runBlocking {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val parser = StatementParserRegistry()

        // Parse and import the Axis Airtel statement
        val parseResult = testContext.assets.open("fixtures/axis_airtel_golden.pdf").use { stream ->
            parser.parse(stream, password = "prit1510")
        }
        val stmtId = viewModel.statementRepository.importStatement(parseResult)
        assertTrue("Statement import should succeed", stmtId.isNotBlank())

        // Also parse and import the ICICI Amazon Pay statement
        val iciciResult = testContext.assets.open("fixtures/icici_amazon_pay_statement_golden.pdf").use { stream ->
            parser.parse(stream, password = "prit1510")
        }
        viewModel.statementRepository.importStatement(iciciResult)

        setMainContent()

        // Navigate to Cards tab
        composeTestRule.onNodeWithText("Cards").performClick()
        composeTestRule.waitForIdle()

        // Wait until statement summaries are emitted by Room and rendered
        composeTestRule.waitUntil(10_000) {
            composeTestRule.onAllNodesWithText(parseResult.metadata.bankName).fetchSemanticsNodes().isNotEmpty()
        }

        // Verify Total Due and Bank name appear on screen (using index 0 in case multiple statements for same bank exist)
        composeTestRule.onAllNodesWithText("Total due")[0].assertIsDisplayed()
        composeTestRule.onAllNodesWithText(parseResult.metadata.bankName)[0].assertIsDisplayed()

        // Switch to "Recent spends" subtab using clickable pill
        composeTestRule.onNode(hasText("Recent spends") and hasClickAction()).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Live Unbilled Spends").assertIsDisplayed()

        // Switch back to "Total due" subtab using clickable pill
        composeTestRule.onNode(hasText("Total due") and hasClickAction()).performClick()
        composeTestRule.waitForIdle()

        // Tap on the statement card to open StatementDetailBottomSheet
        composeTestRule.onAllNodesWithText(parseResult.metadata.bankName)[0].performClick()
        composeTestRule.waitForIdle()

        // Verify bottom sheet contents
        composeTestRule.waitUntil(10_000) {
            composeTestRule.onAllNodesWithText("${parseResult.metadata.bankName} Statement").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("${parseResult.metadata.bankName} Statement").assertIsDisplayed()
        composeTestRule.onNodeWithText("Statement Billed").assertIsDisplayed()

        // Dismiss sheet via Close button
        composeTestRule.onNodeWithText("Close").performClick()
        composeTestRule.waitForIdle()
    }

    private fun assertTrue(message: String, condition: Boolean) {
        org.junit.Assert.assertTrue(message, condition)
    }
}
