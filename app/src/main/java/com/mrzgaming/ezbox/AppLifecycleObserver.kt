package com.mrzgaming.ezbox

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class AppLifecycleObserver(private val appContext: Context) : DefaultLifecycleObserver {

    // Dipanggil saat user benar-benar keluar dari EZBox (tekan Home/app lain/recents),
    // BUKAN saat pindah tab Home/Store/Terminal di dalam app.
    // Ini cuma matiin PROSES VNC untuk hemat baterai - file & konfigurasi desktop
    // tetap tersimpan di $HOME/.ezos/home, jadi saat dibuka lagi otomatis "lanjut"
    // dari kondisi terakhir, bukan mulai dari nol.
    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        stopDesktopSession()
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
            Log.d("EZBox", "Stopped VNC session (app left foreground), desktop state preserved")
        } catch (e: Exception) {
            Log.e("EZBox", "Failed to stop session on background: ${e.message}")
        }
    }
}
