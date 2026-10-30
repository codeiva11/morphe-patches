#!/usr/bin/env python3
"""Ensure the fork's MicroG trust markers survive an upstream sync.

After every upstream merge, the fork's GmsCore check must still:
  1. Point the primary MicroG release check at codeiva11/MicroG-RE
     (upstream's MorpheApp URL stays only as the FALLBACK constant).
  2. Trust the fork's tytydraco signing certificate
     (PUBLIC_KEYSTORE_SIGNING_CERT_SHA256) alongside Morphe's official cert.
  3. Never show the MicroG conflict dialog (conflicts are cleared).

If the primary URL was restored to MorpheApp by a merge, this script fixes
just that one line. If any other marker is missing, it FAILS LOUDLY so the
workflow cannot silently build a bundle that shows the "Action needed"
dialog again. Run from the repo root, right after the upstream merge step.
"""
import re
import sys
from pathlib import Path

GMSCORE = Path("extensions/shared-youtube/library/src/main/java/app/morphe/extension/shared/patches/GmsCoreSupportPatch.java")
FORK_URL = "https://api.github.com/repos/codeiva11/MicroG-RE/releases/latest"
UPSTREAM_URL = "https://api.github.com/repos/MorpheApp/MicroG-RE/releases/latest"
FORK_CERT = "50b9116a6db313b20bb4f4b8d683fdb52eb83ca660302f504e9dcf5e858cf404"

def fail(msg):
    print(f"❌ GMS fork markers: {msg}", file=sys.stderr)
    sys.exit(1)

def main() -> int:
    text = GMSCORE.read_text(encoding="utf-8")
    lines = text.splitlines()
    changed = False

    # 1. Primary URL must be the fork. Only the line belonging to
    # MICROG_LATEST_RELEASE_API_URL may be touched (the UPSTREAM fallback
    # constant must keep pointing at MorpheApp).
    for i, line in enumerate(lines):
        if "MICROG_LATEST_RELEASE_API_URL" in line and "private static final String" in line:
            nxt = lines[i + 1] if i + 1 < len(lines) else ""
            if UPSTREAM_URL in nxt:
                lines[i + 1] = nxt.replace(UPSTREAM_URL, FORK_URL)
                changed = True
                print(f"🔧 Restored fork MicroG URL on line {i + 2}")
            elif FORK_URL not in nxt:
                fail(f"MICROG_LATEST_RELEASE_API_URL points at an unexpected URL: {nxt.strip()}")
            break
    else:
        fail("MICROG_LATEST_RELEASE_API_URL constant not found")

    text = "\n".join(lines) + "\n"

    # 2. Fork signing certificate must be present and used.
    if FORK_CERT not in text:
        fail("PUBLIC_KEYSTORE_SIGNING_CERT_SHA256 (tytydraco cert) is missing")
    if "PUBLIC_KEYSTORE_SIGNING_CERT_SHA256" not in text:
        fail("PUBLIC_KEYSTORE_SIGNING_CERT_SHA256 constant is missing")
    if not re.search(r"matchesAnySigningCert[\s\S]{0,400}?PUBLIC_KEYSTORE_SIGNING_CERT_SHA256", text):
        fail("matchesAnySigningCert does not check the fork certificate")

    # 3. Conflict dialog must stay suppressed.
    if "conflicts.clear()" not in text:
        fail("conflicts.clear() is missing — the MicroG conflict dialog could return")
    m = re.search(r"private static boolean isOfficialMicroG\(PackageInfo packageInfo\)\s*\{([^}]*)\}", text)
    if not m or "return true;" not in m.group(1):
        fail("isOfficialMicroG no longer returns true for fork builds")

    if changed:
        GMSCORE.write_text(text, encoding="utf-8")
        print("✅ Fork MicroG URL restored; all markers present")
    else:
        print("✅ All fork MicroG trust markers present")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
