package com.moneymanager.app.domain.gmail.api

data class GmailProfile(
    val emailAddress: String,
    val messagesTotal: Long,
    val threadsTotal: Long,
    val historyId: String
)

data class GmailHistoryResponse(
    val history: List<GmailHistoryRecord>,
    val historyId: String,
    val nextPageToken: String? = null
)

data class GmailHistoryRecord(
    val id: String,
    val messagesAdded: List<GmailMessageAdded> = emptyList()
)

data class GmailMessageAdded(
    val message: GmailMessageRef
)

data class GmailMessageRef(
    val id: String,
    val threadId: String? = null
)

data class GmailHeader(
    val name: String,
    val value: String
)

data class GmailAttachmentRef(
    val attachmentId: String,
    val filename: String,
    val mimeType: String,
    val size: Long
)

data class GmailMessageDetail(
    val id: String,
    val threadId: String?,
    val internalDate: Long,
    val headers: List<GmailHeader>,
    val attachments: List<GmailAttachmentRef>
) {
    fun getHeader(name: String): String? {
        return headers.firstOrNull { it.name.equals(name, ignoreCase = true) }?.value
    }

    val sender: String get() = getHeader("From").orEmpty()
    val subject: String get() = getHeader("Subject").orEmpty()
    val dateString: String get() = getHeader("Date").orEmpty()
}
