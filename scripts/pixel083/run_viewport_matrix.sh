#!/usr/bin/env bash
# Actual screenshots of the synthetic native fixture, one fresh instrumentation process per state.
# Keep the physical emulator panel in the calibrated 942x1704 configuration.
set -u
adb_bin=$1
out=$2
mkdir -p "$out"
if [ "$("$adb_bin" shell getprop ro.kernel.qemu | tr -d '\r')" != 1 ]; then exit 2; fi
failed=0
for config in 411-100 411-150 411-200 320-100 320-150 320-200 411-dark; do
  case "$config" in
    320-*) "$adb_bin" shell wm size 732x1704 </dev/null ;;
    *) "$adb_bin" shell wm size reset </dev/null ;;
  esac
  case "$config" in
    *-150) scale=1.5;;
    *-200) scale=2.0;;
    *) scale=1.0;;
  esac
  "$adb_bin" shell settings put system font_scale "$scale" </dev/null
  if [ "$config" = 411-dark ]; then "$adb_bin" shell cmd uimode night yes </dev/null
  else "$adb_bin" shell cmd uimode night no </dev/null; fi
  "$adb_bin" shell am force-stop ir.foodcost.app.debug </dev/null
  echo "Capture $config"
  mkdir -p "$out/$config"
  timeout 300 "$adb_bin" shell am instrument -w -e class ir.foodcost.app.PixelReferenceTest#captureFiveNativeReferenceScreensAndVerifyLiveFinancialValues ir.foodcost.app.debug.test/androidx.test.runner.AndroidJUnitRunner > "$out/$config/test.log" 2>&1 </dev/null
  "$adb_bin" exec-out run-as ir.foodcost.app.debug tar -C files/pixel083-screenshots -cf - . > "$out/$config/raw.tar" </dev/null
  tar -xf "$out/$config/raw.tar" -C "$out/$config" && rm "$out/$config/raw.tar"
  if rg -q '^OK \([0-9]+ tests?\)' "$out/$config/test.log"; then echo "PASS $config"; else failed=1; echo "FAIL $config"; tail -10 "$out/$config/test.log"; fi
done
"$adb_bin" shell wm size reset
"$adb_bin" shell settings put system font_scale 1.0
"$adb_bin" shell cmd uimode night no
exit "$failed"
