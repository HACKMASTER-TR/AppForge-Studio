#!/usr/bin/env python3
"""Offline Stage 4C TEST bridge: genuine Phase 3G evaluation before Stage 4B permit.

This runs in the same TEST process. A caller-provided signing fixture is NOT
independent trusted authorization; the service is NOT deployed and its SQLite
storage can be rolled back. Never use this as a production authorization API.
"""
from datetime import datetime, timedelta, timezone
from copy import deepcopy
import hashlib
from pathlib import Path
import secrets

import appforge_phase3g_integrated_v9 as phase3g
import appforge_phase3h_stage4b_authorized_witness_v9 as auth
import appforge_phase3h_stage4_witness_store_v9 as store


def need(ok, reason):
    if not ok:
        raise ValueError('BLOCKED: ' + reason)


def _record(raw):
    state = phase3g.policy.read_canonical(raw)[0]
    phase3g.policy.check_state(state)
    return {'sha256': hashlib.sha256(phase3g.policy.canonical(state)).hexdigest(),
            'root_version': state['root_version'],
            'release_sequence': state['release_sequence']}


def prepare_verified_test(*, witness, device, evidence, root_metadata,
                          release_metadata, sign_test_authorization, now,
                          old_state=None, first_root_public_der=None,
                          first_root_pin=None):
    """Return (transaction_token, verified_candidate) after local signed verify.

    `sign_test_authorization` must be a TEST fixture capable of producing a
    Stage 4B signed envelope. This callback does not constitute service-side
    trust; it MUST NOT be user controlled in a real implementation.
    """
    need(type(witness) is auth.OfflineTestAuthorizedWitness,
         'only isolated test witness accepted')
    store.check_device(device)
    need(callable(sign_test_authorization), 'test signer callback required')
    need(type(now) is datetime and now.tzinfo is not None and
         now.utcoffset() is not None, 'trusted test clock required')
    current = witness.snapshot(device)
    need(current.pending is None, 'pending witness transaction exists')
    old = None
    prior_bytes = None
    if current.committed is None:
        need(current.generation == 0, 'rebootstrap after prior witness activity forbidden')
        need(old_state is None, 'unwitnessed prior state forbidden')
        need(first_root_public_der is not None and first_root_pin is not None,
             'independently provided bootstrap key required')
    else:
        need(old_state is not None, 'committed witness requires prior state')
        need(first_root_public_der is None and first_root_pin is None,
             'bootstrap root cannot override committed witness')
        # Trusted high-water digest comes from witness.snapshot, never a local
        # adjacent hash file. The SQLite witness itself remains TEST-ONLY.
        p = Path(old_state)
        need(p.is_file() and not p.is_symlink(), 'prior state path unsafe')
        prior_bytes = p.read_bytes()
        need(0 < len(prior_bytes) <= phase3g.policy.MAX_STATE,
             'prior state length invalid')
        old = _record(p)
        need(old == current.committed, 'prior state mismatches witnessed checkpoint')
    kwargs = dict(evidence=evidence, root_metadata=root_metadata,
                  release_metadata=release_metadata, now=now)
    if old is None:
        kwargs.update(first_root_public_der=first_root_public_der,
                      first_root_pin=first_root_pin)
    else:
        kwargs.update(old_state=old_state,
                      external_checkpoint=current.committed['sha256'])
    # Directly call the genuine Phase 3G evaluator. Do not accept a caller
    # supplied candidate or a signed *claim* as a replacement for this step.
    candidate = phase3g.evaluate(**kwargs)
    need(type(candidate) is dict, 'Phase 3G did not return verified state')
    phase3g.policy.check_state(candidate)
    candidate_raw = phase3g.policy.canonical(candidate)
    successor = {'sha256': hashlib.sha256(candidate_raw).hexdigest(),
                 'root_version': candidate['root_version'],
                 'release_sequence': candidate['release_sequence']}
    if old is not None:
        need(Path(old_state).read_bytes() == prior_bytes,
             'prior state changed during signed verification')
        need(successor['release_sequence'] > old['release_sequence'],
             'a new signed release required')
    need(witness.snapshot(device) == current,
         'witness state changed during signed verification')
    issued = now.astimezone(timezone.utc).replace(microsecond=0)
    expires = issued + timedelta(seconds=45)
    payload = {'schema': 1, 'role': auth.ROLE, 'source': auth.SOURCE,
               'device_id': device, 'permit_id': secrets.token_hex(16),
               'prior': deepcopy(old), 'next': deepcopy(successor),
               'evidence_sha256': phase3g.policy.EVIDENCE,
               'issued_at': issued.strftime('%Y-%m-%dT%H:%M:%SZ'),
               'expires_at': expires.strftime('%Y-%m-%dT%H:%M:%SZ')}
    # The TEST callback can sign only after Phase 3G succeeds. This module
    # neither stores nor generates signing private keys.
    signed = sign_test_authorization(payload)
    need(type(signed) is bytes, 'test signer did not return envelope bytes')
    need(witness.snapshot(device) == current,
         'witness state changed during test signing')
    token = witness.prepare_signed_test(device, old, successor, signed, now=now)
    return token, candidate


if __name__ == '__main__':
    print('PHASE3H_STAGE4C_VERIFIER_BRIDGE=TEST_ONLY')
    print('EXTERNAL_WITNESS=NOT_DEPLOYED')
    print('REAL_TRUSTED_VERIFIER=NOT_CONFIGURED')
    print('PERSISTENT_ROLLBACK_STATE=NOT_IMPLEMENTED')
    print('R2_UPLOAD=NO')
    print('PUBLIC_DISTRIBUTION=DISABLED')
    print('ANDROID_CLIENT_ENABLED=NO')
    print('ALL_ENGINES_READY=false')
