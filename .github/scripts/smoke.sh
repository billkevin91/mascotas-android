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
adb shell am start -S -W -n $PKG/.MainActivity --es soltar esheep64,blue_sheep,neko,fox --es mover esheep64:47,blue_sheep:215 --ez nombres true
adb shell input keyevent KEYCODE_HOME
for i in 1 2 3 4 5 6; do
  sleep 5
  adb exec-out screencap -p > shots/paseo$i.png
done
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
adb shell am start -S -W -n $PKG/.MainActivity --es soltar esheep64,blue_sheep,neko,fox
adb shell input keyevent KEYCODE_HOME
sleep 5
cpu() { adb shell cat /proc/$(adb shell pidof $PKG | tr -d '\r')/stat | awk '{print $14 + $15}'; }
c0=$(cpu); sleep 30; c1=$(cpu)
adb shell input keyevent KEYCODE_SLEEP
sleep 3; c2=$(cpu); sleep 30; c3=$(cpu)
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard || true
sleep 5
adb exec-out screencap -p > shots/despierta.png
hz=100
if [ -z "$c0$c1" ] || [ -z "$c2" ] || [ -z "$c3" ]; then
  echo "::warning::No se pudo medir el tiempo de CPU"; c0=0; c1=0; c2=0; c3=0
fi
on=$(( (c1 - c0) * 1000 / hz )); off=$(( (c3 - c2) * 1000 / hz ))
{
  echo "CPU de la app en 30 s con 4 mascotas y la pantalla encendida: ${on} ms ($(( on / 300 ))% de un núcleo)"
  echo "CPU de la app en 30 s con la pantalla apagada: ${off} ms"
} | tee shots/bateria.txt
echo "::notice title=Batería (pantalla encendida)::${on} ms de CPU en 30 s"
echo "::notice title=Batería (pantalla apagada)::${off} ms de CPU en 30 s"
if [ "$off" -gt 300 ]; then echo "::error::Con la pantalla apagada la app sigue gastando CPU (${off} ms en 30 s)"; fail=1; fi
adb shell dumpsys activity services $PKG > shots/servicio.txt
grep -q PetService shots/servicio.txt || { echo "::error::El servicio de mascotas no está corriendo"; fail=1; }
adb logcat -d > shots/logcat.txt
if grep -n -A40 "FATAL EXCEPTION" shots/logcat.txt; then fail=1; fi
exit $fail
