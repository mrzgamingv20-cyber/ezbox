package com.mrzgaming.ezbox

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment

class HomeFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        view.findViewById<Button>(R.id.btnSetup).setOnClickListener {
            setupEnvironment()
        }
        return view
    }

    private fun setupEnvironment() {
        val prefs = requireActivity().getSharedPreferences("EZBoxPrefs", Context.MODE_PRIVATE)
        val resolution = prefs.getString("resolution", "1280x720")

        val intent = Intent().apply {
            action = "com.termux.app.RUN_COMMAND"
            component = ComponentName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            // Pass resolution as an environment variable or argument
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", "EZBOX_RES=$resolution ezos-run EZOS"))
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
        }
        requireContext().startService(intent)
    }
}
