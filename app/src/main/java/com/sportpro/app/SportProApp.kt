package com.sportpro.app

import android.app.Application
import com.sportpro.app.core.AppContainer
import com.sportpro.app.core.AppText
import com.sportpro.app.ui.theme.ThemeSettings

class SportProApp : Application() {
    /** Es null solo si en local.properties faltan las claves de Supabase. */
    var container: AppContainer? = null
        private set

    override fun onCreate() {
        super.onCreate()
        // Textos de strings.xml disponibles para ViewModels y utilidades (antes que cualquier pantalla)
        AppText.install(this)
        // Apariencia guardada (claro por defecto): se lee antes de dibujar para no parpadear
        ThemeSettings.install(this)
        if (SupabaseProvider.isConfigured) container = AppContainer(this)
    }
}
