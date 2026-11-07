package com.moneymanager.app.domain.gmail

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.moneymanager.app.data.FakeStatementDao
import com.moneymanager.app.data.StatementRepository
import com.moneymanager.app.data.local.entity.GmailImportStatus
import com.moneymanager.app.data.local.entity.GmailSyncStatus
import com.moneymanager.app.domain.gmail.api.*
import com.moneymanager.app.domain.gmail.auth.GmailOAuthManager
import com.moneymanager.app.domain.gmail.auth.GmailTokenStorage
import com.moneymanager.app.domain.gmail.sync.GmailSyncEngine
import com.moneymanager.app.domain.gmail.sync.SyncOutcome
import com.moneymanager.app.domain.statement.core.BankStatementParser
import com.moneymanager.app.domain.statement.core.StatementParserRegistry
import com.moneymanager.app.domain.statement.parsers.icici.IciciAmazonPayCreditCardParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.InputStream
import java.lang.reflect.Proxy

class GmailStatementParserIntegrationTest {

    private lateinit var context: Context
    private lateinit var tempCacheDir: File
    private lateinit var fakeImportDao: FakeGmailImportDao
    private lateinit var fakeSyncDao: FakeGmailSyncDao
    private lateinit var fakeStatementDao: FakeStatementDao
    private lateinit var statementRepo: StatementRepository
    private lateinit var fakeOAuthManager: GmailOAuthManager

    @Before
    fun setUp() {
        tempCacheDir = File(System.getProperty("java.io.tmpdir"), "moneymanager_integrity_cache_${System.currentTimeMillis()}").apply { mkdirs() }
        val memoryPrefs = mutableMapOf<String, Any>()

        var editorProxy: SharedPreferences.Editor? = null
        editorProxy = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)
        ) { _, editMethod, editArgs ->
            when (editMethod.name) {
                "putString" -> {
                    if (editArgs[1] != null) memoryPrefs[editArgs[0] as String] = editArgs[1]
                    else memoryPrefs.remove(editArgs[0] as String)
                    editorProxy
                }
                "putLong" -> {
                    memoryPrefs[editArgs[0] as String] = editArgs[1]
                    editorProxy
                }
                "remove" -> {
                    memoryPrefs.remove(editArgs[0] as String)
                    editorProxy
                }
                "clear" -> {
                    memoryPrefs.clear()
                    editorProxy
                }
                "apply", "commit" -> null
                else -> editorProxy
            }
        } as SharedPreferences.Editor

        val fakePrefs = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getString" -> memoryPrefs[args[0] as String] ?: (if (args.size > 1) args[1] else null)
                "getLong" -> memoryPrefs[args[0] as String] ?: (if (args.size > 1) args[1] else 0L)
                "getBoolean" -> memoryPrefs[args[0] as String] ?: (if (args.size > 1) args[1] else false)
                "edit" -> editorProxy
                else -> null
            }
        } as SharedPreferences

        context = object : ContextWrapper(null) {
            override fun getCacheDir(): File = tempCacheDir
            override fun getFilesDir(): File = tempCacheDir
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = fakePrefs
            override fun getApplicationContext(): Context = this
        }

        fakeImportDao = FakeGmailImportDao()
        fakeSyncDao = FakeGmailSyncDao()
        fakeStatementDao = FakeStatementDao()
        statementRepo = StatementRepository(fakeStatementDao)

        fakeOAuthManager = object : GmailOAuthManager() {
            override suspend fun getFreshAccessToken(context: Context): String = "valid_test_token"
        }
    }

    private fun loadGoldenPdfBytes(): ByteArray {
        val stream = javaClass.classLoader?.getResourceAsStream("fixtures/icici_amazon_pay_statement_golden.pdf")
            ?: javaClass.getResourceAsStream("/fixtures/icici_amazon_pay_statement_golden.pdf")
        assertNotNull("Golden PDF fixture must be available for testing", stream)
        return stream!!.use { it.readBytes() }
    }

    @Test
    fun testEndToEnd_StatementParserTriggeredWithRightParamsAndImportsIntoDatabase() = runTest {
        val goldenPdfBytes = loadGoldenPdfBytes()
        var parserReceivedPassword: String? = null
        var parserInvocationCount = 0

        // Custom parser wrapping real IciciAmazonPayCreditCardParser to inspect input parameters
        val realIciciParser = IciciAmazonPayCreditCardParser()
        val interceptingParser = object : BankStatementParser by realIciciParser {
            override fun parse(inputStream: InputStream, password: String?): com.moneymanager.app.domain.statement.core.StatementParseResult {
                parserInvocationCount++
                parserReceivedPassword = password
                return realIciciParser.parse(inputStream, password)
            }
        }

        val testRegistry = StatementParserRegistry(listOf(interceptingParser))

        // Set default password in storage to verify propagation
        GmailTokenStorage.saveDefaultPdfPassword(context, "test_decryption_password_123")

        val messageId = "msg_icici_statement_001"
        val attachmentId = "att_pdf_001"

        // Mock Gmail API returning new statement email with PDF
        val fakeApiClient = object : GmailApiClient() {
            override suspend fun listHistory(
                accessToken: String,
                startHistoryId: String,
                maxResults: Int,
                pageToken: String?
            ): GmailHistoryResponse {
                return GmailHistoryResponse(
                    history = listOf(
                        GmailHistoryRecord(
                            id = "hist_01",
                            messagesAdded = listOf(
                                GmailMessageAdded(GmailMessageRef(id = messageId))
                            )
                        )
                    ),
                    historyId = "history_step_2"
                )
            }

            override suspend fun getMessage(accessToken: String, messageId: String): GmailMessageDetail {
                return GmailMessageDetail(
                    id = messageId,
                    threadId = "thread_01",
                    internalDate = 1729000000000L,
                    headers = listOf(
                        GmailHeader("From", "credit_cards@icici.bank.in"),
                        GmailHeader("Subject", "Your ICICI Bank Amazon Pay Credit Card Statement for Sep 2026"),
                        GmailHeader("Date", "Tue, 15 Oct 2026 10:00:00 +0530"),
                        GmailHeader("Authentication-Results", "mx.google.com; dkim=pass; dmarc=pass header.from=icici.bank.in")
                    ),
                    attachments = listOf(
                        GmailAttachmentRef(
                            attachmentId = attachmentId,
                            filename = "4315XXXXXXXX3004_Statement.pdf",
                            mimeType = "application/pdf",
                            size = goldenPdfBytes.size.toLong()
                        )
                    )
                )
            }

            override suspend fun getAttachment(
                accessToken: String,
                messageId: String,
                attachmentId: String
            ): ByteArray {
                return goldenPdfBytes
            }
        }

        val engine = GmailSyncEngine(
            apiClient = fakeApiClient,
            oauthManager = fakeOAuthManager,
            parserRegistry = testRegistry
        )

        // Initialize baseline
        fakeSyncDao.updateSyncProgress(
            historyId = "history_step_1",
            timestamp = 1000L,
            status = GmailSyncStatus.SUCCESS.name
        )

        // Run sync
        val outcome = engine.syncIncremental(context, fakeImportDao, fakeSyncDao, statementRepo)

        // 1. Verify sync outcome
        assertTrue("Sync must succeed", outcome is SyncOutcome.Success)
        val success = outcome as SyncOutcome.Success
        assertEquals("Must report exactly 1 imported statement", 1, success.newStatementsCount)
        assertEquals("Must inspect 1 message", 1, success.messagesProcessed)

        // 2. Verify parser was triggered with exact right parameters
        assertEquals("Statement parser must be invoked exactly once", 1, parserInvocationCount)
        assertEquals(
            "Parser must receive the configured default statement password",
            "test_decryption_password_123",
            parserReceivedPassword
        )

        // 3. Verify Import History Record in Dao
        val historyRecords = fakeImportDao.getAllHistoryRaw()
        assertEquals(1, historyRecords.size)
        val record = historyRecords.first()
        assertEquals(messageId, record.messageId)
        assertEquals("ICICI Bank", record.bankName)
        assertEquals(GmailImportStatus.SUCCESS.name, record.importStatus)
        assertNotNull("Statement ID must be populated", record.statementId)
        val stmtId = record.statementId ?: ""
        assertTrue("Statement ID should identify the card and month", stmtId.contains("4315") || stmtId.contains("3004"))

        // 4. Verify data imported into StatementRepository & StatementDao
        val statementSummaries = fakeStatementDao.getAllStatementSummariesRaw()
        assertEquals("Exactly 1 statement record must exist in statement ledger", 1, statementSummaries.size)
        val summary = statementSummaries.first()
        assertEquals("ICICI_AMAZON_PAY_CC", summary.bankId)
        assertTrue("Summary total amount due must be > 0", summary.statementTotalDue > 0.0)
        assertTrue("Summary transactions count must be > 0", summary.transactionCount > 0)

        val transactions = fakeStatementDao.getTransactionsByStatementIdRaw(summary.statementId)
        assertTrue("Transactions must be inserted into database", transactions.isNotEmpty())

        // 5. Verify security: Temporary downloaded PDF in cacheDir is completely wiped
        val leftoverFiles = tempCacheDir.listFiles { _, name -> name.endsWith(".pdf") || name.startsWith("gmail_stmt_") }
        assertTrue("Temporary downloaded PDF file must be deleted immediately from private storage", leftoverFiles.isNullOrEmpty())
    }

    @Test
    fun testParserPasswordProtectionHandling_FlagsPasswordRequired() = runTest {
        val goldenPdfBytes = loadGoldenPdfBytes()

        // Intercepting parser that simulates a password-protected statement
        val passwordProtectedParser = object : BankStatementParser {
            override val bankId: String = "ICICI_AMAZON_PAY_CC"
            override val displayName: String = "ICICI Bank"
            override val supportedStatementType = com.moneymanager.app.domain.statement.core.StatementType.CREDIT_CARD

            override fun canParse(context: com.moneymanager.app.domain.statement.core.StatementInspectionContext): Boolean = true

            override fun parse(
                inputStream: InputStream,
                password: String?
            ): com.moneymanager.app.domain.statement.core.StatementParseResult {
                if (password.isNullOrBlank()) {
                    throw com.moneymanager.app.domain.statement.core.StatementSecurityException.PasswordRequiredException()
                }
                throw com.moneymanager.app.domain.statement.core.StatementSecurityException.InvalidPasswordException()
            }
        }

        val testRegistry = StatementParserRegistry(listOf(passwordProtectedParser))
        GmailTokenStorage.saveDefaultPdfPassword(context, null) // No password configured

        val messageId = "msg_encrypted_002"
        val fakeApiClient = object : GmailApiClient() {
            override suspend fun listHistory(accessToken: String, startHistoryId: String, maxResults: Int, pageToken: String?): GmailHistoryResponse {
                return GmailHistoryResponse(
                    history = listOf(GmailHistoryRecord(id = "hist_02", messagesAdded = listOf(GmailMessageAdded(GmailMessageRef(id = messageId))))),
                    historyId = "history_step_3"
                )
            }

            override suspend fun getMessage(accessToken: String, messageId: String): GmailMessageDetail {
                return GmailMessageDetail(
                    id = messageId,
                    threadId = "t2",
                    internalDate = 1729000000000L,
                    headers = listOf(
                        GmailHeader("From", "alerts@axis.bank.in"),
                        GmailHeader("Subject", "Axis Bank Credit Card Statement"),
                        GmailHeader("Authentication-Results", "mx.google.com; dkim=pass; dmarc=pass header.from=axis.bank.in")
                    ),
                    attachments = listOf(GmailAttachmentRef("att_enc", "axis_statement.pdf", "application/pdf", goldenPdfBytes.size.toLong()))
                )
            }

            override suspend fun getAttachment(accessToken: String, messageId: String, attachmentId: String): ByteArray {
                return goldenPdfBytes
            }
        }

        val engine = GmailSyncEngine(
            apiClient = fakeApiClient,
            oauthManager = fakeOAuthManager,
            parserRegistry = testRegistry
        )

        fakeSyncDao.updateSyncProgress("history_step_2", 1000L, GmailSyncStatus.SUCCESS.name)

        val outcome = engine.syncIncremental(context, fakeImportDao, fakeSyncDao, statementRepo)
        assertTrue(outcome is SyncOutcome.Success)

        val records = fakeImportDao.getAllHistoryRaw()
        assertEquals(1, records.size)
        assertEquals(GmailImportStatus.PASSWORD_REQUIRED.name, records[0].importStatus)
        assertTrue("Error message must indicate password requirement", records[0].errorMessage!!.contains("password"))

        // Ensure temp file cleaned up even on security exception
        val leftover = tempCacheDir.listFiles { _, name -> name.startsWith("gmail_stmt_") }
        assertTrue("Cache file must be deleted on password required exception", leftover.isNullOrEmpty())
    }
}
