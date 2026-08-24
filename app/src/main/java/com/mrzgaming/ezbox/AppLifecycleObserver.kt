package com.mrzgaming.ezbox

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class AppLifecycleObserver(private val appContext: Context) : DefaultLifecycleObserver {

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        val prefs = appContext.getSharedPreferences("EZBoxPrefs", Context.MODE_PRIVATE)
        val autoStop = prefs.getBoolean("auto_stop_background", true)
        if (autoStop) {
            stopDesktopSession()
        }
    }

    private fun stopDesktopSession() {
        try {
            val command = "pkill -9 -f 'Xvnc :1 '"
            val intent = Intent().apply {
                action = "com.termux.RUN_COMMAND"
                component = ComponentName("com.termux", "com.termux.app.RunCommandService")
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            }
            ContextCompat.startForegroundService(appContext, intent)
            Log.d("EZBox", "Stopped VNC session (app left foreground)")
        } catch (e: Exception) {
            Log.e("EZBox", "Failed to stop session on background: ${e.message}")
        }
    }
}
