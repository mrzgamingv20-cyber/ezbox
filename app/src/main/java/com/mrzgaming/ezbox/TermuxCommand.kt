package com.mrzgaming.ezbox

import android.content.ComponentName
import android.content.Context
import android.content.Intent

object TermuxCommand {

    private const val TERMUX_PACKAGE = "com.termux"
    private const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
    private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"

    fun execute(context: Context, command: String, background: Boolean = true): Intent {
        return Intent().apply {
            action = ACTION_RUN_COMMAND
            component = ComponentName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", background)
        }
    }

    fun start(context: Context, command: String, background: Boolean = true) {
        context.startService(execute(context, command, background))
    }

    fun startForResult(context: Context, command: String, requestCode: Int) {
        val intent = execute(context, command, background = false)
        if (context is android.app.Activity) {
            context.startActivityForResult(intent, requestCode)
        }
    }
}
