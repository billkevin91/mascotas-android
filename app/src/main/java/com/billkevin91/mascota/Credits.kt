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

    private val BUNDLED_EN = """
        MASCOTAS: PETS ON YOUR SCREEN
        Android app by billkevin91 (github.com/billkevin91).

        MOVEMENT ENGINE
        Kotlin port of desktopPet (github.com/Adrianotiger/desktopPet), version 1.6.0:
        • Adriano Petrucci: author of desktopPet and of the animations.xml format. © 2016, MIT license (below).
        • Zecheriah (github.com/ZechsVariety): pet collisions and throwing in desktopPet.

        INCLUDED PETS (from desktopPet's Pets folder)
        • eSheep: animations by Adriano Petrucci (collisions and throwing: Zecheriah). Drawings extracted by LiL_Stenly (spritedatabase.net) from the original 1995 eSheep (author unconfirmed).
        • Blue sheep "Ben" and pink sheep "Pearl" (gSheep 8.0): Oliver B., based on eSheep. They include sounds.
        • Neko: animations by Adriano Petrucci; drawings from webneko.net. The original Neko was created by Naoshi Watanabe in the 1980s.
        • Pink Neko: Michelle!, based on the webneko.net Neko.
        • Fox and pink fox: original drawings by Michelle Tran (Michelle!), a character from The Fox Tale (ganeshsar.github.io/thefoxtale).
        This app slightly changed the eSheep and gSheep bath so it looks right on a phone.

        OTHER
        • App icon: made from the desktopPet eSheep icon.
        • Notification icon: drawn for this app.
    """.trimIndent()

    /** Texto completo con la licencia de desktopPet (lee un archivo: mejor fuera del hilo principal). */
    fun text(context: Context): String = buildString {
        append(tr(BUNDLED, BUNDLED_EN))
        append(tr("\n\nLICENCIA DE desktopPet\n", "\n\ndesktopPet LICENSE\n"))
        append(runCatching { context.assets.open("pets/LICENSE.md").bufferedReader().readText() }.getOrDefault(""))
    }
}
