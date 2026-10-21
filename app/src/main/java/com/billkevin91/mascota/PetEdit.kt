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
