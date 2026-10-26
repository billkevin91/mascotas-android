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
        if (f.frames.isEmpty()) return tr("El movimiento necesita al menos un dibujo", "The movement needs at least one drawing")
        val max = def.tilesX * def.tilesY
        f.frames.firstOrNull { it !in 0 until max }?.let { return tr("El dibujo $it no existe (hay de 0 a ${max - 1})", "Drawing $it doesn't exist (there are 0 to ${max - 1})") }
        for ((label, text) in listOf(tr("Velocidad al empezar", "Speed at the start") to f.startInterval, tr("Velocidad al terminar", "Speed at the end") to f.endInterval)) {
            val ms = text.trim().toDoubleOrNull() ?: return tr("$label: escribe un número de milisegundos", "$label: type a number of milliseconds")
            if (ms < 10 || ms > 10_000) return tr("$label: entre 10 y 10000 ms", "$label: between 10 and 10000 ms")
        }
        val vars: (String) -> Double? = { 1.0 }
        for ((label, text) in listOf(
            tr("Avance horizontal", "Horizontal step") to f.startX, tr("Avance horizontal", "Horizontal step") to f.endX,
            tr("Avance vertical", "Vertical step") to f.startY, tr("Avance vertical", "Vertical step") to f.endY, tr("Repeticiones", "Repeats") to f.repeat,
        )) {
            try {
                Expr.eval(text.trim().ifEmpty { "0" }, vars)
            } catch (e: IllegalArgumentException) {
                return tr("$label: \"$text\" no es un número ni una expresión válida", "$label: \"$text\" is not a number or a valid expression")
            }
        }
        if (f.repeatFrom !in 0 until f.frames.size) return tr("\"Repetir desde\" tiene que ser un dibujo de la lista (0 a ${f.frames.size - 1})", "\"Repeat from\" must be a drawing in the list (0 to ${f.frames.size - 1})")
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
        for ((label, list) in listOf(tr("Al terminar", "When it ends") to adv.onEnd, tr("Al chocar", "When it hits") to adv.onBorder, tr("Sin suelo", "No floor") to adv.onGravity)) {
            for (n in list) {
                if (n.id !in def.animations) return tr("$label: el movimiento ${n.id} no existe", "$label: movement ${n.id} doesn't exist")
                if (n.probability !in 0..1000) return tr("$label: la probabilidad tiene que ir de 0 a 1000", "$label: the probability must be from 0 to 1000")
            }
        }
        if (adv.onEnd.isNotEmpty() && adv.onEnd.all { it.probability == 0 }) return tr("Al terminar: alguna probabilidad tiene que ser mayor que 0", "When it ends: some probability must be greater than 0")
        val vars: (String) -> Double? = { 1.0 }
        adv.children.forEachIndexed { i, c ->
            for (v in listOf(c.x, c.y)) {
                try { Expr.eval(v.text, vars) } catch (e: IllegalArgumentException) {
                    return tr("Cría ${i + 1}: \"${v.text}\" no es un número ni una expresión válida", "Offspring ${i + 1}: \"${v.text}\" is not a number or a valid expression")
                }
            }
            if (c.next !in def.animations) return tr("Cría ${i + 1}: el movimiento ${c.next} no existe", "Offspring ${i + 1}: movement ${c.next} doesn't exist")
        }
        adv.sound?.let {
            if (it.probability !in 0..100) return tr("Sonido: la probabilidad va de 0 a 100", "Sound: the probability goes from 0 to 100")
            if (it.loop !in 0..20) return tr("Sonido: las repeticiones van de 0 a 20", "Sound: repeats go from 0 to 20")
            if (it.base64.length > MAX_SOUND) return tr("Sonido: es demasiado largo (máximo ~1,5 MB)", "Sound: it's too long (max ~1.5 MB)")
            if (it.base64.isBlank()) return tr("Sonido: el archivo está vacío", "Sound: the file is empty")
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
