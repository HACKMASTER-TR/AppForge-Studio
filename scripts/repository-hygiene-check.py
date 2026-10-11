#!/usr/bin/env python3

from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]


def fail(message: str) -> None:
    print(
        f"FAIL  {message}"
    )
    raise SystemExit(1)


tracked = subprocess.check_output(
    [
        "git",
        "ls-files",
    ],
    cwd=ROOT,
    text=True,
).splitlines()


bad_suffixes = (
    ".bak",
    ".tmp",
    ".temp",
    ".orig",
    ".rej",
    ".swp",
    ".save",
)

bad_exact = {
    "worker-log.txt",
    "AppForgeStudio-latest.apk",
}

bad = []

for rel in tracked:
    name = Path(rel).name

    if (
        rel in bad_exact
        or name.endswith(
            bad_suffixes
        )
        or name.endswith("~")
    ):
        bad.append(rel)


if bad:
    print(
        "Tracked generated/temp artifacts:"
    )

    for rel in bad:
        print(
            f"  {rel}"
        )

    fail(
        "generated/temp artifacts are tracked"
    )


retired_exact = {
    ".github/workflows/worker-image.yml",
    ".github/workflows/source-worker-image.yml",
    ".github/workflows/worker-autoscale.yml",
    ".github/scripts/railway_production.py",
}

retired = [
    rel
    for rel in tracked
    if (
        rel.startswith(
            "build-service/"
        )
        or rel in retired_exact
    )
]

if retired:
    print(
        "Retired provider paths:"
    )

    for rel in retired:
        print(
            f"  {rel}"
        )

    fail(
        "retired provider source returned"
    )


ignore_path = ROOT / ".gitignore"

if not ignore_path.is_file():
    fail(
        ".gitignore missing"
    )


ignore = set(
    ignore_path.read_text(
        encoding="utf-8"
    ).splitlines()
)

required_ignore = {
    "*.bak",
    "/worker-log.txt",
    "/AppForgeStudio-latest.apk",
    "*.tmp",
    "*.temp",
    "*.orig",
    "*.rej",
    "*.swp",
    "*.save",
    "*~",
}

missing_ignore = sorted(
    required_ignore - ignore
)

if missing_ignore:
    print(
        "Missing .gitignore rules:"
    )

    for item in missing_ignore:
        print(
            f"  {item}"
        )

    fail(
        "repository hygiene ignore rules incomplete"
    )


print(
    "REPOSITORY_HYGIENE_TRACKED_ARTIFACTS=PASS"
)

print(
    "REPOSITORY_HYGIENE_RETIRED_PATHS=PASS"
)

print(
    "REPOSITORY_HYGIENE_GITIGNORE=PASS"
)

print(
    "REPOSITORY_HYGIENE=PASS"
)
