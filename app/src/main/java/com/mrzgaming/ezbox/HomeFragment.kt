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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        tvStatus = view.findViewById(R.id.tvStatus)
        progressLaunch = view.findViewById(R.id.progressLaunch)
        btnSetup = view.findViewById(R.id.btnSetup)
        tvBackendStatus = view.findViewById(R.id.tvBackendStatus)

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
        view.findViewById<View>(R.id.quickOpenDesktop)?.setOnClickListener {
            startActivity(Intent(requireContext(), VncActivity::class.java))
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        startStatusPolling()
    }

    override fun onPause() {
        super.onPause()
        statusPollRunnable?.let { statusPollHandler.removeCallbacks(it) }
    }

    // Poll status tiap 3 detik selagi tab Home terbuka, supaya card "Backend Status"
    // ter-update otomatis begitu desktop selesai startup - bukan cuma dicek sekali saat onResume
    private fun startStatusPolling() {
        statusPollRunnable = object : Runnable {
            override fun run() {
                checkBackendStatus()
                statusPollHandler.postDelayed(this, 3000)
            }
        }
        statusPollHandler.post(statusPollRunnable!!)
    }

    /**
     * Cek apakah desktop EZOS sedang berjalan dengan meminta Termux menjalankan pgrep
     * dan menuliskan hasilnya ke file status yang dibaca app. RUN_COMMAND tidak
     * mengembalikan stdout langsung ke app tanpa dependency tambahan (lihat catatan
     * di StoreFragment soal keterbatasan ini), jadi status ini best-effort:
     * ditampilkan "Checking..." lalu di-refresh tiap kali user kembali ke tab Home.
     */
    private fun checkBackendStatus() {
        val statusFile = File("/storage/emulated/0/Download/ezbox_backend_status.txt")
        try {
            if (statusFile.exists()) {
                val content = statusFile.readText().trim()
                if (content == "running") {
                    tvBackendStatus?.text = "Running"
                    tvBackendStatus?.setTextColor(ContextCompat.getColor(requireContext(), R.color.ezos_success))
                } else {
                    tvBackendStatus?.text = "Idle"
                    tvBackendStatus?.setTextColor(ContextCompat.getColor(requireContext(), R.color.ezos_text_secondary))
                }
            } else {
                tvBackendStatus?.text = "Idle"
                tvBackendStatus?.setTextColor(ContextCompat.getColor(requireContext(), R.color.ezos_text_secondary))
            }
        } catch (e: Exception) {
            tvBackendStatus?.text = "Unknown"
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
        try {
            // ezos-run sendiri sudah berupa keep-alive loop yang jalan lama (tidak langsung exit),
            // jadi status "running" ditulis di AWAL sebelum ezos-run dipanggil (begitu command mulai jalan,
            // proses Xvnc akan segera menyusul), dan "idle" ditulis ulang HANYA kalau ezos-run
            // benar-benar berhenti/exit (baik karena error maupun VNC server di-stop manual)
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
    }
}
