package com.mrzgaming.ezbox

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
    private val debugLogPath = "/storage/emulated/0/Download/ezbox_debug.log"

    private var terminalOutput: TextView? = null

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

    private fun openTermux() {
        val launchIntent = requireContext().packageManager.getLaunchIntentForPackage(termuxPackage)
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            Toast.makeText(context, "Termux belum terpasang, membuka halaman download...", Toast.LENGTH_SHORT).show()
            val installIntent = Intent(Intent.ACTION_VIEW, Uri.parse(termuxFdroidUrl))
            startActivity(installIntent)
        }
    }

    private fun loadDebugLog() {
        try {
            val file = File(debugLogPath)
            if (file.exists()) {
                val lines = file.readLines()
                val recent = lines.takeLast(50).joinToString("\n")
                terminalOutput?.text = if (recent.isBlank()) "Log kosong." else recent
            } else {
                terminalOutput?.text = "Belum ada log."
            }
        } catch (e: Exception) {
            terminalOutput?.text = "Gagal membaca log: ${e.message}"
        }
    }
}
