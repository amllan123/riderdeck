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
adb shell settings put global hide_error_dialogs 1
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
# Search page with the keyboard up, typing a real query (Google results), portrait then landscape.
adb shell settings put secure show_ime_with_hard_keyboard 1
search_shot() { # name rotation
  local name=$1 rot=$2
  adb shell am force-stop $PKG
  adb shell settings put system user_rotation "$rot"
  adb shell wm user-rotation lock "$rot" || true
  sleep 4
  adb shell am start -W -n $ACT --ez demo true --ez parked true --es sheet goto
  sleep 16
  if [ "$rot" = "1" ]; then adb shell input tap 1200 95; sleep 3; fi
  adb shell input text "motorizzr"
  sleep 10
  adb exec-out screencap -p > "$OUT/$name.png"
}
search_shot shot-8-search-portrait 0
search_shot shot-9-search-landscape 1

# Simulated ride through the real GPS → recorder → navigation → map path, plus a network self-test
# (Google search, weather, shared links, routing). Logs and frame stats go to the release too.
adb shell am force-stop $PKG
adb shell settings put system user_rotation 1
adb shell wm user-rotation lock 1 || true
sleep 3
timeout 20 adb logcat -c || true
adb shell am start -W -n $ACT --ez sim true --ez selftest true
sleep 2
timeout 30 adb shell dumpsys gfxinfo $PKG reset > /dev/null || true
sleep 26
adb exec-out screencap -p > "$OUT/shot-5-sim-riding.png"
sleep 22
adb exec-out screencap -p > "$OUT/shot-6-sim-traffic-stop.png"
sleep 35
adb exec-out screencap -p > "$OUT/shot-7-sim-riding-on.png"
timeout 60 adb shell dumpsys gfxinfo $PKG > "$OUT/gfxinfo.txt"
timeout 60 adb logcat -d -v time -s "RiderSim:*" "RiderSelfTest:*" "RiderSearch:*" "RiderShare:*" "RideLoop:*" "RideService:*" "AndroidRuntime:E" > "$OUT/selftest.txt"
timeout 60 adb logcat -d -v time "*:E" > "$OUT/errors.txt"
ls -la "$OUT"
exit 0
