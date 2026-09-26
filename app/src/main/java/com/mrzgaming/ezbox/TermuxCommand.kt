package com.mrzgaming.ezbox

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log

object TermuxCommand {

    private const val TAG = "TermuxCommand"
    private const val TERMUX_PACKAGE = "com.termux"
    private const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
    private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
    private const val ACTION_RESULT = "com.mrzgaming.ezbox.RUN_COMMAND_RESULT"

    /**
     * Termux reports failures through this PendingIntent. Without it every error is
     * discarded silently and the user just stares at a spinner until timeout.
     */
    private var resultReceiver: BroadcastReceiver? = null

    @Volatile
    var lastError: String? = null
        private set

    fun onError(callback: (String) -> Unit) {
        errorCallbacks += callback
    }

    /** Removes a previously registered callback. Without this the static list pins every
     *  Activity that ever registered, for the life of the process. */
    fun removeErrorListener(callback: (String) -> Unit) {
        errorCallbacks -= callback
    }

    private val errorCallbacks = mutableListOf<(String) -> Unit>()

    private fun ensureReceiver(context: Context) {
        if (resultReceiver != null) return
        val appContext = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val error = intent.getStringExtra(EXTRA_ERROR_MESSAGE)
                val output = intent.getStringExtra(EXTRA_RESULT_OUTPUT)
                val resultCode = intent.getIntExtra(EXTRA_ERROR_CODE, 0)
                if (resultCode != 0 || !error.isNullOrBlank()) {
                    val message = error?.takeIf { it.isNotBlank() }
                        ?: "Termux command failed (code $resultCode)"
                    Log.e(TAG, "RUN_COMMAND failed: $message ${output ?: ""}".trim())
                    lastError = message
                    errorCallbacks.toList().forEach { it(message) }
                }
            }
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pending = PendingIntent.getBroadcast(
            appContext, 0, Intent(ACTION_RESULT).setPackage(appContext.packageName), flags
        )
        appContext.registerReceiver(receiver, IntentFilter(ACTION_RESULT), Context.RECEIVER_NOT_EXPORTED)
        resultReceiver = receiver
        // Attach the PendingIntent to every future execute() call.
        pendingIntentHolder = pending
    }

    private var pendingIntentHolder: PendingIntent? = null

    private const val EXTRA_ERROR_CODE = "com.termux.RUN_COMMAND_ERROR_CODE"
    private const val EXTRA_ERROR_MESSAGE = "com.termux.RUN_COMMAND_ERROR_MESSAGE"
    private const val EXTRA_RESULT_OUTPUT = "com.termux.RUN_COMMAND_RESULT_OUTPUT"
    private const val EXTRA_PENDING = "com.termux.RUN_COMMAND_PENDING_INTENT"

    fun execute(context: Context, command: String, background: Boolean = true): Intent {
        ensureReceiver(context)
        return Intent().apply {
            action = ACTION_RUN_COMMAND
            component = ComponentName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", background)
            putExtra(EXTRA_PENDING, pendingIntentHolder)
        }
    }

    /** Error Termux throws when `allow-external-apps` is not enabled. */
    const val ERR_EXTERNAL_APPS =
        "RunCommandService requires `allow-external-apps` property to be set to `true`"

    fun needsTermuxSetup(message: String): Boolean = message.contains(ERR_EXTERNAL_APPS)

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
