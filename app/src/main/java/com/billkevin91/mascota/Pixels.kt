package com.billkevin91.mascota

import kotlin.math.abs

/** Cuentas con píxeles (ARGB) para preparar dibujos; sin Android, para poder probarlas. */
object Pixels {
    private fun a(c: Int) = c ushr 24
    private fun r(c: Int) = (c shr 16) and 0xFF
    private fun g(c: Int) = (c shr 8) and 0xFF
    private fun b(c: Int) = c and 0xFF

    /**
     * Quita el fondo: rellena desde las 4 esquinas todo lo que se parece al color de la esquina
     * (como el bote de pintura) y lo deja transparente. Lo de dentro del dibujo, aunque sea del
     * mismo color (los ojos blancos sobre fondo blanco), se queda porque no toca el borde.
     */
    fun removeBackground(px: IntArray, w: Int, h: Int, tolerance: Int = 48): IntArray {
        val out = px.copyOf()
        val key = px[0]
        fun near(c: Int) = a(c) < 16 ||
            abs(r(c) - r(key)) + abs(g(c) - g(key)) + abs(b(c) - b(key)) <= tolerance
        val seen = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0
        var tail = 0
        fun push(i: Int) {
            if (!seen[i] && near(px[i])) { seen[i] = true; queue[tail++] = i }
        }
        push(0); push(w - 1); push((h - 1) * w); push(h * w - 1)
        while (head < tail) {
            val i = queue[head++]
            out[i] = 0
            val x = i % w
            val y = i / w
            if (x > 0) push(i - 1)
            if (x < w - 1) push(i + 1)
            if (y > 0) push(i - w)
            if (y < h - 1) push(i + w)
        }
        return out
    }

    /** Caja (izquierda, arriba, derecha, abajo; incluidos) de lo que no es transparente, o null si no hay nada. */
    fun bounds(px: IntArray, w: Int, h: Int): IntArray? {
        var l = w; var t = h; var r = -1; var bt = -1
        for (y in 0 until h) for (x in 0 until w) {
            if (a(px[y * w + x]) >= 16) {
                if (x < l) l = x
                if (x > r) r = x
                if (y < t) t = y
                if (y > bt) bt = y
            }
        }
        return if (r < 0) null else intArrayOf(l, t, r, bt)
    }
}
