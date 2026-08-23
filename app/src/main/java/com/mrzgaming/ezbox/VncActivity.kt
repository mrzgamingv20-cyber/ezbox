package com.mrzgaming.ezbox

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VncActivity : AppCompatActivity() {

    private lateinit var vncScreen: ImageView
    private lateinit var vncStatus: TextView
    private lateinit var hiddenInput: EditText
    private lateinit var btnToggleKeyboard: Button
    private var rfbClient: RfbClient? = null
    private var running = false
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var lastFrameTime = 0L
    private val minFrameIntervalMs = 33L // cap render ke ~30fps, cegah main thread banjir setImageBitmap

    // Menyimpan hanya event pointer TERBARU. CONFLATED = kalau ada event baru
    // sebelum yang lama sempat dikirim, yang lama otomatis dibuang (bukan menumpuk).
    // Ini yang mencegah banjir coroutine saat drag/select teks cepat.
    private data class PointerEvent(val x: Int, val y: Int, val mask: Int)
    private val pointerChannel = Channel<PointerEvent>(Channel.CONFLATED)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.statusBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        setContentView(R.layout.activity_vnc)

        vncScreen = findViewById(R.id.vncScreen)
        vncStatus = findViewById(R.id.vncStatus)
        hiddenInput = findViewById(R.id.hiddenInput)
        btnToggleKeyboard = findViewById(R.id.btnToggleKeyboard)

        connectAndRender()
        setupKeyboardInput()
        startPointerSender()

        vncScreen.setOnTouchListener { _, event ->
            handleTouch(event)
            true
        }
    }

    /**
     * Satu coroutine tunggal, hidup selama Activity ini hidup, yang terus
     * mengambil posisi pointer TERBARU dari channel dan mengirimkannya.
     * Ini menggantikan pola lama "scope.launch{} per touch event" yang
     * membanjiri thread pool IO dan bikin render loop ikut freeze.
     */
    private fun startPointerSender() {
        scope.launch {
            for (pointerEvent in pointerChannel) {
                val client = rfbClient ?: continue
                try {
                    withContext(Dispatchers.IO) {
                        client.sendPointerEvent(pointerEvent.x, pointerEvent.y, pointerEvent.mask)
                    }
                } catch (e: Exception) {
                    Log.e("VncActivity", "Pointer send failed: ${e.message}")
                }
            }
        }
    }

    private fun setupKeyboardInput() {
        btnToggleKeyboard.setOnClickListener {
            hiddenInput.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(hiddenInput, InputMethodManager.SHOW_FORCED)
        }

        hiddenInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (count > 0 && s != null) {
                    val newChars = s.subSequence(start, start + count)
                    for (c in newChars) {
                        if (c == '\n') {
                            sendKeysym(0xFF0D)
                        } else {
                            sendCharKey(c)
                        }
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) {
                if (!s.isNullOrEmpty()) s.clear()
            }
        })

        hiddenInput.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DEL -> {
                        sendKeysym(0xFF08)
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_ENTER -> {
                        sendKeysym(0xFF0D)
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }

        hiddenInput.setOnEditorActionListener { _, actionId, event ->
            sendKeysym(0xFF0D)
            hiddenInput.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(hiddenInput, InputMethodManager.SHOW_FORCED)
            true
        }
    }

    private fun sendCharKey(c: Char) {
        sendKeysym(c.code)
    }

    private fun sendKeysym(keysym: Int) {
        val client = rfbClient ?: return
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    client.sendKeyEvent(keysym, true)
                    client.sendKeyEvent(keysym, false)
                }
            } catch (e: Exception) {
                Log.e("VncActivity", "Key send failed: ${e.message}")
            }
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
                    val now = System.currentTimeMillis()
                    if (now - lastFrameTime >= minFrameIntervalMs) {
                        vncScreen.setImageBitmap(client.bitmap)
                        lastFrameTime = now
                    }
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

    private fun mapTouchToDesktop(client: RfbClient, touchX: Float, touchY: Float): Pair<Int, Int>? {
        val viewWidth = vncScreen.width.toFloat()
        val viewHeight = vncScreen.height.toFloat()
        val bitmapWidth = client.width.toFloat()
        val bitmapHeight = client.height.toFloat()

        if (viewWidth <= 0 || viewHeight <= 0 || bitmapWidth <= 0 || bitmapHeight <= 0) return null

        val scale = minOf(viewWidth / bitmapWidth, viewHeight / bitmapHeight)
        val scaledWidth = bitmapWidth * scale
        val scaledHeight = bitmapHeight * scale

        val offsetX = (viewWidth - scaledWidth) / 2f
        val offsetY = (viewHeight - scaledHeight) / 2f

        val desktopX = ((touchX - offsetX) / scale).toInt()
        val desktopY = ((touchY - offsetY) / scale).toInt()

        if (desktopX < 0 || desktopY < 0 || desktopX >= client.width || desktopY >= client.height) return null

        return Pair(desktopX, desktopY)
    }

    /**
     * Sekarang HANYA menaruh event ke channel (non-blocking, instan),
     * bukan langsung scope.launch{} seperti sebelumnya. Pengiriman
     * sesungguhnya ditangani satu coroutine di startPointerSender().
     */
    private fun handleTouch(event: MotionEvent) {
        val client = rfbClient ?: return
        val mapped = mapTouchToDesktop(client, event.x, event.y) ?: return

        val buttonMask = when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> 1
            MotionEvent.ACTION_UP -> 0
            else -> return
        }

        pointerChannel.trySend(PointerEvent(mapped.first, mapped.second, buttonMask))
    }

    override fun onDestroy() {
        super.onDestroy()
        running = false
        rfbClient?.close()
        pointerChannel.close()
        scope.cancel()
    }
}
