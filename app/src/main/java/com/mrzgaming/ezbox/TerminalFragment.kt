package com.mrzgaming.ezbox

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class TerminalFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_terminal, container, false)
        val terminalView = view.findViewById<TextView>(R.id.terminalView)
        
        // Simplified terminal simulation: in a real app, integrate a Terminal Emulator view
        terminalView.text = "EZOS Terminal v1.0\nType 'ezpkg' to manage packages.\n\nroot@ezos:~# "
        
        return view
    }
}