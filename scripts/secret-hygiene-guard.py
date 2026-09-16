#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

raw = subprocess.check_output(
    [
        "git",
        "ls-files",
        "-co",
        "--exclude-standard",
        "-z",
    ],
    cwd=ROOT,
)

private_key_pattern = re.compile(
    r"-----BEGIN PRIVATE KEY-----\s+"
    r"[A-Za-z0-9+/=\r\n]{64,}"
    r"-----END PRIVATE KEY-----"
)

errors: list[str] = []

for item in raw.split(b"\0"):
    if not item:
        continue

    rel = item.decode("utf-8", errors="replace")
    path = ROOT / rel

    if not path.is_file():
        continue

    lower = path.name.lower()

    if (
        path.suffix.lower() == ".json"
        and (
            "service-account" in lower
            or "service_account" in lower
        )
    ):
        errors.append(
            f"{rel}: service-account JSON must not live in repository/workspace"
        )
        continue

    try:
        if path.stat().st_size > 2_000_000:
            continue
    except OSError:
        continue

    if path.suffix.lower() == ".json":
        try:
            data = json.loads(
                path.read_text(
                    encoding="utf-8",
                    errors="strict",
                )
            )

            if (
                isinstance(data, dict)
                and data.get("type") == "service_account"
                and data.get("private_key")
            ):
                errors.append(
                    f"{rel}: Google service-account private key detected"
                )
                continue
        except Exception:
            pass

    try:
        text = path.read_text(
            encoding="utf-8",
            errors="ignore",
        )
    except Exception:
        continue

    if private_key_pattern.search(text):
        errors.append(
            f"{rel}: PEM private key material detected"
        )

if errors:
    print("SECRET HYGIENE GUARD: BLOCKED")

    for error in errors:
        print(f" - {error}")

    raise SystemExit(1)

print("SECRET HYGIENE GUARD: PASS")
