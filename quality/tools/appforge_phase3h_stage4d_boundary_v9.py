#!/usr/bin/env python3
"""Stage 4D test-only narrow witness API over a genuine Phase 3G bridge.

NOT process isolation, an HTTP server, independent witnessing, key protection,
anti-rollback hardware, or production authorization. The signer is a fixture
bound at construction time; Python object privacy is NOT a security boundary.
"""
from copy import deepcopy
from pathlib import Path

import appforge_phase3e_verify_v9 as crypto
import appforge_phase3h_stage4b_authorized_witness_v9 as authorization
import appforge_phase3h_stage4c_signed_bridge_v9 as bridge
import appforge_phase3h_stage4_witness_store_v9 as store


def block(ok, reason):
    if not ok:
        raise ValueError('BLOCKED: '+reason)


class OfflineBoundary4D:
    """Constrained *illustrative* caller API; NOT a security isolation layer.

    Test enrollment, root pin, and fixture signer are fixed at construction.
    Request API cannot supply signing callbacks, trusted roots, or witness
    implementation. No inherited TestWitnessStore methods are exposed.
    """
    __slots__ = ('__store', '__root', '__pin', '__signer', '__allowed')

    def __init__(self, directory, *, root_der_path, root_pin, authorizer_der,
                 authorizer_pin, allowed_devices, fixture_signer, test_mode=False):
        block(test_mode is True, 'explicit offline test mode required')
        block(callable(fixture_signer), 'fixture signer required')
        root = Path(root_der_path)
        block(root.is_file() and not root.is_symlink(), 'unsafe trusted bootstrap path')
        block(type(root_pin) is str and crypto.spki(root.read_bytes()) == root_pin,
              'independently pinned bootstrap key mismatch')
        block(type(allowed_devices) in (tuple, frozenset, set, list) and
              0 < len(allowed_devices) <= 16, 'trusted enrollment list required')
        for device in allowed_devices:
            store.check_device(device)
        self.__allowed = frozenset(allowed_devices)
        self.__root = root
        self.__pin = root_pin
        self.__signer = fixture_signer
        self.__store = authorization.OfflineTestAuthorizedWitness(
            directory, authorized_der=authorizer_der,
            authorized_pin=authorizer_pin, allowed_devices=self.__allowed,
            test_mode=True)

    def __enrolled(self, device):
        store.check_device(device)
        block(device in self.__allowed, 'device not independently enrolled')

    def snapshot(self, device):
        self.__enrolled(device)
        return deepcopy(self.__store.snapshot(device))

    def prepare(self, *, device, evidence, root_metadata, release_metadata,
                old_state=None, now=None):
        self.__enrolled(device)
        # Root trust is fixed at construction, never supplied by this request.
        current = self.__store.snapshot(device)
        block(current.pending is None, 'pending witness transaction exists')
        bootstrap = current.committed is None
        if bootstrap:
            block(current.generation == 0, 'unwitnessed rebootstrap forbidden')
            block(old_state is None, 'bootstrap cannot supply prior state')
        else:
            block(old_state is not None, 'committed state requires old state')
        # Test-only, caller can still reach internal objects through Python
        # introspection. Real trust requires independent process and keys.
        token, candidate = bridge.prepare_verified_test(
            witness=self.__store, device=device, evidence=evidence,
            root_metadata=root_metadata, release_metadata=release_metadata,
            sign_test_authorization=self.__signer, now=now,
            old_state=old_state,
            first_root_public_der=self.__root if bootstrap else None,
            first_root_pin=self.__pin if bootstrap else None)
        return token, deepcopy(candidate)

    def finalize(self, device, token):
        self.__enrolled(device)
        return deepcopy(self.__store.finalize(device, token))

    def abort(self, device, token):
        self.__enrolled(device)
        return deepcopy(self.__store.abort(device, token))


if __name__ == '__main__':
    print('PHASE3H_STAGE4D_BOUNDARY=OFFLINE_TEST_ONLY')
    print('PROCESS_ISOLATION=NOT_IMPLEMENTED')
    print('REAL_TRUSTED_VERIFIER=NOT_CONFIGURED')
    print('EXTERNAL_WITNESS=NOT_DEPLOYED')
    print('PERSISTENT_ROLLBACK_STATE=NOT_IMPLEMENTED')
    print('R2_UPLOAD=NO')
    print('PUBLIC_DISTRIBUTION=DISABLED')
    print('ANDROID_CLIENT_ENABLED=NO')
    print('ALL_ENGINES_READY=false')
