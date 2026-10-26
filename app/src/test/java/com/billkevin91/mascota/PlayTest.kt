package com.billkevin91.mascota

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Jugar con las mascotas: caricias y comida (las incluidas y las de extras/). */
class PlayTest {
    private val extras = File("../extras").listFiles().orEmpty().map { File(it, "animations.xml") }.filter { it.isFile }
    private val pets: List<Pair<String, PetDef>> =
        Pets.all.map { it to Pets.def(it) } + extras.map { f -> f.parentFile.name to f.inputStream().use { PetDef.parse(it) } }

    @Test
    fun cadaMascotaSabeJugar() {
        for ((name, def) in pets) {
            val m = PetMoves(def)
            val goes = m.walk >= 0 || m.directions.isNotEmpty()
            assertTrue("$name no sabe ir a la comida", goes)
            // Lo que hace al acariciarla o al comer no la mueve del sitio
            for (id in m.happy + m.eat) {
                val a = def.animations.getValue(id)
                assertTrue("$name: ${a.name} se mueve", listOf(a.start.x, a.end.x, a.start.y, a.end.y).all { it.constant == 0.0 })
            }
        }
        // Las que tienen movimientos alegres de verdad
        for (n in listOf("blue_sheep", "pink_sheep", "neko", "fox", "carro_rojo", "corazon")) {
            assertTrue("$n sin movimientos alegres", PetMoves(pets.first { it.first == n }.second).happy.isNotEmpty())
        }
    }

    /** Como el servicio: sólo va a la comida si no tiene crías (la tina...) en pantalla. */
    /** Devuelve cuánto tardó y dónde quedó su centro al llegar, o null si no llegó. */
    private fun feed(w: World, b: PetBrain, food: Target, limitMs: Long): Pair<Long, Double>? {
        val start = w.now
        var got: Pair<Long, Double>? = null
        b.target = food
        w.run(start + limitMs) { p ->
            if (p !== b || got != null) return@run
            if (p.takeArrived()) { got = (w.now - start) to (p.x + p.frameW / 2.0); return@run }
            p.target = if (w.pets.any { w.parent[it] === b }) null else food
        }
        b.target = null
        return got
    }

    @Test
    fun vanALaComidaYSeLaComen() {
        val slow = ArrayList<String>()
        val times = LinkedHashMap<String, ArrayList<Long>>()
        for ((name, def) in pets) for (seed in 1..6) for (left in listOf(true, false)) {
            val w = World(seed = seed)
            val b = w.add(def)
            w.run(3_000)
            val food = Target(if (left) 30.0 else w.area.right - 30.0, w.area.bottom.toDouble())
            val got = feed(w, b, food, 120_000)
            if (got == null) { slow.add("$name semilla $seed ${if (left) "izq" else "der"}"); continue }
            times.getOrPut(name) { ArrayList() }.add(got.first)
            // Llegó de verdad: su centro está junto a la comida (o tan cerca como deja el borde)
            val cx = got.second
            val tx = food.x.coerceIn(w.area.left + b.frameW / 2.0, w.area.right - b.frameW / 2.0)
            assertTrue("$name lejos de la comida: $cx vs $tx", Math.abs(cx - tx) <= maxOf(b.frameW / 4.0, 12.0) + 12)
        }
        for ((n, t) in times) println("comida: $n tarda ${t.min() / 1000}-${t.max() / 1000} s (media ${t.average().toLong() / 1000})")
        assertTrue("No llegaron a la comida en 2 minutos: $slow", slow.isEmpty())
    }

    /** Acariciarla o la comida en medio del baño no la sacan de la tina (se verían dos ovejas). */
    @Test
    fun nadaInterrumpeElBano() {
        val starts = listOf("esheep64" to "batha", "blue_sheep" to "divea", "pink_sheep" to "bath_start")
        for ((name, start) in starts) for (seed in 1..20) for (feed in listOf(false, true)) {
            val def = Pets.def(name)
            val w = World(seed = seed)
            val b = w.add(def)
            w.run(2_000)
            b.play(Pets.id(def, start), loop = false)
            val rnd = kotlin.random.Random(seed)
            val food = Target(rnd.nextDouble(30.0, 330.0), w.area.bottom.toDouble())
            w.run(w.now + 40_000) { p ->
                if (p !== b) return@run
                if (rnd.nextInt(6) == 0) p.caress()
                if (feed) p.target = food
                p.takeArrived()
                val tub = w.pets.filter { w.parent[it] === b && !it.finished && "bath" in it.animationName }
                val mine = p.animationName
                assertTrue("$name semilla $seed: salió del baño ($mine) con ${tub.map { it.animationName }} en pantalla",
                    tub.isEmpty() || "bath" in mine || "dive" in mine)
            }
        }
    }

    @Test
    fun noLaInterrumpeEnElAire() {
        // Saltando o cayendo no se puede desviar hacia la comida
        val def = Pets.def("esheep64")
        val w = World(seed = 3)
        val b = w.add(def)
        b.play(Pets.id(def, "jump"), loop = false)
        b.target = Target(30.0, w.area.bottom.toDouble())
        b.tick()
        assertEquals("jump", b.animationName)
    }

    @Test
    fun caricias() {
        for ((name, def) in pets) for (seed in 1..4) {
            val w = World(seed = seed)
            val b = w.add(def)
            val seen = HashSet<String>()
            var happy = 0
            var next = 2_000L
            w.run(150_000) { p ->
                if (p !== b) return@run
                seen.add(p.animationName)
                if (w.now >= next) {
                    next = w.now + 7_000
                    val before = p.animationId
                    assertTrue("$name no se deja acariciar (${p.animationName})", p.caress() || p.isDying || p.tossing)
                    if (p.animationId != before && p.animationId in p.moves.happy) happy++
                }
            }
            assertTrue("$name se quedó congelada: $seen", seen.size >= 3)
            assertTrue("$name desapareció al acariciarla", !b.finished)
            if (b.moves.happy.isNotEmpty() && name != "esheep64") assertTrue("$name nunca se alegró", happy > 0)
        }
    }

    @Test
    fun noSeAcariciaNiComeEnModoPrueba() {
        val def = Pets.def("blue_sheep")
        val w = World(seed = 1)
        val b = w.add(def)
        b.play(Pets.id(def, "sneeze"), loop = true)
        assertEquals(false, b.caress())
        b.target = Target(10.0, w.area.bottom.toDouble())
        w.run(20_000) { p -> if (p === b) assertEquals(false, p.takeArrived()) }
    }
}
