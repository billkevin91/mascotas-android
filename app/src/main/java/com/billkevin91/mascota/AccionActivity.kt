package com.billkevin91.mascota

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

/**
 * Atajos del icono de la app (mantenerlo pulsado): "Dar de comer" y "Otra mascota".
 * No tiene pantalla: hace la acción y se cierra.
 */
class AccionActivity : Activity() {
    companion object {
        const val COMER = "com.billkevin91.mascota.atajo.COMER"
        const val OTRA = "com.billkevin91.mascota.atajo.OTRA"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent.action) {
            COMER -> MainActivity.feed(this)
            OTRA -> {
                if (Settings.canDrawOverlays(this)) {
                    val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                    val pet = prefs.getString(MainActivity.PREF_PET, null)?.takeIf { PetLibrary.exists(this, it) }
                        ?: PetLibrary.BUNDLED.first()
                    PetService.add(this, pet, prefs.getFloat(MainActivity.PREF_SIZE, 1f))
                } else {
                    Toast.makeText(this, "Primero da permiso para mostrarse sobre otras apps", Toast.LENGTH_LONG).show()
                    startActivity(Intent(this, MainActivity::class.java))
                }
            }
        }
        finish()
    }
}
