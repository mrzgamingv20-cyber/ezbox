package com.mrzgaming.ezbox

import android.content.Context

object ThemeManager {
    enum class Theme(val displayName: String, val accentRes: Int, val accentEndRes: Int) {
        INDIGO("Indigo", R.color.ez_primary, R.color.ez_primary_gradient_end),
        ROSE("Rose", R.color.ez_secondary, R.color.ez_secondary_glow),
        EMERALD("Emerald", R.color.ez_tertiary, R.color.ez_tertiary_glow),
        AMBER("Amber", R.color.ez_warm, R.color.ez_warm_glow),
        CYAN("Cyan", R.color.ez_cool, R.color.ez_cool_glow)
    }

    val ALL: List<Theme> = listOf(
        Theme.INDIGO, Theme.ROSE, Theme.EMERALD, Theme.AMBER, Theme.CYAN
    )

    private const val KEY_THEME = "theme_preference"
    private var current: Theme = Theme.INDIGO

    fun init(context: Context) {
        val name = context.getSharedPreferences("EZBoxPrefs", Context.MODE_PRIVATE)
            .getString(KEY_THEME, "Indigo") ?: "Indigo"
        current = ALL.firstOrNull { it.displayName == name } ?: Theme.INDIGO
    }

    fun getCurrent(): Theme = current

    fun setTheme(context: Context, theme: Theme) {
        current = theme
        context.getSharedPreferences("EZBoxPrefs", Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME, theme.displayName).apply()
    }
}
