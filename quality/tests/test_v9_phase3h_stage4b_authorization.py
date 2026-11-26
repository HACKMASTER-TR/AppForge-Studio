"""Offline Stage 4B TEST authorization. Ephemeral Ed25519 keys, no server."""
import base64
from contextlib import closing
from datetime import datetime, timezone, timedelta
import hashlib
import os
from pathlib import Path
import sqlite3
import subprocess
import sys
import tempfile
import threading
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]/'tools'))
import appforge_phase3h_stage4b_authorized_witness_v9 as a
import appforge_phase3h_stage4_witness_store_v9 as s

NOW = datetime.now(timezone.utc).replace(microsecond=0)
DEVICE = hashlib.sha256(b'phase4b-device-one').hexdigest()
OTHER = hashlib.sha256(b'phase4b-device-two').hexdigest()
UNREGISTERED = hashlib.sha256(b'phase4b-not-enrolled').hexdigest()


def openssl(*args):
    return subprocess.run(['openssl', *map(str,args)], check=True,
                          capture_output=True, timeout=20).stdout


def iso(now):
    return now.strftime('%Y-%m-%dT%H:%M:%SZ')


class SignedAuthorizationStage4BTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.h = tempfile.TemporaryDirectory(prefix='appforge-phase4b-keys-')
        cls.keydir = Path(cls.h.name)
        cls.keys = {}
        for name in ('trusted','rogue'):
            keypath = cls.keydir/(name+'.key')
            openssl('genpkey','-algorithm','ED25519','-out',keypath)
            der = openssl('pkey','-in',keypath,'-pubout','-outform','DER')
            cls.keys[name] = (keypath, der, a.ed25519.spki(der))

    @classmethod
    def tearDownClass(cls):
        cls.h.cleanup()

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='appforge-phase4b-store-')
        self.addCleanup(self.temp.cleanup)
        self.dir = Path(self.temp.name)
        self.dir.chmod(0o700)
        self.index = 0
        self.svc = self.open()

    def open(self, allow=(DEVICE, OTHER)):
        return a.OfflineTestAuthorizedWitness(
            self.dir, authorized_der=self.keys['trusted'][1],
            authorized_pin=self.keys['trusted'][2],
            allowed_devices=allow, test_mode=True)

    def checkpoint(self, seq=1, root=1, tag='original'):
        return {'sha256':hashlib.sha256(f'{tag}-{seq}'.encode()).hexdigest(),
                'root_version':root, 'release_sequence':seq}

    def payload(self, prior=None, nxt=None, *, device=DEVICE, permit=None,
                evidence=None, source=None, issued=None, expires=None):
        self.index += 1
        return {'schema':1, 'role':a.ROLE, 'source':source if source is not None else a.SOURCE,
                'device_id':device,
                'permit_id':permit if permit is not None else f'{self.index:032x}',
                'prior':prior, 'next':nxt if nxt is not None else self.checkpoint(),
                'evidence_sha256':evidence if evidence is not None else a.policy.EVIDENCE,
                'issued_at':iso(issued if issued is not None else NOW-timedelta(seconds=4)),
                'expires_at':iso(expires if expires is not None else NOW+timedelta(seconds=40))}

    def sign(self, payload, *, signer='trusted', domain=None):
        key, der, pin = self.keys[signer]
        raw = (domain if domain is not None else a.DOMAIN)+a.canonical(payload)
        src = self.keydir/('stage4b-signed-'+signer)
        src.write_bytes(raw)
        signature=openssl('pkeyutl','-sign','-inkey',key,'-rawin','-in',src)
        self.assertEqual(len(signature), 64)
        return a.canonical({'payload':payload,'keyid':pin,
                            'sig_b64':base64.b64encode(signature).decode('ascii')})

    def prepare(self, p, *, device=DEVICE, service=None, signed=None):
        target = service or self.svc
        return target.prepare_signed_test(device,p['prior'],p['next'],
                                          signed if signed is not None else self.sign(p), now=NOW)

    def reject(self,p, fragment, **kwargs):
        with self.assertRaisesRegex(ValueError, fragment):
            self.prepare(p, **kwargs)

    def bootstrap(self):
        p=self.payload()
        token=self.prepare(p)
        self.svc.finalize(DEVICE,token)
        return p['next']

    def test_01_real_signed_bootstrap(self):
        p=self.payload()
        token=self.prepare(p)
        snap=self.svc.snapshot(DEVICE)
        self.assertEqual(snap.pending['token'],token)
        self.assertIsNone(snap.committed)

    def test_02_real_signed_advance(self):
        old=self.bootstrap()
        p=self.payload(old,self.checkpoint(2))
        token=self.prepare(p)
        self.assertEqual(self.svc.finalize(DEVICE,token).committed['release_sequence'],2)

    def test_03_root_rotation(self):
        old=self.bootstrap()
        p=self.payload(old,self.checkpoint(2,root=2))
        self.svc.finalize(DEVICE,self.prepare(p))
        self.assertEqual(self.svc.snapshot(DEVICE).committed['root_version'],2)

    def test_04_mutated_signed_payload(self):
        p=self.payload(); raw=self.sign(p)
        obj=a.parse(raw);obj['payload']['next']['release_sequence']=7
        self.reject(p,'signature verification failed',signed=a.canonical(obj))

    def test_05_rogue_signer(self):
        p=self.payload(); self.reject(p,'untrusted authorizer signer',signed=self.sign(p,signer='rogue'))

    def test_06_wrong_signature_domain(self):
        p=self.payload();self.reject(p,'signature verification failed',signed=self.sign(p,domain=b'NOT-THIS-PROTOCOL\x00'))

    def test_07_bad_constructor_pin(self):
        with self.assertRaisesRegex(ValueError,'pin mismatch'):
            a.OfflineTestAuthorizedWitness(self.dir, authorized_der=self.keys['rogue'][1],
                authorized_pin=self.keys['trusted'][2],allowed_devices=[DEVICE],test_mode=True)

    def test_08_noncanonical_envelope(self):
        p=self.payload(); self.reject(p,'noncanonical',signed=self.sign(p)+b' ')

    def test_09_duplicate_envelope_key(self):
        p=self.payload();r=self.sign(p)
        self.reject(p,'duplicate authorization key',signed=r.replace(b'"payload":',b'"payload":null,"payload":',1))

    def test_10_extra_signed_field_rejected(self):
        p=self.payload();p['publish_allowed']=True
        self.reject(p,'invalid authorization payload fields')

    def test_11_wrong_device_in_signed_claim(self):
        p=self.payload(device=OTHER);self.reject(p,'authorization device mismatch')

    def test_12_unenrolled_device(self):
        p=self.payload(device=UNREGISTERED)
        self.reject(p,'device not independently enrolled',device=UNREGISTERED)

    def test_13_mismatched_prior(self):
        p=self.payload(prior=self.checkpoint())
        with self.assertRaisesRegex(ValueError,'authorization transition mismatch'):
            self.svc.prepare_signed_test(DEVICE,None,p['next'],self.sign(p),now=NOW)

    def test_14_mismatched_successor(self):
        p=self.payload()
        with self.assertRaisesRegex(ValueError,'authorization transition mismatch'):
            self.svc.prepare_signed_test(DEVICE,None,self.checkpoint(2),self.sign(p),now=NOW)

    def test_15_wrong_evidence_pin(self):
        p=self.payload(evidence='0'*64); self.reject(p,'evidence pin mismatch')

    def test_16_false_source_stage(self):
        p=self.payload(source='ARBITRARY_CLIENT_ASSERTION');self.reject(p,'unsupported authorization source')

    def test_17_expired_authorization(self):
        p=self.payload(issued=NOW-timedelta(seconds=60), expires=NOW-timedelta(seconds=1))
        self.reject(p,'authorization expired')

    def test_18_future_authorization(self):
        p=self.payload(issued=NOW+timedelta(seconds=12), expires=NOW+timedelta(seconds=30))
        self.reject(p,'issued in future')

    def test_19_oversized_ttl(self):
        p=self.payload(issued=NOW-timedelta(seconds=1), expires=NOW+timedelta(seconds=95))
        self.reject(p,'TTL exceeded')

    def test_20_replay_after_finalize(self):
        p=self.payload();raw=self.sign(p)
        token=self.prepare(p,signed=raw)
        self.svc.finalize(DEVICE,token)
        # To test actual permit reuse independent of CAS, changing state
        # cannot be allowed; the consumed permit table is persisted.
        with closing(sqlite3.connect(self.svc.path)) as db:
            self.assertEqual(db.execute('SELECT count(*) FROM used_test_permits').fetchone()[0],1)
        self.reject(p,'compare-and-swap conflict',signed=raw)

    def test_21_replay_after_abort(self):
        p=self.payload();raw=self.sign(p)
        token=self.prepare(p,signed=raw)
        self.svc.abort(DEVICE,token)
        self.reject(p,'signed authorization permit replay',signed=raw)

    def test_22_replay_after_reopen(self):
        p=self.payload();raw=self.sign(p)
        self.svc.abort(DEVICE,self.prepare(p,signed=raw))
        self.reject(p,'signed authorization permit replay',service=self.open(),signed=raw)

    def test_23_independent_transaction_token(self):
        p=self.payload(); token=self.prepare(p)
        self.assertNotEqual(token,p['permit_id'])
        self.assertEqual(len(token),32)

    def test_24_compare_and_swap_conflict(self):
        p=self.payload(self.checkpoint(1),self.checkpoint(2))
        self.reject(p,'compare-and-swap conflict')

    def test_25_pending_transaction_blocks(self):
        p=self.payload();token=self.prepare(p)
        p2=self.payload()
        self.reject(p2,'pending transaction exists')
        self.svc.abort(DEVICE,token)

    def test_26_release_rollback(self):
        old=self.bootstrap()
        p=self.payload(old,self.checkpoint(seq=1,tag='same-seq'))
        self.reject(p,'release rollback')

    def test_27_root_skip(self):
        old=self.bootstrap();p=self.payload(old,self.checkpoint(2,root=3))
        self.reject(p,'root rollback or skip')

    def test_28_bootstrap_requires_root_one(self):
        p=self.payload(nxt=self.checkpoint(root=2))
        self.reject(p,'bootstrap root invalid')

    def test_29_unsigned_payload_rejected(self):
        p=self.payload(); self.reject(p,'invalid authorization envelope fields',signed=a.canonical(p))

    def test_30_invalid_signature_base64(self):
        p=self.payload();doc=a.parse(self.sign(p));doc['sig_b64']='!'*88
        self.reject(p,'signature base64',signed=a.canonical(doc))

    def test_31_invalid_permit_id(self):
        p=self.payload(permit='!bad');self.reject(p,'invalid signed permit identity')

    def test_32_oversized_authorization(self):
        p=self.payload();self.reject(p,'authorization length',signed=self.sign(p)+b' '*a.MAX_AUTH)

    def test_33_boolean_schema(self):
        p=self.payload();p['schema']=True
        self.reject(p,'role/schema mismatch')

    def test_34_concurrent_same_permit_one_wins(self):
        p=self.payload();raw=self.sign(p)
        start=threading.Barrier(3)
        results=[]
        services=[self.open(),self.open()]
        def worker(svc):
            start.wait()
            try:
                results.append(('pass',svc.prepare_signed_test(DEVICE,None,p['next'],raw,now=NOW)))
            except ValueError as e:
                results.append(('blocked',str(e)))
        threads=[threading.Thread(target=worker,args=(svc,)) for svc in services]
        for t in threads:t.start()
        start.wait()
        for t in threads:t.join(timeout=10)
        self.assertTrue(all(not t.is_alive() for t in threads))
        self.assertEqual([r[0] for r in results].count('pass'),1)
        self.assertEqual([r[0] for r in results].count('blocked'),1)

    def test_35_failed_pending_does_not_consume_permit(self):
        first=self.payload();token=self.prepare(first)
        next_=self.payload()
        raw=self.sign(next_)
        self.reject(next_,'pending transaction exists',signed=raw)
        self.svc.abort(DEVICE,token)
        token2=self.prepare(next_,signed=raw)
        self.assertIsInstance(token2,str)

    def test_36_trusted_clock_required(self):
        p=self.payload()
        with self.assertRaisesRegex(ValueError,'trusted clock'):
            self.svc.prepare_signed_test(DEVICE,None,p['next'],self.sign(p),now=datetime.now())

    def test_37_crypto_failure_no_side_effect(self):
        p=self.payload();raw=self.sign(p)
        obj=a.parse(raw);obj['payload']['permit_id']='a'*32
        self.reject(p,'signature verification failed',signed=a.canonical(obj))
        self.assertEqual(self.svc.snapshot(DEVICE).generation,0)
        with closing(sqlite3.connect(self.svc.path)) as db:
            self.assertEqual(db.execute('SELECT count(*) FROM used_test_permits').fetchone()[0],0)

    def test_38_local_db_rollback_still_possible(self):
        p=self.payload();self.svc.abort(DEVICE,self.prepare(p))
        raw=(self.dir/s.DB_NAME).read_bytes()
        p2=self.payload();self.svc.abort(DEVICE,self.prepare(p2))
        self.assertEqual(self.svc.snapshot(DEVICE).generation,4)
        (self.dir/s.DB_NAME).write_bytes(raw)
        self.assertEqual(self.open().snapshot(DEVICE).generation,2)
        # Counterexample demonstrates why this cannot be called secure anti-rollback.

    def test_39_stage4a_inherited_bypass_is_explicit_limitation(self):
        # The inherited test-only Stage4A callback bypass intentionally remains,
        # proving this class must NOT be exposed as an authoritative service.
        self.assertTrue(callable(self.svc.prepare_model))
        self.assertIn('NOT an independent trust anchor',s.__doc__)

    def test_40_cross_device_single_permit_never_reused(self):
        p=self.payload();self.svc.abort(DEVICE,self.prepare(p))
        q=self.payload(device=OTHER,permit=p['permit_id'])
        self.reject(q,'signed authorization permit replay',device=OTHER)

    def test_41_empty_enrollment_denied(self):
        with self.assertRaisesRegex(ValueError,'enrollment list'):
            self.open(allow=())

    def test_42_explicit_test_mode_required(self):
        with self.assertRaisesRegex(ValueError,'test-only'):
            a.OfflineTestAuthorizedWitness(self.dir,authorized_der=self.keys['trusted'][1],
                authorized_pin=self.keys['trusted'][2],allowed_devices=[DEVICE])

    def test_43_source_contains_no_network_or_signing(self):
        src=Path(a.__file__).read_text()
        for forbidden in ('requests.post','urllib.request','genpkey','pkeyutl\',\'-sign', 'socket.socket', 'http.server'):
            self.assertNotIn(forbidden,src)
        self.assertIn('EXTERNAL_WITNESS=NOT_DEPLOYED',src)


if __name__=='__main__':
    unittest.main()
