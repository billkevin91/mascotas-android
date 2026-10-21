package com.billkevin91.mascota

/**
 * Arreglos para que los XML de desktopPet (pensados para monitores de Windows) se vean bien
 * en un teléfono. Son los mismos que llevan las mascotas incluidas (ver assets/pets/README.md)
 * y se aplican al importar eSheep o cualquier gSheep. Sólo se cambian líneas conocidas.
 */
object PetCompat {
    private const val BATH_D = "bathH/2+(randS*bathH/2)/120"

    /** eSheep: la caída en diagonal hasta la tina cabe en un teléfono en vertical. */
    private val ESHEEP = listOf(
        "<y>areaH/2-(randS*areaH/2)/120-imageH" to "<y>areaH-imageH-($BATH_D)",
        "repeat=\"(areaH/2+(randS*areaH/2)/120-imageH-68)/2\"" to "repeat=\"($BATH_D-imageH-68)/2\"",
        "repeat=\"(areaH/2+(randS*areaH/2)/120-imageH-10)/2\"" to "repeat=\"($BATH_D-imageH-10)/2\"",
        "<x>screenW+10-areaH/2-(randS*areaH/2)/120" to "<x>screenW+10-($BATH_D)",
    )

    /** gSheep: lo mismo, y el salto a la tina del suelo acaba en chapuzón (bath_startC). */
    private val GSHEEP = listOf(
        "<y>areaH/2-(randS*areaH/2)/120-imageH" to "<y>areaH-imageH-($BATH_D)",
        "repeat=\"(areaH/2+(randS*screenH/2)/120-imageH-68)/2\"" to "repeat=\"($BATH_D-imageH-68)/2\"",
        "repeat=\"(screenH/2+(randS*screenH/2)/120-imageH-10)/2\"" to "repeat=\"($BATH_D-imageH-10)/2\"",
        "<x>screenW-areaH/2-(randS*areaH/2)/120" to "<x>screenW-($BATH_D)",
        "<next probability=\"100\" only=\"taskbar\">223</next>" to "<next probability=\"100\" only=\"taskbar\">217</next>",
    )

    /** Devuelve el XML arreglado (igual si no es una mascota conocida o ya estaba arreglado). */
    fun patch(xml: String): String {
        val set = when {
            ESHEEP.all { xml.countOf(it.first) == 1 } -> ESHEEP
            GSHEEP.all { xml.countOf(it.first) == 1 } -> GSHEEP
            else -> return xml
        }
        var out = xml
        for ((from, to) in set) out = out.replace(from, to)
        return out
    }

    private fun String.countOf(s: String): Int {
        var n = 0
        var i = indexOf(s)
        while (i >= 0) { n++; i = indexOf(s, i + s.length) }
        return n
    }
}
