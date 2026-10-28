#!/usr/bin/env python3
"""Fail-closed V9 *evidence inventory*, never grants production permission.

A hash of a self-supplied file is not an independent attestation. Consequently
this script NEVER prints READY, even when all evidence records are present.
It only tells reviewers which evidence is missing and whether the inventory is
structurally consistent. No network, signing, Android or production changes.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re

SHA = re.compile(r'[a-f0-9]{64}\Z')
GIT_SHA = re.compile(r'[a-f0-9]{40}\Z')
EVIDENCE = (
    'independent_witness_deployment',
    'remote_durable_high_water_rollback_drill',
    'trusted_verifier_independent_phase3g',
    'device_enrollment_and_authentication',
    'protected_signer_and_rotation',
    'separate_process_and_key_isolation',
    'signed_fresh_receipt_android_check',
    'multi_device_android_acceptance',
    'power_failure_and_concurrency_acceptance',
    'remote_ci_security_gate',
    'operator_security_review',
)
MAX_MANIFEST = 128 * 1024


def fail(message):
    raise ValueError('BLOCKED: ' + message)


def unique(pairs):
    obj = {}
    for k, v in pairs:
        if k in obj:
            fail('duplicate key')
        obj[k] = v
    return obj


def load(path):
    p = Path(path)
    if p.is_symlink() or not p.is_file():
        fail('manifest is not a regular file')
    if not 0 < p.stat().st_size <= MAX_MANIFEST:
        fail('manifest size')
    try:
        doc = json.loads(p.read_text(encoding='utf-8'), object_pairs_hook=unique)
    except (ValueError, UnicodeError) as exc:
        fail('invalid JSON: ' + str(exc)[:100])
    if type(doc) is not dict or set(doc) != {'schema', 'source_commit', 'items'} or type(doc['schema']) is not int or doc['schema'] != 1:
        fail('invalid manifest schema')
    if type(doc['source_commit']) is not str or not GIT_SHA.fullmatch(doc['source_commit']):
        fail('source commit must be full 40-character Git SHA-1')
    if type(doc['items']) is not dict or set(doc['items']) != set(EVIDENCE):
        fail('missing/extra evidence category')
    return doc


def evaluate(path, *, expected_source=None):
    doc = load(path)
    if expected_source is not None and doc['source_commit'] != expected_source:
        fail('source commit differs from pinned baseline')
    base = Path(path).resolve().parent
    verified, missing = [], []
    for name in EVIDENCE:
        item = doc['items'][name]
        if type(item) is not dict or set(item) != {'status', 'evidence_file', 'sha256', 'reviewed_utc'}:
            fail('evidence field mismatch: ' + name)
        status = item['status']
        if status not in ('MISSING', 'SUBMITTED') or type(status) is not str:
            fail('invalid status: ' + name)
        if status == 'MISSING':
            if item['evidence_file'] is not None or item['sha256'] is not None or item['reviewed_utc'] is not None:
                fail('missing evidence cannot contain material: ' + name)
            missing.append(name)
            continue
        if not all(type(item[k]) is str for k in ('evidence_file', 'sha256', 'reviewed_utc')):
            fail('invalid evidence fields: ' + name)
        rel = Path(item['evidence_file'])
        if rel.is_absolute() or '..' in rel.parts or len(rel.parts) != 1:
            fail('unsafe evidence path: ' + name)
        full = base / rel
        if full.is_symlink() or not full.is_file() or not 0 < full.stat().st_size <= 10 * 1024 * 1024:
            fail('untrusted evidence file: ' + name)
        if not SHA.fullmatch(item['sha256']):
            fail('invalid evidence sha256: ' + name)
        actual = hashlib.sha256(full.read_bytes()).hexdigest()
        if actual != item['sha256']:
            fail('evidence digest mismatch: ' + name)
        try:
            instant = datetime.fromisoformat(item['reviewed_utc'].replace('Z','+00:00'))
        except ValueError:
            fail('invalid review timestamp: ' + name)
        if instant.tzinfo is None or instant > datetime.now(timezone.utc):
            fail('future or naive review timestamp: ' + name)
        verified.append(name)
    # Deliberately no "READY" branch: presence+digest != independent trust.
    return {'submitted_unverified': verified, 'missing': missing, 'production_ready': False}


def template(commit):
    if not isinstance(commit,str) or not GIT_SHA.fullmatch(commit):
        fail('source commit invalid')
    return {'schema': 1, 'source_commit': commit,
            'items': {name: {'status': 'MISSING', 'evidence_file': None, 'sha256': None,
                             'reviewed_utc': None} for name in EVIDENCE}}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--manifest',type=Path)
    p.add_argument('--expected-source')
    p.add_argument('--emit-template',type=Path)
    a = p.parse_args()
    if a.emit_template:
        data = template(a.expected_source)
        a.emit_template.write_text(json.dumps(data,indent=2,sort_keys=True)+'\n')
        print('RELEASE_EVIDENCE_TEMPLATE=CREATED')
        print('V9_PRODUCTION_READINESS=BLOCKED')
        return 0
    if a.manifest is None:
        p.error('--manifest required unless --emit-template provided')
    try:
        result = evaluate(a.manifest,expected_source=a.expected_source)
        for item in result['missing']:
            print('MISSING_EVIDENCE='+item)
        print('SUBMITTED_BUT_NOT_INDEPENDENTLY_ATTESTED='+str(len(result['submitted_unverified'])))
        print('MISSING_EVIDENCE_COUNT='+str(len(result['missing'])))
    except (ValueError,OSError) as exc:
        print('EVIDENCE_INVENTORY=BLOCKED')
        print('REASON='+str(exc)[:180])
        print('V9_PRODUCTION_READINESS=BLOCKED')
        print('ALL_ENGINES_READY=false')
        return 2
    print('EVIDENCE_INVENTORY=VALID_INVENTORY_ONLY')
    print('V9_PRODUCTION_READINESS=BLOCKED')
    print('ALL_ENGINES_READY=false')
    return 1


if __name__ == '__main__':
    raise SystemExit(main())
