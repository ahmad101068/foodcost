#!/usr/bin/env bash
# Runs the walk-through test on the emulator that is already running, then copies the
# screenshots the test leaves in the app's files to ./screenshots.
# Used by the android workflow; needs adb and the debug and test APKs built.
set -euo pipefail
APP=ir.foodcost.app.debug

# Give the emulator's own launcher time to settle, so it does not freeze over the screenshots.
sleep 30

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb logcat -c || true

# One canonical list covers recovery, pricing, visual and audit regressions.
failed=0
./scripts/pixel083/run_device_checks.sh "$(command -v adb)" native-results || failed=1
cat native-results/*.log > walkthrough.log
mkdir -p screenshots
for directory in screenshots pricing-screenshots audit-fixes-screenshots ux-control-screenshots complete-audit-screens pixel083-screenshots; do
  for f in $(adb shell run-as "$APP" ls "files/$directory" 2>/dev/null | tr -d '\r'); do
    case "$f" in
      *.png) adb exec-out run-as "$APP" cat "files/$directory/$f" > "screenshots/$f" ;;
    esac
  done
done
adb logcat -d '*:E' > logcat.txt || true

# The release build is shrunk by R8, which the test above does not cover: it must start too.
# Clear only the disposable emulator's system launcher, whose ANR can cover the release smoke shot.
# This must never stop the launcher of a real connected phone.
if [ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" = "1" ]; then
  adb shell am force-stop com.google.android.apps.nexuslauncher || true
  adb shell am force-stop com.android.launcher3 || true
fi
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

# Every numbered acceptance image, including the previously missing largest-text dishes, is required.
for number in $(seq -w 0 34); do
  if ! compgen -G "screenshots/$number-*.png" > /dev/null; then
    echo "Missing required screenshot $number" | tee -a walkthrough.log
    failed=1
  fi
done
ls -la screenshots
exit $failed
