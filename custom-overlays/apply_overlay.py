#!/usr/bin/env python3
"""Apply the fork's custom overlay files onto the working tree.

The overlay holds the fork-customized version of a file that upstream also
owns (GmsCoreSupportPatch.java). Upstream never has the `custom-overlays/`
paths, so a sync merge can never conflict on them or wipe them. After a
merge, the workflow copies the overlay files into place (and the marker
script then re-verifies the custom values).

Settings.java is DELIBERATELY NOT an overlay file: it is a full-file
snapshot that rots every time upstream adds a setting. On 2026-11-07/08
the stale v2.0.0 snapshot (1230 lines) overwrote upstream's current
Settings.java (1392 lines) after every sync, deleting ~40 settings that
newer upstream patches reference, and the build failed with 300
"cannot find symbol" errors. The fork's only Settings.java customs are
the four default values (Premium header, swipe left=Brightness,
swipe right=Volume, spoof client=Android Creator), which
scripts/apply_custom_defaults.py already re-applies onto whatever
Settings.java the merge produces. Do NOT add Settings.java back here.

Usage: python3 custom-overlays/apply_overlay.py   (run from the repo root)
"""
import shutil
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
OVERLAY_DIR = REPO_ROOT / "custom-overlays" / "files"

# (overlay relative path -> repo relative path); kept identical by design.
OVERLAY_FILES = [
    "extensions/shared-youtube/library/src/main/java/app/morphe/extension/shared/patches/GmsCoreSupportPatch.java",
]

def main() -> int:
    applied = 0
    for rel in OVERLAY_FILES:
        src = OVERLAY_DIR / rel
        dst = REPO_ROOT / rel
        if not src.exists():
            print(f"Error: overlay file missing: {src}")
            return 1
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)
        print(f"Applied overlay: {rel}")
        applied += 1
    print(f"Overlay applied: {applied} file(s).")
    return 0

if __name__ == "__main__":
    sys.exit(main())
