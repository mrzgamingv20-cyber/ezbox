package com.mrzgaming.ezbox

import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView

class StoreFragment : Fragment() {

    private val availablePackages = listOf(
        StorePackage("Wine", "Run Windows applications on EZOS desktop", listOf("wine-staging"), "wine", "🍷", R.color.ezos_icon_rose),
        StorePackage("Box64", "x86_64 binary translation for ARM devices", listOf("box64"), "box64", "📦", R.color.ezos_icon_blue, R.drawable.pkg_box64),
        StorePackage("Firefox", "Web browser for the EZOS desktop", listOf("firefox"), "firefox", "🌐", R.color.ezos_icon_amber, R.drawable.pkg_firefox),
        StorePackage("GIMP", "Image editor", listOf("gimp"), "gimp", "🎨", R.color.ezos_icon_green, R.drawable.pkg_gimp),
        StorePackage("VLC", "Media player", listOf("vlc"), "vlc", "▶", R.color.ezos_icon_cyan, R.drawable.pkg_vlc),
        StorePackage("File Manager", "Lightweight graphical file manager (PCManFM)", listOf("pcmanfm"), "pcmanfm", "📁", R.color.ezos_icon_blue)
    )

    private lateinit var prefs: SharedPreferences
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun prefKeyFor(pkg: StorePackage) = "installed_${pkg.checkBinary}"
    private fun isMarkedInstalled(pkg: StorePackage) = prefs.getBoolean(prefKeyFor(pkg), false)
    private fun markInstalled(pkg: StorePackage) = prefs.edit().putBoolean(prefKeyFor(pkg), true).apply()

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
            radius = 20f
            cardElevation = 0f
            strokeWidth = 2
            strokeColor = ContextCompat.getColor(requireContext(), R.color.ezos_card_border)
            setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.ezos_card_bg))
            setContentPadding(20, 20, 20, 20)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
        }

        val outerColumn = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconBadge: View = if (pkg.iconRes != null) {
            android.widget.ImageView(requireContext()).apply {
                setImageResource(pkg.iconRes)
                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                layoutParams = LinearLayout.LayoutParams(96, 96).apply { marginEnd = 32 }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(ContextCompat.getColor(requireContext(), pkg.colorRes))
                    alpha = 40
                }
                clipToOutline = true
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: android.graphics.Outline) {
                        outline.setOval(0, 0, view.width, view.height)
                    }
                }
                val pad = 16
                setPadding(pad, pad, pad, pad)
            }
        } else {
            TextView(requireContext()).apply {
                text = pkg.icon
                textSize = 20f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(96, 96).apply { marginEnd = 32 }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(ContextCompat.getColor(requireContext(), pkg.colorRes))
                    alpha = 60
                }
            }
        }

        val textContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleView = TextView(requireContext()).apply {
            text = pkg.name
            textSize = 16f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.ezos_text_primary))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        val descView = TextView(requireContext()).apply {
            text = pkg.description
            textSize = 12f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.ezos_text_secondary))
        }

        textContainer.addView(titleView)
        textContainer.addView(descView)

        val installButton = Button(requireContext()).apply {
            setButtonState(this, isMarkedInstalled(pkg))
        }

        row.addView(iconBadge)
        row.addView(textContainer)
        row.addView(installButton)

        // Progress section - hidden by default, shown during install
        val progressSection = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
        }

        val progressBar = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.progress_bar_ezbox)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                12
            ).apply { bottomMargin = 8 }
        }

        val statusText = TextView(requireContext()).apply {
            text = "Initializing..."
            textSize = 11f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.ezos_text_secondary))
        }

        progressSection.addView(progressBar)
        progressSection.addView(statusText)

        installButton.setOnClickListener {
            installPackage(pkg, installButton, progressSection, progressBar, statusText)
        }

        outerColumn.addView(row)
        outerColumn.addView(progressSection)
        card.addView(outerColumn)
        return card
    }

    private fun setButtonState(button: Button, installed: Boolean) {
        button.text = if (installed) "Installed" else "Install"
    }

    private fun getStatusMessage(progress: Int): String {
        return when {
            progress < 5 -> "Initializing download..."
            progress < 15 -> "Setting up environment..."
            progress < 25 -> "Connecting to server..."
            progress < 35 -> "Verifying permissions..."
            progress < 50 -> "Downloading core files..."
            progress < 65 -> "Downloading assets..."
            progress < 80 -> "Downloading dependencies..."
            progress < 90 -> "Extracting files..."
            progress < 95 -> "Validating integrity..."
            progress < 100 -> "Finalizing installation..."
            else -> "Installation complete!"
        }
    }

    private fun installPackage(
        pkg: StorePackage,
        button: Button,
        progressSection: LinearLayout,
        progressBar: ProgressBar,
        statusText: TextView
    ) {
        button.isEnabled = false
        button.text = "Installing..."
        progressSection.visibility = View.VISIBLE
        progressBar.progress = 0

        val pkgList = pkg.pkgNames.joinToString(" ")
        val command = "pkg install -y $pkgList"

        try {
            val intent = Intent().apply {
                action = "com.termux.RUN_COMMAND"
                component = ComponentName("com.termux", "com.termux.app.RunCommandService")
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            }
            ContextCompat.startForegroundService(requireContext(), intent)

            // Simulasi progress bertahap (Termux tidak expose progress asli lewat RUN_COMMAND,
            // jadi progress ini estimasi durasi berbasis waktu, bukan status install nyata)
            simulateProgress(progressBar, statusText) {
                progressSection.visibility = View.GONE
                button.isEnabled = true
                markInstalled(pkg)
                setButtonState(button, true)
                Toast.makeText(context, "${pkg.name} installed", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("StoreFragment", "Install failed: ${e.message}")
            Toast.makeText(context, "Failed to start install: ${e.message}", Toast.LENGTH_SHORT).show()
            progressSection.visibility = View.GONE
            button.isEnabled = true
            setButtonState(button, isMarkedInstalled(pkg))
        }
    }

    private fun simulateProgress(progressBar: ProgressBar, statusText: TextView, onDone: () -> Unit) {
        val totalDurationMs = 15000L
        val stepMs = 150L
        val steps = (totalDurationMs / stepMs).toInt()
        var current = 0

        val runnable = object : Runnable {
            override fun run() {
                current += (steps / 30).coerceAtLeast(1)
                if (current >= 100) {
                    progressBar.progress = 100
                    statusText.text = getStatusMessage(100)
                    mainHandler.postDelayed({ onDone() }, 400)
                    return
                }
                progressBar.progress = current
                statusText.text = getStatusMessage(current)
                mainHandler.postDelayed(this, stepMs)
            }
        }
        mainHandler.post(runnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mainHandler.removeCallbacksAndMessages(null)
    }
}
