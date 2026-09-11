#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import math
import os
import re
import shutil
import statistics
import subprocess
import sys
import time
import urllib.error
import urllib.request
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SB = ROOT / ".secondbrain"
ENGINE = SB / "engine"
MEM = SB / "memory"
RUNTIME = SB / "runtime"
REPORTS = SB / "reports"
CHECKPOINTS = SB / "checkpoints"
UI = SB / "ui"
CACHE_FILE = RUNTIME / "v3-cache.json"
PERF_FILE = MEM / "PERFORMANCE_SAMPLES.json"
TASK_FILE = MEM / "TASK_STATE.json"
META_FILE = MEM / "MEMORY_META.json"
VERSION = "3.0.0"

for directory in (MEM, RUNTIME, REPORTS, CHECKPOINTS, UI):
    directory.mkdir(parents=True, exist_ok=True)

def load_module(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    if not spec or not spec.loader:
        raise RuntimeError(f"Cannot load module: {path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

v2 = load_module("secondbrain_v2", ENGINE / "brain.py")
deep = load_module("secondbrain_deep", ENGINE / "deep_map.py")

def now() -> str:
    return datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")

def run(args, cwd=ROOT, timeout=120):
    try:
        return subprocess.run(
            args,
            cwd=cwd,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=timeout,
        )
    except subprocess.TimeoutExpired as e:
        return subprocess.CompletedProcess(args, 124, e.stdout or "", e.stderr or "timeout")

def git(*args):
    return run(["git", *args]).stdout.strip()

def read_text(path: Path, limit=4_000_000) -> str:
    try:
        if not path.is_file() or path.stat().st_size > limit:
            return ""
        return path.read_text("utf-8", errors="ignore")
    except Exception:
        return ""

def write_text_if_changed(path: Path, text: str):
    path.parent.mkdir(parents=True, exist_ok=True)
    old = read_text(path, max(len(text) + 1000, 4_000_000))
    if old == text:
        return False
    path.write_text(text, encoding="utf-8")
    return True

def jread(path: Path, default):
    try:
        return json.loads(path.read_text("utf-8"))
    except Exception:
        return default

def jwrite(path: Path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    text = json.dumps(data, indent=2, ensure_ascii=False, sort_keys=False) + "\n"
    return write_text_if_changed(path, text)

def rel(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()

def pct(values, p):
    if not values:
        return None
    xs = sorted(float(x) for x in values)
    if len(xs) == 1:
        return xs[0]
    k = (len(xs) - 1) * p
    lo, hi = math.floor(k), math.ceil(k)
    if lo == hi:
        return xs[lo]
    return xs[lo] + (xs[hi] - xs[lo]) * (k - lo)

def project_files():
    return list(v2.source_files())

def file_manifest():
    previous = jread(CACHE_FILE, {})
    new = {}
    changed = []
    for p in project_files():
        rp = rel(p)
        try:
            st = p.stat()
        except OSError:
            continue
        sig = f"{st.st_size}:{st.st_mtime_ns}"
        new[rp] = sig
        if previous.get(rp) != sig:
            changed.append(rp)
    removed = sorted(set(previous) - set(new))
    jwrite(CACHE_FILE, new)
    return {
        "files": len(new),
        "changed_since_cache": sorted(changed),
        "removed_since_cache": removed,
        "incremental": bool(previous),
    }

IMPORT_PATTERNS = [
    re.compile(r'^\s*import\s+([A-Za-z0-9_.$*]+)', re.M),
    re.compile(r'import\s+.*?\s+from\s+["\']([^"\']+)["\']'),
    re.compile(r'require\(\s*["\']([^"\']+)["\']\s*\)'),
    re.compile(r'^\s*from\s+([A-Za-z0-9_.$]+)\s+import\s+', re.M),
]

def dependency_graph():
    files = project_files()
    basenames = defaultdict(list)
    for p in files:
        basenames[p.stem.lower()].append(rel(p))

    nodes = []
    edges = []
    external = Counter()

    for p in files:
        rp = rel(p)
        text = read_text(p, 1_000_000)
        if not text:
            continue
        nodes.append(rp)
        refs = set()
        for pat in IMPORT_PATTERNS:
            refs.update(pat.findall(text))
        for ref in sorted(refs):
            target_stem = ref.split("/")[-1].split(".")[-1].lower()
            candidates = basenames.get(target_stem, [])
            if candidates:
                for dst in candidates[:5]:
                    if dst != rp:
                        edges.append({"from": rp, "to": dst, "kind": "import"})
            else:
                root_pkg = ref.split("/")[0].split(".")[0]
                if root_pkg:
                    external[root_pkg] += 1

    fanout = Counter(e["from"] for e in edges)
    fanin = Counter(e["to"] for e in edges)
    hot = sorted(
        (
            {"file": n, "fan_in": fanin[n], "fan_out": fanout[n], "score": fanin[n] + fanout[n]}
            for n in nodes
        ),
        key=lambda x: (x["score"], x["fan_in"], x["fan_out"]),
        reverse=True,
    )[:40]

    return {
        "generated_at": now(),
        "node_count": len(nodes),
        "edge_count": len(edges),
        "edges": edges[:10000],
        "hotspots": hot,
        "external_dependencies": external.most_common(100),
    }

ROUTE_START = re.compile(
    r'(?:app|router)\s*\.\s*(get|post|put|patch|delete|options|head)\s*\(\s*["\']([^"\']+)["\']',
    re.I | re.S,
)

def api_contracts():
    server = ROOT / "build-service" / "server.js"
    text = read_text(server, 8_000_000)
    matches = list(ROUTE_START.finditer(text))
    routes = []
    middleware_names = [
        "authRequired", "adminRequired", "verifiedEmailRequired",
        "requireScope", "requirePermission", "buildRateLimit",
        "purchaseVerifyRateLimit", "requireIntegrityHeader",
    ]
    for idx, m in enumerate(matches):
        start = m.start()
        end = matches[idx + 1].start() if idx + 1 < len(matches) else min(len(text), start + 8000)
        block = text[start:end]
        middleware = [name for name in middleware_names if name in block[:2500]]
        tables = sorted(set(re.findall(r'\bappforge_[a-z0-9_]+\b', block, re.I)))
        query_params = sorted(set(re.findall(r'req\.query\.([A-Za-z0-9_]+)', block)))
        body_fields = sorted(set(re.findall(r'req\.body\.([A-Za-z0-9_]+)', block)))
        params = sorted(set(re.findall(r'req\.params\.([A-Za-z0-9_]+)', block)))
        routes.append({
            "method": m.group(1).upper(),
            "path": m.group(2),
            "auth": "authRequired" in middleware,
            "admin": "adminRequired" in middleware,
            "middleware": middleware,
            "tables": tables,
            "request": {"query": query_params, "body": body_fields, "params": params},
            "source": "build-service/server.js",
        })
    unique = []
    seen = set()
    for route in routes:
        key = (route["method"], route["path"])
        if key not in seen:
            seen.add(key)
            unique.append(route)
    return {
        "generated_at": now(),
        "count": len(unique),
        "routes": unique,
        "public_routes": [r for r in unique if not r["auth"]],
        "admin_routes": [r for r in unique if r["admin"]],
    }

def db_intelligence():
    sql_dir = ROOT / "build-service" / "sql"
    migrations = []
    tables = set()
    destructive = []
    index_re = re.compile(r'(?i)\bCREATE\s+(?:UNIQUE\s+)?INDEX\s+(?:IF\s+NOT\s+EXISTS\s+)?["`]?([A-Za-z0-9_.]+)')
    fk_re = re.compile(r'(?i)\bREFERENCES\s+["`]?([A-Za-z0-9_.]+)')
    create_re = re.compile(r'(?i)\bCREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?["`]?([A-Za-z0-9_.]+)')
    alter_re = re.compile(r'(?i)\bALTER\s+TABLE\s+(?:IF\s+EXISTS\s+)?["`]?([A-Za-z0-9_.]+)')
    danger_re = re.compile(r'(?i)\b(DROP\s+TABLE|TRUNCATE\s+TABLE|DROP\s+COLUMN|DELETE\s+FROM)\b')

    if sql_dir.exists():
        for path in sorted(sql_dir.glob("*.sql")):
            text = read_text(path)
            creates = sorted(set(create_re.findall(text)))
            alters = sorted(set(alter_re.findall(text)))
            indexes = sorted(set(index_re.findall(text)))
            refs = sorted(set(fk_re.findall(text)))
            danger = sorted(set(m.group(1).upper() for m in danger_re.finditer(text)))
            tables.update(creates)
            tables.update(alters)
            if danger:
                destructive.append({"file": rel(path), "operations": danger})
            migrations.append({
                "file": rel(path),
                "creates": creates,
                "alters": alters,
                "indexes": indexes,
                "references": refs,
                "destructive": danger,
            })

    usage = defaultdict(list)
    for p in project_files():
        rp = rel(p)
        if not rp.startswith("build-service/") or rp.startswith("build-service/sql/"):
            continue
        text = read_text(p, 1_000_000)
        if not text:
            continue
        for table in tables:
            if table in text:
                usage[table].append(rp)

    return {
        "generated_at": now(),
        "migration_count": len(migrations),
        "table_count": len(tables),
        "tables": sorted(tables),
        "migrations": migrations,
        "destructive_migrations": destructive,
        "usage": {k: sorted(set(v)) for k, v in sorted(usage.items())},
    }

def worker_topology():
    package = jread(ROOT / "build-service" / "package.json", {})
    scripts = package.get("scripts") or {}
    workers = []
    for name, command in scripts.items():
        low = f"{name} {command}".lower()
        if "worker" in low or name == "start":
            workers.append({"name": name, "command": command})

    dockerfiles = []
    base = ROOT / "build-service"
    if base.exists():
        dockerfiles = sorted(rel(p) for p in base.glob("Dockerfile*") if ".bak" not in p.name)

    workflows_dir = ROOT / ".github" / "workflows"
    workflows = []
    if workflows_dir.exists():
        for p in sorted(workflows_dir.iterdir()):
            if p.is_file() and p.suffix in {".yml", ".yaml"} and ".bak" not in p.name:
                text = read_text(p, 500_000)
                workflows.append({
                    "file": rel(p),
                    "worker_related": "worker" in p.name.lower() or "worker" in text.lower(),
                    "railway_related": "railway" in text.lower(),
                    "android_related": "android" in p.name.lower() or "gradle" in text.lower(),
                })

    return {
        "generated_at": now(),
        "runtime_scripts": workers,
        "dockerfiles": dockerfiles,
        "workflows": workflows,
    }

def test_intelligence(changes=None):
    changes = changes if changes is not None else v2.changed_files()
    tests = v2.discover_tests()
    domains = v2.domains_for(changes)
    selected = []

    def score(test):
        low = test.lower()
        s = 0
        for d in domains:
            if d.replace("-", "") in low.replace("-", ""):
                s += 8
        for ch in changes:
            stem = Path(ch).stem.lower()
            if len(stem) >= 5 and stem in low:
                s += 12
            top = ch.split("/")[0].lower()
            if top and top in low:
                s += 3
        if "regression" in low:
            s += 2
        return s

    scored = sorted(((score(t), t) for t in tests), reverse=True)
    selected = [t for s, t in scored if s > 0][:80]
    if not selected:
        selected = tests[:30]

    commands = list(v2.test_plan_data().get("recommended_commands", []))
    critical = [
        t for t in tests
        if any(k in t.lower() for k in ("terminal", "auth", "build", "worker", "security", "pipeline"))
    ][:80]

    return {
        "generated_at": now(),
        "discovered": len(tests),
        "domains": domains,
        "selected_tests": selected,
        "critical_regression_tests": critical,
        "commands": commands,
        "last_execution": jread(RUNTIME / "last-test-run.json", {"status": "NOT_RUN"}),
        "regression_rules": jread(SB / "REGRESSION_RULES.json", {"protected_features": []}),
    }

def record_test(status, note=""):
    status = str(status).upper()
    if status not in {"PASS", "FAIL", "SKIPPED", "NOT_RUN"}:
        raise ValueError("status must be PASS, FAIL, SKIPPED or NOT_RUN")
    data = {
        "status": status,
        "at": now(),
        "head": git("rev-parse", "--short", "HEAD"),
        "note": note[:500],
    }
    jwrite(RUNTIME / "last-test-run.json", data)
    return data

def integration_intelligence():
    base = v2.integration_data()
    service_keywords = {
        "AWS/S3": ("@aws-sdk", "s3"),
        "Sentry": ("sentry",),
        "Google APIs": ("googleapis", "google"),
        "PostgreSQL": ("pg", "postgres"),
        "Redis": ("redis",),
        "Email": ("nodemailer", "sendgrid", "mailjet", "smtp"),
        "JWT": ("jsonwebtoken", "jwt"),
        "Railway": ("railway",),
        "GitHub": ("github",),
    }
    sources = defaultdict(list)
    for p in project_files():
        rp = rel(p)
        text = read_text(p, 500_000).lower()
        if not text:
            continue
        for service, keys in service_keywords.items():
            if any(k in text for k in keys):
                sources[service].append(rp)
    return {
        "generated_at": now(),
        "detected_services": sorted(set(base.get("detected_services", [])) | set(sources)),
        "environment_variable_names": base.get("environment_variable_names", []),
        "sources": {k: sorted(set(v))[:80] for k, v in sorted(sources.items())},
        "credential_values_stored": False,
    }

def surface_map():
    candidates = {
        "android": ["android-app"],
        "desktop": ["desktop-app", "windows-app", "electron-app"],
        "web": ["web", "web-app", "studio-web", "public"],
    }
    result = {}
    for name, paths in candidates.items():
        existing = [p for p in paths if (ROOT / p).exists()]
        result[name] = {"detected": bool(existing), "paths": existing}
    return result

def complexity_intelligence():
    rows = []
    for p in project_files():
        if p.suffix.lower() not in {".kt", ".kts", ".java", ".js", ".mjs", ".cjs", ".ts", ".tsx", ".py"}:
            continue
        text = read_text(p, 4_000_000)
        if not text:
            continue
        lines = text.count("\n") + 1
        funcs = len(re.findall(r'\b(fun|function|def|class|object)\b', text))
        rows.append({"file": rel(p), "lines": lines, "symbols": funcs})
    rows.sort(key=lambda x: (x["lines"], x["symbols"]), reverse=True)
    return {
        "generated_at": now(),
        "largest": rows[:60],
        "refactor_candidates": [x for x in rows if x["lines"] >= 2000][:40],
    }

def subsystem_map():
    rules = {
        "terminal": ("terminal", "pty", "ssh"),
        "builder": ("builder", "build", "gradle"),
        "ai": ("/ai/", "assistant", "advisor"),
        "files": ("workspace", "file", "storage"),
        "git": ("git", "github"),
        "connections": ("railway", "connection", "authorization"),
        "workers": ("worker", "queue"),
        "auth": ("auth", "login", "token", "permission"),
        "database": ("sql", "db.", "postgres"),
        "web": ("web", "pwa"),
        "desktop": ("desktop", "windows", "electron"),
    }
    out = defaultdict(list)
    for p in project_files():
        rp = rel(p)
        low = rp.lower()
        for subsystem, keys in rules.items():
            if any(k in low for k in keys):
                out[subsystem].append(rp)
    return {k: sorted(set(v)) for k, v in sorted(out.items())}

def git_intelligence():
    status_lines = git("status", "--porcelain=v1").splitlines()
    branch = git("branch", "--show-current")
    head = git("rev-parse", "--short", "HEAD")
    ahead = behind = 0
    counts = git("rev-list", "--left-right", "--count", "HEAD...@{upstream}")
    if counts:
        try:
            ahead, behind = map(int, counts.split())
        except Exception:
            pass
    staged, unstaged, untracked = [], [], []
    for line in status_lines:
        if len(line) < 4:
            continue
        x, y, name = line[0], line[1], line[3:].strip('"')
        if x == "?" and y == "?":
            untracked.append(name)
        else:
            if x != " ":
                staged.append(name)
            if y != " ":
                unstaged.append(name)
    forbidden = [x for x in staged if x.endswith((".apk", ".aab", ".save", ".bak")) or x in {"dashboard.sh", "dashboard.sh.bak"}]
    return {
        "generated_at": now(),
        "branch": branch,
        "head": head,
        "ahead": ahead,
        "behind": behind,
        "staged": staged,
        "unstaged": unstaged,
        "untracked": untracked,
        "forbidden_staged": forbidden,
        "recent_commits": git("log", "-10", "--pretty=%h %s").splitlines(),
    }

def github_repo():
    url = git("config", "--get", "remote.origin.url")
    m = re.search(r'github\.com[:/]+([^/]+)/([^/.]+)(?:\.git)?$', url)
    if not m:
        return None
    return f"{m.group(1)}/{m.group(2)}"

def http_json(url, headers=None, timeout=10):
    req = urllib.request.Request(url, headers=headers or {})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as res:
            raw = res.read(2_000_000).decode("utf-8", errors="replace")
            return {"ok": True, "status": res.status, "json": json.loads(raw)}
    except Exception as e:
        return {"ok": False, "error": type(e).__name__ + ": " + str(e)[:300]}

def live_health():
    result = {
        "generated_at": now(),
        "github": {"status": "NOT_CHECKED"},
        "railway": {"status": "NOT_CONFIGURED"},
        "build_service": {"status": "NOT_CONFIGURED"},
    }
    repo = github_repo()
    if repo:
        headers = {"User-Agent": "AppForge-SecondBrain/3.0"}
        token = os.environ.get("GITHUB_TOKEN", "").strip()
        if token:
            headers["Authorization"] = f"Bearer {token}"
        gh = http_json(f"https://api.github.com/repos/{repo}/actions/runs?per_page=20", headers=headers)
        if gh["ok"]:
            runs = gh["json"].get("workflow_runs", [])
            result["github"] = {
                "status": "OK",
                "repo": repo,
                "runs": [
                    {
                        "name": x.get("name"),
                        "status": x.get("status"),
                        "conclusion": x.get("conclusion"),
                        "head_sha": str(x.get("head_sha") or "")[:8],
                        "event": x.get("event"),
                    }
                    for x in runs[:20]
                ],
            }
        else:
            result["github"] = {"status": "UNAVAILABLE", "error": gh.get("error")}

    base = (
        os.environ.get("APPFORGE_BUILD_SERVICE_URL")
        or os.environ.get("RAILWAY_SERVICE_URL")
        or (
            "https://" + os.environ["RAILWAY_PUBLIC_DOMAIN"]
            if os.environ.get("RAILWAY_PUBLIC_DOMAIN")
            else ""
        )
    ).rstrip("/")
    if base:
        health = http_json(base + "/health")
        ready = http_json(base + "/ready")
        result["build_service"] = {
            "status": "OK" if health.get("ok") else "UNAVAILABLE",
            "url_configured": True,
            "health": health,
            "ready": ready,
        }
        result["railway"] = {
            "status": "OK" if ready.get("ok") else "REVIEW_REQUIRED",
            "source": "build-service health endpoints",
        }
    elif shutil.which("railway"):
        p = run(["railway", "status"], timeout=15)
        result["railway"] = {
            "status": "CLI_AVAILABLE" if p.returncode == 0 else "CLI_ERROR",
            "authenticated": p.returncode == 0,
        }
    return result

def performance_samples():
    data = jread(PERF_FILE, {"samples": {}})
    if not isinstance(data, dict):
        data = {"samples": {}}
    data.setdefault("samples", {})
    return data

def record_performance(name, value_ms, source="manual"):
    data = performance_samples()
    arr = data["samples"].setdefault(name, [])
    arr.append({"at": now(), "ms": float(value_ms), "source": source})
    del arr[:-100]
    jwrite(PERF_FILE, data)
    return data

def performance_summary():
    data = performance_samples()
    summary = {}
    for name, rows in data["samples"].items():
        vals = [float(x["ms"]) for x in rows if isinstance(x, dict) and "ms" in x]
        baseline = pct(vals[:-1], 0.50) if len(vals) >= 5 else None
        regression = (
            round(((vals[-1] - baseline) / baseline) * 100, 2)
            if baseline and baseline > 0
            else "NOT_ENOUGH_DATA"
        )
        summary[name] = {
            "samples": len(vals),
            "p50_ms": round(pct(vals, 0.50), 2) if vals else "NOT_MEASURED",
            "p95_ms": round(pct(vals, 0.95), 2) if vals else "NOT_MEASURED",
            "latest_ms": round(vals[-1], 2) if vals else "NOT_MEASURED",
            "regression_vs_prior_p50_pct": regression,
        }
    for required in ("app_build", "worker_job", "terminal_latency", "brain_all"):
        summary.setdefault(required, {
            "samples": 0, "p50_ms": "NOT_MEASURED",
            "p95_ms": "NOT_MEASURED", "latest_ms": "NOT_MEASURED"
        })
    return {"generated_at": now(), "metrics": summary}

def security_v3(full=False):
    base = v2.security_data(full=full)
    extra = []
    files = project_files() if full else [ROOT / x for x in v2.changed_files() if (ROOT / x).is_file()]
    for p in files:
        rp = rel(p)
        text = read_text(p, 1_000_000)
        low = text.lower()
        if not text:
            continue
        if "rejectunauthorized: false" in low:
            extra.append({"severity": "MEDIUM", "type": "tls_reject_unauthorized_false", "file": rp})
        if re.search(r'(?i)(console\.(log|error)|println|print)\s*\([^)]*(token|password|secret|authorization)', text):
            extra.append({"severity": "MEDIUM", "type": "possible_credential_logging", "file": rp})
        if "stricthostkeychecking=no" in low:
            extra.append({"severity": "MEDIUM", "type": "ssh_host_check_disabled", "file": rp})
    seen = set()
    findings = []
    for x in base.get("findings", []) + extra:
        k = (x["severity"], x["type"], x["file"])
        if k not in seen:
            seen.add(k)
            findings.append(x)
    health = "RED" if any(x["severity"] == "HIGH" for x in findings) else "YELLOW" if findings else "GREEN"
    return {"generated_at": now(), "scope": "full" if full else "changed", "health": health, "findings": findings}

def risk_v3():
    base = v2.risk_data()
    dep = dependency_graph()
    api = api_contracts()
    db = db_intelligence()
    sec = security_v3(full=False)
    changes = v2.changed_files()
    score = int(base["score"])
    reasons = list(base["reasons"])

    changed_set = set(changes)
    impacted_edges = [e for e in dep["edges"] if e["from"] in changed_set or e["to"] in changed_set]
    if len(impacted_edges) >= 20:
        score += 10
        reasons.append("high dependency fan-out")
    if any(x["severity"] == "HIGH" for x in sec["findings"]):
        score += 35
        reasons.append("high security finding")
    elif sec["findings"]:
        score += 10
        reasons.append("security review required")
    if any(x["file"] in changed_set for x in db["destructive_migrations"]):
        score += 30
        reasons.append("destructive database migration")
    server_changed = "build-service/server.js" in changed_set
    if server_changed and api["count"] > 0:
        score += 8
        reasons.append("public API surface changed")
    score = min(100, score)
    decision = "ACCEPT" if score <= 34 else "REFACTOR_FIRST" if score <= 69 else "REJECT"
    return {
        **base,
        "score": score,
        "decision": decision,
        "reasons": sorted(set(reasons)),
        "dependency_edges_impacted": len(impacted_edges),
        "security": sec["health"],
    }

def ci_plan():
    changes = v2.changed_files()
    domains = set(v2.domains_for(changes))
    workflows = []
    if "android" in domains or "terminal" in domains:
        workflows.append(".github/workflows/android-debug.yml")
    if "workers" in domains or "build-service" in domains:
        workflows.append(".github/workflows/worker-image.yml")
    if any("source" in x.lower() for x in changes):
        workflows.append(".github/workflows/source-worker-image.yml")
    if "desktop" in domains:
        workflows.append(".github/workflows/windows-worker-image.yml")
    if "ci" in domains:
        workflows.extend([x for x in changes if x.startswith(".github/workflows/")])
    if any(x.endswith(("build.gradle.kts", "gradle.properties")) for x in changes):
        workflows.append(".github/workflows/android-debug.yml")
    return sorted(set(x for x in workflows if (ROOT / x).exists()))

def release_readiness():
    risk = risk_v3()
    sec = security_v3(full=False)
    tests = test_intelligence()
    health = live_health()
    db = db_intelligence()
    git_info = git_intelligence()
    blockers = []
    review = []

    if risk["decision"] == "REJECT":
        blockers.append("Risk engine returned REJECT")
    if sec["health"] == "RED":
        blockers.append("Security health is RED")
    if git_info["forbidden_staged"]:
        blockers.append("Forbidden generated/binary files are staged")
    if db["destructive_migrations"]:
        review.append("Database contains destructive migration operations; verify current change scope")
    last_test = tests.get("last_execution", {})
    if last_test.get("status") == "FAIL":
        blockers.append("Last recorded test run failed")
    elif last_test.get("status") in (None, "NOT_RUN"):
        review.append("No executed test evidence recorded for current change")
    if health["github"]["status"] not in ("OK", "NOT_CHECKED"):
        review.append("GitHub live health unavailable")
    if health["build_service"]["status"] in ("UNAVAILABLE",):
        review.append("Build service health unavailable")
    if risk["decision"] == "REFACTOR_FIRST":
        review.append("Risk engine recommends refactor first")

    status = "BLOCKED" if blockers else "REVIEW_REQUIRED" if review else "READY"
    return {
        "generated_at": now(),
        "status": status,
        "blockers": blockers,
        "review": review,
        "risk": risk,
        "security": sec,
        "tests": tests,
        "live_health": health,
        "ci_plan": ci_plan(),
        "release": v2.release_data(),
    }

ROOT_CAUSE_PATTERNS = [
    ("gradle_dependency", re.compile(r'(?i)(could not resolve|dependency.*failed|failed to resolve)')),
    ("kotlin_compile", re.compile(r'(?i)(compilation error|e:\s.*\.kt:|kotlin.*failed)')),
    ("android_manifest", re.compile(r'(?i)(manifest merger failed|uses-sdk|minSdkVersion)')),
    ("network", re.compile(r'(?i)(connection refused|timed out|unknown host|network is unreachable|unable to resolve host)')),
    ("auth", re.compile(r'(?i)(401|403|unauthorized|forbidden|invalid token|authentication failed)')),
    ("oom", re.compile(r'(?i)(outofmemory|heap space|oomkilled|exit code 137)')),
    ("docker", re.compile(r'(?i)(docker build|failed to solve|no space left on device)')),
    ("sql", re.compile(r'(?i)(postgres|sqlstate|relation .* does not exist|duplicate key|migration)')),
    ("permission", re.compile(r'(?i)(permission denied|eacces|operation not permitted)')),
]

def root_cause(text):
    candidates = []
    lines = text.splitlines()
    for name, pat in ROOT_CAUSE_PATTERNS:
        hits = [line.strip() for line in lines if pat.search(line)]
        if hits:
            candidates.append({"type": name, "hits": hits[-8:], "score": min(100, 30 + len(hits) * 10)})
    candidates.sort(key=lambda x: x["score"], reverse=True)
    bug_memory = read_text(MEM / "BUGS.md", 1_000_000)
    return {
        "generated_at": now(),
        "candidates": candidates,
        "past_bug_memory_available": bool(bug_memory.strip()),
    }

def memory_meta(fingerprint=None):
    old = jread(META_FILE, {})
    return {
        "generated_at": now(),
        "version": VERSION,
        "confidence": {
            "source_maps": "verified-from-source",
            "live_health": "live-verified-when-available",
            "performance": "measured-only",
            "heuristic_dependency_graph": "inferred",
            "risk_decision": "advisory",
        },
        "architecture_fingerprint": fingerprint or old.get("architecture_fingerprint"),
        "previous_fingerprint": old.get("architecture_fingerprint"),
    }

def fingerprint(data) -> str:
    raw = json.dumps(data, sort_keys=True, ensure_ascii=False).encode("utf-8")
    return hashlib.sha256(raw).hexdigest()

def write_md_table(rows, headers):
    if not rows:
        return "_None detected._\n"
    out = ["| " + " | ".join(headers) + " |", "| " + " | ".join("---" for _ in headers) + " |"]
    for row in rows:
        out.append("| " + " | ".join(str(v).replace("|", "\\|") for v in row) + " |")
    return "\n".join(out) + "\n"

def export_android_snapshot(state):
    asset = ROOT / "android-app" / "app" / "src" / "main" / "assets" / "second_brain_snapshot.json"
    payload = {
        "version": VERSION,
        "head": state["git"]["head"],
        "branch": state["git"]["branch"],
        "risk": state["risk"]["decision"],
        "riskScore": state["risk"]["score"],
        "security": state["security"]["health"],
        "apiRoutes": state["api"]["count"],
        "databaseTables": state["database"]["table_count"],
        "migrations": state["database"]["migration_count"],
        "tests": state["tests"]["discovered"],
        "release": state["release_gate"]["status"],
        "liveGithub": state["live"]["github"]["status"],
        "liveRailway": state["live"]["railway"]["status"],
    }
    jwrite(asset, payload)
    return rel(asset)

def sync_memory(include_live=True):
    t0 = time.perf_counter()
    try:
        v2.write_memory()
    except Exception as e:
        # Continue with V3 memory even if one legacy memory page fails.
        write_text_if_changed(REPORTS / "V2_SYNC_ERROR.txt", str(e) + "\n")

    manifest = file_manifest()
    dep = dependency_graph()
    api = api_contracts()
    db = db_intelligence()
    workers = worker_topology()
    tests = test_intelligence()
    integrations = integration_intelligence()
    surfaces = surface_map()
    complexity = complexity_intelligence()
    subsystems = subsystem_map()
    git_info = git_intelligence()
    security = security_v3(full=False)
    risk = risk_v3()
    live = live_health() if include_live else {
        "generated_at": now(),
        "github": {"status": "NOT_CHECKED"},
        "railway": {"status": "NOT_CHECKED"},
        "build_service": {"status": "NOT_CHECKED"},
    }
    perf = performance_summary()
    release_gate = release_readiness() if include_live else {
        "status": "REVIEW_REQUIRED",
        "blockers": [],
        "review": ["Live health not checked"],
    }

    architecture_basis = {
        "api": [(x["method"], x["path"]) for x in api["routes"]],
        "tables": db["tables"],
        "workers": workers["runtime_scripts"],
        "workflows": [x["file"] for x in workers["workflows"]],
        "surfaces": surfaces,
        "subsystems": {k: len(v) for k, v in subsystems.items()},
    }
    fp = fingerprint(architecture_basis)
    old_meta = jread(META_FILE, {})
    drift = bool(old_meta.get("architecture_fingerprint") and old_meta.get("architecture_fingerprint") != fp)

    state = {
        "generated_at": now(),
        "version": VERSION,
        "manifest": manifest,
        "dependency": dep,
        "api": api,
        "database": db,
        "workers": workers,
        "tests": tests,
        "integrations": integrations,
        "surfaces": surfaces,
        "complexity": complexity,
        "subsystems": subsystems,
        "git": git_info,
        "security": security,
        "risk": risk,
        "live": live,
        "performance": perf,
        "release_gate": release_gate,
        "architecture_drift": drift,
    }

    jwrite(MEM / "DEPENDENCY_GRAPH.json", dep)
    jwrite(MEM / "API_CONTRACTS.json", api)
    jwrite(MEM / "DATABASE_INTELLIGENCE.json", db)
    jwrite(MEM / "WORKER_TOPOLOGY.json", workers)
    jwrite(MEM / "TEST_INTELLIGENCE.json", tests)
    jwrite(MEM / "INTEGRATION_INTELLIGENCE.json", integrations)
    jwrite(MEM / "COMPLEXITY.json", complexity)
    jwrite(MEM / "SUBSYSTEMS.json", subsystems)
    jwrite(MEM / "LIVE_HEALTH.json", live)
    jwrite(MEM / "RELEASE_READINESS.json", release_gate)
    jwrite(MEM / "STATE.json", state)
    meta = memory_meta(fp)
    meta["architecture_drift_detected"] = drift
    jwrite(META_FILE, meta)

    dep_rows = [(x["file"], x["fan_in"], x["fan_out"], x["score"]) for x in dep["hotspots"][:30]]
    write_text_if_changed(MEM / "DEPENDENCY_GRAPH.md",
        "# Dependency Graph\n\n"
        f"Generated: {now()}\n\nNodes: **{dep['node_count']}**\nEdges: **{dep['edge_count']}**\n\n"
        + write_md_table(dep_rows, ["File", "Fan-in", "Fan-out", "Score"])
        + "\nConfidence: `inferred` from source imports/references.\n"
    )

    api_rows = [(r["method"], r["path"], "yes" if r["auth"] else "no", "yes" if r["admin"] else "no", ", ".join(r["middleware"])) for r in api["routes"]]
    write_text_if_changed(MEM / "API_CONTRACTS.md",
        "# API Contract Intelligence\n\n"
        f"Generated: {now()}\n\nRoutes: **{api['count']}**\n\n"
        + write_md_table(api_rows, ["Method", "Path", "Auth", "Admin", "Middleware"])
    )

    mig_rows = [(m["file"], ", ".join(m["creates"]) or "-", ", ".join(m["alters"]) or "-", ", ".join(m["destructive"]) or "-") for m in db["migrations"]]
    write_text_if_changed(MEM / "DATABASE_INTELLIGENCE.md",
        "# Database Intelligence\n\n"
        f"Generated: {now()}\n\nTables: **{db['table_count']}**\nMigrations: **{db['migration_count']}**\n\n"
        + write_md_table(mig_rows, ["Migration", "Creates", "Alters", "Destructive"])
    )

    write_text_if_changed(MEM / "LIVE_HEALTH.md",
        "# Live Health\n\n"
        f"Generated: {now()}\n\n"
        f"- GitHub: **{live['github']['status']}**\n"
        f"- Railway: **{live['railway']['status']}**\n"
        f"- Build service: **{live['build_service']['status']}**\n\n"
        "Live values are never invented. Missing credentials/endpoints produce NOT_CONFIGURED/NOT_CHECKED.\n"
    )

    write_text_if_changed(MEM / "RELEASE_READINESS.md",
        "# Release Readiness\n\n"
        f"Generated: {now()}\n\nStatus: **{release_gate['status']}**\n\n"
        "## Blockers\n" + ("\n".join(f"- {x}" for x in release_gate.get("blockers", [])) or "- None") +
        "\n\n## Review\n" + ("\n".join(f"- {x}" for x in release_gate.get("review", [])) or "- None") + "\n"
    )

    elapsed = (time.perf_counter() - t0) * 1000
    record_performance("brain_all", elapsed, source="automatic")
    state["performance"] = performance_summary()
    state["brain_all_ms"] = round(elapsed, 2)
    jwrite(MEM / "STATE.json", state)

    asset = export_android_snapshot(state)
    jwrite(UI / "dashboard.json", {
        "version": VERSION,
        "generated_at": now(),
        "summary": {
            "risk": risk["decision"],
            "risk_score": risk["score"],
            "security": security["health"],
            "release": release_gate["status"],
            "api_routes": api["count"],
            "database_tables": db["table_count"],
            "migrations": db["migration_count"],
            "tests": tests["discovered"],
            "github": live["github"]["status"],
            "railway": live["railway"]["status"],
        },
        "asset": asset,
    })
    return state

def checkpoint(label, phase="manual"):
    state = sync_memory(include_live=False)
    data = {
        "created_at": now(),
        "label": label,
        "phase": phase,
        "head": state["git"]["head"],
        "branch": state["git"]["branch"],
        "status": git("status", "--porcelain=v1").splitlines(),
        "risk": state["risk"],
        "security": state["security"],
        "architecture_fingerprint": jread(META_FILE, {}).get("architecture_fingerprint"),
        "api_count": state["api"]["count"],
        "table_count": state["database"]["table_count"],
        "test_count": state["tests"]["discovered"],
    }
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    safe = re.sub(r"[^A-Za-z0-9._-]+", "-", label).strip("-") or "checkpoint"
    path = CHECKPOINTS / f"{stamp}-{phase}-{safe}.json"
    jwrite(path, data)
    return path, data

def latest_checkpoint(phase=None):
    files = sorted(CHECKPOINTS.glob("*.json"), reverse=True)
    for p in files:
        data = jread(p, {})
        if phase is None or data.get("phase") == phase:
            return p, data
    return None, None

def start_task(label):
    p, data = checkpoint(label, "start")
    task = {"label": label, "started_at": now(), "start_checkpoint": rel(p), "status": "ACTIVE"}
    jwrite(TASK_FILE, task)
    return task

def finish_task(label=None):
    task = jread(TASK_FILE, {})
    label = label or task.get("label") or "task"
    end_p, end = checkpoint(label, "finish")
    start_p = ROOT / task.get("start_checkpoint", "") if task.get("start_checkpoint") else None
    start = jread(start_p, {}) if start_p and start_p.is_file() else {}
    diff = {
        "label": label,
        "finished_at": now(),
        "start_head": start.get("head"),
        "end_head": end.get("head"),
        "risk_before": (start.get("risk") or {}).get("decision"),
        "risk_after": (end.get("risk") or {}).get("decision"),
        "security_before": (start.get("security") or {}).get("health"),
        "security_after": (end.get("security") or {}).get("health"),
        "api_count_before": start.get("api_count"),
        "api_count_after": end.get("api_count"),
        "table_count_before": start.get("table_count"),
        "table_count_after": end.get("table_count"),
        "end_checkpoint": rel(end_p),
    }
    jwrite(REPORTS / "TASK_DIFF.json", diff)
    task.update({"status": "DONE", "finished_at": now(), "end_checkpoint": rel(end_p)})
    jwrite(TASK_FILE, task)
    return diff

def ask(query):
    terms = [x for x in re.findall(r'[A-Za-z0-9_.-]+', query.lower()) if len(x) >= 3]
    scored = []
    for p in MEM.glob("*"):
        if p.suffix.lower() not in {".md", ".json"}:
            continue
        text = read_text(p, 2_000_000)
        low = text.lower()
        score = sum(low.count(t) for t in terms)
        if score:
            snippets = []
            for line in text.splitlines():
                ll = line.lower()
                if any(t in ll for t in terms):
                    snippets.append(line.strip())
                    if len(snippets) >= 8:
                        break
            scored.append((score, p.name, snippets))
    scored.sort(reverse=True)
    return {
        "query": query,
        "matches": [{"file": f, "score": s, "snippets": sn} for s, f, sn in scored[:8]],
        "confidence": "memory-search",
    }

def feature_mode(name):
    state = sync_memory(include_live=False)
    task = start_task("feature-" + name)
    return {
        "mode": "feature",
        "name": name,
        "task": task,
        "risk": state["risk"],
        "impact": v2.impact_data(),
        "tests": state["tests"],
        "ci_plan": ci_plan(),
        "relevant_memory": ask(name),
    }

def bug_mode(description):
    state = sync_memory(include_live=False)
    return {
        "mode": "bug",
        "description": description,
        "risk": state["risk"],
        "relevant_memory": ask(description),
        "past_bugs": ask("bug " + description),
        "tests": state["tests"],
    }

def prune(apply=False, keep=60):
    checkpoints = sorted(CHECKPOINTS.glob("*.json"))
    candidates = checkpoints[:-keep] if len(checkpoints) > keep else []
    if apply:
        for p in candidates:
            p.unlink(missing_ok=True)
    return {
        "checkpoint_count": len(checkpoints),
        "keep": keep,
        "candidates": [rel(p) for p in candidates],
        "applied": apply,
        "durable_memory_deleted": False,
    }

def ai_context():
    state = jread(MEM / "STATE.json", {})
    if not state:
        state = sync_memory(include_live=False)
    task = jread(TASK_FILE, {})
    return {
        "version": VERSION,
        "task": task,
        "project": {
            "branch": (state.get("git") or {}).get("branch"),
            "head": (state.get("git") or {}).get("head"),
            "risk": (state.get("risk") or {}).get("decision"),
            "risk_score": (state.get("risk") or {}).get("score"),
            "security": (state.get("security") or {}).get("health"),
            "api_routes": (state.get("api") or {}).get("count"),
            "database_tables": (state.get("database") or {}).get("table_count"),
            "migrations": (state.get("database") or {}).get("migration_count"),
            "tests": (state.get("tests") or {}).get("discovered"),
            "release": (state.get("release_gate") or {}).get("status"),
        },
        "rules": [
            "Source/config/tests/runtime are authoritative.",
            "Never expose secret values.",
            "Unknown live state stays NOT_CHECKED/NOT_CONFIGURED.",
            "Do not commit/push/deploy without explicit request.",
        ],
    }

def selftest():
    failures = []
    def check(name, cond):
        if not cond:
            failures.append(name)
        return {"name": name, "pass": bool(cond)}

    source_names = [rel(p) for p in project_files()]
    api = api_contracts()
    db = db_intelligence()
    sec = security_v3(full=True)
    checks = [
        check("old brain absent", not (ROOT / ".appforge-brain").exists()),
        check("old wiki absent", not (ROOT / "docs/wiki").exists()),
        check("node_modules excluded", not any("/node_modules/" in f"/{x}/" for x in source_names)),
        check("secondbrain self excluded", not any(x.startswith(".secondbrain/") for x in source_names)),
        check("api parser", api["count"] > 0),
        check("db parser", db["migration_count"] > 0 and db["table_count"] > 0),
        check("security no HIGH", not any(x["severity"] == "HIGH" for x in sec["findings"])),
        check("deep mapper exists", (ENGINE / "deep_map.py").is_file()),
        check("v2 engine exists", (ENGINE / "brain.py").is_file()),
    ]
    result = {"generated_at": now(), "version": VERSION, "checks": checks, "failures": failures, "status": "PASS" if not failures else "FAIL"}
    jwrite(REPORTS / "SELFTEST.json", result)
    return result

def doctor():
    base_rc = v2.doctor()
    st = selftest()
    required = [
        MEM / "STATE.json",
        MEM / "API_CONTRACTS.json",
        MEM / "DATABASE_INTELLIGENCE.json",
        MEM / "DEPENDENCY_GRAPH.json",
    ]
    checks = {
        "v2 doctor": base_rc == 0,
        "selftest": st["status"] == "PASS",
        "version file": (SB / "VERSION").is_file(),
        "memory files": all(p.is_file() for p in required),
        "android second-brain bridge": (ROOT / "android-app/app/src/main/java/com/appforge/studio/SecondBrainScreen.kt").is_file(),
        "ai second-brain bridge": (ROOT / "android-app/app/src/main/java/com/appforge/studio/ai/SecondBrainBridge.kt").is_file(),
    }
    return {"version": VERSION, "checks": checks, "status": "PASS" if all(checks.values()) else "FAIL"}

def delegate_to_v2(args):
    p = run([sys.executable, str(ENGINE / "brain.py"), *args], timeout=300)
    sys.stdout.write(p.stdout)
    sys.stderr.write(p.stderr)
    raise SystemExit(p.returncode)

def print_json(data):
    print(json.dumps(data, indent=2, ensure_ascii=False))

def main():
    parser = argparse.ArgumentParser(prog="brain")
    parser.add_argument("command")
    parser.add_argument("arg", nargs="?")
    parser.add_argument("arg2", nargs="?")
    parser.add_argument("--full", action="store_true")
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--no-live", action="store_true")
    ns, unknown = parser.parse_known_args()

    cmd = ns.command

    if cmd == "doctor":
        print_json(doctor())
        return
    if cmd == "selftest":
        result = selftest()
        print_json(result)
        raise SystemExit(0 if result["status"] == "PASS" else 1)
    if cmd in ("sync", "maps", "all"):
        label = ns.arg or "full-analysis"
        state = sync_memory(include_live=not ns.no_live)
        cp, _ = checkpoint(label, "all")
        print("=== SECOND BRAIN V3 ===")
        print("Version:", VERSION)
        print("Decision:", state["risk"]["decision"])
        print("Risk:", f'{state["risk"]["score"]}/100')
        print("Security:", state["security"]["health"])
        print("API routes:", state["api"]["count"])
        print("Database tables:", state["database"]["table_count"])
        print("Migrations:", state["database"]["migration_count"])
        print("Worker scripts:", len(state["workers"]["runtime_scripts"]))
        print("Workflows:", len(state["workers"]["workflows"]))
        print("Tests:", state["tests"]["discovered"])
        print("GitHub:", state["live"]["github"]["status"])
        print("Railway:", state["live"]["railway"]["status"])
        print("Release gate:", state["release_gate"]["status"])
        print("Architecture drift:", state["architecture_drift"])
        print("Checkpoint:", rel(cp))
        return
    if cmd == "dependency":
        print_json(dependency_graph()); return
    if cmd == "api-contracts":
        print_json(api_contracts()); return
    if cmd == "database":
        print_json(db_intelligence()); return
    if cmd == "workers":
        print_json(worker_topology()); return
    if cmd == "tests":
        print_json(test_intelligence()); return
    if cmd == "integrations":
        print_json(integration_intelligence()); return
    if cmd == "test-record":
        print_json(record_test(ns.arg or "NOT_RUN", ns.arg2 or "")); return
    if cmd == "surfaces":
        print_json(surface_map()); return
    if cmd == "complexity":
        print_json(complexity_intelligence()); return
    if cmd == "subsystems":
        print_json(subsystem_map()); return
    if cmd == "git":
        print_json(git_intelligence()); return
    if cmd == "live":
        print_json(live_health()); return
    if cmd == "security":
        print_json(security_v3(full=ns.full)); return
    if cmd == "risk":
        print_json(risk_v3()); return
    if cmd == "performance":
        print_json(performance_summary()); return
    if cmd == "perf-record":
        if not ns.arg or ns.arg2 is None:
            raise SystemExit("Usage: brain perf-record <metric> <milliseconds>")
        print_json(record_performance(ns.arg, float(ns.arg2))); return
    if cmd == "release-check":
        print_json(release_readiness()); return
    if cmd == "ci-plan":
        print_json({"workflows": ci_plan()}); return
    if cmd == "start":
        print_json(start_task(ns.arg or "task")); return
    if cmd == "finish":
        print_json(finish_task(ns.arg)); return
    if cmd == "ask":
        print_json(ask(ns.arg or "")); return
    if cmd == "feature":
        print_json(feature_mode(ns.arg or "feature")); return
    if cmd == "bug":
        print_json(bug_mode(ns.arg or "bug")); return
    if cmd == "root-cause":
        if not ns.arg:
            raise SystemExit("Usage: brain root-cause <log-file>")
        path = Path(ns.arg)
        if not path.is_absolute():
            path = ROOT / path
        print_json(root_cause(read_text(path, 8_000_000))); return
    if cmd == "prune":
        print_json(prune(apply=ns.apply)); return
    if cmd == "ui-data":
        state = sync_memory(include_live=not ns.no_live)
        print_json(jread(UI / "dashboard.json", {})); return
    if cmd == "ai-context":
        ctx = ai_context()
        jwrite(REPORTS / "AI_CONTEXT.json", ctx)
        print_json(ctx); return
    if cmd == "session":
        print_json({
            "task": jread(TASK_FILE, {}),
            "git": git_intelligence(),
            "risk": risk_v3(),
            "release": jread(MEM / "RELEASE_READINESS.json", {}),
        }); return

    # Backward-compatible V2 commands.
    delegate_args = [cmd]
    if ns.arg:
        delegate_args.append(ns.arg)
    if ns.full:
        delegate_args.append("--full")
    delegate_args.extend(unknown)
    delegate_to_v2(delegate_args)

if __name__ == "__main__":
    main()
