#!/usr/bin/env bash
# Runs the walk-through test on the emulator that is already running, then copies the
# screenshots the test leaves in the app's files to ./screenshots.
# Used by the android workflow; needs adb and the debug and test APKs built.
set -uo pipefail
APP=ir.foodcost.app.debug

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb logcat -c || true

adb shell am instrument -w -e class ir.foodcost.app.WalkThroughTest "$APP.test/androidx.test.runner.AndroidJUnitRunner" | tee walkthrough.log

mkdir -p screenshots
for f in $(adb shell run-as "$APP" ls files/screenshots | tr -d '\r'); do
  adb exec-out run-as "$APP" cat "files/screenshots/$f" > "screenshots/$f"
done
ls -la screenshots
adb logcat -d '*:E' > logcat.txt || true

grep -q "^OK (1 test)" walkthrough.log
