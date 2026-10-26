package com.billkevin91.mascota

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class LangTest {
    @After fun back() { Lang.english = false }

    @Test
    fun eligeElIdiomaDelTelefono() {
        Lang.init(Locale.forLanguageTag("es-MX")); assertEquals(false, Lang.english)
        Lang.init(Locale.forLanguageTag("es-GT")); assertEquals(false, Lang.english)
        Lang.init(Locale.US); assertEquals(true, Lang.english)
        Lang.init(Locale.FRANCE); assertEquals("Los demás idiomas, en inglés", true, Lang.english)
    }

    @Test
    fun losAvisosDelEditorEnIngles() {
        val sheep = Pets.def("esheep64")
        val walk = Pets.id(sheep, "walk")
        val f = PetEdit.fieldsOf(sheep.animations.getValue(walk))
        val bad = PetEdit.Fields(emptyList(), f.startInterval, f.endInterval, f.startX, f.endX, f.startY, f.endY, f.repeat, 0)
        assertEquals("El movimiento necesita al menos un dibujo", PetEdit.check(sheep, bad))
        Lang.english = true
        assertEquals("The movement needs at least one drawing", PetEdit.check(sheep, bad))
        val slow = PetEdit.Fields(f.frames, "5", f.endInterval, f.startX, f.endX, f.startY, f.endY, f.repeat, 0)
        assertEquals("Speed at the start: between 10 and 10000 ms", PetEdit.check(sheep, slow))
    }

    @Test
    fun mascotaCreadaEnIngles() {
        Lang.english = true
        val def = PetMaker.build("Mine", "", PetMaker.SINGLE_TILES, 1, "", PetMaker.SINGLE)
        assertTrue(def.header.info.startsWith("Created with"))
    }
}
