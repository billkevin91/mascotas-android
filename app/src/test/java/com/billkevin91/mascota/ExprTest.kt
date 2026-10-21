package com.billkevin91.mascota

import org.junit.Assert.assertEquals
import org.junit.Test

class ExprTest {
    private val vars = mapOf("screenW" to 400.0, "imageW" to 40.0, "random" to 50.0, "areaH" to 700.0, "randS" to 30.0)
    private fun eval(s: String) = Expr.eval(s) { vars[it] }

    @Test fun cuentas() {
        assertEquals(50 * (400 - 40 - 50) / 100.0 + 25, eval("random*(screenW-imageW-50)/100+25"), 1e-9)
        assertEquals(-18.0, eval("-imageW*0.45"), 1e-9)
        assertEquals(-28.0, eval("imageX+(-imageW)*0.7"), 1e-9) // imageX no existe aquí: vale 0
        assertEquals(23 + (200 % 30) / 7.0, eval("23+(Convert(screenW/2,System.Int32)%30)/7"), 1e-9)
        assertEquals(700 / 2.0 - (30 * 700 / 2.0) / 120 - 40, eval("areaH/2-(randS*areaH/2)/120-imageW"), 1e-9)
    }

    @Test fun dividirEntreCeroNoRompe() {
        assertEquals(0.0, eval("5/0"), 1e-9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun expresionRota() {
        eval("(3+")
    }
}
