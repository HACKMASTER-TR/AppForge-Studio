#!/usr/bin/env python3
"""Phase 3H stage 1: test-only crash/recovery protocol model.

The witness and disk here exist only in RAM. They are NOT independently
authenticated, tamper-proof, reboot persistent, or an Android implementation.
The signed verification callback must use Phase 3G; this module does not sign,
verify signatures, publish, or allow distribution. No production key is used.
"""
from dataclasses import dataclass
import hashlib
from typing import Optional

import appforge_phase3f_policy_v9 as policy


def block(ok, reason):
    if not ok:
        raise ValueError('BLOCKED: ' + reason)


def digest(raw):
    return hashlib.sha256(raw).hexdigest() if raw is not None else None


def inspect(raw):
    block(type(raw) is bytes and 0 < len(raw) <= policy.MAX_STATE,
          'invalid state bytes')
    # Validation through existing Phase 3F policy, with strict canonical JSON.
    # The real policy reader expects files, so canonical round-trip is required.
    import json
    try:
        def unique(pairs):
            out = {}
            for k, v in pairs:
                block(k not in out, 'duplicate state key')
                out[k] = v
            return out
        doc = json.loads(raw.decode('utf-8', 'strict'), object_pairs_hook=unique,
                         parse_constant=lambda _: (_ for _ in ()).throw(ValueError('nonfinite')))
        block(policy.canonical(doc) == raw, 'noncanonical state')
        policy.check_state(doc)
    except (UnicodeError, TypeError, OverflowError, RecursionError,
            json.JSONDecodeError) as e:
        raise ValueError('BLOCKED: corrupted state bytes') from e
    return doc


@dataclass(frozen=True)
class Record:
    sha256: str
    root_version: int
    release_sequence: int


@dataclass(frozen=True)
class Intent:
    prior: Optional[Record]
    next: Record


class InMemoryWitness:
    """TRUSTED WITNESS *SIMULATOR*. Not an authenticated external service."""
    def __init__(self):
        self.committed = None
        self.pending = None
        self.online = True

    def snapshot(self):
        block(self.online, 'trusted witness unavailable')
        return self.committed, self.pending

    def prepare(self, expected_prior, successor):
        self.snapshot()
        block(self.pending is None, 'unresolved witness intent')
        block(self.committed == expected_prior, 'witness compare-and-swap conflict')
        if expected_prior is None:
            block(successor.root_version == 1 and successor.release_sequence >= 1,
                  'nonbootstrap initial record')
        else:
            block(successor.release_sequence > expected_prior.release_sequence,
                  'witness refuses release replay or rollback')
            block(successor.root_version in (expected_prior.root_version,
                                             expected_prior.root_version + 1),
                  'witness refuses root rollback or skip')
        self.pending = Intent(expected_prior, successor)

    def finalize(self, intent):
        self.snapshot()
        block(self.pending == intent and self.committed == intent.prior,
              'witness intent mismatch')
        self.committed = intent.next
        self.pending = None

    def abort(self, intent):
        self.snapshot()
        block(self.pending == intent and self.committed == intent.prior,
              'cannot abort another intent')
        self.pending = None


class InMemoryDisk:
    """UNTRUSTED disk simulator; no filesystem writes, no true fsync."""
    def __init__(self):
        self.current = None
        self.staged = None

    def stage(self, raw):
        block(self.staged is None, 'staging conflict')
        self.staged = bytes(raw)

    def replace(self):
        block(self.staged is not None, 'staging absent')
        self.current = self.staged
        self.staged = None


class RecoveryModel:
    """Order: VERIFY -> WITNESS PREPARE -> STAGE -> SWAP -> WITNESS COMMIT.

    `verified_transition(prior_bytes, trusted_sha)` must return the state
    produced from genuine Phase3G signature verification, NOT parsed metadata.
    Neither this method nor the RAM witness can attest that the callback is
    trusted; production callers require authenticated service-side authorization.
    """
    def __init__(self, disk, witness):
        self.disk = disk
        self.witness = witness

    @staticmethod
    def record(raw):
        data = inspect(raw)
        return Record(digest(raw), data['root_version'], data['release_sequence'])

    def recover(self):
        committed, pending = self.witness.snapshot()
        current = self.disk.current
        if pending is not None:
            block(pending.prior == committed, 'inconsistent witness intent')
            if digest(current) == (committed.sha256 if committed else None):
                if committed is not None:
                    block(self.record(current) == committed,
                          'current state contradicts committed checkpoint')
                block(self.disk.staged is None or digest(self.disk.staged) == pending.next.sha256,
                      'tampered staged state')
                self.witness.abort(pending)
                self.disk.staged = None
                return 'RECOVERED_PREVIOUS'
            if digest(current) == pending.next.sha256:
                block(self.record(current) == pending.next,
                      'pending state contradicts authenticated record')
                block(self.disk.staged is None, 'unexpected staged state after swap')
                self.witness.finalize(pending)
                return 'RECOVERED_COMMITTED'
            raise ValueError('BLOCKED: ambiguous or tampered interrupted state')
        if committed is None:
            block(current is None and self.disk.staged is None,
                  'unwitnessed state cannot rebootstrap')
            return 'UNINITIALIZED'
        block(current is not None and self.record(current) == committed,
              'state missing, rolled back, or witness disagrees')
        block(self.disk.staged is None, 'unexpected uncommitted staging residue')
        return 'STABLE'

    def apply_verified(self, verified_transition, *, interrupt_at=None):
        block(callable(verified_transition), 'Phase 3G verification callback required')
        self.recover()  # authenticated committed state is checked first
        committed, _ = self.witness.snapshot()
        prior_raw = self.disk.current
        # The callback must invoke Phase 3G with the above independent witness
        # checkpoint. It cannot advance the witness or write state on its own.
        candidate = verified_transition(prior_raw,
                                        committed.sha256 if committed else None)
        block(type(candidate) is dict, 'verified transition returned no state')
        new_raw = policy.canonical(candidate)
        new = self.record(new_raw)
        block(self.disk.current == prior_raw and self.witness.snapshot() == (committed, None),
              'inputs changed during signed verification')
        if committed is None:
            block(new.root_version == 1, 'bootstrap requires root v1')
        else:
            prior = inspect(prior_raw)
            if new_raw == prior_raw:
                return 'IDEMPOTENT'
            block(new.release_sequence > committed.release_sequence,
                  'no advance or same-sequence equivocation')
            block(new.root_version in (committed.root_version, committed.root_version+1),
                  'root version rollback or skip')
            if new.root_version == committed.root_version:
                block(candidate['root_keyid'] == prior['root_keyid'] and
                      candidate['root_metadata_sha256'] == prior['root_metadata_sha256'],
                      'same-root equivocation')
            else:
                block(candidate['root_keyid'] != prior['root_keyid'],
                      'root rotation did not change key')
        self.witness.prepare(committed, new)
        self._interrupt(interrupt_at, 'after_prepare')
        self.disk.stage(new_raw)
        self._interrupt(interrupt_at, 'after_stage')
        self.disk.replace()
        self._interrupt(interrupt_at, 'after_swap')
        self.witness.finalize(Intent(committed, new))
        self._interrupt(interrupt_at, 'after_finalize')
        return 'COMMITTED_IN_MODEL'

    @staticmethod
    def _interrupt(interrupt_at, point):
        if interrupt_at == point:
            raise InterruptedError('SIMULATED_POWER_LOSS: ' + point)


if __name__ == '__main__':
    print('PHASE3H_RECOVERY_MODEL=TEST_ONLY')
    print('PERSISTENT_ROLLBACK_STATE=NOT_IMPLEMENTED')
    print('EXTERNAL_WITNESS=SIMULATED_NOT_DEPLOYED')
    print('R2_UPLOAD=NO')
    print('PUBLIC_DISTRIBUTION=DISABLED')
    print('ANDROID_CLIENT_ENABLED=NO')
    print('ALL_ENGINES_READY=false')
