package kr.scin.rishmcp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import io.github.muntashirakon.adb.android.AdbMdns
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.NetworkInterface

object WirelessDebugging {
    private const val SETTING = "adb_wifi_enabled"

    fun ensureEnabled(context: Context): Boolean {
        val resolver = context.contentResolver
        if (Settings.Global.getInt(resolver, SETTING, 0) == 1) return true
        val granted = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && runCatching { Settings.Global.putInt(resolver, SETTING, 1) }.getOrDefault(false)
    }

    suspend fun discoverOwnEndpoint(context: Context, timeoutMs: Long): Pair<String, Int>? {
        val own = ownIpv4Addresses()
        val found = CompletableDeferred<Pair<String, Int>>()
        val mdns = AdbMdns(context, AdbMdns.SERVICE_TYPE_TLS_CONNECT) { host, port ->
            val address = (host as? Inet4Address)?.hostAddress
            if (address != null && port > 0 && address in own) found.complete(address to port)
        }
        mdns.start()
        return try {
            withTimeoutOrNull(timeoutMs) { found.await() }
        } finally {
            mdns.stop()
        }
    }

    private fun ownIpv4Addresses(): Set<String> =
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }
            .toSet()
}
