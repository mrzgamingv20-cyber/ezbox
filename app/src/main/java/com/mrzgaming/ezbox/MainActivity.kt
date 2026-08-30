package com.mrzgaming.ezbox

import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.app.AlertDialog
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNavRef: BottomNavigationView
    private lateinit var btnHamburger: ImageButton
    private var menuOpen = false

    fun navigateTo(itemId: Int) {
        bottomNavRef.selectedItemId = itemId
    }

    private val TERMUX_PERMISSION = "com.termux.permission.RUN_COMMAND"
    private val TERMUX_PERMISSION_REQUEST_CODE = 1001
    private val STORAGE_PERMISSION_REQUEST_CODE = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestTermuxPermissionIfNeeded()
        requestAllFilesAccessIfNeeded()

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNavRef = bottomNav
        bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_store -> StoreFragment()
                R.id.nav_terminal -> TerminalFragment()
                R.id.nav_settings -> SettingsFragment()
                else -> HomeFragment()
            }
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                .replace(R.id.fragmentContainer, fragment)
                .commit()
            true
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(R.id.fragmentContainer, HomeFragment()).commit()
        }

        btnHamburger = findViewById(R.id.btnHamburger)
        btnHamburger.setOnClickListener { showMenuDialog() }
    }

    private fun showMenuDialog() {
        // Animasi burger -> X sesaat sebelum dialog muncul
        btnHamburger.animate()
            .rotation(90f)
            .setDuration(180)
            .withEndAction {
                btnHamburger.setImageResource(R.drawable.ic_close)
            }
            .start()

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_menu)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.82).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.setGravity(Gravity.TOP or Gravity.END)
        dialog.window?.setWindowAnimations(android.R.style.Animation_Dialog)
        dialog.window?.attributes = dialog.window?.attributes?.apply {
            y = 90
            x = 16
        }

        dialog.findViewById<TextView>(R.id.menuItemTutorial).setOnClickListener {
            dialog.dismiss()
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                .replace(R.id.fragmentContainer, TutorialFragment())
                .commit()
        }

        dialog.findViewById<TextView>(R.id.menuItemSettings).setOnClickListener {
            dialog.dismiss()
            navigateTo(R.id.nav_settings)
        }

        dialog.findViewById<TextView>(R.id.menuItemAbout).setOnClickListener {
            dialog.dismiss()
            AlertDialog.Builder(this)
                .setTitle("About EZBox")
                .setMessage("EZBox — Your Android desktop environment.\nPowered by Termux backend.\n\nVersion 1.0")
                .setPositiveButton("OK", null)
                .show()
        }

        dialog.setOnDismissListener {
            btnHamburger.animate()
                .rotation(0f)
                .setDuration(180)
                .withEndAction {
                    btnHamburger.setImageResource(R.drawable.ic_hamburger)
                }
                .start()
        }

        dialog.show()
    }

    private fun requestTermuxPermissionIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, TERMUX_PERMISSION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            AlertDialog.Builder(this)
                .setTitle("Termux Access Needed")
                .setMessage("EZBox uses Termux as its backend to run your Linux desktop (XFCE4, VNC server). This permission lets EZBox send commands to Termux to start and stop your desktop environment.\n\nNo commands are sent without your action (e.g. tapping \"Launch Environment\").")
                .setPositiveButton("Continue") { _, _ ->
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(TERMUX_PERMISSION),
                        TERMUX_PERMISSION_REQUEST_CODE
                    )
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun requestAllFilesAccessIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                AlertDialog.Builder(this)
                    .setTitle("Storage Access Needed")
                    .setMessage("EZBox needs access to your device storage to:\n\n• Save crash logs and debug info to your Downloads folder (for troubleshooting)\n• Save desktop screenshots to your Pictures folder\n\nEZBox does not read, upload, or share your personal files. You'll be taken to a system settings page to grant this.")
                    .setPositiveButton("Continue") { _, _ ->
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:$packageName")
                            }
                            startActivityForResult(intent, STORAGE_PERMISSION_REQUEST_CODE)
                        } catch (e: Exception) {
                            val fallbackIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            startActivityForResult(fallbackIntent, STORAGE_PERMISSION_REQUEST_CODE)
                        }
                    }
                    .setNegativeButton("Not now", null)
                    .setCancelable(true)
                    .show()
            }
        }
    }
}
