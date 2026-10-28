package kr.scin.rishmcp

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

/**
 * The ntfy app re-broadcasts every message it receives to any app on the phone,
 * and the broadcast itself carries no sender identity, so the body is the only
 * proof the relay sent it.
 */
object WakeAuth {
    private const val MAX_SKEW_SECONDS = 600L

    fun verify(deviceToken: String, body: String, nowSeconds: Long): Boolean {
        if (deviceToken.isEmpty()) return false
        val parts = body.trim().split(':', limit = 2)
        if (parts.size != 2) return false
        val sentAt = parts[0].toLongOrNull() ?: return false
        if (abs(nowSeconds - sentAt) > MAX_SKEW_SECONDS) return false
        return MessageDigest.isEqual(sign(deviceToken, sentAt).toByteArray(), parts[1].lowercase().toByteArray())
    }

    private fun sign(deviceToken: String, sentAt: Long): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(deviceToken.toByteArray(), "HmacSHA256"))
        return mac.doFinal("rish-wake:$sentAt".toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
