package com.mrzgaming.ezbox

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class HomeFragment : Fragment() {

    private val termuxPermission = "com.termux.permission.RUN_COMMAND"
    private val requestCode = 1001

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        view.findViewById<Button>(R.id.btnSetup).setOnClickListener {
            checkPermissionAndLaunch()
        }
        return view
    }

    private fun checkPermissionAndLaunch() {
        if (ContextCompat.checkSelfPermission(requireContext(), termuxPermission) == PackageManager.PERMISSION_GRANTED) {
            setupEnvironment()
        } else {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(termuxPermission), requestCode)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == this.requestCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupEnvironment()
            } else {
                Toast.makeText(context, "Izin Termux RUN_COMMAND ditolak. EZBox butuh izin ini untuk berjalan.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupEnvironment() {
        val prefs = requireActivity().getSharedPreferences("EZBoxPrefs", Context.MODE_PRIVATE)
        val resolution = prefs.getString("resolution", "960x540")

        Log.d("EZBox", "Attempting to launch EZOS with resolution: $resolution")
        try {
            val intent = Intent().apply {
                action = "com.termux.RUN_COMMAND"
                component = ComponentName("com.termux", "com.termux.app.RunCommandService")
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", "EZBOX_RES=$resolution ezos-run EZOS"))
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            }
            requireContext().startService(intent)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                startActivity(android.content.Intent(requireContext(), NoVncActivity::class.java))
            }, 5000)
            Log.d("EZBox", "Intent sent successfully")
        } catch (e: Exception) {
            Log.e("EZBox", "Error launching: ${e.message}")
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
