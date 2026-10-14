#!/usr/bin/env bash
# Starts the iPhone app in a simulator and fails if it is not running 25 s later.
# Saves a screenshot (ios-screen.png) and the app's log (ios-log.txt).
set -uo pipefail
app="$1"
udid=$(xcrun simctl list devices available -j | python3 -c '
import json, sys
devices = json.load(sys.stdin)["devices"]
phones = [d for runtime, ds in devices.items() if "iOS" in runtime for d in ds if d["name"].startswith("iPhone")]
print(phones[-1]["udid"])')
echo "Simulator: $udid"
xcrun simctl boot "$udid" || true
xcrun simctl bootstatus "$udid" -b
xcrun simctl install "$udid" "$app"
xcrun simctl launch "$udid" com.myclinic.app
sleep 25
xcrun simctl io "$udid" screenshot ios-screen.png || true
xcrun simctl spawn "$udid" log show --last 2m --style compact --predicate 'process == "MyClinic"' > ios-log.txt 2>&1 || true
if xcrun simctl spawn "$udid" launchctl list | grep -q com.myclinic.app; then
  echo "The iPhone app is running."
else
  echo "::error::The iPhone app is not running 25 s after launch (crashed?)."
  grep -iE "exception|fatal|error" ios-log.txt | head -20 | sed 's/^/::error::/'
  exit 1
fi
