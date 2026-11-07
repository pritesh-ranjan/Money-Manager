package com.moneymanager.app.domain.gmail.filter

import com.moneymanager.app.domain.gmail.api.GmailAttachmentRef

object AttachmentValidator {

    // Maximum accepted PDF attachment size (25 MB) to avoid excessive memory allocation
    const val MAX_PDF_ATTACHMENT_BYTES = 25L * 1024 * 1024

    private val VALID_PDF_MIME_TYPES = setOf(
        "application/pdf",
        "application/x-pdf",
        "application/acrobat",
        "applications/vnd.pdf",
        "text/pdf",
        "application/octet-stream" // frequently used by email gateways for PDFs
    )

    /**
     * Checks if a single attachment reference has a valid PDF filename and MIME type.
     */
    fun isValidPdfAttachment(attachment: GmailAttachmentRef): Boolean {
        if (attachment.size > MAX_PDF_ATTACHMENT_BYTES) return false
        val filename = attachment.filename.trim()
        val hasPdfExtension = filename.endsWith(".pdf", ignoreCase = true)
        if (!hasPdfExtension) return false

        val mime = attachment.mimeType.trim().lowercase()
        return mime.isBlank() || VALID_PDF_MIME_TYPES.contains(mime)
    }

    /**
     * Filters list of attachments to only include valid PDF attachments.
     */
    fun filterPdfAttachments(attachments: List<GmailAttachmentRef>): List<GmailAttachmentRef> {
        return attachments.filter { isValidPdfAttachment(it) }
    }
}
