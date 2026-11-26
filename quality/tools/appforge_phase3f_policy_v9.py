#!/usr/bin/env python3
"""AppForge V9 Phase 3F stage 1: OFFLINE trust-state transition policy.

READ-ONLY regarding caller state, GitHub, Android, R2 and release publication.
Must NOT be interpreted as persistent, tamper-proof anti-rollback on Android.
The previous state SHA256 and first-run trust anchor must be trusted outside
this module and outside any attacker-controlled metadata.
"""
import argparse
import base64
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import sys

ROOT = 'OFFLINE_TRUST_STATE_PROTOTYPE_ONLY'
SNAPSHOT = '20261018T000000Z'
EVIDENCE = 'b2341fb22a758774f5cf07c22792539d15342fd931b40f29848db097a9abf439'
HASH = re.compile(r'[0-9a-f]{64}\Z')
MAX_STATE = 4096


def need(ok, msg):
    if not ok:
        raise ValueError('BLOCKED: ' + msg)


def canonical(v):
    return (json.dumps(v, sort_keys=True, ensure_ascii=True, separators=(',', ':'), allow_nan=False) + '\n').encode('ascii')


def unique(pairs):
    d = {}
    for k, v in pairs:
        need(k not in d, 'duplicate JSON key')
        d[k] = v
    return d


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def read_canonical(path, size=MAX_STATE):
    p = Path(path)
    need(p.is_file() and not p.is_symlink(), 'missing or symlinked input')
    raw = p.read_bytes()
    need(0 < len(raw) <= size, 'input size invalid')
    try:
        doc = json.loads(raw.decode('utf-8','strict'), object_pairs_hook=unique,
                         parse_constant=lambda _: (_ for _ in ()).throw(ValueError('nonfinite number')))
    except (UnicodeError, json.JSONDecodeError, OverflowError, RecursionError, TypeError) as e:
        raise ValueError('BLOCKED: invalid JSON input') from e
    need(canonical(doc) == raw, 'input is not canonical JSON')
    return doc, raw


def fields(x, names, label):
    need(type(x) is dict and set(x) == set(names), 'invalid '+label+' fields')


def version(x, label):
    need(type(x) is int and 1 <= x <= 2**31-1, 'invalid '+label)


def check_state(x):
    keys=('schema','stage','snapshot','evidence_sha256','root_version',
          'root_keyid','root_spki_der_b64','root_metadata_sha256',
          'release_sequence','release_metadata_sha256',
          'r2_upload_allowed','public_distribution_allowed','android_client_enabled')
    fields(x, keys, 'trust state')
    need(type(x['schema']) is int and x['schema']==1 and x['stage']==ROOT, 'wrong trust state schema/stage')
    need(x['snapshot']==SNAPSHOT and x['evidence_sha256']==EVIDENCE,'trust state does not bind pinned evidence')
    version(x['root_version'],'root_version')
    version(x['release_sequence'],'release_sequence')
    for k in ('root_keyid','root_metadata_sha256','release_metadata_sha256'):
        need(type(x[k]) is str and bool(HASH.fullmatch(x[k])), 'bad state digest: '+k)
    need(all(x[k] is False for k in ('r2_upload_allowed','public_distribution_allowed','android_client_enabled')),
         'distribution authorization forbidden')
    need(type(x['root_spki_der_b64']) is str and len(x['root_spki_der_b64']) <= 64,'bad public root field')
    try:
        der=base64.b64decode(x['root_spki_der_b64'], validate=True)
    except Exception as e:
        raise ValueError('BLOCKED: root SPKI decoding failed') from e
    need(len(der)==44 and base64.b64encode(der).decode('ascii')==x['root_spki_der_b64'],
         'invalid public root DER encoding')
    need(der.startswith(bytes.fromhex('302a300506032b6570032100')) and sha(der)==x['root_keyid'],
         'invalid public root identity')
    return der


def inspect_existing_state(path, trusted_checkpoint):
    need(type(trusted_checkpoint) is str and bool(HASH.fullmatch(trusted_checkpoint)),
         'external previous state SHA256 checkpoint required')
    doc, raw=read_canonical(path)
    need(sha(raw)==trusted_checkpoint, 'previous state checkpoint mismatch')
    der=check_state(doc)
    return doc, der


def build_next_state(*, old, verified_root, root_raw, release_raw, previous_root_der,
                     previous_root_pin, verifier):
    """Call AFTER genuine Phase 3E signature verification; pure policy check.

    `verified_root` is returned from trusted verifier.verify_root, not from an
    untrusted API argument. This function also verifies new root key shape.
    """
    need(type(previous_root_pin) is str and bool(HASH.fullmatch(previous_root_pin)), 'external root pin absent')
    need(verifier.spki(previous_root_der)==previous_root_pin, 'previous root pin mismatch')
    need(type(root_raw) is bytes and type(release_raw) is bytes, 'metadata must be canonical bytes')
    payload=verified_root
    fields(payload, ('schema','role','version','issued_at','expires_at','root_key','release_keys','release_threshold'), 'verified root')
    new_pin, new_der=verifier.key_entry(payload['root_key'])
    root_version=payload['version']
    release_payload,_=verifier.envelope(json.loads(release_raw.decode('ascii'), object_pairs_hook=unique))
    fields(release_payload, ('schema','role','root_version','sequence','snapshot','evidence_sha256',
        'issued_at','expires_at','r2_upload_allowed','public_distribution_allowed','android_client_enabled'), 'release')
    seq=release_payload['sequence']
    version(root_version,'new root version')
    version(seq,'new release sequence')
    need(release_payload['root_version']==root_version,'root and release version mismatch')
    need(release_payload['snapshot']==SNAPSHOT and release_payload['evidence_sha256']==EVIDENCE,
         'release evidence mismatch')
    need(all(release_payload[k] is False for k in ('r2_upload_allowed','public_distribution_allowed','android_client_enabled')),
         'release tries to enable distribution')
    if old is None:
        need(root_version==1 and new_pin==previous_root_pin, 'bootstrap must use pinned root v1')
    else:
        check_state(old)
        need(previous_root_pin==old['root_keyid'] and
             base64.b64encode(previous_root_der).decode('ascii')==old['root_spki_der_b64'],
             'previous state root was not used')
        need(root_version in (old['root_version'],old['root_version']+1),
             'root rotation skip or rollback')
        need(seq>=old['release_sequence'], 'release rollback')
        same_seq = seq==old['release_sequence']
        if same_seq:
            need(root_version==old['root_version'] and sha(root_raw)==old['root_metadata_sha256']
                 and sha(release_raw)==old['release_metadata_sha256'],
                 'same sequence cannot replace signed metadata')
        else:
            need(seq>old['release_sequence'], 'invalid sequence')
        if root_version==old['root_version']:
            need(new_pin==old['root_keyid'], 'root key changed without increment')
            need(sha(root_raw)==old['root_metadata_sha256'], 'same root version metadata equivocation')
        else:
            need(new_pin!=old['root_keyid'], 'root version changed without key rotation')
    return {'schema':1,'stage':ROOT,'snapshot':SNAPSHOT,'evidence_sha256':EVIDENCE,
            'root_version':root_version,'root_keyid':new_pin,
            'root_spki_der_b64':base64.b64encode(new_der).decode('ascii'),
            'root_metadata_sha256':sha(root_raw),'release_sequence':seq,
            'release_metadata_sha256':sha(release_raw),
            'r2_upload_allowed':False,'public_distribution_allowed':False,
            'android_client_enabled':False}


def plan(*, repo, evidence, root_metadata, release_metadata, first_root_public_der,
         first_root_pin, old_state=None, external_checkpoint=None, now=None):
    """Produce an IN-MEMORY next state after real Phase3E verification.

    Intentionally does not persist the result, so Android rollback security is
    never falsely claimed. Caller must implement trusted state persistence.
    """
    repo=Path(repo)
    p=repo/'quality/tools/appforge_phase3e_verify_v9.py'
    need(p.is_file() and not p.is_symlink(), 'Phase 3E verifier unavailable')
    spec=importlib.util.spec_from_file_location('v9_phase3f_phase3e',p)
    need(spec is not None and spec.loader is not None, 'cannot import Phase 3E')
    v=importlib.util.module_from_spec(spec)
    spec.loader.exec_module(v)
    need(bool(HASH.fullmatch(first_root_pin)), 'first root SPKI pin invalid')
    if old_state is None:
        need(external_checkpoint is None,'bootstrap cannot carry state checkpoint')
        old=None
        previous_root_der=Path(first_root_public_der).read_bytes()
        previous_root_pin=first_root_pin
        min_root=1
        min_seq=1
    else:
        old, previous_root_der=inspect_existing_state(old_state, external_checkpoint)
        # root anchor outside metadata still needed. Production caller must
        # independently trust the persistent checkpoint across app restarts.
        min_root=old['root_version']
        min_seq=old['release_sequence']
        previous_root_pin=old['root_keyid']
    need(v.spki(previous_root_der)==previous_root_pin,'trusted root SPKI mismatch')
    root_doc,root_raw=v.read_canonical(root_metadata)
    release_doc,release_raw=v.read_canonical(release_metadata)
    v.verify_all(evidence=evidence,root_metadata=root_metadata,
                 release_metadata=release_metadata,
                 trusted_root_pub_der=previous_root_der,
                 trusted_root_pin=previous_root_pin,
                 min_root_version=min_root,min_release_sequence=min_seq,
                 now=now,evidence_sha256=EVIDENCE)
    root_payload,_=v.verify_root(root_doc,previous_root_der,previous_root_pin,min_root,
                                now=now or datetime.now(timezone.utc))
    nxt=build_next_state(old=old,verified_root=root_payload,root_raw=root_raw,
                         release_raw=release_raw,previous_root_der=previous_root_der,
                         previous_root_pin=previous_root_pin,verifier=v)
    return nxt


def main(argv=None):
    ap=argparse.ArgumentParser(description=__doc__)
    sub=ap.add_subparsers(dest='command',required=True)
    sub.add_parser('inspect-evidence').add_argument('--evidence',required=True,type=Path)
    args=ap.parse_args(argv)
    try:
        if args.command=='inspect-evidence':
            evidence=Path(args.evidence)
            need(evidence.is_file() and not evidence.is_symlink(),'evidence missing')
            need(sha(evidence.read_bytes())==EVIDENCE,'Phase 3D evidence digest mismatch')
            print('PHASE3D_EVIDENCE_PIN=PASS')
            print('PRODUCTION_ROOT_KEY=NOT_PRESENT')
            print('PERSISTENT_ROLLBACK_STATE=NOT_IMPLEMENTED')
    except (ValueError,OSError,RuntimeError) as e:
        print('PHASE3F_POLICY=BLOCKED')
        print('REASON='+str(e)[:220])
        print('ALL_ENGINES_READY=false')
        return 1
    print('R2_UPLOAD=NO')
    print('ANDROID_CLIENT_ENABLED=NO')
    print('MAIN_MODIFIED=NO')
    print('ALL_ENGINES_READY=false')
    return 0


if __name__=='__main__':
    raise SystemExit(main())
