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
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment() {

    private val mouseModes = listOf("direct", "trackpad")
    private lateinit var prefs: android.content.SharedPreferences

    private val stepAmount = 20
    private val minWidth = 640
    private val maxWidth = 1920
    private val minHeight = 360
    private val maxHeight = 1080

    private var currentWidth = 960
    private var currentHeight = 540
    private var currentDe = "xfce"

    private lateinit var tvWidthValue: TextView
    private lateinit var tvHeightValue: TextView
    private lateinit var deXfceCard: LinearLayout
    private lateinit var deLxqtCard: LinearLayout
    private lateinit var checkDeXfce: TextView
    private lateinit var checkDeLxqt: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        prefs = requireActivity().getSharedPreferences("EZBoxPrefs", Context.MODE_PRIVATE)

        tvWidthValue = view.findViewById(R.id.tvWidthValue)
        tvHeightValue = view.findViewById(R.id.tvHeightValue)
        deXfceCard = view.findViewById(R.id.deXfceCard)
        deLxqtCard = view.findViewById(R.id.deLxqtCard)
        checkDeXfce = view.findViewById(R.id.checkDeXfce)
        checkDeLxqt = view.findViewById(R.id.checkDeLxqt)

        val spinnerMouseMode = view.findViewById<Spinner>(R.id.spinnerMouseMode)
        val inputPassword = view.findViewById<EditText>(R.id.inputVncPassword)
        val switchKeepAwake = view.findViewById<Switch>(R.id.switchKeepAwake)
        val switchAutoStop = view.findViewById<Switch>(R.id.switchAutoStop)
        val btnResetDesktop = view.findViewById<Button>(R.id.btnResetDesktop)

        val checkViewOnly = view.findViewById<CheckBox>(R.id.checkViewOnly)
        val checkDisableClipboard = view.findViewById<CheckBox>(R.id.checkDisableClipboard)
        val checkLowBandwidth = view.findViewById<CheckBox>(R.id.checkLowBandwidth)

        spinnerMouseMode.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, mouseModes)

        val savedRes = prefs.getString("resolution", "960x540") ?: "960x540"
        val parts = savedRes.split("x")
        if (parts.size == 2) {
            currentWidth = parts[0].toIntOrNull() ?: 960
            currentHeight = parts[1].toIntOrNull() ?: 540
        }
        updateResolutionDisplay()

        currentDe = prefs.getString("desktop_environment", "xfce") ?: "xfce"
        updateDeSelection()

        val savedMouseMode = prefs.getString("mouse_mode", "direct") ?: "direct"
        spinnerMouseMode.setSelection(mouseModes.indexOf(savedMouseMode).coerceAtLeast(0))

        val savedPassword = prefs.getString("vnc_password", null)
        if (savedPassword != null) {
            inputPassword.setText(savedPassword)
        } else {
            inputPassword.hint = "Default: ezbox123"
        }
        switchKeepAwake.isChecked = prefs.getBoolean("keep_awake", false)
        switchAutoStop.isChecked = prefs.getBoolean("auto_stop_background", true)

        checkViewOnly.isChecked = prefs.getBoolean("view_only_mode", false)
        checkDisableClipboard.isChecked = prefs.getBoolean("disable_clipboard", false)
        checkLowBandwidth.isChecked = prefs.getBoolean("low_bandwidth_mode", false)

        view.findViewById<Button>(R.id.btnWidthMinus).setOnClickListener {
            currentWidth = (currentWidth - stepAmount).coerceAtLeast(minWidth)
            updateResolutionDisplay()
            saveResolution()
        }
        view.findViewById<Button>(R.id.btnWidthPlus).setOnClickListener {
            currentWidth = (currentWidth + stepAmount).coerceAtMost(maxWidth)
            updateResolutionDisplay()
            saveResolution()
        }
        view.findViewById<Button>(R.id.btnHeightMinus).setOnClickListener {
            currentHeight = (currentHeight - stepAmount).coerceAtLeast(minHeight)
            updateResolutionDisplay()
            saveResolution()
        }
        view.findViewById<Button>(R.id.btnHeightPlus).setOnClickListener {
            currentHeight = (currentHeight + stepAmount).coerceAtMost(maxHeight)
            updateResolutionDisplay()
            saveResolution()
        }

        deXfceCard.setOnClickListener {
            currentDe = "xfce"
            prefs.edit().putString("desktop_environment", currentDe).apply()
            updateDeSelection()
        }
        deLxqtCard.setOnClickListener {
            currentDe = "lxqt"
            prefs.edit().putString("desktop_environment", currentDe).apply()
            updateDeSelection()
        }

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

        checkViewOnly.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("view_only_mode", checked).apply()
        }
        checkDisableClipboard.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("disable_clipboard", checked).apply()
        }
        checkLowBandwidth.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("low_bandwidth_mode", checked).apply()
        }

        btnResetDesktop.setOnClickListener { confirmResetDesktop() }

        return view
    }

    private fun updateDeSelection() {
        val selectedBg = R.drawable.de_card_selected_bg
        val unselectedBg = R.drawable.de_card_unselected_bg

        deXfceCard.setBackgroundResource(if (currentDe == "xfce") selectedBg else unselectedBg)
        deLxqtCard.setBackgroundResource(if (currentDe == "lxqt") selectedBg else unselectedBg)

        checkDeXfce.text = if (currentDe == "xfce") "✓" else ""
        checkDeLxqt.text = if (currentDe == "lxqt") "✓" else ""
    }

    private fun updateResolutionDisplay() {
        tvWidthValue.text = currentWidth.toString()
        tvHeightValue.text = currentHeight.toString()
    }

    private fun saveResolution() {
        prefs.edit().putString("resolution", "${currentWidth}x${currentHeight}").apply()
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

private fun Spinner.setOnItemSelectedListenerCompat(onSelected: (Int) -> Unit) {
    this.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
            onSelected(position)
        }
        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
    }
}
