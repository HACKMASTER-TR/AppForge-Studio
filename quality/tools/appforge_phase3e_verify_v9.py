#!/usr/bin/env python3
"""AppForge V9 Phase 3E: offline Ed25519 release trust verification PROTOTYPE.

This verifier never generates production keys, signs manifests, writes R2,
changes Android clients, changes GitHub, or enables release publication.
Trusted root SPKI hash and rollback floors MUST come from outside untrusted
metadata. A signed payload alone cannot persist rollback protection.
"""
import argparse
import base64
import binascii
from datetime import datetime, timezone, timedelta
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile

SHA = re.compile(r'^[a-f0-9]{64}$')
ED25519_SPKI_PREFIX = bytes.fromhex('302a300506032b6570032100')
SNAPSHOT = '20261018T000000Z'
EVIDENCE_PIN = 'b2341fb22a758774f5cf07c22792539d15342fd931b40f29848db097a9abf439'
ROOT_CONTEXT = b'APPFORGE-V9-PHASE3E-ROOT\x00'
RELEASE_CONTEXT = b'APPFORGE-V9-PHASE3E-RELEASE\x00'
MAX_JSON_BYTES = 128 * 1024


def require(condition, message):
    if not condition:
        raise ValueError('BLOCKED: ' + message)


def canonical(value):
    return (json.dumps(value, ensure_ascii=True, sort_keys=True,
                       separators=(',', ':'), allow_nan=False) + '\n').encode('ascii')


def unique_pairs(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, 'duplicate JSON key')
        result[key] = value
    return result


def read_canonical(path, *, max_bytes=MAX_JSON_BYTES):
    p = Path(path)
    require(p.is_file() and not p.is_symlink(), 'file missing or symlinked: ' + str(p))
    data = p.read_bytes()
    require(0 < len(data) <= max_bytes, 'invalid JSON length')
    try:
        doc = json.loads(data.decode('utf-8', 'strict'), object_pairs_hook=unique_pairs,
                         parse_constant=lambda _: (_ for _ in ()).throw(ValueError('nonfinite JSON number')))
        require(canonical(doc) == data, 'JSON is not canonical')
    except (UnicodeError, json.JSONDecodeError, TypeError, OverflowError, RecursionError) as e:
        raise ValueError('BLOCKED: invalid canonical JSON') from e
    return doc, data


def exact(obj, keys, name):
    require(type(obj) is dict and set(obj) == set(keys), 'invalid '+name+' fields')


def int_range(n, low, high, name):
    require(type(n) is int and low <= n <= high, 'invalid '+name)


def b64decode(value, maxlen):
    require(type(value) is str and len(value) <= 256, 'invalid base64 field')
    try:
        raw = base64.b64decode(value, validate=True)
    except (ValueError, binascii.Error) as e:
        raise ValueError('BLOCKED: invalid base64 encoding') from e
    require(len(raw) <= maxlen and base64.b64encode(raw).decode('ascii') == value,
            'noncanonical or oversized base64')
    return raw


def spki(raw):
    require(len(raw) == 44 and raw.startswith(ED25519_SPKI_PREFIX),
            'only pure Ed25519 SubjectPublicKeyInfo permitted')
    return hashlib.sha256(raw).hexdigest()


def key_entry(entry):
    exact(entry, ('keyid','spki_der_b64'), 'key')
    raw = b64decode(entry['spki_der_b64'], 44)
    identity = spki(raw)
    require(entry['keyid'] == identity, 'key id does not match SPKI')
    return identity, raw


def iso_time(value):
    require(type(value) is str and re.fullmatch(r'\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z', value),
            'invalid time format')
    try:
        return datetime.strptime(value, '%Y-%m-%dT%H:%M:%SZ').replace(tzinfo=timezone.utc)
    except ValueError as e:
        raise ValueError('BLOCKED: invalid date') from e


def check_validity(payload, now):
    issued, expires = iso_time(payload['issued_at']), iso_time(payload['expires_at'])
    require(issued <= now + timedelta(minutes=10), 'future-dated metadata')
    require(expires > now, 'expired signed metadata')
    require(expires > issued and expires - issued <= timedelta(days=90), 'metadata expiry too long')


def envelope(doc):
    exact(doc, ('payload','signatures'), 'signed envelope')
    require(type(doc['payload']) is dict, 'non-object signed payload')
    signatures = doc['signatures']
    require(type(signatures) is list and 1 <= len(signatures) <= 6, 'invalid signature list')
    seen = set()
    sigs = {}
    for s in signatures:
        exact(s, ('keyid','sig_b64'), 'signature')
        require(type(s['keyid']) is str and bool(SHA.fullmatch(s['keyid'])), 'bad signer key id')
        require(s['keyid'] not in seen, 'duplicate signer id')
        seen.add(s['keyid'])
        raw = b64decode(s['sig_b64'],64)
        require(len(raw) == 64, 'invalid Ed25519 signature length')
        sigs[s['keyid']] = raw
    return doc['payload'], sigs


def verify_crypto(key_der, signature, message):
    spki(key_der)
    require(len(signature) == 64 and len(message) <= MAX_JSON_BYTES,
            'cryptographic input bounds exceeded')
    with tempfile.TemporaryDirectory(prefix='appforge-v9-verify-') as tmp:
        root = Path(tmp)
        key, sig, msg = root/'public.der', root/'signature', root/'message'
        key.write_bytes(key_der)
        sig.write_bytes(signature)
        msg.write_bytes(message)
        try:
            p = subprocess.run(['openssl','pkeyutl','-verify','-pubin','-keyform','DER',
                                '-inkey',str(key),'-sigfile',str(sig),'-rawin','-in',str(msg)],
                               capture_output=True, timeout=15, check=False)
        except (OSError,subprocess.TimeoutExpired) as e:
            raise ValueError('BLOCKED: OpenSSL Ed25519 verifier unavailable') from e
        require(p.returncode == 0, 'Ed25519 signature verification failed')


def verify_root(root_doc, trusted_root_der, trusted_root_pin, min_root_version, *, now):
    require(type(trusted_root_pin) is str and bool(SHA.fullmatch(trusted_root_pin)), 'invalid root pin')
    trusted_id = spki(trusted_root_der)
    require(trusted_id == trusted_root_pin, 'root SPKI is not externally pinned')
    payload, sigs = envelope(root_doc)
    exact(payload, ('schema','role','version','issued_at','expires_at','root_key',
                    'release_keys','release_threshold'), 'root metadata')
    require(payload['schema'] == 1 and type(payload['schema']) is int and
            payload['role'] == 'appforge-v9-root', 'wrong root metadata role')
    int_range(payload['version'], 1, 2**31-1,'root version')
    int_range(min_root_version, 1, 2**31-1,'minimum root version')
    require(payload['version'] >= min_root_version, 'root metadata rollback')
    check_validity(payload, now)
    new_id, new_key = key_entry(payload['root_key'])
    if payload['version'] == 1:
        require(new_id == trusted_id, 'root bootstrapping forbidden')
    require(type(payload['release_keys']) is list and 1 <= len(payload['release_keys']) <= 3,
            'release key count invalid')
    keys = dict(key_entry(x) for x in payload['release_keys'])
    require(len(keys) == len(payload['release_keys']), 'duplicate release key')
    int_range(payload['release_threshold'], 1, len(keys), 'release signer threshold')
    signed = ROOT_CONTEXT + canonical(payload)
    require(trusted_id in sigs, 'previous trusted root authorization missing')
    verify_crypto(trusted_root_der, sigs[trusted_id], signed)
    if trusted_id != new_id:
        require(new_id in sigs, 'new root possession signature missing')
        verify_crypto(new_key, sigs[new_id], signed)
    return payload, keys


def verify_evidence(evidence, expected_digest=EVIDENCE_PIN):
    data, raw = read_canonical(evidence, max_bytes=262144)
    require(bool(SHA.fullmatch(expected_digest)) and
            hashlib.sha256(raw).hexdigest() == expected_digest, 'Phase 3D evidence hash mismatch')
    must = {'stage':'OFFLINE_EVIDENCE_ONLY','snapshot':SNAPSHOT,
            'original_objects_verified':218,'original_deb_objects_verified':214,
            'ubuntu_inrelease_verified':4,'signed_packages_indexes_verified':32,
            'distinct_content_addressed_objects':248,
            'distinct_content_addressed_bytes':650832480,
            'appforge_ed25519_release_signature':'NOT_PRESENT',
            'r2_upload_allowed':False,'public_distribution_allowed':False,
            'android_client_enabled':False,'apt_ready':False,'all_engines_ready':False}
    require(type(data) is dict, 'evidence is not JSON object')
    for k,v in must.items():
        require(k in data and type(data[k]) is type(v) and data[k] == v,
                'incorrect evidence field: ' + k)
    return expected_digest


def verify_release(release_doc, root, keys, evidence_digest, min_sequence, *, now):
    payload, sigs = envelope(release_doc)
    exact(payload, ('schema','role','root_version','sequence','snapshot','evidence_sha256',
                    'issued_at','expires_at','r2_upload_allowed','public_distribution_allowed',
                    'android_client_enabled'), 'release metadata')
    require(payload['schema'] == 1 and type(payload['schema']) is int and
            payload['role'] == 'appforge-v9-release', 'wrong release role')
    int_range(payload['sequence'],1,2**31-1,'release sequence')
    int_range(min_sequence,1,2**31-1,'minimum release sequence')
    require(payload['sequence'] >= min_sequence, 'release sequence rollback')
    require(type(payload['root_version']) is int and payload['root_version'] == root['version'],
            'release root-version mismatch')
    require(payload['snapshot'] == SNAPSHOT and payload['evidence_sha256'] == evidence_digest,
            'release evidence binding mismatch')
    check_validity(payload,now)
    for k in ('r2_upload_allowed','public_distribution_allowed','android_client_enabled'):
        require(payload[k] is False, 'distribution must remain disabled')
    message = RELEASE_CONTEXT + canonical(payload)
    valid = 0
    for keyid,sig in sigs.items():
        require(keyid in keys, 'untrusted release signer')
        verify_crypto(keys[keyid],sig,message)
        valid += 1
    require(valid >= root['release_threshold'], 'release signer threshold not met')
    return payload


def verify_all(*, evidence, root_metadata, release_metadata, trusted_root_pub_der,
               trusted_root_pin, min_root_version, min_release_sequence, now=None,
               evidence_sha256=EVIDENCE_PIN):
    now = now or datetime.now(timezone.utc)
    digest = verify_evidence(evidence,evidence_sha256)
    root_data, _ = read_canonical(root_metadata)
    release_data, _ = read_canonical(release_metadata)
    root, keys = verify_root(root_data, trusted_root_pub_der,trusted_root_pin,
                             min_root_version,now=now)
    return verify_release(release_data, root, keys,digest,min_release_sequence, now=now)


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__)
    sub = p.add_subparsers(dest='command',required=True)
    inspect = sub.add_parser('inspect-evidence')
    inspect.add_argument('--evidence',type=Path,required=True)
    verify = sub.add_parser('verify-test-policy')
    verify.add_argument('--evidence',type=Path,required=True)
    verify.add_argument('--root-metadata',type=Path,required=True)
    verify.add_argument('--release-metadata',type=Path,required=True)
    verify.add_argument('--trusted-root-public-der',type=Path,required=True)
    verify.add_argument('--trusted-root-spki-sha256',required=True)
    verify.add_argument('--min-root-version',type=int,required=True)
    verify.add_argument('--min-release-sequence',type=int,required=True)
    args = p.parse_args(argv)
    try:
        if args.command == 'inspect-evidence':
            verify_evidence(args.evidence)
            print('PHASE3D_EVIDENCE_PIN=PASS')
            print('REAL_APPFORGE_SIGNATURE=NOT_PRESENT')
        else:
            keypath = args.trusted_root_public_der
            require(keypath.is_file() and not keypath.is_symlink(), 'trusted root key missing')
            raw = keypath.read_bytes()
            require(len(raw) == 44, 'root DER length invalid')
            policy = verify_all(evidence=args.evidence,root_metadata=args.root_metadata,
                        release_metadata=args.release_metadata,trusted_root_pub_der=raw,
                        trusted_root_pin=args.trusted_root_spki_sha256,
                        min_root_version=args.min_root_version,
                        min_release_sequence=args.min_release_sequence)
            print('TEST_POLICY_SIGNATURE=PASS')
            print('TEST_ROOT_VERSION='+str(policy['root_version']))
            print('TEST_RELEASE_SEQUENCE='+str(policy['sequence']))
            print('PRODUCTION_TRUST_ANCHOR=NOT_CONFIGURED')
    except (ValueError,OSError,RuntimeError) as e:
        print('PHASE3E_VERIFY=BLOCKED')
        print('REASON='+str(e)[:240])
        print('R2_UPLOAD=NO')
        print('PUBLIC_DISTRIBUTION=DISABLED')
        print('ALL_ENGINES_READY=false')
        return 1
    print('R2_UPLOAD=NO')
    print('PUBLIC_DISTRIBUTION=DISABLED')
    print('ANDROID_CLIENT_ENABLED=NO')
    print('ALL_ENGINES_READY=false')
    return 0


if __name__=='__main__':
    raise SystemExit(main())
