package com.mrzgaming.ezbox

import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.provider.Settings
import java.io.File
import android.widget.ProgressBar

class StoreFragment : Fragment() {

    private val expectedDurationMs = mapOf(
        "wine" to 5 * 60_000L,
        "box64" to 3 * 60_000L,
        "firefox" to 90_000L,
        "gimp" to 90_000L,
        "vlc" to 60_000L,
        "pcmanfm" to 45_000L
    )

    private val availablePackages = listOf(
        StorePackage(
            name = "Wine",
            description = "Run Windows applications on EZOS desktop",
            pkgNames = listOf("wine-staging"),
            checkBinary = "wine"
        ),
        StorePackage(
            name = "Box64",
            description = "x86_64 binary translation for ARM devices",
            pkgNames = listOf("box64"),
            checkBinary = "box64"
        ),
        StorePackage(
            name = "Firefox",
            description = "Web browser for the EZOS desktop",
            pkgNames = listOf("firefox"),
            checkBinary = "firefox"
        ),
        StorePackage(
            name = "GIMP",
            description = "Image editor",
            pkgNames = listOf("gimp"),
            checkBinary = "gimp"
        ),
        StorePackage(
            name = "VLC",
            description = "Media player",
            pkgNames = listOf("vlc"),
            checkBinary = "vlc"
        ),
        StorePackage(
            name = "File Manager (PCManFM)",
            description = "Lightweight graphical file manager",
            pkgNames = listOf("pcmanfm"),
            checkBinary = "pcmanfm"
        )
    )

    // Menyimpan status "sudah dipasang" per paket, bertahan walau app ditutup-buka lagi.
    // CATATAN: ini status OPTIMISTIC — ditandai begitu perintah install terkirim ke Termux,
    // bukan konfirmasi bahwa instalasi benar-benar selesai sukses (RUN_COMMAND tidak
    // memberi hasil eksekusi balik ke app tanpa dependency tambahan yang rawan gagal fetch).
    private lateinit var prefs: SharedPreferences

    private fun prefKeyFor(pkg: StorePackage) = "installed_${pkg.checkBinary}"

    private fun isMarkedInstalled(pkg: StorePackage): Boolean =
        prefs.getBoolean(prefKeyFor(pkg), false)

    private fun markInstalled(pkg: StorePackage) {
        prefs.edit().putBoolean(prefKeyFor(pkg), true).apply()
    }

    // Polling verifikasi instalasi nyata via checkBinary, bukan tebakan waktu.
    private val pollHandler = Handler(Looper.getMainLooper())
    private val activePolls = mutableMapOf<String, Runnable>()
    private val cardProgressViews = mutableMapOf<String, Pair<ProgressBar, TextView>>()
    private val progressTickHandler = Handler(Looper.getMainLooper())
    private val activeTicks = mutableMapOf<String, Runnable>()

    private fun startProgressTick(pkg: StorePackage, startTime: Long) {
        val key = pkg.checkBinary
        activeTicks[key]?.let { progressTickHandler.removeCallbacks(it) }
        val duration = expectedDurationMs[key] ?: 90_000L
        val (bar, status) = cardProgressViews[key] ?: return

        val tick = object : Runnable {
            override fun run() {
                if (!isAdded) return
                val elapsed = System.currentTimeMillis() - startTime
                val pct = ((elapsed.toFloat() / duration) * 95).toInt().coerceIn(0, 95)
                bar.progress = pct
                status.text = "Installing ${pkg.name}... $pct%"
                if (activePolls.containsKey(key)) {
                    progressTickHandler.postDelayed(this, 300)
                }
            }
        }
        activeTicks[key] = tick
        progressTickHandler.post(tick)
    }

    private fun stopProgressTick(pkg: StorePackage) {
        val key = pkg.checkBinary
        activeTicks[key]?.let { progressTickHandler.removeCallbacks(it) }
        activeTicks.remove(key)
    }
    private val pollIntervalMs = 3000L
    private val pollTimeoutMs = 10 * 60 * 1000L // 10 menit, cukup untuk Wine/Box64

    private fun hasManageStoragePermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    private fun requestManageStoragePermission() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${requireContext().packageName}")
            }
            startActivity(intent)
            Toast.makeText(context, "Aktifkan \"Allow all files access\" untuk EZBox, lalu coba install lagi.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }

    private fun statusFile(pkg: StorePackage): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), ".ezbox_install_${pkg.checkBinary}")

    private fun schedulePoll(pkg: StorePackage, button: Button, elapsedMs: Long = 0L) {
        val key = pkg.checkBinary
        activePolls[key]?.let { pollHandler.removeCallbacks(it) }
        val (progressBar, statusText) = cardProgressViews[key] ?: Pair(null, null)

        val runnable = Runnable {
            if (!isAdded) return@Runnable
            val file = statusFile(pkg)
            when {
                file.exists() && file.readText().trim() == "done" -> {
                    file.delete()
                    activePolls.remove(key)
                    stopProgressTick(pkg)
                    progressBar?.progress = 100
                    statusText?.text = "Installed"
                    progressBar?.postDelayed({ progressBar.visibility = View.GONE; statusText?.visibility = View.GONE }, 1500)
                    markInstalled(pkg)
                    button.isEnabled = true
                    setButtonState(button, true)
                    Toast.makeText(context, "${pkg.name} installed", Toast.LENGTH_SHORT).show()
                }
                file.exists() && file.readText().trim() == "failed" -> {
                    file.delete()
                    activePolls.remove(key)
                    stopProgressTick(pkg)
                    progressBar?.visibility = View.GONE
                    statusText?.text = "Failed"
                    statusText?.setTextColor(Color.RED)
                    button.isEnabled = true
                    setButtonState(button, isMarkedInstalled(pkg))
                    Toast.makeText(context, "Failed to install ${pkg.name}", Toast.LENGTH_LONG).show()
                }
                elapsedMs >= pollTimeoutMs -> {
                    activePolls.remove(key)
                    stopProgressTick(pkg)
                    progressBar?.visibility = View.GONE
                    statusText?.text = "Timed out"
                    button.isEnabled = true
                    button.text = "Check Termux"
                    Toast.makeText(context, "${pkg.name} install timed out - check Termux manually", Toast.LENGTH_LONG).show()
                }
                else -> {
                    schedulePoll(pkg, button, elapsedMs + pollIntervalMs)
                }
            }
        }
        activePolls[key] = runnable
        pollHandler.postDelayed(runnable, pollIntervalMs)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        prefs = requireContext().getSharedPreferences("ezbox_store", 0)

        val view = inflater.inflate(R.layout.fragment_store, container, false)
        val itemContainer = view.findViewById<LinearLayout>(R.id.storeItemContainer)

        for (pkg in availablePackages) {
            itemContainer.addView(buildPackageCard(pkg))
        }

        return view
    }

    private fun buildPackageCard(pkg: StorePackage): MaterialCardView {
        val card = MaterialCardView(requireContext()).apply {
            radius = 16f
            cardElevation = 2f
            setContentPadding(24, 24, 24, 24)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 24
            layoutParams = params
        }

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val textContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleView = TextView(requireContext()).apply {
            text = pkg.name
            textSize = 16f
            setTextColor(Color.BLACK)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        val descView = TextView(requireContext()).apply {
            text = pkg.description
            textSize = 13f
            setTextColor(Color.DKGRAY)
        }

        textContainer.addView(titleView)
        textContainer.addView(descView)

        val installButton = Button(requireContext()).apply {
            setButtonState(this, isMarkedInstalled(pkg))
            setOnClickListener {
                installPackage(pkg, this)
            }
        }

        row.addView(textContainer)
        row.addView(installButton)

        val progressBar = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12 }
        }

        val statusText = TextView(requireContext()).apply {
            textSize = 12f
            setTextColor(Color.DKGRAY)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 4 }
        }

        val column = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        column.addView(row)
        column.addView(progressBar)
        column.addView(statusText)
        card.addView(column)

        cardProgressViews[pkg.checkBinary] = Pair(progressBar, statusText)

        return card
    }

    /**
     * Set tampilan tombol sesuai status: "Installed" (abu-abu, tetap bisa ditekan untuk
     * reinstall) atau "Install" (default).
     */
    private fun setButtonState(button: Button, installed: Boolean) {
        if (installed) {
            button.text = "Installed"
        } else {
            button.text = "Install"
        }
    }

    private fun installPackage(pkg: StorePackage, button: Button) {
        button.isEnabled = false
        button.text = "Installing..."
        val (progressBar, statusText) = cardProgressViews[pkg.checkBinary] ?: Pair(null, null)
        progressBar?.visibility = View.VISIBLE
        progressBar?.progress = 0
        statusText?.visibility = View.VISIBLE
        statusText?.setTextColor(Color.DKGRAY)
        statusText?.text = "Installing ${pkg.name}... 0%"

        val pkgList = pkg.pkgNames.joinToString(" ")
        val statusPath = statusFile(pkg).absolutePath
        val command = "rm -f \"$statusPath\"; pkg install -y $pkgList; " +
            "if command -v ${pkg.checkBinary} >/dev/null 2>&1; then echo done > \"$statusPath\"; " +
            "else echo failed > \"$statusPath\"; fi"

        try {
            val intent = Intent().apply {
                action = "com.termux.RUN_COMMAND"
                component = ComponentName("com.termux", "com.termux.app.RunCommandService")
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            }
            ContextCompat.startForegroundService(requireContext(), intent)
            Toast.makeText(context, "Installing ${pkg.name} in Termux...", Toast.LENGTH_SHORT).show()

            if (!hasManageStoragePermission()) {
                Toast.makeText(context, "Butuh izin All Files Access untuk verifikasi instalasi", Toast.LENGTH_LONG).show()
                requestManageStoragePermission()
                button.isEnabled = true
                setButtonState(button, isMarkedInstalled(pkg))
                return
            }

            startProgressTick(pkg, System.currentTimeMillis())
            schedulePoll(pkg, button)
        } catch (e: Exception) {
            Log.e("StoreFragment", "Install failed: ${e.message}")
            Toast.makeText(context, "Failed to start install: ${e.message}", Toast.LENGTH_SHORT).show()
            button.isEnabled = true
            setButtonState(button, isMarkedInstalled(pkg))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pollHandler.removeCallbacksAndMessages(null)
        activePolls.clear()
        progressTickHandler.removeCallbacksAndMessages(null)
        activeTicks.clear()
        cardProgressViews.clear()
    }
}
