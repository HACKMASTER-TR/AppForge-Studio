#!/usr/bin/env python3
from pathlib import Path
import subprocess
p=Path(__file__).resolve().parents[1]/"scripts/appforge-termux-build.sh"
s=p.read_text()
subprocess.run(["bash","-n",str(p)],check=True)
for marker in ("NATIVE_SOURCE_AND_CONFIG=PASS","PROFILE_VERSION_MISMATCH","PREFLIGHT_PASS",
               "VERIFIED_PASS","ORIGINAL_KEYSTORE=PASS","restore()","trap 'restore' EXIT",
               "git diff --quiet HEAD","termux-clipboard-set","APP_DATA_CHANGED=NO"):
    assert marker in s,marker
assert s.index("restore\ntrap - EXIT")<s.index('touch "$OUT/VERIFIED_PASS"')
assert "mapfile" not in s and "< <(" not in s
assert "git push" not in s and "adb " not in s and "termux-open" not in s
assert 'git diff --cached --quiet HEAD --' in s
assert s.index('cp "$SNAP" "$OUT/second_brain.before-checkout.json"') < s.index('git restore --source=HEAD --worktree -- "$SNAP"') < s.index('git checkout --detach "$COMMIT"')
assert 'python scripts/generate-second-brain-snapshot.py --check' in s
print("TERMUX_BUILD_CONTRACT=PASS")
