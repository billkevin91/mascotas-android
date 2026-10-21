# Corazón

Corazón con audífonos dibujado para Mascotas en pantalla: rojo, con cara alegre, audífonos azules,
guantes blancos y tenis azules.

![Demo](demo.gif)

**Qué hace:** camina, se da la vuelta en los bordes, parpadea, hace el saludo militar, saluda con
la mano, baila con notas musicales, escucha música con las manos en los audífonos, salta de alegría
y se duerme. Si lo arrastras levanta los brazos; si cae fuerte se marea (con estrellitas). Al
despedirlo dice adiós con la mano, se encoge y se convierte en un corazoncito con brillos.

**Instalarlo:** descarga [`animations.xml`](animations.xml) y en la app pulsa
**Importar mascota (animations.xml)**. También sirve en desktopPet de Windows.

**Cambiarlo:** todo sale de [`generar.py`](generar.py). Con Python y Pillow, `python3 generar.py`
vuelve a crear `animations.xml`, `sheet.png`, `icon.png`, `preview.png` (los 24 dibujos numerados)
y `demo.gif`.
