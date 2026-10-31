#!/usr/bin/env bash
# Runs the walk-through test on the emulator that is already running, then copies the
# screenshots the test leaves in the app's files to ./screenshots.
# Used by the android workflow; needs adb and the debug and test APKs built.
set -uo pipefail
APP=ir.foodcost.app.debug

# Give the emulator's own launcher time to settle, so it does not freeze over the screenshots.
sleep 30

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb logcat -c || true

adb shell am instrument -w -e class ir.foodcost.app.WalkThroughTest "$APP.test/androidx.test.runner.AndroidJUnitRunner" | tee walkthrough.log

mkdir -p screenshots
for f in $(adb shell run-as "$APP" ls files/screenshots | tr -d '\r'); do
  adb exec-out run-as "$APP" cat "files/screenshots/$f" > "screenshots/$f"
done
adb logcat -d '*:E' > logcat.txt || true
failed=0
grep -q "^OK (1 test)" walkthrough.log || failed=1

# The release build is shrunk by R8, which the test above does not cover: it must start too.
adb install -r app/build/outputs/apk/release/app-release.apk
adb logcat -c || true
adb shell am start -W -n ir.foodcost.app/.MainActivity
sleep 8
adb exec-out screencap -p > screenshots/00-release-start.png
if adb shell pidof ir.foodcost.app > /dev/null && ! adb logcat -d | grep -q "FATAL EXCEPTION"; then
  echo "The release build started."
else
  echo "The release build did not start." | tee -a walkthrough.log
  adb logcat -d | grep -A 40 "FATAL EXCEPTION" | tee -a walkthrough.log
  failed=1
fi

ls -la screenshots
exit $failed
