package com.billkevin91.mascota

import android.content.Context

/** Texto de "Créditos y licencias" (el mismo contenido que CREDITS.md del repositorio). */
object Credits {

    private val BUNDLED = """
        MASCOTAS EN PANTALLA
        App Android de billkevin91 (github.com/billkevin91).

        MOTOR DE MOVIMIENTOS
        Port a Kotlin de desktopPet (github.com/Adrianotiger/desktopPet), versión 1.6.0:
        • Adriano Petrucci: autor de desktopPet y del formato animations.xml. © 2016, licencia MIT (abajo).
        • Zecheriah (github.com/ZechsVariety): choques entre mascotas y lanzamiento en desktopPet.

        CARRO ROJO
        Dibujos y movimientos originales hechos para esta app.

        MASCOTAS INCLUIDAS (de la carpeta Pets de desktopPet)
        • eSheep: animaciones de Adriano Petrucci (choques y lanzamiento: Zecheriah). Dibujos extraídos por LiL_Stenly (spritedatabase.net) del eSheep original de 1995 (autor no confirmado).
        • Oveja azul "Ben" y oveja rosa "Pearl" (gSheep 8.0): Oliver B., a partir de eSheep. Incluyen sonidos.
        • Neko: animaciones de Adriano Petrucci; dibujos de webneko.net. El Neko original lo creó Naoshi Watanabe en los años 80.
        • Neko rosa: Michelle!, a partir del Neko de webneko.net.
        • Zorrito y zorrito rosa: dibujos originales de Michelle Tran (Michelle!), personaje de The Fox Tale (ganeshsar.github.io/thefoxtale).
        Esta app cambió un poco el baño de eSheep y gSheep para que se vea bien en un teléfono.

        OTROS
        • Icono de la app: hecho con el icono de eSheep de desktopPet.
        • Icono de la notificación: dibujado para esta app.
    """.trimIndent()

    /** Texto completo con la licencia de desktopPet (lee un archivo: mejor fuera del hilo principal). */
    fun text(context: Context): String = buildString {
        append(BUNDLED)
        append("\n\nLICENCIA DE desktopPet\n")
        append(runCatching { context.assets.open("pets/LICENSE.md").bufferedReader().readText() }.getOrDefault(""))
    }
}
