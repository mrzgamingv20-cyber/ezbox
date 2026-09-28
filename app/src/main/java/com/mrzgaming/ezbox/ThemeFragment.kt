package com.mrzgaming.ezbox

import android.graphics.Typeface
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
    private lateinit var cardsLayout: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_theme, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ThemeManager.init(requireContext())

        cardsLayout = view.findViewById<LinearLayout>(R.id.themeCards)
        currentDot = view.findViewById(R.id.themeCurrentDot)
        currentName = view.findViewById(R.id.themeCurrentName)

        for (theme in ThemeManager.Theme.entries) {
            cardsLayout.addView(createCard(theme, theme == ThemeManager.getCurrent()))
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
        ).apply { bottomMargin = 12 }
        card.layoutParams = params
        card.setBackgroundResource(R.drawable.ez_card)
        card.setPadding(48, 16, 48, 16)

        val dot = View(requireContext())
        val size = resources.getDimensionPixelSize(R.dimen.theme_dot_size)
        val gap = resources.getDimensionPixelSize(R.dimen.theme_dot_gap)
        val dotParams = LinearLayout.LayoutParams(size, size).apply { rightMargin = gap }
        dot.layoutParams = dotParams
        updateDot(dot, theme)

        val name = TextView(requireContext())
        name.text = theme.displayName
        name.setTextColor(ContextCompat.getColor(requireContext(), R.color.ez_text))
        name.textSize = 16f
        name.setTypeface(null, Typeface.BOLD)
        val nameParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        name.layoutParams = nameParams

        val check = TextView(requireContext())
        check.text = "✓"
        check.setTextColor(ContextCompat.getColor(requireContext(), R.color.ez_primary))
        check.textSize = 20f
        check.setTypeface(null, Typeface.BOLD)
        check.visibility = if (isCurrent) View.VISIBLE else View.GONE

        card.setOnClickListener {
            ThemeManager.setTheme(requireContext(), theme)
            updateIndicator(theme)
            for (i in 0 until cardsLayout.childCount) {
                val c = cardsLayout.getChildAt(i)
                (c as? LinearLayout)?.getChildAt(2)?.visibility = View.GONE
            }
            check.visibility = View.VISIBLE
            Toast.makeText(requireContext(), "Tema ${theme.displayName} aktif", Toast.LENGTH_SHORT).show()
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
        val accentEnd = ContextCompat.getColor(requireContext(), theme.accentEndRes)
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(intArrayOf(colors[idx], accentEnd))
        currentDot.background = bg
        currentName.text = theme.displayName
    }

    private fun updateDot(dot: View, theme: ThemeManager.Theme) {
        val idx = ThemeManager.Theme.entries.indexOfFirst { it == theme }
        if (idx < 0) return
        val colors = resources.getIntArray(R.array.theme_accents)
        val accentEnd = ContextCompat.getColor(requireContext(), theme.accentEndRes)
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(intArrayOf(colors[idx], accentEnd))
        dot.background = bg
    }
}
