package com.mrzgaming.ezbox

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class StoreFragment : Fragment() {

    private val availablePackages = listOf(
        StorePackage("Wine", "Run Windows applications on EZOS desktop", listOf("wine-staging"), "wine", "🍷", R.color.ezos_icon_rose, category = "Runtime"),
        StorePackage("Box64", "x86_64 binary translation for ARM devices", listOf("box64"), "box64", "📦", R.color.ezos_icon_blue, R.drawable.pkg_box64, category = "Runtime"),
        StorePackage("Firefox", "Web browser for the EZOS desktop", listOf("firefox"), "firefox", "🌐", R.color.ezos_icon_amber, R.drawable.pkg_firefox, category = "Apps"),
        StorePackage("GIMP", "Image editor", listOf("gimp"), "gimp", "🎨", R.color.ezos_icon_green, R.drawable.pkg_gimp, category = "Apps"),
        StorePackage("VLC", "Media player", listOf("vlc"), "vlc", "▶", R.color.ezos_icon_cyan, R.drawable.pkg_vlc, category = "Apps"),
        StorePackage("File Manager", "Lightweight graphical file manager (PCManFM)", listOf("pcmanfm"), "pcmanfm", "📁", R.color.ezos_icon_blue, category = "Tools")
    )

    private val categories = listOf("All", "Runtime", "Apps", "Tools")
    private var selectedCategory = "All"
    private var searchQuery = ""

    private lateinit var prefs: SharedPreferences
    private lateinit var itemContainer: LinearLayout
    private lateinit var pillContainer: LinearLayout
    private lateinit var tvNoResults: TextView
    private val mainHandler = Handler(Looper.getMainLooper())
    private val verifyScope = CoroutineScope(Dispatchers.Main + Job())
    private val activeVerifications = mutableMapOf<String, Boolean>()
    private var installInProgress = false

    private fun prefKeyFor(pkg: StorePackage) = "installed_${pkg.checkBinary}"
    private fun isMarkedInstalled(pkg: StorePackage) = prefs.getBoolean(prefKeyFor(pkg), false)
    private fun markInstalled(pkg: StorePackage) = prefs.edit().putBoolean(prefKeyFor(pkg), true).apply()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        prefs = requireContext().getSharedPreferences("ezbox_store", 0)
        val view = inflater.inflate(R.layout.fragment_store, container, false)
        itemContainer = view.findViewById(R.id.storeItemContainer)
        pillContainer = view.findViewById(R.id.categoryFilterContainer)
        tvNoResults = view.findViewById(R.id.tvNoResults)

        val inputSearch = view.findViewById<EditText>(R.id.inputSearchStore)
        inputSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim() ?: ""
                renderPackages()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        buildPills()
        renderPackages()

        return view
    }

    private fun countFor(category: String): Int {
        return if (category == "All") availablePackages.size
        else availablePackages.count { it.category == category }
    }

    private fun buildPills() {
        pillContainer.removeAllViews()
        for (category in categories) {
            pillContainer.addView(buildPill(category))
        }
    }

    private fun buildPill(category: String): LinearLayout {
        val isSelected = category == selectedCategory
        val pill = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(28, 16, 20, 16)
            background = ContextCompat.getDrawable(
                requireContext(),
                if (isSelected) R.drawable.pill_selected_bg else R.drawable.pill_unselected_bg
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 12 }
            isClickable = true
            isFocusable = true
        }

        val label = TextView(requireContext()).apply {
            text = category
            textSize = 13f
            setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (isSelected) R.color.ezos_bg_black else R.color.ezos_text_secondary
                )
            )
            if (isSelected) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        val badge = TextView(requireContext()).apply {
            text = countFor(category).toString()
            textSize = 11f
            setPadding(14, 4, 14, 4)
            setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (isSelected) R.color.ezos_bg_black else R.color.ezos_text_secondary
                )
            )
            background = ContextCompat.getDrawable(requireContext(), R.drawable.pill_badge_bg)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = 8 }
        }

        pill.addView(label)
        pill.addView(badge)

        pill.setOnClickListener {
            if (selectedCategory != category) {
                selectedCategory = category
                buildPills()
                renderPackages()
            }
        }

        return pill
    }

    private fun renderPackages() {
        itemContainer.removeAllViews()

        var filtered = if (selectedCategory == "All") availablePackages
        else availablePackages.filter { it.category == selectedCategory }

        if (searchQuery.isNotBlank()) {
            filtered = filtered.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.description.contains(searchQuery, ignoreCase = true)
            }
        }

        if (filtered.isEmpty()) {
            tvNoResults.visibility = View.VISIBLE
        } else {
            tvNoResults.visibility = View.GONE
            for (pkg in filtered) {
                itemContainer.addView(buildPackageCard(pkg))
            }
        }
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
            if (!isMarkedInstalled(pkg)) {
                installPackage(pkg, installButton, progressSection, progressBar, statusText)
            }
        }

        outerColumn.addView(row)
        outerColumn.addView(progressSection)
        card.addView(outerColumn)
        return card
    }

    private fun setButtonState(button: Button, installed: Boolean) {
        button.text = if (installed) "Installed" else "Install"
        button.isEnabled = !installed
        button.alpha = if (installed) 0.6f else 1.0f
    }

    private fun installPackage(
        pkg: StorePackage,
        button: Button,
        progressSection: LinearLayout,
        progressBar: ProgressBar,
        statusText: TextView
    ) {
        if (activeVerifications[pkg.checkBinary] == true) {
            Toast.makeText(context, "Installation already in progress for ${pkg.name}", Toast.LENGTH_SHORT).show()
            return
        }
        if (isMarkedInstalled(pkg)) {
            Toast.makeText(context, "${pkg.name} is already installed", Toast.LENGTH_SHORT).show()
            return
        }

        activeVerifications[pkg.checkBinary] = true
        installInProgress = true
        button.isEnabled = false
        button.text = "Installing..."
        progressSection.visibility = View.VISIBLE
        progressBar.progress = 0
        statusText.text = "Installing..."

        val pkgList = pkg.pkgNames.joinToString(" ")
        val command = "pkg install -y $pkgList && echo INSTALL_SUCCESS_${pkg.checkBinary}"

        try {
            TermuxCommand.start(requireContext(), command)

            verifyInstallation(pkg, progressBar, statusText, button, progressSection)
        } catch (e: Exception) {
            Log.e("StoreFragment", "Install failed: ${e.message}")
            Toast.makeText(context, "Failed to start install: ${e.message}", Toast.LENGTH_SHORT).show()
            cleanupInstall(progressSection, progressBar, statusText, button, pkg)
        }
    }

    private fun verifyInstallation(
        pkg: StorePackage,
        progressBar: ProgressBar,
        statusText: TextView,
        button: Button,
        progressSection: LinearLayout
    ) {
        var checkCount = 0
        val maxChecks = 40
        val checkIntervalMs = 3000L

        verifyScope.launch {
            while (checkCount < maxChecks) {
                delay(checkIntervalMs)
                checkCount++

                val progress = ((checkCount.toFloat() / maxChecks) * 100).toInt()
                val finalProgress = progress.coerceIn(0, 99)

                mainHandler.post {
                    progressBar.progress = finalProgress
                    statusText.text = "Verifying ${pkg.name}... (${checkCount}/${maxChecks})"
                }

                if (isPackageInstalled(pkg)) {
                    mainHandler.post {
                        activeVerifications[pkg.checkBinary] = false
                        installInProgress = false
                        progressBar.progress = 100
                        statusText.text = "Installation complete!"
                        markInstalled(pkg)
                        setButtonState(button, true)
                        button.isEnabled = true
                        Toast.makeText(context, "${pkg.name} installed!", Toast.LENGTH_SHORT).show()
                        progressSection.postDelayed({ progressSection.visibility = View.GONE }, 1500)
                    }
                    return@launch
                }
            }

            mainHandler.post {
                activeVerifications[pkg.checkBinary] = false
                installInProgress = false
                progressBar.progress = 0
                statusText.text = "Timeout - installation may have failed"
                Toast.makeText(context, "${pkg.name}: Timeout waiting for verification. Try checking in the Terminal tab.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun isPackageInstalled(pkg: StorePackage): Boolean {
        return try {
            val file = java.io.File("/data/data/com.termux/files/usr/bin/${pkg.checkBinary}")
            if (!file.exists() || !file.canExecute()) return false
            val verifyCommand = "command -v ${pkg.checkBinary} >/dev/null 2>&1 && echo VERIFIED"
            val intent = TermuxCommand.execute(requireContext(), verifyCommand, background = false)
            try {
                requireContext().startService(intent)
                true
            } catch (e: Exception) {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun cleanupInstall(
        progressSection: LinearLayout,
        progressBar: ProgressBar,
        statusText: TextView,
        button: Button,
        pkg: StorePackage
    ) {
        activeVerifications[pkg.checkBinary] = false
        installInProgress = false
        progressSection.visibility = View.GONE
        button.isEnabled = true
        setButtonState(button, isMarkedInstalled(pkg))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mainHandler.removeCallbacksAndMessages(null)
        verifyScope.coroutineContext[Job]?.cancel()
        activeVerifications.clear()
    }
}