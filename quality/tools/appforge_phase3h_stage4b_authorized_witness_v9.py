#!/usr/bin/env python3
"""TEST-ONLY signed authorizer -> transactional witness adapter (offline).

This is NOT an independent witness, server, production authorization, secure
persistence, or a proof that Phase 3G ran. Test fixture signing keys are external.
The Stage 4A SQLite database can be rolled back on the same machine. No Android,
network, production keys, or deployment. Fail closed on ambiguity.
"""
import base64
import binascii
from datetime import datetime, timedelta, timezone
import json
import re

import appforge_phase3e_verify_v9 as ed25519
import appforge_phase3f_policy_v9 as policy
import appforge_phase3h_stage4_witness_store_v9 as store

DOMAIN = b'APPFORGE-V9-PHASE3H-STAGE4B-TEST-AUTHORIZATION\x00'
ROLE = 'appforge-v9-phase3h-stage4b-test-authorization'
SOURCE = 'PHASE3G_VERIFIED_BY_EXTERNAL_TEST_AUTHORIZER'
MAX_AUTH = 4096
MAX_TTL = 90
HEX32 = re.compile(r'[a-f0-9]{32}\Z')
HEX64 = re.compile(r'[a-f0-9]{64}\Z')


def need(ok, message):
    if not ok:
        raise ValueError('BLOCKED: ' + message)


def canonical(data):
    return (json.dumps(data, sort_keys=True, separators=(',', ':'),
                       ensure_ascii=True, allow_nan=False) + '\n').encode('ascii')


def parse(raw):
    need(type(raw) is bytes and 0 < len(raw) <= MAX_AUTH, 'authorization length')
    def unique(pairs):
        output = {}
        for k, v in pairs:
            need(k not in output, 'duplicate authorization key')
            output[k] = v
        return output
    try:
        result = json.loads(raw.decode('utf-8', 'strict'), object_pairs_hook=unique,
                            parse_constant=lambda _: (_ for _ in ()).throw(ValueError('nonfinite')))
        need(canonical(result) == raw, 'noncanonical authorization')
    except (UnicodeError, json.JSONDecodeError, TypeError, OverflowError, RecursionError) as exc:
        raise ValueError('BLOCKED: malformed authorization JSON') from exc
    return result


def exact(obj, names, label):
    need(type(obj) is dict and set(obj) == set(names), 'invalid '+label+' fields')


def checkhex(value, pattern, label):
    need(type(value) is str and bool(pattern.fullmatch(value)), 'invalid '+label)


def verify_authorization(raw, *, authorized_der, authorized_pin, device,
                         expected_prior, successor, now):
    """Validate a TEST authorizer assertion; do not trust self-asserted keys.

    A signed *claim* that Phase 3G verification occurred does not establish it.
    A real independent service must verify the actual signed metadata itself
    or consume an independently authenticated verifier attestation.
    """
    need(type(authorized_der) is bytes and ed25519.spki(authorized_der) == authorized_pin,
         'independently provisioned test authorizer pin mismatch')
    checkhex(authorized_pin, HEX64, 'authorizer pin')
    store.check_device(device)
    prior = store.record(expected_prior)
    nxt = store.record(successor)
    need(nxt is not None, 'missing authorized successor')
    envelope = parse(raw)
    exact(envelope, ('keyid', 'payload', 'sig_b64'), 'authorization envelope')
    need(envelope['keyid'] == authorized_pin, 'untrusted authorizer signer')
    signature = envelope['sig_b64']
    need(type(signature) is str and len(signature) == 88, 'invalid authorization signature')
    try:
        signature_bytes = base64.b64decode(signature, validate=True)
    except (ValueError, binascii.Error) as exc:
        raise ValueError('BLOCKED: invalid authorization signature base64') from exc
    need(len(signature_bytes) == 64 and
         base64.b64encode(signature_bytes).decode('ascii') == signature,
         'noncanonical authorization signature')
    payload = envelope['payload']
    exact(payload, ('schema', 'role', 'source', 'device_id', 'permit_id',
                    'prior', 'next', 'evidence_sha256', 'issued_at', 'expires_at'),
          'authorization payload')
    # Validate actual cryptographic signature BEFORE authorizing database writes.
    ed25519.verify_crypto(authorized_der, signature_bytes, DOMAIN + canonical(payload))
    need(type(payload['schema']) is int and payload['schema'] == 1 and
         payload['role'] == ROLE, 'authorization role/schema mismatch')
    need(payload['source'] == SOURCE, 'unsupported authorization source')
    need(payload['device_id'] == device, 'authorization device mismatch')
    checkhex(payload['device_id'], HEX64, 'signed device identity')
    checkhex(payload['permit_id'], HEX32, 'signed permit identity')
    need(store.record(payload['prior']) == prior and
         store.record(payload['next']) == nxt, 'authorization transition mismatch')
    need(payload['evidence_sha256'] == policy.EVIDENCE,
         'authorization evidence pin mismatch')
    issued = ed25519.iso_time(payload['issued_at'])
    expires = ed25519.iso_time(payload['expires_at'])
    need(type(now) is datetime and now.tzinfo is not None, 'trusted clock required')
    need(now.utcoffset() is not None, 'trusted clock must be aware')
    need(issued <= now + timedelta(seconds=10), 'authorization issued in future')
    need(expires > now, 'authorization expired')
    need(expires > issued and expires - issued <= timedelta(seconds=MAX_TTL),
         'authorization TTL exceeded')
    if prior is None:
        need(nxt['root_version'] == 1, 'authorization bootstrap root invalid')
    else:
        need(nxt['release_sequence'] > prior['release_sequence'],
             'authorization release rollback')
        need(nxt['root_version'] in (prior['root_version'], prior['root_version']+1),
             'authorization root rollback or skip')
    return payload['permit_id']


class OfflineTestAuthorizedWitness(store.TestWitnessStore):
    """One DB transaction for CAS + consume signed one-use TEST permit.

    Not an authoritative server. The inherited Stage 4A API is still a
    callable-test-authorize bypass; exposing this subclass in production is
    forbidden. No multi-tenant API, networking, identity enrollment or keys.
    """
    def __init__(self, directory, *, authorized_der, authorized_pin,
                 allowed_devices, test_mode=False):
        need(test_mode is True, 'test-only authorization service')
        need(type(authorized_der) is bytes and ed25519.spki(authorized_der) == authorized_pin,
             'test authorizer pin mismatch')
        need(type(allowed_devices) in (list, tuple, frozenset, set) and
             len(allowed_devices) > 0 and len(allowed_devices) <= 16,
             'trusted test enrollment list required')
        for device in allowed_devices:
            store.check_device(device)
        self._allowed = frozenset(allowed_devices)
        self._der = authorized_der
        self._pin = authorized_pin
        super().__init__(directory, test_mode=True)
        with self._connect() as db:
            db.execute('''CREATE TABLE IF NOT EXISTS used_test_permits (
                permit_id TEXT PRIMARY KEY NOT NULL,
                device TEXT NOT NULL,
                prior TEXT,
                successor TEXT NOT NULL)''')

    def prepare_signed_test(self, device, prior, successor, raw, *, now):
        need(device in self._allowed, 'device not independently enrolled')
        permit_id = verify_authorization(
            raw, authorized_der=self._der, authorized_pin=self._pin,
            device=device, expected_prior=prior, successor=successor, now=now)
        prior = store.record(prior)
        successor = store.record(successor)
        with self._connect() as db:
            try:
                db.execute('BEGIN IMMEDIATE')
                current = self._state(db, device)
                need(current.committed == prior, 'authorized compare-and-swap conflict')
                need(current.pending is None, 'pending transaction exists')
                need(current.generation < store.MAX_GENERATION, 'witness generation exhausted')
                # DB UNIQUE constraint + IMMEDIATE transaction protects a single
                # one-use permit across processes operating on this test DB.
                need(db.execute('SELECT 1 FROM used_test_permits WHERE permit_id=?',
                                (permit_id,)).fetchone() is None,
                     'signed authorization permit replay')
                db.execute('INSERT INTO used_test_permits(permit_id,device,prior,successor) '
                           'VALUES(?,?,?,?)', (permit_id, device,
                           store.canonical(prior) if prior is not None else None,
                           store.canonical(successor)))
                # Cryptographically random transaction token is independent of
                # the signed authorization permit, which remains consumed even
                # if a subsequently prepared intent is aborted.
                import secrets
                token = secrets.token_hex(16)
                pending = {'token':token, 'prior':prior, 'next':successor}
                if db.execute('SELECT 1 FROM devices WHERE device=?',
                              (device,)).fetchone() is None:
                    db.execute('INSERT INTO devices(device,committed,pending,generation) VALUES(?,?,?,?)',
                               (device, None, store.canonical(pending), 1))
                else:
                    db.execute('UPDATE devices SET pending=?,generation=? WHERE device=?',
                               (store.canonical(pending), current.generation+1, device))
                db.execute('COMMIT')
                return token
            except BaseException:
                db.execute('ROLLBACK')
                raise


if __name__ == '__main__':
    print('PHASE3H_STAGE4B_AUTHORIZATION=OFFLINE_TEST_ONLY')
    print('REAL_TRUSTED_VERIFIER=NOT_CONFIGURED')
    print('EXTERNAL_WITNESS=NOT_DEPLOYED')
    print('PERSISTENT_ROLLBACK_STATE=NOT_IMPLEMENTED')
    print('R2_UPLOAD=NO')
    print('PUBLIC_DISTRIBUTION=DISABLED')
    print('ANDROID_CLIENT_ENABLED=NO')
    print('ALL_ENGINES_READY=false')
