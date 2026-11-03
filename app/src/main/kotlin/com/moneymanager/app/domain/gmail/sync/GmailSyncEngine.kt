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

sealed class PasswordUnlockOutcome {
    data class Success(val statementId: String?, val message: String) : PasswordUnlockOutcome()
    data class InvalidPassword(val message: String) : PasswordUnlockOutcome()
    data class Error(val message: String) : PasswordUnlockOutcome()
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
            val candidateMessageIds = linkedSetOf<String>()

            val syncRules = com.moneymanager.app.domain.gmail.filter.GmailSyncRulesStorage.getRules(context)

            // 1. Query messages matching syncSince criteria (e.g. from selected month onward)
            val syncSince = GmailTokenStorage.getSyncSinceYearMonth(context)
            val afterDate = "${syncSince.year}/${"%02d".format(syncSince.monthValue)}/01"
            val query = "${syncRules.baseSearchQuery.trim()} after:$afterDate"

            try {
                var searchPageToken: String? = null
                var pagesFetched = 0
                do {
                    val searchResp = apiClient.listMessages(
                        accessToken = accessToken,
                        query = query,
                        maxResults = 100,
                        pageToken = searchPageToken
                    )
                    for (id in searchResp.messageIds) {
                        if (!importDao.hasMessage(id)) {
                            candidateMessageIds.add(id)
                        }
                    }
                    searchPageToken = searchResp.nextPageToken
                    pagesFetched++
                } while (!searchPageToken.isNullOrBlank() && pagesFetched < 5)
            } catch (_: Exception) {
                // Ignore search exception and proceed to history
            }

            // 2. Fetch incremental history page by page
            var pageToken: String? = null
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
                        if (!importDao.hasMessage(added.message.id)) {
                            candidateMessageIds.add(added.message.id)
                        }
                    }
                }
                pageToken = historyResponse.nextPageToken
            } while (!pageToken.isNullOrBlank())

            // 3. Process candidate messages
            for (messageId in candidateMessageIds) {
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

                // 2. Ignored subject rule check: skip if matches user ignore list
                if (BankFilterEngine.shouldIgnoreEmail(sender, subject, syncRules)) {
                    continue
                }

                // 3. Bank filtering: Only process enabled/supported banks or configured custom banks
                val builtInBank = BankFilterEngine.matchBank(sender, subject, syncRules)
                val customBankName = if (builtInBank == null) BankFilterEngine.matchCustomBank(sender, subject, syncRules) else null

                if (builtInBank == null && customBankName == null) {
                    // Non-bank or disabled bank: skip completely
                    continue
                }
                val bankDisplayName = builtInBank?.displayName ?: customBankName ?: "Bank"

                // 4. Attachment validation: accept only PDF attachments
                val pdfAttachments = AttachmentValidator.filterPdfAttachments(messageDetail.attachments)
                if (pdfAttachments.isEmpty()) {
                    // No PDF attachment in bank email
                    continue
                }

                // 5. Download matching PDFs to private storage & pass to parsing engine
                for (attRef in pdfAttachments) {
                    if (!BankFilterEngine.matchesCardCriteria(subject, attRef.filename, syncRules)) {
                        continue
                    }

                    val sanitizedFilename = attRef.filename.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    val tempPdfFile = File(context.cacheDir, "gmail_stmt_${messageId}_${sanitizedFilename}")

                    try {
                        val pdfBytes = apiClient.getAttachment(accessToken, messageId, attRef.attachmentId)
                        FileOutputStream(tempPdfFile).use { it.write(pdfBytes) }

                        // Decrypt/Parse with candidate statement passwords
                        val candidatePasswords = GmailTokenStorage.getPdfPasswords(context)

                        try {
                            val multiResult = FileInputStream(tempPdfFile).use { stream ->
                                parserRegistry.parseWithPasswords(stream, candidatePasswords)
                            }
                            val parseResult = multiResult.result
                            if (multiResult.workingPassword != null) {
                                GmailTokenStorage.addPdfPassword(context, multiResult.workingPassword)
                            }

                            // Successfully parsed! Save statement idempotently
                            val statementId = statementRepository.importStatement(parseResult)
                            newStatementsImported++

                            // Clear any pending cached PDF since it is now successfully imported
                            getPendingPdfFile(context, messageId).delete()

                            importDao.insertOrUpdate(
                                GmailImportHistoryEntity(
                                    messageId = messageId,
                                    sender = sender,
                                    subject = subject,
                                    receivedEpochMillis = messageDetail.internalDate,
                                    bankName = bankDisplayName,
                                    fileName = attRef.filename,
                                    importStatus = GmailImportStatus.SUCCESS.name,
                                    statementId = statementId
                                )
                            )
                        } catch (_: StatementSecurityException.PasswordRequiredException) {
                            cachePendingPdf(context, messageId, tempPdfFile)
                            importDao.insertOrUpdate(
                                GmailImportHistoryEntity(
                                    messageId = messageId,
                                    sender = sender,
                                    subject = subject,
                                    receivedEpochMillis = messageDetail.internalDate,
                                    bankName = bankDisplayName,
                                    fileName = attRef.filename,
                                    importStatus = GmailImportStatus.PASSWORD_REQUIRED.name,
                                    errorMessage = "Statement is password protected."
                                )
                            )
                        } catch (_: StatementSecurityException.InvalidPasswordException) {
                            cachePendingPdf(context, messageId, tempPdfFile)
                            importDao.insertOrUpdate(
                                GmailImportHistoryEntity(
                                    messageId = messageId,
                                    sender = sender,
                                    subject = subject,
                                    receivedEpochMillis = messageDetail.internalDate,
                                    bankName = bankDisplayName,
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
                                    bankName = bankDisplayName,
                                    fileName = attRef.filename,
                                    importStatus = GmailImportStatus.NO_PARSER_MATCH.name,
                                    errorMessage = e.message ?: "No matching parser found for $bankDisplayName."
                                )
                            )
                        } catch (e: Exception) {
                            importDao.insertOrUpdate(
                                GmailImportHistoryEntity(
                                    messageId = messageId,
                                    sender = sender,
                                    subject = subject,
                                    receivedEpochMillis = messageDetail.internalDate,
                                    bankName = bankDisplayName,
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

    /**
     * Unlocks and parses a password-protected statement from Import History.
     * If the password successfully decrypts the document:
     * - Adds the password to the persistent statement passwords list.
     * - Parses & imports the statement into the ledger/repository.
     * - Updates import history entry to SUCCESS (or NO_PARSER_MATCH if no parser supported).
     * - Retries the same password on other locked items in import history.
     */
    suspend fun unlockHistoryItemWithPassword(
        context: Context,
        item: GmailImportHistoryEntity,
        password: String,
        statementRepository: StatementRepository,
        importDao: GmailImportDao
    ): PasswordUnlockOutcome = withContext(Dispatchers.IO) {
        val cleanPassword = password.trim()
        if (cleanPassword.isBlank()) {
            return@withContext PasswordUnlockOutcome.InvalidPassword("Password cannot be empty.")
        }

        val pdfFile = getOrFetchPdfForMessage(context, item.messageId)
            ?: return@withContext PasswordUnlockOutcome.Error("Statement PDF could not be retrieved. Please sync again.")

        try {
            // 1. Verify password against the PDF
            val isValid = FileInputStream(pdfFile).use { stream ->
                com.moneymanager.app.domain.statement.pdf.SpatialPdfExtractor.verifyPassword(stream, cleanPassword)
            }

            if (!isValid) {
                return@withContext PasswordUnlockOutcome.InvalidPassword("Incorrect password. Please try again.")
            }

            // 2. Add working password to main statement passwords list
            GmailTokenStorage.addPdfPassword(context, cleanPassword)

            // 3. Attempt parsing with working password
            try {
                val parseResult = FileInputStream(pdfFile).use { stream ->
                    parserRegistry.parse(stream, cleanPassword)
                }

                val statementId = statementRepository.importStatement(parseResult)
                pdfFile.delete()

                importDao.insertOrUpdate(
                    item.copy(
                        importStatus = GmailImportStatus.SUCCESS.name,
                        statementId = statementId,
                        errorMessage = null
                    )
                )

                // 4. Try unlocking other pending password-required items with this newly saved password
                retryOtherLockedItems(context, cleanPassword, statementRepository, importDao, excludeMessageId = item.messageId)

                PasswordUnlockOutcome.Success(
                    statementId = statementId,
                    message = "Statement unlocked and imported successfully!"
                )
            } catch (e: IllegalArgumentException) {
                // Password was correct, but document is from an unsupported bank/card format
                pdfFile.delete()
                importDao.insertOrUpdate(
                    item.copy(
                        importStatus = GmailImportStatus.NO_PARSER_MATCH.name,
                        errorMessage = e.message ?: "No matching parser found for this statement format."
                    )
                )
                PasswordUnlockOutcome.Success(
                    statementId = null,
                    message = "Password verified and saved, but parser is not yet supported for this format."
                )
            } catch (e: Exception) {
                PasswordUnlockOutcome.Error(e.message ?: "Failed to parse decrypted PDF.")
            }
        } catch (e: Exception) {
            PasswordUnlockOutcome.Error(e.message ?: "Failed to verify password.")
        }
    }

    private suspend fun retryOtherLockedItems(
        context: Context,
        workingPassword: String,
        statementRepository: StatementRepository,
        importDao: GmailImportDao,
        excludeMessageId: String
    ) {
        val history = importDao.getAllHistoryRaw()
        val otherLocked = history.filter {
            it.messageId != excludeMessageId && it.importStatus == GmailImportStatus.PASSWORD_REQUIRED.name
        }

        for (locked in otherLocked) {
            val file = getPendingPdfFile(context, locked.messageId)
            if (!file.exists()) continue
            try {
                val canUnlock = FileInputStream(file).use {
                    com.moneymanager.app.domain.statement.pdf.SpatialPdfExtractor.verifyPassword(it, workingPassword)
                }
                if (canUnlock) {
                    try {
                        val result = FileInputStream(file).use {
                            parserRegistry.parse(it, workingPassword)
                        }
                        val stmtId = statementRepository.importStatement(result)
                        file.delete()
                        importDao.insertOrUpdate(
                            locked.copy(
                                importStatus = GmailImportStatus.SUCCESS.name,
                                statementId = stmtId,
                                errorMessage = null
                            )
                        )
                    } catch (e: IllegalArgumentException) {
                        file.delete()
                        importDao.insertOrUpdate(
                            locked.copy(
                                importStatus = GmailImportStatus.NO_PARSER_MATCH.name,
                                errorMessage = e.message
                            )
                        )
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }

    private suspend fun getOrFetchPdfForMessage(context: Context, messageId: String): File? {
        val cached = getPendingPdfFile(context, messageId)
        if (cached.exists() && cached.length() > 0) {
            return cached
        }

        // On-demand fetch from Gmail if cached file is not present
        val accessToken = oauthManager.getFreshAccessToken(context) ?: return null
        return try {
            val messageDetail = apiClient.getMessage(accessToken, messageId)
            val pdfAtt = AttachmentValidator.filterPdfAttachments(messageDetail.attachments).firstOrNull() ?: return null
            val bytes = apiClient.getAttachment(accessToken, messageId, pdfAtt.attachmentId)
            cached.parentFile?.mkdirs()
            FileOutputStream(cached).use { it.write(bytes) }
            cached
        } catch (_: Exception) {
            null
        }
    }

    private fun getPendingPdfDir(context: Context): File {
        val dir = File(context.filesDir, "pending_gmail_pdfs")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getPendingPdfFile(context: Context, messageId: String): File {
        return File(getPendingPdfDir(context), "${messageId}.pdf")
    }

    private fun cachePendingPdf(context: Context, messageId: String, tempFile: File) {
        try {
            val dest = getPendingPdfFile(context, messageId)
            tempFile.copyTo(dest, overwrite = true)
        } catch (_: Exception) {}
    }
}
