package com.mrzgaming.ezbox

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
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
    private var tvBackendStatus: TextView? = null
    private var tvUptime: TextView? = null
    private var btnEnterDesktop: Button? = null
    private var btnStopDesktop: Button? = null
    private var btnSettings: Button? = null
    private var isDesktopRunning = false
    private val checkExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val statusPollHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var statusPollRunnable: Runnable? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        tvBackendStatus = view.findViewById(R.id.tvBackendStatus)
        tvUptime = view.findViewById(R.id.tvUptime)
        btnEnterDesktop = view.findViewById(R.id.btnEnterDesktop)
        btnStopDesktop = view.findViewById(R.id.btnStopDesktop)
        btnSettings = view.findViewById(R.id.btnSettings)

        btnEnterDesktop?.setOnClickListener { enterDesktop() }
        btnStopDesktop?.setOnClickListener { stopDesktop() }
        btnSettings?.setOnClickListener {
            navigateTo(R.id.nav_settings)
        }
        btnStopDesktop?.isEnabled = false

        setGreeting()
        loadBackendStatus()
        startStatusPolling()

        return view
    }

    private fun enterDesktop() {
        if (isDesktopRunning) {
            val prefs = requireActivity().getSharedPreferences("EZBoxPrefs", Context.MODE_PRIVATE)
            openDesktop(prefs.getString("vnc_password", "ezbox123"))
        } else {
            checkPermissionAndLaunch()
        }
    }

    private fun checkPermissionAndLaunch() {
        val granted = ContextCompat.checkSelfPermission(requireContext(), termuxPermission) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            launchDesktop()
        } else {
            requestPermissions(arrayOf(termuxPermission), requestCode)
        }
    }

    private fun launchDesktop() {
        val prefs = requireActivity().getSharedPreferences("EZBoxPrefs", Context.MODE_PRIVATE)
        val vncPassword = prefs.getString("vnc_password", "ezbox123")
        val resolution = prefs.getString("resolution", "960x540") ?: "960x540"
        val de = prefs.getString("desktop_environment", "xfce") ?: "xfce"

        deployBackendScript()
        val downloadsDir = getDownloadDir()
        downloadsDir?.let { File(it, "ezbox_backend_status.txt").delete() }
        prefs.edit().putLong("desktop_launch_time", System.currentTimeMillis()).apply()

        val statusPath = downloadsDir?.let { File(it, "ezbox_backend_status.txt").absolutePath } ?: "/storage/emulated/0/Download/ezbox_backend_status.txt"
        val safePassword = vncPassword.orEmpty().replace("'", "'\\''")
        val command = "echo running > $statusPath; " +
            "EZBOX_RES=$resolution EZBOX_DE=$de EZBOX_VNC_PASSWORD='$safePassword' ezos-run EZOS; " +
            "echo idle > $statusPath"

        TermuxCommand.start(requireContext(), command)
        waitForBackendReady(vncPassword)
    }

    private fun waitForBackendReady(vncPassword: String?) {
        isDesktopRunning = true
        val startTime = System.currentTimeMillis()
        statusPollRunnable = object : Runnable {
            override fun run() {
                if (!isAdded) return
                val elapsed = System.currentTimeMillis() - startTime
                checkExecutor.execute {
                    val ready = try {
                        val socket = java.net.Socket()
                        socket.connect(java.net.InetSocketAddress("127.0.0.1", 5901), 300)
                        socket.close()
                        true
                    } catch (e: Exception) { false }
                    statusPollHandler.post { onBackendCheckResult(ready, elapsed, vncPassword) }
                }
            }
        }
        statusPollHandler.post(statusPollRunnable!!)
    }

    private fun onBackendCheckResult(ready: Boolean, elapsed: Long, vncPassword: String?) {
        if (!isAdded) return
        if (ready) {
            isDesktopRunning = true
            openDesktop(vncPassword)
        } else if (elapsed >= 20000L) {
            isDesktopRunning = false
            Toast.makeText(context, "Backend timeout. Try again.", Toast.LENGTH_LONG).show()
        } else {
            val retryRunnable = object : Runnable {
                override fun run() {
                    if (isAdded) {
                        checkExecutor.execute {
                            val r = try {
                                val socket = java.net.Socket()
                                socket.connect(java.net.InetSocketAddress("127.0.0.1", 5901), 300)
                                socket.close()
                                true
                            } catch (e: Exception) { false }
                            statusPollHandler.post { onBackendCheckResult(r, elapsed + 500, vncPassword) }
                        }
                    }
                }
            }
            statusPollHandler.postDelayed(retryRunnable, 500)
        }
    }

    private fun openDesktop(vncPassword: String?) {
        val intent = Intent(requireContext(), VncActivity::class.java)
        intent.putExtra("vnc_password", vncPassword)
        intent.putExtra("container_name", "EZBox Desktop")
        startActivity(intent)
    }

    fun stopDesktop() {
        isDesktopRunning = false
        statusPollRunnable?.let { statusPollHandler.removeCallbacks(it) }
        TermuxCommand.start(
            requireContext(),
            "pkill -9 -f 'Xvnc :1 '; pkill -9 -f 'xfce4-session'; pkill -9 -f 'ezos-run'"
        )
        getDownloadDir()?.let { File(it, "ezbox_backend_status.txt").delete() }
        tvBackendStatus?.text = "Idle"
        tvUptime?.visibility = View.GONE
        btnStopDesktop?.isEnabled = false
    }

    private fun setGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour < 12 -> "Good morning"
            hour < 18 -> "Good afternoon"
            else -> "Good evening"
        }
        val tv = view?.findViewById<TextView>(R.id.tvBackendStatus)
        tv?.text = "$greeting"
    }

    private fun loadBackendStatus() {
        // The 3s poll can fire right after detach; requireActivity()/requireContext()
        // here would crash the app.
        val ctx = context ?: return
        val downloadsDir = getDownloadDir() ?: return
        val statusFile = File(downloadsDir, "ezbox_backend_status.txt")
        val prefs = ctx.getSharedPreferences("EZBoxPrefs", Context.MODE_PRIVATE)
        try {
            if (statusFile.exists()) {
                val content = statusFile.readText().trim()
                if (content == "running") {
                    isDesktopRunning = true
                    tvBackendStatus?.text = "Running"
                    tvBackendStatus?.setTextColor(ContextCompat.getColor(ctx, R.color.ezos_success))
                    updateUptime(prefs)
                } else {
                    isDesktopRunning = false
                    tvBackendStatus?.text = "Idle"
                    tvBackendStatus?.setTextColor(ContextCompat.getColor(ctx, R.color.ezos_text_secondary))
                    tvUptime?.visibility = View.GONE
                }
            } else {
                isDesktopRunning = false
                tvBackendStatus?.text = "Idle"
                tvBackendStatus?.setTextColor(ContextCompat.getColor(ctx, R.color.ezos_text_secondary))
                tvUptime?.visibility = View.GONE
            }
        } catch (e: Exception) {
            tvBackendStatus?.text = "Unknown"
        }
        btnStopDesktop?.isEnabled = isDesktopRunning
    }

    private fun updateUptime(prefs: android.content.SharedPreferences) {
        val launchTime = prefs.getLong("desktop_launch_time", 0L)
        if (launchTime == 0L) { tvUptime?.visibility = View.GONE; return }
        val elapsedMs = System.currentTimeMillis() - launchTime
        val minutes = (elapsedMs / 60000) % 60
        val hours = elapsedMs / 3600000
        val text = if (hours > 0) "Running ${hours}h ${minutes}m" else "Running ${minutes}m"
        tvUptime?.text = text
        tvUptime?.visibility = View.VISIBLE
    }

    private fun startStatusPolling() {
        statusPollRunnable = object : Runnable {
            override fun run() {
                loadBackendStatus()
                statusPollHandler.postDelayed(this, 3000)
            }
        }
        statusPollHandler.post(statusPollRunnable!!)
    }

    private fun deployBackendScript() {
        try {
            val scriptContent = requireContext().assets.open("ezos-run").bufferedReader().readText()
            val deployCommand = "cat << 'EZBOX_SCRIPT_EOF' > \$PREFIX/bin/ezos-run\n${scriptContent}\nEZBOX_SCRIPT_EOF\nchmod +x \$PREFIX/bin/ezos-run"
            TermuxCommand.start(requireContext(), deployCommand)
        } catch (e: Exception) {
            Log.e("EZBox", "Deploy failed: ${e.message}")
        }
    }

    fun performLaunch() {
        if (isDesktopRunning) {
            val prefs = requireActivity().getSharedPreferences("EZBoxPrefs", Context.MODE_PRIVATE)
            openDesktop(prefs.getString("vnc_password", "ezbox123"))
        } else {
            checkPermissionAndLaunch()
        }
    }

    private fun getDownloadDir(): File? {
        return try {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        } catch (e: Exception) {
            requireContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        }
    }

    override fun onResume() {
        super.onResume()
        loadBackendStatus()
    }

    override fun onPause() {
        super.onPause()
        statusPollRunnable?.let { statusPollHandler.removeCallbacks(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        statusPollRunnable?.let { statusPollHandler.removeCallbacks(it) }
        tvBackendStatus = null; tvUptime = null
        btnEnterDesktop = null; btnStopDesktop = null; btnSettings = null
    }

    private fun navigateTo(itemId: Int) {
        (activity as? MainActivity)?.navigateTo(itemId)
    }
}