package com.mrzgaming.ezbox

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import java.io.File

class TerminalFragment : Fragment() {

    private val termuxPackage = "com.termux"
    private val termuxFdroidUrl = "https://f-droid.org/packages/com.termux/"
    private var debugLogPath: String = ""
    private fun resolveDebugLogPath() {
        val dir = try {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        } catch (e: Exception) {
            context?.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: run {
                debugLogPath = ""; return
            }
        }
        debugLogPath = File(dir, "ezbox_debug.log").absolutePath
    }

    private var terminalOutput: TextView? = null
    private val refreshHandler = Handler(Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null
    private val logExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_terminal, container, false)

        terminalOutput = view.findViewById(R.id.terminalOutput)

        view.findViewById<MaterialButton>(R.id.btnOpenTermux).setOnClickListener {
            openTermux()
        }

        view.findViewById<MaterialButton>(R.id.btnRefreshLog).setOnClickListener {
            loadDebugLog()
        }

        loadDebugLog()
        return view
    }

    override fun onResume() {
        super.onResume()
        startAutoRefresh()
    }

    override fun onPause() {
        super.onPause()
        stopAutoRefresh()
    }

    private fun startAutoRefresh() {
        refreshRunnable = object : Runnable {
            override fun run() {
                loadDebugLog()
                refreshHandler.postDelayed(this, 3000)
            }
        }
        refreshHandler.postDelayed(refreshRunnable!!, 3000)
    }

    private fun stopAutoRefresh() {
        refreshRunnable?.let { refreshHandler.removeCallbacks(it) }
    }

    private fun openTermux() {
        val launchIntent = requireContext().packageManager.getLaunchIntentForPackage(termuxPackage)
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            Toast.makeText(context, "Termux is not installed, opening download page...", Toast.LENGTH_SHORT).show()
            try {
                val installIntent = Intent(Intent.ACTION_VIEW, Uri.parse(termuxFdroidUrl))
                startActivity(installIntent)
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open browser: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Reads at most the last [n] lines without loading the whole file. */
    private fun tailLines(file: File, n: Int): String {
        val maxBytes = 64L * 1024
        val start = maxOf(0L, file.length() - maxBytes)
        java.io.RandomAccessFile(file, "r").use { raf ->
            raf.seek(start)
            val bytes = ByteArray((raf.length() - start).toInt())
            raf.readFully(bytes)
            val text = String(bytes, Charsets.UTF_8)
            val lines = text.lines()
            // A partial first line is expected when we started mid-file.
            return lines.drop(if (start > 0) 1 else 0).takeLast(n).joinToString("\n")
        }
    }

    private fun loadDebugLog() {
        // Resolve the path on the main thread first: this getter used to call
        // requireContext() from the executor, which throws IllegalStateException on
        // detach and was swallowed by the catch below, so the pane failed forever.
        resolveDebugLogPath()
        val path = debugLogPath
        logExecutor.execute {
            try {
                val file = File(path)
                val text = if (file.exists()) {
                    // readLines() materialised the whole (unbounded) log before takeLast,
                    // so a large ezbox_debug.log OOM'd the process on a background thread.
                    val tail = tailLines(file, 80)
                    if (tail.isBlank()) "Log is empty." else tail
                } else {
                    "No logs yet."
                }
                refreshHandler.post {
                    if (isAdded) {
                        terminalOutput?.text = text
                        // Auto-scroll ke bawah
                        val scrollView = terminalOutput?.parent as? android.widget.ScrollView
                        scrollView?.fullScroll(View.FOCUS_DOWN)
                    }
                }
            } catch (e: Exception) {
                refreshHandler.post {
                    if (isAdded) terminalOutput?.text = "Failed to read log: ${e.message}"
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopAutoRefresh()
        terminalOutput = null
    }

    override fun onDestroy() {
        super.onDestroy()
        logExecutor.shutdownNow()
    }
}
