#!/usr/bin/env python3
"""AppForge V9: offline, content-addressed distribution staging.

No network, cloud credentials, APK modification or automatic publication.
Distribution gate remains CLOSED until signed indexes and package bodies are
seeded, verified, reviewed, then integrated with a separately approved client.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import sys
from urllib.parse import urlsplit, unquote

SNAPSHOT = "20261018T000000Z"
ORIGIN = "https://snapshot.ubuntu.com/ubuntu/" + SNAPSHOT + "/"
ASSETS = Path("android-app/app/src/main/assets/device-build")
HEX = re.compile(r"[a-f0-9]{64}\Z")
SUITES = ("noble", "noble-updates", "noble-security", "noble-backports")


def sha_file(path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def require(cond, why):
    if not cond:
        raise ValueError("BLOCKED: " + why)


def manifest(repo):
    root = Path(repo)
    cat = root / ASSETS / "apt-module-packages-v1.tsv"
    profile = root / ASSETS / "apt-snapshot-profile.tsv"
    require(cat.is_file() and profile.is_file(), "pinned APT catalog/profile missing")
    require(not cat.is_symlink() and not profile.is_symlink(), "pinned APT source symlink")
    indexes = []
    for n, row in enumerate(profile.read_text().splitlines(), 1):
        parts = row.split("\t")
        require(len(parts) == 2, f"malformed profile line {n}")
        suite, sha = parts
        require(suite in SUITES and bool(HEX.fullmatch(sha)), f"invalid pinned index {n}")
        indexes.append({"kind": "InRelease", "name": suite, "sha256": sha,
                        "size": None, "source": ORIGIN + f"dists/{suite}/InRelease"})
    require(tuple(row["name"] for row in indexes) == SUITES, "signed index set changed")
    objects = {}
    modules = set()
    archs = set()
    for n, row in enumerate(cat.read_text().splitlines(), 1):
        parts = row.split("\t")
        require(len(parts) in (8, 9, 10), f"malformed catalog line {n}")
        module, arch, package, version, pkg_arch, sha, size_str, url = parts[:8]
        require(re.fullmatch(r"[A-Z][A-Z0-9_]*", module) is not None, "bad module")
        require(arch in ("arm64", "amd64"), "invalid host architecture")
        require(pkg_arch in (arch, "all"), "package architecture mismatch")
        require(bool(package) and bool(version), "missing package identity")
        require(bool(HEX.fullmatch(sha)), "invalid package SHA-256")
        require(size_str.isascii() and size_str.isdecimal(), "invalid package size")
        size = int(size_str)
        require(0 < size < 2**32, "invalid package length")
        u = urlsplit(url)
        require(u.scheme == "https" and u.hostname == "snapshot.ubuntu.com" and
                u.port is None and not u.username and not u.password and not u.query and
                not u.fragment and url.startswith(ORIGIN + "pool/"), "unpinned package source")
        decoded = unquote(u.path)
        require(".." not in decoded.split("/") and not any(c in decoded for c in ("\\", "\x00"))
                and u.path.endswith(".deb"), "unsafe package URL")
        modules.add(module)
        archs.add(arch)
        if sha in objects:
            require(objects[sha]["size"] == size, "conflicting SHA-256 byte length")
            objects[sha]["sources"].add(url)
        else:
            objects[sha] = {"kind": "deb", "sha256": sha, "size": size, "sources": {url}}
    require(bool(objects), "empty package catalog")
    packages = [dict(rec, sources=sorted(rec["sources"])) for rec in sorted(objects.values(), key=lambda r: r["sha256"])]
    return {"schema": 1, "stage": "LOCAL_MIRROR_ONLY", "snapshot": SNAPSHOT,
            "catalog_sha256": sha_file(cat), "profile_sha256": sha_file(profile),
            "architectures": sorted(archs), "modules": sorted(modules),
            "objects": sorted(indexes, key=lambda v: v["name"]) + packages}


def object_path(root, sha):
    require(bool(HEX.fullmatch(sha)), "invalid object hash")
    return Path(root) / "objects" / "sha256" / sha[:2] / sha


def inspect_store(repo, store):
    spec = manifest(repo)
    root = Path(store)
    total = present = bytes_total = bytes_present = 0
    absent = []
    for item in spec["objects"]:
        total += 1
        path = object_path(root, item["sha256"])
        if item["size"] is not None:
            bytes_total += item["size"]
        if path.is_symlink():
            raise ValueError("BLOCKED: symlink object detected")
        if not path.is_file():
            absent.append(item["sha256"])
            continue
        if item["size"] is not None:
            require(path.stat().st_size == item["size"], "stored object length mismatch")
        require(sha_file(path) == item["sha256"], "stored object SHA-256 mismatch")
        present += 1
        bytes_present += path.stat().st_size
    return spec, {"total": total, "present": present, "missing": len(absent),
                  "known_package_bytes": bytes_total, "verified_local_bytes": bytes_present,
                  "missing_hashes": absent[:8], "apt_ready": False,
                  "distribution_ready": False}


def import_object(repo, store, sha, source):
    spec = manifest(repo)
    candidates = {o["sha256"]: o for o in spec["objects"]}
    require(sha in candidates, "object not present in pinned catalog")
    info = candidates[sha]
    source = Path(source)
    require(source.is_file() and not source.is_symlink(), "input missing or symlink")
    require(source.stat().st_size <= 100 * 1024 * 1024 or info["size"] is not None,
            "unexpectedly large InRelease")
    if info["size"] is not None:
        require(source.stat().st_size == info["size"], "input package length mismatch")
    require(sha_file(source) == sha, "input SHA-256 mismatch")
    dest = object_path(store, sha)
    require(not dest.is_symlink(), "destination symlink")
    dest.parent.mkdir(parents=True, exist_ok=True)
    require(not dest.parent.is_symlink(), "destination directory symlink")
    if dest.exists():
        require(dest.is_file() and sha_file(dest) == sha, "existing object mismatched")
        return "VERIFIED_REUSED"
    temp = dest.with_name(dest.name + ".part." + str(os.getpid()))
    require(not temp.exists() and not temp.is_symlink(), "temporary collision")
    try:
        with source.open("rb") as src, temp.open("xb") as dst:
            shutil.copyfileobj(src, dst, 1024 * 1024)
        require(sha_file(temp) == sha, "staged hash mismatch")
        os.replace(temp, dest)
    finally:
        if temp.exists():
            temp.unlink()
    return "VERIFIED_IMPORTED"


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--store", type=Path, required=True)
    subs = parser.add_subparsers(dest="command", required=True)
    subs.add_parser("inspect")
    ingest = subs.add_parser("ingest")
    ingest.add_argument("--sha256", required=True)
    ingest.add_argument("--file", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        if args.command == "ingest":
            print("INGEST=" + import_object(args.repo, args.store, args.sha256, args.file))
        spec, status = inspect_store(args.repo, args.store)
        print("=== APPFORGE V9 IMMUTABLE MIRROR ===")
        print("SNAPSHOT=" + spec["snapshot"])
        print("PROFILE_SHA256=" + spec["profile_sha256"])
        print("CATALOG_SHA256=" + spec["catalog_sha256"])
        for k, v in status.items():
            print(k.upper() + "=" + json.dumps(v, separators=(",", ":")))
        print("CLOUD_UPLOAD=NOT_CONFIGURED")
        print("ANDROID_CLIENT=NOT_INTEGRATED")
    except (OSError, ValueError) as e:
        print("MIRROR_VERIFY=FAIL", file=sys.stderr)
        print(str(e), file=sys.stderr)
        return 1
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
