package com.billkevin91.mascota

import org.junit.Assert.assertEquals
import org.junit.Test

class PetXmlTest {
    /** Todo lo que el XML dice de una mascota, en texto, para comparar. */
    private fun describe(d: PetDef) = buildString {
        val h = d.header
        appendLine("header ${h.author}|${h.title}|${h.petName}|${h.version}|${h.info}|${h.application}|${h.icon.filterNot { it.isWhitespace() }.hashCode()}")
        appendLine("image ${d.tilesX}x${d.tilesY} ${d.transparency} ${d.png.filterNot { it.isWhitespace() }.hashCode()}")
        for (s in d.spawns) appendLine("spawn ${s.id} ${s.probability} ${s.x.text} ${s.y.text} ${s.next} ${s.nextProbability}")
        for (a in d.animations.values.sortedBy { it.id }) {
            fun m(m: Movement) = "${m.x.text},${m.y.text},${m.offsetY},${m.opacity},${m.interval.text}"
            fun n(l: List<Next>) = l.joinToString { "${it.id}/${it.probability}/${it.only}" }
            appendLine("anim ${a.id} ${a.name} ${m(a.start)} ${m(a.end)} ${a.frames} ${a.repeat.text} ${a.repeatFrom} ${a.action} [${n(a.onEnd)}] [${n(a.onBorder)}] [${n(a.onGravity)}]")
        }
        for ((id, l) in d.children.toSortedMap()) for (c in l) appendLine("child $id ${c.x.text} ${c.y.text} ${c.next}")
        for (s in d.sounds) appendLine("sound ${s.animationId} ${s.probability} ${s.loop} ${s.base64.filterNot { it.isWhitespace() }.hashCode()}")
        appendLine("kill ${d.kill} drag ${d.drag} fall ${d.fall}")
    }

    @Test fun guardarYVolverALeerNoPierdeNada() {
        for (name in Pets.all) {
            val original = Pets.def(name)
            val xml = PetXml.write(original)
            val again = PetDef.parse(xml.byteInputStream())
            assertEquals(name, describe(original), describe(again))
        }
    }

    @Test fun seLeenSonidosYCabecera() {
        val g = Pets.def("blue_sheep")
        assertEquals("Oliver B.", g.header.author)
        assert(g.sounds.size > 20) { "sonidos: ${g.sounds.size}" }
        assert(Pets.def("esheep64").header.icon.length > 100)
    }
}
