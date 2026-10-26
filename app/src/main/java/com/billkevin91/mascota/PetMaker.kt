package com.billkevin91.mascota

/**
 * Crea una mascota nueva a partir de tu dibujo: con los dibujos (poses) de la hoja arma los
 * movimientos básicos para que camine, corra, se alegre, duerma, caiga, se deje arrastrar y se
 * despida desde el primer momento. Luego se pueden ajustar en el editor.
 */
object PetMaker {

    /** Qué dibujo de la hoja (número de casilla) usar para cada pose. */
    class Poses(
        val walk: List<Int>,
        val stand: Int,
        val step: Int,
        val hop: List<Int>,
        val dangle: Int,
        val land: Int,
        val sleep: List<Int>,
    )

    /**
     * Las 11 poses que la app dibuja a partir de un solo dibujo, en este orden en la hoja:
     * 0 de pie, 1 paso (aplastada), 2 inclinada atrás, 3 inclinada adelante, 4 saltito,
     * 5 colgando, 6 aterrizando, 7 tumbada, 8 tumbada "z", 9 tumbada "zz", 10 salto alto.
     */
    const val SINGLE_TILES = 11
    val SINGLE = Poses(
        walk = listOf(0, 2, 0, 3),
        stand = 0,
        step = 1,
        hop = listOf(4, 0, 10, 0),
        dangle = 5,
        land = 6,
        sleep = listOf(7, 8, 9, 8),
    )

    /** Hoja con varios dibujos: la primera fila es caminar; el primero, de pie; el último, dormir. */
    fun gridPoses(tilesX: Int, tilesY: Int): Poses {
        val n = tilesX * tilesY
        val walk = (0 until tilesX).toList().ifEmpty { listOf(0) }
        return Poses(
            walk = walk,
            stand = 0,
            step = walk.getOrElse(1) { 0 },
            hop = listOf(0),
            dangle = if (n > 1) 1 else 0,
            land = 0,
            sleep = listOf(n - 1),
        )
    }

    // Números de los movimientos (los nombres son los que reconocen la app y desktopPet)
    const val WALK = 1
    const val TURN = 2
    const val IDLE = 3
    const val HAPPY = 4
    const val RUN = 5
    const val SLEEP = 6
    const val DRAG = 7
    const val FALL = 8
    const val FALL_SOFT = 9
    const val FALL_HARD = 10
    const val KILL = 11

    fun build(name: String, author: String, tilesX: Int, tilesY: Int, png: String, poses: Poses): PetDef {
        val n = tilesX * tilesY
        require(n > 0) { "La hoja no tiene dibujos" }
        val all = with(poses) { walk + hop + sleep + listOf(stand, step, dangle, land) }
        require(all.all { it in 0 until n }) { "Hay poses fuera de la hoja" }

        fun next(vararg p: Pair<Int, Int>) = p.map { Next(it.first, it.second, Only.NONE) }
        val falls = next(FALL to 100)
        fun anim(
            id: Int, animName: String, frames: List<Int>, ms: Int, x: Int = 0, repeat: String = "0",
            repeatFrom: Int = 0, onEnd: List<Next>, onBorder: List<Next> = emptyList(),
            onGravity: List<Next> = falls, action: String = "", endMs: Int = ms,
            y: Pair<Int, Int> = 0 to 0, opacity: Pair<Double, Double> = 1.0 to 1.0,
        ) = Animation(
            id = id,
            name = animName,
            start = Movement(Value("$x"), Value("${y.first}"), 0, opacity.first, Value("$ms")),
            end = Movement(Value("$x"), Value("${y.second}"), 0, opacity.second, Value("$endMs")),
            frames = frames,
            repeat = Value(repeat),
            repeatFrom = repeatFrom,
            action = action,
            onEnd = onEnd,
            onBorder = onBorder,
            onGravity = onGravity,
        )

        val turn = next(TURN to 100)
        val animations = listOf(
            anim(WALK, "walk", poses.walk, 160, x = -2, repeat = "4+random/10", onBorder = turn,
                onEnd = next(WALK to 55, IDLE to 15, HAPPY to 8, SLEEP to 6, RUN to 8, TURN to 8)),
            anim(TURN, "turn", listOf(poses.stand, poses.step), 120, action = "flip", onEnd = next(WALK to 100)),
            anim(IDLE, "idle", listOf(poses.stand, poses.step, poses.stand), 400, repeat = "2+random/25",
                onEnd = next(WALK to 50, IDLE to 15, HAPPY to 15, SLEEP to 15, TURN to 5)),
            anim(HAPPY, "happy", poses.hop, 130, repeat = "1", onEnd = next(WALK to 70, IDLE to 30)),
            anim(RUN, "run", poses.walk, 90, x = -6, repeat = "6+random/10", onBorder = turn, onEnd = next(WALK to 100)),
            anim(SLEEP, "sleep", poses.sleep, 600, repeat = "6+random/8", repeatFrom = if (poses.sleep.size > 1) 1 else 0,
                onEnd = next(IDLE to 100)),
            anim(DRAG, "drag", listOf(poses.dangle, poses.dangle, poses.step, poses.dangle), 100,
                onEnd = next(FALL to 100), onGravity = emptyList()),
            anim(FALL, "fall", listOf(poses.dangle), 80, endMs = 40, y = 2 to 10, repeat = "30",
                onEnd = next(FALL to 100), onBorder = next(FALL_SOFT to 100), onGravity = emptyList()),
            anim(FALL_SOFT, "fall soft", listOf(poses.land, poses.stand), 150, onEnd = next(IDLE to 100)),
            anim(FALL_HARD, "fall hard", listOf(poses.land, poses.land, poses.step, poses.stand), 150, onEnd = next(IDLE to 100)),
            anim(KILL, "kill", listOf(poses.stand), 80, repeat = "10", opacity = 1.0 to 0.0,
                onEnd = next(WALK to 100), onGravity = emptyList()),
        ).associateBy { it.id }

        val spawns = listOf(
            // Entra caminando por un lado...
            Spawn(40, Value("screenW+10"), Value("areaH-imageH"), WALK),
            // ... o cae desde arriba
            Spawn(60, Value("random*(screenW-imageW-50)/100+25"), Value("-imageH-20"), WALK),
        )
        val header = Header(author = author, title = name, petName = name, version = "1.0",
            info = "Creada con Mascotas en pantalla a partir de un dibujo.")
        return PetDef(name, tilesX, tilesY, png, "", animations, spawns, emptyMap(), header)
    }
}
