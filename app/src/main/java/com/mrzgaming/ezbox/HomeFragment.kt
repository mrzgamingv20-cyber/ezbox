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
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {

    private val termuxPermission = "com.termux.permission.RUN_COMMAND"
    private val requestCode = 1001
    private val launchHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var launchRunnable: Runnable? = null
    private var tvStatus: TextView? = null
    private var progressLaunch: ProgressBar? = null
    private var btnSetup: Button? = null

    private fun setStatus(text: String, loading: Boolean) {
        tvStatus?.text = text
        progressLaunch?.visibility = if (loading) View.VISIBLE else View.GONE
        btnSetup?.isEnabled = !loading
    }

    private fun debugLog(msg: String) {
        try {
            val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            val file = File("/storage/emulated/0/Download/ezbox_debug.log")
            file.appendText("[$ts] $msg\n")
        } catch (e: Exception) {
            Log.e("EZBox", "debugLog failed: ${e.message}")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        tvStatus = view.findViewById(R.id.tvStatus)
        progressLaunch = view.findViewById(R.id.progressLaunch)
        btnSetup = view.findViewById(R.id.btnSetup)
        btnSetup?.setOnClickListener {
            debugLog("Button clicked")
            setStatus("Checking permission...", true)
            checkPermissionAndLaunch()
        }

        val sessionCount = SessionManager(requireContext()).getAllSessions().size
        view.findViewById<android.widget.TextView>(R.id.tvSessionCount)?.text = sessionCount.toString()

        view.findViewById<View>(R.id.quickSessions)?.setOnClickListener {
            (activity as? MainActivity)?.navigateTo(R.id.nav_sessions)
        }
        view.findViewById<View>(R.id.quickStore)?.setOnClickListener {
            (activity as? MainActivity)?.navigateTo(R.id.nav_store)
        }
        view.findViewById<View>(R.id.quickTerminal)?.setOnClickListener {
            (activity as? MainActivity)?.navigateTo(R.id.nav_terminal)
        }

        return view
    }

    private fun checkPermissionAndLaunch() {
        val granted = ContextCompat.checkSelfPermission(requireContext(), termuxPermission) == PackageManager.PERMISSION_GRANTED
        debugLog("Permission granted: $granted")
        if (granted) {
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
                setStatus("Environment not running", false)
                Toast.makeText(context, "Izin Termux RUN_COMMAND ditolak. EZBox butuh izin ini untuk berjalan.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupEnvironment() {
        val prefs = requireActivity().getSharedPreferences("EZBoxPrefs", Context.MODE_PRIVATE)
        val resolution = prefs.getString("resolution", "960x540")

        Log.d("EZBox", "Attempting to launch EZOS with resolution: $resolution")
        debugLog("setupEnvironment start, resolution=$resolution")
        try {
            val intent = Intent().apply {
                action = "com.termux.RUN_COMMAND"
                component = ComponentName("com.termux", "com.termux.app.RunCommandService")
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", "EZBOX_RES=$resolution ezos-run EZOS"))
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            }
            setStatus("Starting EZOS desktop in Termux...", true)
            debugLog("Calling startService...")
            requireContext().startService(intent)
            debugLog("startService returned OK, scheduling launch in 5s")
            setStatus("Preparing desktop, please wait...", true)
            launchRunnable = Runnable {
                debugLog("postDelayed fired, isAdded=$isAdded")
                if (isAdded) {
                    debugLog("Starting VncActivity")
                    setStatus("Environment not running", false)
                    startActivity(android.content.Intent(requireContext(), VncActivity::class.java))
                }
            }
            launchHandler.postDelayed(launchRunnable!!, 5000)
            Log.d("EZBox", "Intent sent successfully")
        } catch (e: Exception) {
            debugLog("EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
            Log.e("EZBox", "Error launching: ${e.message}")
            setStatus("Environment not running", false)
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        launchRunnable?.let { launchHandler.removeCallbacks(it) }
    }
}
