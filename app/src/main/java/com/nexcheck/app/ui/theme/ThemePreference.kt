package com.nexcheck.app.ui.theme

import android.content.Context
import androidx.core.content.edit

/** Preferência de tema escolhida pelo usuário (salva no aparelho). */
enum class ThemeMode(val label: String) {
    SYSTEM("Sistema"),
    LIGHT("Claro"),
    DARK("Escuro")
}

object ThemePreference {
    private const val PREFS = "nexcheck_prefs"
    private const val KEY = "theme_mode"

    fun load(context: Context): ThemeMode {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        return ThemeMode.entries.firstOrNull { it.name == saved } ?: ThemeMode.SYSTEM
    }

    fun save(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, mode.name) }
    }
}
