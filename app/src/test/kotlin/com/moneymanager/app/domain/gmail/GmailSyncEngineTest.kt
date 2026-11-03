package com.moneymanager.app.domain.gmail

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.moneymanager.app.data.FakeStatementDao
import com.moneymanager.app.data.StatementRepository
import com.moneymanager.app.data.local.entity.GmailImportHistoryEntity
import com.moneymanager.app.data.local.entity.GmailImportStatus
import com.moneymanager.app.data.local.entity.GmailSyncStatus
import com.moneymanager.app.domain.gmail.api.*
import com.moneymanager.app.domain.gmail.auth.GmailOAuthManager
import com.moneymanager.app.domain.gmail.sync.GmailSyncEngine
import com.moneymanager.app.domain.gmail.sync.SyncOutcome
import com.moneymanager.app.domain.statement.core.StatementParserRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

class GmailSyncEngineTest {

    private lateinit var context: Context
    private lateinit var fakeImportDao: FakeGmailImportDao
    private lateinit var fakeSyncDao: FakeGmailSyncDao
    private lateinit var fakeStatementDao: FakeStatementDao
    private lateinit var statementRepo: StatementRepository
    private lateinit var fakeOAuthManager: GmailOAuthManager

    @Before
    fun setUp() {
        val tempCacheDir = File(System.getProperty("java.io.tmpdir"), "moneymanager_test_cache_${System.currentTimeMillis()}").apply { mkdirs() }
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
            override suspend fun getFreshAccessToken(context: Context): String = "fake_access_token_123"
        }
    }

    @Test
    fun testConnectAndInitializeBaseline_savesCurrentHistoryIdWithoutImportingPast() = runTest {
        val fakeApiClient = object : GmailApiClient() {
            override suspend fun getProfile(accessToken: String): GmailProfile {
                return GmailProfile(
                    emailAddress = "user@gmail.com",
                    messagesTotal = 5000,
                    threadsTotal = 2000,
                    historyId = "999888" // baseline history id
                )
            }
        }

        val engine = GmailSyncEngine(
            apiClient = fakeApiClient,
            oauthManager = fakeOAuthManager,
            parserRegistry = StatementParserRegistry()
        )

        val email = engine.connectAndInitializeBaseline(context, fakeImportDao, fakeSyncDao)
        assertEquals("user@gmail.com", email)

        val state = fakeSyncDao.getSyncStateRaw()
        assertNotNull(state)
        assertEquals("999888", state?.lastHistoryId)
        assertEquals("user@gmail.com", state?.accountEmail)
        assertEquals(GmailSyncStatus.SUCCESS.name, state?.lastSyncStatus)

        // Verifies zero historical messages were imported
        val historyList = fakeImportDao.getAllHistoryRaw()
        assertTrue("Baseline initialization must not import any past emails", historyList.isEmpty())
    }

    @Test
    fun testIncrementalSync_skipsAlreadyProcessedMessages() = runTest {
        // Pre-populate an already processed message
        fakeImportDao.insertOrUpdate(
            GmailImportHistoryEntity(
                messageId = "already_seen_msg_1",
                sender = "alerts@icicibank.com",
                subject = "Credit Card Statement",
                receivedEpochMillis = 1000L,
                bankName = "ICICI Bank",
                fileName = "stmt.pdf",
                importStatus = GmailImportStatus.SUCCESS.name
            )
        )

        fakeSyncDao.updateSyncProgress("100", System.currentTimeMillis(), GmailSyncStatus.SUCCESS.name)

        var getMessageCallCount = 0
        val fakeApiClient = object : GmailApiClient() {
            override suspend fun listMessages(
                accessToken: String,
                query: String,
                maxResults: Int,
                pageToken: String?
            ): GmailMessagesListResponse {
                return GmailMessagesListResponse(messageIds = emptyList())
            }

            override suspend fun listHistory(
                accessToken: String,
                startHistoryId: String,
                maxResults: Int,
                pageToken: String?
            ): GmailHistoryResponse {
                return GmailHistoryResponse(
                    history = listOf(
                        GmailHistoryRecord(
                            id = "rec_1",
                            messagesAdded = listOf(
                                GmailMessageAdded(GmailMessageRef("already_seen_msg_1"))
                            )
                        )
                    ),
                    historyId = "105"
                )
            }

            override suspend fun getMessage(accessToken: String, messageId: String): GmailMessageDetail {
                getMessageCallCount++
                throw AssertionError("Should not fetch detail for duplicate message")
            }
        }

        val engine = GmailSyncEngine(
            apiClient = fakeApiClient,
            oauthManager = fakeOAuthManager,
            parserRegistry = StatementParserRegistry()
        )

        val outcome = engine.syncIncremental(context, fakeImportDao, fakeSyncDao, statementRepo)
        assertTrue(outcome is SyncOutcome.Success)
        assertEquals(0, getMessageCallCount)
        assertEquals("105", fakeSyncDao.getSyncStateRaw()?.lastHistoryId)
    }

    @Test
    fun testIncrementalSync_handlesExpiredHistoryIdByRebaselining() = runTest {
        fakeSyncDao.updateSyncProgress("ancient_history_id", System.currentTimeMillis(), GmailSyncStatus.SUCCESS.name)

        val fakeApiClient = object : GmailApiClient() {
            override suspend fun listMessages(
                accessToken: String,
                query: String,
                maxResults: Int,
                pageToken: String?
            ): GmailMessagesListResponse {
                return GmailMessagesListResponse(messageIds = emptyList())
            }

            override suspend fun listHistory(
                accessToken: String,
                startHistoryId: String,
                maxResults: Int,
                pageToken: String?
            ): GmailHistoryResponse {
                throw GmailHistoryNotFoundException("History ID is too old")
            }

            override suspend fun getProfile(accessToken: String): GmailProfile {
                return GmailProfile(
                    emailAddress = "user@gmail.com",
                    messagesTotal = 1000,
                    threadsTotal = 500,
                    historyId = "new_baseline_history_id"
                )
            }
        }

        val engine = GmailSyncEngine(
            apiClient = fakeApiClient,
            oauthManager = fakeOAuthManager,
            parserRegistry = StatementParserRegistry()
        )

        val outcome = engine.syncIncremental(context, fakeImportDao, fakeSyncDao, statementRepo)
        assertTrue(outcome is SyncOutcome.Success)
        assertEquals("new_baseline_history_id", fakeSyncDao.getSyncStateRaw()?.lastHistoryId)
    }

    @Test
    fun testIncrementalSync_queriesMessagesSinceSelectedMonth() = runTest {
        var searchedQuery: String? = null
        val fakeApiClient = object : GmailApiClient() {
            override suspend fun listMessages(
                accessToken: String,
                query: String,
                maxResults: Int,
                pageToken: String?
            ): GmailMessagesListResponse {
                searchedQuery = query
                return GmailMessagesListResponse(
                    messageIds = listOf("new_stmt_msg_1")
                )
            }

            override suspend fun listHistory(
                accessToken: String,
                startHistoryId: String,
                maxResults: Int,
                pageToken: String?
            ): GmailHistoryResponse {
                return GmailHistoryResponse(history = emptyList(), historyId = "200")
            }

            override suspend fun getMessage(accessToken: String, messageId: String): GmailMessageDetail {
                return GmailMessageDetail(
                    id = messageId,
                    threadId = "t1",
                    internalDate = 1000L,
                    headers = listOf(
                        GmailHeader("From", "statements@icicibank.com"),
                        GmailHeader("Subject", "Credit Card Statement")
                    ),
                    attachments = listOf(
                        GmailAttachmentRef("att_1", "statement.pdf", "application/pdf", 1024L)
                    )
                )
            }

            override suspend fun getAttachment(accessToken: String, messageId: String, attachmentId: String): ByteArray {
                return "%PDF-1.4 empty fake pdf".toByteArray()
            }
        }

        fakeSyncDao.updateSyncProgress("100", System.currentTimeMillis(), GmailSyncStatus.SUCCESS.name)

        val engine = GmailSyncEngine(
            apiClient = fakeApiClient,
            oauthManager = fakeOAuthManager,
            parserRegistry = StatementParserRegistry()
        )

        val outcome = engine.syncIncremental(context, fakeImportDao, fakeSyncDao, statementRepo)
        assertTrue(outcome is SyncOutcome.Success)
        assertNotNull(searchedQuery)
        assertTrue("Search query must filter by after date", searchedQuery!!.contains("after:"))
        assertTrue("Search query must filter by pdf attachment", searchedQuery!!.contains("filename:pdf"))
    }
}
