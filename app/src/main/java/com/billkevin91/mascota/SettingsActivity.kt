package com.billkevin91.mascota

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.roundToInt

/** Ajustes: cuántas mascotas, volumen, cuándo ocultarlas y cómo se comportan. */
class SettingsActivity : Activity() {
    companion object {
        /** Máximo de mascotas que se pueden soltar a la vez (sin contar crías). */
        const val PREF_MAX = "max_pets"
        const val MAX_PETS = 12
        /** Volumen de los sonidos, de 0 a 100. */
        const val PREF_VOLUME = "volume"
        /** Ocultarlas mientras una app ocupa toda la pantalla (juegos, vídeos). */
        const val PREF_FULLSCREEN = "hide_fullscreen"
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
    private val prefs by lazy { getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE) }
    private val t = Lang::t

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = t("Ajustes", "Settings")
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(24))
        }
        col.addView(TextView(this).apply {
            text = t("Ajustes", "Settings")
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(62, 125, 46))
        })

        col.addView(heading(t("Mascotas", "Pets")))
        col.addView(slider(t("Máximo a la vez", "Maximum at once"), PREF_MAX, 1, MAX_PETS, MAX_PETS) { "$it" })
        col.addView(slider(t("Volumen de los sonidos", "Sound volume"), PREF_VOLUME, 0, 100, 60) { "$it %" })

        col.addView(heading(t("Cuándo se ven", "When they show")))
        col.addView(check(t("Ocultarlas en juegos y vídeos a pantalla completa", "Hide them during full-screen games and videos"),
            PREF_FULLSCREEN, true))
        col.addView(small(t("Vuelven solas al salir de la pantalla completa. Con la pantalla apagada o bloqueada también se esperan.",
            "They come back on their own when you leave full screen. They also wait while the screen is off or locked.")))
        col.addView(check(t("Ahorro de batería: se mueven a pasos, sin deslizarse (también con el ahorro de batería del teléfono)",
            "Battery saver: they move in steps, without gliding (also when the phone's battery saver is on)"),
            MainActivity.PREF_SAVER, false))

        col.addView(heading(t("Cómo se portan", "How they behave")))
        col.addView(check(t("Reaccionan al teléfono: se alegran al cargarlo (⚡) y les entra sueño con la batería baja (😴)",
            "React to the phone: happy when charging (⚡), sleepy when the battery is low (😴)"),
            MainActivity.PREF_REACT, true))
        col.addView(check(t("Según la hora: de noche duermen más y por la mañana están más activas",
            "Follow the clock: they sleep more at night and are livelier in the morning"),
            MainActivity.PREF_CLOCK, true))

        setContentView(ScrollView(this).apply { fitsSystemWindows = true; addView(col) })
    }

    private fun heading(s: String) = TextView(this).apply {
        text = s
        textSize = 17f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(18), 0, dp(4))
    }

    private fun small(s: String) = TextView(this).apply { text = s; textSize = 13f; alpha = 0.7f }

    private fun check(label: String, key: String, default: Boolean) = CheckBox(this).apply {
        text = label
        isChecked = prefs.getBoolean(key, default)
        setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(key, on).apply() }
        setPadding(0, dp(8), 0, dp(8))
    }

    /** Una barra con su valor; se guarda al soltarla. */
    private fun slider(label: String, key: String, min: Int, max: Int, default: Int, show: (Int) -> String): LinearLayout {
        val value = TextView(this).apply { textSize = 15f; setTypeface(typeface, Typeface.BOLD) }
        val current = prefs.getInt(key, default).coerceIn(min, max)
        value.text = show(current)
        val bar = SeekBar(this).apply {
            this.max = max - min
            progress = current - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { value.text = show(p + min) }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) { prefs.edit().putInt(key, progress + min).apply() }
            })
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(6))
            addView(LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(TextView(this@SettingsActivity).apply { text = label; textSize = 15f },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(value)
            })
            addView(bar)
        }
    }
}
