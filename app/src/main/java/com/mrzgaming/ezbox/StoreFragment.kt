package com.mrzgaming.ezbox

import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
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

class StoreFragment : Fragment() {

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

    private lateinit var prefs: SharedPreferences

    private fun prefKeyFor(pkg: StorePackage) = "installed_${pkg.checkBinary}"

    private fun isMarkedInstalled(pkg: StorePackage): Boolean =
        prefs.getBoolean(prefKeyFor(pkg), false)

    private fun markInstalled(pkg: StorePackage) {
        prefs.edit().putBoolean(prefKeyFor(pkg), true).apply()
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
            setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.ezos_card_bg))
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
            setTextColor(ContextCompat.getColor(requireContext(), R.color.ezos_text_primary))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        val descView = TextView(requireContext()).apply {
            text = pkg.description
            textSize = 13f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.ezos_text_secondary))
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
        card.addView(row)

        return card
    }

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
            Toast.makeText(context, "Installing ${pkg.name} in Termux...", Toast.LENGTH_SHORT).show()

            markInstalled(pkg)

            button.postDelayed({
                button.isEnabled = true
                setButtonState(button, true)
            }, 15000)
        } catch (e: Exception) {
            Log.e("StoreFragment", "Install failed: ${e.message}")
            Toast.makeText(context, "Failed to start install: ${e.message}", Toast.LENGTH_SHORT).show()
            button.isEnabled = true
            setButtonState(button, isMarkedInstalled(pkg))
        }
    }
}
