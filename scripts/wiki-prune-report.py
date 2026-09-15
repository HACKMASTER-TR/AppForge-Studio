#!/usr/bin/env python3
import json, re, sys
from datetime import date, datetime
from pathlib import Path

root = Path.cwd(); wiki = root / "docs" / "wiki"; suggestions = []
if not wiki.exists(): suggestions.append({"file": "docs/wiki", "message": "missing wiki"})
else:
    for file in wiki.rglob("*.md"):
        text = file.read_text(encoding="utf-8", errors="ignore")
        body = re.sub(r"^---[\s\S]*?---", "", text).strip(); words = len(body.split())
        rel = str(file.relative_to(root))
        status_match = re.search(r"^status:\s*(\S+)", text, re.M)
        status = status_match.group(1).strip('"\'') if status_match else None
        if file.name == "Hot_Context.md" and words > 500: suggestions.append({"file": rel, "message": "Hot Context exceeds 500 words"})
        if words > 1200 and "/archive/" not in rel.replace("\\", "/"): suggestions.append({"file": rel, "message": "page exceeds 1200 words"})
        verified = re.search(r"^last_verified:\s*(\d{4}-\d{2}-\d{2})", text, re.M)
        if verified:
            try:
                active_types = {"architecture", "api", "database", "integration", "status", "codebase"}
                if status == "active" and re.search(r"^type:\s*(%s)\s*$" % "|".join(active_types), text, re.M) and (date.today() - datetime.strptime(verified.group(1), "%Y-%m-%d").date()).days > 60:
                    suggestions.append({"file": rel, "message": "active evidence may be stale"})
            except ValueError: pass
        headings = [heading.strip().lower() for heading in re.findall(r"^#{2,4}\s+(.+)$", text, re.M)]
        if len(headings) != len(set(headings)):
            suggestions.append({"file": rel, "message": "duplicate headings need review"})
result = {"tool": "wiki-prune-report", "suggestions": suggestions, "health": "Yellow" if suggestions else "Green"}
if "--json" in sys.argv: print(json.dumps(result, indent=2))
else:
    for item in suggestions: print(f"SUGGEST: {item['file']} {item['message']}")
    print(f"Wiki prune health: {result['health']}")
