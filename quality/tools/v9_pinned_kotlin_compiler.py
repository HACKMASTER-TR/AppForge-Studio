#!/usr/bin/env python3
"""CI-only SHA-256 verified Kotlin 2.4.10 compiler prefetch.

The download is verified *before* Gradle can execute the compiler artifact.
Only the target module is routed to the temporary, checked Maven repository.
"""
import hashlib
import os
from pathlib import Path
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

GROUP = "org.jetbrains.kotlin"
ARTIFACT = "kotlin-compiler-embeddable"
VERSION = "2.4.10"
EXPECTED_SIZE = 60_352_373
# SHA-256 from JetBrains/kotlin gradle/verification-metadata.xml.
EXPECTED_SHA256 = "9309638a2ee03e6bde9ef4b7444055a94b84ab906563675d71ff9aecb64da913"
SUBPATH = "org/jetbrains/kotlin/kotlin-compiler-embeddable/2.4.10/"
JAR_NAME = "kotlin-compiler-embeddable-2.4.10.jar"
POM_NAME = "kotlin-compiler-embeddable-2.4.10.pom"
OFFICIAL_BASE = "https://repo.maven.apache.org/maven2/"
MIRRORS = (
    ("wso2", "https://maven.wso2.org/nexus/content/repositories/central/"),
    ("liferay", "https://repository.liferay.com/nexus/content/repositories/gradle-plugins/"),
)


def fetch_jar(url: str, target: Path) -> bool:
    """Download without trusting the source until size and SHA-256 match."""
    request = urllib.request.Request(url, headers={"User-Agent": "AppForge-CI-Pinned-Artifact/1.0"})
    fd, tempname = tempfile.mkstemp(prefix=".kotlin-download-", dir=target.parent)
    os.close(fd)
    tmp = Path(tempname)
    try:
        with urllib.request.urlopen(request, timeout=40) as response, tmp.open("wb") as out:
            final = urllib.parse.urlsplit(response.geturl())
            if final.scheme != "https":
                raise RuntimeError("KOTLIN_NON_HTTPS_REDIRECT")
            digest = hashlib.sha256()
            size = 0
            while True:
                data = response.read(1024 * 1024)
                if not data:
                    break
                size += len(data)
                if size > EXPECTED_SIZE:
                    raise RuntimeError("KOTLIN_SIZE_EXCEEDED")
                digest.update(data)
                out.write(data)
        if size != EXPECTED_SIZE or digest.hexdigest() != EXPECTED_SHA256:
            raise RuntimeError("KOTLIN_PINNED_HASH_MISMATCH")
        os.replace(tmp, target)
        return True
    finally:
        tmp.unlink(missing_ok=True)


def fetch_official_pom(target: Path) -> None:
    """POM only comes from the original Maven Central host over verified TLS."""
    url = OFFICIAL_BASE + SUBPATH + POM_NAME
    request = urllib.request.Request(url, headers={"User-Agent": "AppForge-CI-Pinned-Artifact/1.0"})
    with urllib.request.urlopen(request, timeout=30) as response:
        final = urllib.parse.urlsplit(response.geturl())
        if final.scheme != "https" or final.hostname != "repo.maven.apache.org":
            raise RuntimeError("KOTLIN_POM_ORIGIN_MISMATCH")
        data = response.read(128 * 1024 + 1)
        if len(data) > 128 * 1024:
            raise RuntimeError("KOTLIN_POM_OVERSIZED")
    root = ET.fromstring(data)
    names = {child.tag.split("}")[-1]: (child.text or "").strip() for child in root}
    if (root.tag.split("}")[-1] != "project" or
        names.get("groupId") != GROUP or
        names.get("artifactId") != ARTIFACT or
        names.get("version") != VERSION):
        raise RuntimeError("KOTLIN_POM_COORDINATES_INVALID")
    target.write_bytes(data)


def main() -> int:
    if len(sys.argv) != 2:
        print("BLOCKED=REPOSITORY_PATH_REQUIRED")
        return 2
    root = Path(sys.argv[1]).resolve()
    if "RUNNER_TEMP" not in os.environ or not str(root).startswith(str(Path(os.environ["RUNNER_TEMP"]).resolve()) + os.sep):
        print("BLOCKED=CI_TEMP_DIRECTORY_REQUIRED")
        return 2
    target_dir = root / SUBPATH
    target_dir.mkdir(parents=True, exist_ok=True)
    jar = target_dir / JAR_NAME
    pom = target_dir / POM_NAME
    if jar.is_file():
        valid = jar.stat().st_size == EXPECTED_SIZE and hashlib.sha256(jar.read_bytes()).hexdigest() == EXPECTED_SHA256
        if not valid:
            jar.unlink()
    if not jar.is_file():
        for label, base in MIRRORS:
            for attempt in range(1, 3):
                try:
                    fetch_jar(base + SUBPATH + JAR_NAME, jar)
                    print("KOTLIN_VERIFIED_SOURCE=" + label)
                    break
                except (OSError, urllib.error.URLError, RuntimeError) as ex:
                    print(f"KOTLIN_FETCH={label}:{attempt}:FAILED:{type(ex).__name__}")
            if jar.is_file():
                break
    if not jar.is_file():
        print("BLOCKED=PINNED_KOTLIN_COMPILER_UNAVAILABLE")
        return 2
    fetch_official_pom(pom)
    print("KOTLIN_COMPILER_SHA256=PASS")
    print("KOTLIN_VERSION=2.4.10")
    print("KOTLIN_POM=OFFICIAL_MAVEN_CENTRAL")
    print("KOTLIN_LOCAL_REPOSITORY=READY")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as e:
        print("BLOCKED=KOTLIN_PREFLIGHT:" + type(e).__name__)
        sys.exit(2)
