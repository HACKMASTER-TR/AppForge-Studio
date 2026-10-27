"""Phase 3F offline policy tests. No network or persistent trusted state writes."""
import base64
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import types
import unittest
from unittest.mock import patch

SCRIPT=Path(__file__).resolve().parents[1] / 'tools' / 'appforge_phase3f_policy_v9.py'
spec=importlib.util.spec_from_file_location('appforge_phase3f',SCRIPT)
m=importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)

# These tests exercise state policy only. Crypto correctness is covered by
# Phase 3E OpenSSL signature tests, not mocked policy inputs here.
SPKI_PREFIX=bytes.fromhex('302a300506032b6570032100')
OLD=SPKI_PREFIX+b'1'*32
NEW=SPKI_PREFIX+b'2'*32

def pid(raw): return hashlib.sha256(raw).hexdigest()

class FakeVerifiedAdapter:
    spki=staticmethod(pid)
    @staticmethod
    def key_entry(entry):
        data=base64.b64decode(entry['spki_der_b64'])
        assert pid(data)==entry['keyid']
        return entry['keyid'],data
    @staticmethod
    def envelope(doc):
        return doc['payload'],doc.get('signatures',{})


def signed_root(raw=OLD, v=1, threshold=1):
    return {'schema':1,'role':'appforge-v9-root','version':v,
            'issued_at':'2026-10-27T00:00:00Z','expires_at':'2026-11-01T00:00:00Z',
            'root_key':{'keyid':pid(raw),'spki_der_b64':base64.b64encode(raw).decode('ascii')},
            'release_keys':[{'keyid':pid(raw),'spki_der_b64':base64.b64encode(raw).decode('ascii')}],
            'release_threshold':threshold}


def signed_release(seq=1, root_version=1, evidence=m.EVIDENCE):
    return {'schema':1,'role':'appforge-v9-release','root_version':root_version,
            'sequence':seq,'snapshot':m.SNAPSHOT,'evidence_sha256':evidence,
            'issued_at':'2026-10-27T00:00:00Z','expires_at':'2026-11-01T00:00:00Z',
            'r2_upload_allowed':False,'public_distribution_allowed':False,
            'android_client_enabled':False}


def get_state(old=None, *, seq=1, version=1, key=OLD, root=None, rel=None, previous_key=OLD):
    root=root or signed_root(key,version)
    rel=rel or signed_release(seq,version)
    return m.build_next_state(old=old, verified_root=root,
        root_raw=m.canonical(root), release_raw=m.canonical({'payload':rel,'signatures':[]}),
        previous_root_der=previous_key, previous_root_pin=pid(previous_key),
        verifier=FakeVerifiedAdapter)

class Phase3FTests(unittest.TestCase):
    def setUp(self):
        self.t=tempfile.TemporaryDirectory()
        self.addCleanup(self.t.cleanup)
        self.dir=Path(self.t.name)
        self.before=get_state()

    def test_bootstrap_pinned_v1(self):
        self.assertEqual(get_state()['root_version'],1)

    def test_bootstrap_wrong_public_root(self):
        with self.assertRaisesRegex(ValueError,'bootstrap'):
            get_state(key=NEW)

    def test_bootstrap_skipped_root_version(self):
        with self.assertRaisesRegex(ValueError,'bootstrap'):
            get_state(version=2)

    def test_replay_exact_metadata_idempotent(self):
        self.assertEqual(get_state(self.before),self.before)

    def test_accept_strictly_newer_release(self):
        result=get_state(self.before,seq=2)
        self.assertEqual(result['release_sequence'],2)

    def test_release_rollback_rejected(self):
        state=get_state(self.before,seq=2)
        with self.assertRaisesRegex(ValueError,'rollback'):
            get_state(state,seq=1)

    def test_same_sequence_new_metadata_rejected(self):
        rel=signed_release(1)
        rel['expires_at']='2026-11-02T00:00:00Z'
        with self.assertRaisesRegex(ValueError,'same sequence'):
            get_state(self.before,rel=rel)

    def test_root_skip_rejected(self):
        with self.assertRaisesRegex(ValueError,'rotation skip'):
            get_state(self.before,seq=2,version=3,key=NEW)

    def test_root_version_rollback_rejected(self):
        rotated=get_state(self.before,seq=2,version=2,key=NEW)
        with self.assertRaisesRegex(ValueError,'rotation skip'):
            get_state(rotated,seq=3,version=1,key=OLD,previous_key=NEW)

    def test_new_root_version_must_change_root_key(self):
        with self.assertRaisesRegex(ValueError,'without key rotation'):
            get_state(self.before,seq=2,version=2,key=OLD)

    def test_same_version_cannot_change_root_key(self):
        with self.assertRaisesRegex(ValueError,'without increment'):
            get_state(self.before,seq=2,version=1,key=NEW)

    def test_same_version_root_metadata_immutable(self):
        root=signed_root(OLD,1,threshold=2)
        with self.assertRaisesRegex(ValueError,'equivocation'):
            get_state(self.before,seq=2,root=root)

    def test_valid_root_rotation_with_new_release(self):
        new=get_state(self.before,seq=2,version=2,key=NEW)
        self.assertEqual(new['root_keyid'],pid(NEW))
        self.assertEqual(get_state(new,seq=3,version=2,key=NEW,previous_key=NEW)['release_sequence'],3)

    def test_root_rotation_requires_sequence_advance(self):
        with self.assertRaisesRegex(ValueError,'same sequence'):
            get_state(self.before,seq=1,version=2,key=NEW)

    def test_old_state_wrong_prior_root_rejected(self):
        with self.assertRaisesRegex(ValueError,'previous state root'):
            get_state(self.before,seq=2,previous_key=NEW)

    def test_distribution_flag_true_forbidden(self):
        rel=signed_release(2)
        rel['r2_upload_allowed']=True
        with self.assertRaisesRegex(ValueError,'distribution'):
            get_state(self.before,seq=2,rel=rel)

    def test_evidence_swap_rejected(self):
        rel=signed_release(2,evidence='f'*64)
        with self.assertRaisesRegex(ValueError,'evidence'):
            get_state(self.before,seq=2,rel=rel)

    def test_state_digest_checkpoint_required(self):
        p=self.dir/'state.json'
        p.write_bytes(m.canonical(self.before))
        with self.assertRaisesRegex(ValueError,'external previous state'):
            m.inspect_existing_state(p,None)

    def test_state_wrong_checkpoint_rejected(self):
        p=self.dir/'state.json'
        p.write_bytes(m.canonical(self.before))
        with self.assertRaisesRegex(ValueError,'checkpoint mismatch'):
            m.inspect_existing_state(p,'f'*64)

    def test_valid_state_checkpoint_read(self):
        p=self.dir/'state.json'
        raw=m.canonical(self.before)
        p.write_bytes(raw)
        d,der=m.inspect_existing_state(p,m.sha(raw))
        self.assertEqual((d,der),(self.before,OLD))

    def test_tampered_state_blocked(self):
        p=self.dir/'state.json'
        raw=m.canonical(self.before)
        p.write_bytes(raw)
        p.write_bytes(raw+b' ')
        with self.assertRaisesRegex(ValueError,'canonical'):
            m.inspect_existing_state(p,m.sha(raw+b' '))

    def test_state_symlink_rejected(self):
        a=self.dir/'state.json';a.write_bytes(m.canonical(self.before))
        (self.dir/'alias').symlink_to(a)
        with self.assertRaisesRegex(ValueError,'symlink'):
            m.inspect_existing_state(self.dir/'alias',m.sha(a.read_bytes()))

    def test_duplicate_json_field_rejected(self):
        p=self.dir/'state.json'
        p.write_bytes(b'{"schema":1,"schema":2}\n')
        with self.assertRaisesRegex(ValueError,'duplicate'):
            m.inspect_existing_state(p,m.sha(p.read_bytes()))

    def test_out_of_range_version_rejected(self):
        p=dict(self.before)
        p['root_version']=0
        with self.assertRaisesRegex(ValueError,'root_version'):
            m.check_state(p)

    def test_boolean_as_version_rejected(self):
        p=dict(self.before)
        p['release_sequence']=True
        with self.assertRaisesRegex(ValueError,'release_sequence'):
            m.check_state(p)

    def test_unsigned_payload_not_proof(self):
        source=SCRIPT.read_text()
        self.assertIn('v.verify_all(',source)
        self.assertIn('v.verify_root(',source)
        self.assertNotIn('pkeyutl',source)

    def test_no_persistent_state_or_publication(self):
        source=SCRIPT.read_text()
        for tok in ('os.replace(', 'unlink(', 'put_object(', 'aws s3', 'genpkey', 'r2.put('):
            self.assertNotIn(tok,source)
        self.assertIn('PERSISTENT_ROLLBACK_STATE=NOT_IMPLEMENTED',source)

    def test_real_phase3d_pin_acceptance(self):
        p=self.dir/'evidence'
        p.write_bytes(b'data')
        self.assertNotEqual(m.sha(p.read_bytes()),m.EVIDENCE)
        # No test is permitted to synthesize a valid real Phase 3D evidence blob.

    def test_next_state_is_canonical_and_not_ready(self):
        data=m.canonical(get_state(self.before,seq=2))
        self.assertEqual(m.canonical(json.loads(data)),data)
        self.assertIn(b'"android_client_enabled":false',data)

if __name__=='__main__':
    unittest.main()
