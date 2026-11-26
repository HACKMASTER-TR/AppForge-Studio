"""Stage 4D: offline narrow API tests, real signed Phase 3G fixture integration."""
from pathlib import Path
import hashlib
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]/'tools'))
import appforge_phase3h_stage4d_boundary_v9 as boundary
import test_v9_phase3h_stage4c_signed_bridge as prev

DEVICE = prev.DEVICE
OTHER = hashlib.sha256(b'unknown-stage4d-device').hexdigest()
NOW = prev.NOW


class Stage4DBoundaryTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        prev.Stage4CVerifiedBridgeTests.setUpClass()

    @classmethod
    def tearDownClass(cls):
        prev.Stage4CVerifiedBridgeTests.tearDownClass()

    def setUp(self):
        self.fx = prev.Stage4CVerifiedBridgeTests('test_01_genuine_phase3g_bootstrap_then_one_use_authorization')
        self.fx.setUp()
        self.addCleanup(self.fx.doCleanups)
        self.bound = self.open()

    def open(self, **overrides):
        kw = dict(directory=self.fx.directory,root_der_path=self.fx.fx.pub,
                  root_pin=self.fx.fx.keys['old']['id'],
                  authorizer_der=self.fx.der, authorizer_pin=self.fx.pin,
                  allowed_devices=(DEVICE,),fixture_signer=self.fx.sign,test_mode=True)
        kw.update(overrides)
        return boundary.OfflineBoundary4D(**kw)

    def prepare(self, **kwargs):
        arg = dict(device=DEVICE,evidence=self.fx.fx.evidence,
                   root_metadata=self.fx.fx.root_path,
                   release_metadata=self.fx.fx.release_path,now=NOW)
        arg.update(kwargs)
        return self.bound.prepare(**arg)

    def bootstrap(self):
        token,candidate=self.prepare()
        self.bound.finalize(DEVICE, token)
        state,digest=self.fx.fx.checkpoint(candidate)
        return state,candidate,digest

    def rejects(self, text, **kwargs):
        old=self.bound.snapshot(DEVICE)
        with self.assertRaisesRegex((TypeError,ValueError,OSError),text):
            self.prepare(**kwargs)
        self.assertEqual(self.bound.snapshot(DEVICE),old)

    def test_01_real_signed_bootstrap(self):
        token,candidate=self.prepare()
        self.assertEqual(candidate['release_sequence'],1)
        self.assertEqual(self.bound.snapshot(DEVICE).pending['token'],token)

    def test_02_real_signed_update(self):
        old,_,_=self.bootstrap();self.fx.fx.next_release(2)
        token,state=self.prepare(old_state=old)
        self.assertEqual(state['release_sequence'],2)
        self.assertEqual(self.bound.finalize(DEVICE,token).committed['release_sequence'],2)

    def test_03_genuine_dual_signed_rotation(self):
        old,_,_=self.bootstrap()
        self.fx.fx.root=self.fx.fx.make_root(version=2,rotate=True)
        self.fx.fx.release=self.fx.fx.make_release(seq=2,root_version=2)
        self.fx.fx.write()
        token,state=self.prepare(old_state=old)
        self.assertEqual(state['root_version'],2)
        self.bound.finalize(DEVICE,token)

    def test_04_cannot_pass_callback_from_request(self):
        self.rejects('unexpected keyword',sign_test_authorization=lambda _: b'bad')

    def test_05_cannot_pass_bootstrap_key_from_request(self):
        self.rejects('unexpected keyword',first_root_pin='0'*64)

    def test_06_no_inherited_unsafe_prepare_model(self):
        self.assertFalse(hasattr(self.bound,'prepare_model'))
        self.assertFalse(hasattr(self.bound,'prepare_signed_test'))
        self.assertFalse(hasattr(self.bound,'_connect'))

    def test_07_invalid_release_signature_fails_without_signing(self):
        self.fx.fx.release['payload']['sequence']=2;self.fx.fx.write()
        prior=self.fx.count
        self.rejects('signature verification failed')
        self.assertEqual(self.fx.count,prior)

    def test_08_invalid_root_signature_fails_without_signing(self):
        self.fx.fx.root['payload']['release_threshold']=2;self.fx.fx.write()
        prior=self.fx.count
        self.rejects('signature verification failed')
        self.assertEqual(self.fx.count,prior)

    def test_09_wrong_trusted_root_pin_constructor(self):
        with self.assertRaisesRegex(ValueError,'pinned bootstrap key mismatch'):
            self.open(root_pin='f'*64)

    def test_10_rogue_authorizer_der_constructor(self):
        with self.assertRaisesRegex(ValueError,'pin mismatch'):
            self.open(authorizer_pin='0'*64)

    def test_11_unknown_device_blocked(self):
        self.rejects('device not independently enrolled',device=OTHER)

    def test_12_empty_enrollment_blocked(self):
        with self.assertRaisesRegex(ValueError,'enrollment list'):
            self.open(allowed_devices=[])

    def test_13_test_mode_required(self):
        with self.assertRaisesRegex(ValueError,'test mode'):
            self.open(test_mode=False)

    def test_14_missing_fixture_signer_blocked(self):
        with self.assertRaisesRegex(ValueError,'fixture signer required'):
            self.open(fixture_signer=None)

    def test_15_pending_intent_blocks_second_update(self):
        self.prepare()
        self.rejects('pending witness transaction')

    def test_16_aborted_bootstrap_blocks_repeat(self):
        token,_=self.prepare();self.bound.abort(DEVICE,token)
        self.rejects('unwitnessed rebootstrap')

    def test_17_committed_requires_prior_state(self):
        self.bootstrap()
        self.rejects('committed state requires old state')

    def test_18_old_state_tampered_rejected(self):
        prior,_,_=self.bootstrap();self.fx.fx.next_release(2)
        prior.write_bytes(prior.read_bytes()+b' ')
        self.rejects('canonical|mismatch',old_state=prior)

    def test_19_same_signed_release_replay_blocked(self):
        prior,_,_=self.bootstrap()
        self.rejects('new signed release required',old_state=prior)

    def test_20_roots_cannot_be_replaced_by_caller(self):
        prior,_,_=self.bootstrap();self.fx.fx.next_release(2)
        self.rejects('unexpected keyword',old_state=prior,first_root_public_der=self.fx.fx.pub)

    def test_21_snapshot_deepcopy_isolated(self):
        self.prepare();first=self.bound.snapshot(DEVICE)
        first.pending['next']['sha256']='0'*64
        self.assertNotEqual(first,self.bound.snapshot(DEVICE))

    def test_22_finalized_checkpoint_copy_isolated(self):
        token,_=self.prepare();snap=self.bound.finalize(DEVICE,token)
        snap.committed['sha256']='0'*64
        self.assertNotEqual(snap,self.bound.snapshot(DEVICE))

    def test_23_wrong_device_cannot_finalize(self):
        token,_=self.prepare()
        with self.assertRaisesRegex(ValueError,'not independently enrolled'):
            self.bound.finalize(OTHER,token)

    def test_24_wrong_token_cannot_finalize(self):
        self.prepare()
        with self.assertRaisesRegex(ValueError,'missing or stale'):
            self.bound.finalize(DEVICE,'f'*32)

    def test_25_witness_reopen_preserves_generation(self):
        token,_=self.prepare();self.bound.abort(DEVICE,token)
        reopened=self.open()
        self.assertEqual(reopened.snapshot(DEVICE).generation,2)

    def test_26_expired_authorization_fails_closed(self):
        from datetime import timedelta
        def expired_signer(payload):
            payload['issued_at']=(NOW-timedelta(minutes=1)).strftime('%Y-%m-%dT%H:%M:%SZ')
            payload['expires_at']=(NOW-timedelta(seconds=1)).strftime('%Y-%m-%dT%H:%M:%SZ')
            return self.fx.sign(payload)
        self.bound=self.open(fixture_signer=expired_signer)
        self.rejects('authorization expired')

    def test_27_signer_error_does_not_prepare(self):
        self.bound=self.open(fixture_signer=lambda _: (_ for _ in ()).throw(ValueError('fixture unavailable')))
        self.rejects('fixture unavailable')

    def test_28_candidate_cannot_enable_distribution(self):
        _,candidate=self.prepare()
        for key in ('r2_upload_allowed','public_distribution_allowed','android_client_enabled'):
            self.assertIs(candidate[key],False)

    def test_29_output_candidate_not_same_as_pending(self):
        _,candidate=self.prepare()
        candidate['release_sequence']=9876
        self.assertEqual(self.bound.snapshot(DEVICE).pending['next']['release_sequence'],1)

    def test_30_no_network_server_or_production_claim(self):
        text=Path(boundary.__file__).read_text()
        for bad in ('socket.socket(','http.server','requests.post','cloudflare','genpkey','pkeyutl'):
            self.assertNotIn(bad,text)
        self.assertIn('PROCESS_ISOLATION=NOT_IMPLEMENTED',text)
        self.assertIn('PERSISTENT_ROLLBACK_STATE=NOT_IMPLEMENTED',text)


if __name__=='__main__':
    unittest.main()
