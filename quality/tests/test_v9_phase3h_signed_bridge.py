"""Phase 3H witness MODEL bridged to genuine Phase 3G Ed25519 verifier.

Only temporary signing keys and temporary test data; no real witness service.
"""
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parents[1] / 'tools'))
import appforge_phase3h_recovery_model_v9 as h
import test_v9_phase3g_e2e as source_fixture


class Phase3HRealSignatureBridge(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # Reuse temporary fixture key-generation and actual OpenSSL signatures.
        source_fixture.Phase3GEndToEnd.setUpClass()

    @classmethod
    def tearDownClass(cls):
        source_fixture.Phase3GEndToEnd.tearDownClass()

    def setUp(self):
        self.fx=source_fixture.Phase3GEndToEnd('test_01_real_bootstrap_signatures')
        self.fx.setUp()
        self.addCleanup(self.fx.doCleanups)
        # TEST-ONLY synthetic evidence fixture. Phase 3G's evaluate() temporarily
        # substitutes its fixture pin, but Phase 3H validates returned state
        # after evaluate() exits. Keep exactly the SAME synthetic pin scoped to
        # this test's entire recovery transaction. Production pins are unchanged.
        fixture_pin = patch.object(h.policy, 'EVIDENCE', self.fx.digest)
        fixture_pin.start()
        self.addCleanup(fixture_pin.stop)
        self.model=h.RecoveryModel(h.InMemoryDisk(), h.InMemoryWitness())

    def signed(self, prior, checkpoint):
        if prior is None:
            return self.fx.evaluate()
        prior_path=self.fx.d/'phase3h-checkpointed-prior.json'
        prior_path.write_bytes(prior)  # test fixture only
        return self.fx.evaluate(prior_path, checkpoint)

    def bootstrap(self):
        self.assertEqual(self.model.apply_verified(self.signed),'COMMITTED_IN_MODEL')

    def test_01_real_signed_bootstrap_and_release_advance(self):
        self.bootstrap()
        self.fx.next_release(2)
        self.assertEqual(self.model.apply_verified(self.signed),'COMMITTED_IN_MODEL')
        self.assertEqual(self.model.witness.committed.release_sequence,2)

    def test_02_unsigned_metadata_mutation_never_prepares_witness(self):
        self.bootstrap()
        first=self.model.disk.current
        self.fx.next_release(2)
        self.fx.release['payload']['sequence']=3
        self.fx.write()  # no re-sign: mandatory crypto failure
        with self.assertRaisesRegex(ValueError,'signature verification failed'):
            self.model.apply_verified(self.signed)
        self.assertEqual(self.model.disk.current,first)
        self.assertIsNone(self.model.witness.pending)

    def test_03_real_double_signed_root_rotation(self):
        self.bootstrap()
        self.fx.root=self.fx.make_root(version=2,rotate=True)
        self.fx.release=self.fx.make_release(seq=2,root_version=2)
        self.fx.write()
        self.assertEqual(self.model.apply_verified(self.signed),'COMMITTED_IN_MODEL')
        self.assertEqual(self.model.witness.committed.root_version,2)

    def test_04_root_rotation_without_old_signature_never_advances(self):
        self.bootstrap()
        first=self.model.disk.current
        self.fx.root=self.fx.make_root(version=2,rotate=True,signers=('new',))
        self.fx.release=self.fx.make_release(seq=2,root_version=2)
        self.fx.write()
        with self.assertRaisesRegex(ValueError,'previous trusted root'):
            self.model.apply_verified(self.signed)
        self.assertEqual(self.model.disk.current,first)
        self.assertIsNone(self.model.witness.pending)

    def test_05_witness_unavailable_blocks_even_with_valid_signature(self):
        self.bootstrap()
        self.fx.next_release(2)
        self.model.witness.online=False
        with self.assertRaisesRegex(ValueError,'witness unavailable'):
            self.model.apply_verified(self.signed)


if __name__=='__main__':
    unittest.main()
