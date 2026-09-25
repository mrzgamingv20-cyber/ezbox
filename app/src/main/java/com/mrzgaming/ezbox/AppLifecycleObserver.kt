package com.mrzgaming.ezbox

import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

class AppLifecycleObserver(private val appContext: Context) : DefaultLifecycleObserver {

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        val prefs = appContext.getSharedPreferences("EZBoxPrefs", Context.MODE_PRIVATE)
        val autoStop = prefs.getBoolean("auto_stop_background", false)
        if (autoStop && isAppInBackground()) {
            stopDesktopSession()
        }
    }

    private fun isAppInBackground(): Boolean {
        val processOwner = ProcessLifecycleOwner.get()
        return !processOwner.lifecycle.currentState.isAtLeast(
            androidx.lifecycle.Lifecycle.State.STARTED
        )
    }

    private fun stopDesktopSession() {
        try {
            val command = "pkill -9 -f 'Xvnc :1 '; pkill -9 -f 'xfce4-session'; pkill -9 -f 'startlxqt'; echo idle > /storage/emulated/0/Download/ezbox_backend_status.txt"
            TermuxCommand.start(appContext, command)
            Log.d("EZBox", "Stopped VNC session (app left foreground)")
        } catch (e: Exception) {
            Log.e("EZBox", "Failed to stop session on background: ${e.message}")
        }
    }
}
