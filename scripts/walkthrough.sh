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

# The walk-through, and the tests of the phone's own database, which uses a file of its own.
adb shell am instrument -w -e class ir.foodcost.app.WalkThroughTest,ir.foodcost.app.data.SqliteOperationStoreTest "$APP.test/androidx.test.runner.AndroidJUnitRunner" | tee walkthrough.log

mkdir -p screenshots
for f in $(adb shell run-as "$APP" ls files/screenshots | tr -d '\r'); do
  adb exec-out run-as "$APP" cat "files/screenshots/$f" > "screenshots/$f"
done
failed=0
grep -qE "^OK \([0-9]+ tests?\)" walkthrough.log || failed=1

# Runs the test or class $1 in a process of its own and adds what it says to the log $2. The app
# reads its data once, when it first opens, so a test of data it could not read needs its own.
alone() {
  adb shell am instrument -w -e class "$1" "$APP.test/androidx.test.runner.AndroidJUnitRunner" > alone.log
  tee -a "$2" < alone.log
  grep -qE "^OK \([0-9]+ tests?\)" alone.log || failed=1
}

# A stored change that cannot be read: no figure is shown and every screen says why, and the user
# can go on without it. Each test on data it makes and removes itself.
: > unreadable.log
for test in everyScreenBlocksUnverifiedFinancialResultsAfterWarningAcknowledgement \
  ingredientListAndDetailDoNotPresentStalePricesAsVerified \
  goingOnWithoutTheUnreadablePriceShowsTheFiguresWithoutIt; do
  alone "ir.foodcost.app.UnreadableWarningTest#$test" unreadable.log
done

# A damaged database set aside, and a row not read: no change and no whole backup until a backup
# is brought back or the user chooses to go on without it.
: > recovery.log
alone ir.foodcost.app.RecoveryGuardTest recovery.log
adb logcat -d '*:E' > logcat.txt || true

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
