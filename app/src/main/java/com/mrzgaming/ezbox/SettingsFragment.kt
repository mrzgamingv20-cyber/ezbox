package com.mrzgaming.ezbox

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment() {

    private val resolutions = listOf("800x480", "960x540", "1280x720", "1600x900")
    private val mouseModes = listOf("direct", "trackpad")
    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        prefs = requireActivity().getSharedPreferences("EZBoxPrefs", Context.MODE_PRIVATE)

        val spinnerResolution = view.findViewById<Spinner>(R.id.spinnerResolution)
        val spinnerMouseMode = view.findViewById<Spinner>(R.id.spinnerMouseMode)
        val inputPassword = view.findViewById<EditText>(R.id.inputVncPassword)
        val switchKeepAwake = view.findViewById<Switch>(R.id.switchKeepAwake)
        val switchAutoStop = view.findViewById<Switch>(R.id.switchAutoStop)
        val btnResetDesktop = view.findViewById<Button>(R.id.btnResetDesktop)

        spinnerResolution.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, resolutions)
        spinnerMouseMode.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, mouseModes)

        // Load nilai tersimpan
        val savedRes = prefs.getString("resolution", "960x540") ?: "960x540"
        spinnerResolution.setSelection(resolutions.indexOf(savedRes).coerceAtLeast(0))

        val savedMouseMode = prefs.getString("mouse_mode", "direct") ?: "direct"
        spinnerMouseMode.setSelection(mouseModes.indexOf(savedMouseMode).coerceAtLeast(0))

        // Tampilkan sebagai HINT (placeholder abu-abu), bukan teks ter-isi - supaya jelas
        // ini cuma default, bukan seolah-olah user sudah pernah mengatur password sendiri
        val savedPassword = prefs.getString("vnc_password", null)
        if (savedPassword != null) {
            inputPassword.setText(savedPassword)
        } else {
            inputPassword.hint = "Default: ezbox123"
        }
        switchKeepAwake.isChecked = prefs.getBoolean("keep_awake", false)
        switchAutoStop.isChecked = prefs.getBoolean("auto_stop_background", true)

        // Simpan otomatis tiap ada perubahan
        spinnerResolution.setOnItemSelectedListenerCompat { prefs.edit().putString("resolution", resolutions[it]).apply() }
        spinnerMouseMode.setOnItemSelectedListenerCompat { prefs.edit().putString("mouse_mode", mouseModes[it]).apply() }

        inputPassword.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val pw = inputPassword.text.toString().ifBlank { "ezbox123" }
                prefs.edit().putString("vnc_password", pw).apply()
            }
        }

        switchKeepAwake.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("keep_awake", checked).apply()
        }
        switchAutoStop.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("auto_stop_background", checked).apply()
        }

        btnResetDesktop.setOnClickListener { confirmResetDesktop() }

        return view
    }

    private fun confirmResetDesktop() {
        AlertDialog.Builder(requireContext())
            .setTitle("Reset Desktop?")
            .setMessage("This will permanently delete all files, apps, and settings inside your EZOS desktop. This cannot be undone.")
            .setPositiveButton("Reset") { _, _ -> resetDesktop() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun resetDesktop() {
        try {
            val command = "pkill -9 -f 'Xvnc :1 ' 2>/dev/null; rm -rf \$HOME/.ezos"
            val intent = Intent().apply {
                action = "com.termux.RUN_COMMAND"
                component = ComponentName("com.termux", "com.termux.app.RunCommandService")
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            }
            ContextCompat.startForegroundService(requireContext(), intent)
            android.widget.Toast.makeText(context, "Desktop reset. Next launch will set up a fresh environment.", android.widget.Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Reset failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

// Helper kecil supaya listener Spinner lebih ringkas dibanding AdapterView.OnItemSelectedListener penuh
private fun Spinner.setOnItemSelectedListenerCompat(onSelected: (Int) -> Unit) {
    this.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
            onSelected(position)
        }
        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
    }
}
