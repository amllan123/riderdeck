#!/usr/bin/env bash
# Installs the APK on the CI emulator and screenshots the demo dashboard.
set -x
APK="$1"
OUT=shots
PKG=com.riderdeck.app
ACT=$PKG/.MainActivity
mkdir -p "$OUT"
adb wait-for-device
adb shell svc power stayon true
adb shell wm dismiss-keyguard
adb shell settings put global window_animation_scale 1
adb shell settings put global transition_animation_scale 1
adb shell settings put global animator_duration_scale 1
adb install -r -g "$APK" || exit 0
# no "Viewing full screen" hint, fixed rotation
adb shell settings put secure immersive_mode_confirmations confirmed
adb shell settings put system accelerometer_rotation 0

shot() { # name rotation [extra am args]
  local name=$1 rot=$2
  shift 2
  adb shell am force-stop $PKG
  adb shell settings put system user_rotation "$rot"
  adb shell wm user-rotation lock "$rot" || true
  sleep 4
  adb shell am start -W -n $ACT --ez demo true "$@"
  sleep 22
  adb exec-out screencap -p > "$OUT/$name.png"
}

shot warmup 1
rm -f "$OUT/warmup.png"
shot shot-1-landscape 1
shot shot-2-landscape-sheet 1 --es sheet more
shot shot-3-portrait 0
shot shot-4-landscape-light 1 --ez sun true
ls -la "$OUT"
exit 0
