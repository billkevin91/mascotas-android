// Basado en desktopPet (https://github.com/Adrianotiger/desktopPet)
// Copyright (c) 2016 Adriano Petrucci, licencia MIT (ver assets/pets/LICENSE.md y CREDITS.md).
// Choques entre mascotas y lanzamiento: Zecheriah (https://github.com/ZechsVariety).
package com.billkevin91.mascota

import kotlin.math.min
import kotlin.random.Random

/**
 * Medidas de la pantalla en píxeles de la mascota (píxeles reales / escala).
 * El "suelo" (la barra de tareas en Windows) es el borde de abajo, sobre la barra de navegación.
 */
class Area {
    var screenW = 360
    var screenH = 640
    var left = 0
    var top = 0
    var right = 360
    var bottom = 600
}

/** Una mascota hija que hay que crear (salpicaduras, flores, otra oveja...). */
class ChildRequest(
    val def: ChildDef,
    val parentX: Double,
    val parentY: Double,
    val parentFlipped: Boolean,
    val depth: Int,
    /** El mismo azar que la madre: la tina y la oveja calculan su encuentro con él. */
    val randS: Int,
)

/**
 * Movimiento y animaciones de una mascota. Es la lógica de FormPet.cs de desktopPet
 * sin ventanas de Windows: aquí sólo hay bordes de pantalla y suelo.
 */
class PetBrain(
    val def: PetDef,
    val area: Area,
    val frameW: Int,
    val frameH: Int,
    private val child: ChildRequest? = null,
    private val rnd: Random = Random.Default,
) {
    var x = 0.0
    var y = 0.0
    var offsetY = 0.0
    var opacity = 1.0
    var movingLeft = child?.let { !it.parentFlipped } ?: true
    var frame = 0
    var finished = false
    var dragging = false
    var tossing = false
    val isChild get() = child != null
    /** Píxeles reales por píxel de mascota; sirve para comparar mascotas de distinto tamaño. */
    var scale = 1
    /** Todas las mascotas en pantalla, para chocar entre ellas. */
    var others: () -> List<PetBrain> = { emptyList() }
    val isDying get() = current?.id == def.kill
    /** Nombre del movimiento actual en el XML (walk, sleep1a, batha...). */
    val animationName get() = current?.name.orEmpty()
    val animationId get() = current?.id ?: -1

    /** Modo prueba: al terminar cualquier movimiento vuelve a este (-1 = libre). */
    var loopId = -1
    /** Velocidad general: 0.5 = la mitad de rápido. */
    var speed = 1.0
    /** Sube cada vez que el modo prueba empieza de nuevo un movimiento (las crías anteriores sobran). */
    var restarts = 0
        private set

    /** Milisegundos que le quedan si está en su último movimiento (sin siguiente); si no, muchísimos. */
    fun remainingMs(): Long {
        val a = current ?: return 0
        if (a.onEnd.isNotEmpty()) return Long.MAX_VALUE
        return ((totalSteps - step).coerceAtLeast(0) * windowsTimer(interval) / speed.coerceAtLeast(0.1)).toLong()
    }

    private var current: Animation? = null
    private var step = 0
    private var interval = 200
    private var totalSteps = 1
    private var startX = 0; private var startY = 0; private var endX = 0; private var endY = 0
    private var startInterval = 200; private var endInterval = 200
    private var randS = child?.randS ?: 50
    private var killFade = 1.0
    private var tossVx = 0.0
    private var tossVy = 0.0
    private val pendingChildren = ArrayList<ChildRequest>()
    private val startedAnimations = ArrayList<Int>()
    private var clock = 0L
    private var lastTurn = -10_000L

    fun start() {
        if (child == null) spawn()
        else {
            x = childX(child).toDouble()
            y = value(child.def.y).toDouble()
            offsetY = 0.0
            setAnimation(child.def.next)
        }
    }

    /** Avanza un paso. Devuelve los milisegundos hasta el siguiente. */
    fun tick(): Int {
        if (finished) return 1000
        if (step < 0) step = 0
        sceneSteps++
        nextStep()
        step++
        val ms = windowsTimer(interval)
        clock += ms
        return (ms / speed).toInt().coerceAtLeast(10)
    }

    /** Movimientos que empezaron desde la última vez (para sus sonidos, como en desktopPet). */
    fun takeStarted(): List<Int> {
        if (startedAnimations.isEmpty()) return emptyList()
        val list = ArrayList(startedAnimations)
        startedAnimations.clear()
        return list
    }

    fun takeChildren(): List<ChildRequest> {
        if (pendingChildren.isEmpty()) return emptyList()
        val list = ArrayList(pendingChildren)
        pendingChildren.clear()
        return list
    }

    // ---- Tocar, arrastrar y lanzar ----

    fun grab() {
        // Repitiendo la despedida en el modo prueba: al tocarla vuelve a ser normal
        if (isDying && loopId > 0) { revive(); stopLoop() }
        if (isDying || isChild) return
        dragging = true
        tossing = false
        setAnimation(def.drag)
    }

    /** Suelta la mascota. (vx, vy) es la fuerza del lanzamiento en píxeles cada 30 ms. */
    fun release(vx: Double, vy: Double) {
        if (!dragging) return
        dragging = false
        if (Math.hypot(vx, vy) > 5) {
            if (def.toss > 0) setAnimation(def.toss)
            tossing = true
            tossVx = vx
            tossVy = vy
            interval = 30
        } else {
            setAnimation(def.fall)
        }
    }

    /** Vuelo después de lanzarla; se llama a menudo mientras [tossing] sea verdadero. */
    fun advanceToss(ms: Double) {
        if (!tossing) return
        val dt = ms.coerceIn(0.0, 50.0) / 30.0
        if (dt == 0.0) return
        var nextX = x + tossVx * dt
        val maxX = (area.right - frameW).toDouble()
        if (nextX < area.left || nextX > maxX) {
            val edge = if (nextX < area.left) area.left.toDouble() else maxX
            nextX = edge - (nextX - edge) * .5
            tossVx *= -.5
        }
        x = nextX.coerceIn(area.left.toDouble(), maxX.coerceAtLeast(area.left.toDouble()))
        val dy = tossVy * dt + .75 * dt * dt
        tossVy += 1.5 * dt
        val ground = (area.bottom - frameH).toDouble()
        val land = y + dy >= ground
        y = if (land) ground else y + dy
        offsetY = 0.0
        if (land) {
            if ((tossVx < 0 && !movingLeft) || (tossVx > 0 && movingLeft)) movingLeft = !movingLeft
            tossing = false
            setAnimation(if (tossVy < 40) def.fallSoft else def.fallHard)
        }
    }

    /** Modo prueba: hace ya este movimiento. */
    fun play(id: Int, loop: Boolean) {
        if (isChild || def.animations[id] == null) return
        if (isDying) {
            // Sólo se interrumpe una despedida del modo prueba (repitiendo "kill")
            if (loopId <= 0) return
            revive()
        }
        dragging = false
        tossing = false
        // Muchos movimientos son trozos de una escena (el baño): se prueba la escena entera
        val start = def.sceneStart(id)
        scene = def.scene(start)
        loopId = if (loop) start else -1
        startScene(start)
    }

    /** Movimientos de la escena que se está probando (al salir de ella, se repite). */
    private var scene: Set<Int> = emptySet()
    private var sawInside = false

    /** Empieza una escena de prueba donde se pueda ver. */
    private fun startScene(id: Int, recenter: Boolean = false) {
        val a = def.animations[id] ?: return
        restarts++
        sceneSteps = 0
        sawInside = false
        offsetY = 0.0
        val s = def.spawnFor(id)
        if (s != null) {
            // Empieza apareciendo: igual que cuando aparece sola (la tina depende de esto)
            randS = rnd.nextInt(10, 90)
            y = value(s.y).toDouble()
            val sx = value(s.x)
            x = (if (movingLeft) sx else area.screenW - sx - frameW).toDouble()
        } else {
            // Si chocó con un borde, desde el centro para que se vea el movimiento entero
            if (recenter) toCenter()
            placeFor(a)
        }
        setAnimation(id)
    }

    /** Pasos desde que empezó la escena de prueba (para no reiniciar sin parar). */
    private var sceneSteps = 0
    /** El último reinicio fue nada más empezar (pegada a un borde). */
    private var earlyRestart = false

    /**
     * Dónde empezar un movimiento de prueba para que tenga sentido: los de suelo en el suelo,
     * los de techo arriba, los de pared junto a la pared y los de caer desde arriba.
     */
    private fun placeFor(a: Animation) {
        if (isOutside(partly = true)) toCenter()
        val sy = a.start.y.constant ?: 0.0
        val ey = a.end.y.constant ?: 0.0
        val name = a.name.lowercase()
        val floor = (area.bottom - frameH).toDouble()
        // De pared sólo si no se mueve nada hacia los lados (una fórmula como random/20-5 sí se mueve)
        val wall = a.start.x.constant == 0.0 && a.end.x.constant == 0.0 && (sy != 0.0 || ey != 0.0) &&
            "fall" !in name && "jump" !in name
        when {
            "top" in name -> y = area.top.toDouble()
            wall -> {
                // Pegada a la pared hacia la que mira; si sube, desde abajo
                x = if (movingLeft) area.left.toDouble() else (area.right - frameW).toDouble()
                y = if (sy < 0 || ey < 0) floor else area.top + (area.bottom - area.top) / 4.0
            }
            sy > 0 || ey > 0 -> if (y >= floor - 2) y = area.top + (area.bottom - area.top) / 4.0
            else -> y = floor
        }
    }

    /** Modo prueba: deja de repetir. */
    fun stopLoop() {
        val testGoodbye = isDying && loopId > 0
        loopId = -1
        scene = emptySet()
        // Estaba repitiendo la despedida: que no desaparezca, vuelve a aparecer normal
        if (testGoodbye) { revive(); spawn() }
    }

    /** Deja de despedirse (sólo para el modo prueba). */
    private fun revive() {
        current = null
        killFade = 1.0
        opacity = 1.0
    }

    private fun isOutside(partly: Boolean): Boolean = if (partly) {
        x < area.left || x + frameW > area.right || y < area.top || y + frameH > area.bottom
    } else {
        x < -frameW || x > area.screenW || y < -frameH || y > area.screenH
    }

    private fun toCenter() {
        x = (area.left + area.right - frameW) / 2.0
        y = (area.bottom - frameH).toDouble()
        offsetY = 0.0
    }

    /** Despedirse: animación "kill" si existe, si no desaparece. */
    fun kill() {
        // Doble toque mientras repite la despedida en el modo prueba: se va de verdad
        if (isDying && loopId > 0) { finished = true; return }
        if (def.kill > 0 && !isChild) {
            dragging = false
            tossing = false
            setAnimation(def.kill)
        } else {
            finished = true
        }
    }

    // ---- Lógica de desktopPet ----

    private fun spawn() {
        step = 0
        randS = rnd.nextInt(10, 90)
        val s = randomSpawn()
        if (s == null) {
            x = (area.screenW - frameW) / 2.0
            y = (area.bottom - frameH).toDouble()
            setAnimation(def.animations.keys.minOrNull() ?: 1)
            return
        }
        y = value(s.y).toDouble()
        val sx = value(s.x)
        x = (if (movingLeft) sx else area.screenW - sx - frameW).toDouble()
        offsetY = 0.0
        setAnimation(s.next)
    }

    private fun randomSpawn(): Spawn? {
        if (def.spawns.isEmpty()) return null
        val total = def.spawns.sumOf { it.probability.coerceAtLeast(0) }
        if (total <= 0) return def.spawns.first()
        // Según su probabilidad (desktopPet usa >= y la primera sale de más, aunque valga 0)
        val r = rnd.nextInt(total)
        var sum = 0
        for (s in def.spawns) {
            sum += s.probability.coerceAtLeast(0)
            if (sum > r) return s
        }
        return def.spawns.first()
    }

    private fun setAnimation(id: Int) {
        if (current != null && current?.id == def.kill) return
        val a = def.animations[id]
        if (id < 0 || a == null) {
            if (isChild) finished = true else spawn()
            return
        }
        step = -1
        current = a
        startX = value(a.start.x); startY = value(a.start.y)
        endX = value(a.end.x); endY = value(a.end.y)
        startInterval = value(a.start.interval); endInterval = value(a.end.interval)
        val n = a.frames.size
        totalSteps = if (a.repeat.constant != null) {
            val rep = a.repeat.constant.toInt()
            if (a.repeatFrom > 0) n + (n - a.repeatFrom - 1) * rep else n + n * rep
        } else {
            n + (n - a.repeatFrom) * value(a.repeat)
        }
        if (!dragging && !tossing) offsetY = a.start.offsetY.toDouble()

        startedAnimations.add(id)
        val depth = child?.depth ?: 0
        if (depth < 5) {
            def.children[id]?.forEach {
                pendingChildren.add(ChildRequest(it, x, y, !movingLeft, depth + 1, randS))
            }
        }
        interval = startInterval
    }

    private fun pickNext(list: List<Next>, where: Only): Int {
        if (list.isEmpty()) return -1
        val horizontalPlus = where == Only.HORIZONTAL || where == Only.WINDOW || where == Only.TASKBAR
        val verticalPlus = where == Only.VERTICAL || where == Only.WINDOW_SIDE || where == Only.PET_SIDE
        val valid = list.filter {
            it.only == Only.NONE || it.only == where ||
                (horizontalPlus && it.only == Only.HORIZONTAL_PLUS) ||
                (verticalPlus && it.only == Only.VERTICAL_PLUS)
        }
        val total = valid.sumOf { it.probability }
        if (total <= 0) return -1
        val r = rnd.nextInt(1, total + 1)
        var sum = 0
        for (n in valid) {
            sum += n.probability
            if (sum >= r) return n.id
        }
        return -1
    }

    private fun border(where: Only) = pickNext(current?.onBorder.orEmpty(), where)

    private fun nextStep() {
        val a = current ?: run { spawn(); return }
        val total = totalSteps.coerceAtLeast(1)
        val frames = a.frames
        if (frames.isNotEmpty()) {
            var s = step
            if (!dragging && !tossing && a.id != def.kill) s = min(s, total - 1)
            s = s.coerceAtLeast(0)
            val n = frames.size
            frame = if (s < n) frames[s]
            else if (n - a.repeatFrom <= 0) frames.last()
            else frames[((s - n + a.repeatFrom) % (n - a.repeatFrom) + a.repeatFrom).coerceIn(0, n - 1)]
        }

        // Del principio al final del movimiento; pasado el final (arrastrando, despidiéndose) se queda en el final
        val k = step.coerceIn(0, total)
        if (!tossing) interval = startInterval + (endInterval - startInterval) * k / total
        opacity = a.start.opacity + (a.end.opacity - a.start.opacity) * k / total
        offsetY = (a.start.offsetY + (a.end.offsetY - a.start.offsetY) * k / total).toDouble()

        if (dragging || tossing) return

        if (loopId > 0) {
            if (!isOutside(partly = false)) sawInside = true
            else if (sawInside) {
                // Repitiendo una escena que la sacó de la pantalla: vuelve a empezar
                startScene(loopId)
                return
            }
        }

        var dx = startX.toDouble()
        var dy = startY.toDouble()
        if (total > 1) {
            dx += (endX - startX) * step / (total - 1.0)
            dy += (endY - startY) * step / (total - 1.0)
        }
        var newAnimation = false
        var next = -1
        var leaving = false
        if (!movingLeft) dx = -dx

        if (dx < 0) {
            if (x + dx < area.left) {
                val b = border(Only.VERTICAL)
                if (b >= 0) { x = area.left.toDouble(); dx = 0.0; next = b; newAnimation = true } else leaving = true
            } else {
                // Lado derecho de otra mascota
                val o = bumpInto(dx, dy)
                if (o != null) {
                    val b = interact(o)
                    if (b >= 0) { x = boxOf(o)[2]; dx = 0.0; next = b; newAnimation = true }
                    else if (canTurn()) { x = boxOf(o)[2]; dx = 0.0; movingLeft = !movingLeft; lastTurn = clock }
                }
            }
        } else if (dx > 0) {
            if (x + dx + frameW > area.right) {
                val b = border(Only.VERTICAL)
                if (b >= 0) { x = (area.right - frameW).toDouble(); dx = 0.0; next = b; newAnimation = true } else leaving = true
            } else {
                // Lado izquierdo de otra mascota
                val o = bumpInto(dx, dy)
                if (o != null) {
                    val b = interact(o)
                    if (b >= 0) { x = boxOf(o)[0] - frameW; dx = 0.0; next = b; newAnimation = true }
                    else if (canTurn()) { x = boxOf(o)[0] - frameW; dx = 0.0; movingLeft = !movingLeft; lastTurn = clock }
                }
            }
        }
        if (!newAnimation && !leaving) {
            if (dy > 0) {
                if (y + dy > area.bottom - frameH) {
                    val b = border(Only.TASKBAR)
                    if (b >= 0) { y = (area.bottom - frameH).toDouble(); offsetY = 0.0; dy = 0.0; next = b; newAnimation = true }
                }
            } else if (dy < 0) {
                if (y + dy < area.top) {
                    val b = border(Only.HORIZONTAL)
                    if (b >= 0) { y = area.top.toDouble(); dy = 0.0; next = b; newAnimation = true }
                }
            }
        }

        if (!newAnimation && step >= total) {
            if (a.action == "flip") movingLeft = !movingLeft
            val outside = isOutside(partly = false)
            val nextAnimation = when {
                outside -> -1
                else -> pickNext(a.onEnd, if (y + frameH + dy >= area.bottom - 2) Only.TASKBAR else Only.NONE)
            }
            if (a.id == def.kill) {
                killFade -= 0.1
                // Se sigue desvaneciendo desde donde la dejó el XML (sin volver a verse de golpe)
                opacity = minOf(opacity, killFade)
                if (killFade <= 0.1) {
                    if (loopId > 0) {
                        // Modo prueba repitiendo: la despedida se ve, pero la mascota vuelve
                        killFade = 1.0
                        opacity = 1.0
                        current = null
                        startScene(loopId)
                    } else {
                        finished = true
                    }
                }
                return
            } else if (loopId > 0 && nextAnimation !in scene) {
                // Terminó la escena que se está probando: otra vez desde el principio
                // (desde el centro si quedó pegada a la pared hacia la que mira)
                x += dx
                y += dy
                val nearWall = if (movingLeft) x - area.left < frameW / 2.0 else area.right - (x + frameW) < frameW / 2.0
                startScene(loopId, recenter = nearWall)
                return
            } else if (nextAnimation >= 0) {
                next = nextAnimation
                newAnimation = true
            } else {
                if (isChild) finished = true else spawn()
                return
            }
        } else if (!newAnimation && a.hasGravity) {
            if (y + dy < area.bottom - frameH) {
                if (y + dy + 3 >= area.bottom - frameH) {
                    dy = area.bottom - y - frameH
                } else {
                    next = pickNext(a.onGravity, Only.NONE)
                    newAnimation = true
                }
            }
        }

        x += dx
        y += dy
        if (newAnimation && loopId > 0 && next !in scene && (sceneSteps > 2 || !earlyRestart)) {
            // Repitiendo: un borde o el vacío la llevarían a otro movimiento (idle, caer...)
            // que no es el que se está probando; vuelve a empezar el elegido desde el centro.
            // Si vuelve a pasar nada más empezar, se deja seguir para no reiniciar sin parar.
            earlyRestart = sceneSteps <= 2
            startScene(loopId, recenter = true)
            return
        }
        if (newAnimation) setAnimation(next)
    }

    // ---- Choques entre mascotas (SetPetInteractionAnimations de desktopPet) ----

    /** Caja de otra mascota (izquierda, arriba, derecha, abajo) en mis píxeles. */
    private fun boxOf(o: PetBrain): DoubleArray {
        val f = o.scale.toDouble() / scale
        val left = o.x * f
        val top = (o.y + o.offsetY) * f
        return doubleArrayOf(left, top, left + o.frameW * f, top + o.frameH * f)
    }

    /**
     * Sin animación para el choque: se da la vuelta, pero sólo si su animación
     * reacciona a las paredes y no lo ha hecho hace poco (si no, la atraviesa,
     * como en desktopPet; así no queda rebotando entre dos mascotas).
     */
    private fun canTurn() = current?.onBorder?.isNotEmpty() == true && clock - lastTurn > 5000

    /** La mascota con la que choco en este paso, si la hay. */
    private fun bumpInto(dx: Double, dy: Double): PetBrain? {
        if (isChild) return null
        val nx = x + dx
        val ny = y + dy
        for (o in others()) {
            if (o === this || o.isChild || o.finished || o.dragging || o.tossing || o.isDying || o.current == null) continue
            val (l, t, r, b) = boxOf(o).toList()
            if (ny >= b || ny + frameH <= t) continue
            val hit = if (dx < 0) {
                // Mi borde izquierdo cruza su borde derecho y queda sitio para pasar
                nx <= r && nx >= r + dx && r < area.right - frameW
            } else {
                nx + frameW >= l && nx + frameW <= l + dx && l > area.left + frameW
            }
            if (hit) return o
        }
        return null
    }

    /**
     * Elige qué hacer al chocar y, si toca, también cambia a la otra mascota:
     * 1) las dos hacen la misma animación, 2) imito lo que hace la otra (50 %),
     * 3) cada una reacciona por su cuenta. Devuelve -1 si no tengo reacción.
     */
    private fun interact(o: PetBrain): Int {
        val mine = current ?: return -1
        val theirs = o.current ?: return border(Only.PET_SIDE)
        // Los números de animación sólo significan lo mismo en mascotas del mismo tipo
        val sameKind = o.def === def
        val sameDirection = movingLeft == o.movingLeft ||
            (movingLeft && o.x * o.scale > x * scale) ||
            (!movingLeft && o.x * o.scale < x * scale)
        fun sideways(n: Next) = n.only == Only.PET_SIDE || n.only == Only.NONE || n.only == Only.VERTICAL_PLUS

        val common = ArrayList<Next>()
        var mimic = -1
        for (n in mine.onBorder) {
            if (!sideways(n)) continue
            if (sameKind && theirs.id == n.id) mimic = n.id
            if (sameDirection) {
                if (mimic != -1) break else continue
            }
            if (n.probability == 0) continue
            if (sameKind && theirs.onBorder.any { it.id == n.id && it.probability > 0 && sideways(it) }) common.add(n)
        }

        if (common.isNotEmpty()) {
            val id = pickNext(common, Only.PET_SIDE)
            if (id >= 0) o.setAnimation(id)
            return id
        }
        if (mimic > 0 && rnd.nextInt(100) < 50) return mimic
        if (!sameDirection) {
            val other = o.pickNext(theirs.onBorder, Only.PET_SIDE)
            if (other >= 0) o.setAnimation(other)
        }
        return border(Only.PET_SIDE)
    }

    /**
     * Los XML se hicieron con el temporizador de Windows, que sólo avanza cada 15,6 ms:
     * 40 ms duran en realidad ~47 y 50 ms ~62. Se imita para que no vayan más rápido.
     */
    private fun windowsTimer(ms: Int): Int {
        val tick = 1000.0 / 64
        return (Math.ceil(ms.coerceAtLeast(10) / tick) * tick).toInt()
    }

    /**
     * Dónde aparece una hija. Si la madre mira a la derecha, las posiciones fijas
     * de la pantalla (la tina, por ejemplo) se reflejan, porque la madre también
     * entró por el otro lado; las relativas a la madre (imageX) usan el truco de desktopPet.
     */
    private fun childX(c: ChildRequest): Int {
        if (!c.parentFlipped || c.def.x.text.contains("imageX")) return value(c.def.x)
        return area.screenW - value(c.def.x, flipHack = false) - frameW
    }

    /** Evalúa un valor del XML con las medidas actuales. */
    private fun value(v: Value, flipHack: Boolean = true): Int {
        v.constant?.let { return it.toInt() }
        var text = v.text
        if (flipHack && child != null && child.parentFlipped) {
            // Igual que desktopPet: las hijas aparecen al otro lado si la madre mira a la derecha
            text = if (text.contains("-imageW")) text.replace("-imageW", "+imageW") else text.replace("imageW", "(-imageW)")
        }
        val random = rnd.nextInt(100).toDouble()
        return try {
            Expr.eval(text) { name ->
                when (name) {
                    "screenW" -> area.screenW.toDouble()
                    "screenH" -> area.screenH.toDouble()
                    "areaW" -> (area.right - area.left).toDouble()
                    "areaH" -> area.bottom.toDouble()
                    // Altura para la caída en diagonal hasta la tina (ver assets/pets/README.md)
                    "bathH" -> minOf(area.bottom, area.screenW - frameW).toDouble()
                    "imageW" -> frameW.toDouble()
                    "imageH" -> frameH.toDouble()
                    "imageX" -> child?.parentX ?: x
                    "imageY" -> child?.parentY ?: y
                    "random" -> random
                    "randS" -> randS.toDouble()
                    "scale" -> 1.0
                    else -> null
                }
            }.toInt()
        } catch (e: IllegalArgumentException) {
            0
        }
    }
}
