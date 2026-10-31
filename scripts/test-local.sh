#!/usr/bin/env bash
# Compiles every module and runs its tests with a plain Kotlin compiler: no Gradle and no internet.
# Each module's tests see only that module and the ones it depends on, as in the Gradle build.
# Usage: KOTLIN_HOME=/path/to/kotlinc scripts/test-local.sh
set -euo pipefail

: "${KOTLIN_HOME:?Set KOTLIN_HOME to the unpacked kotlin-compiler folder}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
LIB="$KOTLIN_HOME/lib"
KOTLINC="$KOTLIN_HOME/bin/kotlinc"
MODULES="core data" # in dependency order

echo "Compiling the test stand-ins..."
"$KOTLINC" -nowarn -d "$OUT/stub" "$ROOT/scripts/local-test"

CP=""
TEST_DIRS=()
for module in $MODULES; do
  echo "Compiling $module..."
  "$KOTLINC" -nowarn ${CP:+-cp "$CP"} -d "$OUT/$module.jar" "$ROOT/$module/src/main/kotlin"
  CP="${CP:+$CP:}$OUT/$module.jar"
  if [ -d "$ROOT/$module/src/test/kotlin" ]; then
    echo "Compiling $module tests..."
    "$KOTLINC" -nowarn -cp "$CP:$LIB/kotlin-test.jar:$OUT/stub" -d "$OUT/$module-test" "$ROOT/$module/src/test/kotlin"
    TEST_DIRS+=("$OUT/$module-test")
  fi
done

echo "Running tests..."
TEST_CP="$(IFS=:; echo "${TEST_DIRS[*]}")"
java -cp "$LIB/kotlin-stdlib.jar:$LIB/kotlin-test.jar:$OUT/stub:$CP:$TEST_CP" LocalTestRunnerKt "${TEST_DIRS[@]}"
