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
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_vnc)

        vncScreen = findViewById(R.id.vncScreen)
        vncStatus = findViewById(R.id.vncStatus)
        hiddenInput = findViewById(R.id.hiddenInput)
        btnToggleKeyboard = findViewById(R.id.btnToggleKeyboard)

        connectAndRender()
        setupKeyboardInput()

        vncScreen.setOnTouchListener { _, event ->
            handleTouch(event)
            true
        }
    }

    private fun setupKeyboardInput() {
        btnToggleKeyboard.setOnClickListener {
            hiddenInput.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(hiddenInput, InputMethodManager.SHOW_FORCED)
        }

        // Tangkap karakter yang diketik dan kirim sebagai key event RFB
        hiddenInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (count > 0 && s != null) {
                    val newChars = s.subSequence(start, start + count)
                    for (c in newChars) {
                        if (c == '\n') {
                            sendKeysym(0xFF0D) // Enter, sebagian keyboard kirim newline langsung
                        } else {
                            sendCharKey(c)
                        }
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) {
                // Kosongkan terus supaya EditText tidak menumpuk teks
                if (!s.isNullOrEmpty()) s.clear()
            }
        })

        // Tangkap tombol fisik/hardware: backspace, enter
        hiddenInput.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DEL -> {
                        sendKeysym(0xFF08) // Backspace
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_ENTER -> {
                        sendKeysym(0xFF0D) // Enter
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }

        // Tangkap IME action (tombol Enter/Done pada soft keyboard) supaya tidak menutup keyboard
        hiddenInput.setOnEditorActionListener { _, actionId, event ->
            sendKeysym(0xFF0D) // Enter
            hiddenInput.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(hiddenInput, InputMethodManager.SHOW_FORCED)
            true // konsumsi event supaya keyboard tidak auto-close
        }
    }

    private fun sendCharKey(c: Char) {
        val keysym = c.code
        sendKeysym(keysym)
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
