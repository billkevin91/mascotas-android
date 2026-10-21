package com.billkevin91.mascota

import java.io.File
import kotlin.random.Random

/** Mascotas de assets/pets (las pruebas corren desde la carpeta app/). */
object Pets {
    val dir = File("src/main/assets/pets")
    private val cache = HashMap<String, PetDef>()
    val all: List<String> get() = dir.listFiles()!!.filter { it.isDirectory }.map { it.name }.sorted()
    fun def(name: String) = cache.getOrPut(name) { File(dir, "$name/animations.xml").inputStream().use { PetDef.parse(it) } }
    fun id(def: PetDef, name: String) = def.animations.values.first { it.name == name }.id
}

fun portrait() = Area().apply { screenW = 360; screenH = 780; right = 360; bottom = 740 }
fun landscape() = Area().apply { screenW = 780; screenH = 360; right = 780; bottom = 330 }

/**
 * Simula lo que hace PetService sin Android: cada mascota con su reloj, las crías
 * aparecen y se quitan igual (al repetir una escena o cuando les falta un suspiro).
 */
class World(val area: Area = portrait(), seed: Int = 1) {
    val rnd = Random(seed)
    val pets = ArrayList<PetBrain>()
    val parent = HashMap<PetBrain, PetBrain>()
    private val clocks = HashMap<PetBrain, Long>()
    var now = 0L
        private set

    fun add(def: PetDef, size: Int = 40, start: Boolean = true): PetBrain {
        val b = PetBrain(def, area, size, size, rnd = rnd)
        b.others = { pets }
        if (start) b.start()
        pets.add(b); clocks[b] = now
        return b
    }

    /** Avanza hasta [until] ms. [each] se llama tras cada paso de cualquier mascota. */
    fun run(until: Long, each: (PetBrain) -> Unit = {}) {
        while (true) {
            val (b, t) = clocks.minByOrNull { it.value }?.toPair() ?: return
            if (t > until) { now = until; return }
            now = t
            if (b.finished) { clocks.remove(b); pets.remove(b); continue }
            if (b.tossing) { b.advanceToss(16.0); clocks[b] = t + 16; each(b); continue }
            val anim = b.animationId
            val restarts = b.restarts
            val d = b.tick()
            val kids = pets.filter { parent[it] === b }
            if (b.restarts != restarts) kids.forEach { it.kill() }
            else if (b.animationId != anim) kids.filter { it.remainingMs() < 300 }.forEach { it.kill() }
            for (c in b.takeChildren()) {
                val cb = PetBrain(b.def, area, b.frameW, b.frameH, c, rnd)
                cb.others = { pets }
                cb.start()
                pets.add(cb); clocks[cb] = t + 1; parent[cb] = b
            }
            clocks[b] = t + d
            each(b)
        }
    }

    fun visible(b: PetBrain) = b.x + b.frameW > 0 && b.x < area.screenW && b.y + b.frameH > 0 && b.y < area.screenH
}
