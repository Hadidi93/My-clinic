#!/usr/bin/env bash
# Installs the debug APK on the emulator and checks that:
#   1. the app starts and keeps running;
#   2. opening email links (expired, and with an unusable sign-in code) never
#      crashes it and shows the right message on the sign-in screen.
# The full device log, screen text and a screenshot are saved for diagnosis.
set -uo pipefail
APK=$(ls app-apk/*.apk | head -1)
adb install -r "$APK"
adb logcat -c
fail=0

check_crash() {
  adb logcat -d > logcat.txt
  if grep -q "FATAL EXCEPTION" logcat.txt; then
    echo "::error::The app crashed ($1)"
    grep -A 60 "FATAL EXCEPTION" logcat.txt | head -120
    exit 1
  fi
}

screen_has() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb pull /sdcard/ui.xml ui.xml > /dev/null 2>&1
  grep -q "$1" ui.xml
}

echo "== 1. Normal start"
adb shell am start -W -n com.myclinic.app/.debug.LaunchActivity
sleep 25
check_crash "normal start"
if ! adb shell pidof com.myclinic.app > /dev/null; then
  echo "::error::The app is not running after start"; exit 1
fi
echo "OK: app started and is still running."

echo "== 2. Expired verification link"
adb shell am start -W -a android.intent.action.VIEW \
  -d "'myclinic://auth-callback/verified#error=access_denied&error_code=otp_expired'" com.myclinic.app
sleep 8
check_crash "expired link"
if screen_has "This link has expired"; then echo "OK: expired-link message shown."; else
  echo "::error::Expired-link message not shown"; fail=1; fi

echo "== 3. Verification link with an unusable sign-in code"
adb shell am start -W -a android.intent.action.VIEW \
  -d "'myclinic://auth-callback/verified?code=not-a-real-code'" com.myclinic.app
sleep 12
check_crash "link with bad code"
if screen_has "Your email is verified"; then echo "OK: 'email verified, please sign in' shown."; else
  echo "::error::'Email verified' message not shown"; fail=1; fi

adb shell screencap -p /sdcard/screen.png && adb pull /sdcard/screen.png screen.png > /dev/null 2>&1 || true
adb logcat -d > logcat.txt
exit $fail
