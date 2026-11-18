package app.parallax.opencode.core.net

import app.parallax.opencode.core.repo.RepoException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OpenCodePairingTest {
    @Test fun qrAndPrintedLinkResolveToSameEndpoint() {
        val link = "https://example.test/auth/connect/test-code_123"
        val qr = """{"code":"test-code_123","urls":["https://example.test"]}"""
        assertEquals(listOf(link), ServerAuth.openCodePairingUrls(qr).map { it.toString() })
        assertEquals(ServerAuth.openCodePairingUrls(link), ServerAuth.openCodePairingUrls(qr))
    }

    @Test fun keepsCandidateOrderAndRejectsInvalidCodes() {
        val qr = """{"urls":["http://192.168.1.5:4096","https://example.test"],"code":"test"}"""
        assertEquals(listOf("192.168.1.5", "example.test"), ServerAuth.openCodePairingUrls(qr).map { it.host })
        assertThrows(RepoException::class.java) { ServerAuth.openCodePairingUrls(qr.replace("\"test\"", "\"../test\"")) }
    }
}
