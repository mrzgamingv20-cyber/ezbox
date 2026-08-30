package com.mrzgaming.ezbox

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
import java.util.Calendar
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
    private var tvBackendStatus: TextView? = null
    private var tvUptime: TextView? = null
    private var ringRam: RingProgressView? = null
    private var tvRamPercent: TextView? = null
    private var tvRamDetail: TextView? = null
    private var tvGreeting: TextView? = null
    private val statusPollHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var statusPollRunnable: Runnable? = null

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

    private fun setGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour < 12 -> "Good morning"
            hour < 18 -> "Good afternoon"
            else -> "Good evening"
        }
        tvGreeting?.text = greeting
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        tvStatus = view.findViewById(R.id.tvStatus)
        progressLaunch = view.findViewById(R.id.progressLaunch)
        btnSetup = view.findViewById(R.id.btnSetup)
        tvBackendStatus = view.findViewById(R.id.tvBackendStatus)
        tvUptime = view.findViewById(R.id.tvUptime)
        ringRam = view.findViewById(R.id.ringRam)
        tvRamPercent = view.findViewById(R.id.tvRamPercent)
        tvRamDetail = view.findViewById(R.id.tvRamDetail)
        tvGreeting = view.findViewById(R.id.tvGreeting)

        setGreeting()
        ringRam?.ringColor = ContextCompat.getColor(requireContext(), R.color.ezos_success)

        btnSetup?.setOnClickListener {
            debugLog("Button clicked")
            setStatus("Checking permission...", true)
            checkPermissionAndLaunch()
        }

        view.findViewById<View>(R.id.quickStore)?.setOnClickListener {
            (activity as? MainActivity)?.navigateTo(R.id.nav_store)
        }
        view.findViewById<View>(R.id.quickTerminal)?.setOnClickListener {
            (activity as? MainActivity)?.navigateTo(R.id.nav_terminal)
        }
        view.findViewById<View>(R.id.quickSettings)?.setOnClickListener {
            (activity as? MainActivity)?.navigateTo(R.id.nav_settings)
        }
        view.findViewById<View>(R.id.quickOpenDesktop)?.setOnClickListener {
            startActivity(Intent(requireContext(), VncActivity::class.java))
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        setGreeting()
        startStatusPolling()
    }

    override fun onPause() {
        super.onPause()
        statusPollRunnable?.let { statusPollHandler.removeCallbacks(it) }
    }

    private fun startStatusPolling() {
        statusPollRunnable = object : Runnable {
            override fun run() {
                checkBackendStatus()
                statusPollHandler.postDelayed(this, 3000)
            }
        }
        statusPollHandler.post(statusPollRunnable!!)
    }

    private fun checkBackendStatus() {
        val statusFile = File("/storage/emulated/0/Download/ezbox_backend_status.txt")
        val prefs = requireActivity().getSharedPreferences("EZBoxPrefs", Context.MODE_PRIVATE)
        try {
            if (statusFile.exists()) {
                val content = statusFile.readText().trim()
                if (content == "running") {
                    tvBackendStatus?.text = "Running"
                    tvBackendStatus?.setTextColor(ContextCompat.getColor(requireContext(), R.color.ezos_success))
                    updateUptime(prefs)
                } else {
                    tvBackendStatus?.text = "Idle"
                    tvBackendStatus?.setTextColor(ContextCompat.getColor(requireContext(), R.color.ezos_text_secondary))
                    tvUptime?.visibility = View.GONE
                }
            } else {
                tvBackendStatus?.text = "Idle"
                tvBackendStatus?.setTextColor(ContextCompat.getColor(requireContext(), R.color.ezos_text_secondary))
                tvUptime?.visibility = View.GONE
            }
        } catch (e: Exception) {
            tvBackendStatus?.text = "Unknown"
        }
        updateRamUsage()
    }

    private fun updateUptime(prefs: android.content.SharedPreferences) {
        val launchTime = prefs.getLong("desktop_launch_time", 0L)
        if (launchTime == 0L) {
            tvUptime?.visibility = View.GONE
            return
        }
        val elapsedMs = System.currentTimeMillis() - launchTime
        val minutes = (elapsedMs / 60000) % 60
        val hours = elapsedMs / 3600000
        val text = if (hours > 0) "Running for ${hours}h ${minutes}m" else "Running for ${minutes}m"
        tvUptime?.text = text
        tvUptime?.visibility = View.VISIBLE
    }

    private fun updateRamUsage() {
        try {
            val am = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)

            val totalMb = memInfo.totalMem / (1024 * 1024)
            val availMb = memInfo.availMem / (1024 * 1024)
            val usedMb = totalMb - availMb
            val usedPercent = ((usedMb.toDouble() / totalMb.toDouble()) * 100).toInt()

            ringRam?.progress = usedPercent
            tvRamPercent?.text = "$usedPercent%"
            tvRamDetail?.text = "${usedMb}MB / ${totalMb}MB used"
        } catch (e: Exception) {
            tvRamDetail?.text = "Unable to read memory info"
        }
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
                Toast.makeText(context, "Termux RUN_COMMAND permission denied. EZBox needs this to work.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupEnvironment() {
        val prefs = requireActivity().getSharedPreferences("EZBoxPrefs", Context.MODE_PRIVATE)
        val resolution = prefs.getString("resolution", "960x540")
        val vncPassword = prefs.getString("vnc_password", "ezbox123")

        debugLog("setupEnvironment start, resolution=$resolution")
        prefs.edit().putLong("desktop_launch_time", System.currentTimeMillis()).apply()
        try {
            val command = "echo running > /storage/emulated/0/Download/ezbox_backend_status.txt; " +
                "EZBOX_RES=$resolution EZBOX_VNC_PASSWORD='$vncPassword' ezos-run EZOS; " +
                "echo idle > /storage/emulated/0/Download/ezbox_backend_status.txt"

            val intent = Intent().apply {
                action = "com.termux.RUN_COMMAND"
                component = ComponentName("com.termux", "com.termux.app.RunCommandService")
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            }
            setStatus("Starting EZOS desktop in Termux...", true)
            ContextCompat.startForegroundService(requireContext(), intent)
            setStatus("Preparing desktop, please wait...", true)
            launchRunnable = Runnable {
                debugLog("postDelayed fired, isAdded=$isAdded")
                if (isAdded) {
                    setStatus("Ready to launch", false)
                    checkBackendStatus()
                    val vncIntent = Intent(requireContext(), VncActivity::class.java)
                    vncIntent.putExtra("vnc_password", vncPassword)
                    startActivity(vncIntent)
                }
            }
            launchHandler.postDelayed(launchRunnable!!, 5000)
        } catch (e: Exception) {
            debugLog("EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
            setStatus("Environment not running", false)
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        launchRunnable?.let { launchHandler.removeCallbacks(it) }
        statusPollRunnable?.let { statusPollHandler.removeCallbacks(it) }
    }
}
