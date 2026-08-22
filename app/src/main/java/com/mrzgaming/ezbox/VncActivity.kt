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
import kotlinx.coroutines.delay
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
            vncStatus.text = "Connecting to EZOS desktop..."
            val client = RfbClient("127.0.0.1", 5901, "ezbox123")
            val connected = try {
                withContext(Dispatchers.IO) { client.connect() }
            } catch (e: Exception) {
                Log.e("VncActivity", "Connect error: ${e.message}")
                false
            }

            if (!connected) {
                vncStatus.text = "Failed to connect to EZOS desktop.\nMake sure the environment is running."
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
            try {
                val updated = withContext(Dispatchers.IO) {
                    client.requestFramebufferUpdate(true)
                    client.readServerMessage()
                }
                if (updated) {
                    vncScreen.setImageBitmap(client.bitmap)
                }
            } catch (e: Exception) {
                Log.e("VncActivity", "Connection lost: ${e.message}")
                running = false
                vncStatus.text = "Connection to EZOS desktop was lost.\nTap back and try launching again."
                client.close()
                return
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
            try {
                withContext(Dispatchers.IO) {
                    client.sendPointerEvent(screenX, screenY, buttonMask)
                }
            } catch (e: Exception) {
                Log.e("VncActivity", "Touch send failed: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        running = false
        rfbClient?.close()
    }
}
