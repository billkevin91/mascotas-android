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
adb shell am start -S -W -n $PKG/.MainActivity --es soltar esheep64,blue_sheep,neko,fox
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
adb shell dumpsys activity services $PKG > shots/servicio.txt
grep -q PetService shots/servicio.txt || { echo "::error::El servicio de mascotas no está corriendo"; fail=1; }
adb logcat -d > shots/logcat.txt
if grep -n -A40 "FATAL EXCEPTION" shots/logcat.txt; then fail=1; fi
exit $fail
