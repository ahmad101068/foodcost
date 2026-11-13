#!/usr/bin/env bash
set -euo pipefail
APP=ir.foodcost.app.debug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class ir.foodcost.app.ReferenceVisualTest "$APP.test/androidx.test.runner.AndroidJUnitRunner" | tee reference-preview.log
mkdir -p reference-screenshots
for f in $(adb shell run-as "$APP" ls files/reference-screenshots | tr -d '\r'); do
  adb exec-out run-as "$APP" cat "files/reference-screenshots/$f" > "reference-screenshots/$f"
done
grep -qE '^OK \([0-9]+ tests?\)' reference-preview.log
for number in 01 02 03 04 05 06 07 08 09; do
  compgen -G "reference-screenshots/$number-*.png" > /dev/null
done

# Exercise the real section controls and extreme online percentages in a fresh process too.
adb shell am instrument -w -e class ir.foodcost.app.PricingMethodsTest "$APP.test/androidx.test.runner.AndroidJUnitRunner" | tee pricing-methods-preview.log
for f in $(adb shell run-as "$APP" ls files/pricing-screenshots | tr -d '\r'); do
  adb exec-out run-as "$APP" cat "files/pricing-screenshots/$f" > "reference-screenshots/$f"
done
grep -qE '^OK \([0-9]+ tests?\)' pricing-methods-preview.log
for number in 31 32 33 34; do
  compgen -G "reference-screenshots/$number-*.png" > /dev/null
done
