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
        private const val IMPORT = 7
        private val SIZES = listOf("Pequeña" to 0.75f, "Mediana" to 1f, "Grande" to 1.5f, "Enorme" to 2f)
        private val SPEEDS = listOf("Lenta" to 0.5f, "Tranquila" to 0.75f, "Normal" to 1f, "Rápida" to 1.25f)

        /** Botones de velocidad (se guardan y el servicio la aplica al momento). */
        /** Echa comida si hay mascotas paseando; si no, avisa. Devuelve si la echó. */
        fun feed(a: Activity): Boolean {
            if (PetService.petsOnScreen == 0) {
                android.widget.Toast.makeText(a, "Primero suelta una mascota", android.widget.Toast.LENGTH_SHORT).show()
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
    private var pet = PetLibrary.BUNDLED.first()
    private var size = 1f

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        pet = prefs.getString(PREF_PET, null)?.takeIf { PetLibrary.exists(this, it) } ?: PetLibrary.BUNDLED.first()
        size = prefs.getFloat(PREF_SIZE, 1f)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(24))
        }
        col.addView(TextView(this).apply {
            text = "Mascotas en pantalla"
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(green)
        })
        col.addView(text("Una mascota de los 90 que camina por encima de tus apps. " +
            "Arrástrala con el dedo, lánzala, y tócala dos veces para despedirla."))

        permission = text("")
        col.addView(permission)
        grant = button("Dar permiso para mostrarse sobre otras apps") { askOverlay() }
        col.addView(grant)

        col.addView(title("Elige tu mascota"))
        grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fillGrid()
        col.addView(grid)
        col.addView(button("Importar mascota (animations.xml)") { pickXml() })
        removeButton = button("Quitar mascotas importadas") { removeImported() }
        removeButton.visibility = if (PetLibrary.all(this).any { !it.bundled }) View.VISIBLE else View.GONE
        col.addView(removeButton)
        col.addView(text("Sirven las de desktopPet y las que hagas con su PetEditor. Si importas una con " +
            "el mismo nombre que otra tuya, se reemplaza. También puedes quitar una manteniéndola pulsada.")
            .apply { textSize = 12f; alpha = 0.6f })

        col.addView(title("Tamaño"))
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

        col.addView(title("Velocidad"))
        col.addView(speedRow(this))
        soundBox = android.widget.CheckBox(this)
        col.addView(soundBox.apply {
            text = "Sonidos de las mascotas (algunas traen: las ovejas de colores, los zorritos...)"
            isChecked = prefs.getBoolean(PREF_SOUND, false)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(PREF_SOUND, on).apply() }
            setPadding(0, dp(8), 0, dp(8))
        })
        col.addView(android.widget.CheckBox(this).apply {
            text = "Ahorro de batería: se mueven a pasos, sin deslizarse (también se activa solo con el ahorro de batería del teléfono)"
            isChecked = prefs.getBoolean(PREF_SAVER, false)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(PREF_SAVER, on).apply() }
            setPadding(0, dp(8), 0, dp(8))
        })

        col.addView(View(this), LinearLayout.LayoutParams(1, dp(16)))
        col.addView(button("Soltar en la pantalla") { release() }.apply {
            textSize = 18f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply { setColor(green); cornerRadius = dp(12).toFloat() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)))
        col.addView(View(this), LinearLayout.LayoutParams(1, dp(8)))
        col.addView(button("🍪 Dar de comer") { if (feed(this)) goHome() })
        col.addView(text("Toca una mascota para acariciarla. La comida se puede arrastrar: " +
            "irán a por ella. También desde el icono de la app (mantenlo pulsado).").apply { textSize = 13f; alpha = 0.7f })
        col.addView(button("Quitar todas") { PetService.clear(this) })
        col.addView(button("Probar movimientos") { startActivity(Intent(this, TestActivity::class.java)) })
        col.addView(button("Editar mascotas") { startActivity(Intent(this, EditorActivity::class.java)) })

        col.addView(text("Mascotas del proyecto desktopPet de Adriano Petrucci y su comunidad " +
            "(github.com/Adrianotiger/desktopPet, licencia MIT).").apply { textSize = 12f; alpha = 0.6f; setPadding(0, dp(24), 0, 0) })
        col.addView(button("Créditos y licencias") { showCredits() })

        // Android 15 dibuja detrás de las barras: deja espacio para ellas
        setContentView(ScrollView(this).apply { fitsSystemWindows = true; addView(col) })

        // Para la prueba automática: adb shell am start ... --es soltar esheep64,neko
        val test = intent.getStringExtra("soltar")
        if (test != null && Settings.canDrawOverlays(this)) {
            test.split(',').filter { PetLibrary.exists(this, it) }.forEach { PetService.add(this, it, size) }
            // ... --es mover esheep64:7 --ez nombres true (modo prueba)
            if (intent.hasExtra("nombres")) prefs.edit().putBoolean(PREF_NAMES, intent.getBooleanExtra("nombres", false)).apply()
            if (intent.hasExtra("sonido")) prefs.edit().putBoolean(PREF_SOUND, intent.getBooleanExtra("sonido", false)).apply()
            intent.getStringExtra("mover")?.split(',')?.forEach { m ->
                val (p, id) = m.split(':').let { it[0] to (it.getOrNull(1)?.toIntOrNull() ?: -1) }
                if (PetLibrary.exists(this, p)) PetService.play(this, p, id, true)
            }
            return
        }
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
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
        permission.text = if (ok) "✔ Permiso concedido" else "Para andar por tu pantalla, la app necesita el permiso \"Mostrar sobre otras apps\"."
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
                AlertDialog.Builder(this).setTitle("Créditos y licencias")
                    .setView(ScrollView(this).apply { addView(tv) })
                    .setPositiveButton("Cerrar", null).show()
            }
        }.start()
    }

    /** Lista de las importadas para marcar las que se quitan. */
    private fun removeImported() {
        val imported = PetLibrary.all(this).filter { !it.bundled }
        if (imported.isEmpty()) return
        val checked = BooleanArray(imported.size)
        AlertDialog.Builder(this).setTitle("Quitar mascotas importadas")
            .setMultiChoiceItems(imported.map { it.name }.toTypedArray(), checked) { _, i, on -> checked[i] = on }
            .setPositiveButton("Quitar") { _, _ -> delete(imported.filterIndexed { i, _ -> checked[i] }.map { it.id }) }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun delete(ids: List<String>) {
        for (id in ids) {
            PetLibrary.delete(this, id)
            PetService.forget(this, id)  // también se va de la pantalla
        }
        if (pet in ids) { pet = PetLibrary.BUNDLED.first(); save() }
        fillGrid()
        refresh()
        if (ids.isNotEmpty()) Toast.makeText(this, if (ids.size == 1) "Mascota quitada" else "${ids.size} mascotas quitadas", Toast.LENGTH_SHORT).show()
    }

    private fun pickXml() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
            .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/xml", "application/xml", "text/plain", "application/octet-stream"))
        runCatching { startActivityForResult(i, IMPORT) }
            .onFailure { Toast.makeText(this, "No hay explorador de archivos", Toast.LENGTH_LONG).show() }
    }

    @Deprecated("Activity sin AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (requestCode != IMPORT || resultCode != RESULT_OK) return
        Toast.makeText(this, "Importando...", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this, if (r.replaced) "${e.name} se actualizó (ya la tenías)" else "¡${e.name} está lista para soltar!",
                        Toast.LENGTH_LONG).show()
                }.onFailure { err ->
                    AlertDialog.Builder(this).setTitle("No se pudo importar")
                        .setMessage(err.message ?: err.toString()).setPositiveButton("Vale", null).show()
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
            if (!e.bundled) setOnLongClickListener {
                AlertDialog.Builder(this@MainActivity).setTitle("¿Quitar ${e.name}?")
                    .setMessage("Se borra la mascota importada de este teléfono.")
                    .setPositiveButton("Quitar") { _, _ -> delete(listOf(folder)) }
                    .setNegativeButton("Cancelar", null).show()
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
