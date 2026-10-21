package com.billkevin91.mascota

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import kotlin.math.roundToInt

class MainActivity : Activity() {

    companion object {
        const val PREFS = "mascotas"
        const val PREF_PET = "pet"
        const val PREF_SIZE = "size"

        /** Carpetas en assets/pets (mascotas de desktopPet). */
        val PETS = listOf("esheep64", "blue_sheep", "pink_sheep", "neko", "pink_neko", "fox", "pink_fox")
        private val NAMES = mapOf(
            "esheep64" to "eSheep", "blue_sheep" to "Oveja azul", "pink_sheep" to "Oveja rosa",
            "neko" to "Neko", "pink_neko" to "Neko rosa", "fox" to "Zorrito", "pink_fox" to "Zorrito rosa",
        )
        private val SIZES = listOf("Pequeña" to 0.75f, "Mediana" to 1f, "Grande" to 1.5f, "Enorme" to 2f)
    }

    private val green = Color.rgb(62, 125, 46)
    private lateinit var permission: TextView
    private lateinit var grant: Button
    private val petCards = HashMap<String, View>()
    private val sizeButtons = ArrayList<Pair<Float, Button>>()
    private var pet = PETS.first()
    private var size = 1f

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        pet = prefs.getString(PREF_PET, null)?.takeIf { it in PETS } ?: PETS.first()
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
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        PETS.chunked(3).forEach { rowPets ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowPets.forEach { folder -> row.addView(card(folder), LinearLayout.LayoutParams(0, dp(110), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }) }
            repeat(3 - rowPets.size) { row.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f)) }
            grid.addView(row)
        }
        col.addView(grid)

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

        col.addView(View(this), LinearLayout.LayoutParams(1, dp(16)))
        col.addView(button("Soltar en la pantalla") { release() }.apply {
            textSize = 18f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply { setColor(green); cornerRadius = dp(12).toFloat() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)))
        col.addView(View(this), LinearLayout.LayoutParams(1, dp(8)))
        col.addView(button("Quitar todas") { PetService.clear(this) })

        col.addView(text("Mascotas del proyecto desktopPet de Adriano Petrucci y su comunidad " +
            "(github.com/Adrianotiger/desktopPet, licencia MIT).").apply { textSize = 12f; alpha = 0.6f; setPadding(0, dp(24), 0, 0) })

        // Android 15 dibuja detrás de las barras: deja espacio para ellas
        setContentView(ScrollView(this).apply { fitsSystemWindows = true; addView(col) })

        // Para la prueba automática: adb shell am start ... --es soltar esheep64,neko
        val test = intent.getStringExtra("soltar")
        if (test != null && Settings.canDrawOverlays(this)) {
            test.split(',').filter { it in PETS }.forEach { PetService.add(this, it, size) }
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
        // Vuelve al inicio para verla pasear
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun askOverlay() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    private fun save() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_PET, pet).putFloat(PREF_SIZE, size).apply()
    }

    private fun refresh() {
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

    private fun card(folder: String): View {
        val icon = runCatching {
            assets.open("pets/$folder/icon.png").use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
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
                text = NAMES[folder] ?: folder
                gravity = Gravity.CENTER
                textSize = 13f
            })
            setOnClickListener { pet = folder; save(); refresh() }
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
