package com.moneymanager.app.domain.statement.pdf

import com.moneymanager.app.domain.statement.core.StatementInspectionContext
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class SpatialPdfExtractor : PDFTextStripper() {

    private val pages = mutableListOf<SpatialPage>()
    private val currentElements = mutableListOf<SpatialTextElement>()
    private var currentPageWidth: Float = 0f
    private var currentPageHeight: Float = 0f

    init {
        sortByPosition = true
    }

    override fun startPage(page: PDPage) {
        currentElements.clear()
        currentPageWidth = page.mediaBox.width
        currentPageHeight = page.mediaBox.height
        super.startPage(page)
    }

    override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
        if (text.isBlank() || textPositions.isEmpty()) return

        val minX = textPositions.minOf { it.xDirAdj }
        val maxX = textPositions.maxOf { it.xDirAdj + it.widthDirAdj }
        val minY = textPositions.minOf { it.yDirAdj - it.heightDir }
        val maxY = textPositions.maxOf { it.yDirAdj }
        val avgFontSize = textPositions.map { it.fontSizeInPt }.average().toFloat()

        currentElements.add(
            SpatialTextElement(
                text = text.trim(),
                bounds = BoundingBox.fromPoints(minX, minY, maxX, maxY),
                fontSize = avgFontSize
            )
        )
    }

    override fun endPage(page: PDPage) {
        val lines = clusterElementsIntoLines(currentElements)
        pages.add(
            SpatialPage(
                pageNumber = currentPageNo,
                width = currentPageWidth,
                height = currentPageHeight,
                lines = lines
            )
        )
        super.endPage(page)
    }

    private fun clusterElementsIntoLines(
        elements: List<SpatialTextElement>,
        yTolerance: Float = 3.5f
    ): List<SpatialLine> {
        if (elements.isEmpty()) return emptyList()

        // Sort elements primarily by top Y, then X
        val sorted = elements.sortedWith(
            compareBy<SpatialTextElement> { it.bounds.y }
                .thenBy { it.bounds.x }
        )

        val lineBuckets = mutableListOf<MutableList<SpatialTextElement>>()

        for (el in sorted) {
            // Find existing line bucket where vertical difference is within tolerance
            val matchingBucket = lineBuckets.firstOrNull { bucket ->
                val avgY = bucket.map { it.bounds.y }.average().toFloat()
                abs(avgY - el.bounds.y) <= yTolerance
            }

            if (matchingBucket != null) {
                matchingBucket.add(el)
            } else {
                lineBuckets.add(mutableListOf(el))
            }
        }

        return lineBuckets.map { bucket ->
            // Sort elements left-to-right within the line
            bucket.sortBy { it.bounds.x }
            val minX = bucket.minOf { it.bounds.x }
            val maxX = bucket.maxOf { it.bounds.right }
            val minY = bucket.minOf { it.bounds.y }
            val maxY = bucket.maxOf { it.bounds.bottom }
            SpatialLine(
                elements = bucket,
                bounds = BoundingBox.fromPoints(minX, minY, maxX, maxY)
            )
        }.sortedBy { it.bounds.y }
    }

    companion object {
        /**
         * Checks if the PDF input stream is encrypted or password-protected.
         */
        fun isPasswordProtected(inputStream: InputStream): Boolean {
            return try {
                val doc = PDDocument.load(inputStream)
                val encrypted = doc.isEncrypted
                doc.close()
                encrypted
            } catch (e: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
                true
            } catch (e: Exception) {
                e.message?.contains("password", ignoreCase = true) == true
            }
        }

        /**
         * Verifies if the provided password can unlock the PDF.
         */
        fun verifyPassword(inputStream: InputStream, password: String): Boolean {
            return try {
                val doc = PDDocument.load(inputStream, password)
                doc.close()
                true
            } catch (e: Exception) {
                false
            }
        }

        /**
         * Extracts all pages with complete spatial layout and bounding boxes.
         * Optionally takes a password if the PDF is encrypted.
         */
        fun extractPages(inputStream: InputStream, password: String? = null): List<SpatialPage> {
            val document = loadDocument(inputStream, password)
            document.use { doc ->
                val extractor = SpatialPdfExtractor()
                extractor.pages.clear()
                extractor.getText(doc)
                return extractor.pages.toList()
            }
        }

        /**
         * Lightweight inspection for auto-detecting bank format from page 1.
         * Optionally takes a password if the PDF is encrypted.
         */
        fun inspect(inputStream: InputStream, password: String? = null): StatementInspectionContext {
            val document = loadDocument(inputStream, password)
            document.use { doc ->
                val totalPages = doc.numberOfPages
                val extractor = SpatialPdfExtractor()
                extractor.startPage = 1
                extractor.endPage = 1
                extractor.getText(doc)
                val firstPage = extractor.pages.firstOrNull()
                val text = firstPage?.fullText.orEmpty()
                val headerLines = firstPage?.lines?.take(15)?.map { it.text } ?: emptyList()
                return StatementInspectionContext(
                    firstPageText = text,
                    totalPages = totalPages,
                    headerLines = headerLines
                )
            }
        }

        private fun loadDocument(inputStream: InputStream, password: String?): PDDocument {
            return try {
                if (password.isNullOrEmpty()) {
                    PDDocument.load(inputStream)
                } else {
                    PDDocument.load(inputStream, password)
                }
            } catch (e: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
                if (password.isNullOrEmpty()) {
                    throw com.moneymanager.app.domain.statement.core.StatementSecurityException.PasswordRequiredException()
                } else {
                    throw com.moneymanager.app.domain.statement.core.StatementSecurityException.InvalidPasswordException()
                }
            } catch (e: java.io.IOException) {
                if (e.message?.contains("password", ignoreCase = true) == true ||
                    e.message?.contains("encrypted", ignoreCase = true) == true
                ) {
                    if (password.isNullOrEmpty()) {
                        throw com.moneymanager.app.domain.statement.core.StatementSecurityException.PasswordRequiredException()
                    } else {
                        throw com.moneymanager.app.domain.statement.core.StatementSecurityException.InvalidPasswordException()
                    }
                }
                throw e
            }
        }
    }
}
