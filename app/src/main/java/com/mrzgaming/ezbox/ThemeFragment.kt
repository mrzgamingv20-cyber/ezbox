package com.mrzgaming.ezbox

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class ThemeFragment : Fragment() {

    private lateinit var currentDot: View
    private lateinit var currentName: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_theme, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ThemeManager.init(requireContext())

        val cards = view.findViewById<LinearLayout>(R.id.themeCards)
        currentDot = view.findViewById(R.id.themeCurrentDot)
        currentName = view.findViewById(R.id.themeCurrentName)

        for (theme in ThemeManager.Theme.entries) {
            val isCurrent = theme == ThemeManager.getCurrent()
            cards.addView(createCard(theme, isCurrent))
        }
        updateIndicator(ThemeManager.getCurrent())
    }

    private fun createCard(theme: ThemeManager.Theme, isCurrent: Boolean): View {
        val card = LinearLayout(requireContext())
        card.orientation = LinearLayout.HORIZONTAL
        card.gravity = Gravity.CENTER_VERTICAL
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.bottomMargin = 12
        card.layoutParams = params
        card.setBackgroundResource(R.drawable.ez_card)
        card.setPadding(48, 16, 48, 16)
        card.tag = theme

        val dot = View(requireContext())
        val size = resources.getDimensionPixelSize(R.dimen.theme_dot_size)
        val gap = resources.getDimensionPixelSize(R.dimen.theme_dot_gap)
        val dotParams = ViewGroup.LayoutParams(size, size)
        dotParams.rightMargin = gap
        dot.layoutParams = dotParams
        updateDot(dot, theme)

        val name = TextView(requireContext())
        name.text = theme.name
        name.setTextColor(ContextCompat.getColor(requireContext(), R.color.ez_text))
        name.textSize = 16f
        name.textStyle = android.graphics.Typeface.BOLD
        val nameParams = LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        )
        name.layoutParams = nameParams

        val check = TextView(requireContext())
        check.text = "✓"
        check.setTextColor(ContextCompat.getColor(requireContext(), R.color.ez_primary))
        check.textSize = 20f
        check.textStyle = android.graphics.Typeface.BOLD
        check.visibility = if (isCurrent) View.VISIBLE else View.GONE

        card.setOnClickListener {
            ThemeManager.setTheme(requireContext(), theme)
            updateIndicator(theme)
            cards.children.forEach { c ->
                (c as? LinearLayout)?.getChildAt(2)?.visibility = View.GONE
            }
            check.visibility = View.VISIBLE
            Toast.makeText(requireContext(), "Tema ${theme.name} aktif", Toast.LENGTH_SHORT).show()
        }

        card.addView(dot)
        card.addView(name)
        card.addView(check)
        return card
    }

    private fun updateIndicator(theme: ThemeManager.Theme) {
        val idx = ThemeManager.Theme.entries.indexOfFirst { it == theme }
        if (idx < 0) return
        val colors = resources.getIntArray(R.array.theme_accents)
        val dot = currentDot
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        val accentEnd = ContextCompat.getColor(requireContext(), theme.accentEndRes)
        bg.setColor(intArrayOf(colors[idx], accentEnd))
        dot.background = bg
        currentName.text = theme.name
    }

    private fun updateDot(dot: View, theme: ThemeManager.Theme) {
        val idx = ThemeManager.Theme.entries.indexOfFirst { it == theme }
        if (idx < 0) return
        val colors = resources.getIntArray(R.array.theme_accents)
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        val accentEnd = ContextCompat.getColor(requireContext(), theme.accentEndRes)
        bg.setColor(intArrayOf(colors[idx], accentEnd))
        dot.background = bg
    }
}
