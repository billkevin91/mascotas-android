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
class ChildRequest(val def: ChildDef, val parentX: Double, val parentY: Double, val parentFlipped: Boolean, val depth: Int)

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
    val isDying get() = current?.id == def.kill

    private var current: Animation? = null
    private var step = 0
    private var interval = 200
    private var totalSteps = 1
    private var startX = 0; private var startY = 0; private var endX = 0; private var endY = 0
    private var startInterval = 200; private var endInterval = 200
    private var randS = 50
    private var killFade = 1.0
    private var tossVx = 0.0
    private var tossVy = 0.0
    private val pendingChildren = ArrayList<ChildRequest>()

    fun start() {
        if (child == null) spawn()
        else {
            x = value(child.def.x).toDouble()
            y = value(child.def.y).toDouble()
            offsetY = 0.0
            setAnimation(child.def.next)
        }
    }

    /** Avanza un paso. Devuelve los milisegundos hasta el siguiente. */
    fun tick(): Int {
        if (finished) return 1000
        if (step < 0) step = 0
        nextStep()
        step++
        return interval.coerceAtLeast(10)
    }

    fun takeChildren(): List<ChildRequest> {
        if (pendingChildren.isEmpty()) return emptyList()
        val list = ArrayList(pendingChildren)
        pendingChildren.clear()
        return list
    }

    // ---- Tocar, arrastrar y lanzar ----

    fun grab() {
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

    /** Despedirse: animación "kill" si existe, si no desaparece. */
    fun kill() {
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
        val total = def.spawns.sumOf { it.probability }
        val r = if (total > 0) rnd.nextInt(total) else 0
        var sum = 0
        for (s in def.spawns) {
            sum += s.probability
            if (sum >= r) return s
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

        val depth = child?.depth ?: 0
        if (depth < 5) {
            def.children[id]?.forEach {
                pendingChildren.add(ChildRequest(it, x, y, !movingLeft, depth + 1))
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

        if (!tossing) interval = startInterval + (endInterval - startInterval) * step / total
        opacity = a.start.opacity + (a.end.opacity - a.start.opacity) * step / total
        offsetY = (a.start.offsetY + (a.end.offsetY - a.start.offsetY) * step / total).toDouble()

        if (dragging || tossing) return

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
            }
        } else if (dx > 0) {
            if (x + dx + frameW > area.right) {
                val b = border(Only.VERTICAL)
                if (b >= 0) { x = (area.right - frameW).toDouble(); dx = 0.0; next = b; newAnimation = true } else leaving = true
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
            val nextAnimation = when {
                x < -frameW || x > area.screenW -> -1
                y < -frameH || y > area.screenH -> -1
                else -> pickNext(a.onEnd, if (y + frameH + dy >= area.bottom - 2) Only.TASKBAR else Only.NONE)
            }
            if (a.id == def.kill) {
                killFade -= 0.1
                opacity = killFade
                if (killFade <= 0.1) finished = true
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
        if (newAnimation) setAnimation(next)
    }

    /** Evalúa un valor del XML con las medidas actuales. */
    private fun value(v: Value): Int {
        v.constant?.let { return it.toInt() }
        var text = v.text
        if (child != null && child.parentFlipped) {
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
