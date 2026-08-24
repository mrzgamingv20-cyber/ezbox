package com.mrzgaming.ezbox

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
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
            supportFragmentManager.beginTransaction().replace(R.id.fragmentContainer, fragment).commit()
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
            ActivityCompat.requestPermissions(
                this,
                arrayOf(TERMUX_PERMISSION),
                TERMUX_PERMISSION_REQUEST_CODE
            )
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
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivityForResult(intent, STORAGE_PERMISSION_REQUEST_CODE)
                } catch (e: Exception) {
                    // Fallback untuk device yang tidak support intent spesifik di atas
                    val fallbackIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivityForResult(fallbackIntent, STORAGE_PERMISSION_REQUEST_CODE)
                }
            }
        }
    }
}
