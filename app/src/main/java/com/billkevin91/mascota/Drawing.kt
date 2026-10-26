package com.billkevin91.mascota

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** Prepara tu dibujo para que sea una mascota: quita el fondo, lo recorta y dibuja sus poses. */
object Drawing {
    /** Tamaño máximo del dibujo en la hoja (en la pantalla se amplía según el tamaño elegido). */
    const val MAX_SIDE = 64

    /** ¿La esquina ya es transparente? (PNG con fondo transparente: no hay que quitar nada). */
    fun hasTransparentCorner(b: Bitmap) = Color.alpha(b.getPixel(0, 0)) < 16

    /** Quita el fondo que toca los bordes (ver [Pixels.removeBackground]). */
    fun removeBackground(src: Bitmap): Bitmap {
        val px = IntArray(src.width * src.height)
        src.getPixels(px, 0, src.width, 0, 0, src.width, src.height)
        return Bitmap.createBitmap(Pixels.removeBackground(px, src.width, src.height), src.width, src.height, Bitmap.Config.ARGB_8888)
    }

    /** Recorta lo transparente de alrededor. */
    fun trim(src: Bitmap): Bitmap {
        val px = IntArray(src.width * src.height)
        src.getPixels(px, 0, src.width, 0, 0, src.width, src.height)
        val (l, t, r, b) = requireNotNull(Pixels.bounds(px, src.width, src.height)) { "El dibujo está vacío (¿todo era fondo?)" }.toList()
        return Bitmap.createBitmap(src, l, t, r - l + 1, b - t + 1)
    }

    /** Lo achica hasta [MAX_SIDE] si es más grande (los pequeños, como el pixel art, se quedan igual). */
    fun fit(src: Bitmap, maxSide: Int = MAX_SIDE): Bitmap {
        val m = max(src.width, src.height)
        if (m <= maxSide) return src
        val f = maxSide.toFloat() / m
        return Bitmap.createScaledBitmap(src, max(1, (src.width * f).roundToInt()), max(1, (src.height * f).roundToInt()), true)
    }

    /** Espejo horizontal (las mascotas se dibujan mirando a la izquierda). */
    fun mirror(src: Bitmap): Bitmap =
        Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { preScale(-1f, 1f) }, false)

    /** Las 11 poses de [PetMaker.SINGLE] en una fila, a partir de un solo dibujo (ya recortado). */
    fun singleSheet(d: Bitmap): Bitmap {
        val w = d.width.toFloat()
        val h = d.height.toFloat()
        // Casilla cuadrada con sitio para inclinarse, saltar y tumbarse
        val t = ceil(max(w, h) * 1.3f).toInt()
        val sheet = Bitmap.createBitmap(t * PetMaker.SINGLE_TILES, t, Bitmap.Config.ARGB_8888)
        val c = Canvas(sheet)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val zPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(90, 110, 200)
            typeface = Typeface.DEFAULT_BOLD
            textSize = t * 0.22f
        }
        // Cada pose: escala, giro y subida, siempre apoyada en el centro de abajo de su casilla
        fun pose(i: Int, sx: Float = 1f, sy: Float = 1f, deg: Float = 0f, up: Float = 0f, lying: Boolean = false, z: String = "") {
            val m = Matrix()
            m.postTranslate(-w / 2, -h)
            m.postScale(sx, sy)
            m.postRotate(if (lying) -90f else deg)
            m.postTranslate(0f, -up * h)
            if (lying) {
                // Tumbada de lado: tras girar, su caja se vuelve a apoyar centrada en el suelo
                val box = android.graphics.RectF(0f, 0f, w, h)
                m.mapRect(box)
                m.postTranslate(-box.centerX(), -box.bottom)
            }
            m.postTranslate(i * t + t / 2f, t.toFloat())
            c.drawBitmap(d, m, paint)
            if (z.isNotEmpty()) c.drawText(z, i * t + t * 0.62f, t * 0.42f, zPaint)
        }
        pose(0)
        pose(1, sx = 1.06f, sy = 0.93f)
        pose(2, deg = 7f)
        pose(3, deg = -7f)
        pose(4, up = 0.12f)
        pose(5, sx = 0.92f, sy = 1.08f, deg = 4f)
        pose(6, sx = 1.2f, sy = 0.78f)
        pose(7, lying = true)
        pose(8, lying = true, z = "z")
        pose(9, lying = true, z = "zZ")
        pose(10, up = 0.25f)
        return sheet
    }

    /**
     * Hoja con [cols] × [rows] dibujos: cada uno se achica hasta [MAX_SIDE] (y se voltea si
     * hace falta) y se vuelven a juntar en una hoja de casillas iguales.
     */
    fun gridSheet(src: Bitmap, cols: Int, rows: Int, flip: Boolean): Bitmap {
        require(cols in 1..20 && rows in 1..20) { "Columnas y filas: de 1 a 20" }
        val cw = src.width / cols
        val ch = src.height / rows
        require(cw > 0 && ch > 0) { "La imagen es demasiado pequeña para tantas casillas" }
        val f = minOf(1f, MAX_SIDE.toFloat() / max(cw, ch))
        val tw = max(1, (cw * f).roundToInt())
        val th = max(1, (ch * f).roundToInt())
        val sheet = Bitmap.createBitmap(tw * cols, th * rows, Bitmap.Config.ARGB_8888)
        val c = Canvas(sheet)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        for (r in 0 until rows) for (k in 0 until cols) {
            var tile = Bitmap.createBitmap(src, k * cw, r * ch, cw, ch)
            if (flip) tile = mirror(tile)
            c.drawBitmap(tile, android.graphics.Rect(0, 0, cw, ch), android.graphics.Rect(k * tw, r * th, (k + 1) * tw, (r + 1) * th), paint)
        }
        return sheet
    }

    fun toBase64Png(b: Bitmap): String {
        val out = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.PNG, 100, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    /** Un dibujo de ejemplo (para la prueba automática): una bolita roja con ojos sobre fondo blanco. */
    fun demo(): Bitmap {
        val b = Bitmap.createBitmap(200, 160, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(Color.WHITE)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.rgb(220, 40, 60); c.drawOval(40f, 30f, 160f, 150f, p)
        p.color = Color.WHITE; c.drawCircle(75f, 75f, 14f, p); c.drawCircle(120f, 75f, 14f, p)
        p.color = Color.BLACK; c.drawCircle(70f, 77f, 6f, p); c.drawCircle(115f, 77f, 6f, p)
        return b
    }
}
