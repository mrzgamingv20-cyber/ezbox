package com.mrzgaming.ezbox

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.InputDevice
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mrzgaming.ezbox.EZBoxNotificationManager

class VncActivity : AppCompatActivity() {
    private lateinit var vncScreen: ImageView
    private lateinit var vncStatus: TextView
    private lateinit var vncStatusIcon: TextView
    private lateinit var vncStatusSpinner: android.widget.ProgressBar
    private lateinit var btnRetryConnection: Button
    private lateinit var hiddenInput: EditText
    private lateinit var btnToggleKeyboard: Button
    private lateinit var btnStopDesktop: Button
    private lateinit var extraKeysPanel: android.widget.FrameLayout
    private lateinit var btnEditKeys: Button
    private lateinit var typingPreviewBar: TextView
    private var typedBuffer = StringBuilder()
    private var rfbClient: RfbClient? = null
    private var running = false
    private val notificationManager = EZBoxNotificationManager(this)
    private var gamepadConnected = false
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var mouseMode = "direct"
    private var lastTrackpadX = 0f
    private var lastTrackpadY = 0f
    private lateinit var extraKeys: ExtraKeysPanel
    private var virtualCursorX = 0
    private var virtualCursorY = 0

    // Gestures: tap = left click, double tap = right click, hold = drag,
    // two-finger = scroll wheel. See handleDirectTouch / handleTrackpadTouch.
    private var longPressHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var twoFingerStartY = 0f
    private var isTwoFinger = false
    private var isDragging = false
    private var isSecondTap = false
    private var movedBeyondSlop = false
    private var gestureStartX = 0f
    private var gestureStartY = 0f
    private var lastTapUpTime = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var pendingTapRunnable: Runnable? = null
    private var tapSlop = 0f
    private val tapTimeoutMs = 300L
    private val longPressMs = 400L

    // Advanced VNC preferences, dibaca sekali saat onCreate dari SettingsFragment
    private var viewOnlyMode = false
    private var disableClipboard = false
    private var lowBandwidthMode = false
    private var vsyncEnabled = true

    private val KEY_CTRL_L = 0xFFE3
    private val KEY_ALT_L = 0xFFE9
    private val KEY_ENTER = 0xFF0D
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

    // UNLIMITED, not CONFLATED: a conflated channel keeps exactly one element, so the
    // press/release pair sent back-to-back lost the press and every tap, scroll and
    // long-press right-click reached the server as a release with no button held.
    private val pointerChannel = Channel<Triple<Int, Int, Int>>(Channel.UNLIMITED)

    private val termuxErrorListener: (String) -> Unit = { showTermuxError(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TermuxCommand.onError(termuxErrorListener)
        supportActionBar?.hide()
        setContentView(R.layout.activity_vnc)
        val prefs = getSharedPreferences("EZBoxPrefs", MODE_PRIVATE)
        mouseMode = intent.getStringExtra("mouse_mode") ?: prefs.getString("mouse_mode", "trackpad") ?: "trackpad"
        if (prefs.getBoolean("keep_awake", false)) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        viewOnlyMode = prefs.getBoolean("view_only_mode", false)
        disableClipboard = prefs.getBoolean("disable_clipboard", false)
        lowBandwidthMode = prefs.getBoolean("low_bandwidth_mode", false)
        vsyncEnabled = prefs.getBoolean("graphics_vsync", true)

        vncScreen = findViewById(R.id.vncScreen)
        vncStatus = findViewById(R.id.vncStatus)
        vncStatusIcon = findViewById(R.id.vncStatusIcon)
        vncStatusSpinner = findViewById(R.id.vncStatusSpinner)
        btnRetryConnection = findViewById(R.id.btnRetryConnection)
        btnRetryConnection.setOnClickListener {
            showLoadingState("Reconnecting to desktop...")
            connectAndRender()
        }
        hiddenInput = findViewById(R.id.hiddenInput)
        btnToggleKeyboard = findViewById(R.id.btnToggleKeyboard)
        btnStopDesktop = findViewById(R.id.btnStopDesktop)
        extraKeysPanel = findViewById(R.id.extraKeysPanel)
        btnEditKeys = findViewById(R.id.btnEditKeys)
        typingPreviewBar = findViewById(R.id.typingPreviewBar)

        btnStopDesktop.setOnClickListener { stopDesktop() }
        btnEditKeys.setOnClickListener { editExtraKeys() }

        connectAndRender()
        notificationManager.showRunningNotification(intent.getStringExtra("container_name") ?: "EZBox")
        setupKeyboardInput()
        extraKeys = ExtraKeysPanel(this, prefs, extraKeysPanel) { k -> sendKey(k) }
        extraKeys.load()
        extraKeys.render()
        setupClipboardAndScreenshot()
        startPointerSender()

        applyViewOnlyMode()

        tapSlop = android.view.ViewConfiguration.get(this).scaledTouchSlop.toFloat()
        vncScreen.setOnTouchListener { _, event ->
            if (!viewOnlyMode) handleTouch(event)
            true
        }
    }

    /**
     * View-only mode: tidak ada input yang boleh dikirim ke desktop sama sekali.
     * Sembunyikan total tombol keyboard dan toolbar extra keys, supaya tidak ada
     * cara bagi user untuk mencoba mengirim input yang toh akan diabaikan.
     */
    private fun applyViewOnlyMode() {
        if (viewOnlyMode) {
            btnToggleKeyboard.visibility = View.GONE
            btnEditKeys.visibility = View.GONE
            extraKeysPanel.visibility = View.GONE
        }
    }

    // Toggle toolbar SAJA tanpa animasi margin - layout_above di XML handle otomatis
    // supaya user bisa akses Ctrl/Alt/Esc/dll tanpa harus buka keyboard sekaligus
    /** Menu tombol "Keys": tambah tombol, atau bersihkan panel. */
    private fun editExtraKeys() {
        if (extraKeys.isEmpty) {
            extraKeys.showPicker { extraKeys.addKey(it) }
            return
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Custom keys")
            .setItems(arrayOf("Add key", "Delete all keys")) { _, which ->
                when (which) {
                    0 -> extraKeys.showPicker { extraKeys.addKey(it) }
                    1 -> extraKeys.clearAll()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTermuxError(message: String) {
        if (isFinishing || isDestroyed) return
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            val setupMissing = TermuxCommand.needsTermuxSetup(message)
            val text = if (setupMissing) {
                "EZBox cannot run commands in Termux.\n\n" +
                    "Open ~/.termux/termux.properties and add:\n\n" +
                    "allow-external-apps = true\n\n" +
                    "Then force-stop Termux and try again."
            } else {
                "Termux command failed:\n\n$message"
            }
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(if (setupMissing) "Termux is not ready" else "Termux error")
                .setMessage(text)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun stopDesktop() {
        val command = "pkill -9 -f 'Xvnc :1 '; pkill -9 -f 'xfce4-session'; pkill -9 -f 'startlxqt'; echo done"
        try {
            TermuxCommand.start(this, command)
        } catch (e: Exception) {
            Log.e("VncActivity", "Stop desktop failed: ${e.message}")
        } finally {
            running = false
            rfbClient?.close()
            notificationManager.cancel()
            finish()
        }
    }

    private fun setupClipboardAndScreenshot() {
        val btnClipboard = findViewById<Button>(R.id.btnClipboard)
        if (disableClipboard) {
            btnClipboard.visibility = View.GONE
        } else {
            btnClipboard.setOnClickListener { sendAndroidClipboardToDesktop() }
        }
        findViewById<Button>(R.id.btnScreenshot).setOnClickListener { saveScreenshot() }
    }

    private fun sendAndroidClipboardToDesktop() {
        if (disableClipboard) return
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
        scope.launch {
            try {
                val filename = "EZBox_${System.currentTimeMillis()}.png"
                val bitmap = client.bitmap
                withContext(Dispatchers.IO) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        val values = android.content.ContentValues().apply {
                            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
                            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES)
                        }
                        val uri = contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        uri?.let { contentResolver.openOutputStream(it)?.use { out -> bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out) } }
                    } else {
                        val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
                        if (!dir.exists()) dir.mkdirs()
                        val file = java.io.File(dir, filename)
                        java.io.FileOutputStream(file).use { out -> bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out) }
                        android.media.MediaScannerConnection.scanFile(this@VncActivity, arrayOf(file.absolutePath), arrayOf("image/png"), null)
                    }
                }
                runOnUiThread { android.widget.Toast.makeText(this@VncActivity, "Screenshot saved", android.widget.Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) { runOnUiThread { android.widget.Toast.makeText(this@VncActivity, "Screenshot failed", android.widget.Toast.LENGTH_SHORT).show() } }
        }
    }

    private fun setupKeyboardInput() {
        if (viewOnlyMode) return

        // Murni toggle soft keyboard SAJA - tidak menyentuh extraKeysBar sama sekali.
        // extraKeysBar exclusive dipicu oleh btnExpandKeys (toggleExtraKeysBar()).
        var keyboardShown = false
        btnToggleKeyboard.setOnClickListener {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            if (keyboardShown) {
                imm.hideSoftInputFromWindow(hiddenInput.windowToken, 0)
                typingPreviewBar.visibility = View.GONE
                keyboardShown = false
            } else {
                hiddenInput.requestFocus()
                imm.showSoftInput(hiddenInput, InputMethodManager.SHOW_FORCED)
                typedBuffer.clear()
                typingPreviewBar.text = ""
                typingPreviewBar.visibility = View.VISIBLE
                keyboardShown = true
            }
        }
        hiddenInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (count > 0 && s != null) {
                    val newChars = s.subSequence(start, start + count)
                    for (c in newChars) {
                        if (c == '\n') {
                            sendKeysym(0xFF0D)
                            typedBuffer.clear()
                        } else {
                            sendModifiedChar(c)
                            typedBuffer.append(c)
                            if (typedBuffer.length > 60) typedBuffer.delete(0, typedBuffer.length - 60)
                        }
                    }
                    typingPreviewBar.text = typedBuffer.toString()
                }
            }
            override fun afterTextChanged(s: Editable?) { if (!s.isNullOrEmpty()) s.clear() }
        })
        hiddenInput.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DEL -> {
                        sendKeysym(0xFF08)
                        if (typedBuffer.isNotEmpty()) typedBuffer.deleteCharAt(typedBuffer.length - 1)
                        typingPreviewBar.text = typedBuffer.toString()
                        true
                    }
                    KeyEvent.KEYCODE_ENTER -> { sendKeysym(0xFF0D); typedBuffer.clear(); typingPreviewBar.text = ""; true }
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
        if (viewOnlyMode) return
        val client = rfbClient ?: return
        val ctrlOn = extraKeys.isModActive("ctrl")
        val altOn = extraKeys.isModActive("alt")
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
                if (ctrlOn || altOn) runOnUiThread { extraKeys.clearMods() }
            } catch (e: Exception) { Log.e("VncActivity", "Key failed: ${e.message}") }
        }
    }

    private fun sendKeysym(keysym: Int) {
        if (viewOnlyMode) return
        val client = rfbClient ?: return
        scope.launch {
            try { withContext(Dispatchers.IO) { client.sendKeyEvent(keysym, true); client.sendKeyEvent(keysym, false) } }
            catch (e: Exception) { Log.e("VncActivity", "Key failed: ${e.message}") }
        }
    }

    private fun showLoadingState(message: String) {
        if (isFinishing || isDestroyed) return
        vncStatusSpinner.visibility = View.VISIBLE
        vncStatusIcon.visibility = View.GONE
        btnRetryConnection.visibility = View.GONE
        vncStatus.text = message
        vncStatus.visibility = View.VISIBLE
        findViewById<View>(R.id.vncStatusCard).visibility = View.VISIBLE
    }

    private fun showErrorState(message: String) {
        if (isFinishing || isDestroyed) return
        vncStatusSpinner.visibility = View.GONE
        vncStatusIcon.visibility = View.VISIBLE
        (vncStatusIcon as TextView).setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_triangle_alert, 0, 0, 0)
        btnRetryConnection.visibility = View.VISIBLE
        vncStatus.text = message
        findViewById<View>(R.id.vncStatusCard).visibility = View.VISIBLE
    }

    private fun hideStatusCard() {
        if (isFinishing || isDestroyed) return
        findViewById<View>(R.id.vncStatusCard).visibility = View.GONE
    }

    private fun connectAndRender() {
        val port = intent.getIntExtra("vnc_port", 5901)
        val password = intent.getStringExtra("vnc_password") ?: "ezbox123"
        scope.launch {
            connectWithRetry(port, password)
        }
    }

    private suspend fun connectWithRetry(port: Int, password: String) {
        var retryCount = 0
        val maxRetries = 5
        while (retryCount < maxRetries) {
            showLoadingState("Connecting to desktop...${if (retryCount > 0) " (retry ${retryCount}/${maxRetries})" else ""}")
            val client = RfbClient("127.0.0.1", port, password)
            val connected = try { withContext(Dispatchers.IO) { client.connect() } } catch (e: Exception) { false }
            if (connected) {
                rfbClient = client
                virtualCursorX = client.width / 2
                virtualCursorY = client.height / 2
                hideStatusCard()
                running = true
                renderLoop(client)
                return
            }
            // Leaked one socket + two 64KB buffers per retry otherwise.
            try { client.close() } catch (_: Exception) {}
            retryCount++
            if (retryCount < maxRetries) {
                delay((retryCount * 2000L).coerceAtMost(10000L))
            }
        }
        if (!isFinishing && !isDestroyed) showErrorState("Failed to connect after ${maxRetries} attempts.\nTap retry to try again.")
    }

    private fun sendKey(k: ExtraKey) {
        if (viewOnlyMode) return
        if (k.mod != null) {
            extraKeys.toggleMod(k.mod)
            return
        }
        if (k.char != null) {
            sendModifiedChar(k.char[0])
            return
        }
        if (k.keysym != 0) {
            sendKeysym(k.keysym)
            return
        }
    }

    private fun showRetryButton() {
        if (isFinishing || isDestroyed) return
        btnRetryConnection.visibility = View.VISIBLE
    }

    /**
     * Low bandwidth mode: tambahkan jeda antar permintaan framebuffer update,
     * jadi request ke server lebih jarang (hemat data), bukan cuma skip render lokal.
     * Normal: tanpa jeda (secepat mungkin). Low bandwidth: ~10fps (100ms jeda).
     */
    private suspend fun renderLoop(client: RfbClient) {
        val frameDelayMs = when {
            lowBandwidthMode -> 100L
            !vsyncEnabled -> 0L  // unlimited: vsync off means no frame cap
            else -> 16L
        }  // renderLoop: vsync on = ~60fps cap, vsync off = unlimited, low bandwidth = ~10fps
        // Set bitmap sekali saja, setelahnya cukup invalidate karena bitmap di-mutate in-place
        vncScreen.setImageBitmap(client.bitmap)
        while (running) {
            try {
                val updated = withContext(Dispatchers.IO) { client.requestFramebufferUpdate(true); client.readServerMessage() }
                // `if (updated)` already skips the redraw for untouched frames, which is
                // what graphics_vsync promises, so no extra branch is needed.
                if (updated && !isFinishing && !isDestroyed) vncScreen.invalidate()
                // Sync clipboard dari desktop ke Android
                if (!disableClipboard && !isFinishing && !isDestroyed) {
                    client.serverClipboardText?.let { text ->
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("EZBox Desktop", text)
                        clipboard.setPrimaryClip(clip)
                        client.clearServerClipboard()
                    }
                }
                if (frameDelayMs > 0) delay(frameDelayMs)
            } catch (e: kotlinx.coroutines.CancellationException) {
                // delay() throws this on scope cancel; it is not a lost connection.
                throw e
            } catch (e: Exception) {
                running = false
                if (!isFinishing && !isDestroyed) showErrorState("Connection lost.\nTap retry to reconnect.")
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

    /** Reset per-gesture state on finger-down and detect a double tap. */
    private fun onGestureStart(x: Float, y: Float) {
        cancelLongPress()
        gestureStartX = x
        gestureStartY = y
        movedBeyondSlop = false
        isDragging = false
        val now = android.os.SystemClock.uptimeMillis()
        isSecondTap = now - lastTapUpTime < tapTimeoutMs &&
            Math.abs(x - lastTapX) < tapSlop * 5 &&
            Math.abs(y - lastTapY) < tapSlop * 5
    }

    private fun tapMoved(x: Float, y: Float) =
        Math.abs(x - gestureStartX) > tapSlop || Math.abs(y - gestureStartY) > tapSlop

    /** Finger-up that was neither a drag nor a big move: a tap. */
    private fun onTap(mx: Int, my: Int, x: Float, y: Float) {
        if (isSecondTap) {
            cancelPendingTap()
            isSecondTap = false
            lastTapUpTime = 0L
            pointerChannel.trySend(Triple(mx, my, 4))  // right button down
            pointerChannel.trySend(Triple(mx, my, 0))
        } else {
            schedulePendingTap(mx, my)
            lastTapUpTime = android.os.SystemClock.uptimeMillis()
            lastTapX = x
            lastTapY = y
        }
    }

    /**
     * The left click waits tapTimeoutMs so a second tap can cancel it and turn it
     * into a right click instead. The same window is the double-tap window, so the
     * two can never both fire for one gesture pair.
     */
    private fun schedulePendingTap(mx: Int, my: Int) {
        cancelPendingTap()
        pendingTapRunnable = Runnable {
            pendingTapRunnable = null
            pointerChannel.trySend(Triple(mx, my, 1))
            pointerChannel.trySend(Triple(mx, my, 0))
        }
        longPressHandler.postDelayed(pendingTapRunnable!!, tapTimeoutMs)
    }

    private fun cancelPendingTap() {
        pendingTapRunnable?.let { longPressHandler.removeCallbacks(it) }
        pendingTapRunnable = null
    }

    /** Fire a waiting tap now, so it lands before a drag takes over the button. */
    private fun flushPendingTap() {
        val r = pendingTapRunnable ?: return
        longPressHandler.removeCallbacks(r)
        pendingTapRunnable = null
        r.run()
    }

    /** Left button down: the drag begins (from hold or from a finger slide). */
    private fun beginDrag(mx: Int, my: Int) {
        if (isDragging) return
        flushPendingTap()
        isDragging = true
        isSecondTap = false
        pointerChannel.trySend(Triple(mx, my, 1))
    }

    private fun handleTouch(event: MotionEvent) {
        val client = rfbClient ?: return
        if (mouseMode == "trackpad") handleTrackpadTouch(client, event) else handleDirectTouch(client, event)
    }

    private fun handleDirectTouch(client: RfbClient, event: MotionEvent) {
        // Two-finger scroll wheel
        if (event.pointerCount == 2) {
            isTwoFinger = true
            cancelLongPress()
            flushPendingTap()
            if (isDragging) {
                isDragging = false
                mapTouchToDesktop(client, event.x, event.y)?.let { (mx, my) ->
                    pointerChannel.trySend(Triple(mx, my, 0))
                }
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_POINTER_DOWN -> {
                    twoFingerStartY = (event.getY(0) + event.getY(1)) / 2f
                }
                MotionEvent.ACTION_MOVE -> {
                    val currentY = (event.getY(0) + event.getY(1)) / 2f
                    val deltaY = currentY - twoFingerStartY
                    val mapped = mapTouchToDesktop(client, event.x, event.y) ?: return
                    if (deltaY < -30f) {
                        // Scroll up (button 4)
                        pointerChannel.trySend(Triple(mapped.first, mapped.second, 8))
                        pointerChannel.trySend(Triple(mapped.first, mapped.second, 0))
                        twoFingerStartY = currentY
                    } else if (deltaY > 30f) {
                        // Scroll down (button 5)
                        pointerChannel.trySend(Triple(mapped.first, mapped.second, 16))
                        pointerChannel.trySend(Triple(mapped.first, mapped.second, 0))
                        twoFingerStartY = currentY
                    }
                }
            }
            return
        }

        if (event.actionMasked == MotionEvent.ACTION_UP && isTwoFinger) {
            isTwoFinger = false
            return
        }

        val mapped = mapTouchToDesktop(client, event.x, event.y) ?: return
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                onGestureStart(event.x, event.y)
                // Hold still: left button goes down, drag begins.
                longPressRunnable = Runnable {
                    if (!movedBeyondSlop) beginDrag(mapped.first, mapped.second)
                }
                longPressHandler.postDelayed(longPressRunnable!!, longPressMs)
            }
            MotionEvent.ACTION_MOVE -> {
                if (tapMoved(event.x, event.y) && !movedBeyondSlop) {
                    movedBeyondSlop = true
                    cancelLongPress()
                    // Sliding the finger carries the button, as before: drag.
                    beginDrag(mapped.first, mapped.second)
                }
                if (isDragging) {
                    pointerChannel.trySend(Triple(mapped.first, mapped.second, 1))
                }
            }
            MotionEvent.ACTION_UP -> {
                cancelLongPress()
                if (isDragging) {
                    pointerChannel.trySend(Triple(mapped.first, mapped.second, 0))
                    isDragging = false
                } else {
                    onTap(mapped.first, mapped.second, event.x, event.y)
                }
            }
            // Without this a cancelled gesture (incoming call, system edge-swipe) leaves
            // button 1 held down on the remote desktop indefinitely.
            MotionEvent.ACTION_CANCEL -> {
                cancelLongPress()
                if (isDragging) pointerChannel.trySend(Triple(mapped.first, mapped.second, 0))
                isDragging = false
                isSecondTap = false
                isTwoFinger = false
            }
        }
    }

    private fun cancelLongPress() {
        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
        longPressRunnable = null
    }

    private fun handleTrackpadTouch(client: RfbClient, event: MotionEvent) {
        // Two-finger scroll in trackpad mode
        if (event.pointerCount == 2) {
            isTwoFinger = true
            cancelLongPress()
            flushPendingTap()
            when (event.actionMasked) {
                MotionEvent.ACTION_POINTER_DOWN -> {
                    twoFingerStartY = (event.getY(0) + event.getY(1)) / 2f
                }
                MotionEvent.ACTION_MOVE -> {
                    val currentY = (event.getY(0) + event.getY(1)) / 2f
                    val deltaY = currentY - twoFingerStartY
                    if (deltaY < -30f) {
                        pointerChannel.trySend(Triple(virtualCursorX, virtualCursorY, 8))
                        pointerChannel.trySend(Triple(virtualCursorX, virtualCursorY, 0))
                        twoFingerStartY = currentY
                    } else if (deltaY > 30f) {
                        pointerChannel.trySend(Triple(virtualCursorX, virtualCursorY, 16))
                        pointerChannel.trySend(Triple(virtualCursorX, virtualCursorY, 0))
                        twoFingerStartY = currentY
                    }
                }
            }
            return
        }

        if (event.actionMasked == MotionEvent.ACTION_UP && isTwoFinger) {
            isTwoFinger = false
            return
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                onGestureStart(event.x, event.y)
                lastTrackpadX = event.x; lastTrackpadY = event.y
                // Hold still: left button goes down, cursor moves then drag the desktop.
                longPressRunnable = Runnable {
                    if (!movedBeyondSlop) beginDrag(virtualCursorX, virtualCursorY)
                }
                longPressHandler.postDelayed(longPressRunnable!!, longPressMs)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.x - lastTrackpadX).toInt()
                val dy = (event.y - lastTrackpadY).toInt()
                lastTrackpadX = event.x; lastTrackpadY = event.y
                if (tapMoved(event.x, event.y) && !movedBeyondSlop) {
                    movedBeyondSlop = true
                    cancelLongPress()
                }
                virtualCursorX = (virtualCursorX + dx).coerceIn(0, client.width - 1)
                virtualCursorY = (virtualCursorY + dy).coerceIn(0, client.height - 1)
                pointerChannel.trySend(Triple(virtualCursorX, virtualCursorY, if (isDragging) 1 else 0))
            }
            MotionEvent.ACTION_UP -> {
                cancelLongPress()
                if (isDragging) {
                    pointerChannel.trySend(Triple(virtualCursorX, virtualCursorY, 0))
                    isDragging = false
                } else if (!movedBeyondSlop) {
                    onTap(virtualCursorX, virtualCursorY, event.x, event.y)
                }
                // Moved without holding: cursor reposition only, no click.
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelLongPress()
                if (isDragging) pointerChannel.trySend(Triple(virtualCursorX, virtualCursorY, 0))
                isDragging = false
                isSecondTap = false
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> sendKeysym(KEY_UP)
            KeyEvent.KEYCODE_DPAD_DOWN -> sendKeysym(KEY_DOWN)
            KeyEvent.KEYCODE_DPAD_LEFT -> sendKeysym(KEY_LEFT)
            KeyEvent.KEYCODE_DPAD_RIGHT -> sendKeysym(KEY_RIGHT)
            KeyEvent.KEYCODE_ENTER -> sendKeysym(KEY_ENTER)
            KeyEvent.KEYCODE_BUTTON_A -> sendKeysym(KEY_ENTER)
            KeyEvent.KEYCODE_BUTTON_B -> sendKeysym(KEY_ESC)
            KeyEvent.KEYCODE_BUTTON_X -> sendKeysym(KEY_TAB)
            KeyEvent.KEYCODE_BUTTON_Y -> sendKeysym(KEY_F1)
            KeyEvent.KEYCODE_BUTTON_L1 -> sendKeysym(KEY_CTRL_L)
            KeyEvent.KEYCODE_BUTTON_R1 -> sendKeysym(KEY_ALT_L)
            KeyEvent.KEYCODE_BACK -> return false
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        if (event?.source?.and(InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
            val x = event.getAxisValue(MotionEvent.AXIS_X)
            val y = event.getAxisValue(MotionEvent.AXIS_Y)
            val rx = event.getAxisValue(MotionEvent.AXIS_Z)
            val ry = event.getAxisValue(MotionEvent.AXIS_RZ)
            if (Math.abs(y) > 0.5f) sendKeysym(if (y < 0) KEY_UP else KEY_DOWN)
            if (Math.abs(x) > 0.5f) sendKeysym(if (x < 0) KEY_LEFT else KEY_RIGHT)
            if (Math.abs(rx) > 0.5f) sendKeysym(KEY_F1)
            if (Math.abs(ry) > 0.5f) sendKeysym(KEY_F2)
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        running = false
        cancelLongPress()
        cancelPendingTap()
        TermuxCommand.removeErrorListener(termuxErrorListener)
        rfbClient?.close()
        scope.coroutineContext[Job]?.cancel()
        notificationManager.cancel()
    }
}
