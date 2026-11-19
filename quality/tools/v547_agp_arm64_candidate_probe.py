"""Fail-closed, CI-only offline replay of an authenticated GitHub AGP cache candidate.

Artifact is NOT a signed package, is not installed to a user device or to R2,
and successful Gradle plugin resolution is NOT a device APK build.
"""
from __future__ import annotations
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import sys
import tarfile

ARTIFACT = "agp911-gradle931-modules2.tar.gz"
EXPECTED_FILES = 471
EXPECTED_BYTES = 133080728
REQUIRED = (
    "modules-2/files-2.1/com.android.tools.build/gradle/9.1.1/",
    "modules-2/files-2.1/com.android.application/com.android.application.gradle.plugin/9.1.1/",
)
HEX64 = re.compile(r"[0-9a-f]{64}\Z")


def sha_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def audit_and_expand(artifact_dir: Path, cache_dir: Path, expected_files=EXPECTED_FILES, expected_bytes=EXPECTED_BYTES):
    """Extract only into a previously absent CI-owned cache path after hashing every file."""
    artifact_dir = Path(artifact_dir)
    cache_dir = Path(cache_dir)
    assert artifact_dir.is_dir() and not artifact_dir.is_symlink(), "UNTRUSTED_CANDIDATE_DIRECTORY"
    assert not cache_dir.exists() and not cache_dir.is_symlink(), "DESTINATION_NOT_EMPTY"
    manifest_file = artifact_dir / "manifest.json"
    sha_record = artifact_dir / "archive.sha256"
    tar_path = artifact_dir / ARTIFACT
    for path in (manifest_file, sha_record, tar_path):
        assert path.is_file() and not path.is_symlink(), "CANDIDATE_FILE_MISSING_OR_SYMLINK"
    assert tar_path.stat().st_size < 300 * 1024 * 1024, "ARCHIVE_TOO_LARGE"
    record = sha_record.read_text("ascii")
    match = re.fullmatch(r"([0-9a-f]{64})  agp911-gradle931-modules2\.tar\.gz\n?", record)
    assert match, "INVALID_ARCHIVE_RECORD"
    assert sha_file(tar_path) == match.group(1), "ARCHIVE_SHA256_MISMATCH"
    assert manifest_file.stat().st_size < 512 * 1024, "MANIFEST_TOO_LARGE"
    manifest = json.loads(manifest_file.read_text("utf-8"))
    assert {k: manifest.get(k) for k in ("schema", "phase", "agp", "gradle")} == {
        "schema": 1, "phase": "CI_CANDIDATE_NOT_SIGNED", "agp": "9.1.1", "gradle": "9.3.1"
    }, "MANIFEST_NOT_AUTHORIZED_FOR_PRODUCTION"
    rows = manifest.get("files")
    assert type(rows) is list and len(rows) == expected_files, "FILE_COUNT_MISMATCH"
    entries: dict[str, tuple[str, int]] = {}
    total = 0
    for obj in rows:
        assert type(obj) is dict and set(obj) == {"path", "sha256", "bytes"}, "ENTRY_SCHEMA"
        rel = obj["path"]
        assert type(rel) is str and rel.startswith("modules-2/") and "//" not in rel, "UNTRUSTED_ENTRY"
        name = PurePosixPath(rel)
        assert not name.is_absolute() and ".." not in name.parts and rel not in entries, "UNSAFE_ENTRY"
        size = obj["bytes"]
        assert type(size) is int and 0 <= size <= expected_bytes, "INVALID_SIZE"
        assert type(obj["sha256"]) is str and HEX64.fullmatch(obj["sha256"]), "INVALID_SHA256"
        total += size
        entries[rel] = (obj["sha256"], size)
    assert total == expected_bytes, "PAYLOAD_SIZE_MISMATCH"
    for prefix in REQUIRED:
        assert any(rel.startswith(prefix) for rel in entries), "AGP_REQUIRED_ARTIFACT_MISSING"
    assert cache_dir.parent.is_dir() and not cache_dir.parent.is_symlink(), "CACHE_PARENT_UNTRUSTED"
    cache_dir.mkdir(mode=0o700)
    count = 0
    with tarfile.open(tar_path, "r|gz") as tf:
        for member in tf:
            name = PurePosixPath(member.name)
            assert not name.is_absolute() and ".." not in name.parts, "TAR_PATH_TRAVERSAL"
            assert member.isdir() or member.isfile(), "TAR_SPECIAL_ENTRY_FORBIDDEN"
            if member.isdir():
                continue
            rel = member.name
            assert rel in entries, "TAR_UNLISTED_FILE"
            expected_sha, expected_size = entries.pop(rel)
            assert member.size == expected_size, "TAR_LENGTH_MISMATCH"
            out = cache_dir / rel
            assert out.resolve().is_relative_to(cache_dir.resolve()), "TAR_ESCAPE"
            out.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
            assert not out.parent.is_symlink(), "TAR_PARENT_SYMLINK"
            h = hashlib.sha256()
            obj = tf.extractfile(member)
            assert obj is not None, "TAR_CONTENT_MISSING"
            flags = os.O_CREAT | os.O_EXCL | os.O_WRONLY | getattr(os, "O_NOFOLLOW", 0)
            fd = os.open(out, flags, 0o600)
            with os.fdopen(fd, "wb") as dest:
                while True:
                    data = obj.read(1024 * 1024)
                    if not data:
                        break
                    h.update(data)
                    dest.write(data)
            assert h.hexdigest() == expected_sha, "TAR_FILE_HASH_MISMATCH"
            count += 1
    assert not entries and count == expected_files, "TAR_FILES_INCOMPLETE"
    print(f"APPFORGE_V547_AGP_ARM64_CACHE_BYTES_VERIFIED={total}")
    print(f"APPFORGE_V547_AGP_ARM64_CACHE_FILES_VERIFIED={count}")
    print("APPFORGE_V547_AGP_SIGNED_RELEASE=NO")
    return count


def main():
    if len(sys.argv) != 3:
        raise SystemExit("Usage: ... <artifact-dir> <new-ci-gradle-caches-dir>")
    audit_and_expand(Path(sys.argv[1]), Path(sys.argv[2]))


if __name__ == "__main__":
    main()
