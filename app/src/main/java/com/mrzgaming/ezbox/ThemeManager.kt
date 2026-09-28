package com.mrzgaming.ezbox

import android.content.Context

object ThemeManager {
    enum class Theme(val displayName: String, val accentRes: Int, val accentEndRes: Int) {
        CLASSIC_DARK("Classic Dark", R.color.ez_primary, R.color.ez_primary_gradient_end),
        AMOLED("AMOLED", R.color.ez_primary, R.color.ez_primary_gradient_end),
        OCEAN("Ocean", R.color.ez_info, R.color.ez_primary_gradient_end)
    }

    val ALL: List<Theme> = listOf(CLASSIC_DARK, AMOLED, OCEAN)

    private const val KEY_THEME = "theme_preference"
    private var current: Theme = CLASSIC_DARK

    fun init(context: Context) {
        val name = context.getSharedPreferences("EZBoxPrefs", Context.MODE_PRIVATE)
            .getString(KEY_THEME, "Classic Dark") ?: "Classic Dark"
        current = ALL.firstOrNull { it.displayName == name } ?: CLASSIC_DARK
    }

    fun getCurrent(): Theme = current

    fun setTheme(context: Context, theme: Theme) {
        current = theme
        context.getSharedPreferences("EZBoxPrefs", Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME, theme.displayName).apply()
    }
}