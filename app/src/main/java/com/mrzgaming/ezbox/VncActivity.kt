package com.mrzgaming.ezbox

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VncActivity : AppCompatActivity() {

    private lateinit var vncScreen: ImageView
    private lateinit var vncStatus: TextView
    private lateinit var hiddenInput: EditText
    private lateinit var btnToggleKeyboard: Button
    private lateinit var btnCtrl: ToggleButton
    private lateinit var btnAlt: ToggleButton
    private var rfbClient: RfbClient? = null
    private var running = false
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var mouseMode = "direct"
    private var lastTrackpadX = 0f
    private var lastTrackpadY = 0f
    private var virtualCursorX = 0
    private var virtualCursorY = 0

    // Keysym constants
    private val KEY_CTRL_L = 0xFFE3
    private val KEY_ALT_L = 0xFFE9
    private val KEY_ESC = 0xFF1B
    private val KEY_TAB = 0xFF09
    private val KEY_UP = 0xFF52
    private val KEY_DOWN = 0xFF54
    private val KEY_LEFT = 0xFF51
    private val KEY_RIGHT = 0xFF53

    private val pointerChannel = Channel<Triple<Int, Int, Int>>(Channel.CONFLATED)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_vnc)

        mouseMode = intent.getStringExtra("mouse_mode") ?: "direct"

        vncScreen = findViewById(R.id.vncScreen)
        vncStatus = findViewById(R.id.vncStatus)
        hiddenInput = findViewById(R.id.hiddenInput)
        btnToggleKeyboard = findViewById(R.id.btnToggleKeyboard)
        btnCtrl = findViewById(R.id.btnCtrl)
        btnAlt = findViewById(R.id.btnAlt)

        connectAndRender()
        setupKeyboardInput()
        setupExtraKeys()
        startPointerSender()

        vncScreen.setOnTouchListener { _, event ->
            handleTouch(event)
            true
        }
    }

    private fun setupExtraKeys() {
        findViewById<Button>(R.id.btnEsc).setOnClickListener { sendKeysym(KEY_ESC) }
        findViewById<Button>(R.id.btnTab).setOnClickListener { sendKeysym(KEY_TAB) }
        findViewById<Button>(R.id.btnUp).setOnClickListener { sendKeysym(KEY_UP) }
        findViewById<Button>(R.id.btnDown).setOnClickListener { sendKeysym(KEY_DOWN) }
        findViewById<Button>(R.id.btnLeft).setOnClickListener { sendKeysym(KEY_LEFT) }
        findViewById<Button>(R.id.btnRight).setOnClickListener { sendKeysym(KEY_RIGHT) }
        // Ctrl dan Alt sengaja tidak langsung kirim di sini - statusnya (isChecked) dibaca
        // saat tombol lain/keyboard ditekan, supaya bisa dipakai sebagai modifier kombinasi (Ctrl+C dst)
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
                            sendModifiedChar(c)
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

    // Kirim karakter dengan mempertimbangkan Ctrl/Alt yang sedang di-toggle aktif
    private fun sendModifiedChar(c: Char) {
        val client = rfbClient ?: return
        val ctrlOn = btnCtrl.isChecked
        val altOn = btnAlt.isChecked

        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (ctrlOn) client.sendKeyEvent(KEY_CTRL_L, true)
                    if (altOn) client.sendKeyEvent(KEY_ALT_L, true)

                    client.sendKeyEvent(c.code, true)
                    client.sendKeyEvent(c.code, false)

                    if (altOn) client.sendKeyEvent(KEY_ALT_L, false)
                    if (ctrlOn) client.sendKeyEvent(KEY_CTRL_L, false)
                }
                // Modifier sekali pakai - otomatis lepas toggle setelah dipakai
                if (ctrlOn) runOnUiThread { btnCtrl.isChecked = false }
                if (altOn) runOnUiThread { btnAlt.isChecked = false }
            } catch (e: Exception) {
                Log.e("VncActivity", "Modified key send failed: ${e.message}")
            }
        }
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
        val port = intent.getIntExtra("vnc_port", 5901)
        val password = intent.getStringExtra("vnc_password") ?: "ezbox123"
        scope.launch {
            vncStatus.text = "Connecting to EZOS desktop..."
            val client = RfbClient("127.0.0.1", port, password)
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
            virtualCursorX = client.width / 2
            virtualCursorY = client.height / 2
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

    // Channel conflated: hanya menyimpan event TERBARU, mencegah banjir pointer event saat drag cepat
    private fun startPointerSender() {
        scope.launch {
            for (event in pointerChannel) {
                try {
                    withContext(Dispatchers.IO) {
                        rfbClient?.sendPointerEvent(event.first, event.second, event.third)
                    }
                } catch (e: Exception) {
                    Log.e("VncActivity", "Pointer send failed: ${e.message}")
                }
            }
        }
    }

    private fun handleTouch(event: MotionEvent) {
        val client = rfbClient ?: return

        if (mouseMode == "trackpad") {
            handleTrackpadTouch(client, event)
        } else {
            handleDirectTouch(client, event)
        }
    }

    private fun handleDirectTouch(client: RfbClient, event: MotionEvent) {
        val mapped = mapTouchToDesktop(client, event.x, event.y) ?: return
        val buttonMask = when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> 1
            MotionEvent.ACTION_UP -> 0
            else -> return
        }
        pointerChannel.trySend(Triple(mapped.first, mapped.second, buttonMask))
    }

    // Mode trackpad: gerakan jari menggeser posisi kursor secara RELATIF (seperti touchpad laptop),
    // bukan tap-langsung-ke-posisi. Cocok untuk kontrol presisi tinggi.
    private fun handleTrackpadTouch(client: RfbClient, event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTrackpadX = event.x
                lastTrackpadY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.x - lastTrackpadX).toInt()
                val dy = (event.y - lastTrackpadY).toInt()
                virtualCursorX = (virtualCursorX + dx).coerceIn(0, client.width - 1)
                virtualCursorY = (virtualCursorY + dy).coerceIn(0, client.height - 1)
                lastTrackpadX = event.x
                lastTrackpadY = event.y
                pointerChannel.trySend(Triple(virtualCursorX, virtualCursorY, 0))
            }
            MotionEvent.ACTION_UP -> {
                // Tap singkat di mode trackpad = klik di posisi kursor virtual saat ini
                pointerChannel.trySend(Triple(virtualCursorX, virtualCursorY, 1))
                pointerChannel.trySend(Triple(virtualCursorX, virtualCursorY, 0))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        running = false
        rfbClient?.close()
    }
}
