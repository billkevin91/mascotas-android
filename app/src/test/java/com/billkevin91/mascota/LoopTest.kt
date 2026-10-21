package com.billkevin91.mascota

import org.junit.Assert.assertTrue
import org.junit.Test

/** Modo prueba con "Repetir": se repite el movimiento elegido, sin que se cuelen otros. */
class LoopTest {
    /**
     * Mascota mínima como las de tipo Pikachu (pikachu, mareep, shiny_sylveon de desktopPet):
     * aparece en idle, "walk down" se desvía al azar hacia un lado y al chocar con un borde pasa a idle.
     * Antes, repitiendo walk down se veía idle, walk down, idle...
     */
    private val pikachuLike = """
        <animations>
          <header><title>Prueba</title></header>
          <image><tilesx>2</tilesx><tilesy>1</tilesy><png>AA==</png><transparency>Magenta</transparency></image>
          <spawns><spawn id="1" probability="100"><x>screenW-imageW</x><y>areaH-imageH</y><next probability="100">1</next></spawn></spawns>
          <animations>
            <animation id="1"><name>idle</name>
              <start><x>0</x><y>0</y><offsety>0</offsety><opacity>1</opacity><interval>200</interval></start>
              <end><x>0</x><y>0</y><offsety>0</offsety><opacity>1</opacity><interval>200</interval></end>
              <sequence repeatfrom="0" repeat="random/20+2"><frame>0</frame>
                <next probability="50" only="none">1</next><next probability="50" only="none">2</next></sequence>
            </animation>
            <animation id="2"><name>walk down</name>
              <start><x>0</x><y>6</y><offsety>0</offsety><opacity>1</opacity><interval>200</interval></start>
              <end><x>random/20-5</x><y>6</y><offsety>0</offsety><opacity>1</opacity><interval>200</interval></end>
              <sequence repeatfrom="0" repeat="5+random/10"><frame>0</frame><frame>1</frame>
                <next probability="50" only="none">2</next><next probability="50" only="none">1</next><action>flip</action></sequence>
              <border><next probability="10" only="none">1</next></border>
            </animation>
          </animations>
        </animations>
    """.trimIndent()

    /** Veces que la mascota pasa a un movimiento con el que también aparece (idle, walk...) fuera de la escena. */
    private fun intrusions(def: PetDef, id: Int, seed: Int): Int {
        val w = World(seed = seed)
        val pet = w.add(def)
        pet.play(id, loop = true)
        val scene = def.scene(def.sceneStart(id))
        val spawnNexts = def.spawns.map { it.next }.toSet()
        var count = 0
        var last = pet.animationId
        w.run(40_000) { b ->
            if (b === pet && b.animationId != last) {
                if (b.animationId !in scene && b.animationId in spawnNexts) count++
                last = b.animationId
            }
        }
        return count
    }

    @Test fun repetirWalkDownNoMeteIdle() {
        val def = PetDef.parse(pikachuLike.byteInputStream())
        for (seed in 1..10) {
            val n = intrusions(def, 2, seed)
            assertTrue("semilla $seed: idle se coló $n veces", n <= 1)
        }
    }

    @Test fun repetirEnLasIncluidasNoMeteOtros() {
        for (name in Pets.all) {
            val def = Pets.def(name)
            for (a in def.animations.values) {
                val n = intrusions(def, a.id, a.id)
                assertTrue("$name/${a.name}: se coló otro movimiento $n veces", n <= 2)
            }
        }
    }
}
