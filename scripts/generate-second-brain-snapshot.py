#!/usr/bin/env python3

import argparse
import hashlib
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

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
    path = ROOT / rel

    if not path.is_file():
        stop(
            "REQUIRED_SOURCE_MISSING_"
            + rel.replace("/", "_")
        )

    return path.read_text(
        encoding="utf-8",
        errors="strict",
    )


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
            path.is_file()
            and path.suffix.lower()
            in EVIDENCE_EXTENSIONS
        ):
            files.append(rel)

    return sorted(
        set(files)
    )


def basis_hash(
    files: list[str],
) -> str:
    digest = hashlib.sha256()

    for rel in files:
        path = ROOT / rel

        content_digest = hashlib.sha256(
            path.read_bytes()
        ).digest()

        digest.update(
            rel.encode("utf-8")
        )
        digest.update(b"\0")
        digest.update(content_digest)
        digest.update(b"\0")

    return digest.hexdigest()


def migration_state() -> tuple[int, str]:
    root = (
        ROOT
        / "cloudflare"
        / "control-plane"
        / "migrations"
    )

    names = sorted(
        path.name
        for path in root.glob("*.sql")
        if path.is_file()
    )

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
        "RECONCILED_0001_0005",
    )


def verify_current_contract() -> None:
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


def build_snapshot() -> dict:
    verify_current_contract()

    evidence_files = repository_files()

    if not evidence_files:
        stop(
            "EVIDENCE_FILE_SET_EMPTY"
        )

    migrations, ledger = migration_state()

    wiki_pages = len(
        list(
            (ROOT / "docs/wiki")
            .rglob("*.md")
        )
    )

    quality_contract_files = len(
        list(
            (ROOT / "quality/tests")
            .rglob("*.test.js")
        )
    )

    android_unit_test_files = len(
        list(
            (
                ROOT
                / "android-app"
                / "app"
                / "src"
                / "test"
            )
            .rglob("*.kt")
        )
    )

    return {
        "schemaVersion": 2,
        "project": "AppForge Studio",
        "authority": "REPOSITORY_DERIVED",
        "generatedBy":
            "scripts/generate-second-brain-snapshot.py",
        "sourceBasisSha256":
            basis_hash(evidence_files),
        "sourceBasisFileCount":
            len(evidence_files),
        "buildArchitecture":
            "DEVICE_LOCAL",
        "publisherAuthorization":
            "STAGING_DEVICE_ACCEPTED",
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
            "DISABLED",
        "liveState":
            "NOT_LIVE_QUERY",
    }


def rendered() -> str:
    return (
        json.dumps(
            build_snapshot(),
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

    expected = rendered()

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

    ASSET.write_text(
        expected,
        encoding="utf-8",
    )

    print(
        "SECOND_BRAIN_SNAPSHOT_GENERATED=PASS"
    )


if __name__ == "__main__":
    main()
