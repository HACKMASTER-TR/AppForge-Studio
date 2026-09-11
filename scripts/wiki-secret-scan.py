#!/usr/bin/env python3

from pathlib import Path
import argparse
import json
import re
import sys

ROOT = Path.cwd()
WIKI = ROOT / "docs" / "wiki"

PATTERNS = [
    ("Generic secret assignment", re.compile(r'(?i)(api[_-]?key|secret|token|password|client[_-]?secret)\s*[:=]\s*["\'][^"\']{12,}["\']')),
    ("JWT-like token", re.compile(r'eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}')),
    ("Private key block", re.compile(r'-----BEGIN (RSA |EC |OPENSSH |PRIVATE )?PRIVATE KEY-----')),
    ("AWS access key", re.compile(r'AKIA[0-9A-Z]{16}')),
    ("GitHub token", re.compile(r'gh[pousr]_[A-Za-z0-9_]{20,}')),
    ("OpenAI-style key", re.compile(r'sk-[A-Za-z0-9]{20,}')),
    ("Supabase service key hint", re.compile(r'(?i)(service_role|supabase).*?(eyJ[A-Za-z0-9_-]{10,})')),
    ("Anthropic API key", re.compile(r'sk-ant-[A-Za-z0-9_-]{20,}')),
    ("Stripe live secret key", re.compile(r'sk_live_[A-Za-z0-9]{20,}')),
    ("Stripe live publishable key", re.compile(r'pk_live_[A-Za-z0-9]{20,}')),
    ("SendGrid API key", re.compile(r'SG\.[A-Za-z0-9_-]{16,}\.[A-Za-z0-9_-]{16,}')),
    ("Twilio Account SID", re.compile(r'AC[0-9a-fA-F]{32}')),
]

ALLOWLIST_HINTS = [
    "placeholder",
    "example",
    "your_api_key",
    "your-token",
    "xxxx",
    "replace_me",
    "not-a-real-secret",
]

def is_allowlisted(line: str) -> bool:
    lower = line.lower()
    return any(h in lower for h in ALLOWLIST_HINTS)

def mask_value(value: str) -> str:
    compact = value.strip().replace("\n", "")
    if len(compact) <= 8:
        return "[masked]"
    return f"{compact[:4]}...[masked]...{compact[-4:]}"

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--json", action="store_true", help="Output JSON")
    args = parser.parse_args()

    result = {
        "tool": "wiki-secret-scan",
        "findings": [],
        "health": "Green",
    }

    if not WIKI.exists():
        result["findings"].append({
            "file": "docs/wiki",
            "line": 0,
            "type": "Missing wiki",
            "preview": "docs/wiki does not exist."
        })
        result["health"] = "Red"
        if args.json:
            print(json.dumps(result, indent=2))
        else:
            print("ERROR: docs/wiki does not exist.")
        return 1

    for file in WIKI.rglob("*.md"):
        text = file.read_text(encoding="utf-8", errors="ignore")
        for idx, line in enumerate(text.splitlines(), start=1):
            if is_allowlisted(line):
                continue
            for name, pattern in PATTERNS:
                match = pattern.search(line)
                if match:
                    result["findings"].append({
                        "file": str(file.relative_to(ROOT)),
                        "line": idx,
                        "type": name,
                        "preview": mask_value(match.group(0)),
                    })

    if result["findings"]:
        result["health"] = "Red"

    if args.json:
        print(json.dumps(result, indent=2))
    else:
        if result["findings"]:
            print("Potential secrets found in docs/wiki:")
            for finding in result["findings"]:
                print(f"- {finding['file']}:{finding['line']} [{finding['type']}] {finding['preview']}")
        else:
            print("No obvious secrets found in docs/wiki.")
        print(f"Wiki secret health: {result['health']}")

    return 1 if result["findings"] else 0

if __name__ == "__main__":
    sys.exit(main())
