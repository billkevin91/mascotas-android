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
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

class MainActivity : Activity() {

    companion object {
        const val PREFS = "mascotas"
        const val PREF_PET = "pet"
        const val PREF_SIZE = "size"
        const val PREF_SPEED = "speed"
        const val PREF_NAMES = "names"
        const val PREF_TEST_SPEED = "test_speed"
        const val PREF_SOUND = "sound"
        const val PREF_SAVER = "battery_saver"
        const val PREF_CLOCK = "follow_clock"
        const val PREF_REACT = "react_phone"
        private const val IMPORT = 7
        private val SIZES get() = listOf(tr("Pequeña", "Small") to 0.75f, tr("Mediana", "Medium") to 1f, tr("Grande", "Large") to 1.5f, tr("Enorme", "Huge") to 2f)
        private val SPEEDS get() = listOf(tr("Lenta", "Slow") to 0.5f, tr("Tranquila", "Calm") to 0.75f, tr("Normal", "Normal") to 1f, tr("Rápida", "Fast") to 1.25f)

        /** Botones de velocidad (se guardan y el servicio la aplica al momento). */
        /** Echa comida si hay mascotas paseando; si no, avisa. Devuelve si la echó. */
        fun feed(a: Activity): Boolean {
            if (PetService.petsOnScreen == 0) {
                android.widget.Toast.makeText(a, tr("Primero suelta una mascota", "Release a pet first"), android.widget.Toast.LENGTH_SHORT).show()
                return false
            }
            PetService.feed(a)
            return true
        }

        fun speedRow(a: Activity, key: String = PREF_SPEED): LinearLayout {
            val prefs = a.getSharedPreferences(PREFS, MODE_PRIVATE)
            val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
            val buttons = ArrayList<Pair<Float, Button>>()
            fun paint() {
                val current = prefs.getFloat(key, 1f)
                for ((f, b) in buttons) {
                    b.setTypeface(null, if (f == current) Typeface.BOLD else Typeface.NORMAL)
                    b.alpha = if (f == current) 1f else 0.55f
                }
            }
            SPEEDS.forEach { (name, f) ->
                val b = Button(a).apply {
                    text = name
                    isAllCaps = false
                    setOnClickListener { prefs.edit().putFloat(key, f).apply(); paint() }
                }
                buttons.add(f to b)
                row.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
            paint()
            return row
        }
    }

    private val green = Color.rgb(62, 125, 46)
    private lateinit var permission: TextView
    private lateinit var grant: Button
    private val petCards = HashMap<String, View>()
    private val sizeButtons = ArrayList<Pair<Float, Button>>()
    private lateinit var grid: LinearLayout
    private lateinit var removeButton: Button
    private lateinit var soundBox: android.widget.CheckBox
    /** La mascota que se está guardando como archivo (mientras eliges dónde). */
    private var savingId: String? = null
    private var pet = PetLibrary.BUNDLED.first()
    private var size = 1f

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savingId = savedInstanceState?.getString("guardando")
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        pet = prefs.getString(PREF_PET, null)?.takeIf { PetLibrary.exists(this, it) } ?: PetLibrary.BUNDLED.first()
        size = prefs.getFloat(PREF_SIZE, 1f)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(24))
        }
        col.addView(TextView(this).apply {
            text = tr("Mascotas en pantalla", "Mascotas: pets on your screen")
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(green)
        })
        col.addView(text(tr("Una mascota de los 90 que camina por encima de tus apps. ", "A 90s pet that walks on top of your apps. ") +
            tr("Arrástrala con el dedo, lánzala, y tócala dos veces para despedirla.", "Drag it with your finger, throw it, and double-tap it to say goodbye.")))

        permission = text("")
        col.addView(permission)
        grant = button(tr("Dar permiso para mostrarse sobre otras apps", "Allow it to display over other apps")) { askOverlay() }
        col.addView(grant)

        col.addView(title(tr("Elige tu mascota", "Choose your pet")))
        grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fillGrid()
        col.addView(grid)
        col.addView(button(tr("Importar mascota (animations.xml)", "Import pet (animations.xml)")) { pickXml() })
        removeButton = button(tr("Quitar mascotas importadas", "Remove imported pets")) { removeImported() }
        removeButton.visibility = if (PetLibrary.all(this).any { !it.bundled }) View.VISIBLE else View.GONE
        col.addView(removeButton)
        col.addView(text(tr("Sirven las de desktopPet y las que hagas con su PetEditor. Si importas una con ", "desktopPet pets and the ones you make with its PetEditor work. If you import one with ") +
            tr("el mismo nombre que otra tuya, se reemplaza. Mantén pulsada una mascota para compartirla o quitarla.", "the same name as one of yours, it's replaced. Hold a pet down to share it or remove it."))
            .apply { textSize = 12f; alpha = 0.6f })

        col.addView(title(tr("Tamaño", "Size")))
        val sizes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        SIZES.forEach { (name, f) ->
            val b = Button(this).apply {
                text = name
                isAllCaps = false
                setOnClickListener { size = f; save(); refresh() }
            }
            sizeButtons.add(f to b)
            sizes.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        col.addView(sizes)

        col.addView(title(tr("Velocidad", "Speed")))
        col.addView(speedRow(this))
        soundBox = android.widget.CheckBox(this)
        col.addView(soundBox.apply {
            text = tr("Sonidos de las mascotas (algunas traen: las ovejas de colores, los zorritos...)", "Pet sounds (some have them: the colored sheep, the foxes...)")
            isChecked = prefs.getBoolean(PREF_SOUND, false)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(PREF_SOUND, on).apply() }
            setPadding(0, dp(8), 0, dp(8))
        })

        col.addView(View(this), LinearLayout.LayoutParams(1, dp(16)))
        col.addView(button(tr("Soltar en la pantalla", "Release on screen")) { release() }.apply {
            textSize = 18f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply { setColor(green); cornerRadius = dp(12).toFloat() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)))
        col.addView(View(this), LinearLayout.LayoutParams(1, dp(8)))
        col.addView(button(tr("🍪 Dar de comer", "🍪 Feed them")) { if (feed(this)) goHome() })
        col.addView(text(tr("Toca una mascota para acariciarla. La comida se puede arrastrar: ", "Tap a pet to pet it. You can drag the food: ") +
            tr("irán a por ella. También desde el icono de la app (mantenlo pulsado).", "they'll go get it. Also from the app icon (hold it down).")).apply { textSize = 13f; alpha = 0.7f })
        col.addView(button(tr("Quitar todas", "Remove all")) { PetService.clear(this) })
        col.addView(button(tr("⚙ Ajustes", "⚙ Settings")) { startActivity(Intent(this, SettingsActivity::class.java)) })
        col.addView(button(tr("Probar movimientos", "Try movements")) { startActivity(Intent(this, TestActivity::class.java)) })
        col.addView(button(tr("Editar mascotas", "Edit pets")) { startActivity(Intent(this, EditorActivity::class.java)) })
        col.addView(button(tr("🎨 Crear mascota desde un dibujo", "🎨 Create a pet from a drawing")) { startActivity(Intent(this, MakerActivity::class.java)) })

        col.addView(text(tr("Mascotas del proyecto desktopPet de Adriano Petrucci y su comunidad ", "Pets from the desktopPet project by Adriano Petrucci and its community ") +
            tr("(github.com/Adrianotiger/desktopPet, licencia MIT).", "(github.com/Adrianotiger/desktopPet, MIT license).")).apply { textSize = 12f; alpha = 0.6f; setPadding(0, dp(24), 0, 0) })
        col.addView(button(tr("Créditos y licencias", "Credits and licenses")) { showCredits() })

        // Android 15 dibuja detrás de las barras: deja espacio para ellas
        setContentView(ScrollView(this).apply { fitsSystemWindows = true; addView(col) })

        if (testHook()) return
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
    }

    /**
     * Órdenes de la prueba automática del emulador (.github/scripts/smoke.sh), con
     * `adb shell am start -n com.billkevin91.mascota/.MainActivity` y alguno de estos extras:
     * - `--es soltar esheep64,neko` suelta esas mascotas; con `--es mover esheep64:7` les hace
     *   repetir ese movimiento (modo prueba); `--ez nombres true`, `--ez sonido true` los activan.
     * - `--ez crear_prueba true` crea una mascota desde un dibujo de ejemplo y la suelta.
     * - `--es compartir esheep64` abre el menú de compartir esa mascota.
     * - `--ez pantalla_completa true` oculta las barras (para probar que ellas se ocultan).
     * Devuelve si había alguna orden (y entonces no se pide el permiso de notificaciones).
     */
    private fun testHook(): Boolean {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        intent.getStringExtra("compartir")?.let { runCatching { PetShare.share(this, it) }; return true }
        if (intent.getBooleanExtra("pantalla_completa", false)) {
            if (Build.VERSION.SDK_INT >= 30) {
                window.insetsController?.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            }
            return true
        }
        if (!Settings.canDrawOverlays(this)) return false
        if (intent.getBooleanExtra("crear_prueba", false)) {
            Thread {
                runCatching { MakerActivity.create(this, Drawing.demo(), "Dibujo de prueba", 1, 1, removeBg = true, facingRight = false) }
                    .onSuccess {
                        android.util.Log.i("Mascotas", "creada: ${it.id}")
                        PetService.add(this, it.id, size)
                    }.onFailure { android.util.Log.e("Mascotas", "crear: no se pudo", it) }
            }.start()
            return true
        }
        val release = intent.getStringExtra("soltar") ?: return false
        // Antes de soltarlas (el servicio lo mira al arrancar). --ez medir true: el servicio cuenta cada 30 s lo que gasta (ver PetService.measure)
        if (intent.hasExtra("medir")) prefs.edit().putBoolean("medir", intent.getBooleanExtra("medir", false)).commit()
        release.split(',').filter { PetLibrary.exists(this, it) }.forEach { PetService.add(this, it, size) }
        if (intent.hasExtra("nombres")) prefs.edit().putBoolean(PREF_NAMES, intent.getBooleanExtra("nombres", false)).apply()
        if (intent.hasExtra("sonido")) prefs.edit().putBoolean(PREF_SOUND, intent.getBooleanExtra("sonido", false)).apply()
        intent.getStringExtra("mover")?.split(',')?.forEach { m ->
            val (p, id) = m.split(':').let { it[0] to (it.getOrNull(1)?.toIntOrNull() ?: -1) }
            if (PetLibrary.exists(this, p)) PetService.play(this, p, id, true)
        }
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Si Android cierra la app mientras eliges dónde guardar, al volver se sabe qué mascota era
        savingId?.let { outState.putString("guardando", it) }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun release() {
        if (!Settings.canDrawOverlays(this)) { askOverlay(); return }
        save()
        PetService.add(this, pet, size)
        goHome()
    }

    /** Vuelve al inicio para ver a las mascotas. */
    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun askOverlay() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    private fun save() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_PET, pet).putFloat(PREF_SIZE, size).apply()
    }

    private fun refresh() {
        // También se enciende o apaga desde la notificación
        soundBox.isChecked = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(PREF_SOUND, false)
        val ok = Settings.canDrawOverlays(this)
        permission.text = if (ok) tr("✔ Permiso concedido", "✔ Permission granted") else tr("Para andar por tu pantalla, la app necesita el permiso \"Mostrar sobre otras apps\".", "To walk on your screen, the app needs the \"Display over other apps\" permission.")
        grant.visibility = if (ok) View.GONE else View.VISIBLE
        for ((folder, v) in petCards) {
            v.background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                if (folder == pet) { setColor(Color.rgb(220, 240, 210)); setStroke(dp(3), green) }
                else { setColor(Color.rgb(245, 245, 245)); setStroke(dp(1), Color.LTGRAY) }
            }
        }
        for ((f, b) in sizeButtons) b.setTypeface(null, if (f == size) Typeface.BOLD else Typeface.NORMAL)
        for ((f, b) in sizeButtons) b.alpha = if (f == size) 1f else 0.55f
    }

    private fun fillGrid() {
        if (::removeButton.isInitialized) removeButton.visibility = if (PetLibrary.all(this).any { !it.bundled }) View.VISIBLE else View.GONE
        grid.removeAllViews()
        petCards.clear()
        PetLibrary.all(this).chunked(3).forEach { rowPets ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowPets.forEach { e -> row.addView(card(e), LinearLayout.LayoutParams(0, dp(110), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }) }
            repeat(3 - rowPets.size) { row.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f)) }
            grid.addView(row)
        }
    }

    private fun showCredits() {
        Thread {
            val text = Credits.text(this)
            runOnUiThread {
                val tv = TextView(this).apply {
                    this.text = text
                    textSize = 13f
                    setTextIsSelectable(true)
                    autoLinkMask = android.text.util.Linkify.WEB_URLS
                    setPadding(dp(20), dp(12), dp(20), dp(12))
                }
                AlertDialog.Builder(this).setTitle(tr("Créditos y licencias", "Credits and licenses"))
                    .setView(ScrollView(this).apply { addView(tv) })
                    .setPositiveButton(tr("Cerrar", "Close"), null).show()
            }
        }.start()
    }

    /** Lista de las importadas para marcar las que se quitan. */
    private fun removeImported() {
        val imported = PetLibrary.all(this).filter { !it.bundled }
        if (imported.isEmpty()) return
        val checked = BooleanArray(imported.size)
        AlertDialog.Builder(this).setTitle(tr("Quitar mascotas importadas", "Remove imported pets"))
            .setMultiChoiceItems(imported.map { it.name }.toTypedArray(), checked) { _, i, on -> checked[i] = on }
            .setPositiveButton(tr("Quitar", "Remove")) { _, _ -> delete(imported.filterIndexed { i, _ -> checked[i] }.map { it.id }) }
            .setNegativeButton(tr("Cancelar", "Cancel"), null).show()
    }

    private fun delete(ids: List<String>) {
        for (id in ids) {
            PetLibrary.delete(this, id)
            PetService.forget(this, id)  // también se va de la pantalla
        }
        if (pet in ids) { pet = PetLibrary.BUNDLED.first(); save() }
        fillGrid()
        refresh()
        if (ids.isNotEmpty()) Toast.makeText(this, if (ids.size == 1) tr("Mascota quitada", "Pet removed") else tr("${ids.size} mascotas quitadas", "${ids.size} pets removed"), Toast.LENGTH_SHORT).show()
    }

    private fun pickXml() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
            .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/xml", "application/xml", "text/plain", "application/octet-stream"))
        runCatching { startActivityForResult(i, IMPORT) }
            .onFailure { Toast.makeText(this, tr("No hay explorador de archivos", "There's no file browser"), Toast.LENGTH_LONG).show() }
    }

    @Deprecated("Activity sin AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (requestCode == PetShare.SAVE_REQUEST && resultCode == RESULT_OK) {
            val id = savingId ?: return
            val ok = runCatching { PetShare.saveTo(this, id, uri) }.isSuccess
            Toast.makeText(this, if (ok) tr("Guardada", "Saved") else tr("No se pudo guardar", "Couldn't save"), Toast.LENGTH_SHORT).show()
            return
        }
        if (requestCode != IMPORT || resultCode != RESULT_OK) return
        Toast.makeText(this, tr("Importando...", "Importing..."), Toast.LENGTH_SHORT).show()
        Thread {
            val result = runCatching {
                contentResolver.openInputStream(uri)!!.use { PetLibrary.import(this, it) }
            }
            runOnUiThread {
                result.onSuccess { r ->
                    val e = r.entry
                    // Si reemplazó a una que estaba en pantalla, que use la versión nueva
                    if (r.replaced) PetService.forget(this, e.id)
                    pet = e.id
                    save()
                    fillGrid()
                    refresh()
                    Toast.makeText(this, if (r.replaced) tr("${e.name} se actualizó (ya la tenías)", "${e.name} was updated (you already had it)") else tr("¡${e.name} está lista para soltar!", "${e.name} is ready to release!"),
                        Toast.LENGTH_LONG).show()
                }.onFailure { err ->
                    AlertDialog.Builder(this).setTitle(tr("No se pudo importar", "Couldn't import"))
                        .setMessage(err.message ?: err.toString()).setPositiveButton(tr("Vale", "OK"), null).show()
                }
            }
        }.start()
    }

    private fun card(e: PetEntry): View {
        val folder = e.id
        val icon = PetLibrary.icon(this, folder)
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(8), dp(4), dp(8))
            addView(ImageView(this@MainActivity).apply {
                if (icon != null) {
                    // Pixelado, sin suavizar
                    val big = Bitmap.createScaledBitmap(icon, icon.width * 3, icon.height * 3, false)
                    setImageDrawable(BitmapDrawable(resources, big).apply { paint.isFilterBitmap = false })
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, LinearLayout.LayoutParams(dp(56), dp(56)))
            addView(TextView(this@MainActivity).apply {
                text = e.name
                gravity = Gravity.CENTER
                textSize = 13f
                maxLines = 2
            })
            setOnClickListener { pet = folder; save(); refresh() }
            // Mantener pulsada: compartirla, guardarla como archivo o (si es tuya) quitarla
            setOnLongClickListener {
                val options = arrayListOf(tr("Compartir", "Share"), tr("Guardar como archivo", "Save as file"))
                if (!e.bundled) options.add(tr("Quitar", "Remove"))
                AlertDialog.Builder(this@MainActivity).setTitle(e.name)
                    .setItems(options.toTypedArray()) { _, which ->
                        when (which) {
                            0 -> runCatching { PetShare.share(this@MainActivity, folder) }
                            1 -> {
                                savingId = folder
                                if (!PetShare.askWhere(this@MainActivity, folder)) Toast.makeText(this@MainActivity,
                                    tr("No hay explorador de archivos", "There's no file browser"), Toast.LENGTH_SHORT).show()
                            }
                            2 -> AlertDialog.Builder(this@MainActivity).setTitle(tr("¿Quitar ${e.name}?", "Remove ${e.name}?"))
                                .setMessage(tr("Se borra la mascota importada de este teléfono.", "The pet is deleted from this phone."))
                                .setPositiveButton(tr("Quitar", "Remove")) { _, _ -> delete(listOf(folder)) }
                                .setNegativeButton(tr("Cancelar", "Cancel"), null).show()
                        }
                    }.show()
                true
            }
        }
        petCards[folder] = v
        return v
    }

    private fun title(s: String) = TextView(this).apply {
        text = s
        textSize = 18f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(20), 0, dp(6))
    }

    private fun text(s: String) = TextView(this).apply {
        text = s
        textSize = 15f
        setPadding(0, dp(8), 0, dp(8))
    }

    private fun button(s: String, onClick: () -> Unit) = Button(this).apply {
        text = s
        isAllCaps = false
        setOnClickListener { onClick() }
    }
}
