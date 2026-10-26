package com.billkevin91.mascota

/**
 * Movimientos que sirven para jugar con la mascota (acariciarla, que vaya a la comida),
 * buscados por su nombre en el XML. Valen para las incluidas y para casi cualquier importada.
 */
class PetMoves(def: PetDef) {
    /** Caminar por el suelo (ovejas, carro, corazón): -1 si no tiene. */
    val walk: Int
    /** Correr por el suelo, para cuando la comida está lejos: -1 si no tiene. */
    val run: Int
    /**
     * Correr en 8 direcciones como Neko y los zorritos (run_left, run_ul...): clave l, r, u, d,
     * ul, ur, dl, dr. Vacío si la mascota camina por el suelo.
     */
    val directions: Map<String, Int>
    /** Movimientos alegres sin moverse del sitio (bailar, saludar, sacar la lengua...). */
    val happy: List<Int>
    /** Comer, sin moverse del sitio. */
    val eat: List<Int>
    /**
     * Movimientos de escenas con crías (el baño con su tina, la oveja negra...): no se interrumpen,
     * porque la cría sigue sola y se verían dos ovejas.
     */
    val withProps: Set<Int>

    init {
        val all = def.animations.values.sortedBy { it.id }
        fun named(n: String) = all.firstOrNull { it.name.trim().lowercase() == n }
        fun still(a: Animation) = listOf(a.start.x, a.end.x, a.start.y, a.end.y).all { it.constant == 0.0 }
        // Avanza de lado a paso fijo, sin subir ni bajar, y sigue en el suelo
        fun floorMove(a: Animation?) = a != null && a.hasGravity && a.id != def.kill &&
            a.start.x.constant.let { it != null && it != 0.0 } && a.start.x.constant == a.end.x.constant &&
            a.start.y.constant == 0.0 && a.end.y.constant == 0.0

        walk = (listOf(named("walk"), named("walk_task")) + all.filter { it.name.lowercase().startsWith("walk") })
            .firstOrNull { floorMove(it) }?.id ?: -1
        run = named("run")?.takeIf { floorMove(it) }?.id ?: -1

        val dirNames = mapOf(
            "l" to "run_left", "r" to "run_right", "u" to "run_up", "d" to "run_down",
            "ul" to "run_ul", "ur" to "run_ur", "dl" to "run_dl", "dr" to "run_dr",
        )
        val dirs = dirNames.mapNotNull { (k, n) -> named(n)?.let { k to it.id } }.toMap()
        directions = if (walk < 0 && "l" in dirs && "r" in dirs) dirs else emptyMap()

        // Sin crías (la tina, la oveja negra...) para que no salga nada raro al acariciarla
        fun fits(a: Animation) = still(a) && a.id != def.kill && def.children[a.id].isNullOrEmpty()
        val words = listOf(
            "happy", "love", "joy", "dance", "wave", "salute", "music", "cheer", "smile", "laugh",
            "tongue_flick", "standblink", "honk", "lights", "scratcha",
        )
        happy = all.filter { a -> fits(a) && words.any { a.name.lowercase().startsWith(it) } }.map { it.id }
        eat = all.filter { fits(it) && it.name.lowercase().startsWith("eat") }.map { it.id }
        withProps = all.map { it.id }.filter { id ->
            def.scene(def.sceneStart(id)).any { !def.children[it].isNullOrEmpty() }
        }.toSet()
    }
}
