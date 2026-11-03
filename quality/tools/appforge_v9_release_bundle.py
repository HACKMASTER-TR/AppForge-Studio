#!/usr/bin/env python3
"""Prepare/verify AppForge V9 Ed25519 candidate release without private key access.

PREPARE: requires real signed Ubuntu Packages closure for BOTH host architectures,
all 218 immutable local objects, and a live GET+SHA256 for each R2 object. The
manifest is never installed or published here. No fake root, simulated witness,
production flag edits, R2 writes, or test signer accepted as an authority.
VERIFY: uses the exact SPKI pinned in the Android app; no caller-supplied key.
"""
import argparse
import base64
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import urllib.request
from contextlib import closing

ROOT = Path(__file__).resolve().parents[2]
TOOLS = Path(__file__).resolve().parent
CATALOG_PIN = '77efb669105e5c17fda85dbd653b78b22b0614eaf70c4db8c7d00a8bb4df9e2f'
PROFILE_PIN = '356b70db7922a69651eac67811f59c78a612709e134b00b324721dd93b6c3ea2'
SNAPSHOT = '20261018T000000Z'
SOURCE_COMMIT = '721cfcbaf121631e8b531d8361b6ec7a079aed3f'
RELEASE_ID = 'appforge-v9-20261018-001'
RELEASE_SEQUENCE = 2026101801
BUCKET = 'appforge-packages-staging'
BASE_URL = 'https://appforge-v9-packages-readonly.28550040284a.workers.dev'
UBUNTU_FPR = 'F6ECB3762474EDA9D21B7022871920D1991BC93C'
SPKI_B64 = 'MCowBQYDK2VwAyEA0JMW5UgobzPUxqVshfG7INhMNGFCM8gPq6ZhsD/KDqg='
SPKI_SHA = 'c612b3a0cbfc6d5478a23eba046ba1d62b32bd9b59fb5e54a7eaa66b86e3018e'
DOMAIN = b'APPFORGE_V9_RELEASE_V1\n'
SHA_RE = re.compile(r'[0-9a-f]{64}\Z')
SUITES = ('noble', 'noble-updates', 'noble-security', 'noble-backports')
EXPECTED = dict(zip(SUITES, (
    'cdb2f31d809f589719a53c6ad15f255b27569c4059542ada282aaa21b8e164b0',
    'f8db4261891aad2963b51de65b6f2a7f52250df02109b8a16fcfe5692d2187c3',
    'f444994ac2ed9f6a64365d5baee6f9540f2cad3320d7f574b0efd4c0d514c32c',
    '9bb3c4bace14bf88014e5a66c8d431e65da66fd055dc7fdb45423305cf132cce',
)))
MAX_MANIFEST = 12 * 1024 * 1024
MAX_OBJECT = 40 * 1024 * 1024
EXPECTED_TOTAL_BYTES = 366_821_311


class Blocked(ValueError):
    pass


def require(condition, reason):
    if not condition:
        raise Blocked('BLOCKED: ' + reason)


def sha_bytes(data):
    return hashlib.sha256(data).hexdigest()


def load_local(name, filename):
    spec = importlib.util.spec_from_file_location(name, TOOLS / filename)
    require(spec is not None and spec.loader is not None, 'missing existing V9 verifier')
    obj = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(obj)
    return obj


def no_duplicates(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, 'duplicate JSON key')
        result[key] = value
    return result


def canonical(doc):
    return (json.dumps(doc, sort_keys=True, ensure_ascii=True,
                       separators=(',', ':'), allow_nan=False) + '\n').encode('utf-8')


def object_key(sha):
    require(isinstance(sha, str) and SHA_RE.fullmatch(sha), 'invalid digest')
    return 'objects/sha256/' + sha[:2] + '/' + sha


def checked_objects(mirror, repo, store):
    spec, summary = mirror.inspect_store(repo, store)
    require(spec['snapshot'] == SNAPSHOT and spec['catalog_sha256'] == CATALOG_PIN and
            spec['profile_sha256'] == PROFILE_PIN, 'immutable catalog/profile drift')
    require(summary['total'] == 218 and summary['present'] == 218 and summary['missing'] == 0,
            '218 local source objects are required')
    records, inrelease = [], {}
    for entry in spec['objects']:
        digest = entry['sha256']
        file = mirror.object_path(Path(store), digest)
        require(file.is_file() and not file.is_symlink(), 'missing/symlinked object')
        size = file.stat().st_size
        require(0 < size <= MAX_OBJECT and (entry['size'] is None or entry['size'] == size),
                'local object size drift')
        require(mirror.sha_file(file) == digest, 'local object hash drift')
        kind = entry['kind']
        require(kind in ('deb', 'InRelease'), 'unknown catalog record')
        if kind == 'InRelease':
            name = entry['name']
            require(name in EXPECTED and digest == EXPECTED[name] and name not in inrelease,
                    'untrusted Ubuntu release identity')
            inrelease[name] = digest
        records.append({'key': object_key(digest), 'sha256': digest,
                        'size': size, 'kind': kind})
    require(len(records) == 218 and len(set(x['sha256'] for x in records)) == 218,
            'catalog count or duplicate')
    require(sum(x['kind'] == 'deb' for x in records) == 214 and inrelease == EXPECTED,
            'package/release count drift')
    require(sum(x['size'] for x in records) == EXPECTED_TOTAL_BYTES,
            'source aggregate size drift')
    return sorted(records, key=lambda x: x['key'])


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        raise Blocked('BLOCKED: redirected R2 object')


def check_remote_object(item, opener=None):
    """Full-body hash; HEAD/metadata alone NEVER establishes remote_verified."""
    url = BASE_URL + '/v9/' + item['key']
    request = urllib.request.Request(url, headers={'Accept-Encoding': 'identity',
                              'User-Agent': 'AppForge-V9-Signed-Candidate-Remote-Audit/1.0'})
    opener = opener or urllib.request.build_opener(NoRedirect)
    with closing(opener.open(request, timeout=45)) as response:
        require(response.status == 200 and response.geturl() == url,
                'remote HTTP/URL mismatch')
        require(response.headers.get('Content-Encoding') in (None, 'identity'),
                'unexpected compression')
        length = response.headers.get('Content-Length')
        require(length is not None and length.isascii() and length.isdecimal() and
                int(length) == item['size'], 'remote content length mismatch')
        digest = hashlib.sha256()
        total = 0
        while True:
            block = response.read(256 * 1024)
            if not block:
                break
            total += len(block)
            require(total <= item['size'], 'remote object oversized')
            digest.update(block)
        require(total == item['size'] and digest.hexdigest() == item['sha256'],
                'remote SHA-256 mismatch')
    return total


def verify_apt_closure(repo, store, keyring):
    require(Path(keyring).is_file() and not Path(keyring).is_symlink(),
            'preinstalled trusted Ubuntu keyring absent')
    signed = load_local('appforge_v9_bundle_signed_closure',
                        'appforge_signed_apt_closure_v9.py')
    summary = {}
    for arch, identity_count in (('arm64', 139), ('amd64', 94)):
        result = signed.verify_architecture(repo, store, keyring, arch)
        require(result.get('signed_suites') == 4 and
                result.get('verified_indexes') == 16 and
                result.get('catalog_closure') is True and
                result.get('catalog_identities') == identity_count,
                'signed Ubuntu Packages index closure failed for ' + arch)
        summary[arch] = result
    return summary


def make_manifest(records):
    require(len(records) == 218 and
            all(set(x) == {'key', 'sha256', 'size', 'kind'} and
                x['key'] == object_key(x['sha256']) and
                type(x['size']) is int and 0 < x['size'] <= MAX_OBJECT and
                x['kind'] in ('deb', 'InRelease') for x in records),
            'invalid release objects')
    require(len({x['key'] for x in records}) == 218 and
            sum(x['kind'] == 'deb' for x in records) == 214 and
            sum(x['kind'] == 'InRelease' for x in records) == 4,
            'duplicate/wrong release object count')
    require({x['sha256'] for x in records if x['kind'] == 'InRelease'} == set(EXPECTED.values()),
            'pinned Ubuntu InRelease drift')
    return {
        'schema': 'appforge.release.v1', 'channel': 'candidate',
        'release_id': RELEASE_ID, 'sequence': RELEASE_SEQUENCE,
        'snapshot': SNAPSHOT, 'origin_repo_commit': SOURCE_COMMIT,
        'catalog_sha256': CATALOG_PIN, 'profile_sha256': PROFILE_PIN,
        'bucket': BUCKET, 'metadata_expired': False,
        'package_count': 214, 'total_objects': 218,
        'remote_verified': {'objects': 218, 'sha256_verified': True},
        'ubuntu_inrelease': [{'suite': suite, 'sha256': EXPECTED[suite],
                              'signer': UBUNTU_FPR, 'expired': False}
                             for suite in SUITES],
        'objects': sorted(records, key=lambda x: x['key']),
    }


def validate_manifest_bytes(data):
    require(type(data) is bytes and 0 < len(data) <= MAX_MANIFEST, 'manifest length')
    try:
        doc = json.loads(data.decode('utf-8', 'strict'), object_pairs_hook=no_duplicates)
    except (ValueError, UnicodeError) as exc:
        raise Blocked('BLOCKED: malformed JSON manifest') from exc
    require(type(doc) is dict and canonical(doc) == data, 'noncanonical manifest bytes')
    fields = {'schema', 'channel', 'release_id', 'sequence', 'snapshot',
              'origin_repo_commit', 'catalog_sha256', 'profile_sha256',
              'bucket', 'metadata_expired', 'package_count', 'total_objects',
              'remote_verified', 'ubuntu_inrelease', 'objects'}
    require(set(doc) == fields, 'missing/unknown manifest field')
    require(doc['schema'] == 'appforge.release.v1' and doc['channel'] == 'candidate' and
            doc['release_id'] == RELEASE_ID and type(doc['sequence']) is int and
            doc['sequence'] == RELEASE_SEQUENCE and doc['snapshot'] == SNAPSHOT and
            doc['origin_repo_commit'] == SOURCE_COMMIT and
            doc['catalog_sha256'] == CATALOG_PIN and doc['profile_sha256'] == PROFILE_PIN and
            doc['bucket'] == BUCKET and doc['metadata_expired'] is False and
            type(doc['package_count']) is int and doc['package_count'] == 214 and
            type(doc['total_objects']) is int and doc['total_objects'] == 218,
            'V9 policy fields do not match Android trust policy')
    require(doc['remote_verified'] == {'objects': 218, 'sha256_verified': True},
            'remote verification not confirmed')
    suites = doc['ubuntu_inrelease']
    require(type(suites) is list and suites == [
        {'suite': suite, 'sha256': EXPECTED[suite], 'signer': UBUNTU_FPR, 'expired': False}
        for suite in SUITES], 'Ubuntu release metadata pins do not match')
    objs = doc['objects']
    require(type(objs) is list and len(objs) == 218, 'release object count')
    require(objs == sorted(objs, key=lambda x: x['key']), 'noncanonical object order')
    require(sum(x.get('size', 0) for x in objs if type(x) is dict and type(x.get('size')) is int)
            == EXPECTED_TOTAL_BYTES, 'release object aggregate bytes')
    require(make_manifest(objs) == doc, 'release content drift')
    return doc


def verify_signature(manifest, signature, spki=base64.b64decode(SPKI_B64)):
    """Test helper accepts SPKI; CLI calls ONLY the pinned production SPKI."""
    require(type(signature) is bytes and len(signature) == 64 and
            type(spki) is bytes and len(spki) == 44, 'signature or SPKI length')
    require(subprocess.run(['openssl', 'version'], stdout=subprocess.DEVNULL,
                           stderr=subprocess.DEVNULL, check=False).returncode == 0,
            'openssl is required')
    with tempfile.TemporaryDirectory(prefix='appforge-v9-verify-') as folder:
        p = Path(folder)
        (p / 'spki.der').write_bytes(spki)
        (p / 'signed.dat').write_bytes(DOMAIN + manifest)
        (p / 'sig.dat').write_bytes(signature)
        done = subprocess.run(['openssl', 'pkeyutl', '-verify', '-pubin',
                               '-keyform', 'DER', '-inkey', str(p / 'spki.der'),
                               '-rawin', '-in', str(p / 'signed.dat'),
                               '-sigfile', str(p / 'sig.dat')],
                              capture_output=True, timeout=20, check=False)
        require(done.returncode == 0, 'Ed25519 signature invalid')


def verify_bundle(manifest, signature):
    require(sha_bytes(base64.b64decode(SPKI_B64)) == SPKI_SHA,
            'compiled Android SPKI pin mismatch')
    validate_manifest_bytes(manifest)
    verify_signature(manifest, signature)


def create_once(path, content):
    p = Path(path)
    require(p.parent.is_dir() and not p.is_symlink() and not p.exists(),
            'output directory missing or file already exists')
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, 'O_NOFOLLOW', 0)
    fd = os.open(p, flags, 0o600)
    with os.fdopen(fd, 'wb') as dest:
        dest.write(content)
        dest.flush()
        os.fsync(dest.fileno())


def cli(argv=None):
    p = argparse.ArgumentParser(description=__doc__)
    sub = p.add_subparsers(dest='cmd', required=True)
    prepare = sub.add_parser('prepare', help='real local closure plus 218 LIVE remote SHA-256')
    prepare.add_argument('--repo', type=Path, default=ROOT)
    prepare.add_argument('--store', type=Path, required=True)
    prepare.add_argument('--keyring', type=Path, required=True)
    prepare.add_argument('--out', type=Path, required=True)
    check = sub.add_parser('verify-bundle', help='verify with Android compiled SPKI')
    check.add_argument('--manifest', type=Path, required=True)
    check.add_argument('--signature', type=Path, required=True)
    a = p.parse_args(argv)
    try:
        if a.cmd == 'verify-bundle':
            require(a.manifest.is_file() and a.signature.is_file() and
                    not a.manifest.is_symlink() and not a.signature.is_symlink(),
                    'unsafe signed bundle')
            verify_bundle(a.manifest.read_bytes(), a.signature.read_bytes())
            print('V9_PINNED_ED25519=PASS')
            print('V9_BUNDLE_POLICY=PASS')
            print('ANDROID_AUTOMATIC_ROUTING=DISABLED')
            return 0
        require(not a.out.exists() and not a.out.is_symlink(), 'output exists')
        mirror = load_local('appforge_v9_bundle_mirror', 'appforge_package_mirror_v9.py')
        records = checked_objects(mirror, a.repo, a.store)
        print('V9_LOCAL_OBJECT_SHA256=PASS;OBJECTS=218', flush=True)
        signed = verify_apt_closure(a.repo, a.store, a.keyring)
        print('V9_UBUNTU_GPG_CLOSURE=PASS;ARM64=' +
              str(signed['arm64']['verified_indexes']) + ';AMD64=' +
              str(signed['amd64']['verified_indexes']), flush=True)
        total = 0
        for i, rec in enumerate(records, 1):
            total += check_remote_object(rec)
            if i % 25 == 0 or i == len(records):
                print(f'LIVE_REMOTE_SHA256_PROGRESS={i}/218', flush=True)
        require(total == EXPECTED_TOTAL_BYTES, 'remote aggregate byte mismatch')
        print('V9_REMOTE_OBJECT_SHA256=PASS;OBJECTS=218', flush=True)
        raw = canonical(make_manifest(records))
        validate_manifest_bytes(raw)
        create_once(a.out, raw)
        print('V9_CANDIDATE_MANIFEST=PREPARED_UNSIGNED')
        print('MANIFEST_SHA256=' + sha_bytes(raw))
        print('MANIFEST_PATH=' + str(a.out))
        print('V9_RELEASE_SIGNATURE=NOT_PRESENT')
        print('ANDROID_AUTOMATIC_ROUTING=DISABLED')
        print('GITHUB_PUSH=NO;CLOUDFLARE_MUTATIONS=0;PLAY_PRODUCTION=UNCHANGED')
        return 0
    except (Blocked, OSError, ValueError, subprocess.SubprocessError, ImportError,
            urllib.error.URLError) as exc:
        print('V9_SIGNED_CANDIDATE_GATE=BLOCKED', flush=True)
        print('REASON=' + str(exc)[:300], flush=True)
        print('ANDROID_AUTOMATIC_ROUTING=DISABLED', flush=True)
        return 2


if __name__ == '__main__':
    sys.exit(cli())
