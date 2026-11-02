package com.moneymanager.app.domain.gmail

import com.moneymanager.app.domain.gmail.api.GmailAttachmentRef
import com.moneymanager.app.domain.gmail.filter.AttachmentValidator
import org.junit.Assert.*
import org.junit.Test

class AttachmentValidatorTest {

    @Test
    fun testValidPdfAttachmentsAccepted() {
        val pdf1 = GmailAttachmentRef("att_1", "statement_oct2026.pdf", "application/pdf", 102400)
        val pdf2 = GmailAttachmentRef("att_2", "CARD_STATEMENT.PDF", "application/x-pdf", 204800)
        val pdf3 = GmailAttachmentRef("att_3", "AxisBank_8158.Pdf", "application/octet-stream", 51200)

        assertTrue(AttachmentValidator.isValidPdfAttachment(pdf1))
        assertTrue(AttachmentValidator.isValidPdfAttachment(pdf2))
        assertTrue(AttachmentValidator.isValidPdfAttachment(pdf3))

        val filtered = AttachmentValidator.filterPdfAttachments(listOf(pdf1, pdf2, pdf3))
        assertEquals(3, filtered.size)
    }

    @Test
    fun testNonPdfAttachmentsRejected() {
        val image = GmailAttachmentRef("att_img", "header_logo.png", "image/png", 5000)
        val zip = GmailAttachmentRef("att_zip", "statement.zip", "application/zip", 10000)
        val doc = GmailAttachmentRef("att_doc", "summary.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 20000)
        val html = GmailAttachmentRef("att_html", "receipt.html", "text/html", 3000)

        assertFalse(AttachmentValidator.isValidPdfAttachment(image))
        assertFalse(AttachmentValidator.isValidPdfAttachment(zip))
        assertFalse(AttachmentValidator.isValidPdfAttachment(doc))
        assertFalse(AttachmentValidator.isValidPdfAttachment(html))

        val filtered = AttachmentValidator.filterPdfAttachments(listOf(image, zip, doc, html))
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun testMixedAttachmentsFilter() {
        val validPdf = GmailAttachmentRef("att_pdf", "bank_statement.pdf", "application/pdf", 102400)
        val logo = GmailAttachmentRef("att_logo", "logo.jpg", "image/jpeg", 4000)

        val filtered = AttachmentValidator.filterPdfAttachments(listOf(logo, validPdf))
        assertEquals(1, filtered.size)
        assertEquals("att_pdf", filtered[0].attachmentId)
    }
}
