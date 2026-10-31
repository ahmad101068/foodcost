#!/usr/bin/env bash
# Compiles the core and runs its tests with a plain Kotlin compiler: no Gradle and no internet.
# Usage: KOTLIN_HOME=/path/to/kotlinc scripts/test-local.sh
set -euo pipefail

: "${KOTLIN_HOME:?Set KOTLIN_HOME to the unpacked kotlin-compiler folder}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
LIB="$KOTLIN_HOME/lib"

echo "Compiling core..."
"$KOTLIN_HOME/bin/kotlinc" -nowarn -d "$OUT/main.jar" "$ROOT/core/src/main/kotlin"

echo "Compiling tests..."
"$KOTLIN_HOME/bin/kotlinc" -nowarn -cp "$OUT/main.jar:$LIB/kotlin-test.jar" -d "$OUT/test" \
  "$ROOT/core/src/test/kotlin" "$ROOT/scripts/local-test"

echo "Running tests..."
java -cp "$LIB/kotlin-stdlib.jar:$LIB/kotlin-test.jar:$OUT/main.jar:$OUT/test" LocalTestRunnerKt "$OUT/test"
