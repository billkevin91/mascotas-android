package com.billkevin91.mascota

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedEditTest {
    private val sheep = Pets.def("esheep64")
    private val walk = Pets.id(sheep, "walk")
    private val sleep = Pets.id(sheep, "sleep1a")

    private fun adv(def: PetDef, id: Int) = PetEdit.advancedOf(def, id)
    private fun with(a: PetEdit.Advanced, onEnd: List<Next> = a.onEnd, children: List<ChildDef> = a.children,
                     sound: Sound? = a.sound, soundChanged: Boolean = a.soundChanged) =
        PetEdit.Advanced(onEnd, a.onBorder, a.onGravity, children, sound, soundChanged)

    @Test
    fun cambiarQueHaceDespuesSeNota() {
        // Que al terminar de caminar siempre se duerma
        val a = with(adv(sheep, walk), onEnd = listOf(Next(sleep, 100, Only.NONE)))
        assertNull(PetEdit.checkAdvanced(sheep, a))
        val edited = PetEdit.withAdvanced(sheep, walk, a)
        val w = World(seed = 1)
        val b = w.add(edited, start = false)
        b.play(walk, loop = false)
        b.stopLoop()
        // En el centro, para que termine de caminar sin llegar a una pared
        b.x = (w.area.right - b.frameW) / 2.0
        var after: String? = null
        var prev = b.animationName
        w.run(60_000) { p ->
            if (p === b && after == null && prev == "walk" && p.animationName != "walk") after = p.animationName
            if (p === b) prev = p.animationName
        }
        assertEquals("sleep1a", after)
    }

    @Test
    fun seGuardaEnElXml() {
        val sound = Sound(0, 40, 2, "SUQzBAAAAAAA")
        val a = with(adv(sheep, walk),
            onEnd = listOf(Next(sleep, 7, Only.TASKBAR), Next(walk, 93, Only.NONE)),
            children = listOf(ChildDef(Value("imageX+10"), Value("imageY"), sleep)),
            sound = sound, soundChanged = true)
        val edited = PetEdit.withAdvanced(sheep, walk, a)
        val again = PetDef.parse(PetXml.write(edited).byteInputStream())
        val w2 = again.animations.getValue(walk)
        assertEquals(listOf(Triple(sleep, 7, Only.TASKBAR), Triple(walk, 93, Only.NONE)), w2.onEnd.map { Triple(it.id, it.probability, it.only) })
        val kids = again.children.getValue(walk)
        assertEquals(1, kids.size)
        assertEquals("imageX+10", kids[0].x.text)
        assertEquals(sleep, kids[0].next)
        val s = again.sounds.single { it.animationId == walk }
        assertEquals(40, s.probability); assertEquals(2, s.loop); assertEquals("SUQzBAAAAAAA", s.base64)
    }

    @Test
    fun quitarCriasYSonido() {
        val eat = Pets.id(sheep, "eat")
        val withKids = sheep.children[eat]
        assertNotNull("eSheep come con una flor", withKids)
        val noKids = PetEdit.withAdvanced(sheep, eat, with(adv(sheep, eat), children = emptyList()))
        assertNull(noKids.children[eat])

        val fox = Pets.def("fox")
        val idle = Pets.id(fox, "idle")
        assertEquals(3, fox.sounds.count { it.animationId == idle })
        // Sin tocar el sonido se quedan los tres; al cambiarlo, queda uno; al quitarlo, ninguno
        assertEquals(3, PetEdit.withAdvanced(fox, idle, adv(fox, idle)).sounds.count { it.animationId == idle })
        val one = PetEdit.withAdvanced(fox, idle, with(adv(fox, idle), sound = Sound(idle, 50, 0, "AAAA"), soundChanged = true))
        assertEquals(1, one.sounds.count { it.animationId == idle })
        val none = PetEdit.withAdvanced(fox, idle, with(adv(fox, idle), sound = null, soundChanged = true))
        assertEquals(0, none.sounds.count { it.animationId == idle })
        assertEquals("los de otros movimientos siguen", fox.sounds.size - 3, none.sounds.size)
    }

    @Test
    fun avisaDeErrores() {
        val base = adv(sheep, walk)
        assertTrue(PetEdit.checkAdvanced(sheep, with(base, onEnd = listOf(Next(9999, 10, Only.NONE))))!!.contains("no existe"))
        assertTrue(PetEdit.checkAdvanced(sheep, with(base, onEnd = listOf(Next(walk, 0, Only.NONE))))!!.contains("mayor que 0"))
        assertTrue(PetEdit.checkAdvanced(sheep, with(base, onEnd = listOf(Next(walk, 5000, Only.NONE))))!!.contains("de 0 a 1000"))
        assertTrue(PetEdit.checkAdvanced(sheep, with(base, children = listOf(ChildDef(Value("imageX+"), Value("0"), walk))))!!.contains("expresión"))
        assertTrue(PetEdit.checkAdvanced(sheep, with(base, sound = Sound(walk, 150, 0, "AA"), soundChanged = true))!!.contains("0 a 100"))
        assertTrue(PetEdit.checkAdvanced(sheep, with(base, sound = Sound(walk, 50, 0, "A".repeat(PetEdit.MAX_SOUND + 1)), soundChanged = true))!!.contains("largo"))
        assertNull(PetEdit.checkAdvanced(sheep, base))
    }

    @Test
    fun autorYDescripcion() {
        val e = PetEdit.withInfo(sheep, "Bill", "Mi oveja")
        val again = PetDef.parse(PetXml.write(e).byteInputStream())
        assertEquals("Bill", again.header.author)
        assertEquals("Mi oveja", again.header.info)
        assertEquals(sheep.header.petName, again.header.petName)
    }
}
