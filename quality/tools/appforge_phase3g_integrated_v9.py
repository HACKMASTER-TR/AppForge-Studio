#!/usr/bin/env python3
"""Phase 3G: single-snapshot signed metadata -> anti-rollback policy (offline).

Does NOT persist trust, mint/sign keys, modify Android, publish releases, or
authorize R2/public distribution. Inputs must come from independently trusted
bootstrap and/or checkpoint channels. Output is only a candidate next state.
"""
import argparse
from datetime import datetime, timezone
import hashlib
from pathlib import Path
import sys

import appforge_phase3e_verify_v9 as crypto
import appforge_phase3f_policy_v9 as policy


def require(ok, why):
    if not ok:
        raise ValueError('BLOCKED: ' + why)


def evaluate(*, evidence, root_metadata, release_metadata,
             first_root_public_der=None, first_root_pin=None,
             old_state=None, external_checkpoint=None, now=None):
    """Verify each canonical metadata snapshot once, then derive state in memory.

    On existing-state updates, `external_checkpoint` MUST be authenticated and
    protected independently of old_state. It is NOT safe to read a sibling hash
    file in the same untrusted storage domain. This helper never advances floors.
    """
    require(policy.EVIDENCE == crypto.EVIDENCE_PIN,
            'pinned Phase 3D evidence constants differ')
    require(policy.SNAPSHOT == crypto.SNAPSHOT,
            'pinned Phase 3D snapshot constants differ')
    require((old_state is None) == (external_checkpoint is None),
            'existing state requires an external trusted checkpoint')
    now = datetime.now(timezone.utc) if now is None else now
    require(isinstance(now, datetime) and now.tzinfo is not None,
            'timezone-aware verification clock required')

    if old_state is None:
        require(first_root_public_der is not None and first_root_pin is not None,
                'independent bootstrap root public DER and pin required')
        root_path = Path(first_root_public_der)
        require(root_path.is_file() and not root_path.is_symlink(),
                'bootstrap public root missing or symlinked')
        previous_der = root_path.read_bytes()
        require(crypto.spki(previous_der) == first_root_pin,
                'bootstrap root pin mismatch')
        old = None
        previous_pin = first_root_pin
        min_root = min_release = 1
    else:
        require(first_root_public_der is None and first_root_pin is None,
                'existing state must use externally checkpointed root')
        old, previous_der = policy.inspect_existing_state(old_state, external_checkpoint)
        previous_pin = old['root_keyid']
        require(crypto.spki(previous_der) == previous_pin,
                'checkpointed prior root identity mismatch')
        min_root, min_release = old['root_version'], old['release_sequence']

    # Evidence, ROOT and RELEASE each consumed from one fixed byte snapshot.
    # In particular, never pass file paths back into verify_all() for a re-read.
    evidence_digest = crypto.verify_evidence(evidence, policy.EVIDENCE)
    root_doc, root_raw = crypto.read_canonical(root_metadata)
    release_doc, release_raw = crypto.read_canonical(release_metadata)
    root, keys = crypto.verify_root(root_doc, previous_der, previous_pin,
                                    min_root, now=now)
    verified_release = crypto.verify_release(release_doc, root, keys,
                                             evidence_digest, min_release,
                                             now=now)
    # The state policy consumes the SAME canonical root/release byte snapshots.
    candidate = policy.build_next_state(old=old, verified_root=root,
        root_raw=root_raw, release_raw=release_raw,
        previous_root_der=previous_der, previous_root_pin=previous_pin,
        verifier=crypto)
    require(candidate['release_sequence'] == verified_release['sequence'] and
            candidate['root_version'] == verified_release['root_version'],
            'verified release is not the transition release')
    require(all(candidate[x] is False for x in
                ('r2_upload_allowed','public_distribution_allowed','android_client_enabled')),
            'closed distribution gates violated')
    return candidate


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__)
    sub = ap.add_subparsers(dest='command',required=True)
    cmd = sub.add_parser('verify-offline')
    cmd.add_argument('--evidence',type=Path,required=True)
    cmd.add_argument('--root-metadata',type=Path,required=True)
    cmd.add_argument('--release-metadata',type=Path,required=True)
    cmd.add_argument('--first-root-public-der',type=Path)
    cmd.add_argument('--first-root-pin')
    cmd.add_argument('--old-state',type=Path)
    cmd.add_argument('--external-checkpoint')
    args = ap.parse_args(argv)
    try:
        candidate = evaluate(evidence=args.evidence,
            root_metadata=args.root_metadata,release_metadata=args.release_metadata,
            first_root_public_der=args.first_root_public_der,
            first_root_pin=args.first_root_pin,old_state=args.old_state,
            external_checkpoint=args.external_checkpoint)
        b = policy.canonical(candidate)
        print('PHASE3G_REAL_SIGNATURE_AND_POLICY=PASS')
        print('CANDIDATE_STATE_SHA256=' + hashlib.sha256(b).hexdigest())
        print('CANDIDATE_ROOT_VERSION=' + str(candidate['root_version']))
        print('CANDIDATE_RELEASE_SEQUENCE=' + str(candidate['release_sequence']))
        print('TRUST_STATE_PERSISTED=NO')
        outcome = 0
    except (ValueError, OSError, RuntimeError, TypeError, OverflowError) as err:
        print('PHASE3G_REAL_SIGNATURE_AND_POLICY=BLOCKED')
        print('REASON=' + str(err)[:220])
        print('TRUST_STATE_PERSISTED=NO')
        outcome = 1
    print('PRODUCTION_ROOT_KEY=NOT_PRESENT')
    print('PERSISTENT_ROLLBACK_STATE=NOT_IMPLEMENTED')
    print('R2_UPLOAD=NO')
    print('PUBLIC_DISTRIBUTION=DISABLED')
    print('ANDROID_CLIENT_ENABLED=NO')
    print('ALL_ENGINES_READY=false')
    return outcome


if __name__ == '__main__':
    sys.exit(main())
