# Mascotas en pantalla

App Android con mascotas de los 90 (eSheep, Neko, zorritos...) que caminan
**por encima de todas tus apps**, como el clásico eSheep de Windows.
Es un port a Kotlin del motor de [desktopPet](https://github.com/Adrianotiger/desktopPet)
(versión 1.6.0, commit `f86f303`, licencia MIT) y usa sus mismos archivos `animations.xml`.

- Arrastra la mascota con el dedo, lánzala y verás cómo rebota y cae.
- Toca dos veces para despedirla.
- Si pones varias, reaccionan al chocar entre sí: las eSheep se dan la vuelta, rebotan
  o se echan a dormir junto a la que ya duerme; las demás se dan la vuelta o siguen de largo.
- Desde la notificación: **Otra más** o **Quitar todas**.
- **Velocidad**: Lenta, Tranquila, Normal o Rápida, para todas a la vez.
- **Probar movimientos**: elige cualquiera de los movimientos del XML (con buscador) y la
  mascota lo hace al momento; puede repetirlo y mostrar su nombre encima.
- **Importar mascotas**: cualquier `animations.xml` de desktopPet (las 26 de su carpeta
  [Pets](https://github.com/Adrianotiger/desktopPet/tree/master/Pets) funcionan) o hecho con su PetEditor.

| Archivo | Contenido |
| --- | --- |
| `PetDef.kt` | Lee `animations.xml` y calcula sus expresiones (`screenW`, `random`...) |
| `PetBrain.kt` | Movimiento, bordes, gravedad, lanzamiento e hijas (port de `FormPet.cs`) |
| `PetService.kt` | Servicio que dibuja cada mascota en una ventanita flotante |
| `MainActivity.kt` | Elegir mascota y tamaño, pedir el permiso y soltarla |
| `TestActivity.kt` | Modo prueba: lista de movimientos para verlos uno a uno |
| `PetLibrary.kt` | Mascotas incluidas e importadas; importar, guardar y borrar |
| `PetXml.kt` | Guardar una mascota en formato `animations.xml` (para el editor) |
| `PetCompat.kt` | Arreglos del baño para eSheep/gSheep importadas |
| `app/src/test/` | Pruebas automáticas del motor (corren en GitHub antes de compilar) |
| `app/src/main/assets/pets/` | Mascotas de desktopPet (sprites + animaciones; ver su README) |

## Instalarla

1. En GitHub: **Actions** → **APK Mascotas** → última ejecución → **Artifacts** → `mascotas-apk`.
2. Descomprime el `.zip` e instala `app-debug.apk` (Android pedirá permitir apps de origen desconocido).
3. Abre la app, pulsa **Dar permiso para mostrarse sobre otras apps**, activa el permiso,
   vuelve y pulsa **Soltar en la pantalla**.

Funciona desde Android 7.0. El plan del editor de mascotas está en [`docs/EDITOR.md`](docs/EDITOR.md). Para Android Studio: **File → Open** y elige la carpeta del repositorio.

## Créditos

Mascotas y motor original: [desktopPet](https://github.com/Adrianotiger/desktopPet) de Adriano Petrucci
y su comunidad (eSheep y Neko: Adriano; gSheep azul y rosa: Oliver B.; zorritos y Neko rosa: Michelle!).
Licencia MIT en `app/src/main/assets/pets/LICENSE.md`.
