package com.billkevin91.mascota

import org.junit.Assert.assertTrue
import org.junit.Test

class MovementTest {
    /** Una hora con cada mascota, arrastrándola y lanzándola a veces: nada se rompe ni se pierde. */
    @Test fun unaHoraConCadaMascota() {
        for (name in Pets.all) {
            val def = Pets.def(name)
            val w = World(seed = 3)
            val pet = w.add(def)
            var grabs = 0
            w.run(3_600_000) { b ->
                assertTrue("$name: dibujo ${b.frame} fuera de la hoja", b.frame in 0 until def.tilesX * def.tilesY)
                assertTrue("$name: posición inválida", !b.x.isNaN() && !b.y.isNaN())
                assertTrue("$name: opacidad ${b.opacity}", b.opacity in -0.001..1.001)
                if (b === pet && !b.tossing && w.rnd.nextInt(2500) == 0) {
                    b.grab(); b.tick()
                    b.x = w.rnd.nextInt(300).toDouble(); b.y = w.rnd.nextInt(600).toDouble()
                    b.release(w.rnd.nextDouble(-30.0, 30.0), w.rnd.nextDouble(-30.0, 30.0))
                    grabs++
                }
            }
            assertTrue("$name: la mascota desapareció sola", !pet.finished)
            assertTrue("$name: no se probó arrastrar", grabs > 0)
            pet.kill()
            repeat(500) { if (!pet.finished) pet.tick() }
            assertTrue("$name: no se despide", pet.finished)
        }
    }

    /** La oveja se mete en la tina: eSheep (cae desde un lado) y gSheep (clavado), en vertical y horizontal. */
    @Test fun seMeteEnLaTina() {
        for ((name, start) in listOf("esheep64" to "batha", "blue_sheep" to "divea", "pink_sheep" to "divea")) {
            val full = Pets.def(name)
            var ok = 0; var total = 0
            for (area in listOf(portrait(), landscape())) for (left in listOf(true, false)) for (seed in 1..10) {
                val w = World(area, seed)
                val pet = w.add(full, start = false)
                pet.movingLeft = left
                pet.play(Pets.id(full, start), loop = false)
                var result = false; var done = false
                w.run(60_000) { b ->
                    if (!done && b === pet && b.animationName == "bathc") {
                        val tub = w.pets.firstOrNull { it !== pet && w.parent[it] === pet && !it.finished && it.animationName.startsWith("bath") }
                        result = tub != null && Math.abs(tub.x - pet.x) <= 12
                        done = true
                    }
                }
                total++; if (result) ok++
            }
            assertTrue("$name: se mete en la tina $ok de $total", ok >= total * 9 / 10)
        }
    }

    /** gSheep: el salto a la tina del suelo acaba en baño completo (no desaparece). */
    @Test fun gSheepSeBanaEnElSuelo() {
        val def = Pets.def("pink_sheep")
        val w = World(seed = 2)
        val pet = w.add(def, start = false)
        pet.play(Pets.id(def, "bath_start"), loop = false)
        val seen = LinkedHashSet<String>()
        w.run(60_000) { if (it === pet) seen.add(it.animationName) }
        assertTrue("secuencia: $seen", seen.containsAll(listOf("bath_start", "bath_startC", "bath2a", "bath2b")))
    }

    /** Dos eSheep que caminan de frente chocan y reaccionan (no se atraviesan). */
    @Test fun dosOvejasChocan() {
        val def = Pets.def("esheep64")
        val walk = Pets.id(def, "walk")
        var reacted = 0
        for (seed in 1..10) {
            val w = World(seed = seed)
            val a = w.add(def, start = false); val b = w.add(def, start = false)
            a.movingLeft = false; b.movingLeft = true
            a.play(walk, false); b.play(walk, false)
            a.x = 100.0; b.x = 220.0
            var hit = false
            w.run(8_000) { p ->
                if (p.animationName.startsWith("rotate") && Math.abs(a.x - b.x) < 60) hit = true
            }
            if (hit) reacted++
        }
        assertTrue("reaccionaron $reacted de 10", reacted >= 8)
    }

    /** Modo prueba: repetir cualquier movimiento de cualquier mascota no rompe nada y se ve en pantalla. */
    @Test fun repetirCadaMovimiento() {
        for (name in Pets.all) {
            val def = Pets.def(name)
            for (a in def.animations.values) {
                val w = World(seed = a.id)
                val pet = w.add(def)
                pet.speed = 0.5
                pet.play(a.id, loop = true)
                var out = 0L; var last = 0L
                // Cuenta como visible si se ve ella o alguna de sus crías (luckybsheepstart se esconde
                // a propósito mientras su cría, la oveja negra, entra caminando)
                w.run(60_000) { b ->
                    if (b === pet) {
                        val seen = w.visible(pet) || w.pets.any { it !== pet && !it.finished && w.visible(it) }
                        if (!seen) out += w.now - last
                        last = w.now
                    }
                }
                assertTrue("$name/${a.name}: desapareció", !pet.finished)
                assertTrue("$name/${a.name}: fuera de la pantalla ${out * 100 / 60_000}% del tiempo", out < 60_000 * 3 / 10)
                assertTrue("$name/${a.name}: crías acumuladas ${w.pets.size - 1}", w.pets.size - 1 <= 6)
            }
        }
    }
}
