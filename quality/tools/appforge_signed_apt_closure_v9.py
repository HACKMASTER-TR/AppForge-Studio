#!/usr/bin/env python3
"""AppForge V9 Phase 3A — offline Ubuntu signed-index and catalog closure.

This tool NEVER downloads, uploads, edits apt sources or enables distribution.
Only verification of existing immutable objects is supported. Real Ubuntu keyring
and the exact pinned Ubuntu 2018 signing fingerprint are mandatory via CLI.
"""
import argparse
from datetime import datetime, timedelta, timezone
from email.utils import parsedate_to_datetime
import hashlib
import importlib.util
from pathlib import Path
import re
import shutil
import subprocess
import sys
from urllib.parse import unquote, urlsplit

MIRROR_PATH = Path(__file__).with_name('appforge_package_mirror_v9.py')
SPEC = importlib.util.spec_from_file_location('appforge_v9_base_mirror', MIRROR_PATH)
assert SPEC is not None and SPEC.loader is not None
mirror = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(mirror)

SNAPSHOT = '20261018T000000Z'
CATALOG_PIN = '77efb669105e5c17fda85dbd653b78b22b0614eaf70c4db8c7d00a8bb4df9e2f'
PROFILE_PIN = '356b70db7922a69651eac67811f59c78a612709e134b00b324721dd93b6c3ea2'
UBUNTU_2018_FPR = 'F6ECB3762474EDA9D21B7022871920D1991BC93C'
SUITES = ('noble', 'noble-updates', 'noble-security', 'noble-backports')
COMPONENTS = ('main', 'restricted', 'universe', 'multiverse')
SHA = re.compile(r'[0-9a-f]{64}\Z')
MAX_PACKAGES_BYTES = 128 * 1024 * 1024


def require(ok, reason):
    if not ok:
        raise ValueError('BLOCKED: ' + reason)


def safe_object(store, digest, *, required=True):
    require(bool(SHA.fullmatch(digest)), 'invalid SHA-256')
    root = Path(store).absolute()
    path = mirror.object_path(root, digest)
    require(not root.is_symlink(), 'symlinked store root')
    for part in (root, *[root.joinpath(*path.relative_to(root).parts[:i]) for i in range(1, len(path.relative_to(root).parts) + 1)]):
        require(not part.is_symlink(), 'symlinked object ancestor')
    if not path.is_file():
        require(not required, 'missing pinned object ' + digest)
        return None
    require(path.stat().st_size <= MAX_PACKAGES_BYTES, 'oversized metadata object')
    require(mirror.sha_file(path) == digest, 'object hash mismatch: ' + digest)
    return path


def read_release_fields(payload):
    require(len(payload) < 2 * 1024 * 1024, 'oversized signed Release text')
    require(b'\x00' not in payload and b'\r' not in payload, 'invalid signed Release text')
    try:
        text = payload.decode('utf-8', 'strict')
    except UnicodeDecodeError as exc:
        raise ValueError('BLOCKED: non UTF-8 Release') from exc
    lines = text.splitlines()
    values, hashes = {}, {}
    in_sha = False
    for line in lines:
        if line == 'SHA256:':
            require(not in_sha, 'duplicate SHA256 stanza')
            in_sha = True
            continue
        if in_sha and line.startswith(' '):
            fields = line.split()
            require(len(fields) == 3, 'malformed signed SHA256 record')
            sha, raw_size, name = fields
            require(bool(SHA.fullmatch(sha)) and raw_size.isdecimal(), 'malformed signed hash')
            size = int(raw_size)
            require(size > 0 and size <= MAX_PACKAGES_BYTES, 'signed index size limit')
            require(name not in hashes, 'duplicate signed index')
            hashes[name] = (sha, size)
            continue
        in_sha = False
        if line and not line.startswith(' ') and ':' in line:
            key, val = line.split(':', 1)
            if key in ('Origin', 'Codename', 'Suite', 'Date', 'Valid-Until'):
                require(key not in values, 'duplicate release field: ' + key)
                values[key] = val.strip()
    require(bool(hashes), 'missing signed SHA256 table')
    return values, hashes


def utc_date(value):
    try:
        d = parsedate_to_datetime(value)
    except (ValueError, TypeError) as exc:
        raise ValueError('BLOCKED: invalid Release date') from exc
    require(d is not None and d.tzinfo is not None, 'ambiguous Release date')
    return d.astimezone(timezone.utc)


def verify_inrelease(path, pinned_hash, suite, keyring, signer=UBUNTU_2018_FPR, now=None):
    """GPGV validates clearsigned bytes, then outputs authenticated plaintext."""
    require(path.is_file() and not path.is_symlink(), 'missing InRelease')
    require(mirror.sha_file(path) == pinned_hash, 'pinned InRelease SHA-256 mismatch')
    require(Path(keyring).is_file() and not Path(keyring).is_symlink(), 'trusted keyring missing')
    gpgv = shutil.which('gpgv')
    require(gpgv is not None, 'gpgv is required; unsigned fallback forbidden')
    try:
        p = subprocess.run([gpgv, '--status-fd=2', '--keyring', str(keyring), '--output', '-', str(path)],
                           capture_output=True, timeout=30, check=False)
    except subprocess.TimeoutExpired as exc:
        raise ValueError('BLOCKED: GPG verification timeout') from exc
    require(p.returncode == 0, 'Ubuntu InRelease signature invalid')
    signatures = [x.split()[2] for x in p.stderr.decode('utf-8', errors='replace').splitlines()
                  if x.startswith('[GNUPG:] VALIDSIG ') and len(x.split()) >= 3]
    require(signatures == [signer], 'unexpected Ubuntu signing fingerprint')
    fields, sha_table = read_release_fields(p.stdout)
    require(fields.get('Origin') == 'Ubuntu' and fields.get('Codename') == 'noble'
            and fields.get('Suite') == suite, 'signed Release distribution identity mismatch')
    moment = now or datetime.now(timezone.utc)
    require('Date' in fields, 'missing signed Date')
    require(utc_date(fields['Date']) <= moment + timedelta(minutes=10), 'future-dated Release')
    if 'Valid-Until' in fields:
        require(utc_date(fields['Valid-Until']) >= moment, 'expired signed metadata')
    return sha_table


def verify_repo_pins(repo, expected_catalog=CATALOG_PIN, expected_profile=PROFILE_PIN):
    spec = mirror.manifest(repo)
    require(spec['snapshot'] == SNAPSHOT, 'snapshot date drift')
    require(spec['catalog_sha256'] == expected_catalog, 'pinned package catalog changed')
    require(spec['profile_sha256'] == expected_profile, 'pinned APT profile changed')
    return spec


def release_tables(repo, store, keyring, *, signer=UBUNTU_2018_FPR,
                   expected_catalog=CATALOG_PIN, expected_profile=PROFILE_PIN, now=None):
    spec = verify_repo_pins(repo, expected_catalog, expected_profile)
    indexes = {x['name']: x['sha256'] for x in spec['objects'] if x['kind'] == 'InRelease'}
    require(set(indexes) == set(SUITES), 'pinned suite list mismatch')
    tables = {}
    for suite in SUITES:
        f = safe_object(store, indexes[suite])
        tables[suite] = verify_inrelease(f, indexes[suite], suite, keyring, signer, now)
    return tables


def package_rows(index_bytes):
    """Yield only the fields needed to establish exact signed package identity."""
    try:
        text = index_bytes.decode('utf-8', 'strict')
    except UnicodeDecodeError as exc:
        raise ValueError('BLOCKED: invalid Packages encoding') from exc
    fields = {}
    for line in text.splitlines() + ['']:
        if not line:
            if fields:
                keys = ('Package', 'Version', 'Architecture', 'Filename', 'Size', 'SHA256')
                require(all(x in fields for x in keys), 'incomplete Packages record')
                require(fields['Size'].isascii() and fields['Size'].isdecimal(), 'invalid package size')
                require(bool(SHA.fullmatch(fields['SHA256'])), 'invalid package hash')
                yield tuple(fields[k] for k in keys)
                fields = {}
            continue
        if line.startswith(' '):
            continue  # dependency continuation line; never a package identity field
        require(':' in line, 'invalid Packages entry')
        key, value = line.split(':', 1)
        if key in ('Package', 'Version', 'Architecture', 'Filename', 'Size', 'SHA256'):
            require(key not in fields, 'duplicate Packages identity field')
            fields[key] = value.strip()


def verify_index_object(store, signed_indexes, name):
    require(name in signed_indexes, 'index missing from authenticated Release: ' + name)
    digest, size = signed_indexes[name]
    p = safe_object(store, digest)
    require(p.stat().st_size == size, 'signed Packages index size mismatch')
    return p.read_bytes()


def expected_package_rows(repo, architecture):
    cat = Path(repo) / mirror.ASSETS / 'apt-module-packages-v1.tsv'
    expected = set()
    for line in cat.read_text().splitlines():
        f = line.split('\t')
        if f[1] != architecture:
            continue
        pkg, ver, arch, sha, size, url = f[2:8]
        rel = unquote(urlsplit(url).path).split('/pool/', 1)
        require(len(rel) == 2 and rel[1] and '..' not in rel[1].split('/'), 'unsafe catalog package path')
        expected.add((pkg, ver, arch, 'pool/' + rel[1], size, sha))
    require(expected, 'no catalog records for architecture')
    return expected


def verify_architecture(repo, store, keyring, arch, *, signer=UBUNTU_2018_FPR,
                        expected_catalog=CATALOG_PIN, expected_profile=PROFILE_PIN, now=None):
    require(arch in ('arm64', 'amd64'), 'unsupported architecture')
    tables = release_tables(repo, store, keyring, signer=signer,
                            expected_catalog=expected_catalog, expected_profile=expected_profile, now=now)
    observed = set()
    nindexes = 0
    for suite in SUITES:
        for component in COMPONENTS:
            path = f'{component}/binary-{arch}/Packages'
            buf = verify_index_object(store, tables[suite], path)
            nindexes += 1
            for package in package_rows(buf):
                require(package[2] in (arch, 'all'), 'foreign package in signed index')
                observed.add(package)
    expected = expected_package_rows(repo, arch)
    missing = expected - observed
    require(not missing, 'signed Packages closure missing %d catalog identities' % len(missing))
    return {'signed_suites': len(tables), 'verified_indexes': nindexes,
            'catalog_identities': len(expected), 'catalog_closure': True}


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--repo', type=Path, default=Path(__file__).resolve().parents[2])
    ap.add_argument('--store', type=Path, required=True)
    ap.add_argument('--keyring', type=Path, required=True, help='Trusted preexisting Ubuntu archive keyring')
    ap.add_argument('--arch', choices=('arm64', 'amd64'), required=True)
    args = ap.parse_args(argv)
    try:
        verified = verify_architecture(args.repo, args.store, args.keyring, args.arch)
        print('SIGNED_INRELEASE=PASS')
        print('SIGNED_INDEXES='+str(verified['verified_indexes']))
        print('PACKAGES_CLOSURE=PASS')
        print('CATALOG_IDENTITIES='+str(verified['catalog_identities']))
        print('R2_UPLOAD_ALLOWED=NO')
        print('PUBLIC_DISTRIBUTION_ALLOWED=NO')
        print('ANDROID_CLIENT_ENABLED=NO')
        return 0
    except (ValueError, OSError, RuntimeError) as e:
        print('SIGNED_INRELEASE=NOT_VERIFIED', file=sys.stderr)
        print('PACKAGES_CLOSURE=BLOCKED', file=sys.stderr)
        print('R2_UPLOAD_ALLOWED=NO', file=sys.stderr)
        print('PUBLIC_DISTRIBUTION_ALLOWED=NO', file=sys.stderr)
        print(str(e), file=sys.stderr)
        return 1

if __name__ == '__main__':
    sys.exit(main())
