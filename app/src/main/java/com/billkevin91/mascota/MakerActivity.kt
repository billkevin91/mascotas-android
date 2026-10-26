package com.billkevin91.mascota

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * Crear una mascota desde tu dibujo: eliges una imagen y la app arma la hoja de dibujos y sus
 * movimientos básicos. Con un solo dibujo inventa las poses (paso, saltito, tumbada...);
 * con una hoja de varios dibujos usa los tuyos.
 */
class MakerActivity : Activity() {

    companion object {
        private const val PICK = 7

        /** Hace la mascota y la guarda con las tuyas. Lento: llamarlo fuera del hilo principal. */
        fun create(context: Context, image: Bitmap, name: String, cols: Int, rows: Int, removeBg: Boolean, facingRight: Boolean): PetEntry {
            val sheet = sheetFor(image, cols, rows, removeBg, facingRight)
            val single = cols == 1 && rows == 1
            val tilesX = if (single) PetMaker.SINGLE_TILES else cols
            val tilesY = if (single) 1 else rows
            val poses = if (single) PetMaker.SINGLE else PetMaker.gridPoses(cols, rows)
            val def = PetMaker.build(name, "", tilesX, tilesY, Drawing.toBase64Png(sheet), poses)
            return PetLibrary.save(context, null, def)
        }

        /** La hoja de dibujos que tendrá la mascota (también sirve de vista previa). */
        fun sheetFor(image: Bitmap, cols: Int, rows: Int, removeBg: Boolean, facingRight: Boolean): Bitmap {
            val clean = if (removeBg) Drawing.removeBackground(image) else image
            if (cols == 1 && rows == 1) {
                var d = Drawing.fit(Drawing.trim(clean))
                if (facingRight) d = Drawing.mirror(d)
                return Drawing.singleSheet(d)
            }
            return Drawing.gridSheet(clean, cols, rows, facingRight)
        }
    }

    private val green = Color.rgb(62, 125, 46)
    private var image: Bitmap? = null
    private lateinit var preview: ImageView
    private lateinit var single: RadioButton
    private lateinit var cols: EditText
    private lateinit var rows: EditText
    private lateinit var gridRow: LinearLayout
    private lateinit var removeBg: CheckBox
    private lateinit var facingRight: CheckBox
    private lateinit var name: EditText
    private lateinit var status: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(24))
        }
        col.addView(TextView(this).apply {
            text = tr("Crear mascota desde un dibujo", "Create a pet from a drawing")
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(green)
        })
        col.addView(small(tr("Elige un dibujo tuyo (una foto de un papel, un dibujo de una app...). Con un solo dibujo, ", "Choose one of your drawings (a photo of paper, a drawing from an app...). With a single drawing, ") +
            tr("la app inventa sus poses: dar pasos, saltar, tumbarse a dormir... Si tienes varios dibujos en una hoja ", "the app invents its poses: stepping, hopping, lying down to sleep... If you have several drawings on a sheet ") +
            tr("(en cuadrícula), usa los tuyos: la primera fila para caminar, el primero de pie y el último para dormir.", "(in a grid), it uses yours: the first row to walk, the first one standing and the last one to sleep.")))
        col.addView(button(tr("Elegir imagen", "Choose image")) {
            startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), PICK)
        })

        preview = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = GradientDrawable().apply { setColor(Color.rgb(230, 240, 250)); cornerRadius = dp(8).toFloat() }
            minimumHeight = dp(80)
        }
        col.addView(preview, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, dp(8), 0, dp(8))
        })

        single = RadioButton(this).apply { text = tr("Es un solo dibujo", "It's a single drawing"); id = View.generateViewId(); isChecked = true }
        val grid = RadioButton(this).apply { text = tr("Es una hoja con varios dibujos", "It's a sheet with several drawings"); id = View.generateViewId() }
        col.addView(RadioGroup(this).apply {
            addView(single); addView(grid)
            setOnCheckedChangeListener { _, _ -> gridRow.visibility = if (single.isChecked) View.GONE else View.VISIBLE; refresh() }
        })
        cols = number("4"); rows = number("1")
        gridRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            addView(TextView(this@MakerActivity).apply { text = tr("Columnas ", "Columns ") })
            addView(cols, LinearLayout.LayoutParams(dp(64), LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(TextView(this@MakerActivity).apply { text = tr("  Filas ", "  Rows ") })
            addView(rows, LinearLayout.LayoutParams(dp(64), LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(button(tr("Ver", "View")) { refresh() })
        }
        col.addView(gridRow)
        removeBg = CheckBox(this).apply {
            text = tr("Quitar el fondo (el color de las esquinas)", "Remove the background (the color of the corners)")
            isChecked = true
            setOnCheckedChangeListener { _, _ -> refresh() }
        }
        col.addView(removeBg)
        facingRight = CheckBox(this).apply {
            text = tr("Mi dibujo mira a la derecha", "My drawing faces right")
            setOnCheckedChangeListener { _, _ -> refresh() }
        }
        col.addView(facingRight)

        col.addView(TextView(this).apply { text = tr("Nombre", "Name"); setTypeface(typeface, Typeface.BOLD); setPadding(0, dp(10), 0, 0) })
        name = EditText(this).apply { isSingleLine = true; setText(tr("Mi mascota", "My pet")) }
        col.addView(name)

        status = small("")
        col.addView(status)
        col.addView(button(tr("Crear mascota", "Create pet")) { make() }.apply {
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply { setColor(green); cornerRadius = dp(10).toFloat() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)))
        setContentView(ScrollView(this).apply { fitsSystemWindows = true; addView(col) })
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (requestCode != PICK || resultCode != RESULT_OK) return
        val b = runCatching { open(uri) }.getOrNull()
        if (b == null) { status.text = tr("No se pudo abrir esa imagen", "Couldn't open that image"); return }
        image = b
        // Con fondo transparente no hace falta quitar nada
        removeBg.isChecked = !Drawing.hasTransparentCorner(b)
        refresh()
    }

    /** Abre la imagen sin cargarla enorme en memoria (las fotos pueden tener muchos megapíxeles). */
    private fun open(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        return contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    private fun grid(): Pair<Int, Int> = if (single.isChecked) 1 to 1
    else (cols.text.toString().toIntOrNull() ?: 1).coerceIn(1, 20) to (rows.text.toString().toIntOrNull() ?: 1).coerceIn(1, 20)

    /** Vista previa de la hoja que tendrá (todas sus poses). */
    private fun refresh() {
        val b = image ?: return
        val (c, r) = grid()
        val bg = removeBg.isChecked
        val right = facingRight.isChecked
        status.text = tr("Preparando...", "Preparing...")
        Thread {
            val sheet = runCatching { sheetFor(b, c, r, bg, right) }
            runOnUiThread {
                sheet.onSuccess {
                    preview.setImageDrawable(BitmapDrawable(resources, it).apply { paint.isFilterBitmap = false })
                    status.text = if (c == 1 && r == 1) tr("Estas son sus poses. Si se ve raro el fondo, prueba a quitar la casilla del fondo.", "These are its poses. If the background looks odd, try unticking the background box.")
                    else tr("Así quedan tus ${c * r} dibujos (mirando a la izquierda).", "This is how your ${c * r} drawings look (facing left).")
                }.onFailure { status.text = it.message ?: tr("No se pudo preparar el dibujo", "Couldn't prepare the drawing") }
            }
        }.start()
    }

    private fun make() {
        val b = image ?: run { status.text = tr("Primero elige una imagen", "Choose an image first"); return }
        val (c, r) = grid()
        val n = name.text.toString().trim().ifEmpty { tr("Mi mascota", "My pet") }
        val bg = removeBg.isChecked
        val right = facingRight.isChecked
        status.text = tr("Creando...", "Creating...")
        Thread {
            val result = runCatching { create(this, b, n, c, r, bg, right) }
            runOnUiThread {
                result.onSuccess { e ->
                    getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit().putString(MainActivity.PREF_PET, e.id).apply()
                    status.text = tr("¡${e.name} está lista!", "${e.name} is ready!")
                    AlertDialog.Builder(this)
                        .setTitle(tr("¡${e.name} está lista!", "${e.name} is ready!"))
                        .setMessage(tr("Ya camina, corre, salta, duerme y se deja arrastrar. Puedes soltarla ya o ajustar sus movimientos en el editor.", "It already walks, runs, hops, sleeps and can be dragged. Release it now or tweak its movements in the editor."))
                        .setPositiveButton(tr("Soltarla", "Release it")) { _, _ -> release(e) }
                        .setNeutralButton(tr("Editarla", "Edit it")) { _, _ -> startActivity(Intent(this, EditorActivity::class.java)); finish() }
                        .setNegativeButton(tr("Más tarde", "Later")) { _, _ -> finish() }
                        .show()
                }.onFailure { err ->
                    status.text = ""
                    AlertDialog.Builder(this).setTitle(tr("No se pudo crear", "Couldn't create it")).setMessage(err.message ?: err.toString())
                        .setPositiveButton(tr("Vale", "OK"), null).show()
                }
            }
        }.start()
    }

    private fun release(e: PetEntry) {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        val size = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).getFloat(MainActivity.PREF_SIZE, 1f)
        PetService.add(this, e.id, size)
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun small(s: String) = TextView(this).apply { text = s; textSize = 13f; alpha = 0.75f; setPadding(0, dp(4), 0, dp(4)) }
    private fun number(v: String) = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER; isSingleLine = true; setText(v) }
    private fun button(s: String, onClick: () -> Unit) = Button(this).apply { text = s; isAllCaps = false; setOnClickListener { onClick() } }
}
