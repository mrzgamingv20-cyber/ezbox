package com.mrzgaming.ezbox

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionsFragment : Fragment() {

    private fun debugLog(msg: String) {
        try {
            val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            val file = File("/storage/emulated/0/Download/ezbox_debug.log")
            file.appendText("[$ts] [Sessions] $msg\n")
        } catch (e: Exception) {
            android.util.Log.e("EZBox", "debugLog failed: ${e.message}")
        }
    }

    private lateinit var sessionManager: SessionManager
    private lateinit var adapter: SessionAdapter
    private val resolutions = listOf("800x480", "960x540", "1280x720", "1600x900")
    private val wineVariants = listOf("wine-staging", "wine")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_sessions, container, false)
        sessionManager = SessionManager(requireContext())

        val recyclerView = view.findViewById<RecyclerView>(R.id.sessionRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = SessionAdapter(
            sessions = sessionManager.getAllSessions(),
            onLaunch = { launchSession(it) },
            onEdit = { showEditDialog(it) },
            onDelete = { deleteSession(it) }
        )
        recyclerView.adapter = adapter

        view.findViewById<FloatingActionButton>(R.id.fabAddSession).setOnClickListener {
            showCreateDialog()
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        adapter.updateData(sessionManager.getAllSessions())
    }

    private fun showCreateDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_session_edit, null)
        setupSpinners(dialogView)

        AlertDialog.Builder(requireContext())
            .setTitle("New Session")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val name = dialogView.findViewById<EditText>(R.id.inputName).text.toString().ifBlank { "New Session" }
                val resolution = dialogView.findViewById<Spinner>(R.id.spinnerResolution).selectedItem as String
                val wineVariant = dialogView.findViewById<Spinner>(R.id.spinnerWineVariant).selectedItem as String

                val session = sessionManager.createNewSession(name)
                session.resolution = resolution
                session.wineVariant = wineVariant
                sessionManager.saveSession(session)
                refreshList()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditDialog(session: EzSession) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_session_edit, null)
        setupSpinners(dialogView)

        dialogView.findViewById<EditText>(R.id.inputName).setText(session.name)
        dialogView.findViewById<Spinner>(R.id.spinnerResolution).setSelection(resolutions.indexOf(session.resolution).coerceAtLeast(0))
        dialogView.findViewById<Spinner>(R.id.spinnerWineVariant).setSelection(wineVariants.indexOf(session.wineVariant).coerceAtLeast(0))

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Session")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                session.name = dialogView.findViewById<EditText>(R.id.inputName).text.toString().ifBlank { session.name }
                session.resolution = dialogView.findViewById<Spinner>(R.id.spinnerResolution).selectedItem as String
                session.wineVariant = dialogView.findViewById<Spinner>(R.id.spinnerWineVariant).selectedItem as String
                sessionManager.saveSession(session)
                refreshList()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupSpinners(dialogView: View) {
        val resolutionAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, resolutions)
        dialogView.findViewById<Spinner>(R.id.spinnerResolution).adapter = resolutionAdapter

        val wineAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, wineVariants)
        dialogView.findViewById<Spinner>(R.id.spinnerWineVariant).adapter = wineAdapter
    }

    private fun deleteSession(session: EzSession) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Session")
            .setMessage("Delete '${session.name}'?")
            .setPositiveButton("Delete") { _, _ ->
                sessionManager.deleteSession(session.id)
                refreshList()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun launchSession(session: EzSession) {
        debugLog("launchSession clicked: ${session.name}")
        sessionManager.markUsed(session.id)

        val command = "WINE_VARIANT=${session.wineVariant} EZBOX_RES=${session.resolution} ezos-run EZOS"
        debugLog("Command: $command")

        try {
            val intent = Intent().apply {
                action = "com.termux.RUN_COMMAND"
                component = ComponentName("com.termux", "com.termux.app.RunCommandService")
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            }
            debugLog("Calling startForegroundService...")
            ContextCompat.startForegroundService(requireContext(), intent)
            debugLog("startForegroundService returned OK, scheduling launch in 5s")

            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                debugLog("postDelayed fired, isAdded=$isAdded")
                if (isAdded) {
                    debugLog("Starting VncActivity from Sessions")
                    startActivity(Intent(requireContext(), VncActivity::class.java))
                }
            }, 5000)
        } catch (e: Exception) {
            debugLog("EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
