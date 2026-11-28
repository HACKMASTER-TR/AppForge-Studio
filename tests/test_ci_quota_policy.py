#!/usr/bin/env python3
"""Contract: optional costly cloud jobs require explicit intent; stability remains mandatory."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]/".github/workflows"
branch="refs/heads/codex/r58-r62-integrated-v579-20261126"
for filename, marker, count in (
    ("r58-r62-integrated-v579-ci.yml","signed",1),
    ("r58-r62-integrated-v579-unsigned-ci.yml","unsigned",1),
    ("r58-r62-module-reference-ci.yml","reference",3),
):
    s=(root/filename).read_text()
    assert s.count("    runs-on:")==count,filename
    assert s.count("contains(github.event.head_commit.message, '[ci "+marker+"]')")==count,filename
    assert s.count("github.event_name == 'workflow_dispatch'")==count,filename
    assert s.count("github.ref == '"+branch+"'")==count,filename
    assert "cancel-in-progress: true" in s,filename
    assert "workflow_dispatch:" in s and "  push:" in s,filename
s=(root/"appforge-stability-gate.yml").read_text()
assert "pull_request:" in s and "cancel-in-progress: true" in s
for job in ("policy","backend-regression","android-regression","static-smoke"):
    assert 'needs.'+job+'.result }}" = "success" ] || FAIL=' in s,job
assert "APPFORGE GLOBAL REGRESSION GATE: BLOCKED" in s
print("CI_QUOTA_POLICY=PASS")
