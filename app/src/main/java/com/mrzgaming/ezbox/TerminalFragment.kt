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
    private val debugLogPath: String
        get() {
            val dir = try {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            } catch (e: Exception) {
                requireContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return ""
            }
            return File(dir, "ezbox_debug.log").absolutePath
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

    private fun loadDebugLog() {
        // Baca file di background thread supaya tidak ANR kalau log besar
        logExecutor.execute {
            try {
                val file = File(debugLogPath)
                val text = if (file.exists()) {
                    val lines = file.readLines()
                    val recent = lines.takeLast(80).joinToString("\n")
                    if (recent.isBlank()) "Log is empty." else recent
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
    }
}
