package kr.scin.rishmcp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kr.scin.rishmcp.Prefs.enabled

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action in RESTART_ACTIONS && context.enabled) AgentService.start(context)
    }

    private companion object {
        val RESTART_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
