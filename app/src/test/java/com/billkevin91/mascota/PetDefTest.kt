package com.billkevin91.mascota

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PetDefTest {
    private val vars = mapOf("screenW" to 360.0, "screenH" to 780.0, "areaW" to 360.0, "areaH" to 740.0, "imageW" to 40.0,
        "imageH" to 40.0, "imageX" to 100.0, "imageY" to 600.0, "random" to 50.0, "randS" to 50.0, "scale" to 1.0, "bathH" to 320.0)

    @Test fun todasLasMascotasSeLeen() {
        for (name in Pets.all) {
            val def = Pets.def(name)
            assertTrue("$name sin movimientos", def.animations.isNotEmpty())
            assertTrue("$name sin dibujos", def.tilesX > 0 && def.tilesY > 0 && def.png.length > 100)
        }
    }

    @Test fun todasLasExpresionesSeCalculan() {
        for (name in Pets.all) {
            val def = Pets.def(name)
            val values = def.animations.values.flatMap { listOf(it.start.x, it.start.y, it.start.interval, it.end.x, it.end.y, it.end.interval, it.repeat) } +
                def.spawns.flatMap { listOf(it.x, it.y) } + def.children.values.flatten().flatMap { listOf(it.x, it.y) }
            for (v in values) {
                if (v.constant != null) continue
                val unknown = HashSet<String>()
                Expr.eval(v.text) { n -> vars[n] ?: run { unknown.add(n); null } }
                assertTrue("$name: variables desconocidas $unknown en '${v.text}'", unknown.isEmpty())
            }
        }
    }

    @Test fun losSaltosLlevanAMovimientosQueExisten() {
        for (name in Pets.all) {
            val def = Pets.def(name)
            for (a in def.animations.values) for (n in a.onEnd + a.onBorder + a.onGravity) {
                assertTrue("$name: ${a.name} lleva a ${n.id}, que no existe", n.id in def.animations)
            }
            for (s in def.spawns) assertTrue("$name: aparición lleva a ${s.next}", s.next in def.animations)
        }
    }

    @Test fun escenas() {
        val e = Pets.def("esheep64")
        for (part in listOf("batha", "bathb", "bathc", "bathd", "bathw", "bathz")) {
            assertEquals(part, "batha", e.animations[e.sceneStart(Pets.id(e, part))]!!.name)
        }
        assertEquals("walk", e.animations[e.sceneStart(Pets.id(e, "walk"))]!!.name)
        assertEquals("sleep1a", e.animations[e.sceneStart(Pets.id(e, "sleep1b"))]!!.name)
        assertTrue(e.spawnFor(Pets.id(e, "batha")) != null)
        assertEquals(null, e.spawnFor(Pets.id(e, "walk")))
        val g = Pets.def("pink_sheep")
        for (part in listOf("bath_start", "bath2a", "bath2b", "bath_wait", "bath_eyes")) {
            assertEquals(part, "bath_start", g.animations[g.sceneStart(Pets.id(g, part))]!!.name)
        }
    }
}
