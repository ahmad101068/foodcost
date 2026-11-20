#!/usr/bin/env python3
"""Fail CI when an instrumented regression is absent from the isolated runner."""
from pathlib import Path
import re

root = Path(__file__).resolve().parent.parent
suite = set((root / "scripts/android-test-suite.txt").read_text().splitlines())
tests = set()
for source in (root / "app/src/androidTest").rglob("*.kt"):
    text = source.read_text()
    package = re.search(r"^package ([\w.]+)", text, re.M)
    for name in re.findall(r"@Test\s+(?:fun\s+)(\w+)\s*\(", text):
        tests.add(f"{package[1]}.{source.stem}#{name}")
missing = sorted(t for t in tests if t not in suite and t.split("#")[0] not in suite)
stale = sorted(s for s in suite if not any(t == s or t.startswith(s + "#") for t in tests))
if missing or stale:
    raise SystemExit(f"Missing tests: {missing}\nUnknown entries: {stale}")
print(f"All {len(tests)} native tests are included in {len(suite)} isolated runs.")
