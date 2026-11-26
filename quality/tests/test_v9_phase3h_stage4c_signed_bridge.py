"""Stage 4C offline Phase 3G -> Stage 4B signed witness TEST bridge.

Real OpenSSL Ed25519 root/release/auth signatures; ephemeral keys only. These
integration tests DO NOT prove an external verifier or independent witness.
"""
import base64
from datetime import timedelta
import hashlib
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools'))
import appforge_phase3h_stage4c_signed_bridge_v9 as bridge
import appforge_phase3h_stage4b_authorized_witness_v9 as auth
import test_v9_phase3g_e2e as source

DEVICE = hashlib.sha256(b'phase4c-isolated-device').hexdigest()
NOW = source.NOW


def openssl(*args):
    return subprocess.run(['openssl', *map(str, args)], check=True,
                          capture_output=True, timeout=20).stdout


class Stage4CVerifiedBridgeTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        source.Phase3GEndToEnd.setUpClass()
        cls.keytmp = tempfile.TemporaryDirectory(prefix='stage4c-auth-key-')
        cls.kdir = Path(cls.keytmp.name)
        cls.private = cls.kdir / 'test-authorizer.key'
        openssl('genpkey', '-algorithm', 'ED25519', '-out', cls.private)
        cls.der = openssl('pkey', '-in', cls.private, '-pubout', '-outform', 'DER')
        cls.pin = auth.ed25519.spki(cls.der)

    @classmethod
    def tearDownClass(cls):
        cls.keytmp.cleanup()
        source.Phase3GEndToEnd.tearDownClass()

    def setUp(self):
        self.fx = source.Phase3GEndToEnd('test_01_real_bootstrap_signatures')
        self.fx.setUp()
        self.addCleanup(self.fx.doCleanups)
        p1 = patch.object(bridge.phase3g.crypto, 'EVIDENCE_PIN', self.fx.digest)
        p2 = patch.object(bridge.phase3g.policy, 'EVIDENCE', self.fx.digest)
        p1.start(); p2.start()
        self.addCleanup(p1.stop); self.addCleanup(p2.stop)
        self.temp = tempfile.TemporaryDirectory(prefix='stage4c-test-witness-')
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.directory.chmod(0o700)
        self.witness = auth.OfflineTestAuthorizedWitness(
            self.directory, authorized_der=self.der, authorized_pin=self.pin,
            allowed_devices=[DEVICE], test_mode=True)
        self.count = 0
        self.last = None

    def sign(self, payload):
        self.count += 1
        message = self.directory / 'signed-message'
        message.write_bytes(auth.DOMAIN + auth.canonical(payload))
        sig = openssl('pkeyutl', '-sign', '-inkey', self.private,
                      '-rawin', '-in', message)
        return auth.canonical({'payload': payload, 'keyid': self.pin,
                               'sig_b64': base64.b64encode(sig).decode('ascii')})

    def prepare(self, *, old=None, sign=None, witness=None, **kwargs):
        params = dict(witness=witness or self.witness, device=DEVICE,
            evidence=self.fx.evidence, root_metadata=self.fx.root_path,
            release_metadata=self.fx.release_path, now=NOW,
            sign_test_authorization=sign if sign is not None else self.sign,
            old_state=old)
        if old is None:
            params['first_root_public_der'] = self.fx.pub
            params['first_root_pin'] = self.fx.keys['old']['id']
        params.update(kwargs)
        self.last = bridge.prepare_verified_test(**params)
        return self.last

    def bootstrap(self):
        token, candidate = self.prepare()
        self.witness.finalize(DEVICE, token)
        location, sha = self.fx.checkpoint(candidate)
        return location, candidate, sha

    def reject(self, msg, **kwargs):
        before = self.witness.snapshot(DEVICE)
        with self.assertRaisesRegex((ValueError, OSError), msg):
            self.prepare(**kwargs)
        self.assertEqual(self.witness.snapshot(DEVICE), before)

    def test_01_genuine_phase3g_bootstrap_then_one_use_authorization(self):
        token, state = self.prepare()
        self.assertEqual(state['release_sequence'], 1)
        self.assertEqual(len(token), 32)
        self.assertEqual(self.witness.snapshot(DEVICE).pending['next']['release_sequence'], 1)
        self.assertEqual(self.count, 1)

    def test_02_genuine_signed_update(self):
        path, _, _ = self.bootstrap()
        self.fx.next_release(2)
        token, state = self.prepare(old=path)
        snap = self.witness.finalize(DEVICE, token)
        self.assertEqual(snap.committed['release_sequence'], 2)
        self.assertEqual(state['root_version'], 1)

    def test_03_genuine_double_signed_root_rotation(self):
        path, _, _ = self.bootstrap()
        self.fx.root = self.fx.make_root(version=2, rotate=True)
        self.fx.release = self.fx.make_release(seq=2, root_version=2)
        self.fx.write()
        token, state = self.prepare(old=path)
        self.assertEqual(state['root_version'], 2)
        self.witness.finalize(DEVICE, token)

    def test_04_unsigned_release_mutation_never_signs_authorization(self):
        path, _, _ = self.bootstrap()
        self.fx.next_release(2)
        self.fx.release['payload']['sequence'] = 3
        self.fx.write()
        n = self.count
        self.reject('signature verification failed', old=path)
        self.assertEqual(self.count, n)

    def test_05_unsigned_root_mutation_never_signs(self):
        self.fx.root['payload']['release_threshold'] = 2
        self.fx.write()
        self.reject('signature verification failed')
        self.assertEqual(self.count, 0)

    def test_06_old_root_signature_required(self):
        path, _, _ = self.bootstrap()
        self.fx.root = self.fx.make_root(version=2, rotate=True, signers=('new',))
        self.fx.release = self.fx.make_release(seq=2, root_version=2)
        self.fx.write()
        n = self.count
        self.reject('previous trusted root', old=path)
        self.assertEqual(self.count, n)

    def test_07_prior_state_tamper_cannot_use_signed_release(self):
        path, _, _ = self.bootstrap()
        self.fx.next_release(2)
        path.write_bytes(path.read_bytes() + b' ')
        self.reject('canonical|mismatch', old=path)

    def test_08_missing_prior_state_blocks_without_signing(self):
        self.bootstrap()
        self.fx.next_release(2)
        n = self.count
        self.reject('requires prior state')
        self.assertEqual(self.count, n)

    def test_09_external_root_cannot_override_checkpoint(self):
        path, _, _ = self.bootstrap()
        self.fx.next_release(2)
        self.reject('bootstrap root cannot override', old=path,
                    first_root_public_der=self.fx.pub,
                    first_root_pin=self.fx.keys['old']['id'])

    def test_10_bootstrap_after_abort_blocks(self):
        token, _ = self.prepare()
        self.witness.abort(DEVICE, token)
        self.reject('rebootstrap')

    def test_11_second_update_while_pending_blocks(self):
        self.prepare()
        self.reject('pending witness transaction')

    def test_12_signer_failure_never_prepares(self):
        def broken(_):
            raise ValueError('fixture signer offline')
        self.reject('fixture signer offline', sign=broken)

    def test_13_signer_mutated_candidate_rejected(self):
        def malicious(p):
            p['next']['release_sequence'] = 2
            return self.sign(p)
        self.reject('authorization transition mismatch', sign=malicious)

    def test_14_rogue_signature_does_not_write_state(self):
        def rogue(p):
            raw = auth.parse(self.sign(p))
            raw['sig_b64'] = base64.b64encode(b'\x00'*64).decode()
            return auth.canonical(raw)
        self.reject('signature verification failed', sign=rogue)

    def test_15_witness_changed_during_signing_blocks(self):
        def mutate_then_sign(p):
            other = self.witness.prepare_model(
                DEVICE, None, {'sha256': 'a'*64, 'root_version': 1,
                               'release_sequence': 1}, authorize=lambda *_: True)
            self.witness.abort(DEVICE, other)
            return self.sign(p)
        # Stage 4A inherited bypass is deliberately still available TEST-ONLY.
        before = self.witness.snapshot(DEVICE)
        with self.assertRaisesRegex(ValueError, 'witness state changed during test signing'):
            self.prepare(sign=mutate_then_sign)
        self.assertEqual(self.witness.snapshot(DEVICE).generation, before.generation+2)

    def test_16_replayed_same_signed_release_no_new_witness_prepare(self):
        path, _, _ = self.bootstrap()
        n = self.count
        self.reject('new signed release required', old=path)
        self.assertEqual(self.count, n)

    def test_17_wrong_device_not_enrolled(self):
        self.reject('device not independently enrolled|invalid device',
                    device=hashlib.sha256(b'unknown').hexdigest())

    def test_18_untrusted_time_denied(self):
        self.reject('trusted test clock', now=NOW.replace(tzinfo=None))

    def test_19_proof_never_enables_distribution(self):
        token, candidate = self.prepare()
        for field in ('r2_upload_allowed', 'public_distribution_allowed',
                      'android_client_enabled'):
            self.assertIs(candidate[field], False)
        self.assertIsInstance(token, str)

    def test_20_only_test_mode_no_network_signing_or_persistence_claim(self):
        data = Path(bridge.__file__).read_text()
        for forbidden in ('genpkey', "'pkeyutl'", 'requests.post',
                          'socket.socket(', 'r2.put(', 'gh release'):
            self.assertNotIn(forbidden, data)
        self.assertIn('REAL_TRUSTED_VERIFIER=NOT_CONFIGURED', data)


if __name__ == '__main__':
    unittest.main()
