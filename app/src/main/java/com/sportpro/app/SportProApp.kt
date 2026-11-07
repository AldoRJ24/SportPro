package com.sportpro.app

import android.app.Application
import com.sportpro.app.core.AppContainer

class SportProApp : Application() {
    /** Null only if local.properties is missing the Supabase keys. */
    var container: AppContainer? = null
        private set

    override fun onCreate() {
        super.onCreate()
        if (SupabaseProvider.isConfigured) container = AppContainer(this)
    }
}
