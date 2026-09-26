package com.mrzgaming.ezbox

import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity

class NoVncActivity : AppCompatActivity() {

    // Held as a field so onDestroy can reach it. As a local val it was impossible to
    // destroy, so the reconnect=true JS loop kept polling every 2s forever.
    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        val prefs = getSharedPreferences("EZBoxPrefs", MODE_PRIVATE)
        val vncPassword = prefs.getString("vnc_password", "ezbox123")
        val port = intent.getIntExtra("vnc_port", 6080)

        val wv = WebView(this)
        webView = wv
        setContentView(wv)

        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        val url = "file:///android_asset/novnc/index.html?host=localhost&port=$port&password=$vncPassword&resize=scale&reconnect=true&reconnect_delay=2000&autoconnect=true"
        wv.loadUrl(url)
    }

    override fun onPause() {
        super.onPause()
        webView?.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }

    override fun onDestroy() {
        webView?.apply {
            stopLoading()
            loadUrl("about:blank")
            clearHistory()
            removeAllViews()
            destroy()
        }
        webView = null
        super.onDestroy()
    }
}
