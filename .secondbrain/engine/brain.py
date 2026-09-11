#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import time
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SB = ROOT / ".secondbrain"
MEM = SB / "memory"
REPORTS = SB / "reports"
RUNTIME = SB / "runtime"
CHECKPOINTS = SB / "checkpoints"
DEEP_MAP_ENGINE = SB / "engine" / "deep_map.py"
DEEP_MAP_JSON = MEM / "DEEP_MAP.json"

TEXT_EXTS = {
    ".kt", ".kts", ".java", ".js", ".mjs", ".cjs", ".ts", ".tsx", ".jsx",
    ".py", ".sh", ".ps1", ".json", ".yaml", ".yml", ".toml", ".xml",
    ".gradle", ".properties", ".md", ".html", ".css", ".scss", ".sql"
}

LANG = {
    ".kt": "Kotlin", ".kts": "Kotlin",
    ".java": "Java",
    ".js": "JavaScript", ".mjs": "JavaScript", ".cjs": "JavaScript",
    ".ts": "TypeScript", ".tsx": "TypeScript", ".jsx": "JavaScript",
    ".py": "Python", ".sh": "Shell",
    ".json": "JSON", ".yaml": "YAML", ".yml": "YAML",
    ".sql": "SQL", ".md": "Markdown",
    ".html": "HTML", ".css": "CSS"
}

IGNORE_PREFIXES = (
    ".git/",
    ".secondbrain/runtime/",
    ".secondbrain/reports/",
    "node_modules/",
    "android-app/.gradle/",
    "android-app/build/",
    "build-service/node_modules/",
    "dist/",
    "out/"
)

BRAIN_PATHS = (
    ".secondbrain/",
    "AGENTS.md",
    "SECOND_BRAIN.md",
    "scripts/brain",
    "docs/wiki/",
    ".appforge-brain/",
    "scripts/wiki-audit.mjs",
    "scripts/wiki-secret-scan.py",
    "scripts/wiki-prune-report.py",
)

def now():
    return datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")

def run(args, check=False):
    p = subprocess.run(
        args,
        cwd=ROOT,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE
    )
    if check and p.returncode:
        raise RuntimeError(p.stderr.strip() or "command failed")
    return p

def git(*args):
    return run(["git", *args]).stdout.strip()

def safe_read(path: Path, limit=2_000_000):
    try:
        if not path.is_file() or path.stat().st_size > limit:
            return ""
        return path.read_text("utf-8", errors="ignore")
    except Exception:
        return ""

def rel(path: Path):
    return path.relative_to(ROOT).as_posix()

def iter_project_files():
    skip_dirs = {
        ".git",
        ".secondbrain",
        "node_modules",
        ".gradle",
        ".idea",
        ".vscode",
        "build",
        "dist",
        "out",
        ".next",
        ".cache",
        "coverage",
        "__pycache__",
        "vendor",
        "tmp",
        "temp"
    }

    for current, dirs, files in os.walk(ROOT):
        current_path = Path(current)

        dirs[:] = [
            d for d in dirs
            if d not in skip_dirs
            and not (
                current_path == SB
                and d in {"runtime", "reports"}
            )
        ]

        for name in files:
            yield current_path / name


def source_files():
    out = []

    for p in iter_project_files():
        try:
            rp = rel(p)
        except Exception:
            continue

        if (
            p.suffix.lower() in TEXT_EXTS
            or p.name in {"Dockerfile", "Makefile", "gradlew"}
            or p.name.startswith("Dockerfile.")
        ):
            out.append(p)

    return out

CORE_UNTRACKED_PREFIXES = (
    "android-app/",
    "build-service/",
    "desktop-app/",
    ".github/",
    "scripts/",
    "web-app/",
    "web/"
)


def is_brain_path(name):
    normalized = str(name).replace("\\", "/").strip()

    while normalized.startswith("./"):
        normalized = normalized[2:]

    variants = {normalized}

    # Bazı terminal/git ortamlarında ".secondbrain" başındaki nokta
    # normalize edilmiş biçimde dönebiliyor. İki formu da brain kabul et.
    if normalized == "secondbrain":
        variants.add(".secondbrain")

    if normalized.startswith("secondbrain/"):
        variants.add("." + normalized)

    for candidate in variants:
        if (
            candidate == ".secondbrain"
            or candidate.startswith(".secondbrain/")
        ):
            return True

        if any(
            candidate == x
            or candidate.startswith(x)
            for x in BRAIN_PATHS
        ):
            return True

    return False


def is_analyzable_path(name):
    p = Path(name)

    if p.suffix.lower() in TEXT_EXTS:
        return True

    if p.name in {"Dockerfile", "Makefile", "gradlew"}:
        return True

    if p.name.startswith("Dockerfile."):
        return True

    return False


def changed_files():
    result = []
    raw = git("status", "--porcelain=v1")

    for line in raw.splitlines():
        if len(line) < 4:
            continue

        status = line[:2]
        name = line[3:]

        if " -> " in name:
            name = name.split(" -> ", 1)[1]

        name = name.strip('"')

        if is_brain_path(name):
            continue

        if not is_analyzable_path(name):
            continue

        # Yeni/untracked bir dosya yalnız gerçek proje alanlarından birindeyse
        # impact/risk analizine katılır.
        if status == "??":
            if not name.startswith(CORE_UNTRACKED_PREFIXES):
                continue

        result.append(name)

    return sorted(set(result))


def changed_numstat():
    paths = changed_files()

    tracked = []

    for name in paths:
        result = run([
            "git",
            "ls-files",
            "--error-unmatch",
            "--",
            name
        ])

        if result.returncode == 0:
            tracked.append(name)

    if not tracked:
        return 0, 0

    p = run([
        "git",
        "diff",
        "HEAD",
        "--numstat",
        "--",
        *tracked
    ])

    total_add = 0
    total_del = 0

    for line in p.stdout.splitlines():
        parts = line.split("\t")

        if len(parts) < 3:
            continue

        try:
            a = int(parts[0])
            d = int(parts[1])
        except ValueError:
            continue

        total_add += a
        total_del += d

    return total_add, total_del


def domains_for(paths):
    d = set()
    for p in paths:
        q = p.lower()
        if q.startswith("android-app/"):
            d.add("android")
        if "/terminal/" in q or "terminal" in q:
            d.add("terminal")
        if q.startswith("build-service/"):
            d.add("build-service")
        if "worker" in q:
            d.add("workers")
        if q.startswith(".github/workflows/"):
            d.add("ci")
        if "dockerfile" in q or "docker-compose" in q:
            d.add("containers")
        if any(x in q for x in ("auth", "token", "login", "session", "jwt")):
            d.add("auth")
        if any(x in q for x in ("ssh", "credential", "keystore", "signing")):
            d.add("credentials")
        if any(x in q for x in ("database", "migration", ".sql", "postgres", "redis")):
            d.add("database")
        if any(x in q for x in ("desktop", "windows", "electron")):
            d.add("desktop")
        if any(x in q for x in ("web", "pwa", "frontend")):
            d.add("web")
    return sorted(d)

def scan_data():
    files = source_files()
    langs = Counter()
    lines = 0
    largest = []

    for p in files:
        langs[LANG.get(p.suffix.lower(), "Other")] += 1
        try:
            size = p.stat().st_size
        except OSError:
            size = 0

        text = safe_read(p)
        if text:
            lines += text.count("\n") + 1
        largest.append((size, rel(p)))

    largest.sort(reverse=True)

    return {
        "generated_at": now(),
        "files": len(files),
        "estimated_lines": lines,
        "languages": dict(langs.most_common()),
        "largest_text_files": [
            {"path": p, "bytes": s}
            for s, p in largest[:20]
        ]
    }

def dependency_data(changes=None):
    changes = changes or changed_files()
    imports = {}
    patterns = [
        re.compile(r'^\s*import\s+([A-Za-z0-9_.$*]+)', re.M),
        re.compile(r'require\(["\']([^"\']+)["\']\)'),
        re.compile(r'from\s+["\']([^"\']+)["\']'),
        re.compile(r'import\s+.*?from\s+["\']([^"\']+)["\']')
    ]

    for name in changes:
        p = ROOT / name
        text = safe_read(p)
        if not text:
            continue
        found = []
        for pattern in patterns:
            found.extend(pattern.findall(text))
        if found:
            imports[name] = sorted(set(found))[:100]

    return imports

def impact_data():
    changes = changed_files()
    domains = domains_for(changes)
    imports = dependency_data(changes)

    refs = {}
    all_files = source_files()

    for changed in changes[:30]:
        stem = Path(changed).stem
        if len(stem) < 5:
            continue
        hits = []
        for p in all_files:
            rp = rel(p)
            if rp == changed:
                continue
            text = safe_read(p, 500_000)
            if stem in text:
                hits.append(rp)
                if len(hits) >= 20:
                    break
        if hits:
            refs[changed] = hits

    return {
        "generated_at": now(),
        "changed_files": changes,
        "domains": domains,
        "imports": imports,
        "possible_dependents": refs
    }

def risk_data():
    impact = impact_data()
    changes = impact["changed_files"]
    domains = impact["domains"]
    additions, deletions = changed_numstat()
    delta = additions + deletions

    score = 0
    reasons = []

    if len(changes) >= 5:
        score += 8
        reasons.append("multiple source files changed")
    if len(changes) >= 15:
        score += 12
        reasons.append("wide change surface")
    if len(changes) >= 30:
        score += 15
        reasons.append("very wide change surface")

    if delta >= 300:
        score += 8
        reasons.append("large diff")
    if delta >= 1000:
        score += 15
        reasons.append("very large diff")

    weights = {
        "terminal": 12,
        "workers": 12,
        "auth": 18,
        "credentials": 22,
        "database": 14,
        "ci": 10,
        "containers": 8,
        "desktop": 6,
        "web": 6,
        "android": 5,
        "build-service": 6
    }

    for d in domains:
        w = weights.get(d, 0)
        score += w
        if w:
            reasons.append(f"{d} subsystem affected")

    sensitive = (
        "keystore", "password", "secret", "privatekey",
        "jwt", "authorization", "ssh", "billing"
    )
    for name in changes:
        q = name.lower()
        if any(x in q for x in sensitive):
            score += 8
            reasons.append(f"sensitive path: {name}")
            break

    score = min(score, 100)

    if score <= 34:
        decision = "ACCEPT"
    elif score <= 69:
        decision = "REFACTOR_FIRST"
    else:
        decision = "REJECT"

    return {
        "generated_at": now(),
        "score": score,
        "decision": decision,
        "changed_files": len(changes),
        "added_lines": additions,
        "deleted_lines": deletions,
        "domains": domains,
        "reasons": sorted(set(reasons))
    }

PRIVATE_KEY_RE = re.compile(
    r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"
)

AWS_KEY_RE = re.compile(
    r"\bAKIA[0-9A-Z]{16}\b"
)

GITHUB_TOKEN_RE = re.compile(
    r"\bgh[pousr]_[A-Za-z0-9_]{20,}\b"
)

GENERIC_SECRET_RE = re.compile(
    r"""(?im)^\s*["']?(?P<name>[A-Za-z_][A-Za-z0-9_]*(?:api[_-]?key|secret|token|password|access[_-]?token|private[_-]?key)[A-Za-z0-9_]*)["']?\s*[:=]\s*(?P<quote>["'])(?P<value>[^"'\\n]{8,})(?P=quote)\s*[,;]?\s*$"""
)


def fixture_or_test_path(rp):
    q = "/" + rp.lower().strip("/") + "/"

    return any(x in q for x in (
        "/test/",
        "/tests/",
        "/androidtest/",
        "/fixtures/",
        "/fixture/",
        "/examples/",
        "/example/"
    ))


def obvious_placeholder(value):
    v = value.strip().lower()

    markers = (
        "example",
        "dummy",
        "fake",
        "sample",
        "placeholder",
        "changeme",
        "change-me",
        "your_",
        "your-",
        "your ",
        "test-token",
        "test_token",
        "test-secret",
        "test_secret",
        "mock",
        "not-a-real",
        "not_real"
    )

    if any(x in v for x in markers):
        return True

    if (
        v.startswith("${")
        or v.startswith("$")
        or v.startswith("<")
        or v.startswith("process.env")
        or v.startswith("system.getenv")
        or v.startswith("os.getenv")
    ):
        return True

    return False


def security_data(full=False):
    files = (
        source_files()
        if full
        else [
            ROOT / x
            for x in changed_files()
            if (ROOT / x).is_file()
        ]
    )

    findings = []

    for p in files:
        try:
            rp = rel(p)
        except Exception:
            continue

        low_path = rp.lower()

        if (
            low_path.endswith(".env.example")
            or low_path.endswith(".env.sample")
            or low_path.endswith(".env.template")
        ):
            continue

        text = safe_read(p)
        if not text:
            continue

        if PRIVATE_KEY_RE.search(text):
            findings.append({
                "severity": "HIGH",
                "type": "private_key",
                "file": rp
            })

        if AWS_KEY_RE.search(text):
            if not fixture_or_test_path(rp):
                findings.append({
                    "severity": "HIGH",
                    "type": "aws_key",
                    "file": rp
                })

        for token in GITHUB_TOKEN_RE.findall(text):
            if fixture_or_test_path(rp):
                continue
            if obvious_placeholder(token):
                continue

            findings.append({
                "severity": "HIGH",
                "type": "github_token",
                "file": rp
            })

        for match in GENERIC_SECRET_RE.finditer(text):
            value = match.group("value")

            if fixture_or_test_path(rp):
                continue
            if obvious_placeholder(value):
                continue

            findings.append({
                "severity": "HIGH",
                "type": "hardcoded_secret_literal",
                "file": rp
            })

        low = text.lower()

        if "stricthostkeychecking=no" in low:
            findings.append({
                "severity": "MEDIUM",
                "type": "ssh_host_check_disabled",
                "file": rp
            })

        if re.search(r'(?i)\bverify\s*[:=]\s*false\b', text):
            findings.append({
                "severity": "MEDIUM",
                "type": "certificate_verification_disabled",
                "file": rp
            })

    unique = []
    seen = set()

    for item in findings:
        key = (
            item["severity"],
            item["type"],
            item["file"]
        )

        if key not in seen:
            seen.add(key)
            unique.append(item)

    return {
        "generated_at": now(),
        "scope": "full" if full else "changed",
        "findings": unique,
        "health": (
            "RED"
            if any(x["severity"] == "HIGH" for x in unique)
            else "YELLOW"
            if unique
            else "GREEN"
        )
    }

def discover_tests():
    files = []

    for p in iter_project_files():
        try:
            rp = rel(p)
        except Exception:
            continue

        q = p.name.lower()
        low = rp.lower()

        if (
            "test" in q
            or "/test/" in low
            or "/tests/" in low
            or "/androidtest/" in low
        ):
            files.append(rp)

    return sorted(files)[:300]

def test_plan_data():
    impact = impact_data()
    domains = impact["domains"]
    commands = []

    if "build-service" in domains or "workers" in domains:
        if (ROOT / "build-service/package.json").exists():
            commands.append("cd build-service && npm test")

    if "android" in domains or "terminal" in domains:
        if (ROOT / "android-app/gradlew").exists():
            commands.append("cd android-app && ./gradlew test")
        else:
            commands.append("Android CI / Gradle compile verification")

    if "ci" in domains:
        commands.append("Validate affected GitHub Actions workflow")

    if "containers" in domains:
        commands.append("Validate affected Docker image/build workflow")

    if "auth" in domains or "credentials" in domains:
        commands.append("Run authentication/credential regression tests")

    if not commands:
        commands.append("Run tests for the nearest affected module")

    return {
        "generated_at": now(),
        "domains": domains,
        "recommended_commands": commands,
        "discovered_test_files": discover_tests()
    }

def task_plan_data():
    impact = impact_data()
    risk = risk_data()
    tests = test_plan_data()

    steps = [
        "Verify expected behavior against current source and runtime contract.",
        "Inspect directly affected files and possible dependents."
    ]

    if risk["decision"] == "REFACTOR_FIRST":
        steps.append("Reduce change surface or isolate risky coupling before implementation.")
    elif risk["decision"] == "REJECT":
        steps.append("Do not proceed as one change; split or redesign the risky change first.")

    if any(d in impact["domains"] for d in ("auth", "credentials")):
        steps.append("Verify credential boundaries and avoid logging secret values.")

    if "database" in impact["domains"]:
        steps.append("Verify schema/migration compatibility and rollback behavior.")

    steps += [
        "Implement the smallest safe change.",
        "Run the recommended tests.",
        "Review diff for accidental files, secrets and generated artifacts.",
        "Sync durable Second Brain memory only if architecture or behavior changed."
    ]

    return {
        "generated_at": now(),
        "risk": risk,
        "impact": impact,
        "tests": tests,
        "steps": steps
    }

def extract_api():
    endpoints = []
    candidates = [
        ROOT / "build-service/server.js",
        ROOT / "build-service/server.mjs"
    ]

    route_re = re.compile(
        r'(?:app|router)\.(get|post|put|patch|delete|options|head)'
        r'\s*\(\s*[\'"`]([^\'"`]+)[\'"`]',
        re.I
    )

    for p in candidates:
        text = safe_read(p)
        for method, path in route_re.findall(text):
            endpoints.append({
                "method": method.upper(),
                "path": path,
                "source": rel(p)
            })

    return endpoints

def extract_env_names():
    names = set()
    for p in [
        ROOT / "build-service/.env.example",
        ROOT / ".env.example"
    ]:
        text = safe_read(p)
        for line in text.splitlines():
            m = re.match(r"^\s*([A-Z][A-Z0-9_]+)\s*=", line)
            if m:
                names.add(m.group(1))
    return sorted(names)

def extract_db():
    tables = set()
    sources = set()

    patterns = [
        re.compile(r'(?i)\bcreate\s+table\s+(?:if\s+not\s+exists\s+)?["`]?([A-Za-z0-9_.-]+)'),
        re.compile(r'(?i)\bfrom\s+["`]?([A-Za-z0-9_.-]+)["`]?\b'),
        re.compile(r'(?i)\binto\s+["`]?([A-Za-z0-9_.-]+)["`]?\b'),
        re.compile(r'(?i)\bupdate\s+["`]?([A-Za-z0-9_.-]+)["`]?\b')
    ]

    for p in source_files():
        rp = rel(p)
        text = safe_read(p, 600_000)
        if not text:
            continue
        low = text.lower()
        if (
            p.suffix.lower() == ".sql" or
            "pool.query" in low or
            "create table" in low or
            "postgres" in low
        ):
            sources.add(rp)
            for pattern in patterns:
                for name in pattern.findall(text):
                    if len(name) <= 80:
                        tables.add(name)

    return {
        "tables_or_candidates": sorted(tables),
        "source_files": sorted(sources)
    }

def integration_data():
    package = {}
    p = ROOT / "build-service/package.json"
    try:
        package = json.loads(safe_read(p))
    except Exception:
        pass

    deps = sorted((package.get("dependencies") or {}).keys())

    known = []
    checks = {
        "AWS/S3": lambda x: x.startswith("@aws-sdk/"),
        "Sentry": lambda x: x.startswith("@sentry/"),
        "Google APIs": lambda x: x == "googleapis",
        "PostgreSQL": lambda x: x == "pg",
        "Redis": lambda x: x == "redis",
        "Email": lambda x: x == "nodemailer",
        "JWT": lambda x: x == "jsonwebtoken"
    }

    for name, fn in checks.items():
        if any(fn(x) for x in deps):
            known.append(name)

    return {
        "generated_at": now(),
        "detected_services": known,
        "environment_variable_names": extract_env_names(),
        "note": "Only variable names are mapped. Values are never stored."
    }

def release_data():
    branch = git("branch", "--show-current")
    head = git("rev-parse", "--short", "HEAD")
    status = git("status", "--short")
    versions = {}

    p = ROOT / "build-service/package.json"
    try:
        package = json.loads(safe_read(p))
        versions["build-service"] = package.get("version")
    except Exception:
        pass

    gradle_files = list((ROOT / "android-app").rglob("*.gradle.kts"))
    version_name_re = re.compile(r'versionName\s*=\s*"([^"]+)"')
    version_code_re = re.compile(r'versionCode\s*=\s*(\d+)')

    for gf in gradle_files:
        text = safe_read(gf)
        m1 = version_name_re.search(text)
        m2 = version_code_re.search(text)
        if m1 or m2:
            versions["android"] = {
                "versionName": m1.group(1) if m1 else None,
                "versionCode": int(m2.group(1)) if m2 else None,
                "source": rel(gf)
            }
            break

    return {
        "generated_at": now(),
        "branch": branch,
        "head": head,
        "working_tree_clean": not bool(status),
        "versions": versions,
        "live_production_health": "NOT_CHECKED",
        "note": "Live GitHub/Railway/Sentry health requires an authenticated live source."
    }

def performance_data():
    t0 = time.perf_counter()
    scan = scan_data()
    scan_ms = round((time.perf_counter() - t0) * 1000, 2)

    t1 = time.perf_counter()
    run(["git", "status", "--short"])
    git_status_ms = round((time.perf_counter() - t1) * 1000, 2)

    return {
        "generated_at": now(),
        "source_files": scan["files"],
        "estimated_lines": scan["estimated_lines"],
        "brain_scan_ms": scan_ms,
        "git_status_ms": git_status_ms,
        "app_build_p50": "NOT_MEASURED",
        "app_build_p95": "NOT_MEASURED",
        "worker_job_p50": "NOT_MEASURED",
        "worker_job_p95": "NOT_MEASURED",
        "terminal_latency": "NOT_MEASURED"
    }

def md_table(rows, headers):
    if not rows:
        return "_None detected._\n"
    out = [
        "| " + " | ".join(headers) + " |",
        "| " + " | ".join("---" for _ in headers) + " |"
    ]
    for row in rows:
        out.append("| " + " | ".join(str(x).replace("|", "\\|") for x in row) + " |")
    return "\n".join(out) + "\n"

def write_memory():
    MEM.mkdir(parents=True, exist_ok=True)

    scan = scan_data()
    impact = impact_data()
    risk = risk_data()
    tests = test_plan_data()
    api = extract_api()
    db = extract_db()
    integrations = integration_data()
    security = security_data(full=False)
    release = release_data()
    perf = performance_data()
    deep = run_deep_map()

    branch = release["branch"]
    head = release["head"]

    (MEM / "INDEX.md").write_text(f"""# Second Brain V2 — Index

Generated: {now()}

## Core memory

- HOT_CONTEXT.md
- ARCHITECTURE.md
- BACKEND_API.md
- DATABASE.md
- TESTS.md
- INTEGRATIONS.md
- SECURITY.md
- PERFORMANCE.md
- RELEASES.md
- DEEP_MAP.md
- DEEP_MAP.json
- DECISIONS.md
- BUGS.md

Source code, configuration, tests and runtime behavior remain authoritative.
""", encoding="utf-8")

    (MEM / "HOT_CONTEXT.md").write_text(f"""# Hot Context

Updated: {now()}

- Branch: `{branch}`
- HEAD: `{head}`
- Current risk decision: **{risk["decision"]}**
- Risk score: **{risk["score"]}/100**
- Changed source files: **{len(impact["changed_files"])}**
- Impacted domains: {", ".join(impact["domains"]) or "none detected"}
- Security health: **{security["health"]}**
- Live production health: **NOT_CHECKED**

## Rule

Never infer live deployment health from repository state alone.
""", encoding="utf-8")

    langs = "\n".join(
        f"- {k}: {v} files"
        for k, v in scan["languages"].items()
    )

    (MEM / "ARCHITECTURE.md").write_text(f"""# Architecture Map

Generated from current repository source: {now()}

## Repository baseline

- Text/source files: {scan["files"]}
- Estimated lines: {scan["estimated_lines"]}

## Languages

{langs or "- None detected"}

## Core surfaces

- `android-app/`: Android Studio application and embedded terminal surface.
- `build-service/`: API/build workers and build infrastructure.
- `.github/workflows/`: CI/release automation when present.
- `.secondbrain/`: project analysis and durable engineering memory.

This page is generated from repository structure and must not override source.
""", encoding="utf-8")

    endpoint_rows = [
        (x["method"], x["path"], x["source"])
        for x in api
    ]

    (MEM / "BACKEND_API.md").write_text(
        "# Backend / API Map\n\n"
        f"Generated: {now()}\n\n"
        "## Detected endpoints\n\n" +
        md_table(endpoint_rows, ["Method", "Path", "Source"]) +
        "\n## Worker/runtime evidence\n\n"
        "- Inspect `build-service/package.json` scripts for server and worker entry points.\n"
        "- Inspect Dockerfiles and compose files for deployment topology.\n",
        encoding="utf-8"
    )

    (MEM / "DATABASE.md").write_text(
        "# Database Map\n\n"
        f"Generated: {now()}\n\n"
        "## Detected table/query candidates\n\n" +
        "\n".join(f"- `{x}`" for x in db["tables_or_candidates"]) +
        ("\n" if db["tables_or_candidates"] else "_None automatically confirmed._\n") +
        "\n## Database-related source files\n\n" +
        "\n".join(f"- `{x}`" for x in db["source_files"]) +
        ("\n" if db["source_files"] else "_None automatically detected._\n") +
        "\nAuto-detection is advisory; verify SQL/schema behavior from source and migrations.\n",
        encoding="utf-8"
    )

    (MEM / "TESTS.md").write_text(
        "# Test Map\n\n"
        f"Generated: {now()}\n\n"
        "## Recommended for current change\n\n" +
        "\n".join(f"- `{x}`" for x in tests["recommended_commands"]) +
        "\n\n## Discovered test files\n\n" +
        "\n".join(f"- `{x}`" for x in tests["discovered_test_files"]) +
        "\n",
        encoding="utf-8"
    )

    (MEM / "INTEGRATIONS.md").write_text(
        "# Integration Map\n\n"
        f"Generated: {now()}\n\n"
        "## Detected services\n\n" +
        "\n".join(f"- {x}" for x in integrations["detected_services"]) +
        "\n\n## Environment variable names\n\n" +
        "\n".join(f"- `{x}`" for x in integrations["environment_variable_names"]) +
        "\n\nValues are intentionally never recorded.\n",
        encoding="utf-8"
    )

    (MEM / "SECURITY.md").write_text(
        "# Security Memory\n\n"
        f"Generated: {now()}\n\n"
        f"Current changed-file scan: **{security['health']}**\n\n"
        "Second Brain scans for obvious secret exposure and selected unsafe patterns. "
        "It is not a replacement for a dedicated security review.\n",
        encoding="utf-8"
    )

    (MEM / "PERFORMANCE.md").write_text(
        "# Performance Baseline\n\n"
        f"Generated: {now()}\n\n"
        f"- Source files: {perf['source_files']}\n"
        f"- Estimated lines: {perf['estimated_lines']}\n"
        f"- Second Brain scan: {perf['brain_scan_ms']} ms\n"
        f"- Git status: {perf['git_status_ms']} ms\n"
        "- App build P50/P95: NOT_MEASURED\n"
        "- Worker job P50/P95: NOT_MEASURED\n"
        "- Terminal latency: NOT_MEASURED\n\n"
        "Do not invent performance numbers. Record real measurements when available.\n",
        encoding="utf-8"
    )

    (MEM / "RELEASES.md").write_text(
        "# Release Health\n\n"
        f"Updated: {now()}\n\n"
        f"- Branch: `{release['branch']}`\n"
        f"- HEAD: `{release['head']}`\n"
        f"- Working tree clean: `{release['working_tree_clean']}`\n"
        f"- Local versions: `{json.dumps(release['versions'], ensure_ascii=False)}`\n"
        "- Live production health: `NOT_CHECKED`\n\n"
        "Use authenticated live systems for GitHub Actions, Railway and monitoring health.\n",
        encoding="utf-8"
    )

    for name, title in [
        ("DECISIONS.md", "Architecture Decisions"),
        ("BUGS.md", "Significant Bugs And Fixes")
    ]:
        p = MEM / name
        if not p.exists():
            p.write_text(
                f"# {title}\n\n"
                "This file is durable human/agent memory and is not overwritten by auto-sync.\n",
                encoding="utf-8"
            )

    return {
        "scan": scan,
        "impact": impact,
        "risk": risk,
        "tests": tests,
        "security": security,
        "release": release,
        "performance": perf,
        "deep_map": deep
    }

def checkpoint(label):
    CHECKPOINTS.mkdir(parents=True, exist_ok=True)
    data = {
        "created_at": now(),
        "label": label,
        "branch": git("branch", "--show-current"),
        "head": git("rev-parse", "--short", "HEAD"),
        "impact": impact_data(),
        "risk": risk_data(),
        "test_plan": test_plan_data()
    }

    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    safe = re.sub(r"[^A-Za-z0-9._-]+", "-", label).strip("-") or "checkpoint"
    p = CHECKPOINTS / f"{stamp}-{safe}.json"
    p.write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding="utf-8")
    return p

def latest_report(label="analysis"):
    REPORTS.mkdir(parents=True, exist_ok=True)

    impact = impact_data()
    risk = risk_data()
    sec = security_data()
    tests = test_plan_data()
    plan = task_plan_data()
    release = release_data()
    perf = performance_data()
    deep = load_deep_map()

    data = {
        "generated_at": now(),
        "label": label,
        "impact": impact,
        "risk": risk,
        "security": sec,
        "tests": tests,
        "plan": plan,
        "release": release,
        "performance": perf,
        "deep_map": deep
    }

    (REPORTS / "LATEST.json").write_text(
        json.dumps(data, indent=2, ensure_ascii=False),
        encoding="utf-8"
    )

    md = [
        "# Second Brain V2 — Latest Report",
        "",
        f"Generated: {data['generated_at']}",
        f"Label: `{label}`",
        "",
        f"## Decision: {risk['decision']}",
        "",
        f"Risk score: **{risk['score']}/100**",
        f"Security: **{sec['health']}**",
        f"Changed source files: **{len(impact['changed_files'])}**",
        f"Domains: {', '.join(impact['domains']) or 'none'}",
        "",
        "## Risk reasons"
    ]

    md += [f"- {x}" for x in risk["reasons"]] or ["- No elevated risk reasons."]
    md += ["", "## Test plan"]
    md += [f"- {x}" for x in tests["recommended_commands"]]
    md += ["", "## Task plan"]
    md += [f"{i}. {x}" for i, x in enumerate(plan["steps"], 1)]
    md += [
        "",
        "## Release",
        f"- Branch: `{release['branch']}`",
        f"- HEAD: `{release['head']}`",
        "- Live production health: `NOT_CHECKED`",
        "",
        "## Deep Project Map",
        (
            f"- API routes: {len((deep or {}).get('api', []))}"
        ),
        (
            "- Database tables: "
            f"{len(((deep or {}).get('database') or {}).get('tables', []))}"
        ),
        (
            "- SQL migrations: "
            f"{len(((deep or {}).get('database') or {}).get('migrations', []))}"
        ),
        (
            "- Worker runtime scripts: "
            f"{len(((deep or {}).get('workers') or {}).get('runtime_scripts', []))}"
        ),
        (
            "- GitHub workflows: "
            f"{len((deep or {}).get('workflows', []))}"
        ),
        (
            "- Test files: "
            f"{len((deep or {}).get('tests', []))}"
        ),
        "",
        "## Performance",
        f"- Brain scan: {perf['brain_scan_ms']} ms",
        "- Application build P50/P95: NOT_MEASURED",
        "- Worker P50/P95: NOT_MEASURED"
    ]

    (REPORTS / "LATEST.md").write_text("\n".join(md) + "\n", encoding="utf-8")
    return data

def doctor():
    checks = {
        "git repository": (ROOT / ".git").exists(),
        "android-app": (ROOT / "android-app").is_dir(),
        "build-service": (ROOT / "build-service").is_dir(),
        "README": (ROOT / "README.md").is_file(),
        "old .appforge-brain absent": not (ROOT / ".appforge-brain").exists(),
        "old docs/wiki absent": not (ROOT / "docs/wiki").exists(),
        "engine": Path(__file__).exists(),
        "deep-map engine": DEEP_MAP_ENGINE.is_file()
    }

    ok = True
    for name, result in checks.items():
        print(("PASS" if result else "FAIL"), "-", name)
        ok = ok and result

    print("Python:", sys.version.split()[0])
    print("Second Brain:", "2.1.0")
    return 0 if ok else 1

def run_deep_map():
    if not DEEP_MAP_ENGINE.is_file():
        raise RuntimeError(
            "Deep Map engine missing: "
            + rel(DEEP_MAP_ENGINE)
        )

    result = run([
        sys.executable,
        str(DEEP_MAP_ENGINE)
    ])

    if result.returncode != 0:
        raise RuntimeError(
            result.stderr.strip()
            or result.stdout.strip()
            or "Deep Map failed"
        )

    if not DEEP_MAP_JSON.is_file():
        raise RuntimeError(
            "Deep Map output missing: "
            + rel(DEEP_MAP_JSON)
        )

    try:
        return json.loads(
            DEEP_MAP_JSON.read_text(
                encoding="utf-8"
            )
        )
    except Exception as error:
        raise RuntimeError(
            "Deep Map JSON invalid: "
            + str(error)
        )


def load_deep_map():
    if not DEEP_MAP_JSON.is_file():
        return None

    try:
        return json.loads(
            DEEP_MAP_JSON.read_text(
                encoding="utf-8"
            )
        )
    except Exception:
        return None


def print_json(data):
    print(json.dumps(data, indent=2, ensure_ascii=False))


def main():
    parser = argparse.ArgumentParser(prog="brain")
    parser.add_argument("command", choices=[
        "doctor", "scan", "impact", "risk", "security",
        "test-plan", "plan", "next", "gate", "maps",
        "release", "performance", "deep-map", "sync", "checkpoint",
        "session", "all"
    ])
    parser.add_argument("label", nargs="?", default="")
    parser.add_argument("--full", action="store_true")
    args = parser.parse_args()

    if args.command == "doctor":
        raise SystemExit(doctor())

    if args.command == "scan":
        print_json(scan_data())

    elif args.command == "impact":
        print_json(impact_data())

    elif args.command == "risk":
        print_json(risk_data())

    elif args.command == "security":
        print_json(security_data(full=args.full))

    elif args.command == "test-plan":
        print_json(test_plan_data())

    elif args.command in ("plan", "next"):
        print_json(task_plan_data())

    elif args.command == "release":
        print_json(release_data())

    elif args.command == "performance":
        print_json(performance_data())

    elif args.command == "deep-map":
        data = run_deep_map()
        print("PASS - Deep Project Map generated")
        print("API routes:", len(data.get("api", [])))
        print(
            "Database tables:",
            len((data.get("database") or {}).get("tables", []))
        )
        print(
            "Migrations:",
            len((data.get("database") or {}).get("migrations", []))
        )
        print(
            "Worker scripts:",
            len((data.get("workers") or {}).get("runtime_scripts", []))
        )
        print("Workflows:", len(data.get("workflows", [])))
        print("Tests:", len(data.get("tests", [])))

    elif args.command in ("maps", "sync"):
        data = write_memory()
        print("PASS - memory synchronized")
        print("Decision:", data["risk"]["decision"])
        print("Risk:", data["risk"]["score"])
        print("Security:", data["security"]["health"])

    elif args.command == "checkpoint":
        p = checkpoint(args.label or "manual")
        print("PASS - checkpoint:", rel(p))

    elif args.command == "session":
        print("Branch:", git("branch", "--show-current"))
        print("HEAD:", git("rev-parse", "--short", "HEAD"))
        print("Changed:", len(changed_files()))
        r = risk_data()
        print("Decision:", r["decision"])
        print("Risk:", r["score"])

    elif args.command == "gate":
        r = risk_data()
        print(r["decision"], r["score"])
        if r["decision"] == "ACCEPT":
            raise SystemExit(0)
        if r["decision"] == "REFACTOR_FIRST":
            raise SystemExit(1)
        raise SystemExit(2)

    elif args.command == "all":
        label = args.label or "full-analysis"
        write_memory()
        p = checkpoint(label)
        data = latest_report(label)

        print("=== SECOND BRAIN V2 ===")
        print("Decision:", data["risk"]["decision"])
        print("Risk:", f'{data["risk"]["score"]}/100')
        print("Security:", data["security"]["health"])
        print("Changed source files:", len(data["impact"]["changed_files"]))
        print("Domains:", ", ".join(data["impact"]["domains"]) or "none")

        deep = data.get("deep_map") or {}

        print("API routes:", len(deep.get("api", [])))
        print(
            "Database tables:",
            len((deep.get("database") or {}).get("tables", []))
        )
        print(
            "Migrations:",
            len((deep.get("database") or {}).get("migrations", []))
        )
        print(
            "Worker scripts:",
            len((deep.get("workers") or {}).get("runtime_scripts", []))
        )
        print("Workflows:", len(deep.get("workflows", [])))
        print("Tests:", len(deep.get("tests", [])))

        print("Checkpoint:", rel(p))
        print("Report: .secondbrain/reports/LATEST.md")

if __name__ == "__main__":
    main()
