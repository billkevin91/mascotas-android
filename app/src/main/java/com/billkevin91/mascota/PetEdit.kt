package com.billkevin91.mascota

/** Cambios del editor sobre una mascota: cada función devuelve una mascota nueva (la original no cambia). */
object PetEdit {

    /** Lo que el editor deja cambiar de un movimiento. Los textos admiten expresiones (random, screenW...). */
    class Fields(
        val frames: List<Int>,
        val startInterval: String,
        val endInterval: String,
        val startX: String,
        val endX: String,
        val startY: String,
        val endY: String,
        val repeat: String,
        val repeatFrom: Int,
    )

    fun fieldsOf(a: Animation) = Fields(
        frames = a.frames,
        startInterval = a.start.interval.text,
        endInterval = a.end.interval.text,
        startX = a.start.x.text,
        endX = a.end.x.text,
        startY = a.start.y.text,
        endY = a.end.y.text,
        repeat = a.repeat.text,
        repeatFrom = a.repeatFrom,
    )

    /** Comprueba los campos; devuelve un mensaje para el usuario si algo no vale. */
    fun check(def: PetDef, f: Fields): String? {
        if (f.frames.isEmpty()) return "El movimiento necesita al menos un dibujo"
        val max = def.tilesX * def.tilesY
        f.frames.firstOrNull { it !in 0 until max }?.let { return "El dibujo $it no existe (hay de 0 a ${max - 1})" }
        for ((label, text) in listOf("Velocidad al empezar" to f.startInterval, "Velocidad al terminar" to f.endInterval)) {
            val ms = text.trim().toDoubleOrNull() ?: return "$label: escribe un número de milisegundos"
            if (ms < 10 || ms > 10_000) return "$label: entre 10 y 10000 ms"
        }
        val vars: (String) -> Double? = { 1.0 }
        for ((label, text) in listOf(
            "Avance horizontal" to f.startX, "Avance horizontal" to f.endX,
            "Avance vertical" to f.startY, "Avance vertical" to f.endY, "Repeticiones" to f.repeat,
        )) {
            try {
                Expr.eval(text.trim().ifEmpty { "0" }, vars)
            } catch (e: IllegalArgumentException) {
                return "$label: \"$text\" no es un número ni una expresión válida"
            }
        }
        if (f.repeatFrom !in 0 until f.frames.size) return "\"Repetir desde\" tiene que ser un dibujo de la lista (0 a ${f.frames.size - 1})"
        return null
    }

    fun withFields(def: PetDef, id: Int, f: Fields): PetDef {
        val a = def.animations.getValue(id)
        fun mv(m: Movement, x: String, y: String, iv: String) =
            Movement(Value(x.ifBlank { "0" }), Value(y.ifBlank { "0" }), m.offsetY, m.opacity, Value(iv))
        val changed = Animation(
            id = a.id,
            name = a.name,
            start = mv(a.start, f.startX, f.startY, f.startInterval),
            end = mv(a.end, f.endX, f.endY, f.endInterval),
            frames = f.frames,
            repeat = Value(f.repeat.ifBlank { "0" }),
            repeatFrom = f.repeatFrom,
            action = a.action,
            onEnd = a.onEnd,
            onBorder = a.onBorder,
            onGravity = a.onGravity,
        )
        return copy(def, animations = def.animations + (id to changed))
    }

    // ---- Opciones avanzadas ----

    /**
     * Lo avanzado de un movimiento: a cuáles puede pasar (al terminar, al chocar con un borde,
     * al quedarse sin suelo) y con qué probabilidad, sus crías y su sonido.
     */
    class Advanced(
        val onEnd: List<Next>,
        val onBorder: List<Next>,
        val onGravity: List<Next>,
        val children: List<ChildDef>,
        /** El sonido elegido (null = sin sonido). Sólo se usa si [soundChanged]. */
        val sound: Sound?,
        val soundChanged: Boolean,
    )

    fun advancedOf(def: PetDef, id: Int): Advanced {
        val a = def.animations.getValue(id)
        return Advanced(a.onEnd, a.onBorder, a.onGravity, def.children[id].orEmpty(),
            def.sounds.firstOrNull { it.animationId == id }, soundChanged = false)
    }

    /** Tamaño máximo de un sonido (en base64): ~1,5 MB de mp3. */
    const val MAX_SOUND = 2_000_000

    fun checkAdvanced(def: PetDef, adv: Advanced): String? {
        for ((label, list) in listOf("Al terminar" to adv.onEnd, "Al chocar" to adv.onBorder, "Sin suelo" to adv.onGravity)) {
            for (n in list) {
                if (n.id !in def.animations) return "$label: el movimiento ${n.id} no existe"
                if (n.probability !in 0..1000) return "$label: la probabilidad tiene que ir de 0 a 1000"
            }
        }
        if (adv.onEnd.isNotEmpty() && adv.onEnd.all { it.probability == 0 }) return "Al terminar: alguna probabilidad tiene que ser mayor que 0"
        val vars: (String) -> Double? = { 1.0 }
        adv.children.forEachIndexed { i, c ->
            for (v in listOf(c.x, c.y)) {
                try { Expr.eval(v.text, vars) } catch (e: IllegalArgumentException) {
                    return "Cría ${i + 1}: \"${v.text}\" no es un número ni una expresión válida"
                }
            }
            if (c.next !in def.animations) return "Cría ${i + 1}: el movimiento ${c.next} no existe"
        }
        adv.sound?.let {
            if (it.probability !in 0..100) return "Sonido: la probabilidad va de 0 a 100"
            if (it.loop !in 0..20) return "Sonido: las repeticiones van de 0 a 20"
            if (it.base64.length > MAX_SOUND) return "Sonido: es demasiado largo (máximo ~1,5 MB)"
            if (it.base64.isBlank()) return "Sonido: el archivo está vacío"
        }
        return null
    }

    fun withAdvanced(def: PetDef, id: Int, adv: Advanced): PetDef {
        val a = def.animations.getValue(id)
        val changed = Animation(a.id, a.name, a.start, a.end, a.frames, a.repeat, a.repeatFrom, a.action,
            adv.onEnd, adv.onBorder, adv.onGravity)
        val children = def.children.toMutableMap()
        if (adv.children.isEmpty()) children.remove(id) else children[id] = adv.children
        val sounds = if (!adv.soundChanged) def.sounds
        else def.sounds.filter { it.animationId != id } + listOfNotNull(adv.sound?.let { Sound(id, it.probability, it.loop, it.base64) })
        return PetDef(def.title, def.tilesX, def.tilesY, def.png, def.transparency, def.animations + (id to changed),
            def.spawns, children, def.header, sounds)
    }

    /** Autor y descripción de la mascota. */
    fun withInfo(def: PetDef, author: String, info: String): PetDef {
        val h = def.header
        return copy(def, header = Header(author, h.title, h.petName, h.version, info, h.application, h.icon))
    }

    fun renamed(def: PetDef, name: String): PetDef {
        val h = def.header
        val header = Header(h.author, name, name, h.version, h.info, h.application, h.icon)
        return copy(def, title = name, header = header)
    }

    private fun copy(
        def: PetDef,
        title: String = def.title,
        animations: Map<Int, Animation> = def.animations,
        header: Header = def.header,
    ) = PetDef(title, def.tilesX, def.tilesY, def.png, def.transparency, animations, def.spawns, def.children, header, def.sounds)
}
