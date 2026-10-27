"""Phase 3E offline, test-only Ed25519 verification adversarial cases.

Every private key is ephemeral and stays in tempfile; no production signer.
"""
import base64
from datetime import datetime, timezone, timedelta
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

SCRIPT = Path(__file__).resolve().parents[1] / 'tools' / 'appforge_phase3e_verify_v9.py'
spec = importlib.util.spec_from_file_location('phase3e_verifier', SCRIPT)
v = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v)

NOW = datetime(2026,10,27,17,0,0,tzinfo=timezone.utc)

def openssl(*args, input=None):
    x=subprocess.run(['openssl', *map(str,args)],input=input,capture_output=True,check=True,timeout=15)
    return x.stdout

class TrustTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.t = tempfile.TemporaryDirectory(prefix='appforge-v9-phase3e-test-')
        cls.folder = Path(cls.t.name)
        cls.keys = {}
        for label in ('old','new','release_a','release_b','wrong'):
            p = cls.folder / (label+'.private.key')
            openssl('genpkey','-algorithm','ED25519','-out',p)
            der = openssl('pkey','-in',p,'-pubout','-outform','DER')
            identity = v.spki(der)
            cls.keys[label]={'private':p,'der':der,'id':identity,
                             'entry':{'keyid':identity,'spki_der_b64':base64.b64encode(der).decode('ascii')}}

    @classmethod
    def tearDownClass(cls):
        cls.t.cleanup()

    def setUp(self):
        self.workspace = tempfile.TemporaryDirectory(prefix='appforge-v9-phase3e-fixture-')
        self.addCleanup(self.workspace.cleanup)
        self.dir = Path(self.workspace.name)
        self.evidence = self.dir/'evidence.json'
        status={'schema':1,'stage':'OFFLINE_EVIDENCE_ONLY', 'snapshot':v.SNAPSHOT,
                'original_objects_verified':218,'original_deb_objects_verified':214,
                'ubuntu_inrelease_verified':4,'signed_packages_indexes_verified':32,
                'distinct_content_addressed_objects':248,
                'distinct_content_addressed_bytes':650832480,
                'appforge_ed25519_release_signature':'NOT_PRESENT',
                'r2_upload_allowed':False,'public_distribution_allowed':False,
                'android_client_enabled':False,'apt_ready':False,'all_engines_ready':False}
        self.evidence.write_bytes(v.canonical(status))
        self.digest=hashlib.sha256(self.evidence.read_bytes()).hexdigest()
        self.root = self.make_root()
        self.release = self.make_release()
        self.write()

    def date(self, delta):
        return (NOW + timedelta(days=delta)).strftime('%Y-%m-%dT%H:%M:%SZ')

    def sign(self, key, context, payload):
        msg=context+v.canonical(payload)
        with tempfile.NamedTemporaryFile(mode='wb', dir=self.dir) as f:
            f.write(msg)
            f.flush()
            signature=openssl('pkeyutl','-sign','-inkey',self.keys[key]['private'],'-rawin','-in',f.name)
        self.assertEqual(len(signature),64)
        return {'keyid':self.keys[key]['id'],
                'sig_b64':base64.b64encode(signature).decode('ascii')}

    def make_root(self, version=1, replace=False, threshold=1):
        payload={'schema':1,'role':'appforge-v9-root','version':version,
                 'issued_at':self.date(-1),'expires_at':self.date(30),
                 'root_key':self.keys['new' if replace else 'old']['entry'],
                 'release_keys':[self.keys[x]['entry'] for x in ('release_a','release_b')],
                 'release_threshold':threshold}
        names=['old','new'] if replace else ['old']
        return {'payload':payload,'signatures':[self.sign(n,v.ROOT_CONTEXT,payload) for n in names]}

    def make_release(self, root_version=1, seq=9, signers=('release_a',)):
        payload={'schema':1,'role':'appforge-v9-release','root_version':root_version,
                 'sequence':seq,'snapshot':v.SNAPSHOT,'evidence_sha256':self.digest,
                 'issued_at':self.date(-1),'expires_at':self.date(20),
                 'r2_upload_allowed':False,'public_distribution_allowed':False,
                 'android_client_enabled':False}
        return {'payload':payload,'signatures':[self.sign(n,v.RELEASE_CONTEXT,payload) for n in signers]}

    def write(self):
        self.rootfile=self.dir/'root.json'
        self.releasefile=self.dir/'release.json'
        self.rootfile.write_bytes(v.canonical(self.root))
        self.releasefile.write_bytes(v.canonical(self.release))

    def verify(self,*,min_root=1,min_seq=9,rootpin='old'):
        return v.verify_all(evidence=self.evidence,root_metadata=self.rootfile,
                  release_metadata=self.releasefile,
                  trusted_root_pub_der=self.keys[rootpin]['der'],
                  trusted_root_pin=self.keys[rootpin]['id'],
                  min_root_version=min_root,min_release_sequence=min_seq,now=NOW,
                  evidence_sha256=self.digest)

    def test_valid_ed25519_and_signed_evidence(self):
        self.assertEqual(self.verify()['sequence'],9)

    def test_root_key_rotation_requires_old_and_new_signatures(self):
        self.root=self.make_root(version=2,replace=True)
        self.release=self.make_release(root_version=2)
        self.write()
        self.assertEqual(self.verify(min_root=2)['root_version'],2)

    def test_root_rotation_missing_old_authorization_rejected(self):
        self.root=self.make_root(version=2,replace=True)
        self.root['signatures']=self.root['signatures'][1:]
        self.release=self.make_release(root_version=2)
        self.write()
        with self.assertRaisesRegex(ValueError,'previous trusted root'):
            self.verify()

    def test_root_rotation_missing_new_key_proof_rejected(self):
        self.root=self.make_root(version=2,replace=True)
        self.root['signatures']=self.root['signatures'][:1]
        self.release=self.make_release(root_version=2)
        self.write()
        with self.assertRaisesRegex(ValueError,'new root possession'):
            self.verify()

    def test_bogus_root_pin_rejected(self):
        with self.assertRaisesRegex(ValueError,'not externally pinned'):
            v.verify_all(evidence=self.evidence,root_metadata=self.rootfile,
             release_metadata=self.releasefile,trusted_root_pub_der=self.keys['old']['der'],
             trusted_root_pin=self.keys['wrong']['id'],min_root_version=1,
             min_release_sequence=9,now=NOW,evidence_sha256=self.digest)

    def test_bootstrap_wrong_key_rejected(self):
        self.root=self.make_root(version=1,replace=True)
        self.write()
        with self.assertRaisesRegex(ValueError,'bootstrapping'):
            self.verify()

    def test_signed_release_threshold_two(self):
        self.root=self.make_root(threshold=2)
        self.release=self.make_release(signers=('release_a','release_b'))
        self.write()
        self.assertEqual(self.verify()['sequence'],9)

    def test_insufficient_release_threshold_rejected(self):
        self.root=self.make_root(threshold=2)
        self.write()
        with self.assertRaisesRegex(ValueError,'threshold'):
            self.verify()

    def test_release_sequence_rollback_rejected(self):
        with self.assertRaisesRegex(ValueError,'rollback'):
            self.verify(min_seq=10)

    def test_root_metadata_version_rollback_rejected(self):
        with self.assertRaisesRegex(ValueError,'rollback'):
            self.verify(min_root=2)

    def test_signature_payload_tampering_rejected(self):
        self.release['payload']['sequence']=10
        self.write()
        with self.assertRaisesRegex(ValueError,'signature verification failed'):
            self.verify()

    def test_evidence_tamper_rejected_even_if_sha_ref_same(self):
        self.evidence.write_bytes(self.evidence.read_bytes()+b' ')
        with self.assertRaisesRegex(ValueError,'not canonical'):
            self.verify()

    def test_expired_root_rejected(self):
        self.root['payload']['expires_at']=self.date(-0.5)
        self.root['signatures']=[self.sign('old',v.ROOT_CONTEXT,self.root['payload'])]
        self.write()
        with self.assertRaisesRegex(ValueError,'expired'):
            self.verify()

    def test_future_dated_release_rejected(self):
        self.release['payload']['issued_at']=self.date(1)
        self.release['signatures']=[self.sign('release_a',v.RELEASE_CONTEXT,self.release['payload'])]
        self.write()
        with self.assertRaisesRegex(ValueError,'future-dated'):
            self.verify()

    def test_release_expiry_more_than_90_days_rejected(self):
        self.release['payload']['expires_at']=self.date(120)
        self.release['signatures']=[self.sign('release_a',v.RELEASE_CONTEXT,self.release['payload'])]
        self.write()
        with self.assertRaisesRegex(ValueError,'expiry too long'):
            self.verify()

    def test_untrusted_release_signer_rejected(self):
        self.release=self.make_release(signers=('wrong',))
        self.write()
        with self.assertRaisesRegex(ValueError,'untrusted release signer'):
            self.verify()

    def test_release_wrong_root_version_rejected(self):
        self.release=self.make_release(root_version=2)
        self.write()
        with self.assertRaisesRegex(ValueError,'root-version mismatch'):
            self.verify()

    def test_release_attempt_to_enable_distribution_rejected(self):
        self.release['payload']['r2_upload_allowed']=True
        self.release['signatures']=[self.sign('release_a',v.RELEASE_CONTEXT,self.release['payload'])]
        self.write()
        with self.assertRaisesRegex(ValueError,'distribution must remain disabled'):
            self.verify()

    def test_duplicate_json_key_rejected(self):
        self.rootfile.write_bytes(b'{"payload":{},"payload":{},"signatures":[]}\n')
        with self.assertRaisesRegex(ValueError,'duplicate JSON key'):
            self.verify()

    def test_noncanonical_json_rejected(self):
        self.rootfile.write_text(json.dumps(self.root,indent=2)+'\n')
        with self.assertRaisesRegex(ValueError,'not canonical'):
            self.verify()

    def test_weak_non_ed25519_spki_rejected(self):
        with self.assertRaisesRegex(ValueError,'only pure Ed25519'):
            v.spki(b'X'*44)

    def test_invalid_short_signature_rejected(self):
        self.release['signatures'][0]['sig_b64']=base64.b64encode(b'x'*63).decode('ascii')
        self.write()
        with self.assertRaisesRegex(ValueError,'signature length'):
            self.verify()

    def test_wrong_crypto_domain_rejected(self):
        self.release['signatures']=[self.sign('release_a',v.ROOT_CONTEXT,self.release['payload'])]
        self.write()
        with self.assertRaisesRegex(ValueError,'signature verification failed'):
            self.verify()

    def test_release_evidence_digest_mismatch_rejected(self):
        self.release['payload']['evidence_sha256']='0'*64
        self.release['signatures']=[self.sign('release_a',v.RELEASE_CONTEXT,self.release['payload'])]
        self.write()
        with self.assertRaisesRegex(ValueError,'evidence binding mismatch'):
            self.verify()

    def test_signed_root_extra_field_rejected(self):
        self.root['payload']['allow_public']=True
        self.write()
        with self.assertRaisesRegex(ValueError,'invalid root metadata fields'):
            self.verify()

    def test_test_suite_no_private_key_writes_in_verifier(self):
        src=SCRIPT.read_text()
        for forbidden in ('genpkey','-sign\'', 'put_object(', 'aws s3', 'r2.put('):
            self.assertNotIn(forbidden,src)
        self.assertIn('PUBLIC_DISTRIBUTION=DISABLED',src)

if __name__ == '__main__':
    unittest.main()
