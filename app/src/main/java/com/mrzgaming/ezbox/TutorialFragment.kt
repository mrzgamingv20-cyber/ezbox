package com.mrzgaming.ezbox

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import androidx.fragment.app.Fragment

class TutorialFragment : Fragment() {

    data class TutorialStep(val title: String, val body: String, val actionLabel: String, val actionUrl: String? = null)

    private val steps = listOf(
        TutorialStep(
            "1. Launch Environment",
            "Tap 'Launch Environment' on the Home tab to start your EZOS desktop. First launch will set up the backend automatically.",
            "Got it"
        ),
        TutorialStep(
            "2. Open the Desktop",
            "Once the backend is running, tap 'Backend Status' card to open the VNC desktop view.",
            "Got it"
        ),
        TutorialStep(
            "3. Install Apps",
            "Go to the Store tab to install Wine, Box64, or other apps directly into your EZOS environment.",
            "Got it"
        ),
        TutorialStep(
            "4. Customize Settings",
            "Adjust resolution, mouse mode, and VNC password from the Settings tab to fit your workflow.",
            "Got it"
        ),
        TutorialStep(
            "5. Use the Terminal",
            "Need raw shell access? The Terminal tab opens Termux directly for advanced tasks.",
            "Got it"
        ),
        TutorialStep(
            "6. Display Backend: VNC (Built-in)",
            "VNC is the default backend, no extra app needed. It works out of the box but may feel slightly less responsive than a native X11 connection. Good choice if you want a simple, self-contained setup.",
            "Got it"
        ),
        TutorialStep(
            "7. Display Backend: Termux:X11 (External)",
            "Termux:X11 gives a more responsive, native-feeling desktop, but requires installing the separate Termux:X11 app first. EZBox does not bundle it - install it from F-Droid, then select \"Termux:X11 (External)\" in Settings > Display Backend.",
            "Get Termux:X11",
            "https://f-droid.org/packages/com.termux.x11/"
        )
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_tutorial, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val container = view.findViewById<android.widget.LinearLayout>(R.id.tutorialContainer)

        for (step in steps) {
            val card = layoutInflater.inflate(R.layout.item_tutorial_card, container, false)
            card.findViewById<android.widget.TextView>(R.id.tvCardTitle).text = step.title
            card.findViewById<android.widget.TextView>(R.id.tvCardBody).text = step.body
            val btn = card.findViewById<android.widget.Button>(R.id.btnCardAction)
            btn.text = step.actionLabel
            btn.setOnClickListener {
                if (step.actionUrl != null) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(step.actionUrl)))
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Couldn't open link: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Noted!", Toast.LENGTH_SHORT).show()
                }
            }
            container.addView(card)
        }
    }
}
