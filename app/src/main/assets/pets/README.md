# Mascotas

`carro_rojo`: hecho para esta app (lo genera `extras/carro_rojo/generar.py`; no editar a mano).
El resto:

Copiadas de [desktopPet](https://github.com/Adrianotiger/desktopPet/tree/master/Pets)
(commit `f86f303`, licencia MIT en `LICENSE.md`).

Cambios:

1. En `esheep64`, `blue_sheep` y `pink_sheep`, la caída en diagonal hasta la
tina usa `bathH` en vez de `areaH`/`screenH`. Los XML están pensados para monitores más
anchos que altos; en un teléfono en vertical el recorrido no cabía y la tina quedaba fuera
de la pantalla. `bathH` es la altura disponible, pero nunca más que el ancho, así que en
horizontal todo queda igual que en el original.

2. En `blue_sheep` y `pink_sheep`, cuando `bath_start` (el salto a la tina que aparece en
   el suelo) toca el suelo, sigue con `bath_startC` (el chapuzón) en vez de `bath_eyes`.
   `bath_eyes` no lleva a ninguna otra animación: la oveja desaparecía, reaparecía en otro
   lado y dejaba la tina vacía, así que el baño nunca empezaba.
