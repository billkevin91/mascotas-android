package com.billkevin91.mascota

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mascotas creadas desde un dibujo: tienen que funcionar desde el primer momento. */
class MakerTest {
    private val kinds = listOf(
        "un dibujo" to PetMaker.build("Mi dibujo", "Yo", PetMaker.SINGLE_TILES, 1, "", PetMaker.SINGLE),
        "hoja 4x2" to PetMaker.build("Hoja", "Yo", 4, 2, "", PetMaker.gridPoses(4, 2)),
        "hoja 1x1" to PetMaker.build("Uno", "Yo", 1, 1, "", PetMaker.gridPoses(1, 1)),
    )

    @Test
    fun vivenMediaHoraSinAtascarse() {
        for ((kind, def) in kinds) for (seed in 1..6) {
            val w = World(seed = seed)
            val b = w.add(def)
            val seen = HashSet<String>()
            var onFloor = 0L; var total = 0L; var last = 0L
            var moved = 0.0; var lx = b.x
            w.run(30 * 60_000) { p ->
                if (p !== b) return@run
                seen.add(p.animationName)
                val dt = w.now - last; last = w.now; total += dt
                if (p.y + p.frameH >= w.area.bottom - 2) onFloor += dt
                moved += Math.abs(p.x - lx); lx = p.x
                assertTrue("$kind: fuera de la pantalla en ${p.animationName} (${p.x}, ${p.y})",
                    p.x > -p.frameW * 2 && p.x < w.area.screenW + p.frameW * 2 && p.y < w.area.screenH)
            }
            assertTrue("$kind se fue", !b.finished)
            assertTrue("$kind no hace de todo: $seen", seen.containsAll(listOf("walk", "idle", "sleep", "happy")))
            assertTrue("$kind no pisa el suelo (${onFloor * 100 / total} %)", onFloor > total * 0.9)
            assertTrue("$kind no camina ($moved px)", moved > 1000)
        }
    }

    @Test
    fun caeArrastraLanzaYSeDespide() {
        for ((kind, def) in kinds) {
            val w = World(seed = 3)
            val b = w.add(def)
            w.run(5_000)
            val floor = (w.area.bottom - b.frameH).toDouble()
            // Soltarla desde arriba: cae y aterriza
            b.grab(); assertEquals("drag", b.animationName)
            b.y = 50.0
            b.release(0.0, 0.0)
            w.run(w.now + 15_000)
            assertEquals("$kind no aterrizó", floor, b.y, 0.5)
            // Lanzarla: vuela y aterriza
            b.grab(); b.release(25.0, -20.0)
            assertTrue(b.tossing)
            var t = 0
            while (b.tossing && t++ < 2000) b.advanceToss(16.0)
            assertTrue("$kind no aterrizó tras lanzarla", !b.tossing && b.y == floor)
            // Despedirse: se desvanece y se va
            b.kill()
            w.run(w.now + 20_000)
            assertTrue("$kind no se despidió", b.finished)
        }
    }

    @Test
    fun laAppLaEntiende() {
        for ((kind, def) in kinds) {
            val m = PetMoves(def)
            assertTrue("$kind: sin caminar", m.walk == PetMaker.WALK)
            assertTrue("$kind: sin correr", m.run == PetMaker.RUN)
            assertTrue("$kind: sin alegrarse", m.happy == listOf(PetMaker.HAPPY))
            assertTrue("$kind: sin dormir", m.sleep == listOf(PetMaker.SLEEP))
            assertEquals(PetMaker.DRAG, def.drag)
            assertEquals(PetMaker.FALL, def.fall)
            assertEquals(PetMaker.KILL, def.kill)
            assertEquals(PetMaker.FALL_SOFT, def.fallSoft)
            assertEquals(PetMaker.FALL_HARD, def.fallHard)
        }
    }

    @Test
    fun vaALaComida() {
        for ((kind, def) in kinds) for (left in listOf(true, false)) {
            val w = World(seed = 5)
            val b = w.add(def)
            w.run(5_000)
            b.target = Target(if (left) 20.0 else w.area.right - 20.0, w.area.bottom.toDouble())
            var got = false
            w.run(w.now + 120_000) { p -> if (p === b && !got) { if (p.takeArrived()) got = true else p.target = b.target ?: Target(if (left) 20.0 else w.area.right - 20.0, w.area.bottom.toDouble()) } }
            assertTrue("$kind no llegó a la comida", got)
        }
    }

    @Test
    fun seGuardaYSeLeeIgual() {
        for ((kind, def) in kinds) {
            val again = PetDef.parse(PetXml.write(def).byteInputStream())
            assertEquals(kind, def.animations.keys, again.animations.keys)
            for ((id, a) in def.animations) {
                val b = again.animations.getValue(id)
                assertEquals("$kind ${a.name}", a.frames, b.frames)
                assertEquals(a.onEnd.map { it.id to it.probability }, b.onEnd.map { it.id to it.probability })
                assertEquals(a.action, b.action)
                assertEquals(a.end.opacity, b.end.opacity, 0.001)
            }
            assertEquals(def.spawns.size, again.spawns.size)
            assertEquals("Mi dibujo".takeIf { kind == "un dibujo" } ?: def.header.petName, again.header.petName)
        }
    }

    @Test
    fun posesFueraDeLaHojaNoValen() {
        val bad = runCatching { PetMaker.build("x", "", 2, 1, "", PetMaker.SINGLE) }
        assertTrue(bad.isFailure)
    }
}
