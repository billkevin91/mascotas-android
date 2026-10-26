package com.billkevin91.mascota

import java.util.Locale

/**
 * Idioma de la app: español si el teléfono está en español; si no, inglés.
 * Los textos van juntos en el código, `t("Soltar", "Release")`, para no perder ninguno.
 */
object Lang {
    @Volatile var english = false

    fun init(locale: Locale = Locale.getDefault()) {
        english = locale.language != "es"
    }

    fun t(es: String, en: String) = if (english) en else es
}

/** Atajo de [Lang.t]: `tr("Guardar", "Save")`. */
fun tr(es: String, en: String) = Lang.t(es, en)
