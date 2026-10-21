package com.billkevin91.mascota

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PetEditTest {
    @Test fun editarGuardarYLeer() {
        val def = Pets.def("esheep64")
        val walk = Pets.id(def, "walk")
        val f = PetEdit.fieldsOf(def.animations.getValue(walk))
        val nueva = PetEdit.Fields(listOf(3, 2, 2, 3), "300", "250", "-4", "-4", "0", "0", "3", 1)
        assertNull(PetEdit.check(def, nueva))
        val edited = PetEdit.renamed(PetEdit.withFields(def, walk, nueva), "Mi oveja")
        val again = PetDef.parse(PetXml.write(edited).byteInputStream())
        val a = again.animations.getValue(walk)
        assertEquals(listOf(3, 2, 2, 3), a.frames)
        assertEquals("300", a.start.interval.text)
        assertEquals("-4", a.start.x.text)
        assertEquals(1, a.repeatFrom)
        assertEquals("Mi oveja", again.header.petName)
        // Lo demás no cambia
        assertEquals(def.animations.size, again.animations.size)
        assertEquals(def.animations.getValue(walk).onBorder.size, a.onBorder.size)
        // La original sigue igual
        assertEquals(f.frames, def.animations.getValue(walk).frames)
        // Y se mueve sin problemas
        val w = World(seed = 1)
        val p = w.add(again)
        p.play(walk, loop = true)
        w.run(20_000)
        assert(!p.finished)
    }

    @Test fun rechazaCamposMalos() {
        val def = Pets.def("esheep64")
        fun f(frames: List<Int> = listOf(1), si: String = "100", x: String = "0", rep: String = "0", from: Int = 0) =
            PetEdit.Fields(frames, si, si, x, x, "0", "0", rep, from)
        assertNotNull(PetEdit.check(def, f(frames = emptyList())))
        assertNotNull(PetEdit.check(def, f(frames = listOf(9999))))
        assertNotNull(PetEdit.check(def, f(si = "rápido")))
        assertNotNull(PetEdit.check(def, f(si = "1")))
        assertNotNull(PetEdit.check(def, f(x = "(3+")))
        assertNotNull(PetEdit.check(def, f(from = 5)))
        assertNull(PetEdit.check(def, f(x = "random/10-5", rep = "5+random/10")))
    }
}
