"""Phase 3H crash/witness MODEL tests. No real disk, network, production keys.

This suite tests transaction semantics with a mock signed-verifier callback.
Actual Ed25519 authorization is separately exercised in Phase 3G's 27 tests.
"""
import base64
import hashlib
from pathlib import Path
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1] / 'tools'))
import appforge_phase3h_recovery_model_v9 as h

PREFIX=bytes.fromhex('302a300506032b6570032100')
K1=PREFIX+b'A'*32
K2=PREFIX+b'B'*32

def sha(x): return hashlib.sha256(x).hexdigest()


def state(seq=1, rootver=1, root=K1, rootmeta=None, relmeta=None):
    return {'schema':1,'stage':h.policy.ROOT,
        'snapshot':h.policy.SNAPSHOT,'evidence_sha256':h.policy.EVIDENCE,
        'root_version':rootver, 'root_keyid':sha(root),
        'root_spki_der_b64':base64.b64encode(root).decode('ascii'),
        'root_metadata_sha256':rootmeta or ('a'*64 if root==K1 else 'b'*64),
        'release_sequence':seq,'release_metadata_sha256':relmeta or sha(str(seq).encode()),
        'r2_upload_allowed':False,'public_distribution_allowed':False,
        'android_client_enabled':False}


class Phase3HRecoveryTests(unittest.TestCase):
    def setUp(self):
        self.disk=h.InMemoryDisk()
        self.witness=h.InMemoryWitness()
        self.model=h.RecoveryModel(self.disk,self.witness)
        self.first=state()
        self.bootstrap()

    def bootstrap(self):
        self.assertEqual(self.model.apply_verified(lambda _old,_cp: self.first),
                         'COMMITTED_IN_MODEL')

    def update(self, n=2, **kw):
        candidate=state(seq=n,**kw)
        return self.model.apply_verified(lambda old,cp: candidate)

    def test_01_initial_state_and_witness_agree(self):
        self.assertEqual(self.model.recover(),'STABLE')
        self.assertEqual(self.witness.committed.sha256,sha(self.disk.current))

    def test_02_signed_candidate_update_simulated(self):
        self.assertEqual(self.update(),'COMMITTED_IN_MODEL')
        self.assertEqual(self.witness.committed.release_sequence,2)

    def test_03_exact_replay_is_idempotent(self):
        old=self.disk.current
        self.assertEqual(self.model.apply_verified(lambda *_:dict(self.first)),'IDEMPOTENT')
        self.assertEqual(self.disk.current,old)

    def test_04_lower_release_rejected(self):
        self.update(3)
        with self.assertRaisesRegex(ValueError,'no advance'):
            self.update(2)

    def test_05_same_sequence_new_metadata_rejected(self):
        with self.assertRaisesRegex(ValueError,'same-sequence'):
            self.update(1,relmeta='f'*64)

    def test_06_root_version_skip_rejected(self):
        with self.assertRaisesRegex(ValueError,'root version'):
            self.update(2,rootver=3,root=K2)

    def test_07_root_version_rollback_rejected(self):
        self.update(2,rootver=2,root=K2)
        with self.assertRaisesRegex(ValueError,'root version'):
            self.update(3,rootver=1,root=K1)

    def test_08_same_root_equivocation_rejected(self):
        with self.assertRaisesRegex(ValueError,'same-root equivocation'):
            self.update(2,rootmeta='e'*64)

    def test_09_root_rotation_valid_with_model_verified_input(self):
        self.assertEqual(self.update(2,rootver=2,root=K2),'COMMITTED_IN_MODEL')
        self.assertEqual(self.witness.committed.root_version,2)

    def test_10_root_rotation_without_changed_key_rejected(self):
        with self.assertRaisesRegex(ValueError,'did not change key'):
            self.update(2,rootver=2,root=K1)

    def test_11_verifier_failure_has_no_side_effect(self):
        original=self.disk.current
        def reject(*_): raise ValueError('Ed25519 verification failed')
        with self.assertRaisesRegex(ValueError,'Ed25519'):
            self.model.apply_verified(reject)
        self.assertEqual(self.disk.current,original)
        self.assertIsNone(self.witness.pending)

    def test_12_callback_gets_trusted_checkpoint(self):
        before=self.witness.committed
        def cb(old,checkpoint):
            self.assertEqual(old,self.disk.current)
            self.assertEqual(checkpoint,before.sha256)
            return state(2)
        self.model.apply_verified(cb)

    def test_13_no_witness_available_blocks(self):
        self.witness.online=False
        with self.assertRaisesRegex(ValueError,'witness unavailable'):
            self.model.apply_verified(lambda *_:state(2))

    def test_14_missing_committed_local_state_blocks(self):
        self.disk.current=None
        with self.assertRaisesRegex(ValueError,'state missing'):
            self.model.recover()

    def test_15_corrupt_state_blocks(self):
        self.disk.current=b'{}\n'
        with self.assertRaises(ValueError): self.model.recover()

    def test_16_witness_downgrade_blocks(self):
        # Attack on untrusted state store; witness retains higher floor.
        old=self.disk.current
        self.update(2)
        self.disk.current=old
        with self.assertRaisesRegex(ValueError,'witness disagrees'):
            self.model.recover()

    def test_17_witness_identity_conflict_blocks(self):
        self.disk.current=h.policy.canonical(state(2))
        with self.assertRaisesRegex(ValueError,'witness disagrees'):
            self.model.recover()

    def test_18_powerloss_after_prepare_recovers_old(self):
        old=self.disk.current
        with self.assertRaises(InterruptedError):
            self.model.apply_verified(lambda *_:state(2),interrupt_at='after_prepare')
        self.assertEqual(self.model.recover(),'RECOVERED_PREVIOUS')
        self.assertEqual(self.disk.current,old)
        self.assertIsNone(self.witness.pending)

    def test_19_powerloss_after_stage_recovers_old(self):
        old=self.disk.current
        with self.assertRaises(InterruptedError):
            self.model.apply_verified(lambda *_:state(2),interrupt_at='after_stage')
        self.assertIsNotNone(self.disk.staged)
        self.assertEqual(self.model.recover(),'RECOVERED_PREVIOUS')
        self.assertEqual(self.disk.current,old)
        self.assertIsNone(self.disk.staged)

    def test_20_powerloss_after_swap_finalizes(self):
        with self.assertRaises(InterruptedError):
            self.model.apply_verified(lambda *_:state(2),interrupt_at='after_swap')
        self.assertEqual(self.model.recover(),'RECOVERED_COMMITTED')
        self.assertEqual(self.witness.committed.release_sequence,2)

    def test_21_powerloss_after_finalize_stable(self):
        with self.assertRaises(InterruptedError):
            self.model.apply_verified(lambda *_:state(2),interrupt_at='after_finalize')
        self.assertEqual(self.model.recover(),'STABLE')
        self.assertEqual(self.witness.committed.release_sequence,2)

    def test_22_pending_intent_tamper_blocks(self):
        with self.assertRaises(InterruptedError):
            self.model.apply_verified(lambda *_:state(2),interrupt_at='after_swap')
        self.disk.current=b'wrong\n'
        with self.assertRaisesRegex(ValueError,'ambiguous'):
            self.model.recover()
        self.assertIsNotNone(self.witness.pending)

    def test_23_tampered_stage_blocks(self):
        with self.assertRaises(InterruptedError):
            self.model.apply_verified(lambda *_:state(2),interrupt_at='after_stage')
        self.disk.staged=b'evil'
        with self.assertRaisesRegex(ValueError,'staged'):
            self.model.recover()

    def test_24_absent_witness_with_state_blocks_bootstrap(self):
        self.witness.committed=None
        with self.assertRaisesRegex(ValueError,'unwitnessed'):
            self.model.recover()

    def test_25_witness_conflict_prepare_blocks(self):
        self.witness.pending=h.Intent(None,h.Record('f'*64,1,2))
        with self.assertRaises(ValueError): self.model.apply_verified(lambda *_:state(2))

    def test_26_noncanonical_candidate_blocks(self):
        bad=state(2);bad['release_sequence']=True
        with self.assertRaisesRegex(ValueError,'release_sequence'):
            self.model.apply_verified(lambda *_:bad)

    def test_27_distribution_enable_blocks(self):
        bad=state(2);bad['r2_upload_allowed']=True
        with self.assertRaisesRegex(ValueError,'distribution'):
            self.model.apply_verified(lambda *_:bad)

    def test_28_failed_update_keeps_original_floor(self):
        previous=self.disk.current
        for invalid in [state(1,relmeta='e'*64),state(2,rootver=3,root=K2)]:
            with self.assertRaises(ValueError):
                self.model.apply_verified(lambda *_:invalid)
            self.assertEqual(self.disk.current,previous)
            self.assertEqual(self.witness.committed.release_sequence,1)

    def test_29_pending_old_vs_swapped_new_never_accepts_arbitrary_third(self):
        with self.assertRaises(InterruptedError):
            self.model.apply_verified(lambda *_:state(2),interrupt_at='after_prepare')
        self.disk.current=h.policy.canonical(state(3))
        with self.assertRaises(ValueError): self.model.recover()

    def test_30_no_real_persistence_or_network(self):
        src=Path(h.__file__).read_text()
        for forbidden in ('os.replace(', 'fsync(', 'requests.', 'r2.put(',
                          'genpkey', 'aws s3', 'subprocess.run('):
            self.assertNotIn(forbidden,src)
        self.assertIn('SIMULATED_POWER_LOSS',src)

    def test_31_uninitialized_empty_state(self):
        m=h.RecoveryModel(h.InMemoryDisk(),h.InMemoryWitness())
        self.assertEqual(m.recover(),'UNINITIALIZED')

    def test_32_bootstrap_without_root_version_one_rejected(self):
        m=h.RecoveryModel(h.InMemoryDisk(),h.InMemoryWitness())
        with self.assertRaisesRegex(ValueError,'bootstrap'):
            m.apply_verified(lambda *_:state(2,rootver=2,root=K2))

    def test_33_verify_attempt_mutating_disk_rejected(self):
        def compromised(*_):
            self.disk.current=b'injected'
            return state(2)
        with self.assertRaisesRegex(ValueError,'inputs changed'):
            self.model.apply_verified(compromised)
        self.assertIsNone(self.witness.pending)

    def test_34_witness_offline_during_recovery_blocks(self):
        with self.assertRaises(InterruptedError):
            self.model.apply_verified(lambda *_:state(2),interrupt_at='after_swap')
        self.witness.online=False
        with self.assertRaisesRegex(ValueError,'witness unavailable'):
            self.model.recover()


if __name__=='__main__':
    unittest.main()
