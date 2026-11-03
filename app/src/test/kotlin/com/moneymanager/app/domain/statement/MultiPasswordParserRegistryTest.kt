package com.moneymanager.app.domain.statement

import com.moneymanager.app.domain.statement.core.StatementParserRegistry
import com.moneymanager.app.domain.statement.core.StatementSecurityException
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

class MultiPasswordParserRegistryTest {

    private lateinit var registry: StatementParserRegistry

    @Before
    fun setUp() {
        registry = StatementParserRegistry()
    }

    private fun loadAirtelPdfStream(): InputStream {
        val stream = javaClass.getResourceAsStream("/fixtures/axis_airtel_golden.pdf")
        assertNotNull("Axis Airtel golden PDF fixture must be present", stream)
        return stream!!
    }

    private fun createEncryptedPdfBytes(password: String): ByteArray {
        val rawBytes = loadAirtelPdfStream().use { it.readBytes() }
        val doc = PDDocument.load(rawBytes)
        val ap = AccessPermission()
        val spp = StandardProtectionPolicy(password, "owner_$password", ap)
        doc.protect(spp)
        val out = ByteArrayOutputStream()
        doc.save(out)
        doc.close()
        return out.toByteArray()
    }

    @Test
    fun testParseWithPasswordsSucceedsWhenValidPasswordInList() {
        val encryptedBytes = createEncryptedPdfBytes("prit1510")
        val candidates = listOf("wrong_pass_1", "wrong_pass_2", "prit1510", "another_wrong")

        ByteArrayInputStream(encryptedBytes).use { stream ->
            val multiResult = registry.parseWithPasswords(stream, candidates)
            assertNotNull(multiResult)
            assertEquals("prit1510", multiResult.workingPassword)
            assertEquals("Axis Bank", multiResult.result.metadata.bankName)
            assertTrue(multiResult.result.transactions.isNotEmpty())
        }
    }

    @Test
    fun testParseWithPasswordsThrowsInvalidPasswordWhenNoneMatch() {
        val encryptedBytes = createEncryptedPdfBytes("correct_pass")
        val candidates = listOf("wrong_pass_1", "wrong_pass_2", "wrong_pass_3")

        ByteArrayInputStream(encryptedBytes).use { stream ->
            try {
                registry.parseWithPasswords(stream, candidates)
                fail("Should have thrown InvalidPasswordException")
            } catch (e: StatementSecurityException.InvalidPasswordException) {
                // Expected
                assertTrue(e.message!!.contains("incorrect", ignoreCase = true))
            }
        }
    }

    @Test
    fun testParseWithPasswordsThrowsPasswordRequiredWhenListIsEmpty() {
        val encryptedBytes = createEncryptedPdfBytes("correct_pass")

        ByteArrayInputStream(encryptedBytes).use { stream ->
            try {
                registry.parseWithPasswords(stream, emptyList())
                fail("Should have thrown PasswordRequiredException")
            } catch (e: StatementSecurityException.PasswordRequiredException) {
                // Expected
                assertTrue(e.message!!.contains("required", ignoreCase = true))
            }
        }
    }

    @Test
    fun testParseWithPasswordsSucceedsOnUnencryptedPdf() {
        loadAirtelPdfStream().use { stream ->
            val multiResult = registry.parseWithPasswords(stream, emptyList())
            assertNotNull(multiResult)
            assertNull(multiResult.workingPassword)
            assertEquals("Axis Bank", multiResult.result.metadata.bankName)
        }
    }
}
