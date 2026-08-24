package com.mrzgaming.ezbox

import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable
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
        StorePackage("Wine", "Run Windows applications on EZOS desktop", listOf("wine-staging"), "wine", "🍷", R.color.ezos_icon_rose),
        StorePackage("Box64", "x86_64 binary translation for ARM devices", listOf("box64"), "box64", "📦", R.color.ezos_icon_blue),
        StorePackage("Firefox", "Web browser for the EZOS desktop", listOf("firefox"), "firefox", "🌐", R.color.ezos_icon_amber),
        StorePackage("GIMP", "Image editor", listOf("gimp"), "gimp", "🎨", R.color.ezos_icon_green),
        StorePackage("VLC", "Media player", listOf("vlc"), "vlc", "▶", R.color.ezos_icon_cyan),
        StorePackage("File Manager", "Lightweight graphical file manager (PCManFM)", listOf("pcmanfm"), "pcmanfm", "📁", R.color.ezos_icon_blue)
    )

    private lateinit var prefs: SharedPreferences

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

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Badge ikon bulat berwarna
        val iconBadge = TextView(requireContext()).apply {
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
            setOnClickListener { installPackage(pkg, this) }
        }

        row.addView(iconBadge)
        row.addView(textContainer)
        row.addView(installButton)
        card.addView(row)
        return card
    }

    private fun setButtonState(button: Button, installed: Boolean) {
        button.text = if (installed) "Installed" else "Install"
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
