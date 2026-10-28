package kr.scin.rishmcp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import androidx.core.content.ContextCompat

/**
 * Always-on foreground service. Holds the relay connection (via
 * [ConnectionManager]) and the ADB shell session (via [AdbShellClient]).
 *
 * This used to own the WebSocket and the Shizuku UserService binding
 * directly; that logic now lives in ConnectionManager/AdbShellClient, so
 * this class stays a thin lifecycle + notification shell (docs/DESIGN.md
 * §2.1: "역할 동일, AdbShellClient/ConnectionManager 사용하도록 내부 배선만
 * 교체").
 */
class AgentService : Service() {

    private lateinit var connectionManager: ConnectionManager

    private val wakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getStringExtra("topic") != WAKE_TOPIC) return
            sendBroadcast(Intent(TAILSCALE_CONNECT).setClassName(TAILSCALE_PKG, "$TAILSCALE_PKG.IPNReceiver"))
            WirelessDebugging.ensureEnabled(context)
            connectionManager.forceReconnect("wake")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AgentState.serviceRunning = true
        startForeground(NOTIF_ID, buildNotification("starting…"))
        connectionManager = ConnectionManager(
            context = this,
            shellClient = AdbShellClient.getInstance(this),
            onStateChanged = ::updateNotif,
        )
        connectionManager.start()
        ContextCompat.registerReceiver(
            this,
            wakeReceiver,
            IntentFilter(NTFY_MESSAGE_RECEIVED),
            ContextCompat.RECEIVER_EXPORTED,
        )
        updateNotif()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Re-provisioning (new relay/token/adb host) restarts the connection
        // without a force-stop.
        if (intent?.getBooleanExtra("reconnect", false) == true) {
            connectionManager.forceReconnect("reconfigured")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        AgentState.serviceRunning = false
        AgentState.conn = AgentState.Conn.IDLE
        unregisterReceiver(wakeReceiver)
        connectionManager.stop()
        super.onDestroy()
    }

    private fun updateNotif() {
        val text = when (AgentState.conn) {
            AgentState.Conn.CONNECTED -> "connected · ${AgentState.network} · shell ${AgentState.shell}"
            AgentState.Conn.CONNECTING -> "connecting…"
            AgentState.Conn.DISCONNECTED -> "reconnecting…"
            AgentState.Conn.IDLE -> AgentState.lastEvent.ifBlank { "idle" }
        }
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL, "rish-mcp agent", NotificationManager.IMPORTANCE_LOW)
        channel.setShowBadge(false)
        nm.createNotificationChannel(channel)
        return Notification.Builder(this, CHANNEL)
            .setContentTitle(if (DeviceProfile.isWatch(this)) "rish-mcp watch agent" else "rish-mcp agent")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "rishmcp-agent"
        private const val NOTIF_ID = 42
        private const val NTFY_MESSAGE_RECEIVED = "io.heckel.ntfy.MESSAGE_RECEIVED"
        private const val WAKE_TOPIC = "rish-wake"
        private const val TAILSCALE_PKG = "com.tailscale.ipn"
        private const val TAILSCALE_CONNECT = "com.tailscale.ipn.CONNECT_VPN"

        fun start(ctx: Context, reconnect: Boolean = false) {
            val intent = Intent(ctx, AgentService::class.java).putExtra("reconnect", reconnect)
            ctx.startForegroundService(intent)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, AgentService::class.java))
        }
    }
}
