package com.billkevin91.mascota

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import kotlin.math.roundToInt

/** Modo prueba: elegir cualquier movimiento del XML y ver cómo lo hace la mascota. */
class TestActivity : Activity() {

    private class Move(val id: Int, val name: String, val detail: String, val partOfScene: Boolean = false) {
        override fun toString() = "$id · $name\n$detail"
    }

    private val green = Color.rgb(62, 125, 46)
    private var pet = PetLibrary.BUNDLED.first()
    private var entries = emptyList<PetEntry>()
    private var moves = emptyList<Move>()
    private lateinit var adapter: ArrayAdapter<Move>
    private lateinit var search: EditText
    private lateinit var loop: CheckBox
    private lateinit var status: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
    private val prefs get() = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        entries = PetLibrary.all(this)
        pet = prefs.getString(MainActivity.PREF_PET, null)?.takeIf { id -> entries.any { it.id == id } } ?: PetLibrary.BUNDLED.first()

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(8))
            fitsSystemWindows = true
        }
        col.addView(TextView(this).apply {
            text = tr("Probar movimientos", "Try movements")
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(green)
        })
        col.addView(TextView(this).apply {
            text = tr("Mientras estás aquí, tus mascotas se esperan ocultas y sólo se mueve la de prueba. ", "While you're here, your pets wait hidden and only the test pet moves. ") +
                tr("Toca un movimiento y la mascota lo hará encima de esta pantalla. ", "Tap a movement and the pet will do it on top of this screen. ") +
                tr("Los que son parte de una escena (como el baño) se ven con la escena entera.", "The ones that are part of a scene (like the bath) are shown with the whole scene.")
            textSize = 14f
            setPadding(0, dp(4), 0, dp(8))
        })

        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, entries.map { it.name })
        spinner.setSelection(entries.indexOfFirst { it.id == pet }.coerceAtLeast(0))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val chosen = entries[position].id
                if (chosen != pet || moves.isEmpty()) { pet = chosen; load() }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        col.addView(spinner)

        loop = CheckBox(this).apply { text = tr("Repetir el movimiento elegido", "Repeat the chosen movement"); isChecked = true }
        col.addView(loop)
        col.addView(CheckBox(this).apply {
            text = tr("Mostrar el nombre del movimiento encima de la mascota", "Show the movement's name above the pet")
            isChecked = prefs.getBoolean(MainActivity.PREF_NAMES, true)
            prefs.edit().putBoolean(MainActivity.PREF_NAMES, isChecked).apply()
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(MainActivity.PREF_NAMES, on).apply() }
        })

        col.addView(TextView(this).apply { text = tr("Velocidad de la mascota de prueba", "Speed of the test pet"); setPadding(0, dp(6), 0, 0) })
        col.addView(MainActivity.speedRow(this, MainActivity.PREF_TEST_SPEED))

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(Button(this).apply {
            text = tr("Que se mueva sola", "Let it move on its own")
            isAllCaps = false
            setOnClickListener { PetService.play(this@TestActivity, pet, -1, false); status.text = tr("Se mueve sola otra vez", "It moves on its own again") }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        buttons.addView(Button(this).apply {
            text = tr("Quitar la de prueba", "Remove the test pet")
            isAllCaps = false
            setOnClickListener { PetService.testClear(this@TestActivity); status.text = "" }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        col.addView(buttons)

        search = EditText(this).apply {
            hint = tr("Buscar (walk, bath, sleep, sonido...)", "Search (walk, bath, sleep, sound...)")
            isSingleLine = true
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) { filter() }
            })
        }
        col.addView(search)
        status = TextView(this).apply { textSize = 13f; alpha = 0.7f }
        col.addView(status)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList())
        val list = ListView(this)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            val m = adapter.getItem(position) ?: return@setOnItemClickListener
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                return@setOnItemClickListener
            }
            PetService.play(this, pet, m.id, loop.isChecked)
            status.text = tr("Haciendo: ${m.id} · ${m.name}", "Doing: ${m.id} · ${m.name}") + if (loop.isChecked) tr(" (repitiendo)", " (repeating)") else ""
            if (m.partOfScene) status.append(tr(" — se ve la escena entera", " — the whole scene is shown"))
        }
        col.addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(col)
    }

    override fun onStart() {
        super.onStart()
        PetService.testStart(this)
    }

    override fun onStop() {
        // Al salir: la de prueba se va y tus mascotas siguen donde estaban
        PetService.testEnd(this)
        super.onStop()
    }

    private fun load() {
        status.text = tr("Cargando movimientos...", "Loading movements...")
        val folder = pet
        Thread {
            val def = runCatching { PetLibrary.load(this, folder) }.getOrNull()
            val list = def?.animations?.values?.sortedBy { it.id }?.map { a ->
                val ms = a.start.interval.constant?.toInt()
                val parts = ArrayList<String>()
                parts.add(tr("${a.frames.size} dibujos", "${a.frames.size} drawings"))
                if (ms != null) parts.add(tr("cada $ms ms", "every $ms ms"))
                val x = a.start.x.constant
                if (x != null && x != 0.0) parts.add(tr("avanza ${abs(x).toInt()} px", "moves ${abs(x).toInt()} px"))
                val names = def.animations
                // Avisos para que no parezca que algo está congelado
                val still = a.frames.distinct().size <= 1 && a.start.x.constant == 0.0 && a.start.y.constant == 0.0 &&
                    a.end.x.constant == 0.0 && a.end.y.constant == 0.0
                if (a.id == def.kill) parts.add(0, tr("despedida: se desvanece", "goodbye: fades out"))
                else if (still) parts.add(0, tr("pose quieta", "still pose"))
                // Aquí el sonido suena siempre (si está encendido); paseando, con su probabilidad
                def.sounds.firstOrNull { it.animationId == a.id }?.let { parts.add(0, tr("🔊 sonido (${it.probability} % paseando)", "🔊 sound (${it.probability} % while walking)")) }
                val start = def.sceneStart(a.id)
                val scene = def.scene(start)
                when {
                    start != a.id -> parts.add(tr("parte de la escena de ${names[start]?.name}", "part of the ${names[start]?.name} scene"))
                    scene.size > 1 -> parts.add(tr("escena: ", "scene: ") + scene.joinToString(" → ") { names[it]?.name.orEmpty() })
                    else -> (a.onEnd.firstOrNull()?.let { names[it.id]?.name })?.let { parts.add(tr("luego $it", "then $it")) }
                }
                Move(a.id, a.name, parts.joinToString(" · "), partOfScene = start != a.id)
            }.orEmpty()
            runOnUiThread {
                if (folder != pet) return@runOnUiThread
                moves = list
                status.text = tr("${list.size} movimientos de ${entries.firstOrNull { it.id == folder }?.name ?: folder}", "${list.size} movements of ${entries.firstOrNull { it.id == folder }?.name ?: folder}")
                filter()
            }
        }.start()
    }

    private fun abs(v: Double) = if (v < 0) -v else v

    private fun filter() {
        val q = search.text.toString().trim().lowercase()
        adapter.clear()
        adapter.addAll(if (q.isEmpty()) moves else moves.filter { it.name.lowercase().contains(q) || it.detail.lowercase().contains(q) || it.id.toString() == q })
    }
}
