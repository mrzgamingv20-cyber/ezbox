package com.mrzgaming.ezbox

<<<<<<< HEAD
import android.content.ComponentName
import android.content.Context
import android.content.Intent
=======
>>>>>>> a2c7cb2 (Rename project: EZLauncher -> EZBox)
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment

class HomeFragment : Fragment() {
<<<<<<< HEAD
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
=======

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        
        val launchButton = view.findViewById<Button>(R.id.launchButton)
        launchButton.setOnClickListener {
            launchEzOs("EZOS")
        }
        
        return view
    }

import android.util.Log

// ... inside launchEzOs ...
    private fun launchEzOs(distroName: String) {
        Log.d("EZLauncher", "Attempting to launch: $distroName")
        try {
            val intent = android.content.Intent().apply {
                action = "com.termux.app.RUN_COMMAND"
                component = android.content.ComponentName("com.termux", "com.termux.app.RunCommandService")
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", "ezos-run $distroName"))
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
            }
            requireContext().startService(intent)
            Log.d("EZLauncher", "Intent sent successfully")
        } catch (e: Exception) {
            Log.e("EZLauncher", "Error launching: ${e.message}")
            android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
>>>>>>> a2c7cb2 (Rename project: EZLauncher -> EZBox)
    }
}
