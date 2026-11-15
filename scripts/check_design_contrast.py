#!/usr/bin/env python3
"""Check normal-size FoodCost semantic text pairs against WCAG 2.2 AA contrast (4.5:1)."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
TOKENS = ROOT / "app/src/main/kotlin/ir/foodcost/app/ui/theme/DesignTokens.kt"
PATTERN = re.compile(r"val\s+(\w+)\s*=\s*Color\(0xFF([0-9A-Fa-f]{6})\)")
TOKENS_MAP = dict(PATTERN.findall(TOKENS.read_text(encoding="utf-8")))

PAIRS = [
    ("primary button", "OnPrimary", "Primary"),
    ("primary container", "OnPrimaryContainer", "PrimaryContainer"),
    ("page body", "Ink", "Paper"),
    ("secondary body", "Muted", "Paper"),
    ("sales figure", "SalesForeground", "SalesBackground"),
    ("food cost", "FoodForeground", "FoodBackground"),
    ("success", "SuccessForeground", "SuccessBackground"),
    ("warning", "WarningForeground", "WarningBackground"),
    ("error", "ErrorForeground", "ErrorBackground"),
]

def luminance(hex_color: str) -> float:
    channels = [int(hex_color[index:index+2], 16)/255.0 for index in (0, 2, 4)]
    linear = [c/12.92 if c <= 0.04045 else ((c+0.055)/1.055)**2.4 for c in channels]
    return 0.2126*linear[0] + 0.7152*linear[1] + 0.0722*linear[2]

def contrast(foreground: str, background: str) -> float:
    a, b = luminance(foreground), luminance(background)
    return (max(a, b) + 0.05)/(min(a, b)+0.05)

def main() -> None:
    failures = []
    for label, foreground, background in PAIRS:
        if foreground not in TOKENS_MAP or background not in TOKENS_MAP:
            failures.append(f"{label}: missing token {foreground} or {background}")
            continue
        ratio = contrast(TOKENS_MAP[foreground], TOKENS_MAP[background])
        print(f"{label}: {ratio:.2f}:1 (required >= 4.50:1)")
        if ratio < 4.5:
            failures.append(f"{label} failed: {ratio:.2f}:1")
    if failures:
        raise SystemExit("\n".join(failures))
    print("PASS: all checked light-theme semantic text pairs meet WCAG 2.2 AA.")

if __name__ == "__main__":
    main()
