"""Stage 2 offline Ed25519 witness receipt tests. Keys are ephemeral TEST ONLY."""
import base64
from datetime import datetime,timezone,timedelta
import hashlib
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from copy import deepcopy

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import appforge_phase3h_witness_receipt_v9 as w
import appforge_phase3h_recovery_model_v9 as h

NOW=datetime.now(timezone.utc).replace(microsecond=0)
DEVICE=hashlib.sha256(b'device-fixture-001').hexdigest()
OTHER=hashlib.sha256(b'device-fixture-002').hexdigest()


def openssl(*args):
    return subprocess.run(['openssl',*map(str,args)],check=True,
                          capture_output=True,timeout=20).stdout


def iso(d):
    return d.strftime('%Y-%m-%dT%H:%M:%SZ')


class WitnessReceiptTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp=tempfile.TemporaryDirectory(prefix='phase3h-stage2-fixtures-')
        cls.dir=Path(cls.temp.name)
        cls.keys={}
        for n in ('trusted','rogue'):
            private=cls.dir/(n+'.key')
            openssl('genpkey','-algorithm','ED25519','-out',private)
            der=openssl('pkey','-in',private,'-pubout','-outform','DER')
            cls.keys[n]=(private,der,w.crypto.spki(der))

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def setUp(self):
        self.i=0
        self.verifier=w.ReceiptVerifier(self.keys['trusted'][1],self.keys['trusted'][2])

    def challenge(self):
        self.i+=1
        return f'{self.i:032x}'

    def checkpoint(self,seq=1,root=1,tag='a'):
        return {'sha256':hashlib.sha256((tag+str(seq)).encode()).hexdigest(),
                'root_version':root,'release_sequence':seq}

    def payload(self,challenge,committed=None,pending=None,generation=0,epoch=1,
                device=DEVICE,issued=None,expires=None):
        return {'schema':1,'role':'appforge-v9-witness-receipt',
                'device_id':device,'challenge':challenge,'witness_epoch':epoch,
                'generation':generation,'committed':committed,'pending':pending,
                'issued_at':iso(issued if issued is not None else NOW-timedelta(seconds=5)),
                'expires_at':iso(expires if expires is not None else NOW+timedelta(seconds=60))}

    def receipt(self,payload,key='trusted',domain=w.DOMAIN):
        priv=self.keys[key][0]
        signed=domain+w.canonical(payload)
        src=self.dir/('message-'+key)
        src.write_bytes(signed)
        sig=openssl('pkeyutl','-sign','-inkey',priv,'-rawin','-in',src)
        assert len(sig)==64
        return w.canonical({'payload':payload,'keyid':self.keys[key][2],
                            'sig_b64':base64.b64encode(sig).decode('ascii')})

    def verify(self,payload,receipt=None,challenge=None):
        return self.verifier.verify(receipt if receipt is not None else self.receipt(payload),
            device_id=DEVICE,challenge=challenge if challenge is not None else payload['challenge'],now=NOW)

    def rejects(self,p,fragment=None,receipt=None):
        with self.assertRaises(ValueError) as ctx:
            self.verify(p,receipt=receipt)
        if fragment:
            self.assertIn(fragment,str(ctx.exception))

    def test_01_real_signed_empty_witness(self):
        p=self.payload(self.challenge())
        s=self.verify(p)
        self.assertIsNone(s.committed)
        self.assertEqual(s.generation,0)

    def test_02_real_signed_committed_checkpoint(self):
        p=self.payload(self.challenge(),self.checkpoint(),generation=1)
        self.assertEqual(self.verify(p).committed[2],1)

    def test_03_real_signed_pending_intent(self):
        old=self.checkpoint(); nxt=self.checkpoint(seq=2)
        p=self.payload(self.challenge(),old,{'prior':old,'next':nxt},generation=2)
        self.assertEqual(self.verify(p).pending['next']['release_sequence'],2)

    def test_04_bootstrap_pending_without_prior(self):
        nxt=self.checkpoint()
        p=self.payload(self.challenge(),None,{'prior':None,'next':nxt},generation=1)
        self.assertIsNotNone(self.verify(p).pending)

    def test_05_mutated_byte_signature_rejected(self):
        p=self.payload(self.challenge(),self.checkpoint(),generation=1)
        raw=self.receipt(p)
        doc=w.parse(raw);doc['payload']['generation']=2
        self.rejects(p,'signature verification failed',w.canonical(doc))

    def test_06_rogue_signer_rejected(self):
        p=self.payload(self.challenge())
        self.rejects(p,'untrusted witness signer',self.receipt(p,key='rogue'))

    def test_07_crypto_domain_separation(self):
        p=self.payload(self.challenge())
        self.rejects(p,'signature verification failed',self.receipt(p,domain=b'wrong\x00'))

    def test_08_wrong_pinned_public_key(self):
        with self.assertRaisesRegex(ValueError,'pin mismatch'):
            w.ReceiptVerifier(self.keys['rogue'][1],self.keys['trusted'][2])

    def test_09_wrong_device_rejected(self):
        p=self.payload(self.challenge(),device=OTHER)
        self.rejects(p,'device mismatch')

    def test_10_wrong_challenge_rejected(self):
        p=self.payload(self.challenge());self.i+=1
        with self.assertRaisesRegex(ValueError,'stale or cross-request'):
            self.verify(p,challenge=self.challenge())

    def test_11_replayed_same_challenge_rejected(self):
        p=self.payload(self.challenge());self.verify(p)
        self.rejects(p,'reused witness challenge')

    def test_12_expired_receipt_rejected(self):
        p=self.payload(self.challenge(),issued=NOW-timedelta(seconds=50),expires=NOW-timedelta(seconds=1))
        self.rejects(p,'expired')

    def test_13_future_receipt_rejected(self):
        p=self.payload(self.challenge(),issued=NOW+timedelta(seconds=11),expires=NOW+timedelta(seconds=90))
        self.rejects(p,'issued in future')

    def test_14_overlong_validity_rejected(self):
        p=self.payload(self.challenge(),issued=NOW-timedelta(seconds=1),expires=NOW+timedelta(seconds=121))
        self.rejects(p,'freshness window')

    def test_15_epoch_floor_rejects_old(self):
        self.verifier=w.ReceiptVerifier(self.keys['trusted'][1],self.keys['trusted'][2],epoch_floor=3)
        p=self.payload(self.challenge(),epoch=2)
        self.rejects(p,'epoch rollback')

    def test_16_generation_replay_rejected(self):
        p=self.payload(self.challenge(),self.checkpoint(2),generation=5);self.verify(p)
        p=self.payload(self.challenge(),self.checkpoint(1),generation=4)
        self.rejects(p,'generation rollback')

    def test_17_same_generation_equivocation_rejected(self):
        p=self.payload(self.challenge(),self.checkpoint(),generation=4);self.verify(p)
        p=self.payload(self.challenge(),self.checkpoint(tag='b'),generation=4)
        self.rejects(p,'equivocation')

    def test_18_same_generation_same_state_new_nonce_ok(self):
        p=self.payload(self.challenge(),self.checkpoint(),generation=4);self.verify(p)
        p=self.payload(self.challenge(),self.checkpoint(),generation=4)
        self.assertEqual(self.verify(p).generation,4)

    def test_19_higher_generation_committed_floor_rollback_rejected(self):
        self.verify(self.payload(self.challenge(),self.checkpoint(3),generation=3))
        p=self.payload(self.challenge(),self.checkpoint(2),generation=4)
        self.rejects(p,'floor rollback')

    def test_20_same_seq_different_hash_rejected(self):
        self.verify(self.payload(self.challenge(),self.checkpoint(2),generation=2))
        p=self.payload(self.challenge(),self.checkpoint(2,tag='b'),generation=3)
        self.rejects(p,'same release')

    def test_21_signed_pending_wrong_prior_rejected(self):
        old=self.checkpoint();wrong=self.checkpoint(tag='b')
        p=self.payload(self.challenge(),old,{'prior':wrong,'next':self.checkpoint(2)},generation=3)
        self.rejects(p,'pending prior mismatch')

    def test_22_pending_release_rollback_rejected(self):
        old=self.checkpoint(2)
        p=self.payload(self.challenge(),old,{'prior':old,'next':self.checkpoint(1)},generation=3)
        self.rejects(p,'pending rollback')

    def test_23_pending_root_skip_rejected(self):
        old=self.checkpoint(2)
        p=self.payload(self.challenge(),old,{'prior':old,'next':self.checkpoint(3,root=3)},generation=3)
        self.rejects(p,'root skip')

    def test_24_pending_root_decrease_rejected(self):
        old=self.checkpoint(2,root=2)
        p=self.payload(self.challenge(),old,{'prior':old,'next':self.checkpoint(3,root=1)},generation=3)
        self.rejects(p,'root skip')

    def test_25_bool_version_rejected(self):
        rec=self.checkpoint();rec['root_version']=True
        p=self.payload(self.challenge(),rec,generation=1)
        self.rejects(p,'root version')

    def test_26_duplicate_json_key_rejected(self):
        p=self.payload(self.challenge())
        raw=self.receipt(p)
        bad=raw.replace(b'"payload":',b'"payload":null,"payload":',1)
        self.rejects(p,'duplicate JSON key',bad)

    def test_27_noncanonical_json_rejected(self):
        p=self.payload(self.challenge())
        raw=self.receipt(p)
        self.rejects(p,'noncanonical',raw+b' ')

    def test_28_extra_payload_field_rejected(self):
        p=self.payload(self.challenge());p['r2_upload_allowed']=True
        self.rejects(p,'witness payload fields')

    def test_29_invalid_signature_base64_rejected(self):
        p=self.payload(self.challenge());obj=w.parse(self.receipt(p));obj['sig_b64']='!'*88
        self.rejects(p,'invalid witness signature base64',w.canonical(obj))

    def test_30_oversized_receipt_rejected(self):
        p=self.payload(self.challenge());raw=self.receipt(p)
        self.rejects(p,'receipt size',raw+b' '*w.MAX_RECEIPT)

    def test_31_unsigned_empty_witness_generation_inconsistent(self):
        p=self.payload(self.challenge(),generation=10)
        self.rejects(p,'empty witness')

    def test_32_witness_epoch_change_requires_repin(self):
        self.verify(self.payload(self.challenge(),self.checkpoint(),generation=3))
        p=self.payload(self.challenge(),self.checkpoint(2),generation=0,epoch=2)
        self.rejects(p,'independent re-pinning')

    def test_33_bridge_stage1_witness_records(self):
        prior=self.checkpoint();next_=self.checkpoint(2)
        old=h.Record(prior['sha256'],prior['root_version'],prior['release_sequence'])
        new=h.Record(next_['sha256'],next_['root_version'],next_['release_sequence'])
        mock=h.InMemoryWitness()
        mock.committed=old
        mock.prepare(old,new)
        p=self.payload(self.challenge(),prior,{'prior':prior,'next':next_},generation=2)
        snapshot=self.verify(p)
        self.assertEqual(snapshot.committed,(mock.committed.sha256,mock.committed.root_version,mock.committed.release_sequence))
        self.assertEqual(snapshot.pending['next']['sha256'],mock.pending.next.sha256)
        # A signed receipt is evidence about the mock, not production durability.

    def test_34_tampering_with_authenticated_pending_rejected(self):
        prior=self.checkpoint(); next_=self.checkpoint(2)
        p=self.payload(self.challenge(),prior,{'prior':prior,'next':next_},generation=2)
        obj=w.parse(self.receipt(p));obj['payload']['pending']['next']['sha256']='0'*64
        self.rejects(p,'signature verification failed',w.canonical(obj))

    def test_35_valid_committed_advance(self):
        a=self.checkpoint(1);b=self.checkpoint(2)
        self.verify(self.payload(self.challenge(),a,generation=1))
        self.verify(self.payload(self.challenge(),a,{'prior':a,'next':b},generation=2))
        self.assertEqual(self.verify(self.payload(self.challenge(),b,generation=3)).committed[2],2)

    def test_36_committed_state_deleted_rejected(self):
        self.verify(self.payload(self.challenge(),self.checkpoint(1),generation=2))
        p=self.payload(self.challenge(),None,generation=0)
        # Same epoch generation rollback blocks, even before deletion detection.
        self.rejects(p,'generation rollback')

    def test_37_source_verifier_contains_no_signing_or_network(self):
        src=Path(w.__file__).read_text()
        for forbidden in ('genpkey','-sign\'','requests.post','urllib.request','os.replace(','.write_bytes('):
            self.assertNotIn(forbidden,src)
        self.assertIn('REAL_WITNESS=NOT_DEPLOYED',src)


if __name__=='__main__':
    unittest.main()
