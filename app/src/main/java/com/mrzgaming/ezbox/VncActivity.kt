package com.mrzgaming.ezbox

import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VncActivity : AppCompatActivity() {

    private lateinit var vncScreen: ImageView
    private lateinit var vncStatus: TextView
    private var rfbClient: RfbClient? = null
    private var running = false
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vnc)

        vncScreen = findViewById(R.id.vncScreen)
        vncStatus = findViewById(R.id.vncStatus)

        connectAndRender()

        vncScreen.setOnTouchListener { _, event ->
            handleTouch(event)
            true
        }
    }

    private fun connectAndRender() {
        scope.launch {
            val client = RfbClient("127.0.0.1", 5901, "ezbox123")
            val connected = withContext(Dispatchers.IO) { client.connect() }

            if (!connected) {
                vncStatus.text = "Failed to connect to EZOS desktop.\nMake sure the environment is running."
                Log.e("VncActivity", "Connection failed")
                return@launch
            }

            rfbClient = client
            vncStatus.text = ""
            running = true
            renderLoop(client)
        }
    }

    private suspend fun renderLoop(client: RfbClient) {
        while (running) {
            val updated = withContext(Dispatchers.IO) {
                client.requestFramebufferUpdate(true)
                client.readServerMessage()
            }
            if (updated) {
                vncScreen.setImageBitmap(client.bitmap)
            }
        }
    }

    private fun handleTouch(event: MotionEvent) {
        val client = rfbClient ?: return
        val screenX = event.x.toInt().coerceIn(0, client.width - 1)
        val screenY = event.y.toInt().coerceIn(0, client.height - 1)

        val buttonMask = when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> 1
            MotionEvent.ACTION_UP -> 0
            else -> return
        }

        scope.launch {
            withContext(Dispatchers.IO) {
                client.sendPointerEvent(screenX, screenY, buttonMask)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        running = false
        rfbClient?.close()
    }
}
