#!/usr/bin/env python3

from pathlib import Path
from datetime import datetime, timezone
import subprocess
import json
import sys

ROOT = Path("/root/AppForge-Studio")
RUNTIME = ROOT / ".appforge-brain/runtime"
CHECKPOINT = RUNTIME / "checkpoint.json"
SESSIONS = RUNTIME / "sessions.jsonl"
ARCH_RISK = RUNTIME / "architecture-risk.json"

RUNTIME.mkdir(parents=True, exist_ok=True)


def run(*args):
    try:
        return subprocess.check_output(
            args,
            cwd=ROOT,
            text=True,
            stderr=subprocess.DEVNULL
        ).strip()
    except Exception:
        return ""


def load_checkpoint():
    try:
        return json.loads(
            CHECKPOINT.read_text(encoding="utf-8")
        )
    except Exception:
        return {}


def save_checkpoint(data):
    data["checkpointUpdatedAt"] = (
        datetime.now(timezone.utc).isoformat()
    )

    CHECKPOINT.write_text(
        json.dumps(
            data,
            ensure_ascii=False,
            indent=2
        ) + "\n",
        encoding="utf-8"
    )


def clean_status():
    lines = run("git", "status", "--short").splitlines()

    ignored_display = {
        "?? dashboard.sh",
        "?? dashboard.sh.bak",
    }

    return [
        line
        for line in lines
        if line not in ignored_display
    ]


def overview():
    cp = load_checkpoint()

    print("================================")
    print(" SECOND BRAIN PROJECT OVERVIEW")
    print("================================")
    print()

    print("Repo   :", ROOT)
    print("Branch :", run("git", "branch", "--show-current"))
    print("HEAD   :", run("git", "log", "-1", "--oneline"))

    status = clean_status()

    print(
        "Worktree:",
        "CLEAN" if not status else "CHANGED"
    )

    if status:
        for line in status:
            print(" ", line)

    print()
    print(
        "Stage  :",
        cp.get(
            "currentStage",
            "No current stage recorded"
        )
    )

    print(
        "Next   :",
        cp.get(
            "nextAction",
            "No next action recorded"
        )
    )

    print(
        "20K    :",
        cp.get(
            "terminal20kAndroidBuildStatus",
            cp.get(
                "terminal20kLocalBuildStatus",
                "validated"
            )
        )
    )

    print(
        "Push   :",
        cp.get(
            "pushPolicy",
            "blocked"
        ).upper()
    )


def next_action():
    cp = load_checkpoint()

    print("=== SECOND BRAIN NEXT ===")

    value = cp.get("nextAction")

    if value:
        print(value)
    else:
        print(
            "Run brain-test, then continue "
            "the current local development task."
        )

    print()
    print("=== ARCHITECTURE RISK CONTEXT ===")

    try:
        risk = json.loads(
            ARCH_RISK.read_text(encoding="utf-8")
        )
    except Exception:
        risk = {}

    if not risk:
        print(
            "No architecture risk report yet."
        )
        print(
            "Run: .appforge-brain/bin/brain-risk "
            "\"<feature description>\""
        )
        return

    print("Feature :", risk.get("feature", "unknown"))
    print(
        "Risk    :",
        str(risk.get("overallRisk", "?")) + "/10"
    )
    print(
        "Decision:",
        risk.get("decision", "unknown")
    )
    print(
        "Tests   :",
        risk.get("testCoverageRequired", "unknown")
    )

    refs = risk.get("recommendedReferences") or []

    if refs:
        print("References:")
        for ref in refs:
            print(" -", ref)

    gates = risk.get("requiredGates") or []

    if gates:
        print("Required gates:")
        for gate in gates:
            print(" -", gate)

    decision = risk.get("decision")

    if decision == "REFACTOR_FIRST":
        print()
        print(
            "NEXT POLICY: Establish module/test boundaries "
            "before implementing the feature."
        )

    elif decision == "REJECT":
        print()
        print(
            "NEXT POLICY: Redesign the implementation "
            "before source changes."
        )

    elif decision == "ACCEPT":
        print()
        print(
            "NEXT POLICY: Implementation may proceed "
            "with normal safe-development checks."
        )


def session(action, message):
    cp = load_checkpoint()

    item = {
        "timestamp":
            datetime.now(timezone.utc).isoformat(),
        "action":
            action,
        "message":
            message,
        "commit":
            run("git", "rev-parse", "--short", "HEAD"),
        "branch":
            run("git", "branch", "--show-current"),
    }

    with SESSIONS.open(
        "a",
        encoding="utf-8"
    ) as f:
        f.write(
            json.dumps(
                item,
                ensure_ascii=False
            ) + "\n"
        )

    if action == "start":
        cp["activeSession"] = message or "AppForge development"

    elif action == "note":
        cp["lastSessionNote"] = message

    elif action == "end":
        cp["lastSessionResult"] = message or "completed"
        cp.pop("activeSession", None)

    save_checkpoint(cp)

    print(
        "PASS - session",
        action,
        message
    )


def history():
    print("=== SECOND BRAIN SESSION HISTORY ===")

    if not SESSIONS.exists():
        print("No session history.")
        return

    lines = SESSIONS.read_text(
        encoding="utf-8"
    ).splitlines()[-10:]

    for line in lines:
        try:
            item = json.loads(line)

            print(
                item.get("timestamp", ""),
                "|",
                item.get("action", ""),
                "|",
                item.get("message", "")
            )
        except Exception:
            pass


def audit():
    failed = 0

    print("=== SECOND BRAIN PROJECT AUDIT ===")

    safe = ROOT / ".appforge-brain/bin/brain-safe-check"

    rc = subprocess.call(
        [str(safe)],
        cwd=ROOT
    )

    if rc != 0:
        failed += 1

    print()
    print("=== STAGED SAFETY ===")

    staged = run(
        "git",
        "diff",
        "--cached",
        "--name-only"
    ).splitlines()

    forbidden = []

    for name in staged:
        low = name.lower()

        if (
            name in {
                "dashboard.sh",
                "dashboard.sh.bak"
            }
            or low.endswith(".apk")
        ):
            forbidden.append(name)

    if forbidden:
        print("FAIL - forbidden staged files:")
        for name in forbidden:
            print(" ", name)
        failed += 1
    else:
        print("PASS - staged files safe")

    print()
    print("=== PUSH LOCK ===")

    hook = ROOT / ".githooks/pre-push"

    if hook.is_file():
        rc = subprocess.call(
            [str(hook), "origin", "audit"],
            cwd=ROOT,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL
        )

        if rc == 0:
            print("PASS - git push AUTHORIZED")
        else:
            print("FAIL - push authorization disabled")
            failed += 1
    else:
        print("FAIL - push hook missing")
        failed += 1

    print()
    print("==============================")
    print("fail", failed)
    print("==============================")

    if failed == 0:
        print("SECOND BRAIN V4 AUDIT PASS")
        print("LOCAL DEVELOPMENT: READY")
        print("GIT PUSH: AUTHORIZED")

    return failed


def main():
    cmd = (
        sys.argv[1]
        if len(sys.argv) > 1
        else "overview"
    )

    if cmd == "overview":
        overview()

    elif cmd == "next":
        next_action()

    elif cmd == "history":
        history()

    elif cmd == "audit":
        raise SystemExit(audit())

    elif cmd == "session":
        if len(sys.argv) < 3:
            print(
                "usage: project-control.py "
                "session start|note|end [message]"
            )
            raise SystemExit(2)

        action = sys.argv[2]

        if action not in {
            "start",
            "note",
            "end"
        }:
            raise SystemExit(
                "invalid session action"
            )

        message = " ".join(
            sys.argv[3:]
        ).strip()

        session(
            action,
            message
        )

    else:
        raise SystemExit(
            f"Unknown command: {cmd}"
        )


if __name__ == "__main__":
    main()
