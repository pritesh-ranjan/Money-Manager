package com.moneymanager.app.domain.gmail.sync

import android.content.Context
import com.moneymanager.app.data.StatementRepository
import com.moneymanager.app.data.local.AppDatabase
import com.moneymanager.app.data.local.dao.GmailImportDao
import com.moneymanager.app.data.local.dao.GmailSyncDao
import com.moneymanager.app.data.local.entity.GmailImportHistoryEntity
import com.moneymanager.app.data.local.entity.GmailImportStatus
import com.moneymanager.app.data.local.entity.GmailSyncStateEntity
import com.moneymanager.app.data.local.entity.GmailSyncStatus
import com.moneymanager.app.domain.gmail.api.GmailApiClient
import com.moneymanager.app.domain.gmail.api.GmailHistoryNotFoundException
import com.moneymanager.app.domain.gmail.auth.GmailOAuthManager
import com.moneymanager.app.domain.gmail.auth.GmailTokenStorage
import com.moneymanager.app.domain.gmail.filter.AttachmentValidator
import com.moneymanager.app.domain.gmail.filter.BankFilterEngine
import com.moneymanager.app.domain.statement.core.StatementParserRegistry
import com.moneymanager.app.domain.statement.core.StatementSecurityException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

sealed class SyncOutcome {
    data class Success(val newStatementsCount: Int, val messagesProcessed: Int) : SyncOutcome()
    data class Error(val message: String) : SyncOutcome()
}

class GmailSyncEngine(
    private val apiClient: GmailApiClient = GmailApiClient(),
    private val oauthManager: GmailOAuthManager = GmailOAuthManager(),
    private val parserRegistry: StatementParserRegistry = StatementParserRegistry()
) {

    /**
     * Called on first Google OAuth connection:
     * Saves the current Gmail profile historyId as the baseline;
     * NEVER scans or imports historical emails.
     */
    suspend fun connectAndInitializeBaseline(
        context: Context,
        importDao: GmailImportDao,
        syncDao: GmailSyncDao
    ): String? = withContext(Dispatchers.IO) {
        val accessToken = oauthManager.getFreshAccessToken(context) ?: return@withContext null
        try {
            val profile = apiClient.getProfile(accessToken)
            GmailTokenStorage.setConnectedEmail(context, profile.emailAddress)

            val currentState = syncDao.getSyncStateRaw()
            val newState = GmailSyncStateEntity(
                id = 1,
                accountEmail = profile.emailAddress,
                lastHistoryId = profile.historyId, // Baseline historyId: never scan past emails!
                lastSyncTimestamp = System.currentTimeMillis(),
                lastSyncStatus = GmailSyncStatus.SUCCESS.name,
                lastSyncError = null,
                isAutoSyncEnabled = currentState?.isAutoSyncEnabled ?: true,
                defaultPdfPasswordEncrypted = currentState?.defaultPdfPasswordEncrypted
            )
            syncDao.updateSyncState(newState)
            profile.emailAddress
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Performs incremental synchronization using Gmail History API:
     * Detects newly added messages since lastHistoryId, inspects bank metadata,
     * validates PDF attachments, downloads directly to private storage,
     * and passes to the bank statement parsing engine.
     */
    suspend fun syncIncremental(
        context: Context,
        importDao: GmailImportDao,
        syncDao: GmailSyncDao,
        statementRepository: StatementRepository
    ): SyncOutcome = withContext(Dispatchers.IO) {
        val accessToken = oauthManager.getFreshAccessToken(context)
            ?: return@withContext SyncOutcome.Error("Not connected to Google Account. Please authenticate.")

        var syncState = syncDao.getSyncStateRaw()
        var baselineHistoryId = syncState?.lastHistoryId

        // If no baseline historyId exists, initialize baseline now without importing historical emails
        if (baselineHistoryId.isNullOrBlank()) {
            val email = connectAndInitializeBaseline(context, importDao, syncDao)
            if (email == null) {
                return@withContext SyncOutcome.Error("Failed to fetch initial Gmail profile baseline.")
            }
            return@withContext SyncOutcome.Success(newStatementsCount = 0, messagesProcessed = 0)
        }

        syncDao.updateSyncProgress(
            historyId = baselineHistoryId,
            timestamp = System.currentTimeMillis(),
            status = GmailSyncStatus.SYNCING.name,
            error = null
        )

        var newStatementsImported = 0
        var totalMessagesInspected = 0
        var latestHistoryId = baselineHistoryId

        try {
            var pageToken: String? = null
            val newlyAddedMessageIds = linkedSetOf<String>()

            // 1. Fetch incremental history page by page
            do {
                val historyResponse = try {
                    apiClient.listHistory(
                        accessToken = accessToken,
                        startHistoryId = baselineHistoryId,
                        maxResults = 100,
                        pageToken = pageToken
                    )
                } catch (_: GmailHistoryNotFoundException) {
                    // History ID expired on Gmail servers: re-baseline without importing past emails
                    val profile = apiClient.getProfile(accessToken)
                    syncDao.updateSyncProgress(
                        historyId = profile.historyId,
                        timestamp = System.currentTimeMillis(),
                        status = GmailSyncStatus.SUCCESS.name,
                        error = "History ID was reset by Gmail. Re-baselined to current."
                    )
                    return@withContext SyncOutcome.Success(newStatementsCount = 0, messagesProcessed = 0)
                }

                latestHistoryId = historyResponse.historyId

                for (record in historyResponse.history) {
                    for (added in record.messagesAdded) {
                        newlyAddedMessageIds.add(added.message.id)
                    }
                }

                pageToken = historyResponse.nextPageToken
            } while (!pageToken.isNullOrBlank())

            // 2. Process newly added messages
            for (messageId in newlyAddedMessageIds) {
                // Duplicate prevention: skip if messageId was already recorded
                if (importDao.hasMessage(messageId)) {
                    continue
                }

                totalMessagesInspected++

                val messageDetail = try {
                    apiClient.getMessage(accessToken, messageId)
                } catch (_: Exception) {
                    continue
                }

                val sender = messageDetail.sender
                val subject = messageDetail.subject

                // 3. Bank filtering: Only process ICICI, Axis, HDFC, Bank of Baroda, or Federal Bank
                val bank = BankFilterEngine.matchBank(sender, subject)
                if (bank == null) {
                    // Non-bank email: skip completely
                    continue
                }

                // 4. Attachment validation: accept only PDF attachments
                val pdfAttachments = AttachmentValidator.filterPdfAttachments(messageDetail.attachments)
                if (pdfAttachments.isEmpty()) {
                    // No PDF attachment in bank email
                    continue
                }

                // 5. Download matching PDFs to private storage & pass to parsing engine
                for (attRef in pdfAttachments) {
                    val sanitizedFilename = attRef.filename.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    val tempPdfFile = File(context.cacheDir, "gmail_stmt_${messageId}_${sanitizedFilename}")

                    try {
                        val pdfBytes = apiClient.getAttachment(accessToken, messageId, attRef.attachmentId)
                        FileOutputStream(tempPdfFile).use { it.write(pdfBytes) }

                        // Decrypt/Parse with statement password (if configured)
                        val storedPassword = GmailTokenStorage.getDefaultPdfPassword(context)

                        try {
                            val parseResult = FileInputStream(tempPdfFile).use { stream ->
                                parserRegistry.parse(stream, password = storedPassword)
                            }

                            // Successfully parsed! Save statement idempotently
                            val statementId = statementRepository.importStatement(parseResult)
                            newStatementsImported++

                            importDao.insertOrUpdate(
                                GmailImportHistoryEntity(
                                    messageId = messageId,
                                    sender = sender,
                                    subject = subject,
                                    receivedEpochMillis = messageDetail.internalDate,
                                    bankName = bank.displayName,
                                    fileName = attRef.filename,
                                    importStatus = GmailImportStatus.SUCCESS.name,
                                    statementId = statementId
                                )
                            )
                        } catch (_: StatementSecurityException.PasswordRequiredException) {
                            importDao.insertOrUpdate(
                                GmailImportHistoryEntity(
                                    messageId = messageId,
                                    sender = sender,
                                    subject = subject,
                                    receivedEpochMillis = messageDetail.internalDate,
                                    bankName = bank.displayName,
                                    fileName = attRef.filename,
                                    importStatus = GmailImportStatus.PASSWORD_REQUIRED.name,
                                    errorMessage = "Statement is password protected."
                                )
                            )
                        } catch (_: StatementSecurityException.InvalidPasswordException) {
                            importDao.insertOrUpdate(
                                GmailImportHistoryEntity(
                                    messageId = messageId,
                                    sender = sender,
                                    subject = subject,
                                    receivedEpochMillis = messageDetail.internalDate,
                                    bankName = bank.displayName,
                                    fileName = attRef.filename,
                                    importStatus = GmailImportStatus.PASSWORD_REQUIRED.name,
                                    errorMessage = "Provided statement password was incorrect."
                                )
                            )
                        } catch (e: IllegalArgumentException) {
                            // No matching parser found in registry (e.g. Bank of Baroda or Federal Bank)
                            importDao.insertOrUpdate(
                                GmailImportHistoryEntity(
                                    messageId = messageId,
                                    sender = sender,
                                    subject = subject,
                                    receivedEpochMillis = messageDetail.internalDate,
                                    bankName = bank.displayName,
                                    fileName = attRef.filename,
                                    importStatus = GmailImportStatus.NO_PARSER_MATCH.name,
                                    errorMessage = e.message ?: "No matching parser found for ${bank.displayName}."
                                )
                            )
                        } catch (e: Exception) {
                            importDao.insertOrUpdate(
                                GmailImportHistoryEntity(
                                    messageId = messageId,
                                    sender = sender,
                                    subject = subject,
                                    receivedEpochMillis = messageDetail.internalDate,
                                    bankName = bank.displayName,
                                    fileName = attRef.filename,
                                    importStatus = GmailImportStatus.PARSE_FAILED.name,
                                    errorMessage = e.message ?: "Failed to parse PDF statement."
                                )
                            )
                        }
                    } finally {
                        // Keep on-device storage strictly clean & secure
                        if (tempPdfFile.exists()) {
                            tempPdfFile.delete()
                        }
                    }
                }
            }

            // Advance baseline historyId to latest
            syncDao.updateSyncProgress(
                historyId = latestHistoryId,
                timestamp = System.currentTimeMillis(),
                status = GmailSyncStatus.SUCCESS.name,
                error = null
            )

            SyncOutcome.Success(
                newStatementsCount = newStatementsImported,
                messagesProcessed = totalMessagesInspected
            )
        } catch (e: Exception) {
            val errMsg = e.message ?: "Incremental sync failed"
            syncDao.updateSyncProgress(
                historyId = latestHistoryId,
                timestamp = System.currentTimeMillis(),
                status = GmailSyncStatus.ERROR.name,
                error = errMsg
            )
            SyncOutcome.Error(errMsg)
        }
    }
}
