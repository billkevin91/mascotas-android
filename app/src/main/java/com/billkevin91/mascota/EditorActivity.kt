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
    companion object {
        private const val PICK_SOUND = 9
    }

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

    // Opciones avanzadas del movimiento elegido
    private lateinit var advancedBox: LinearLayout
    private lateinit var author: EditText
    private lateinit var info: EditText
    private var onEnd = ArrayList<Next>()
    private var onBorder = ArrayList<Next>()
    private var onGravity = ArrayList<Next>()
    private var kids = ArrayList<ChildDef>()
    private var sound: Sound? = null
    private var soundChanged = false

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

        val toggle = Button(this).apply { text = "Opciones avanzadas ▾"; isAllCaps = false }
        advancedBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        toggle.setOnClickListener {
            val show = advancedBox.visibility != View.VISIBLE
            advancedBox.visibility = if (show) View.VISIBLE else View.GONE
            toggle.text = if (show) "Opciones avanzadas ▴" else "Opciones avanzadas ▾"
        }
        col.addView(toggle)
        col.addView(advancedBox)
        author = EditText(this).apply { isSingleLine = true }
        info = EditText(this)

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
        val shareRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        shareRow.addView(Button(this).apply {
            text = "Compartir"; isAllCaps = false
            setOnClickListener { if (savedFirst()) runCatching { PetShare.share(this@EditorActivity, petId) } }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        shareRow.addView(Button(this).apply {
            text = "Guardar como archivo"; isAllCaps = false
            setOnClickListener { if (savedFirst()) PetShare.askWhere(this@EditorActivity, petId) }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        col.addView(shareRow)
        col.addView(Button(this).apply {
            text = "Deshacer los cambios sin guardar"
            isAllCaps = false
            setOnClickListener { dirty = false; showAnimation(animId) }
        })

        setContentView(ScrollView(this).apply { fitsSystemWindows = true; addView(col) })
        // Al escribir en cualquier campo hay cambios sin guardar
        for (e in listOf(nameEdit, startMs, endMs, startX, endX, startY, endY, repeat, repeatFrom, author, info)) {
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
                author.setText(d.header.author)
                info.setText(d.header.info)
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
        val adv = PetEdit.advancedOf(d, id)
        onEnd = ArrayList(adv.onEnd); onBorder = ArrayList(adv.onBorder); onGravity = ArrayList(adv.onGravity)
        kids = ArrayList(adv.children)
        sound = adv.sound
        soundChanged = false
        showAdvanced()
    }

    // ---- Opciones avanzadas ----

    /** Dónde tiene que estar para que valga ese "siguiente" (atributo only de desktopPet). */
    private val places = listOf(
        Only.NONE to "siempre", Only.TASKBAR to "en el suelo", Only.VERTICAL to "en una pared",
        Only.HORIZONTAL to "en el techo", Only.PET_SIDE to "junto a otra mascota",
        Only.HORIZONTAL_PLUS to "techo o suelo", Only.VERTICAL_PLUS to "pared o mascota",
        Only.WINDOW to "sobre una ventana", Only.WINDOW_SIDE to "junto a una ventana",
    )

    private fun showAdvanced() {
        val d = def ?: return
        advancedBox.removeAllViews()
        advancedBox.addView(small("La probabilidad es un peso: con 30 y 10, el primero sale 3 de cada 4 veces. " +
            "\"Dónde\" dice cuándo vale: por ejemplo, \"en el suelo\" sólo si ha terminado pisando el suelo."))
        nextList("Al terminar, pasa a", onEnd)
        nextList("Al chocar con un borde o con otra mascota, pasa a", onBorder)
        nextList("Si se queda sin suelo, pasa a", onGravity)

        advancedBox.addView(label("Crías (aparecen al empezar este movimiento: la tina, una flor...)"))
        kids.forEachIndexed { i, c ->
            val x = expr().apply { setText(c.x.text) }
            val y = expr().apply { setText(c.y.text) }
            val next = animPicker(c.next)
            fun update() {
                kids[i] = ChildDef(Value(x.text.toString()), Value(y.text.toString()), animAt(next.selectedItemPosition))
                dirty = true
            }
            watch(x) { update() }; watch(y) { update() }
            next.onItemSelectedListener = listener { if (animAt(it) != kids[i].next) update() }
            advancedBox.addView(pair("x (imageX = la madre)", x, "y (imageY = la madre)", y))
            advancedBox.addView(row(next, remove { kids.removeAt(i); dirty = true; showAdvanced() }))
        }
        advancedBox.addView(Button(this).apply {
            text = "+ Añadir cría"; isAllCaps = false
            setOnClickListener { kids.add(ChildDef(Value("imageX"), Value("imageY"), animId)); dirty = true; showAdvanced() }
        })

        advancedBox.addView(label("Sonido al empezar este movimiento"))
        val s = sound
        advancedBox.addView(small(if (s == null) "Sin sonido" else "Tiene sonido (${s.base64.length * 3 / 4 / 1024} KB)"))
        if (s != null) {
            val prob = number().apply { setText(s.probability.toString()) }
            val loops = number().apply { setText(s.loop.toString()) }
            fun update() {
                sound = Sound(animId, prob.text.toString().toIntOrNull() ?: -1, loops.text.toString().toIntOrNull() ?: -1, s.base64)
                soundChanged = true; dirty = true
            }
            watch(prob) { update() }; watch(loops) { update() }
            advancedBox.addView(pair("probabilidad (%)", prob, "repeticiones", loops))
        }
        val soundButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        soundButtons.addView(Button(this).apply {
            text = if (s == null) "Elegir sonido" else "Cambiar"; isAllCaps = false
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE), PICK_SOUND)
            }
        })
        if (s != null) {
            soundButtons.addView(Button(this).apply { text = "Escuchar"; isAllCaps = false; setOnClickListener { listen(s) } })
            soundButtons.addView(Button(this).apply {
                text = "Quitar"; isAllCaps = false
                setOnClickListener { sound = null; soundChanged = true; dirty = true; showAdvanced() }
            })
        }
        advancedBox.addView(soundButtons)

        advancedBox.addView(label("De la mascota"))
        advancedBox.addView(TextView(this).apply { text = "Autor"; textSize = 12f })
        (author.parent as? LinearLayout)?.removeView(author)
        advancedBox.addView(author)
        advancedBox.addView(TextView(this).apply { text = "Descripción"; textSize = 12f })
        (info.parent as? LinearLayout)?.removeView(info)
        advancedBox.addView(info)
    }

    private fun nextList(title: String, list: ArrayList<Next>) {
        advancedBox.addView(label(title))
        if (list.isEmpty()) advancedBox.addView(small("(nada)"))
        list.forEachIndexed { i, n ->
            val anim = animPicker(n.id)
            val prob = number().apply { setText(n.probability.toString()) }
            val place = Spinner(this).apply {
                adapter = ArrayAdapter(this@EditorActivity, android.R.layout.simple_spinner_dropdown_item, places.map { it.second })
                setSelection(places.indexOfFirst { it.first == n.only }.coerceAtLeast(0))
            }
            fun update() {
                list[i] = Next(animAt(anim.selectedItemPosition), prob.text.toString().toIntOrNull() ?: -1, places[place.selectedItemPosition].first)
                dirty = true
            }
            anim.onItemSelectedListener = listener { if (animAt(it) != list[i].id) update() }
            place.onItemSelectedListener = listener { if (places[it].first != list[i].only) update() }
            watch(prob) { update() }
            advancedBox.addView(row(anim, remove { list.removeAt(i); dirty = true; showAdvanced() }))
            advancedBox.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(TextView(this@EditorActivity).apply { text = "probabilidad "; textSize = 12f })
                addView(prob, LinearLayout.LayoutParams(dp(72), LinearLayout.LayoutParams.WRAP_CONTENT))
                addView(TextView(this@EditorActivity).apply { text = "  dónde "; textSize = 12f })
                addView(place, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            })
        }
        advancedBox.addView(Button(this).apply {
            text = "+ Añadir"; isAllCaps = false
            setOnClickListener { list.add(Next(animId, 10, Only.NONE)); dirty = true; showAdvanced() }
        })
    }

    private fun sortedAnims() = def?.animations?.values?.sortedBy { it.id }.orEmpty()
    private fun animAt(pos: Int) = sortedAnims().getOrNull(pos)?.id ?: animId

    private fun animPicker(id: Int) = Spinner(this).apply {
        val anims = sortedAnims()
        adapter = ArrayAdapter(this@EditorActivity, android.R.layout.simple_spinner_dropdown_item, anims.map { "${it.id} · ${it.name}" })
        setSelection(anims.indexOfFirst { it.id == id }.coerceAtLeast(0))
    }

    private fun row(main: View, end: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(main, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(end)
    }

    private fun remove(onClick: () -> Unit) = Button(this).apply { text = "✖"; setOnClickListener { onClick() } }

    private fun watch(e: EditText, onChange: () -> Unit) {
        e.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { if (!filling) onChange() }
        })
    }

    private var player: android.media.MediaPlayer? = null

    private fun listen(s: Sound) {
        runCatching {
            player?.release()
            val f = java.io.File(cacheDir, "escuchar")
            f.writeBytes(android.util.Base64.decode(s.base64.substringAfter(";base64,"), android.util.Base64.DEFAULT))
            player = android.media.MediaPlayer().apply { setDataSource(f.path); prepare(); start() }
        }.onFailure { Toast.makeText(this, "No se pudo reproducir", Toast.LENGTH_SHORT).show() }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PetShare.SAVE_REQUEST && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val ok = runCatching { PetShare.saveTo(this, petId, uri) }.isSuccess
            Toast.makeText(this, if (ok) "Guardada" else "No se pudo guardar", Toast.LENGTH_SHORT).show()
            return
        }
        if (requestCode != PICK_SOUND || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val bytes = runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        if (bytes == null || bytes.isEmpty()) { Toast.makeText(this, "No se pudo leer el sonido", Toast.LENGTH_SHORT).show(); return }
        val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        if (b64.length > PetEdit.MAX_SOUND) { Toast.makeText(this, "Es demasiado largo (máximo ~1,5 MB)", Toast.LENGTH_LONG).show(); return }
        sound = Sound(animId, sound?.probability ?: 100, sound?.loop ?: 0, b64)
        soundChanged = true
        dirty = true
        showAdvanced()
    }

    override fun onDestroy() {
        player?.release()
        super.onDestroy()
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
        val adv = PetEdit.Advanced(onEnd.toList(), onBorder.toList(), onGravity.toList(), kids.toList(), sound, soundChanged)
        PetEdit.checkAdvanced(d, adv)?.let { msg ->
            AlertDialog.Builder(this).setTitle("Revisa las opciones avanzadas").setMessage(msg).setPositiveButton("Vale", null).show()
            return
        }
        val edited = PetEdit.withInfo(
            PetEdit.renamed(PetEdit.withAdvanced(PetEdit.withFields(d, animId, f), animId, adv), name),
            author.text.toString().trim(), info.text.toString().trim(),
        )
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
                    soundChanged = false
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

    /** Se comparte lo guardado: si hay cambios sin guardar, avisa. */
    private fun savedFirst(): Boolean {
        if (dirty) Toast.makeText(this, "Primero guarda los cambios", Toast.LENGTH_SHORT).show()
        return !dirty
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
