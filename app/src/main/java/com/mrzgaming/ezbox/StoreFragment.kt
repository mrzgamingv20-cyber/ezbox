package com.mrzgaming.ezbox

import android.content.ComponentName
import android.content.Intent
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
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
            text = "Install"
            setOnClickListener {
                installPackage(pkg, this)
            }
        }

        row.addView(textContainer)
        row.addView(installButton)
        card.addView(row)

        return card
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
            requireContext().startService(intent)
            Toast.makeText(context, "Installing ${pkg.name} in Termux...", Toast.LENGTH_SHORT).show()

            // Re-enable tombol setelah beberapa saat (kita tidak punya cara langsung tahu kapan pkg install selesai
            // tanpa polling tambahan, jadi kasih waktu wajar lalu izinkan coba lagi/cek manual)
            button.postDelayed({
                button.isEnabled = true
                button.text = "Install"
            }, 15000)
        } catch (e: Exception) {
            Log.e("StoreFragment", "Install failed: ${e.message}")
            Toast.makeText(context, "Failed to start install: ${e.message}", Toast.LENGTH_SHORT).show()
            button.isEnabled = true
            button.text = "Install"
        }
    }
}
