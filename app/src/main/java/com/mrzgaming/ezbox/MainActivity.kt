package com.mrzgaming.ezbox

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnSetup).setOnClickListener {
            setupEnvironment()
        }
    }

    private fun setupEnvironment() {
        val intent = Intent().apply {
            action = "com.termux.app.RUN_COMMAND"
            component = ComponentName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/pkg")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("install", "-y", "wine", "box64", "box86", "x11-repo", "xfce4"))
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
        }
        startService(intent)
    }
}
