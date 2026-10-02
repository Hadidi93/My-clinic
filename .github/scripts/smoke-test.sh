#!/usr/bin/env bash
# Installs the debug APK on the emulator, opens the app, waits, and fails if
# it crashed. The full device log is saved for diagnosis either way.
set -uo pipefail
APK=$(ls app-apk/*.apk | head -1)
adb install -r "$APK"
adb logcat -c
adb shell am start -W -n com.myclinic.app/.debug.LaunchActivity
sleep 25
adb logcat -d > logcat.txt
adb shell screencap -p /sdcard/screen.png && adb pull /sdcard/screen.png screen.png || true

if grep -q "FATAL EXCEPTION" logcat.txt; then
  echo "::error::The app crashed on start"
  grep -A 60 "FATAL EXCEPTION" logcat.txt | head -120
  exit 1
fi
if ! adb shell pidof com.myclinic.app > /dev/null; then
  echo "::error::The app is not running after start"
  grep -iE "myclinic|AndroidRuntime" logcat.txt | tail -80
  exit 1
fi
echo "App started and is still running after 25 seconds."
