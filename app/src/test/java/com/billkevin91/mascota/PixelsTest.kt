package com.billkevin91.mascota

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PixelsTest {
    private val white = 0xFFFFFFFF.toInt()
    private val offWhite = 0xFFF4F2F0.toInt()  // fondo de una foto: casi blanco
    private val red = 0xFFDC283C.toInt()
    private val black = 0xFF000000.toInt()

    /** Bolita roja (con un ojo blanco dentro) sobre fondo casi blanco, de 20 × 16. */
    private fun ball(bg: Int = offWhite): IntArray {
        val w = 20; val h = 16
        return IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            val inside = (x - 10) * (x - 10) + (y - 8) * (y - 8) <= 36
            val eye = x in 8..9 && y in 6..7
            val pupil = x == 9 && y == 7
            when { pupil -> black; eye -> white; inside -> red; else -> bg }
        }
    }

    @Test
    fun quitaElFondoPeroNoLosOjos() {
        val out = Pixels.removeBackground(ball(), 20, 16)
        assertEquals("esquina", 0, out[0])
        assertEquals("borde", 0, out[19 * 1 + 0])
        assertEquals("rojo", red, out[8 * 20 + 10])
        assertEquals("ojo blanco dentro del dibujo", white, out[6 * 20 + 8])
        assertEquals("pupila", black, out[7 * 20 + 9])
    }

    @Test
    fun recortaAlDibujo() {
        val out = Pixels.removeBackground(ball(), 20, 16)
        assertArrayEquals(intArrayOf(4, 2, 16, 14), Pixels.bounds(out, 20, 16))
    }

    @Test
    fun conFondoTransparenteNoTocaElDibujo() {
        val px = ball(bg = 0)
        val out = Pixels.removeBackground(px, 20, 16)
        assertArrayEquals(px, out)
    }

    @Test
    fun todoFondoEsVacio() {
        val out = Pixels.removeBackground(IntArray(30) { offWhite }, 6, 5)
        assertNull(Pixels.bounds(out, 6, 5))
    }
}
