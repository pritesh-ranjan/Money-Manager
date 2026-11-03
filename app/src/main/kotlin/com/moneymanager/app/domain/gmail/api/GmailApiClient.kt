package com.moneymanager.app.domain.gmail.api

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class GmailHistoryNotFoundException(message: String) : IOException(message)

open class GmailApiClient(
    private val client: OkHttpClient = defaultOkHttpClient()
) {

    companion object {
        const val BASE_URL = "https://gmail.googleapis.com/gmail/v1/users/me"

        fun defaultOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }

    /**
     * Retrieves user Gmail profile (email and current baseline historyId)
     */
    open suspend fun getProfile(accessToken: String): GmailProfile = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/profile")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Gmail getProfile failed: HTTP ${response.code} $body")
            }
            val json = JSONObject(body)
            GmailProfile(
                emailAddress = json.getString("emailAddress"),
                messagesTotal = json.optLong("messagesTotal", 0L),
                threadsTotal = json.optLong("threadsTotal", 0L),
                historyId = json.getString("historyId")
            )
        }
    }

    /**
     * Lists message IDs matching a search query (e.g. q="has:attachment filename:pdf after:2024/11/01")
     */
    open suspend fun listMessages(
        accessToken: String,
        query: String,
        maxResults: Int = 100,
        pageToken: String? = null
    ): GmailMessagesListResponse = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val urlBuilder = StringBuilder("$BASE_URL/messages")
            .append("?q=").append(encodedQuery)
            .append("&maxResults=").append(maxResults)

        if (!pageToken.isNullOrBlank()) {
            urlBuilder.append("&pageToken=").append(pageToken)
        }

        val request = Request.Builder()
            .url(urlBuilder.toString())
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Gmail listMessages failed: HTTP ${response.code} $body")
            }

            val json = JSONObject(body)
            val nextPageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
            val messagesArr = json.optJSONArray("messages") ?: JSONArray()
            val messageIds = mutableListOf<String>()

            for (i in 0 until messagesArr.length()) {
                val item = messagesArr.getJSONObject(i)
                val id = item.optString("id")
                if (id.isNotBlank()) {
                    messageIds.add(id)
                }
            }

            GmailMessagesListResponse(
                messageIds = messageIds,
                nextPageToken = nextPageToken
            )
        }
    }

    /**
     * Queries incremental history from startHistoryId.
     * Throws GmailHistoryNotFoundException if historyId is too old (HTTP 404).
     */
    open suspend fun listHistory(
        accessToken: String,
        startHistoryId: String,
        maxResults: Int = 100,
        pageToken: String? = null
    ): GmailHistoryResponse = withContext(Dispatchers.IO) {
        val urlBuilder = StringBuilder("$BASE_URL/history")
            .append("?startHistoryId=").append(startHistoryId)
            .append("&historyTypes=messageAdded")
            .append("&maxResults=").append(maxResults)

        if (!pageToken.isNullOrBlank()) {
            urlBuilder.append("&pageToken=").append(pageToken)
        }

        val request = Request.Builder()
            .url(urlBuilder.toString())
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.code == 404) {
                throw GmailHistoryNotFoundException("History ID $startHistoryId is too old or expired.")
            }
            if (!response.isSuccessful) {
                throw IOException("Gmail listHistory failed: HTTP ${response.code} $body")
            }

            val json = JSONObject(body)
            val historyId = json.optString("historyId", startHistoryId)
            val nextPageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }

            val historyRecords = mutableListOf<GmailHistoryRecord>()
            val historyArray = json.optJSONArray("history") ?: JSONArray()

            for (i in 0 until historyArray.length()) {
                val item = historyArray.getJSONObject(i)
                val id = item.optString("id", "")
                val messagesAddedList = mutableListOf<GmailMessageAdded>()
                val addedArray = item.optJSONArray("messagesAdded") ?: JSONArray()

                for (j in 0 until addedArray.length()) {
                    val addObj = addedArray.getJSONObject(j)
                    val msgObj = addObj.optJSONObject("message")
                    if (msgObj != null) {
                        val msgId = msgObj.getString("id")
                        val threadId = msgObj.optString("threadId")
                        messagesAddedList.add(GmailMessageAdded(GmailMessageRef(msgId, threadId)))
                    }
                }

                historyRecords.add(GmailHistoryRecord(id, messagesAddedList))
            }

            GmailHistoryResponse(
                history = historyRecords,
                historyId = historyId,
                nextPageToken = nextPageToken
            )
        }
    }

    /**
     * Fetches message details including headers and recursive MIME attachments
     */
    open suspend fun getMessage(
        accessToken: String,
        messageId: String
    ): GmailMessageDetail = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/messages/$messageId?format=full"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Gmail getMessage failed: HTTP ${response.code} $body")
            }

            val json = JSONObject(body)
            val id = json.getString("id")
            val threadId = json.optString("threadId")
            val internalDate = json.optLong("internalDate", System.currentTimeMillis())

            val headers = mutableListOf<GmailHeader>()
            val attachments = mutableListOf<GmailAttachmentRef>()

            val payload = json.optJSONObject("payload")
            if (payload != null) {
                // Extract headers
                val headersArr = payload.optJSONArray("headers") ?: JSONArray()
                for (i in 0 until headersArr.length()) {
                    val hObj = headersArr.getJSONObject(i)
                    headers.add(GmailHeader(hObj.optString("name"), hObj.optString("value")))
                }

                // Recursively inspect payload parts for attachments
                extractAttachments(payload, attachments)
            }

            GmailMessageDetail(
                id = id,
                threadId = threadId,
                internalDate = internalDate,
                headers = headers,
                attachments = attachments
            )
        }
    }

    private fun extractAttachments(part: JSONObject, result: MutableList<GmailAttachmentRef>) {
        val filename = part.optString("filename", "")
        val mimeType = part.optString("mimeType", "")
        val body = part.optJSONObject("body")
        val attachmentId = body?.optString("attachmentId")
        val size = body?.optLong("size", 0L) ?: 0L

        if (!attachmentId.isNullOrBlank() && filename.isNotBlank()) {
            result.add(
                GmailAttachmentRef(
                    attachmentId = attachmentId,
                    filename = filename,
                    mimeType = mimeType,
                    size = size
                )
            )
        }

        val subParts = part.optJSONArray("parts") ?: JSONArray()
        for (i in 0 until subParts.length()) {
            extractAttachments(subParts.getJSONObject(i), result)
        }
    }

    /**
     * Downloads attachment bytes and decodes from URL-safe Base64
     */
    open suspend fun getAttachment(
        accessToken: String,
        messageId: String,
        attachmentId: String
    ): ByteArray = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/messages/$messageId/attachments/$attachmentId"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Gmail getAttachment failed: HTTP ${response.code} $body")
            }

            val json = JSONObject(body)
            val base64Data = json.getString("data")
            // Google uses URL-safe Base64 without padding or with padding
            java.util.Base64.getUrlDecoder().decode(base64Data)
        }
    }
}
