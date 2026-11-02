package com.moneymanager.app.domain.statement

import com.moneymanager.app.domain.statement.core.StatementParserRegistry
import com.moneymanager.app.domain.statement.core.StatementSecurityException
import com.moneymanager.app.domain.statement.parsers.icici.IciciAmazonPayCreditCardParser
import com.moneymanager.app.domain.statement.pdf.SpatialPdfExtractor
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileInputStream

class StatementPasswordTest {

    private lateinit var parser: IciciAmazonPayCreditCardParser
    private lateinit var registry: StatementParserRegistry
    private val encryptedPdfFile = File("4315XXXXXXXX3004_2191082_Retail_Amazon_NORM.pdf")

    @Before
    fun setUp() {
        parser = IciciAmazonPayCreditCardParser()
        registry = StatementParserRegistry()
        registry.register(parser)
    }

    @Test
    fun testIsPasswordProtectedDetection() {
        if (!encryptedPdfFile.exists()) return

        FileInputStream(encryptedPdfFile).use { stream ->
            val isProtected = SpatialPdfExtractor.isPasswordProtected(stream)
            assertTrue("Expected 4315... PDF to be detected as password-protected", isProtected)
        }

        // Test with decrypted golden PDF
        val unencryptedStream = javaClass.getResourceAsStream("/fixtures/icici_amazon_pay_statement_golden.pdf")
        assertNotNull(unencryptedStream)
        unencryptedStream!!.use { stream ->
            val isProtected = SpatialPdfExtractor.isPasswordProtected(stream)
            assertFalse("Expected golden test fixture to be unencrypted", isProtected)
        }
    }

    @Test
    fun testMissingPasswordThrowsPasswordRequiredException() {
        if (!encryptedPdfFile.exists()) return

        FileInputStream(encryptedPdfFile).use { stream ->
            assertThrows(StatementSecurityException.PasswordRequiredException::class.java) {
                parser.parse(stream, password = null)
            }
        }
    }

    @Test
    fun testIncorrectPasswordThrowsInvalidPasswordException() {
        if (!encryptedPdfFile.exists()) return

        FileInputStream(encryptedPdfFile).use { stream ->
            assertThrows(StatementSecurityException.InvalidPasswordException::class.java) {
                parser.parse(stream, password = "IncorrectPassword123")
            }
        }
    }

    @Test
    fun testVerifyPasswordHelper() {
        if (!encryptedPdfFile.exists()) return

        FileInputStream(encryptedPdfFile).use { stream ->
            val correct = SpatialPdfExtractor.verifyPassword(stream, "IncorrectPassword123")
            assertFalse("verifyPassword should return false for incorrect password", correct)
        }
    }

    private fun resolvePasswordFromEnvOrFile(): List<String> {
        val candidates = mutableListOf<String>()
        val envPassword = System.getenv("STATEMENT_PDF_PASSWORD")
        if (!envPassword.isNullOrBlank()) {
            candidates.add(envPassword.trim())
        }

        val propsFile = File("statement_credentials.properties")
        if (propsFile.exists()) {
            val props = java.util.Properties()
            propsFile.inputStream().use { props.load(it) }
            props.getProperty("STATEMENT_PDF_PASSWORD")?.let { candidates.add(it.trim()) }
            props.getProperty("STATEMENT_PDF_PASSWORD_ALT")?.let { candidates.add(it.trim()) }
        }

        val dotEnvFile = File(".env")
        if (dotEnvFile.exists()) {
            dotEnvFile.readLines().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("STATEMENT_PDF_PASSWORD=") || trimmed.startsWith("STATEMENT_PDF_PASSWORD_ALT=")) {
                    val value = trimmed.substringAfter("=").trim()
                    if (value.isNotBlank() && value !in candidates) {
                        candidates.add(value)
                    }
                }
            }
        }

        return candidates.distinct()
    }

    @Test
    fun testSuccessfulDecryptionAndParsingWithEnvPassword() {
        if (!encryptedPdfFile.exists()) return

        val passwords = resolvePasswordFromEnvOrFile()
        assertTrue("At least one password candidate must be configured in untracked file or env", passwords.isNotEmpty())

        var workingPassword: String? = null
        for (pwd in passwords) {
            val valid = FileInputStream(encryptedPdfFile).use { stream ->
                SpatialPdfExtractor.verifyPassword(stream, pwd)
            }
            if (valid) {
                workingPassword = pwd
                break
            }
        }

        assertNotNull("One of the password candidates must successfully unlock the PDF: $passwords", workingPassword)

        val result = FileInputStream(encryptedPdfFile).use { stream ->
            registry.parse(stream, workingPassword)
        }

        // Full validation of decrypted statement data
        assertEquals("ICICI_AMAZON_PAY_CC", result.metadata.bankId)
        assertEquals("MR PRITESH RANJAN", result.metadata.accountHolderName)
        assertEquals(27, result.transactions.size)
        assertEquals(20789.41, result.summary.totalAmountDue, 0.01)
        assertTrue("Reconciliation must succeed for encrypted PDF", result.reconciliation.isReconciled)
        assertTrue("Reconciliation discrepancies: ${result.reconciliation.discrepancies}", result.reconciliation.discrepancies.isEmpty())
    }
}
