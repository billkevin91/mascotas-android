# Editor de mascotas

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

## Hecho: fase 1 sencilla

Botón **Editar mascotas** (`EditorActivity.kt`, cambios en `PetEdit.kt`, pruebas en `PetEditTest.kt`):

- Elegir mascota y movimiento; cambiar el nombre de la mascota.
- Dibujos del movimiento: tocar uno de la tira lo quita; tocar uno de la hoja lo añade al final.
- Velocidad (ms al empezar y al terminar), avance horizontal y vertical, repeticiones y
  "repetir desde el dibujo nº". Admiten expresiones como en el XML (`random`, `screenW`...).
- **Guardar y probar**: guarda y la mascota de prueba hace el movimiento en bucle.
- Las incluidas se guardan como copia "<nombre> (mía)"; las tuyas se sobrescriben.

## Lo que ya estaba preparado

- **Modelo completo** (`PetDef.kt`): se lee todo el XML, incluidos cabecera, icono y sonidos.
- **Guardar** (`PetXml.kt`): escribe el XML de nuevo. Una prueba comprueba con las 7 mascotas que leer → guardar → leer no pierde nada.
- **Biblioteca** (`PetLibrary.kt`): mascotas incluidas + las tuyas (importadas, editadas o creadas), guardadas con `PetLibrary.save`.
- **Importar**: botón "Importar mascota (animations.xml)". Las 26 mascotas de desktopPet funcionan (probado). A eSheep y a las gSheep se les aplican los arreglos del baño (`PetCompat.kt`).
- **Vista previa**: el modo "Probar movimientos" ya muestra cualquier movimiento o escena en pantalla; el editor lo usará para ver los cambios al momento.
- **Pruebas automáticas** (`app/src/test`): cada cambio en el motor se prueba en GitHub antes de compilar.

## A tener en cuenta

- `PetService` guarda en memoria cada mascota cargada (`defs`, `sheets`): al guardar cambios de
  una mascota que está en pantalla hay que borrarla de esa memoria para que use la versión nueva.

## Hecho: avanzado y crear desde un dibujo

- **Opciones avanzadas** (en Editar mascotas): siguientes movimientos (al terminar, al chocar, sin
  suelo) con probabilidad y dónde valen; crías; sonido; autor y descripción. Lógica en
  `PetEdit.Advanced` (`AdvancedEditTest`).
- **Crear desde un dibujo** (`MakerActivity`): un solo dibujo → 11 poses generadas (`Drawing`) y
  movimientos básicos (`PetMaker`, `MakerTest`); o una hoja en cuadrícula. Quitar fondo y recortar
  en `Pixels` (`PixelsTest`).

## Hecho: compartir

"Compartir" y "Guardar como archivo" en el editor y al mantener pulsada una mascota (`PetShare.kt`).

## Pendiente

1. **Apariciones**: dónde y cómo entra en pantalla (por ahora, las del XML o las de `PetMaker`).
