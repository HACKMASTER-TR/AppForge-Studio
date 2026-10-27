#!/usr/bin/env python3
"""Offline-only V9 Phase 3D evidence manifest; NO signer, R2 client or publication.

Evidence is anchored to pre-pinned Ubuntu 2018 GPG fingerprint and SHA-256,
not an AppForge Ed25519 release signature. Enabling any distribution is forbidden.
"""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import stat
import sys

SHA = re.compile(r'[a-f0-9]{64}\Z')
SNAPSHOT = '20261018T000000Z'
CATALOG_PIN = '77efb669105e5c17fda85dbd653b78b22b0614eaf70c4db8c7d00a8bb4df9e2f'
PROFILE_PIN = '356b70db7922a69651eac67811f59c78a612709e134b00b324721dd93b6c3ea2'
BUCKET = 'appforge-packages-staging'


def require(condition, message):
    if not condition:
        raise ValueError('BLOCKED: ' + message)


def canonical(data):
    return (json.dumps(data, sort_keys=True, ensure_ascii=True, separators=(',', ':')) + '\n').encode('ascii')


def read_module(path, name):
    spec = importlib.util.spec_from_file_location(name, path)
    require(spec is not None and spec.loader is not None, 'tool missing: ' + str(path))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def check_object_path(store, digest):
    require(bool(SHA.fullmatch(digest)), 'invalid SHA-256 filename')
    root = Path(store).absolute()
    require(root.is_dir() and not root.is_symlink(), 'invalid store')
    path = root / 'objects' / 'sha256' / digest[:2] / digest
    cursor = root
    for part in path.relative_to(root).parts:
        cursor = cursor / part
        require(not cursor.is_symlink(), 'symlink in object path')
    require(path.is_file(), 'object absent: ' + digest)
    st = path.stat()
    require(stat.S_ISREG(st.st_mode) and st.st_nlink == 1, 'nonregular or hardlinked object')
    return path


def add_cas_entry(cas, sha, size, kind):
    require(bool(SHA.fullmatch(sha)), 'invalid SHA-256')
    require(type(size) is int and 0 <= size < 1 << 32, 'invalid object byte length')
    require(kind in ('deb', 'InRelease', 'Packages'), 'invalid object kind')
    if sha not in cas:
        cas[sha] = {'sha256': sha, 'bytes': size,
                    'key': f'objects/sha256/{sha[:2]}/{sha}', 'roles': [kind]}
    else:
        require(cas[sha]['bytes'] == size, 'same SHA-256 with conflicting byte count')
        if kind not in cas[sha]['roles']:
            cas[sha]['roles'].append(kind)
            cas[sha]['roles'].sort()


def verify_storage(store, entries, mirror):
    total_bytes = 0
    for item in entries:
        path = check_object_path(store, item['sha256'])
        require(path.stat().st_size == item['bytes'], 'object size mismatch: ' + item['sha256'])
        require(mirror.sha_file(path) == item['sha256'], 'object SHA-256 mismatch: ' + item['sha256'])
        total_bytes += item['bytes']
    return total_bytes


def collect(repo, store, keyring):
    repo, store, keyring = map(Path, (repo, store, keyring))
    base = repo / 'quality/tools'
    mirror = read_module(base / 'appforge_package_mirror_v9.py', 'audit_mirror')
    signed = read_module(base / 'appforge_signed_apt_closure_v9.py', 'audit_signed')
    r2 = read_module(base / 'appforge_r2_plan_v9.py', 'audit_r2')
    require(keyring.is_file() and not keyring.is_symlink(), 'trusted Ubuntu keyring missing')
    spec, status = mirror.inspect_store(repo, store)
    require(spec['snapshot'] == SNAPSHOT and
            spec['catalog_sha256'] == CATALOG_PIN and
            spec['profile_sha256'] == PROFILE_PIN, 'pinned catalog/profile mismatch')
    require(status['total'] == 218 and status['present'] == 218 and status['missing'] == 0,
            'original mirror not complete')
    require(status['known_package_bytes'] == 366187036, 'DEB catalog total byte drift')
    specs = spec['objects']
    debs = [x for x in specs if x['kind'] == 'deb']
    inreleases = [x for x in specs if x['kind'] == 'InRelease']
    require(len(debs) == 214 and len(inreleases) == 4, 'original manifest object count mismatch')

    for arch, count in (('arm64',139),('amd64',94)):
        verified = signed.verify_architecture(repo, store, keyring, arch)
        require(verified == {'signed_suites': 4, 'verified_indexes': 16,
                             'catalog_identities': count, 'catalog_closure': True},
                'signed index/catalog closure failed: ' + arch)

    tables = signed.release_tables(repo, store, keyring)
    require(tuple(tables) == tuple(signed.SUITES), 'authenticated suite set mismatch')
    cas = {}
    original = []
    for obj in specs:
        path = check_object_path(store, obj['sha256'])
        size = path.stat().st_size
        require(obj['size'] is None or obj['size'] == size, 'original object length mismatch')
        add_cas_entry(cas, obj['sha256'], size, obj['kind'])
        original.append({'sha256': obj['sha256'], 'bytes': size, 'kind': obj['kind'],
                         'key': f'objects/sha256/{obj["sha256"][:2]}/{obj["sha256"]}'})
    original.sort(key=lambda x: x['key'])
    indexes = []
    for suite in signed.SUITES:
        for arch in ('arm64', 'amd64'):
            for component in signed.COMPONENTS:
                name = f'{component}/binary-{arch}/Packages'
                require(name in tables[suite], 'index absent in signed Release table')
                digest, nbytes = tables[suite][name]
                require(bool(SHA.fullmatch(digest)) and 0 <= nbytes <= signed.MAX_PACKAGES_BYTES,
                        'invalid signed Packages record')
                path = check_object_path(store, digest)
                require(path.stat().st_size == nbytes, 'signed Packages size mismatch')
                add_cas_entry(cas, digest, nbytes, 'Packages')
                indexes.append({'suite': suite, 'architecture': arch, 'component': component,
                                'path': name, 'sha256': digest, 'bytes': nbytes,
                                'key': f'objects/sha256/{digest[:2]}/{digest}'})
    require(len(indexes) == 32, 'expected 32 signed Packages indexes')
    indexes.sort(key=lambda x: (x['suite'], x['architecture'], x['component']))
    entries = sorted(cas.values(), key=lambda x: x['key'])
    distinct_bytes = verify_storage(store, entries, mirror)

    r2plan = r2.plan(repo, store)
    require(r2plan['bucket'] == BUCKET and r2plan['object_count_verified'] == 218 and
            r2plan['object_count_missing'] == 0 and
            r2plan['r2_upload_allowed'] is False and
            r2plan['public_distribution_allowed'] is False, 'R2 staging dry run gate mismatch')
    require([(a['key'],a['sha256'],a['bytes'],a['kind']) for a in r2plan['objects']] ==
            [(a['key'],a['sha256'],a['bytes'],a['kind']) for a in original],
            'R2 dry-run original object mismatch')

    report = {
        'schema': 1,
        'stage': 'OFFLINE_EVIDENCE_ONLY',
        'snapshot': SNAPSHOT,
        'catalog_sha256': CATALOG_PIN,
        'profile_sha256': PROFILE_PIN,
        'ubuntu_archive_signer_fingerprint': signed.UBUNTU_2018_FPR,
        'ubuntu_inrelease_verified': 4,
        'signed_packages_indexes_verified': 32,
        'signed_catalog_closure': {'arm64': 139, 'amd64': 94},
        'original_objects_verified': 218,
        'original_deb_objects_verified': 214,
        'distinct_content_addressed_objects': len(entries),
        'distinct_content_addressed_bytes': distinct_bytes,
        'r2_staging_bucket_name_for_future_review_only': BUCKET,
        'appforge_ed25519_release_signature': 'NOT_PRESENT',
        'artifact_signature_trust_status': 'UNSIGNED_NOT_FOR_RELEASE',
        'r2_upload_allowed': False,
        'public_distribution_allowed': False,
        'android_client_enabled': False,
        'apt_ready': False,
        'all_engines_ready': False,
        'original_objects': original,
        'signed_packages_indexes': indexes,
        'distinct_objects': entries,
    }
    require(len(entries) >= 218, 'CAS entry count inconsistent')
    return report


def write_exclusive_or_identical(path, data):
    path = Path(path)
    require(not path.is_symlink(), 'evidence destination symlink')
    if path.exists():
        require(path.is_file() and path.read_bytes() == data, 'existing evidence differs')
        return 'VERIFIED_REUSED'
    with path.open('xb') as out:
        out.write(data)
        out.flush()
        os.fsync(out.fileno())
    return 'CREATED'


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--repo', type=Path, default=Path.cwd())
    ap.add_argument('--store', type=Path, default=Path.home() / 'appforge-v9-local-mirror')
    ap.add_argument('--keyring', type=Path, default=Path('android-app/app/src/test/resources/apt-snapshot/ubuntu-archive-keyring.gpg'))
    ap.add_argument('--output-dir', type=Path, required=True)
    args = ap.parse_args(argv)
    try:
        output = args.output_dir.absolute()
        require(not output.is_symlink(), 'output symlink forbidden')
        output.mkdir(parents=True, mode=0o700, exist_ok=True)
        require(output.is_dir() and output.stat().st_mode & 0o077 == 0,
                'output directory permissions too broad')
        report = collect(args.repo, args.store, args.keyring)
        body = canonical(report)
        digest = hashlib.sha256(body).hexdigest()
        state = write_exclusive_or_identical(output / 'appforge-v9-phase3d-audit-manifest.json', body)
        sha_body = digest + '  appforge-v9-phase3d-audit-manifest.json\n'
        write_exclusive_or_identical(output / 'SHA256SUMS', sha_body.encode('ascii'))
        print('=== APPFORGE V9 PHASE 3D OFFLINE EVIDENCE ===')
        print('SIGNED_INRELEASE=4/4_PASS')
        print('SIGNED_PACKAGES_INDEXES=32/32_PASS')
        print('CATALOG_CLOSURE_arm64=PASS')
        print('CATALOG_CLOSURE_amd64=PASS')
        print('ORIGINAL_OBJECTS=218/218')
        print('DEB_OBJECTS=214/214')
        print('DISTINCT_CAS_OBJECTS=' + str(report['distinct_content_addressed_objects']))
        print('DISTINCT_CAS_BYTES=' + str(report['distinct_content_addressed_bytes']))
        print('MANIFEST_SHA256=' + digest)
        print('MANIFEST_WRITE=' + state)
        print('RELEASE_SIGNATURE=NOT_PRESENT')
        print('R2_UPLOAD=NO')
        print('PUBLIC_DISTRIBUTION=DISABLED')
        print('ANDROID_CLIENT_ENABLED=NO')
        print('ALL_ENGINES_READY=false')
        return 0
    except (OSError, ValueError, RuntimeError, AssertionError) as exc:
        print('PHASE3D_EVIDENCE=BLOCKED', file=sys.stderr)
        print(str(exc), file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
