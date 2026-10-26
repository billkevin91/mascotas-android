package com.billkevin91.mascota

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reacciones al teléfono: batería baja (sueño). Cargar y notificaciones usan la caricia (PlayTest). */
class ReactTest {
    private val pets = Pets.withExtras

    private fun sleepShare(def: PetDef, sleepy: Boolean): Double {
        var asleep = 0L; var total = 0L
        for (seed in 1..6) {
            val w = World(seed = seed)
            val b = w.add(def, start = false)
            b.sleepy = sleepy
            b.start()
            var last = 0L; var name = b.animationName
            w.run(30 * 60_000) { p ->
                if (p !== b) return@run
                val dt = w.now - last
                if ("sleep" in name.lowercase()) asleep += dt
                total += dt; last = w.now; name = p.animationName
            }
        }
        return asleep.toDouble() / total
    }

    @Test
    fun conLaBateriaBajaDuermenMas() {
        for ((name, def) in pets) {
            if (PetMoves(def).sleep.isEmpty()) continue
            val normal = sleepShare(def, false)
            val low = sleepShare(def, true)
            println("batería baja: $name duerme ${(normal * 100).toInt()} % → ${(low * 100).toInt()} %")
            assertTrue("$name no duerme más con la batería baja ($normal → $low)", low > normal * 1.3 || low > 0.85)
        }
    }

    @Test
    fun todasSabenDormirse() {
        for ((name, def) in pets) {
            val m = PetMoves(def)
            assertTrue("$name sin siesta", m.sleep.isNotEmpty())
            for (id in m.sleep) assertTrue("$name: ${def.animations[id]?.name} no es dormir", "sleep" in def.animations.getValue(id).name.lowercase())
        }
    }

    @Test
    fun seDuermenSiEstanTranquilas() {
        for ((name, def) in pets) {
            var slept = 0
            for (seed in 1..10) {
                val w = World(seed = seed)
                val b = w.add(def)
                w.run(seed * 3_000L)
                if (b.doze() && "sleep" in b.animationName.lowercase()) slept++
            }
            assertTrue("$name no se durmió nunca al avisarle ($slept de 10)", slept > 0)
        }
    }

    @Test
    fun laBateriaBajaNoInterrumpeElBano() {
        val def = Pets.def("esheep64")
        val w = World(seed = 4)
        val b = w.add(def)
        b.play(Pets.id(def, "batha"), loop = false)
        w.run(w.now + 600) { }
        w.run(w.now + 20_000) { p ->
            if (p !== b) return@run
            val tub = w.pets.any { w.parent[it] === b && !it.finished }
            val before = p.animationName
            p.doze()
            if (tub || before.startsWith("bath")) assertEquals("se durmió en medio del baño", before, p.animationName)
        }
    }
}
