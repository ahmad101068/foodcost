#!/usr/bin/env python3
"""Summarize recorded native layouts, touch bounds and capture validity.

This reports diagnostics, not a substitute for inspecting the actual PNGs or TalkBack.
Scroll-clipped touch regions remain in the output instead of being silently discarded.
"""
import argparse
import json
from pathlib import Path

from PIL import Image


def audit(matrix: Path):
    rows = []
    for directory in sorted(p for p in matrix.iterdir() if p.is_dir()):
        layouts = []
        failures = []
        raw_overflow = 0
        touches = []
        small = []
        images = []
        for path in sorted(directory.glob("0*-text-layouts.json")):
            for item in json.loads(path.read_text()):
                layouts.append(item)
                raw_overflow += bool(item["overflow"])
                if item["text"] and (
                    not item["glyphWidthFits"] or not item["glyphHeightFits"]
                ):
                    failures.append({"page": path.name, **item})
        for path in sorted(directory.glob("0*-touch-bounds.json")):
            for item in json.loads(path.read_text()):
                left, top, right, bottom = item["visibleBoundsPx"]
                if right <= left or bottom <= top:
                    continue
                touches.append(item)
                if item["touchWidthDp"] < 47.99 or item["touchHeightDp"] < 47.99:
                    small.append({"page": path.name, **item})
        for path in sorted(directory.glob("0*.png")):
            with Image.open(path) as frame:
                extrema = frame.convert("RGB").getextrema()
                images.append(
                    {
                        "file": path.name,
                        "size_px": frame.size,
                        "rgb_extrema": extrema,
                        "black_frame": all(high == 0 for low, high in extrema),
                    }
                )
        log = (directory / "test.log").read_text()
        rows.append(
            {
                "configuration": directory.name,
                "capture_cohort": json.loads((directory / "capture-cohort.json").read_text())
                if (directory / "capture-cohort.json").exists()
                else None,
                "native_test_pass": "OK (1 test)" in log,
                "laid_out_texts": len(layouts),
                "raw_overflow_flags": raw_overflow,
                "glyph_fit_failures": failures,
                "visible_clickable_nodes": len(touches),
                "small_touch_bounds_including_scroll_clips": small,
                "frames": images,
                "device": (directory / "device.txt").read_text(),
            }
        )
    return rows


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--matrix", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    args.out.write_text(
        json.dumps(
            {
                "platform": "API26",
                "cohort": "See capture_cohort per configuration",
                "offline": True,
                "note": "Raw paragraph overflow flags and glyph bounds are both retained. "
                "A short touch region at a scroll viewport edge needs scroll/visual review; "
                "these diagnostics do not certify accessibility.",
                "rows": audit(args.matrix),
            },
            ensure_ascii=False,
            indent=2,
        )
        + "\n"
    )
