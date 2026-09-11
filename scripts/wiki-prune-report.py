#!/usr/bin/env python3

from pathlib import Path
from datetime import date, datetime
import argparse
import json
import re
import sys

ROOT = Path.cwd()
WIKI = ROOT / "docs" / "wiki"

MAX_HOT_CONTEXT_WORDS = 500
MAX_PAGE_WORDS = 1200
STALE_DAYS = 60

def read(path):
    return path.read_text(encoding="utf-8", errors="ignore")

def word_count(text):
    text = re.sub(r"^---[\s\S]*?---", "", text)
    return len([w for w in re.split(r"\s+", text) if w])

def frontmatter_value(text, key):
    m = re.search(rf"^{key}:\s*(.+)$", text, re.MULTILINE)
    if not m:
        return None
    return m.group(1).strip().strip('"').strip("'")

def parse_date(value):
    if not value:
        return None
    try:
        return datetime.strptime(value[:10], "%Y-%m-%d").date()
    except ValueError:
        return None

def duplicate_heading_score(text):
    headings = re.findall(r"^#{2,4}\s+(.+)$", text, flags=re.MULTILINE)
    normalized = [h.strip().lower() for h in headings]
    return len(normalized) - len(set(normalized))

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--json", action="store_true", help="Output JSON")
    args = parser.parse_args()

    result = {
        "tool": "wiki-prune-report",
        "suggestions": [],
        "health": "Green",
    }

    if not WIKI.exists():
        result["suggestions"].append({
            "file": "docs/wiki",
            "message": "docs/wiki does not exist."
        })
        result["health"] = "Red"
        if args.json:
            print(json.dumps(result, indent=2))
        else:
            print("ERROR: docs/wiki does not exist.")
        return 1

    today = date.today()

    for file in WIKI.rglob("*.md"):
        text = read(file)
        rel = str(file.relative_to(ROOT))
        wc = word_count(text)
        last_verified = parse_date(frontmatter_value(text, "last_verified"))
        status = frontmatter_value(text, "status")
        page_type = frontmatter_value(text, "type")
        duplicate_headings = duplicate_heading_score(text)

        if file.name == "Hot_Context.md" and wc > MAX_HOT_CONTEXT_WORDS:
            result["suggestions"].append({
                "file": rel,
                "message": f"Hot_Context exceeds {MAX_HOT_CONTEXT_WORDS} words ({wc}). Summarize active context."
            })

        if wc > MAX_PAGE_WORDS and "archive" not in rel:
            result["suggestions"].append({
                "file": rel,
                "message": f"Page exceeds {MAX_PAGE_WORDS} words ({wc}). Consider summarizing or splitting only if concepts differ."
            })

        if last_verified and (today - last_verified).days > STALE_DAYS:
            if page_type in {"architecture", "api", "database", "integration", "status", "codebase"} and status == "active":
                result["suggestions"].append({
                    "file": rel,
                    "message": f"Active {page_type} page last verified {(today - last_verified).days} days ago. Re-verify if still important."
                })

        if duplicate_headings > 0:
            result["suggestions"].append({
                "file": rel,
                "message": f"Has {duplicate_headings} duplicate heading(s). Check for repeated sections."
            })

        if "08_Prompts" in rel:
            lower = text.lower()
            suspicious = [
                "market research",
                "database schema",
                "api endpoint",
                "feature status",
            ]
            hits = [s for s in suspicious if s in lower]
            if hits:
                result["suggestions"].append({
                    "file": rel,
                    "message": f"08_Prompts may contain project facts or non-procedure content: {', '.join(hits)}"
                })

    result["health"] = "Yellow" if result["suggestions"] else "Green"

    if args.json:
        print(json.dumps(result, indent=2))
    else:
        print("Wiki prune report:")
        if not result["suggestions"]:
            print("- No obvious pruning suggestions.")
        else:
            for suggestion in result["suggestions"]:
                print(f"- {suggestion['file']}: {suggestion['message']}")
        print(f"Wiki prune health: {result['health']}")

    return 0

if __name__ == "__main__":
    sys.exit(main())
