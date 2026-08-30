package com.mrzgaming.ezbox

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
    private lateinit var btnStopDesktop: Button
    private lateinit var btnCtrl: ToggleButton
    private lateinit var btnAlt: ToggleButton
    private lateinit var extraKeysBar: android.widget.HorizontalScrollView
    private var rfbClient: RfbClient? = null
    private var running = false
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var mouseMode = "direct"
    private var lastTrackpadX = 0f
    private var lastTrackpadY = 0f
    private var virtualCursorX = 0
    private var virtualCursorY = 0

    private val KEY_CTRL_L = 0xFFE3
    private val KEY_ALT_L = 0xFFE9
    private val KEY_ESC = 0xFF1B
    private val KEY_TAB = 0xFF09
    private val KEY_UP = 0xFF52
    private val KEY_DOWN = 0xFF54
    private val KEY_LEFT = 0xFF51
    private val KEY_RIGHT = 0xFF53
    private val KEY_SUPER = 0xFFEB
    private val KEY_F1 = 0xFFBE
    private val KEY_F2 = 0xFFBF
    private val KEY_F3 = 0xFFC0
    private val KEY_F4 = 0xFFC1
    private val KEY_HOME = 0xFF50
    private val KEY_END = 0xFF57
    private val KEY_PGUP = 0xFF55
    private val KEY_PGDN = 0xFF56
    private val KEY_DEL = 0xFFFF

    private val pointerChannel = Channel<Triple<Int, Int, Int>>(Channel.CONFLATED)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_vnc)
        val prefs = getSharedPreferences("EZBoxPrefs", MODE_PRIVATE)
        mouseMode = intent.getStringExtra("mouse_mode") ?: prefs.getString("mouse_mode", "direct") ?: "direct"
        if (prefs.getBoolean("keep_awake", false)) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        vncScreen = findViewById(R.id.vncScreen)
        vncStatus = findViewById(R.id.vncStatus)
        hiddenInput = findViewById(R.id.hiddenInput)
        btnToggleKeyboard = findViewById(R.id.btnToggleKeyboard)
        btnStopDesktop = findViewById(R.id.btnStopDesktop)
        btnCtrl = findViewById(R.id.btnCtrl)
        btnAlt = findViewById(R.id.btnAlt)
        extraKeysBar = findViewById(R.id.extraKeysBar)

        btnStopDesktop.setOnClickListener { stopDesktop() }

        connectAndRender()
        setupKeyboardInput()
        setupExtraKeys()
        setupClipboardAndScreenshot()
        startPointerSender()
        vncScreen.setOnTouchListener { _, event -> handleTouch(event); true }
    }

    private fun stopDesktop() {
        val command = "pkill -9 -f 'Xvnc :1 '; pkill -9 -f 'ezos-run'; echo done"
        try {
            val intent = Intent("com.termux.RUN_COMMAND").apply {
                component = ComponentName("com.termux", "com.termux.app.RunCommandService")
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            }
            ContextCompat.startForegroundService(this, intent)
        } catch (e: Exception) {
            Log.e("VncActivity", "Stop desktop failed: ${e.message}")
        } finally {
            running = false
            rfbClient?.close()
            finish()
        }
    }

    private fun setupExtraKeys() {
        findViewById<Button>(R.id.btnEsc).setOnClickListener { sendKeysym(KEY_ESC) }
        findViewById<Button>(R.id.btnTab).setOnClickListener { sendKeysym(KEY_TAB) }
        findViewById<Button>(R.id.btnUp).setOnClickListener { sendKeysym(KEY_UP) }
        findViewById<Button>(R.id.btnDown).setOnClickListener { sendKeysym(KEY_DOWN) }
        findViewById<Button>(R.id.btnLeft).setOnClickListener { sendKeysym(KEY_LEFT) }
        findViewById<Button>(R.id.btnRight).setOnClickListener { sendKeysym(KEY_RIGHT) }
        findViewById<Button>(R.id.btnSuper).setOnClickListener { sendKeysym(KEY_SUPER) }
        findViewById<Button>(R.id.btnF1).setOnClickListener { sendKeysym(KEY_F1) }
        findViewById<Button>(R.id.btnF2).setOnClickListener { sendKeysym(KEY_F2) }
        findViewById<Button>(R.id.btnF3).setOnClickListener { sendKeysym(KEY_F3) }
        findViewById<Button>(R.id.btnF4).setOnClickListener { sendKeysym(KEY_F4) }
        findViewById<Button>(R.id.btnHome).setOnClickListener { sendKeysym(KEY_HOME) }
        findViewById<Button>(R.id.btnEnd).setOnClickListener { sendKeysym(KEY_END) }
        findViewById<Button>(R.id.btnPgUp).setOnClickListener { sendKeysym(KEY_PGUP) }
        findViewById<Button>(R.id.btnPgDn).setOnClickListener { sendKeysym(KEY_PGDN) }
        findViewById<Button>(R.id.btnDel).setOnClickListener { sendKeysym(KEY_DEL) }
    }

    private fun setupClipboardAndScreenshot() {
        findViewById<Button>(R.id.btnClipboard).setOnClickListener { sendAndroidClipboardToDesktop() }
        findViewById<Button>(R.id.btnScreenshot).setOnClickListener { saveScreenshot() }
    }

    private fun sendAndroidClipboardToDesktop() {
        val client = rfbClient ?: return
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = clipboard.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).text?.toString() ?: return
        scope.launch {
            try {
                withContext(Dispatchers.IO) { client.sendClientCutText(text) }
                android.widget.Toast.makeText(this@VncActivity, "Clipboard sent", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Log.e("VncActivity", "Clipboard failed: ${e.message}") }
        }
    }

    private fun saveScreenshot() {
        val client = rfbClient ?: return
        try {
            val filename = "EZBox_${System.currentTimeMillis()}.png"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES)
                }
                val uri = contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                uri?.let { contentResolver.openOutputStream(it)?.use { out -> client.bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out) } }
            }
            android.widget.Toast.makeText(this, "Screenshot saved", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { android.widget.Toast.makeText(this, "Screenshot failed", android.widget.Toast.LENGTH_SHORT).show() }
    }

    private fun setupKeyboardInput() {
        btnToggleKeyboard.setOnClickListener {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            if (extraKeysBar.visibility == View.VISIBLE) {
                extraKeysBar.animate().alpha(0f).setDuration(150).withEndAction {
                    extraKeysBar.visibility = View.GONE
                }.start()
                imm.hideSoftInputFromWindow(hiddenInput.windowToken, 0)
            } else {
                extraKeysBar.alpha = 0f
                extraKeysBar.visibility = View.VISIBLE
                extraKeysBar.animate().alpha(1f).setDuration(150).start()
                hiddenInput.requestFocus()
                imm.showSoftInput(hiddenInput, InputMethodManager.SHOW_FORCED)
            }
        }
        hiddenInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (count > 0 && s != null) {
                    val newChars = s.subSequence(start, start + count)
                    for (c in newChars) { if (c == '\n') sendKeysym(0xFF0D) else sendModifiedChar(c) }
                }
            }
            override fun afterTextChanged(s: Editable?) { if (!s.isNullOrEmpty()) s.clear() }
        })
        hiddenInput.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DEL -> { sendKeysym(0xFF08); true }
                    KeyEvent.KEYCODE_ENTER -> { sendKeysym(0xFF0D); true }
                    else -> false
                }
            } else false
        }
        hiddenInput.setOnEditorActionListener { _, _, _ ->
            sendKeysym(0xFF0D)
            hiddenInput.requestFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(hiddenInput, InputMethodManager.SHOW_FORCED)
            true
        }
    }

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
                if (ctrlOn) runOnUiThread { btnCtrl.isChecked = false }
                if (altOn) runOnUiThread { btnAlt.isChecked = false }
            } catch (e: Exception) { Log.e("VncActivity", "Key failed: ${e.message}") }
        }
    }

    private fun sendKeysym(keysym: Int) {
        val client = rfbClient ?: return
        scope.launch {
            try { withContext(Dispatchers.IO) { client.sendKeyEvent(keysym, true); client.sendKeyEvent(keysym, false) } }
            catch (e: Exception) { Log.e("VncActivity", "Key failed: ${e.message}") }
        }
    }

    private fun connectAndRender() {
        val port = intent.getIntExtra("vnc_port", 5901)
        val password = intent.getStringExtra("vnc_password") ?: "ezbox123"
        scope.launch {
            vncStatus.text = "Connecting to EZOS desktop..."
            val client = RfbClient("127.0.0.1", port, password)
            val connected = try { withContext(Dispatchers.IO) { client.connect() } } catch (e: Exception) { false }
            if (!connected) { vncStatus.text = "Failed to connect.\nMake sure the environment is running."; return@launch }
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
                val updated = withContext(Dispatchers.IO) { client.requestFramebufferUpdate(true); client.readServerMessage() }
                if (updated) vncScreen.setImageBitmap(client.bitmap)
            } catch (e: Exception) {
                running = false
                vncStatus.text = "Connection lost.\nTap back and try again."
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

    private fun startPointerSender() {
        scope.launch {
            for (event in pointerChannel) {
                try { withContext(Dispatchers.IO) { rfbClient?.sendPointerEvent(event.first, event.second, event.third) } }
                catch (e: Exception) { Log.e("VncActivity", "Pointer failed: ${e.message}") }
            }
        }
    }

    private fun handleTouch(event: MotionEvent) {
        val client = rfbClient ?: return
        if (mouseMode == "trackpad") handleTrackpadTouch(client, event) else handleDirectTouch(client, event)
    }

    private fun handleDirectTouch(client: RfbClient, event: MotionEvent) {
        val mapped = mapTouchToDesktop(client, event.x, event.y) ?: return
        val buttonMask = when (event.action) { MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> 1; MotionEvent.ACTION_UP -> 0; else -> return }
        pointerChannel.trySend(Triple(mapped.first, mapped.second, buttonMask))
    }

    private fun handleTrackpadTouch(client: RfbClient, event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> { lastTrackpadX = event.x; lastTrackpadY = event.y }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.x - lastTrackpadX).toInt()
                val dy = (event.y - lastTrackpadY).toInt()
                virtualCursorX = (virtualCursorX + dx).coerceIn(0, client.width - 1)
                virtualCursorY = (virtualCursorY + dy).coerceIn(0, client.height - 1)
                lastTrackpadX = event.x; lastTrackpadY = event.y
                pointerChannel.trySend(Triple(virtualCursorX, virtualCursorY, 0))
            }
            MotionEvent.ACTION_UP -> {
                pointerChannel.trySend(Triple(virtualCursorX, virtualCursorY, 1))
                pointerChannel.trySend(Triple(virtualCursorX, virtualCursorY, 0))
            }
        }
    }

    override fun onDestroy() { super.onDestroy(); running = false; rfbClient?.close() }
}
