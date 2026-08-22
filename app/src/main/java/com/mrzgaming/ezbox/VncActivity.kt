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

    // Konversi koordinat sentuhan (posisi di ImageView) ke koordinat asli desktop (bitmap),
    // dengan memperhitungkan scaling dan offset dari scaleType="fitCenter"
    private fun mapTouchToDesktop(client: RfbClient, touchX: Float, touchY: Float): Pair<Int, Int>? {
        val viewWidth = vncScreen.width.toFloat()
        val viewHeight = vncScreen.height.toFloat()
        val bitmapWidth = client.width.toFloat()
        val bitmapHeight = client.height.toFloat()

        if (viewWidth <= 0 || viewHeight <= 0 || bitmapWidth <= 0 || bitmapHeight <= 0) return null

        // fitCenter: pilih scale terkecil supaya bitmap muat penuh di dalam view
        val scale = minOf(viewWidth / bitmapWidth, viewHeight / bitmapHeight)
        val scaledWidth = bitmapWidth * scale
        val scaledHeight = bitmapHeight * scale

        // Offset karena bitmap yang sudah di-scale diletakkan di tengah (centered)
        val offsetX = (viewWidth - scaledWidth) / 2f
        val offsetY = (viewHeight - scaledHeight) / 2f

        val desktopX = ((touchX - offsetX) / scale).toInt()
        val desktopY = ((touchY - offsetY) / scale).toInt()

        if (desktopX < 0 || desktopY < 0 || desktopX >= client.width || desktopY >= client.height) return null

        return Pair(desktopX, desktopY)
    }

    private fun handleTouch(event: MotionEvent) {
        val client = rfbClient ?: return
        val mapped = mapTouchToDesktop(client, event.x, event.y) ?: return

        val buttonMask = when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> 1
            MotionEvent.ACTION_UP -> 0
            else -> return
        }

        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    client.sendPointerEvent(mapped.first, mapped.second, buttonMask)
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
