#!/usr/bin/env python3

import argparse
import hashlib
import json
import os
import stat
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

BASIS_POLICY_VERSION = "SECOND_BRAIN_V2_BASIS_POLICY_V2"

ASSET_REL = (
    "android-app/app/src/main/assets/"
    "second_brain_snapshot.json"
)

ASSET = ROOT / ASSET_REL

EVIDENCE_PREFIXES = (
    "docs/wiki/",
    "android-app/app/src/main/",
    "android-app/app/src/test/",
    "quality/tests/",
    "cloudflare/control-plane/",
    ".github/workflows/",
    "scripts/",
)

EVIDENCE_EXTENSIONS = {
    ".kt",
    ".kts",
    ".java",
    ".js",
    ".mjs",
    ".py",
    ".json",
    ".md",
    ".yml",
    ".yaml",
    ".sql",
    ".sh",
    ".toml",
}

EXPECTED_MIGRATIONS = [
    "0001_accountless_control_plane.sql",
    "0002_admin_pro_codes.sql",
    "0003_pro_grants.sql",
    "0004_pro_grant_revocation.sql",
    "0005_pro_lifecycle.sql",
]


def stop(code: str) -> None:
    raise SystemExit("STOP: " + code)


def text(rel: str) -> str:
    return stable_read(rel).decode("utf-8", errors="strict")


def lexical_stat(rel: str):
    path = Path(rel)
    if path.is_absolute() or ".." in path.parts:
        stop("SECOND_BRAIN_NON_REGULAR_INPUT " + rel)
    current = ROOT
    for part in path.parts:
        current = current / part
        observed = current.lstat()
        if stat.S_ISLNK(observed.st_mode):
            stop("SECOND_BRAIN_SYMLINK_NOT_ALLOWED " + rel)
    return observed


def metadata(observed):
    return (observed.st_dev, observed.st_ino, observed.st_mode,
            observed.st_size, observed.st_mtime_ns, observed.st_ctime_ns)


def stable_read(rel: str) -> bytes:
    """Best-effort stable observation; not a global atomic filesystem snapshot."""
    try:
        before = lexical_stat(rel)
        if not stat.S_ISREG(before.st_mode):
            stop("SECOND_BRAIN_NON_REGULAR_INPUT " + rel)
        flags = os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0) | getattr(os, "O_NONBLOCK", 0)
        fd = os.open(ROOT / rel, flags)
        with os.fdopen(fd, "rb") as stream:
            opened = os.fstat(stream.fileno())
            if not stat.S_ISREG(opened.st_mode) or metadata(before) != metadata(opened):
                stop("SECOND_BRAIN_INPUT_CHANGED_DURING_READ " + rel)
            if metadata(lexical_stat(rel)) != metadata(opened):
                stop("SECOND_BRAIN_INPUT_CHANGED_DURING_READ " + rel)
            content = stream.read()
            if (metadata(os.fstat(stream.fileno())) != metadata(opened)
                    or metadata(lexical_stat(rel)) != metadata(opened)
                    or len(content) != opened.st_size):
                stop("SECOND_BRAIN_INPUT_CHANGED_DURING_READ " + rel)
        return content
    except OSError:
        stop("SECOND_BRAIN_INPUT_CHANGED_DURING_READ " + rel)


def candidate_regular(rel: str) -> bool:
    try:
        return stat.S_ISREG(lexical_stat(rel).st_mode)
    except FileNotFoundError:
        return False
    except OSError:
        stop("SECOND_BRAIN_INPUT_CHANGED_DURING_READ " + rel)


def require_marker(
    rel: str,
    marker: str,
    code: str,
) -> None:
    if marker.lower() not in text(rel).lower():
        stop(code)


def repository_files() -> list[str]:
    result = subprocess.run(
        [
            "git",
            "ls-files",
            "--cached",
            "--others",
            "--exclude-standard",
            "-z",
        ],
        cwd=ROOT,
        check=True,
        capture_output=True,
    )

    values = [
        item.decode("utf-8")
        for item in result.stdout.split(b"\0")
        if item
    ]

    files = []

    for rel in values:
        if rel == ASSET_REL:
            continue

        if not rel.startswith(EVIDENCE_PREFIXES):
            continue

        path = ROOT / rel

        if (
            path.suffix.lower() in EVIDENCE_EXTENSIONS
            and candidate_regular(rel)
        ):
            files.append(rel)

    return sorted(
        set(files)
    )


def basis_hash(
    files: list[str],
    observations: dict | None = None,
) -> str:
    digest = hashlib.sha256()
    digest.update(BASIS_POLICY_VERSION.encode("utf-8"))
    digest.update(b"\0")

    for rel in sorted(files):
        content_digest = hashlib.sha256(stable_read(rel)).digest()
        if observations is not None:
            observations[rel] = content_digest

        digest.update(
            rel.encode("utf-8")
        )
        digest.update(b"\0")
        digest.update(content_digest)
        digest.update(b"\0")

    return digest.hexdigest()


def migration_files() -> list[str]:
    root = (
        ROOT
        / "cloudflare"
        / "control-plane"
        / "migrations"
    )

    # Check the directory boundary before glob can traverse it.
    try:
        lexical_stat("cloudflare/control-plane/migrations")
    except FileNotFoundError:
        return []
    return sorted(
        path.relative_to(ROOT).as_posix()
        for path in root.glob("*.sql")
        if candidate_regular(path.relative_to(ROOT).as_posix())
    )


def canonical_basis_files(migrations: list[str]) -> list[str]:
    return sorted(set(repository_files()) | set(migrations))


def revalidate_inputs(files: list[str], migrations: list[str], observations: dict) -> None:
    current_migrations = migration_files()
    current_files = canonical_basis_files(current_migrations)
    if current_files != files or current_migrations != migrations:
        stop("SECOND_BRAIN_INPUT_SET_CHANGED")
    for rel in files:
        if hashlib.sha256(stable_read(rel)).digest() != observations[rel]:
            stop("SECOND_BRAIN_INPUT_CHANGED_AFTER_HASH " + rel)


def migration_state(files: list[str]) -> tuple[int, str]:
    names = sorted(Path(rel).name for rel in files)

    if names != EXPECTED_MIGRATIONS:
        stop(
            "D1_MIGRATION_SET_NOT_EXACT"
        )

    require_marker(
        "docs/wiki/Hot_Context.md",
        "D1 migration ledger reconciliation is complete",
        "D1_LEDGER_WIKI_EVIDENCE_MISSING",
    )

    return (
        len(names),
        "RECORDED_RECONCILIATION_0001_0005__LIVE_TARGET_NOT_VERIFIED",
    )


def verify_current_contract() -> None:
    # Repository markers guard documented contracts, not current/live acceptance.
    require_marker(
        "docs/wiki/Hot_Context.md",
        "Normal project compilation remains device-local",
        "DEVICE_LOCAL_EVIDENCE_MISSING",
    )

    require_marker(
        "docs/wiki/03_Architecture/"
        "Windows_Publisher_Authorization.md",
        "Physical acceptance proved:",
        "PUBLISHER_PHYSICAL_EVIDENCE_MISSING",
    )

    require_marker(
        "docs/wiki/03_Architecture/"
        "Windows_Publisher_Authorization.md",
        "Production custom-domain signing remains disabled",
        "PRODUCTION_PUBLISHER_BOUNDARY_MISSING",
    )

    require_marker(
        "docs/wiki/01_Project/Open_Questions.md",
        "Real Windows Authenticode",
        "WINDOWS_PHYSICAL_GATE_MISSING",
    )

    worker = text(
        "cloudflare/control-plane/src/index.mjs"
    )

    for route in (
        "/api/admin/windows-signing/grant",
        "/api/admin/windows-signing/consume",
    ):
        if route not in worker:
            stop(
                "PUBLISHER_ROUTE_MISSING"
            )

    auth_client = text(
        "android-app/app/src/main/java/"
        "com/appforge/studio/build/"
        "WindowsPublisherSigningAuthorizationClient.kt"
    )

    if (
        "windows-publisher-signing-v1"
        not in auth_client
    ):
        stop(
            "PUBLISHER_CLIENT_CONTRACT_MISSING"
        )


def build_snapshot(read_context: list | None = None) -> dict:
    verify_current_contract()

    migration_inventory = migration_files()
    evidence_files = canonical_basis_files(migration_inventory)

    if not evidence_files:
        stop(
            "EVIDENCE_FILE_SET_EMPTY"
        )

    migrations, ledger = migration_state(migration_inventory)

    wiki_pages = sum(
        rel.startswith("docs/wiki/") and rel.endswith(".md")
        for rel in evidence_files
    )
    quality_contract_files = sum(
        rel.startswith("quality/tests/") and rel.endswith(".test.js")
        for rel in evidence_files
    )
    android_unit_test_files = sum(
        rel.startswith("android-app/app/src/test/") and rel.endswith(".kt")
        for rel in evidence_files
    )

    observations = {}
    source_basis = basis_hash(evidence_files, observations)
    payload = {
        "schemaVersion": 2,
        "project": "AppForge Studio",
        "authority": "REPOSITORY_DERIVED",
        "generatedBy":
            "scripts/generate-second-brain-snapshot.py",
        "sourceBasisSha256":
            source_basis,
        "sourceBasisFileCount":
            len(evidence_files),
        "buildArchitecture":
            "DEVICE_LOCAL",
        "publisherAuthorization":
            "RECORDED_HISTORICAL_STAGING_DEVICE_ACCEPTANCE__CURRENT_HEAD_NOT_INFERRED",
        "d1Ledger":
            ledger,
        "d1Migrations":
            migrations,
        "wikiPages":
            wiki_pages,
        "qualityContractFiles":
            quality_contract_files,
        "androidUnitTestFiles":
            android_unit_test_files,
        "releaseGate":
            "REVIEW_REQUIRED",
        "productionPublisherEndpoint":
            "REPOSITORY_RECORDED_DISABLED_PENDING_REVIEW__LIVE_PRODUCTION_NOT_VERIFIED",
        "liveState":
            "NOT_LIVE_QUERY",
    }

    revalidate_inputs(evidence_files, migration_inventory, observations)
    if read_context is not None:
        read_context[:] = [evidence_files, migration_inventory, observations]
    return payload


def rendered(read_context: list | None = None) -> str:
    return (
        json.dumps(
            build_snapshot(read_context),
            ensure_ascii=False,
            indent=2,
        )
        + "\n"
    )


def main() -> None:
    parser = argparse.ArgumentParser()

    parser.add_argument(
        "--check",
        action="store_true",
    )

    args = parser.parse_args()

    read_context = []
    expected = rendered(read_context)

    if args.check:
        if not ASSET.is_file():
            stop(
                "SNAPSHOT_FILE_MISSING"
            )

        current = ASSET.read_text(
            encoding="utf-8"
        )

        if current != expected:
            stop(
                "SNAPSHOT_OUT_OF_DATE"
            )

        revalidate_inputs(*read_context)

        payload = json.loads(
            current
        )

        print(
            "SECOND_BRAIN_SCHEMA="
            + str(
                payload["schemaVersion"]
            )
        )

        print(
            "SECOND_BRAIN_BASIS="
            + payload[
                "sourceBasisSha256"
            ][:16]
        )

        print(
            "SECOND_BRAIN_EVIDENCE_FILES="
            + str(
                payload[
                    "sourceBasisFileCount"
                ]
            )
        )

        print(
            "SECOND_BRAIN_WIKI_PAGES="
            + str(
                payload["wikiPages"]
            )
        )

        print(
            "SECOND_BRAIN_QUALITY_CONTRACT_FILES="
            + str(
                payload[
                    "qualityContractFiles"
                ]
            )
        )

        print(
            "SECOND_BRAIN_D1_MIGRATIONS="
            + str(
                payload["d1Migrations"]
            )
        )

        print(
            "SECOND_BRAIN_SNAPSHOT_CHECK=PASS"
        )

        return

    ASSET.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    revalidate_inputs(*read_context)

    ASSET.write_text(
        expected,
        encoding="utf-8",
    )

    print(
        "SECOND_BRAIN_SNAPSHOT_GENERATED=PASS"
    )


if __name__ == "__main__":
    main()
