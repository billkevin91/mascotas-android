# Mascotas

Copiadas de [desktopPet](https://github.com/Adrianotiger/desktopPet/tree/master/Pets)
(commit `f86f303`, licencia MIT en `LICENSE.md`).

Único cambio: en `esheep64`, `blue_sheep` y `pink_sheep`, la caída en diagonal hasta la
tina usa `bathH` en vez de `areaH`/`screenH`. Los XML están pensados para monitores más
anchos que altos; en un teléfono en vertical el recorrido no cabía y la tina quedaba fuera
de la pantalla. `bathH` es la altura disponible, pero nunca más que el ancho, así que en
horizontal todo queda igual que en el original.
