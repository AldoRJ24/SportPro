package com.sportpro.app.ui.theme

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Apariencia elegida en Perfil → Apariencia. Por defecto la app se ve en modo claro. */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

/**
 * Guarda la apariencia en el propio celular (no en Supabase: es una preferencia de este
 * dispositivo, no de la cuenta). Se expone como StateFlow para que el tema cambie al instante,
 * sin reiniciar la app.
 */
object ThemeSettings {
    private const val FILE = "sportpro_ui"
    private const val KEY = "theme_mode"

    private var prefs: SharedPreferences? = null
    private val _mode = MutableStateFlow(ThemeMode.LIGHT)
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    /** Se llama una vez al abrir la app (SportProApp), antes de dibujar cualquier pantalla. */
    fun install(context: Context) {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = p
        // Un valor desconocido (por ejemplo de una versión vieja) vuelve al modo claro
        _mode.value = runCatching { ThemeMode.valueOf(p.getString(KEY, null) ?: "") }.getOrDefault(ThemeMode.LIGHT)
    }

    fun set(mode: ThemeMode) {
        _mode.value = mode
        prefs?.edit()?.putString(KEY, mode.name)?.apply()
    }
}
