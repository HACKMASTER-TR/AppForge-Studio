#!/usr/bin/env python3
"""AppForge V9 Phase 2: deterministic, READ-ONLY R2 staging plan.

Never performs network requests, writes to R2, or enables distribution.
Inputs must be the existing, pinned V9 local package mirror. This tool does NOT
claim that Ubuntu PGP signatures or Packages-index closure have been verified.
"""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import sys

MIRROR_FILE = Path(__file__).with_name('appforge_package_mirror_v9.py')
H64 = re.compile(r'[0-9a-f]{64}\Z')
BUCKET = 'appforge-packages-staging'


def load_mirror():
    spec = importlib.util.spec_from_file_location('appforge_mirror_v9', MIRROR_FILE)
    if spec is None or spec.loader is None:
        raise RuntimeError('BLOCKED: V9 original mirror module missing')
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def fail(cond, why):
    if not cond:
        raise ValueError('BLOCKED: ' + why)


def safe_parents(path, root):
    root = Path(root).absolute()
    path = Path(path).absolute()
    fail(path.is_relative_to(root), 'store path escaped root')
    fail(not root.is_symlink(), 'symlinked store parent')
    cursor = root
    for segment in path.relative_to(root).parts:
        cursor = cursor / segment
        fail(not cursor.is_symlink(), 'symlinked object or ancestor')


def plan(repo, store, bucket=BUCKET):
    fail(bucket == BUCKET, 'unapproved R2 bucket')
    mirror = load_mirror()
    spec, summary = mirror.inspect_store(repo, store)
    root = Path(store).absolute()
    fail(not root.is_symlink(), 'symlinked mirror root')
    records = []
    seen = set()
    for obj in spec['objects']:
        sha = obj['sha256']
        fail(bool(H64.fullmatch(sha)), 'invalid object identity')
        fail(sha not in seen, 'duplicate object identity')
        seen.add(sha)
        file = mirror.object_path(root, sha)
        safe_parents(file, root)
        if not file.exists():
            continue
        fail(file.is_file() and not file.is_symlink(), 'nonregular cache object')
        size = file.stat().st_size
        fail(obj['size'] is None or size == obj['size'], 'package length mismatch')
        fail(mirror.sha_file(file) == sha, 'staged object hash mismatch')
        records.append({
            'key': f'objects/sha256/{sha[:2]}/{sha}',
            'sha256': sha,
            'bytes': size,
            'kind': obj['kind'],
        })
    records.sort(key=lambda a: a['key'])
    fail(len(records) == summary['present'], 'mirror inspection changed during planning')
    result = {
        'schema': 1,
        'stage': 'DRY_RUN_ONLY',
        'bucket': bucket,
        'snapshot': spec['snapshot'],
        'catalog_sha256': spec['catalog_sha256'],
        'profile_sha256': spec['profile_sha256'],
        'object_count_expected': summary['total'],
        'object_count_verified': len(records),
        'object_count_missing': summary['missing'],
        'signed_inrelease_verification': 'NOT_IMPLEMENTED',
        'packages_index_closure': 'NOT_IMPLEMENTED',
        'appforge_ed25519_release_signature': 'NOT_IMPLEMENTED',
        'r2_upload_allowed': False,
        'public_distribution_allowed': False,
        'android_client_enabled': False,
        'objects': records,
    }
    fail(not result['r2_upload_allowed'] and not result['public_distribution_allowed'], 'publish gate unexpectedly opened')
    return result


def canonical_json(obj):
    return (json.dumps(obj, sort_keys=True, ensure_ascii=True, separators=(',', ':')) + '\n').encode('utf-8')


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--repo', type=Path, default=Path(__file__).resolve().parents[2])
    ap.add_argument('--store', type=Path, required=True)
    ap.add_argument('--out', type=Path, help='Optional new plan file; never overwrites')
    args = ap.parse_args(argv)
    try:
        output = plan(args.repo, args.store)
        raw = canonical_json(output)
        if args.out:
            fail(not args.out.exists() and not args.out.is_symlink(), 'refusing to overwrite a plan')
            args.out.parent.mkdir(parents=True, exist_ok=True)
            with args.out.open('xb') as dest:
                dest.write(raw)
        print('=== APPFORGE V9 PHASE 2 R2 STAGING ===')
        print('BUCKET=' + output['bucket'])
        print('SNAPSHOT=' + output['snapshot'])
        print('VERIFIED=' + str(output['object_count_verified']))
        print('MISSING=' + str(output['object_count_missing']))
        print('PLAN_SHA256=' + hashlib.sha256(raw).hexdigest())
        print('SIGNED_INRELEASE=' + output['signed_inrelease_verification'])
        print('PACKAGES_CLOSURE=' + output['packages_index_closure'])
        print('R2_UPLOAD_ALLOWED=NO')
        print('PUBLIC_DISTRIBUTION_ALLOWED=NO')
        print('ANDROID_CLIENT_ENABLED=NO')
        print('MAIN_MODIFIED=NO')
    except (OSError, ValueError, RuntimeError) as exc:
        print(str(exc), file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
