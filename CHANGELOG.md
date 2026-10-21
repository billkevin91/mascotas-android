# Cambios

Todos los cambios de la app, del más nuevo al más antiguo.

## [Sin publicar] — versión 1.0 en desarrollo

### Añadido
- **Carro rojo** ([`extras/carro_rojo`](extras/carro_rojo)): mascota nueva con dibujos originales,
  lista para importar. Al despedirlo se va destrozando; al caer fuerte rebota.
- **Créditos y licencias**: botón en la app y [`CREDITS.md`](CREDITS.md) con los autores del motor
  y de cada mascota, la licencia MIT de desktopPet y avisos antes de publicar.
- **Modo prueba aparte**: al probar movimientos, tus mascotas se esperan ocultas y sólo se mueve una
  mascota de prueba; al salir, vuelven donde estaban. El letrero y la velocidad de esa pantalla sólo
  afectan a la de prueba.
- **Importar mascotas** (`animations.xml` de desktopPet o de su PetEditor). Las 26 de desktopPet
  funcionan; a eSheep y a las gSheep se les aplican los arreglos del baño. Mantener pulsada una
  importada para quitarla.
- **Guardar mascotas en XML** sin perder nada (cabecera, icono, sonidos): base para el editor.
- **Pruebas automáticas** del motor (22) que GitHub ejecuta antes de compilar el APK.
- **Probar movimientos**: lista de todos los movimientos con buscador, repetir, nombre del movimiento
  encima de la mascota, y escenas enteras (como el baño) al tocar una de sus partes.
- **Velocidad**: Lenta, Tranquila, Normal o Rápida.
- **Movimiento suave**: la mascota se desliza entre pasos en vez de saltar.
- **Choques entre mascotas**: se dan la vuelta, rebotan o se echan a dormir juntas.
- **Crías tocables**: la oveja negra, las flores o las tinas se quitan con doble toque.
- Primera versión: mascotas de desktopPet (eSheep, gSheep azul y rosa, Neko, zorritos) que caminan
  sobre las demás apps; arrastrar, lanzar y despedir con doble toque; tamaño y notificación con
  "Otra más" y "Quitar todas".

### Corregido
- Lanzar la mascota fallaba con el teléfono encendido varios días seguidos.
- Importar: se rechazan archivos con `DOCTYPE`/`ENTITY` y cuadrículas de dibujos no válidas.
- Modo prueba: repetir la despedida (`kill`) dejaba la mascota congelada y creaba otra.
- La despedida se desvanecía y volvía a verse de golpe (también fuera del modo prueba).
- Modo prueba: al repetir, se colaba otro movimiento (por ejemplo `idle` en las mascotas tipo Pikachu).
- Modo prueba: cada movimiento empieza donde tiene sentido (suelo, techo, pared o desde arriba).
- Al salir de la tina, la oveja se veía duplicada.
- El baño: la tina aparecía en otro sitio o fuera de la pantalla, y en las gSheep la oveja
  desaparecía en vez de bañarse.
- Las animaciones iban hasta un 25 % más rápido que en Windows.
- Las apariciones no respetaban su probabilidad.
