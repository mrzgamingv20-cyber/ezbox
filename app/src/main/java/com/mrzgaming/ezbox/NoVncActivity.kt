package com.mrzgaming.ezbox

import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity

class NoVncActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        val webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        val url = "file:///android_asset/novnc/index.html?host=localhost&port=6080&password=ezbox123&resize=scale&reconnect=true&reconnect_delay=2000&autoconnect=true"
        webView.loadUrl(url)
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
