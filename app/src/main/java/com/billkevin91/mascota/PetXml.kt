package com.billkevin91.mascota

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Guarda una mascota en el formato animations.xml de desktopPet, para que el editor
 * pueda escribir lo que cambie y el archivo siga sirviendo en Windows y en esta app.
 */
object PetXml {

    fun write(def: PetDef): String {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        val root = doc.createElement("animations")
        root.setAttribute("xmlns", "https://esheep.petrucci.ch/")
        root.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance")
        doc.appendChild(root)

        val h = def.header
        root.add(doc, "header") {
            text(doc, "author", h.author)
            text(doc, "title", h.title.ifEmpty { def.title })
            text(doc, "petname", h.petName)
            text(doc, "version", h.version)
            text(doc, "info", h.info)
            text(doc, "application", h.application)
            if (h.icon.isNotEmpty()) cdata(doc, "icon", h.icon)
        }

        root.add(doc, "image") {
            text(doc, "tilesx", def.tilesX.toString())
            text(doc, "tilesy", def.tilesY.toString())
            cdata(doc, "png", def.png)
            text(doc, "transparency", def.transparency)
        }

        root.add(doc, "spawns") {
            def.spawns.forEachIndexed { i, s ->
                add(doc, "spawn") {
                    setAttribute("id", (if (s.id > 0) s.id else i + 1).toString())
                    setAttribute("probability", s.probability.toString())
                    text(doc, "x", s.x.text)
                    text(doc, "y", s.y.text)
                    add(doc, "next") {
                        setAttribute("probability", s.nextProbability.toString())
                        textContent = s.next.toString()
                    }
                }
            }
        }

        root.add(doc, "animations") {
            for (a in def.animations.values.sortedBy { it.id }) {
                add(doc, "animation") {
                    setAttribute("id", a.id.toString())
                    text(doc, "name", a.name)
                    movement(doc, "start", a.start)
                    movement(doc, "end", a.end)
                    add(doc, "sequence") {
                        setAttribute("repeatfrom", a.repeatFrom.toString())
                        setAttribute("repeat", a.repeat.text)
                        for (f in a.frames) text(doc, "frame", f.toString())
                        nexts(doc, a.onEnd)
                        if (a.action.isNotEmpty()) text(doc, "action", a.action)
                    }
                    if (a.onBorder.isNotEmpty()) add(doc, "border") { nexts(doc, a.onBorder) }
                    if (a.onGravity.isNotEmpty()) add(doc, "gravity") { nexts(doc, a.onGravity) }
                }
            }
        }

        if (def.children.isNotEmpty()) {
            root.add(doc, "childs") {
                for ((animationId, list) in def.children.toSortedMap()) for (c in list) {
                    add(doc, "child") {
                        setAttribute("animationid", animationId.toString())
                        text(doc, "x", c.x.text)
                        text(doc, "y", c.y.text)
                        text(doc, "next", c.next.toString())
                    }
                }
            }
        }

        if (def.sounds.isNotEmpty()) {
            root.add(doc, "sounds") {
                for (s in def.sounds) {
                    add(doc, "sound") {
                        setAttribute("animationid", s.animationId.toString())
                        text(doc, "probability", s.probability.toString())
                        if (s.loop > 0) text(doc, "loop", s.loop.toString())
                        text(doc, "base64", s.base64)
                    }
                }
            }
        }

        val out = StringWriter()
        TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.ENCODING, "utf-8")
            setOutputProperty(OutputKeys.INDENT, "yes")
        }.transform(DOMSource(doc), StreamResult(out))
        return out.toString()
    }

    private fun Element.movement(doc: Document, name: String, m: Movement) = add(doc, name) {
        text(doc, "x", m.x.text)
        text(doc, "y", m.y.text)
        text(doc, "offsety", m.offsetY.toString())
        text(doc, "opacity", number(m.opacity))
        text(doc, "interval", m.interval.text)
    }

    private fun Element.nexts(doc: Document, list: List<Next>) {
        for (n in list) add(doc, "next") {
            setAttribute("probability", n.probability.toString())
            setAttribute("only", only(n.only))
            textContent = n.id.toString()
        }
    }

    private fun only(o: Only) = when (o) {
        Only.NONE -> "none"
        Only.TASKBAR -> "taskbar"
        Only.WINDOW -> "window"
        Only.WINDOW_SIDE -> "windowSide"
        Only.HORIZONTAL -> "horizontal"
        Only.HORIZONTAL_PLUS -> "horizontal+"
        Only.VERTICAL -> "vertical"
        Only.VERTICAL_PLUS -> "vertical+"
        Only.PET_SIDE -> "petSide"
    }

    private fun number(d: Double) = if (d == Math.floor(d) && !d.isInfinite()) d.toLong().toString() else d.toString()

    private inline fun Element.add(doc: Document, name: String, fill: Element.() -> Unit): Element {
        val e = doc.createElement(name)
        e.fill()
        appendChild(e)
        return e
    }

    private fun Element.text(doc: Document, name: String, value: String) {
        appendChild(doc.createElement(name).also { it.textContent = value })
    }

    private fun Element.cdata(doc: Document, name: String, value: String) {
        appendChild(doc.createElement(name).also { it.appendChild(doc.createCDATASection(value)) })
    }
}
