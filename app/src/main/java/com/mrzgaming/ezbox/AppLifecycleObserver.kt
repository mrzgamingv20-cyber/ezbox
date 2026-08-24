package com.mrzgaming.ezbox

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class AppLifecycleObserver(private val appContext: Context) : DefaultLifecycleObserver {

    // Dipanggil saat SEMUA activity EZBox sudah tidak terlihat (user benar-benar keluar
    // dari app - tekan Home, buka app lain, atau swipe dari recents), BUKAN saat
    // pindah antar tab/fragment di dalam EZBox
    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        stopActiveSession()
    }

    private fun stopActiveSession() {
        val prefs = appContext.getSharedPreferences("EZBoxActiveSession", Context.MODE_PRIVATE)
        val displayNum = prefs.getInt("active_display_num", -1)

        if (displayNum == -1) return // Belum pernah launch session apa pun

        try {
            val command = "pkill -9 -f 'Xvnc :$displayNum '"
            val intent = Intent().apply {
                action = "com.termux.RUN_COMMAND"
                component = ComponentName("com.termux", "com.termux.app.RunCommandService")
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            }
            ContextCompat.startForegroundService(appContext, intent)
            Log.d("EZBox", "Stopped session on display :$displayNum due to app leaving foreground")

            prefs.edit().remove("active_display_num").apply()
        } catch (e: Exception) {
            Log.e("EZBox", "Failed to stop session on background: ${e.message}")
        }
    }
}
