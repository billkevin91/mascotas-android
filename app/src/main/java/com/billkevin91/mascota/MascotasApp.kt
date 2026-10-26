package com.billkevin91.mascota

import android.app.Application
import android.content.res.Configuration

/** Elige el idioma al arrancar y si lo cambias en los ajustes del teléfono. */
class MascotasApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Lang.init()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Lang.init()
    }
}
