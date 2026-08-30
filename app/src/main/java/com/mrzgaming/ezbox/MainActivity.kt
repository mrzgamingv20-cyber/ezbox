package com.mrzgaming.ezbox

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNavRef: BottomNavigationView

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

    /**
     * MANAGE_EXTERNAL_STORAGE (All Files Access) adalah "special permission" di Android 11+
     * yang TIDAK bisa diminta lewat ActivityCompat.requestPermissions biasa — harus lewat
     * halaman Settings khusus. Tanpa ini, membaca file di /storage/emulated/0/Download/
     * (dipakai CrashHandler dan debug log) akan gagal dengan EACCES walau sudah dideklarasikan
     * di manifest.
     */
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
