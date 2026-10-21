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

    private class Move(val id: Int, val name: String, val detail: String) {
        override fun toString() = "$id · $name\n$detail"
    }

    private val green = Color.rgb(62, 125, 46)
    private var pet = MainActivity.PETS.first()
    private var moves = emptyList<Move>()
    private lateinit var adapter: ArrayAdapter<Move>
    private lateinit var search: EditText
    private lateinit var loop: CheckBox
    private lateinit var status: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
    private val prefs get() = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pet = prefs.getString(MainActivity.PREF_PET, null)?.takeIf { it in MainActivity.PETS } ?: MainActivity.PETS.first()

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(8))
            fitsSystemWindows = true
        }
        col.addView(TextView(this).apply {
            text = "Probar movimientos"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(green)
        })
        col.addView(TextView(this).apply {
            text = "Toca un movimiento y la mascota lo hará encima de esta pantalla. " +
                "Los que son parte de una escena (como el baño) se ven con la escena entera."
            textSize = 14f
            setPadding(0, dp(4), 0, dp(8))
        })

        val spinner = Spinner(this)
        val names = MainActivity.PETS.map { MainActivity.NAMES[it] ?: it }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        spinner.setSelection(MainActivity.PETS.indexOf(pet))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val chosen = MainActivity.PETS[position]
                if (chosen != pet || moves.isEmpty()) { pet = chosen; load() }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        col.addView(spinner)

        loop = CheckBox(this).apply { text = "Repetir el movimiento elegido"; isChecked = true }
        col.addView(loop)
        col.addView(CheckBox(this).apply {
            text = "Mostrar el nombre del movimiento encima de la mascota"
            isChecked = prefs.getBoolean(MainActivity.PREF_NAMES, true)
            prefs.edit().putBoolean(MainActivity.PREF_NAMES, isChecked).apply()
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(MainActivity.PREF_NAMES, on).apply() }
        })

        col.addView(TextView(this).apply { text = "Velocidad de todas las mascotas"; setPadding(0, dp(6), 0, 0) })
        col.addView(MainActivity.speedRow(this))

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(Button(this).apply {
            text = "Que se mueva sola"
            isAllCaps = false
            setOnClickListener { PetService.play(this@TestActivity, pet, -1, false); status.text = "Se mueve sola otra vez" }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        buttons.addView(Button(this).apply {
            text = "Quitar todas"
            isAllCaps = false
            setOnClickListener { PetService.clear(this@TestActivity) }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        col.addView(buttons)

        search = EditText(this).apply {
            hint = "Buscar (walk, run, bath, sleep...)"
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
            status.text = "Haciendo: ${m.id} · ${m.name}" + if (loop.isChecked) " (repitiendo)" else ""
            if (m.detail.contains("parte de la escena")) status.append(" — se ve la escena entera")
        }
        col.addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(col)
    }

    private fun load() {
        status.text = "Cargando movimientos..."
        val folder = pet
        Thread {
            val def = runCatching { assets.open("pets/$folder/animations.xml").use { PetDef.parse(it) } }.getOrNull()
            val list = def?.animations?.values?.sortedBy { it.id }?.map { a ->
                val ms = a.start.interval.constant?.toInt()
                val parts = ArrayList<String>()
                parts.add("${a.frames.size} dibujos")
                if (ms != null) parts.add("cada $ms ms")
                val x = a.start.x.constant
                if (x != null && x != 0.0) parts.add("avanza ${abs(x).toInt()} px")
                val names = def.animations
                val start = def.sceneStart(a.id)
                val scene = def.scene(start)
                when {
                    start != a.id -> parts.add("parte de la escena de ${names[start]?.name}")
                    scene.size > 1 -> parts.add("escena: " + scene.joinToString(" → ") { names[it]?.name.orEmpty() })
                    else -> (a.onEnd.firstOrNull()?.let { names[it.id]?.name })?.let { parts.add("luego $it") }
                }
                Move(a.id, a.name, parts.joinToString(" · "))
            }.orEmpty()
            runOnUiThread {
                if (folder != pet) return@runOnUiThread
                moves = list
                status.text = "${list.size} movimientos de ${MainActivity.NAMES[folder] ?: folder}"
                filter()
            }
        }.start()
    }

    private fun abs(v: Double) = if (v < 0) -v else v

    private fun filter() {
        val q = search.text.toString().trim().lowercase()
        adapter.clear()
        adapter.addAll(if (q.isEmpty()) moves else moves.filter { it.name.lowercase().contains(q) || it.id.toString() == q })
    }
}
