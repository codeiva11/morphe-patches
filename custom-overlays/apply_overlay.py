#!/usr/bin/env python3
"""Apply the fork's custom overlay files onto the working tree.

The overlay holds the fork-customized versions of files that upstream also
owns (Settings.java, GmsCoreSupportPatch.java). Upstream never has the
`custom-overlays/` paths, so a sync merge can never conflict on them or wipe
them. After a merge, the workflow copies the overlay files into place (and
the marker/defaults scripts then re-verify the custom values).

Usage: python3 custom-overlays/apply_overlay.py   (run from the repo root)
"""
import shutil
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
OVERLAY_DIR = REPO_ROOT / "custom-overlays" / "files"

# (overlay relative path -> repo relative path); kept identical by design.
OVERLAY_FILES = [
    "extensions/youtube/src/main/java/app/morphe/extension/youtube/settings/Settings.java",
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
