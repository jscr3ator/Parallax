package app.parallax.opencode.ui

import app.parallax.opencode.core.model.PermissionDecision
import app.parallax.opencode.core.net.privateHttpHost
import app.parallax.opencode.ui.screens.connect.normalizeUrl
import app.parallax.opencode.ui.screens.voice.voiceApproval
import org.junit.Assert.*
import org.junit.Test

class SecurityPolicyTest {
    @Test fun approvalRequiresAnUnambiguousCommand() {
        assertEquals(PermissionDecision.REJECT, voiceApproval("Don’t allow."))
        assertEquals(PermissionDecision.ONCE, voiceApproval("Allow once."))
        assertEquals(PermissionDecision.ALWAYS, voiceApproval("Always allow."))
        for (phrase in listOf("don't always allow", "always", "not okay", "allow or deny", "yesterday")) {
            assertNull(voiceApproval(phrase))
        }
    }

    @Test fun addressesDefaultToHttpsAndHttpIsLimitedToPrivateHosts() {
        assertEquals("https://example.com", normalizeUrl(" example.com/ "))
        assertEquals("http://10.0.0.2:4096", normalizeUrl("http://10.0.0.2:4096"))
        for (host in listOf("10.0.0.2", "192.168.1.2", "172.31.0.1", "100.64.0.1", "::1", "fd00::1")) {
            assertTrue(host, privateHttpHost(host))
        }
        for (host in listOf("example.com", "localhost.example", "10.0.0.1.example", "172.32.0.1", "100.128.0.1", "8.8.8.8", "2001:db8::1")) {
            assertFalse(host, privateHttpHost(host))
        }
    }

    @org.junit.Test
    fun bareAddressesUseHttpOnlyOnPrivateNetworks() {
        assertEquals("http://10.0.0.177:3080", normalizeUrl("10.0.0.177:3080"))
        assertEquals("http://192.168.1.20:4096", normalizeUrl("192.168.1.20:4096/"))
        assertEquals("http://100.120.41.52:4096", normalizeUrl("100.120.41.52:4096"))
        assertEquals("http://localhost:3000", normalizeUrl("localhost:3000"))
        assertEquals("https://pi.example.com", normalizeUrl("pi.example.com"))
    }
}
