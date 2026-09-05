package com.mrzgaming.ezbox

import android.app.Dialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
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
    private var tvStatus: TextView? = null
    private var progressLaunch: ProgressBar? = null
    private var btnSetup: Button? = null
    private var btnQuickSettings: Button? = null
    private var tvBackendStatus: TextView? = null
    private var tvUptime: TextView? = null
    private var tvActiveConfig: TextView? = null
    private var ringRam: RingProgressView? = null
    private var tvRamPercent: TextView? = null
    private var tvRamDetail: TextView? = null
    private var tvGreeting: TextView? = null
    private val statusPollHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var statusPollRunnable: Runnable? = null

    // Poll cepat khusus dipakai saat menunggu backend siap setelah tombol launch ditekan.
    // Beda dari statusPollRunnable (poll ambient tiap 3 detik yang selalu jalan di background)
    private val launchWaitHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var launchWaitRunnable: Runnable? = null
    private var isWaitingForLaunch = false

    private var isDesktopRunning = false

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
        btnQuickSettings = view.findViewById(R.id.btnQuickSettings)
        tvBackendStatus = view.findViewById(R.id.tvBackendStatus)
        tvUptime = view.findViewById(R.id.tvUptime)
        tvActiveConfig = view.findViewById(R.id.tvActiveConfig)
        ringRam = view.findViewById(R.id.ringRam)
        tvRamPercent = view.findViewById(R.id.tvRamPercent)
        tvRamDetail = view.findViewById(R.id.tvRamDetail)
        tvGreeting = view.findViewById(R.id.tvGreeting)

        setGreeting()
        ringRam?.ringColor = ContextCompat.getColor(requireContext(), R.color.ezos_success)
        updateActiveConfigLabel()

        btnSetup?.setOnClickListener {
            if (isDesktopRunning) {
                openDesktop()
            } else {
                debugLog("Button clicked")
                setStatus("Checking permission...", true)
                checkPermissionAndLaunch()
            }
        }

        btnQuickSettings?.setOnClickListener { showQuickSettingsDialog() }

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
            openDesktop()
        }

        return view
    }

    private fun openDesktop() {
        val prefs = requireActivity().getSharedPreferences("EZBoxPrefs", Context.MODE_PRIVATE)
        val vncPassword = prefs.getString("vnc_password", "ezbox123")
        val vncIntent = Intent(requireContext(), VncActivity::class.java)
        vncIntent.putExtra("vnc_password", vncPassword)
        startActivity(vncIntent)
    }

    private fun updateActiveConfigLabel() {
        val prefs = requireActivity().getSharedPreferences("EZBoxPrefs", Context.MODE_PRIVATE)
        val resolution = prefs.getString("resolution", "960x540")
        val de = prefs.getString("desktop_environment", "xfce")
        val deLabel = if (de == "lxqt") "LXQt" else "XFCE4"
        tvActiveConfig?.text = "$resolution · $deLabel"
    }

    private fun showQuickSettingsDialog() {
        val prefs = requireActivity().getSharedPreferences("EZBoxPrefs", Context.MODE_PRIVATE)
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_quick_settings)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        var selectedRes = prefs.getString("resolution", "960x540") ?: "960x540"
        var selectedDe = prefs.getString("desktop_environment", "xfce") ?: "xfce"

        val btnResPhone = dialog.findViewById<Button>(R.id.btnResPhone)
        val btnResTablet = dialog.findViewById<Button>(R.id.btnResTablet)
        val btnResDesktop = dialog.findViewById<Button>(R.id.btnResDesktop)
        val btnDeXfce = dialog.findViewById<Button>(R.id.btnDeXfce)
        val btnDeLxqt = dialog.findViewById<Button>(R.id.btnDeLxqt)

        fun refreshResButtons() {
            val options = mapOf(
                "800x480" to btnResPhone,
                "1280x720" to btnResTablet,
                "1600x900" to btnResDesktop
            )
            for ((res, btn) in options) {
                val active = res == selectedRes
                btn?.setTextColor(ContextCompat.getColor(requireContext(),
                    if (active) R.color.ezos_accent else R.color.ezos_text_primary))
            }
        }

        fun refreshDeButtons() {
            btnDeXfce?.setTextColor(ContextCompat.getColor(requireContext(),
                if (selectedDe == "xfce") R.color.ezos_accent else R.color.ezos_text_primary))
            btnDeLxqt?.setTextColor(ContextCompat.getColor(requireContext(),
                if (selectedDe == "lxqt") R.color.ezos_accent else R.color.ezos_text_primary))
        }

        refreshResButtons()
        refreshDeButtons()

        btnResPhone?.setOnClickListener { selectedRes = "800x480"; refreshResButtons() }
        btnResTablet?.setOnClickListener { selectedRes = "1280x720"; refreshResButtons() }
        btnResDesktop?.setOnClickListener { selectedRes = "1600x900"; refreshResButtons() }
        btnDeXfce?.setOnClickListener { selectedDe = "xfce"; refreshDeButtons() }
        btnDeLxqt?.setOnClickListener { selectedDe = "lxqt"; refreshDeButtons() }

        dialog.findViewById<Button>(R.id.btnQuickSettingsDone)?.setOnClickListener {
            prefs.edit()
                .putString("resolution", selectedRes)
                .putString("desktop_environment", selectedDe)
                .apply()
            updateActiveConfigLabel()
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        setGreeting()
        updateActiveConfigLabel()
        startStatusPolling()
    }

    override fun onPause() {
        super.onPause()
        statusPollRunnable?.let { statusPollHandler.removeCallbacks(it) }
        launchWaitRunnable?.let { launchWaitHandler.removeCallbacks(it) }
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
                    isDesktopRunning = true
                    tvBackendStatus?.text = "Running"
                    tvBackendStatus?.setTextColor(ContextCompat.getColor(requireContext(), R.color.ezos_success))
                    updateUptime(prefs)
                    if (!isWaitingForLaunch) {
                        btnSetup?.text = "Open Desktop"
                    }
                } else {
                    isDesktopRunning = false
                    tvBackendStatus?.text = "Idle"
                    tvBackendStatus?.setTextColor(ContextCompat.getColor(requireContext(), R.color.ezos_text_secondary))
                    tvUptime?.visibility = View.GONE
                    if (!isWaitingForLaunch) {
                        btnSetup?.text = "Launch Environment"
                    }
                }
            } else {
                isDesktopRunning = false
                tvBackendStatus?.text = "Idle"
                tvBackendStatus?.setTextColor(ContextCompat.getColor(requireContext(), R.color.ezos_text_secondary))
                tvUptime?.visibility = View.GONE
                if (!isWaitingForLaunch) {
                    btnSetup?.text = "Launch Environment"
                }
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
        val desktopEnvironment = prefs.getString("desktop_environment", "xfce")
        val vncPassword = prefs.getString("vnc_password", "ezbox123")

        debugLog("setupEnvironment start, resolution=$resolution")
        // Hapus status file lama dulu supaya polling tidak salah baca status basi dari sesi sebelumnya
        try { File("/storage/emulated/0/Download/ezbox_backend_status.txt").delete() } catch (e: Exception) {}

        prefs.edit().putLong("desktop_launch_time", System.currentTimeMillis()).apply()
        try {
            val command = "echo running > /storage/emulated/0/Download/ezbox_backend_status.txt; " +
                "EZBOX_RES=$resolution EZBOX_DE=$desktopEnvironment EZBOX_VNC_PASSWORD='$vncPassword' ezos-run EZOS; " +
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
            waitForBackendReady(vncPassword)
        } catch (e: Exception) {
            debugLog("EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
            setStatus("Environment not running", false)
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Poll status file setiap 500ms sampai backend benar-benar menulis "running",
     * dengan timeout 20 detik sebagai fallback kalau file tidak pernah muncul
     * (misal ezos-run gagal start). Ini menggantikan delay tebakan 5 detik yang lama -
     * sekarang menunggu sinyal nyata dari backend, bukan durasi tetap.
     */
    private fun waitForBackendReady(vncPassword: String?) {
        isWaitingForLaunch = true
        val statusFile = File("/storage/emulated/0/Download/ezbox_backend_status.txt")
        val startTime = System.currentTimeMillis()
        val timeoutMs = 20000L

        launchWaitRunnable = object : Runnable {
            override fun run() {
                if (!isAdded) return

                val elapsed = System.currentTimeMillis() - startTime
                val ready = try {
                    statusFile.exists() && statusFile.readText().trim() == "running"
                } catch (e: Exception) { false }

                if (ready) {
                    debugLog("Backend ready after ${elapsed}ms")
                    isWaitingForLaunch = false
                    isDesktopRunning = true
                    setStatus("Ready to launch", false)
                    btnSetup?.text = "Open Desktop"
                    checkBackendStatus()
                    val vncIntent = Intent(requireContext(), VncActivity::class.java)
                    vncIntent.putExtra("vnc_password", vncPassword)
                    startActivity(vncIntent)
                } else if (elapsed >= timeoutMs) {
                    debugLog("Backend wait timeout after ${elapsed}ms")
                    isWaitingForLaunch = false
                    setStatus("Taking longer than usual. Try opening the desktop manually.", false)
                } else {
                    launchWaitHandler.postDelayed(this, 500)
                }
            }
        }
        launchWaitHandler.post(launchWaitRunnable!!)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        statusPollRunnable?.let { statusPollHandler.removeCallbacks(it) }
        launchWaitRunnable?.let { launchWaitHandler.removeCallbacks(it) }
    }
}
