# Créditos y licencias

## La app

**Mascotas en pantalla**, app Android de [billkevin91](https://github.com/billkevin91).

## Motor de movimientos

El motor (`PetBrain.kt`, `PetDef.kt`) es un port a Kotlin de **desktopPet**
(<https://github.com/Adrianotiger/desktopPet>, versión 1.6.0, commit `f86f303`):

- **Adriano Petrucci**: autor de desktopPet y del formato `animations.xml`. © 2016, licencia MIT.
- **Zecheriah** ([ZechsVariety](https://github.com/ZechsVariety)): choques entre mascotas y
  lanzamiento en desktopPet, en los que se basan los de esta app.

La licencia MIT de desktopPet obliga a incluir su aviso de copyright y su texto en las copias.
Está en [`app/src/main/assets/pets/LICENSE.md`](app/src/main/assets/pets/LICENSE.md), va dentro
de la app y se muestra en la pantalla "Créditos y licencias".

## Mascotas incluidas

**Carro rojo** (`carro_rojo`): dibujos y movimientos originales hechos para esta app
(generador en [`extras/carro_rojo`](extras/carro_rojo)).

Las demás están copiadas de la carpeta [Pets](https://github.com/Adrianotiger/desktopPet/tree/master/Pets) de desktopPet.

| Mascota | Animaciones (XML) | Dibujos | Notas |
| --- | --- | --- | --- |
| eSheep (`esheep64`) | Adriano Petrucci; choques y lanzamiento: Zecheriah | Extraídos por **LiL_Stenly** ([spritedatabase.net](http://spritedatabase.net/game/2071)) del eSheep original de 1995 | Autor del eSheep original sin confirmar (se le asocia con *Stray Sheep*) |
| Oveja azul (`blue_sheep`, "Ben") | **Oliver B.** (gSheep 8.0) | Basados en eSheep | Incluye sonidos (la app aún no los reproduce) |
| Oveja rosa (`pink_sheep`, "Pearl") | **Oliver B.** (gSheep 8.0) | Basados en eSheep | Incluye sonidos |
| Neko (`neko`) | Adriano Petrucci | De [webneko.net](https://webneko.net/) | El Neko original lo creó Naoshi Watanabe en los años 80 |
| Neko rosa (`pink_neko`) | **Michelle!** | Neko de webneko.net, recoloreado | |
| Zorrito (`fox`) | **Michelle!** (Michelle Tran) | **Originales de Michelle Tran**: personaje de [The Fox Tale](https://ganeshsar.github.io/thefoxtale/index.html) | |
| Zorrito rosa (`pink_fox`) | **Michelle!** | Originales de Michelle Tran, versión rosa | |

Cambios hechos en esta app a esos XML (ver [`assets/pets/README.md`](app/src/main/assets/pets/README.md)):
la caída hasta la tina de eSheep y gSheep cabe en un teléfono en vertical, y el salto de las gSheep
a la tina del suelo termina en chapuzón.

## Otros

- **Icono de la app**: hecho con el icono de eSheep de desktopPet (`esheep64/icon.png`).
- **Icono de la notificación** (la huella): dibujado para esta app.
- **JUnit 4** (EPL 1.0): sólo para las pruebas automáticas; no va dentro de la app.

## Antes de publicar (por ejemplo, en Google Play)

Esto no es asesoría legal, pero conviene saberlo:

- La licencia MIT cubre el **código** de desktopPet. Varios **dibujos** vienen de otras obras:
  los de eSheep (y las gSheep, basadas en ellos) se extrajeron de un programa de 1995 de otro autor,
  y los de Neko vienen de webneko.net. MIT no puede dar permiso sobre obras de terceros, así que
  sus derechos no están claros. Lo mismo pasa con los sonidos de las gSheep, cuyo origen no consta.
- El **carro rojo** es dibujo propio de esta app, y los **zorritos** son dibujos originales de su
  autora publicados en desktopPet: son la opción más segura, junto con las mascotas que dibujes tú.
- El **icono de la app** sale de eSheep: para publicar es mejor uno propio.
- El propio autor de desktopPet publica su app Android con eSheep en Google Play, pero eso no
  garantiza que otra app pueda hacerlo.
- Tu código (lo que no viene de desktopPet) todavía **no tiene licencia** en el repositorio:
  sin licencia, nadie más puede reutilizarlo legalmente. Si quieres que sea libre, MIT es la opción
  más sencilla y compatible con desktopPet.
