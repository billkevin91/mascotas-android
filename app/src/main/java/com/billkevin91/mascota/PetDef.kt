package com.billkevin91.mascota

import org.w3c.dom.Element
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

// Formato animations.xml de desktopPet (https://github.com/Adrianotiger/desktopPet, licencia MIT).

/** Dónde está la mascota cuando se elige la siguiente animación (atributo `only`). */
enum class Only {
    NONE, TASKBAR, WINDOW, WINDOW_SIDE, HORIZONTAL, HORIZONTAL_PLUS, VERTICAL, VERTICAL_PLUS, PET_SIDE;

    companion object {
        fun parse(s: String?) = when (s?.trim()) {
            "taskbar" -> TASKBAR
            "window" -> WINDOW
            "windowSide" -> WINDOW_SIDE
            "horizontal" -> HORIZONTAL
            "horizontal+" -> HORIZONTAL_PLUS
            "vertical" -> VERTICAL
            "vertical+" -> VERTICAL_PLUS
            "petSide" -> PET_SIDE
            else -> NONE
        }
    }
}

/** Número del XML: puede ser fijo ("-2") o una expresión ("random*(screenW-imageW)/100"). */
class Value(raw: String?) {
    val text = raw?.trim().orEmpty().ifEmpty { "0" }
    val constant: Double? = text.toDoubleOrNull()
}

class Movement(val x: Value, val y: Value, val offsetY: Int, val opacity: Double, val interval: Value)

class Next(val id: Int, val probability: Int, val only: Only)

class Animation(
    val id: Int,
    val name: String,
    val start: Movement,
    val end: Movement,
    val frames: List<Int>,
    val repeat: Value,
    val repeatFrom: Int,
    val action: String,
    val onEnd: List<Next>,
    val onBorder: List<Next>,
    val onGravity: List<Next>,
) {
    val hasGravity get() = onGravity.isNotEmpty()
}

class Spawn(val probability: Int, val x: Value, val y: Value, val next: Int)

class ChildDef(val x: Value, val y: Value, val next: Int)

class PetDef(
    val title: String,
    val tilesX: Int,
    val tilesY: Int,
    val png: String,
    val transparency: String,
    val animations: Map<Int, Animation>,
    val spawns: List<Spawn>,
    val children: Map<Int, List<ChildDef>>,
) {
    private fun idOf(name: String, default: Int) = animations.values.firstOrNull { it.name == name }?.id ?: default

    // Nombres especiales que la app usa por su cuenta
    val drag = idOf("drag", 1)
    val fall = idOf("fall", 1)
    val kill = idOf("kill", -1)
    val toss = idOf("toss", -1)
    val fallSoft = idOf("fall soft", 1)
    val fallHard = idOf("fall hard", 1)

    companion object {
        fun parse(input: InputStream): PetDef {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input)
            val root = doc.documentElement
            val header = root.kid("header")
            val image = root.kid("image")!!

            val animations = root.kid("animations")?.kids("animation").orEmpty().associate { a ->
                val seq = a.kid("sequence")
                val id = a.getAttribute("id").toInt()
                id to Animation(
                    id = id,
                    name = a.text("name"),
                    start = movement(a.kid("start")),
                    end = movement(a.kid("end")),
                    frames = seq?.kids("frame").orEmpty().map { it.textContent.trim().toInt() },
                    repeat = Value(seq?.getAttribute("repeat")),
                    repeatFrom = seq?.getAttribute("repeatfrom")?.trim()?.toIntOrNull() ?: 0,
                    action = seq?.text("action").orEmpty(),
                    onEnd = nexts(seq),
                    onBorder = nexts(a.kid("border")),
                    onGravity = nexts(a.kid("gravity")),
                )
            }
            val spawns = root.kid("spawns")?.kids("spawn").orEmpty().map { s ->
                Spawn(
                    probability = s.getAttribute("probability").trim().toIntOrNull() ?: 0,
                    x = Value(s.text("x")),
                    y = Value(s.text("y")),
                    next = s.text("next").toIntOrNull() ?: 1,
                )
            }
            val children = root.kid("childs")?.kids("child").orEmpty().groupBy(
                { it.getAttribute("animationid").trim().toInt() },
                { ChildDef(Value(it.text("x")), Value(it.text("y")), it.text("next").toIntOrNull() ?: -1) },
            )
            return PetDef(
                title = header?.text("title")?.ifEmpty { null } ?: "Mascota",
                tilesX = image.text("tilesx").toInt(),
                tilesY = image.text("tilesy").toInt(),
                png = image.text("png"),
                transparency = image.text("transparency"),
                animations = animations,
                spawns = spawns,
                children = children,
            )
        }

        private fun movement(e: Element?) = Movement(
            x = Value(e?.text("x")),
            y = Value(e?.text("y")),
            offsetY = e?.text("offsety")?.toDoubleOrNull()?.toInt() ?: 0,
            opacity = e?.text("opacity")?.toDoubleOrNull() ?: 1.0,
            interval = Value(e?.text("interval")?.ifEmpty { null } ?: "1000"),
        )

        private fun nexts(e: Element?) = e?.kids("next").orEmpty().mapNotNull { n ->
            val id = n.textContent.trim().toIntOrNull() ?: return@mapNotNull null
            Next(id, n.getAttribute("probability").trim().toIntOrNull() ?: 0, Only.parse(n.getAttribute("only")))
        }

        private fun Element.kids(name: String): List<Element> {
            val list = ArrayList<Element>()
            val nodes = childNodes
            for (i in 0 until nodes.length) {
                val n = nodes.item(i)
                if (n is Element && n.tagName == name) list.add(n)
            }
            return list
        }

        private fun Element.kid(name: String) = kids(name).firstOrNull()
        private fun Element.text(name: String) = kid(name)?.textContent?.trim().orEmpty()
    }
}

/**
 * Calculadora para las expresiones del XML: + - * / %, paréntesis, variables
 * y Convert(valor, System.Int32). Las variables desconocidas valen 0.
 */
class Expr private constructor(private val s: String, private val vars: (String) -> Double?) {
    private var i = 0

    companion object {
        fun eval(text: String, vars: (String) -> Double?): Double {
            val e = Expr(text, vars)
            val v = e.sum()
            e.skip()
            require(e.i >= e.s.length) { "Expresión inválida: $text" }
            return v
        }
    }

    private fun skip() {
        while (i < s.length && s[i].isWhitespace()) i++
    }

    private fun eat(c: Char): Boolean {
        skip()
        if (i < s.length && s[i] == c) { i++; return true }
        return false
    }

    private fun sum(): Double {
        var v = product()
        while (true) {
            v = when {
                eat('+') -> v + product()
                eat('-') -> v - product()
                else -> return v
            }
        }
    }

    private fun product(): Double {
        var v = unary()
        while (true) {
            v = when {
                eat('*') -> v * unary()
                eat('/') -> unary().let { d -> if (d == 0.0) 0.0 else v / d }
                eat('%') -> unary().let { d -> if (d == 0.0) 0.0 else v % d }
                else -> return v
            }
        }
    }

    private fun unary(): Double = when {
        eat('-') -> -unary()
        eat('+') -> unary()
        else -> atom()
    }

    private fun atom(): Double {
        skip()
        require(i < s.length) { "Expresión incompleta: $s" }
        if (eat('(')) {
            val v = sum()
            require(eat(')')) { "Falta ')': $s" }
            return v
        }
        val c = s[i]
        if (c.isDigit() || c == '.') {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            return s.substring(start, i).toDouble()
        }
        require(c.isLetter() || c == '_') { "Carácter inesperado '$c': $s" }
        val start = i
        while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_' || s[i] == '.')) i++
        val name = s.substring(start, i)
        if (eat('(')) {
            val args = ArrayList<Double>()
            if (!eat(')')) {
                do {
                    skip()
                    // El tipo de Convert(x, System.Int32) es un nombre, no un número
                    val typeStart = i
                    while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '.' || s[i] == '_')) i++
                    val word = s.substring(typeStart, i)
                    skip()
                    if (word.startsWith("System.") && (i >= s.length || s[i] == ',' || s[i] == ')')) {
                        args.add(0.0)
                    } else {
                        i = typeStart
                        args.add(sum())
                    }
                } while (eat(','))
                require(eat(')')) { "Falta ')': $s" }
            }
            return when (name.lowercase()) {
                "convert" -> Math.rint(args.firstOrNull() ?: 0.0)
                else -> args.firstOrNull() ?: 0.0
            }
        }
        return vars(name) ?: 0.0
    }
}
