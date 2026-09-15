#!/usr/bin/env python3
import json, re, sys
from pathlib import Path

root = Path.cwd()
wiki = root / "docs" / "wiki"
patterns = [
    ("private key", r"-----BEGIN (?:RSA |EC |OPENSSH |)?PRIVATE KEY-----"),
    ("GitHub token", r"gh[pousr]_[A-Za-z0-9_]{20,}"),
    ("OpenAI key", r"sk-[A-Za-z0-9]{20,}"),
    ("Anthropic key", r"sk-ant-[A-Za-z0-9_-]{20,}"),
    ("AWS key", r"AKIA[0-9A-Z]{16}"),
    ("JWT-like token", r"eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}"),
    ("Stripe live secret", r"sk_live_[A-Za-z0-9]{20,}"),
    ("SendGrid key", r"SG\.[A-Za-z0-9_-]{16,}\.[A-Za-z0-9_-]{16,}"),
    ("secret assignment", r"(?i)(api[_-]?key|secret|token|password|client[_-]?secret)\s*[:=]\s*['\"][^'\"]{12,}['\"]"),
]
findings = []
if not wiki.exists(): findings.append({"file": "docs/wiki", "line": 0, "type": "missing wiki"})
else:
    for file in wiki.rglob("*.md"):
        for number, line in enumerate(file.read_text(encoding="utf-8", errors="ignore").splitlines(), 1):
            if any(hint in line.lower() for hint in ("placeholder", "example", "replace_me", "not-a-real-secret")): continue
            for name, pattern in patterns:
                if re.search(pattern, line): findings.append({"file": str(file.relative_to(root)), "line": number, "type": name})
result = {"tool": "wiki-secret-scan", "findings": findings, "health": "Red" if findings else "Green"}
if "--json" in sys.argv: print(json.dumps(result, indent=2))
else:
    for finding in findings: print(f"ERROR: {finding['file']}:{finding['line']} [{finding['type']}]")
    print(f"Wiki secret health: {result['health']}")
sys.exit(1 if findings else 0)
