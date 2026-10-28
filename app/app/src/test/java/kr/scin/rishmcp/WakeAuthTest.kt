package kr.scin.rishmcp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeAuthTest {
    private val body = "1700000000:73ab80c36313b7e89adc8969e43cb072bc3d51089169c5ce2d10b76888e680de"

    @Test
    fun `accepts a body signed with the device token`() {
        assertTrue(WakeAuth.verify("tok", body, 1_700_000_000))
    }

    @Test
    fun `tolerates trailing whitespace and upper-case hex`() {
        assertTrue(WakeAuth.verify("tok", " " + body.uppercase() + "\n", 1_700_000_000))
    }

    @Test
    fun `rejects the old unauthenticated body`() {
        assertFalse(WakeAuth.verify("tok", "wake", 1_700_000_000))
    }

    @Test
    fun `rejects a different token and an empty token`() {
        assertFalse(WakeAuth.verify("other", body, 1_700_000_000))
        assertFalse(WakeAuth.verify("", body, 1_700_000_000))
    }

    @Test
    fun `rejects a changed timestamp or signature`() {
        assertFalse(WakeAuth.verify("tok", body.replaceFirst("1700000000", "1700000001"), 1_700_000_000))
        assertFalse(WakeAuth.verify("tok", body.dropLast(1) + "0", 1_700_000_000))
        assertFalse(WakeAuth.verify("tok", "1700000000:", 1_700_000_000))
    }

    @Test
    fun `rejects a replay outside the ten minute window`() {
        assertTrue(WakeAuth.verify("tok", body, 1_700_000_600))
        assertFalse(WakeAuth.verify("tok", body, 1_700_000_601))
        assertTrue(WakeAuth.verify("tok", body, 1_699_999_400))
        assertFalse(WakeAuth.verify("tok", body, 1_699_999_399))
    }
}
