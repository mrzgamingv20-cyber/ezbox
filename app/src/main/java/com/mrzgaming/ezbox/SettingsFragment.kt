package com.mrzgaming.ezbox

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        val editRes = view.findViewById<EditText>(R.id.editResolution)
        val btnSave = view.findViewById<Button>(R.id.btnSave)

        val prefs = requireActivity().getSharedPreferences("EZBoxPrefs", Context.MODE_PRIVATE)
        editRes.setText(prefs.getString("resolution", "1280x720"))

        btnSave.setOnClickListener {
            prefs.edit().putString("resolution", editRes.text.toString()).apply()
            Toast.makeText(context, "Settings Saved", Toast.LENGTH_SHORT).show()
        }

        return view
    }
}
