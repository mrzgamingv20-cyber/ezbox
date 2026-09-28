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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.drawerlayout.widget.DrawerLayout

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNavRef: BottomNavigationView
    private lateinit var btnHamburger: ImageButton
    private lateinit var drawerLayout: DrawerLayout

    fun navigateTo(itemId: Int) {
        bottomNavRef.selectedItemId = itemId
    }

    private val TERMUX_PERMISSION = "com.termux.permission.RUN_COMMAND"
    private val TERMUX_PERMISSION_REQUEST_CODE = 1001
    private val STORAGE_PERMISSION_REQUEST_CODE = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestTermuxPermissionIfNeeded { requestAllFilesAccessIfNeeded() }

        drawerLayout = findViewById(R.id.drawerLayout)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNavRef = bottomNav
        bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_apps -> AppLibraryFragment()
                R.id.nav_files -> FileManagerFragment()
                R.id.nav_store -> StoreFragment()
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
        btnHamburger.setOnClickListener { openDrawer() }

        handleShortcutIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcutIntent(intent)
    }

    private fun handleShortcutIntent(intent: Intent?) {
        when (intent?.getStringExtra("shortcut_action")) {
            "launch_desktop" -> {
                navigateTo(R.id.nav_home)
                window.decorView.postDelayed({
                    val homeFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer) as? HomeFragment
                    homeFragment?.performLaunch()
                }, 200)
            }
            "open_store" -> {
                navigateTo(R.id.nav_store)
            }
        }
    }

    private fun openDrawer() {
        btnHamburger.animate()
            .rotation(90f)
            .setDuration(180)
            .withEndAction {
                btnHamburger.setImageResource(R.drawable.ic_close)
            }
            .start()

        drawerLayout.openDrawer(Gravity.END)

        drawerLayout.addDrawerListener(object : androidx.drawerlayout.widget.DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerClosed(drawerView: android.view.View) {
                btnHamburger.animate()
                    .rotation(0f)
                    .setDuration(180)
                    .withEndAction {
                        btnHamburger.setImageResource(R.drawable.ic_hamburger)
                    }
                    .start()
                drawerLayout.removeDrawerListener(this)
            }
        })

        setupDrawerMenu()
    }

    private fun setupDrawerMenu() {
        val header = drawerLayout.findViewById<LinearLayout>(R.id.drawerHeader)
        if (header == null) {
            val headerView = layoutInflater.inflate(R.layout.drawer_header, null)
            drawerLayout.addView(headerView)
        }

        val menuItems = listOf(
            R.id.menuItemTutorial to {
                drawerLayout.closeDrawers()
                supportFragmentManager.beginTransaction()
                    .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                    .replace(R.id.fragmentContainer, TutorialFragment())
                    .commit()
            },
            R.id.menuItemStopDesktop to {
                drawerLayout.closeDrawers()
                val home = supportFragmentManager.findFragmentById(R.id.fragmentContainer) as? HomeFragment
                if (home != null) {
                    home.stopDesktop()
                } else {
                    TermuxCommand.start(
                        this,
                        "pkill -9 -f 'Xvnc :1 '; pkill -9 -f 'xfce4-session'; pkill -9 -f 'ezos-run'"
                    )
                    Toast.makeText(this, "Desktop stopped", Toast.LENGTH_SHORT).show()
                }
            },
            R.id.menuItemTerminal to {
                drawerLayout.closeDrawers()
                supportFragmentManager.beginTransaction()
                    .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                    .replace(R.id.fragmentContainer, TerminalFragment())
                    .commit()
            },
            R.id.menuItemSettings to {
                drawerLayout.closeDrawers()
                navigateTo(R.id.nav_settings)
            },
            R.id.menuItemTheme to {
                drawerLayout.closeDrawers()
                supportFragmentManager.beginTransaction()
                    .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                    .replace(R.id.fragmentContainer, ThemeFragment())
                    .commit()
            },
            R.id.menuItemAbout to {
                drawerLayout.closeDrawers()
                AlertDialog.Builder(this)
                    .setTitle("About EZBox")
                    .setMessage("EZBox — Your Android desktop environment.\nPowered by Termux backend.\n\nVersion ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    .setPositiveButton("OK", null)
                    .show()
            }
        )

        for ((id, action) in menuItems) {
            drawerLayout.findViewById<TextView>(id)?.setOnClickListener { action() }
        }
    }

    private fun requestTermuxPermissionIfNeeded(then: () -> Unit) {
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
                .setNegativeButton("Not now") { _, _ -> then() }
                .setCancelable(false)
                .show()
        } else {
            then()
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == TERMUX_PERMISSION_REQUEST_CODE) {
            requestAllFilesAccessIfNeeded()
            return
        }
        for (fragment in supportFragmentManager.fragments) {
            fragment.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }
}