"""CI-only AGP/Gradle cache manifest; NOT a signed release/production install authorization."""
import hashlib
import json
import pathlib
import re
import sys

VERSION = 1
HEX = re.compile(r"^[0-9a-f]{64}$")


def files_in(root: pathlib.Path):
    base = root / "modules-2"
    if not base.is_dir() or base.is_symlink():
        raise ValueError("MODULES2_ROOT_MISSING_OR_UNTRUSTED")
    files = []
    for obj in sorted(base.rglob("*")):
        if obj.is_symlink():
            raise ValueError("SYMLINK_IN_SEED")
        if obj.is_file():
            rel = obj.relative_to(root).as_posix()
            if rel.endswith(".lock"):
                raise ValueError("LIVE_LOCK_FILE_IN_SEED")
            files.append((rel, obj))
    if not 0 < len(files) <= 25000:
        raise ValueError("SEED_FILE_COUNT_INVALID")
    if sum(path.stat().st_size for _, path in files) > 2 * 1024**3:
        raise ValueError("SEED_SIZE_OVER_LIMIT")
    return files


def digest(path: pathlib.Path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def create(root: pathlib.Path):
    entries = [dict(path=rel, sha256=digest(obj), bytes=obj.stat().st_size)
               for rel, obj in files_in(root)]
    required = ["modules-2/files-2.1/com.android.tools.build/gradle/9.1.1/",
                "modules-2/files-2.1/com.android.application/com.android.application.gradle.plugin/9.1.1/"]
    if not all(any(item["path"].startswith(prefix) for item in entries) for prefix in required):
        raise ValueError("AGP_911_REQUIRED_ARTIFACT_MISSING")
    return dict(schema=VERSION, phase="CI_CANDIDATE_NOT_SIGNED", agp="9.1.1",
                gradle="9.3.1", files=entries)


def verify(root: pathlib.Path, manifest: dict):
    if {key: manifest.get(key) for key in ["schema", "phase", "agp", "gradle"]} != {
        "schema": 1, "phase": "CI_CANDIDATE_NOT_SIGNED", "agp": "9.1.1", "gradle": "9.3.1"}:
        raise ValueError("MANIFEST_METADATA_INVALID")
    expected = create(root)
    if expected != manifest:
        raise ValueError("MANIFEST_OR_FILE_HASH_MISMATCH")
    return len(expected["files"])


def main():
    if len(sys.argv) != 4 or sys.argv[1] not in ("create", "verify"):
        raise SystemExit("Usage: manifest.py create|verify <gradle-caches-dir> <manifest.json>")
    operation, root, target = sys.argv[1], pathlib.Path(sys.argv[2]), pathlib.Path(sys.argv[3])
    if operation == "create":
        data = create(root)
        target.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n")
        print(f"AGP_SEED_MANIFEST_CREATED={len(data['files'])}")
    else:
        print(f"AGP_SEED_MANIFEST_VERIFIED={verify(root, json.loads(target.read_text()))}")

if __name__ == "__main__":
    main()
