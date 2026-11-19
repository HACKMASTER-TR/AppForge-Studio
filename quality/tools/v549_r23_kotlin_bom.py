#!/usr/bin/env python3
"""R23 CI-only, exact SHA-256 pinned Kotlin Gradle plugins BOM 2.4.10.

Never installs unpinned metadata, never changes device / production Maven settings.
Two independent public Gradle verification-metadata publications document the
same POM and .module SHA-256 values. Remote hosts are allowlisted and TLS-verified.
"""
from __future__ import annotations
import hashlib
import json
import os
from pathlib import Path
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

GROUP = "org.jetbrains.kotlin"
ARTIFACT = "kotlin-gradle-plugins-bom"
VERSION = "2.4.10"
DIRECTORY = f"org/jetbrains/kotlin/{ARTIFACT}/{VERSION}"
HASHES = {
    f"{ARTIFACT}-{VERSION}.pom": "733fccda03ae34773e7b5c85d0e278c897df283370059cb39825f7c0381e49b6",
    f"{ARTIFACT}-{VERSION}.module": "48eb4756284d1d7ea24639d22861f15ff0cb685a15331a6c6a3674ff44baf029",
}
SOURCES = (
    ("liferay-gradle-plugins", "https://repository.liferay.com/nexus/content/repositories/gradle-plugins/", "repository.liferay.com"),
    ("google-central-mirror", "https://maven-central.storage-download.googleapis.com/maven2/", "maven-central.storage-download.googleapis.com"),
    ("maven-central", "https://repo.maven.apache.org/maven2/", "repo.maven.apache.org"),
)
MAX_SIZE = 128 * 1024


def validate(data: bytes, name: str, expected_sha: str) -> None:
    if not 80 <= len(data) <= MAX_SIZE or hashlib.sha256(data).hexdigest() != expected_sha:
        raise ValueError("KOTLIN_BOM_SHA256_OR_SIZE_MISMATCH")
    if name.endswith(".pom"):
        root = ET.fromstring(data)
        tags = {child.tag.rsplit("}", 1)[-1]: (child.text or "").strip() for child in root}
        if root.tag.rsplit("}", 1)[-1] != "project" or any((tags.get(k) != v) for k, v in {
            "groupId": GROUP, "artifactId": ARTIFACT, "version": VERSION,
            "packaging": "pom",
        }.items()):
            raise ValueError("KOTLIN_BOM_POM_COORDINATES_INVALID")
    else:
        obj = json.loads(data)
        component = obj.get("component") or {}
        if any(component.get(k) != v for k, v in {"group": GROUP, "module": ARTIFACT, "version": VERSION}.items()):
            raise ValueError("KOTLIN_BOM_MODULE_COORDINATES_INVALID")


def load_from_source(name: str) -> tuple[bytes, str]:
    for label, base, host in SOURCES:
        for attempt in (1, 2):
            try:
                url = base + DIRECTORY + "/" + name
                req = urllib.request.Request(url, headers={"User-Agent": "AppForge-Verified-BOM-R23/1.0"})
                with urllib.request.urlopen(req, timeout=35) as response:
                    final = urllib.parse.urlsplit(response.geturl())
                    if final.scheme != "https" or final.hostname != host or final.query or final.fragment:
                        raise ValueError("KOTLIN_BOM_ORIGIN_INVALID")
                    data = response.read(MAX_SIZE + 1)
                validate(data, name, HASHES[name])
                return data, label
            except (OSError, urllib.error.URLError, ValueError, ET.ParseError, json.JSONDecodeError) as e:
                print(f"KOTLIN_BOM_FETCH={label}:{attempt}:FAILED:{type(e).__name__}")
    raise RuntimeError("KOTLIN_BOM_ALL_VERIFIED_SOURCES_UNAVAILABLE")


def prepare(root: Path) -> None:
    root = root.resolve()
    ci_temp = os.environ.get("RUNNER_TEMP", "")
    if os.environ.get("GITHUB_ACTIONS") != "true" or not ci_temp or not root.is_relative_to(Path(ci_temp).resolve()) or root == Path(ci_temp).resolve():
        raise RuntimeError("KOTLIN_BOM_CI_TEMP_REQUIRED")
    target = root / DIRECTORY
    target.mkdir(mode=0o700, parents=True, exist_ok=True)
    for name, digest in HASHES.items():
        file = target / name
        if file.is_symlink():
            raise RuntimeError("KOTLIN_BOM_SYMLINK_FORBIDDEN")
        if file.is_file():
            try:
                validate(file.read_bytes(), name, digest)
                print(f"KOTLIN_BOM_VERIFIED_CACHED={name}")
                continue
            except (ValueError, ET.ParseError, json.JSONDecodeError):
                raise RuntimeError("KOTLIN_BOM_PREEXISTING_INVALID")
        data, source = load_from_source(name)
        fd, tmp_name = tempfile.mkstemp(prefix=".bom-", dir=target)
        try:
            with os.fdopen(fd, "wb") as stream:
                stream.write(data)
                stream.flush()
                os.fsync(stream.fileno())
            if file.exists() or file.is_symlink():
                raise RuntimeError("KOTLIN_BOM_DESTINATION_ALREADY_EXISTS")
            os.replace(tmp_name, file)
        finally:
            Path(tmp_name).unlink(missing_ok=True)
        print(f"KOTLIN_BOM_VERIFIED_SOURCE={name}:{source}")
    print("KOTLIN_GRADLE_PLUGINS_BOM_2410_SHA256=PASS")
    print("KOTLIN_BOM_REPOSITORY=CI_ONLY_READY")


if __name__ == "__main__":
    try:
        if len(sys.argv) != 2:
            raise ValueError("KOTLIN_BOM_USAGE")
        prepare(Path(sys.argv[1]))
    except Exception as e:
        print(f"KOTLIN_BOM_BLOCKED={type(e).__name__}:{e}")
        sys.exit(2)
