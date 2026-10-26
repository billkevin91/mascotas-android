#!/usr/bin/env bash
# Prueba de humo de Mascotas: suelta varias mascotas, saca capturas y falla si la app se cierra.
set -u
PKG=com.billkevin91.mascota
adb install -r apk/app-debug.apk
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
mkdir -p shots
adb logcat -c
fail=0
adb shell am start -W -n $PKG/.MainActivity
sleep 4
adb exec-out screencap -p > shots/menu.png
adb shell am start -S -W -n $PKG/.MainActivity --es soltar esheep64,blue_sheep,neko,fox --es mover esheep64:47,blue_sheep:61 --ez nombres true --ez sonido true
adb shell input keyevent KEYCODE_HOME
for i in 1 2 3 4 5 6; do
  sleep 5
  adb exec-out screencap -p > shots/paseo$i.png
done
# Sonido: la oveja azul de prueba estornuda en bucle; Android tiene que haber reproducido algo de la app
# (cada sonido que empieza queda en el registro de la app)
adb shell dumpsys audio > shots/audio.txt
sonidos=$(adb logcat -d -s Mascotas | grep -c "sonido:")
echo "::notice title=Sonido::$sonidos sonidos según el registro de la app"
if [ "$sonidos" -lt 1 ]; then echo "::error::La app no reprodujo ningún sonido"; fail=1; fi
# Arrastrar y lanzar por la parte de abajo, donde caminan
read -r W H < <(adb shell wm size | tail -1 | sed 's/.*: //; s/x/ /')
for x in $(seq $((W / 8)) $((W / 8)) $((W - W / 8))); do
  adb shell input swipe $x $((H - H / 8)) $((W / 2)) $((H / 4)) 120
done
sleep 6
adb exec-out screencap -p > shots/lanzadas.png
if ! adb shell pidof $PKG > /dev/null; then
  echo "::error::La app se cerró"
  fail=1
fi
# Batería: tiempo de CPU de la app en 30 s con la pantalla encendida y en 30 s apagada
adb shell am start -S -W -n $PKG/.MainActivity --es soltar esheep64,blue_sheep,neko,fox --ez sonido false
adb shell input keyevent KEYCODE_HOME
sleep 5
cpu() { adb shell cat /proc/$(adb shell pidof $PKG | tr -d '\r')/stat | awk '{print $14 + $15}'; }
antes=$(adb shell dumpsys window windows | awk -v pkg=$PKG '/Window #/ {mine = index($0, pkg) > 0} mine && /mViewVisibility=0x0/ {n++} END {print n+0}')
echo "Mascotas visibles antes de apagar la pantalla: $antes" | tee shots/bateria.txt
c0=$(cpu); sleep 30; c1=$(cpu)
adb shell input keyevent KEYCODE_SLEEP
sleep 3; c2=$(cpu); sleep 30; c3=$(cpu)
# Al encender y desbloquear tienen que volver a verse
adb shell input keyevent KEYCODE_WAKEUP
sleep 3
adb shell wm dismiss-keyguard || true
sleep 5
adb exec-out screencap -p > shots/despierta.png
adb shell dumpsys window windows > shots/ventanas.txt
visibles=$(awk -v pkg=$PKG '/Window #/ {mine = index($0, pkg) > 0} mine && /mViewVisibility=0x0/ {n++} END {print n+0}' shots/ventanas.txt)
echo "Mascotas visibles después de encender la pantalla: $visibles" | tee -a shots/bateria.txt
echo "::notice title=Visibles::antes de apagar $antes, al encender $visibles"
if [ "$visibles" -lt 4 ]; then echo "::error::Al encender la pantalla no volvieron las mascotas ($visibles visibles)"; fail=1; fi
# Igual, pero con PIN como un teléfono de verdad: apagar, encender, escribir el PIN
contar() { adb shell dumpsys window windows | awk -v pkg=$PKG '/Window #/ {mine = index($0, pkg) > 0} mine && /mViewVisibility=0x0/ {n++} END {print n+0}'; }
adb shell locksettings set-pin 1234
adb shell input keyevent KEYCODE_SLEEP
sleep 8
adb shell input keyevent KEYCODE_WAKEUP
sleep 3
bloqueada=$(contar)
adb shell input keyevent KEYCODE_MENU
sleep 2
adb shell input text 1234
adb shell input keyevent KEYCODE_ENTER
sleep 6
adb exec-out screencap -p > shots/despierta_pin.png
con_pin=$(contar)
adb shell locksettings clear --old 1234 || true
echo "Con PIN: visibles con la pantalla bloqueada $bloqueada, al desbloquear $con_pin" | tee -a shots/bateria.txt
echo "::notice title=Visibles con PIN::bloqueada $bloqueada, al desbloquear $con_pin"
if [ "$con_pin" -lt 4 ]; then echo "::error::Al desbloquear con PIN no volvieron las mascotas ($con_pin visibles)"; fail=1; fi
# Caricia: un toque a una mascota tiene que sacar corazones
ventanas() { adb shell dumpsys window windows | python3 .github/scripts/ventanas.py "$@"; }
corazones=0
for intento in 1 2 3; do
  centro=$(ventanas centros $PKG | sed -n "${intento}p")
  [ -z "$centro" ] && continue
  adb shell input tap $centro
  sleep 0.6
  corazones=$(ventanas contar Corazones-mascota)
  [ "$corazones" -gt 0 ] && break
  sleep 2
done
adb exec-out screencap -p > shots/caricia.png
echo "::notice title=Caricia::$corazones ventanas de corazones tras tocar una mascota (intento $intento)"
if [ "$corazones" -lt 1 ]; then echo "::error::Al tocar una mascota no salieron corazones"; fail=1; fi
# Comida desde el atajo del icono: cae, van a por ella y se la comen (lo cuenta el registro de la app)
adb logcat -c
adb shell am start -n $PKG/.AccionActivity -a com.billkevin91.mascota.atajo.COMER
comida_en=-1
for s in $(seq 1 120); do
  sleep 1
  [ $s -eq 2 ] && adb exec-out screencap -p > shots/comida.png
  if adb logcat -d -s Mascotas | grep -q "se la comió"; then comida_en=$s; break; fi
done
adb logcat -d -s Mascotas > shots/comida.txt
cat shots/comida.txt
cae=$(grep -c "comida: cae" shots/comida.txt)
quien=$(grep -o "se la comió .*" shots/comida.txt | head -1)
echo "::notice title=Comida::cayó $cae; $quien a los ${comida_en} s (-1 = nadie)"
if [ "$cae" -lt 1 ]; then echo "::error::El atajo Dar de comer no echó comida"; fail=1; fi
if [ "$comida_en" -lt 0 ]; then echo "::error::Nadie se comió la comida en 2 minutos"; fail=1; fi
# Reacciones al teléfono: cargador y batería baja
reaccion() {  # reaccion NOMBRE_EN_EL_REGISTRO TÍTULO
  local ok=0
  for s in 1 2 3 4 5 6; do
    sleep 1
    if adb logcat -d -s Mascotas | grep -q "reacción: $1"; then ok=1; break; fi
  done
  burbujas=$(ventanas contar Burbuja-mascota)
  echo "::notice title=$2::reaccionó=$ok, burbujas en pantalla=$burbujas"
  if [ "$ok" -ne 1 ]; then echo "::error::No reaccionaron a: $2"; fail=1; fi
}
adb logcat -c
adb shell dumpsys battery unplug
sleep 2
adb shell dumpsys battery set ac 1
reaccion CHARGE "Cargador"
adb exec-out screencap -p > shots/cargando.png
adb shell dumpsys battery unplug
adb shell dumpsys battery set level 50
sleep 2
adb shell dumpsys battery set level 5
reaccion LOW_BATTERY "Batería baja"
adb exec-out screencap -p > shots/bateria_baja.png
adb shell dumpsys battery reset
adb logcat -d -s Mascotas > shots/reacciones.txt
# Crear una mascota desde un dibujo (una bolita con ojos sobre fondo blanco) y soltarla
antes_crear=$(ventanas contar $PKG)
adb logcat -c
# Tarea nueva (NEW_TASK + CLEAR_TASK): si no, Android sólo trae al frente la pantalla abierta
adb shell am start -n $PKG/.MainActivity -f 0x10008000 --ez crear_prueba true
sleep 2
adb shell input keyevent KEYCODE_HOME
creada=""
for s in $(seq 1 15); do
  sleep 1
  creada=$(adb logcat -d -s Mascotas | grep -o "creada: .*" | head -1)
  [ -n "$creada" ] && break
done
sleep 4
adb exec-out screencap -p > shots/creada.png
despues_crear=$(ventanas contar $PKG)
adb logcat -d -s Mascotas > shots/creada.txt
echo "::notice title=Crear desde un dibujo::${creada:-no se creó}; mascotas visibles $antes_crear → $despues_crear"
if [ -z "$creada" ]; then echo "::error::No se pudo crear la mascota desde un dibujo"; grep -A5 "crear:" shots/creada.txt; fail=1; fi
if [ "$despues_crear" -le "$antes_crear" ]; then echo "::error::La mascota creada no apareció en pantalla"; fail=1; fi
# Ajustes: ocultarlas cuando otra app ocupa toda la pantalla (y volver al salir)
adb logcat -c
adb shell am start -n $PKG/.MainActivity -f 0x10008000 --ez pantalla_completa true
sleep 4
adb exec-out screencap -p > shots/pantalla_completa.png
en_completa=$(ventanas contar $PKG)
adb shell input keyevent KEYCODE_HOME
sleep 4
al_salir=$(ventanas contar $PKG)
adb logcat -d -s Mascotas | grep "pantalla completa" > shots/pantalla_completa.txt
echo "::notice title=Pantalla completa::visibles a pantalla completa $en_completa, al salir $al_salir; $(tr '\n' ' ' < shots/pantalla_completa.txt)"
if [ "$en_completa" -ne 0 ]; then echo "::error::A pantalla completa siguen viéndose $en_completa mascotas"; fail=1; fi
if [ "$al_salir" -lt 1 ]; then echo "::error::Al salir de la pantalla completa no volvieron"; fail=1; fi
# Compartir: se prepara el archivo y se abre el menú de compartir de Android
adb logcat -c
adb shell am start -n $PKG/.MainActivity -f 0x10008000 --es compartir blue_sheep
sleep 4
adb exec-out screencap -p > shots/compartir.png
compartido=$(adb logcat -d -s Mascotas | grep -o "compartir: .*" | head -1)
menu=$(adb shell dumpsys activity activities | grep -c -i "chooser")
echo "::notice title=Compartir::${compartido:-no se preparó}; menú de compartir abierto: $menu"
if [ -z "$compartido" ] || [ "$menu" -lt 1 ]; then echo "::error::No se pudo compartir la mascota"; fail=1; fi
adb shell input keyevent KEYCODE_BACK
adb shell input keyevent KEYCODE_HOME
sleep 2
# El widget está registrado
widget=$(adb shell dumpsys appwidget | grep -c "MascotasWidget")
echo "::notice title=Widget::registrado $widget veces en dumpsys appwidget"
if [ "$widget" -lt 1 ]; then echo "::error::El widget no está registrado"; fail=1; fi
hz=100
if [ -z "$c0$c1" ] || [ -z "$c2" ] || [ -z "$c3" ]; then
  echo "::warning::No se pudo medir el tiempo de CPU"; c0=0; c1=0; c2=0; c3=0
fi
on=$(( (c1 - c0) * 1000 / hz )); off=$(( (c3 - c2) * 1000 / hz ))
{
  echo "CPU de la app en 30 s con 4 mascotas y la pantalla encendida: ${on} ms ($(( on / 300 ))% de un núcleo)"
  echo "CPU de la app en 30 s con la pantalla apagada: ${off} ms"
} | tee -a shots/bateria.txt
echo "::notice title=Batería (pantalla encendida)::${on} ms de CPU en 30 s"
echo "::notice title=Batería (pantalla apagada)::${off} ms de CPU en 30 s"
if [ "$off" -gt 300 ]; then echo "::error::Con la pantalla apagada la app sigue gastando CPU (${off} ms en 30 s)"; fail=1; fi
adb shell dumpsys activity services $PKG > shots/servicio.txt
grep -q PetService shots/servicio.txt || { echo "::error::El servicio de mascotas no está corriendo"; fail=1; }
adb logcat -d > shots/logcat.txt
if grep -n -A40 "FATAL EXCEPTION" shots/logcat.txt; then fail=1; fi
# Botón "Quitar" del widget (y del atajo): se van todas
adb shell am start -n $PKG/.AccionActivity -a com.billkevin91.mascota.atajo.QUITAR
sleep 4
quedan=$(ventanas contar $PKG)
echo "::notice title=Quitar (widget)::quedan $quedan mascotas"
if [ "$quedan" -ne 0 ]; then echo "::error::El botón Quitar del widget no las quitó ($quedan)"; fail=1; fi
exit $fail
