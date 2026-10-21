# Editor de mascotas: plan

## Qué es una mascota

Un archivo `animations.xml` (el formato de [desktopPet](https://github.com/Adrianotiger/desktopPet)) con:

| Parte | Qué guarda |
| --- | --- |
| Cabecera | nombre, autor, versión, descripción, icono |
| Imagen | la hoja de dibujos (PNG) dividida en una cuadrícula (`tilesx` × `tilesy`) y el color de fondo transparente |
| Apariciones (`spawns`) | dónde aparece la mascota y con qué movimiento empieza |
| Movimientos (`animations`) | qué dibujos muestra, cada cuántos ms, cuánto avanza, cuántas veces repite y a qué movimiento pasa después (al terminar, al chocar con un borde o al quedarse sin suelo) |
| Crías (`childs`) | otras figuras que aparecen con un movimiento (la tina, las flores, la oveja negra) |
| Sonidos | mp3/wav que suenan al empezar un movimiento |

## Lo que ya está preparado

- **Modelo completo** (`PetDef.kt`): se lee todo el XML, incluidos cabecera, icono y sonidos.
- **Guardar** (`PetXml.kt`): escribe el XML de nuevo. Una prueba comprueba con las 7 mascotas que leer → guardar → leer no pierde nada.
- **Biblioteca** (`PetLibrary.kt`): mascotas incluidas + mascotas tuyas guardadas en el teléfono. El editor guardará con `PetLibrary.save`.
- **Importar**: botón "Importar mascota (animations.xml)". Las 26 mascotas de desktopPet funcionan (probado). A eSheep y a las gSheep se les aplican los arreglos del baño (`PetCompat.kt`).
- **Vista previa**: el modo "Probar movimientos" ya muestra cualquier movimiento o escena en pantalla; el editor lo usará para ver los cambios al momento.
- **Pruebas automáticas** (`app/src/test`): cada cambio en el motor se prueba en GitHub antes de compilar.

## Fases propuestas

1. **Editar movimientos** de una mascota (si es incluida, se edita una copia):
   lista de movimientos → elegir dibujos tocando la cuadrícula de la hoja, velocidad,
   cuánto avanza, repeticiones y a qué pasa después (con probabilidades). Vista previa en vivo.
2. **Cabecera, apariciones y crías**: nombre, autor, dónde aparece, qué crías salen.
3. **Mascota nueva desde un dibujo**: elegir una imagen de la galería, indicar la cuadrícula
   y el color de fondo; la app crea movimientos básicos (caminar, caer, arrastrar, dormir)
   para que funcione desde el primer momento y luego se ajustan en la fase 1.
4. **Compartir**: exportar el `animations.xml` (sirve también en desktopPet de Windows).
5. **Sonidos**: hoy la app no reproduce los sonidos de los XML; se podrían activar y editar.

## Decisiones pendientes (para ti)

1. ¿Qué va primero: **editar las mascotas que ya hay** (fase 1) o **crear una nueva desde tu propio dibujo** (fase 3)?
2. ¿El editor **dentro de la app** (en el teléfono) o como **página web** (más cómodo en pantalla grande, y luego se importa)?
3. ¿**Sencillo** (velocidad, orden de dibujos, nombre) o **completo** como el PetEditor de Windows (todo, incluidas probabilidades y crías)?
4. ¿Quieres poder **compartir** las mascotas (por ejemplo, para usarlas en Windows o pasarlas a otro teléfono)?

Recomendación: dentro de la app, empezando por la fase 1 en modo sencillo con vista previa en vivo,
y dejando las opciones avanzadas en una sección aparte.
