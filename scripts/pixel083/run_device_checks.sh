#!/usr/bin/env bash
# Isolated Android regression tests. Run only on a disposable emulator.
# Usage: ANDROID_SERIAL=emulator-5554 ./scripts/pixel083/run_device_checks.sh /path/to/adb OUTPUT
set -u
adb_bin=$1
out=$2
mkdir -p "$out"
if [ "$("$adb_bin" shell getprop ro.kernel.qemu | tr -d '\r')" != 1 ]; then
  echo 'Refusing synthetic-data tests on a real device.' >&2
  exit 2
fi
"$adb_bin" shell wm size > "$out/viewport.txt"
"$adb_bin" shell wm density >> "$out/viewport.txt"
"$adb_bin" shell settings get system font_scale >> "$out/viewport.txt"
failed=0
while IFS= read -r test_class; do
  short=${test_class#ir.foodcost.app.}
  file=${short//\#/-}
  echo "Running $short"
  "$adb_bin" shell am force-stop ir.foodcost.app.debug </dev/null
  timeout 720 "$adb_bin" shell am instrument -w -e class "$test_class" ir.foodcost.app.debug.test/androidx.test.runner.AndroidJUnitRunner > "$out/$file.log" 2>&1 </dev/null
  if rg -q '^OK \([0-9]+ tests?\)' "$out/$file.log"; then
    echo "PASS $short"
  else
    echo "FAIL $short"
    tail -12 "$out/$file.log"
    failed=1
  fi
done <<'TESTS'
ir.foodcost.app.WalkThroughTest
ir.foodcost.app.data.SqliteOperationStoreTest
ir.foodcost.app.SettingsScopeTest
ir.foodcost.app.PricingMethodsTest
ir.foodcost.app.CurrencyPreferenceTest
ir.foodcost.app.ExitConfirmationTest
ir.foodcost.app.UnreadableWarningTest#everyScreenBlocksUnverifiedFinancialResultsAfterWarningAcknowledgement
ir.foodcost.app.UnreadableWarningTest#ingredientListAndDetailDoNotPresentStalePricesAsVerified
ir.foodcost.app.UnreadableWarningTest#goingOnWithoutTheUnreadablePriceShowsTheFiguresWithoutIt
ir.foodcost.app.RecoveryGuardTest
ir.foodcost.app.ReferenceVisualTest
TESTS
"$adb_bin" logcat -d -t 2000 '*:E' > "$out/logcat-errors.txt"
exit "$failed"
