"""V9 canonical offline-signature boundary, no production key/manifest publishing."""
import copy
import hashlib
import importlib.util
import io
from pathlib import Path
import subprocess
import tempfile
import unittest

TOOL = Path(__file__).resolve().parents[1] / 'tools/appforge_v9_release_bundle.py'
spec = importlib.util.spec_from_file_location('v9_candidate_bundle_under_test', TOOL)
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)


def fixture():
    signed_sizes = (255850, 126125, 126127, 126173)
    rows = []
    for i in range(214):
        digest = f'{i+1:064x}'
        rows.append({'key': mod.object_key(digest), 'sha256': digest,
                     'size': 1_700_000 if i < 213 else 4_087_036, 'kind': 'deb'})
    for (name, digest), size in zip(mod.EXPECTED.items(), signed_sizes):
        rows.append({'key': mod.object_key(digest), 'sha256': digest,
                     'size': size, 'kind': 'InRelease'})
    return sorted(rows, key=lambda x: x['key'])


class Response(io.BytesIO):
    status = 200

    def __init__(self, body, url, override=None):
        super().__init__(body)
        self.url = url
        self.headers = {'Content-Length': str(len(body))} if override is None else override

    def geturl(self):
        return self.url


class Opener:
    def __init__(self, body, url, headers=None):
        self.body, self.url, self.headers = body, url, headers

    def open(self, req, timeout):
        return Response(self.body, self.url, self.headers)


class BundleTest(unittest.TestCase):
    def setUp(self):
        self.rows = fixture()
        self.doc = mod.make_manifest(self.rows)
        self.raw = mod.canonical(self.doc)

    def reject(self, change):
        doc = copy.deepcopy(self.doc)
        change(doc)
        with self.assertRaises(mod.Blocked):
            mod.validate_manifest_bytes(mod.canonical(doc))

    def test_01_exact_v9_candidate_schema(self):
        self.assertEqual(218, len(mod.validate_manifest_bytes(self.raw)['objects']))

    def test_02_expected_total_bytes(self):
        self.assertEqual(mod.EXPECTED_TOTAL_BYTES, sum(x['size'] for x in self.rows))

    def test_03_unknown_schema_field(self):
        self.reject(lambda d: d.update(extra=True))

    def test_04_wrong_catalog_pin(self):
        self.reject(lambda d: d.update(catalog_sha256='0' * 64))

    def test_05_wrong_origin_commit(self):
        self.reject(lambda d: d.update(origin_repo_commit='0' * 40))

    def test_06_wrong_release_sequence(self):
        self.reject(lambda d: d.update(sequence=7))

    def test_07_boolean_sequence_not_number(self):
        self.reject(lambda d: d.update(sequence=True))

    def test_08_expired_release(self):
        self.reject(lambda d: d.update(metadata_expired=True))

    def test_09_unsigned_remote_not_trusted(self):
        self.reject(lambda d: d.update(remote_verified={'objects':218, 'sha256_verified':False}))

    def test_10_wrong_ubuntu_fingerprint(self):
        self.reject(lambda d: d['ubuntu_inrelease'][0].update(signer='0'*40))

    def test_11_object_hash_replacement(self):
        self.reject(lambda d: d['objects'][0].update(sha256='0'*64))

    def test_12_object_duplicate(self):
        self.reject(lambda d: d['objects'][1].update(d['objects'][0]))

    def test_13_object_size_drift(self):
        self.reject(lambda d: d['objects'][0].update(size=d['objects'][0]['size']+1))

    def test_14_object_order_drift(self):
        self.reject(lambda d: d['objects'].reverse())

    def test_15_duplicate_json_keys(self):
        bad = b'{"schema":1,"schema":1}\n'
        with self.assertRaises(mod.Blocked):
            mod.validate_manifest_bytes(bad)

    def test_16_noncanonical_json(self):
        with self.assertRaises(mod.Blocked):
            mod.validate_manifest_bytes(b' ' + self.raw)

    def test_17_raw_forged_signature_refused(self):
        with self.assertRaises(mod.Blocked):
            mod.verify_bundle(self.raw, b'\x00' * 64)

    def test_18_signature_uses_domain_separation(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder)
            private = path / 'key.pem'
            pub = path / 'pub.der'
            data = path / 'data'
            sig = path / 'sig'
            subprocess.run(['openssl','genpkey','-algorithm','Ed25519','-out',str(private)],check=True,capture_output=True)
            subprocess.run(['openssl','pkey','-in',str(private),'-pubout','-outform','DER','-out',str(pub)],check=True,capture_output=True)
            data.write_bytes(mod.DOMAIN+self.raw)
            subprocess.run(['openssl','pkeyutl','-sign','-inkey',str(private),'-rawin','-in',str(data),'-out',str(sig)],check=True,capture_output=True)
            mod.verify_signature(self.raw,sig.read_bytes(),pub.read_bytes())
            self.assertRaises(mod.Blocked, mod.verify_bundle,self.raw,sig.read_bytes())
            data.write_bytes(self.raw)
            subprocess.run(['openssl','pkeyutl','-sign','-inkey',str(private),'-rawin','-in',str(data),'-out',str(sig)],check=True,capture_output=True)
            self.assertRaises(mod.Blocked,mod.verify_signature,self.raw,sig.read_bytes(),pub.read_bytes())

    def test_19_no_overwrite(self):
        with tempfile.TemporaryDirectory() as folder:
            p=Path(folder)/'manifest.json'
            mod.create_once(p,b'first')
            self.assertRaises(mod.Blocked,mod.create_once,p,b'second')
            self.assertEqual(b'first',p.read_bytes())

    def test_20_remote_full_body_sha256_verified(self):
        b=b'hello real immutable object'
        digest=hashlib.sha256(b).hexdigest()
        item={'sha256':digest,'key':mod.object_key(digest),'size':len(b),'kind':'deb'}
        url=mod.BASE_URL+'/v9/'+item['key']
        self.assertEqual(len(b),mod.check_remote_object(item,Opener(b,url)))

    def test_21_remote_wrong_digest_blocked(self):
        b=b'tampered'
        digest='0'*64
        item={'sha256':digest,'key':mod.object_key(digest),'size':len(b),'kind':'deb'}
        url=mod.BASE_URL+'/v9/'+item['key']
        self.assertRaises(mod.Blocked,mod.check_remote_object,item,Opener(b,url))

    def test_22_remote_missing_length_blocked(self):
        b=b'payload'
        digest=hashlib.sha256(b).hexdigest()
        item={'sha256':digest,'key':mod.object_key(digest),'size':len(b),'kind':'deb'}
        url=mod.BASE_URL+'/v9/'+item['key']
        self.assertRaises(mod.Blocked,mod.check_remote_object,item,Opener(b,url,{}))

    def test_23_redirect_url_blocked(self):
        b=b'payload'
        digest=hashlib.sha256(b).hexdigest()
        item={'sha256':digest,'key':mod.object_key(digest),'size':len(b),'kind':'deb'}
        self.assertRaises(mod.Blocked,mod.check_remote_object,item,Opener(b,'https://attacker.example.com/'))

    def test_24_raw_object_key_rejects_path_traversal(self):
        self.assertRaises(mod.Blocked,mod.object_key,'../../etc/passwd')


if __name__=='__main__':
    unittest.main()
