#!/usr/bin/env python3

import json
import re
from pathlib import Path
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[2]
MEM = ROOT / ".secondbrain" / "memory"

def now():
    return datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")

def read(path):
    try:
        return path.read_text("utf-8", errors="ignore")
    except Exception:
        return ""

# ---------------------------------------------------------
# API
# ---------------------------------------------------------

def api_map():
    server = ROOT / "build-service" / "server.js"
    text = read(server)

    route_re = re.compile(
        r'(?:app|router)\s*\.\s*'
        r'(get|post|put|patch|delete|options|head)'
        r'\s*\(\s*'
        r'["\']([^"\']+)["\']',
        re.I | re.S
    )

    rows = []

    for method, path in route_re.findall(text):
        rows.append({
            "method": method.upper(),
            "path": path,
            "source": "build-service/server.js"
        })

    unique = []
    seen = set()

    for item in rows:
        key = (item["method"], item["path"])
        if key not in seen:
            seen.add(key)
            unique.append(item)

    return unique

# ---------------------------------------------------------
# DATABASE
# ---------------------------------------------------------

CREATE_RE = re.compile(
    r'(?i)\bCREATE\s+TABLE\s+'
    r'(?:IF\s+NOT\s+EXISTS\s+)?'
    r'["`]?([A-Za-z0-9_.]+)'
)

ALTER_RE = re.compile(
    r'(?i)\bALTER\s+TABLE\s+'
    r'(?:IF\s+EXISTS\s+)?'
    r'["`]?([A-Za-z0-9_.]+)'
)

def database_map():
    sql_dir = ROOT / "build-service" / "sql"

    migrations = []
    tables = set()

    if not sql_dir.exists():
        return {
            "migrations": [],
            "tables": []
        }

    for path in sorted(sql_dir.glob("*.sql")):
        text = read(path)

        created = sorted(set(CREATE_RE.findall(text)))
        altered = sorted(set(ALTER_RE.findall(text)))

        tables.update(created)
        tables.update(altered)

        migrations.append({
            "file": path.relative_to(ROOT).as_posix(),
            "creates": created,
            "alters": altered
        })

    return {
        "migrations": migrations,
        "tables": sorted(tables)
    }

# ---------------------------------------------------------
# WORKERS
# ---------------------------------------------------------

def worker_map():
    package = ROOT / "build-service" / "package.json"

    try:
        data = json.loads(read(package))
    except Exception:
        data = {}

    scripts = data.get("scripts") or {}

    workers = []

    for name, command in scripts.items():
        low = (name + " " + str(command)).lower()

        if "worker" in low or name == "start":
            workers.append({
                "name": name,
                "command": command
            })

    dockerfiles = sorted(
        p.relative_to(ROOT).as_posix()
        for p in (ROOT / "build-service").glob("Dockerfile*")
        if not p.name.endswith(".bak")
    )

    return {
        "runtime_scripts": workers,
        "dockerfiles": dockerfiles
    }

# ---------------------------------------------------------
# CI
# ---------------------------------------------------------

def workflow_map():
    root = ROOT / ".github" / "workflows"

    if not root.exists():
        return []

    return sorted(
        p.relative_to(ROOT).as_posix()
        for p in root.iterdir()
        if (
            p.is_file()
            and p.suffix in {".yml", ".yaml"}
            and ".bak" not in p.name
        )
    )

# ---------------------------------------------------------
# TESTS
# ---------------------------------------------------------

SKIP_DIRS = {
    ".git",
    ".secondbrain",
    "node_modules",
    ".gradle",
    "build",
    "dist",
    "out",
    ".next",
    "coverage"
}

def test_map():
    results = []

    for path in ROOT.rglob("*"):
        if not path.is_file():
            continue

        parts = set(path.parts)

        if parts & SKIP_DIRS:
            continue

        rp = path.relative_to(ROOT).as_posix()
        low = rp.lower()
        name = path.name.lower()

        if (
            "/test/" in low
            or "/tests/" in low
            or "/androidtest/" in low
            or "test." in name
            or name.endswith("test.kt")
            or name.endswith("tests.kt")
            or name.endswith(".test.js")
        ):
            results.append(rp)

    return sorted(set(results))

# ---------------------------------------------------------
# WEB / DESKTOP
# ---------------------------------------------------------

def surface_map():
    candidates = {
        "android": [
            "android-app"
        ],
        "desktop": [
            "desktop-app",
            "windows-app",
            "electron-app"
        ],
        "web": [
            "web",
            "web-app",
            "studio-web",
            "public"
        ]
    }

    result = {}

    for surface, paths in candidates.items():
        existing = []

        for candidate in paths:
            p = ROOT / candidate
            if p.exists():
                existing.append(candidate)

        result[surface] = {
            "detected": bool(existing),
            "paths": existing
        }

    return result

# ---------------------------------------------------------
# OUTPUT
# ---------------------------------------------------------

def md_table(rows, headers):
    if not rows:
        return "_None detected._\n"

    lines = [
        "| " + " | ".join(headers) + " |",
        "| " + " | ".join("---" for _ in headers) + " |"
    ]

    for row in rows:
        lines.append(
            "| " +
            " | ".join(
                str(x).replace("|", "\\|")
                for x in row
            ) +
            " |"
        )

    return "\n".join(lines) + "\n"

def main():
    MEM.mkdir(parents=True, exist_ok=True)

    api = api_map()
    db = database_map()
    workers = worker_map()
    workflows = workflow_map()
    tests = test_map()
    surfaces = surface_map()

    data = {
        "generated_at": now(),
        "api": api,
        "database": db,
        "workers": workers,
        "workflows": workflows,
        "tests": tests,
        "surfaces": surfaces
    }

    (MEM / "DEEP_MAP.json").write_text(
        json.dumps(
            data,
            indent=2,
            ensure_ascii=False
        ),
        encoding="utf-8"
    )

    api_rows = [
        (
            x["method"],
            x["path"],
            x["source"]
        )
        for x in api
    ]

    migration_rows = []

    for migration in db["migrations"]:
        migration_rows.append((
            migration["file"],
            ", ".join(migration["creates"]) or "-",
            ", ".join(migration["alters"]) or "-"
        ))

    worker_rows = [
        (x["name"], x["command"])
        for x in workers["runtime_scripts"]
    ]

    text = f"""# AppForge Studio — Deep Project Map

Generated: {data["generated_at"]}

## API Routes

{md_table(api_rows, ["Method", "Route", "Source"])}

## Database

Detected tables / schema objects: **{len(db["tables"])}**

{chr(10).join("- `" + x + "`" for x in db["tables"]) or "_None detected._"}

### Migrations

{md_table(migration_rows, ["Migration", "Creates", "Alters"])}

## Worker Runtime

{md_table(worker_rows, ["Script", "Command"])}

### Worker Dockerfiles

{chr(10).join("- `" + x + "`" for x in workers["dockerfiles"]) or "_None detected._"}

## GitHub Actions

{chr(10).join("- `" + x + "`" for x in workflows) or "_None detected._"}

## Tests

Detected test files: **{len(tests)}**

{chr(10).join("- `" + x + "`" for x in tests[:200])}

## Product Surfaces

- Android: `{surfaces["android"]}`
- Web: `{surfaces["web"]}`
- Desktop: `{surfaces["desktop"]}`

## Rules

- This map is generated from repository evidence.
- Missing Web/Desktop paths are reported as missing, not guessed.
- Database schema is derived from SQL migrations.
- Live deployment health is not inferred from this file.
"""

    (MEM / "DEEP_MAP.md").write_text(
        text,
        encoding="utf-8"
    )

    print("PASS - Deep Project Map generated")
    print("API routes:", len(api))
    print("Database tables:", len(db["tables"]))
    print("Migrations:", len(db["migrations"]))
    print("Worker scripts:", len(workers["runtime_scripts"]))
    print("Workflows:", len(workflows))
    print("Tests:", len(tests))
    print("Android:", surfaces["android"]["detected"])
    print("Web:", surfaces["web"]["detected"])
    print("Desktop:", surfaces["desktop"]["detected"])
    print("Output: .secondbrain/memory/DEEP_MAP.md")

if __name__ == "__main__":
    main()
