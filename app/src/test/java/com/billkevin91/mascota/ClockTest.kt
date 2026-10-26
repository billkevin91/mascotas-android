package com.billkevin91.mascota

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Según la hora: de noche duermen más, por la mañana menos (y nada se rompe). */
class ClockTest {
    private val extras = File("../extras").listFiles().orEmpty().map { File(it, "animations.xml") }.filter { it.isFile }
    private val pets: List<Pair<String, PetDef>> =
        Pets.all.map { it to Pets.def(it) } + extras.map { f -> f.parentFile.name to f.inputStream().use { PetDef.parse(it) } }

    /** Parte del tiempo que pasa durmiendo en [minutes] minutos a la hora [hour] (null = sin reloj). */
    private fun sleeping(def: PetDef, hour: Int?, seeds: IntRange = 1..6, minutes: Long = 30): Double {
        var asleep = 0L
        var total = 0L
        for (seed in seeds) {
            val w = World(seed = seed)
            val b = w.add(def, start = false)
            if (hour != null) { b.followClock = true; b.hourOfDay = { hour } }
            b.start()
            var last = 0L
            var name = b.animationName
            w.run(minutes * 60_000) { p ->
                if (p !== b) return@run
                val dt = w.now - last
                if ("sleep" in name.lowercase()) asleep += dt
                total += dt
                last = w.now
                name = p.animationName
            }
        }
        return asleep.toDouble() / total
    }

    @Test
    fun deNocheDuermenMasYPorLaMananaMenos() {
        val report = StringBuilder()
        for ((name, def) in pets) {
            if (def.animations.values.none { "sleep" in it.name.lowercase() }) continue
            val day = sleeping(def, 14)
            val night = sleeping(def, 23)
            val morning = sleeping(def, 8)
            report.append("$name: mañana ${pct(morning)}, día ${pct(day)}, noche ${pct(night)}\n")
            assertTrue("$name no duerme más de noche: $report", night > day * 1.3 || night > 0.85)
            assertTrue("$name no duerme menos por la mañana: $report", morning < day || day < 0.02)
            // De noche también se despiertan a ratos (no se quedan dormidas para siempre)
            assertTrue("$name sólo duerme de noche: $report", night < 0.97)
        }
        println(report)
    }

    private fun pct(d: Double) = "${(d * 100).toInt()} %"

    @Test
    fun sinRelojEsIgualQueAntes() {
        // Con el reloj apagado (o de día) elige exactamente lo mismo que desktopPet
        for ((name, def) in pets.take(3)) {
            fun trace(clock: Boolean): List<String> {
                val w = World(seed = 7)
                val b = w.add(def, start = false)
                if (clock) { b.followClock = true; b.hourOfDay = { 15 } }
                b.start()
                val seen = ArrayList<String>()
                w.run(10 * 60_000) { p -> if (p === b) seen.add(p.animationName) }
                return seen
            }
            assertEquals("$name cambió de día", trace(false), trace(true))
        }
    }

    @Test
    fun deNocheTambienVanALaComida() {
        for ((name, def) in pets) {
            val w = World(seed = 2)
            val b = w.add(def, start = false)
            b.followClock = true
            b.hourOfDay = { 2 }
            b.start()
            w.run(5 * 60_000)
            val food = Target(30.0, w.area.bottom.toDouble())
            var got = false
            w.run(w.now + 180_000) { p ->
                if (p !== b || got) return@run
                if (p.takeArrived()) { got = true; return@run }
                p.target = if (w.pets.any { w.parent[it] === b }) null else food
            }
            assertTrue("$name no fue a la comida de noche", got)
        }
    }
}
