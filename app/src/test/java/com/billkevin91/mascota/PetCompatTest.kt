package com.billkevin91.mascota

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class PetCompatTest {
    private fun xml(name: String) = File(Pets.dir, "$name/animations.xml").readText(Charsets.UTF_8)

    /** Las incluidas ya llevan el arreglo: aplicarlo otra vez no cambia nada. */
    @Test fun noSeAplicaDosVeces() {
        for (name in Pets.all) assertEquals(name, xml(name), PetCompat.patch(xml(name)))
    }

    /** Un XML original de desktopPet (se reconstruye deshaciendo el arreglo) queda como la copia incluida. */
    @Test fun arreglaElOriginal() {
        val ours = xml("pink_sheep")
        val original = ours
            .replace("<y>areaH-imageH-(bathH/2+(randS*bathH/2)/120)", "<y>areaH/2-(randS*areaH/2)/120-imageH")
            .replace("repeat=\"(bathH/2+(randS*bathH/2)/120-imageH-68)/2\"", "repeat=\"(areaH/2+(randS*screenH/2)/120-imageH-68)/2\"")
            .replace("repeat=\"(bathH/2+(randS*bathH/2)/120-imageH-10)/2\"", "repeat=\"(screenH/2+(randS*screenH/2)/120-imageH-10)/2\"")
            .replace("<x>screenW-(bathH/2+(randS*bathH/2)/120)", "<x>screenW-areaH/2-(randS*areaH/2)/120")
            // Sólo la de bath_start (bath_startB ya iba a 217 en el original)
            .replaceFirst("<next probability=\"100\" only=\"taskbar\">217</next>", "<next probability=\"100\" only=\"taskbar\">223</next>")
        assert(original != ours)
        assertEquals(ours, PetCompat.patch(original))
    }
}
