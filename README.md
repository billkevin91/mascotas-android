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

| Archivo | Contenido |
| --- | --- |
| `PetDef.kt` | Lee `animations.xml` y calcula sus expresiones (`screenW`, `random`...) |
| `PetBrain.kt` | Movimiento, bordes, gravedad, lanzamiento e hijas (port de `FormPet.cs`) |
| `PetService.kt` | Servicio que dibuja cada mascota en una ventanita flotante |
| `MainActivity.kt` | Elegir mascota y tamaño, pedir el permiso y soltarla |
| `app/src/main/assets/pets/` | Mascotas copiadas de desktopPet (sprites + animaciones) |

## Instalarla

1. En GitHub: **Actions** → **APK Mascotas** → última ejecución → **Artifacts** → `mascotas-apk`.
2. Descomprime el `.zip` e instala `app-debug.apk` (Android pedirá permitir apps de origen desconocido).
3. Abre la app, pulsa **Dar permiso para mostrarse sobre otras apps**, activa el permiso,
   vuelve y pulsa **Soltar en la pantalla**.

Funciona desde Android 7.0. Para Android Studio: **File → Open** y elige la carpeta del repositorio.

## Créditos

Mascotas y motor original: [desktopPet](https://github.com/Adrianotiger/desktopPet) de Adriano Petrucci
y su comunidad (eSheep y Neko: Adriano; gSheep azul y rosa: Oliver B.; zorritos y Neko rosa: Michelle!).
Licencia MIT en `app/src/main/assets/pets/LICENSE.md`.
