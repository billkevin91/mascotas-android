package com.billkevin91.mascota

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

/**
 * Editor sencillo: nombre de la mascota y, de cada movimiento, sus dibujos, su velocidad,
 * cuánto avanza y cuántas veces se repite. Las incluidas se guardan como copia tuya.
 * Mientras estás aquí, como en "Probar movimientos", sólo se mueve la mascota de prueba.
 */
class EditorActivity : Activity() {

    private val green = Color.rgb(62, 125, 46)
    private var entries = emptyList<PetEntry>()
    private var petId = PetLibrary.BUNDLED.first()
    private var def: PetDef? = null
    private var sheet: Bitmap? = null
    private var animId = -1
    private var frames = ArrayList<Int>()
    private var dirty = false

    private lateinit var petSpinner: Spinner
    private lateinit var nameEdit: EditText
    private lateinit var animSpinner: Spinner
    private lateinit var sequence: LinearLayout
    private lateinit var tiles: LinearLayout
    private lateinit var startMs: EditText
    private lateinit var endMs: EditText
    private lateinit var startX: EditText
    private lateinit var endX: EditText
    private lateinit var startY: EditText
    private lateinit var endY: EditText
    private lateinit var repeat: EditText
    private lateinit var repeatFrom: EditText
    private lateinit var status: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        entries = PetLibrary.all(this)
        petId = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).getString(MainActivity.PREF_PET, null)
            ?.takeIf { id -> entries.any { it.id == id } } ?: PetLibrary.BUNDLED.first()

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(24))
        }
        col.addView(TextView(this).apply {
            text = "Editar mascotas"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(green)
        })
        col.addView(small("Cambia cómo se mueve tu mascota y pruébalo al momento. Las que vienen con la app " +
            "se guardan como una copia tuya (la original no cambia). Mientras estás aquí, tus mascotas se esperan ocultas."))

        petSpinner = Spinner(this)
        petSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, entries.map { it.name })
        petSpinner.setSelection(entries.indexOfFirst { it.id == petId }.coerceAtLeast(0))
        petSpinner.onItemSelectedListener = listener { pos ->
            val chosen = entries[pos].id
            if (chosen != petId || def == null) askDiscard { petId = chosen; load() }
        }
        col.addView(label("Mascota"))
        col.addView(petSpinner)

        col.addView(label("Nombre"))
        nameEdit = EditText(this).apply { isSingleLine = true }
        col.addView(nameEdit)

        col.addView(label("Movimiento"))
        animSpinner = Spinner(this)
        col.addView(animSpinner)

        col.addView(label("Dibujos de este movimiento (toca uno para quitarlo)"))
        sequence = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        col.addView(HorizontalScrollView(this).apply { addView(sequence) })

        col.addView(label("Toca un dibujo de la hoja para añadirlo al final"))
        tiles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(HorizontalScrollView(this).apply { addView(tiles) })

        col.addView(label("Velocidad: milisegundos por dibujo (menos = más rápido)"))
        startMs = number(); endMs = number()
        col.addView(pair("al empezar", startMs, "al terminar", endMs))

        col.addView(label("Cuánto avanza en cada paso (horizontal: negativo = hacia donde mira)"))
        startX = expr(); endX = expr()
        col.addView(pair("al empezar", startX, "al terminar", endX))
        col.addView(label("Cuánto baja en cada paso (negativo = sube)"))
        startY = expr(); endY = expr()
        col.addView(pair("al empezar", startY, "al terminar", endY))

        col.addView(label("Repeticiones"))
        repeat = expr(); repeatFrom = number()
        col.addView(pair("veces", repeat, "desde el dibujo nº", repeatFrom))
        col.addView(small("Admiten fórmulas como en desktopPet, por ejemplo 5+random/10 (random va de 0 a 99)."))

        status = small("")
        col.addView(status)
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(Button(this).apply {
            text = "Guardar y probar"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply { setColor(green); cornerRadius = dp(10).toFloat() }
            setOnClickListener { save(test = true) }
        }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(0, 0, dp(6), 0) })
        buttons.addView(Button(this).apply {
            text = "Guardar"
            isAllCaps = false
            setOnClickListener { save(test = false) }
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        col.addView(buttons)
        col.addView(Button(this).apply {
            text = "Deshacer los cambios sin guardar"
            isAllCaps = false
            setOnClickListener { dirty = false; showAnimation(animId) }
        })

        setContentView(ScrollView(this).apply { fitsSystemWindows = true; addView(col) })
        // Al escribir en cualquier campo hay cambios sin guardar
        for (e in listOf(nameEdit, startMs, endMs, startX, endX, startY, endY, repeat, repeatFrom)) {
            e.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) { if (!filling) dirty = true }
            })
        }
    }

    override fun onStart() {
        super.onStart()
        PetService.testStart(this)
    }

    override fun onStop() {
        PetService.testEnd(this)
        super.onStop()
    }

    // ---- Cargar y mostrar ----

    private var filling = false

    private fun load() {
        status.text = "Cargando..."
        val id = petId
        Thread {
            val d = runCatching { PetLibrary.load(this, id) }.getOrNull()
            val s = d?.let { runCatching { PetLibrary.sheet(it) }.getOrNull() }
            runOnUiThread {
                if (id != petId) return@runOnUiThread
                if (d == null || s == null) { status.text = "No se pudo abrir esta mascota"; return@runOnUiThread }
                def = d
                sheet = s
                dirty = false
                filling = true
                nameEdit.setText(d.header.petName.ifEmpty { d.title })
                filling = false
                val anims = d.animations.values.sortedBy { it.id }
                animSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, anims.map { "${it.id} · ${it.name}" })
                animSpinner.onItemSelectedListener = listener { pos ->
                    val chosen = anims[pos].id
                    if (chosen != animId) askDiscard { showAnimation(chosen) }
                }
                buildTiles()
                showAnimation(anims.first().id)
                status.text = ""
            }
        }.start()
    }

    private fun showAnimation(id: Int) {
        val d = def ?: return
        val a = d.animations[id] ?: return
        animId = id
        val anims = d.animations.values.sortedBy { it.id }
        animSpinner.setSelection(anims.indexOfFirst { it.id == id }.coerceAtLeast(0))
        val f = PetEdit.fieldsOf(a)
        frames = ArrayList(f.frames)
        filling = true
        startMs.setText(f.startInterval); endMs.setText(f.endInterval)
        startX.setText(f.startX); endX.setText(f.endX)
        startY.setText(f.startY); endY.setText(f.endY)
        repeat.setText(f.repeat); repeatFrom.setText(f.repeatFrom.toString())
        filling = false
        dirty = false
        showSequence()
    }

    private fun showSequence() {
        sequence.removeAllViews()
        frames.forEachIndexed { i, fr ->
            sequence.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(tile(fr, 56))
                addView(TextView(this@EditorActivity).apply { text = "$i: $fr"; textSize = 11f; gravity = Gravity.CENTER })
                setOnClickListener {
                    if (frames.size > 1) { frames.removeAt(i); dirty = true; showSequence() }
                    else Toast.makeText(this@EditorActivity, "Tiene que quedar al menos un dibujo", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun buildTiles() {
        tiles.removeAllViews()
        val d = def ?: return
        for (row in 0 until d.tilesY) {
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (col in 0 until d.tilesX) {
                val n = row * d.tilesX + col
                line.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    addView(tile(n, 44))
                    addView(TextView(this@EditorActivity).apply { text = "$n"; textSize = 10f; gravity = Gravity.CENTER })
                    setOnClickListener { frames.add(n); dirty = true; showSequence() }
                })
            }
            tiles.addView(line)
        }
    }

    /** Un dibujo de la hoja, pixelado, sobre fondo claro. */
    private fun tile(n: Int, sizeDp: Int): View {
        val d = def!!
        val s = sheet!!
        val fw = s.width / d.tilesX
        val fh = s.height / d.tilesY
        val bmp = Bitmap.createBitmap(s, (n % d.tilesX) * fw, (n / d.tilesX) * fh, fw, fh)
        return ImageView(this).apply {
            setImageDrawable(BitmapDrawable(resources, bmp).apply { paint.isFilterBitmap = false })
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = GradientDrawable().apply { setColor(Color.rgb(230, 240, 250)); setStroke(1, Color.LTGRAY) }
            layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)).apply { setMargins(dp(2), dp(2), dp(2), 0) }
        }
    }

    // ---- Guardar ----

    private fun save(test: Boolean) {
        val d = def ?: return
        if (test && !Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        val f = PetEdit.Fields(
            frames = frames.toList(),
            startInterval = startMs.text.toString().trim(),
            endInterval = endMs.text.toString().trim(),
            startX = startX.text.toString().trim(),
            endX = endX.text.toString().trim(),
            startY = startY.text.toString().trim(),
            endY = endY.text.toString().trim(),
            repeat = repeat.text.toString().trim(),
            repeatFrom = repeatFrom.text.toString().trim().toIntOrNull() ?: -1,
        )
        PetEdit.check(d, f)?.let { msg ->
            AlertDialog.Builder(this).setTitle("Revisa los datos").setMessage(msg).setPositiveButton("Vale", null).show()
            return
        }
        val entry = entries.first { it.id == petId }
        var name = nameEdit.text.toString().trim().ifEmpty { entry.name }
        // Las incluidas se guardan como copia tuya (con otro nombre para no confundirlas)
        if (entry.bundled && name == entry.name) name = "${entry.name} (mía)"
        val edited = PetEdit.renamed(PetEdit.withFields(d, animId, f), name)
        val target = if (entry.bundled) {
            // Si ya hay una copia tuya con ese nombre, se guarda encima
            PetLibrary.all(this).firstOrNull { !it.bundled && it.name.equals(name, true) }?.id
        } else petId
        status.text = "Guardando..."
        val anim = animId
        Thread {
            val result = runCatching { PetLibrary.save(this, target, edited) }
            runOnUiThread {
                result.onSuccess { e ->
                    PetService.forget(this, e.id)  // que use la versión nueva si estaba en pantalla
                    dirty = false
                    getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit().putString(MainActivity.PREF_PET, e.id).apply()
                    if (e.id != petId) {
                        // Ahora se edita la copia
                        entries = PetLibrary.all(this)
                        petId = e.id
                        petSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, entries.map { it.name })
                        petSpinner.setSelection(entries.indexOfFirst { it.id == petId }.coerceAtLeast(0))
                    }
                    def = edited
                    status.text = "Guardada como \"${e.name}\"" + if (test) ". Mírala en la pantalla." else ""
                    if (test) PetService.play(this, e.id, anim, loop = true)
                }.onFailure { err ->
                    status.text = ""
                    AlertDialog.Builder(this).setTitle("No se pudo guardar")
                        .setMessage(err.message ?: err.toString()).setPositiveButton("Vale", null).show()
                }
            }
        }.start()
    }

    private fun askDiscard(then: () -> Unit) {
        if (!dirty) { then(); return }
        AlertDialog.Builder(this).setTitle("Hay cambios sin guardar")
            .setMessage("Si cambias, se pierden.")
            .setPositiveButton("Cambiar") { _, _ -> dirty = false; then() }
            .setNegativeButton("Seguir aquí") { _, _ ->
                // Vuelve a dejar elegido lo que estaba
                petSpinner.setSelection(entries.indexOfFirst { it.id == petId }.coerceAtLeast(0))
                def?.animations?.values?.sortedBy { it.id }?.indexOfFirst { it.id == animId }?.let { if (it >= 0) animSpinner.setSelection(it) }
            }
            .show()
    }

    // ---- Piezas de la pantalla ----

    private fun listener(onPick: (Int) -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = onPick(position)
        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    private fun label(s: String) = TextView(this).apply {
        text = s
        textSize = 15f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(14), 0, dp(4))
    }

    private fun small(s: String) = TextView(this).apply {
        text = s
        textSize = 13f
        alpha = 0.75f
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun number() = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER; isSingleLine = true }
    private fun expr() = EditText(this).apply { isSingleLine = true }

    private fun pair(l1: String, e1: EditText, l2: String, e2: EditText) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        for ((l, e) in listOf(l1 to e1, l2 to e2)) {
            addView(LinearLayout(this@EditorActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@EditorActivity).apply { text = l; textSize = 12f })
                addView(e)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, dp(8), 0) })
        }
    }
}
