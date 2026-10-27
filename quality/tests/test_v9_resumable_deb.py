import hashlib
import fcntl
import importlib.util
import io
import os
from pathlib import Path
import tempfile
import unittest

TARGET = Path(__file__).resolve().parents[1] / 'tools' / 'appforge_resumable_deb_v9.py'
S = importlib.util.spec_from_file_location('phase3c_resume_under_test', TARGET)
M = importlib.util.module_from_spec(S)
S.loader.exec_module(M)

class FakeMirror:
    ORIGIN = 'https://snapshot.ubuntu.com/ubuntu/20261018T000000Z/'
    @staticmethod
    def object_path(store, digest):
        return Path(store)/'objects'/'sha256'/digest[:2]/digest

class Response:
    def __init__(self, data, status=200, headers=None):
        self.content = io.BytesIO(data)
        self.status = status
        self.headers = headers or {}
    def read(self, count):
        return self.content.read(count)
    def __enter__(self): return self
    def __exit__(self, *args): self.content.close()

class Opener:
    def __init__(self, response):
        self.response = response
        self.requests = []
    def open(self, req, timeout=20):
        self.requests.append(req)
        return self.response

class BatchTests(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.store = Path(tmp.name)/'store'
        self.store.mkdir()
        self.stage = M.check_directory(self.store / M.PARTS_NAME, create=True)
        self.data = b'!<arch>\n' + (b'hello-world' * 32)
        self.sha = hashlib.sha256(self.data).hexdigest()
        self.item = {'sha256': self.sha, 'size': len(self.data),
                     'url': FakeMirror.ORIGIN + 'pool/m/test/test_1.0_arm64.deb'}
        self.item['sources'] = [self.item['url']]
        self.dest = FakeMirror.object_path(self.store,self.sha)
        self.part = self.stage/(self.sha + '.part')

    def write_partial(self, payload):
        self.part.write_bytes(payload)
        self.part.chmod(0o600)

    def test_exclusive_batch_lock(self):
        fd = M.acquire_batch_lock(self.stage)
        try:
            with self.assertRaisesRegex(ValueError, 'another Phase 3C batch'):
                M.acquire_batch_lock(self.stage)
        finally:
            os.close(fd)
        fd = M.acquire_batch_lock(self.stage)
        os.close(fd)

    def test_oversized_partial_rejected(self):
        self.write_partial(self.data+b'oops')
        with self.assertRaisesRegex(ValueError, 'oversized retained partial'):
            M.import_one(self.item,self.store,self.stage,FakeMirror)
        self.assertFalse(self.dest.exists())

    def test_atomic_capability_no_replace(self):
        M.capability_probe(self.stage)
        a, b = self.stage/'a', self.stage/'b'
        a.write_bytes(b'new')
        b.write_bytes(b'old')
        with self.assertRaises(FileExistsError):
            M.no_replace_rename(a, b)
        self.assertEqual(b'old',b.read_bytes())
        self.assertEqual(b'new',a.read_bytes())

    def test_one_download_verified_atomically(self):
        p = Opener(Response(self.data, headers={'Content-Length':str(len(self.data))}))
        result,n = M.import_one(self.item,self.store,self.stage,FakeMirror,opener=p)
        self.assertEqual((result,n),('VERIFIED_IMPORTED', len(self.data)))
        self.assertEqual(self.data,self.dest.read_bytes())
        self.assertFalse(self.part.exists())
        self.assertIsNone(p.requests[0].get_header('Range'))

    def test_resume_range_206_then_publish(self):
        self.write_partial(self.data[:47])
        res = Response(self.data[47:],206, {'Content-Range': f'bytes 47-{len(self.data)-1}/{len(self.data)}',
                                                 'Content-Length':str(len(self.data)-47)})
        p = Opener(res)
        result,n = M.import_one(self.item,self.store,self.stage,FakeMirror,opener=p)
        self.assertEqual(result,'VERIFIED_IMPORTED')
        self.assertEqual(n,len(self.data)-47)
        self.assertEqual(p.requests[0].get_header('Range'),f'bytes=47-{len(self.data)-1}')
        self.assertEqual(self.data,self.dest.read_bytes())

    def test_ignored_range_200_is_rejected_preserves_partial(self):
        self.write_partial(self.data[:47])
        with self.assertRaisesRegex(ValueError, 'Range resume'):
            M.import_one(self.item,self.store,self.stage,FakeMirror,opener=Opener(Response(self.data)))
        self.assertEqual(self.part.read_bytes(),self.data[:47])
        self.assertFalse(self.dest.exists())

    def test_wrong_range_is_rejected(self):
        self.write_partial(self.data[:8])
        with self.assertRaisesRegex(ValueError,'Content-Range'):
            M.import_one(self.item,self.store,self.stage,FakeMirror,
                opener=Opener(Response(self.data[8:],206, {'Content-Range':'bytes 9-99/999'})))
        self.assertFalse(self.dest.exists())

    def test_hash_mismatch_is_rejected(self):
        buf = self.data[:-1]+b'!'
        with self.assertRaisesRegex(ValueError,'SHA256 mismatch'):
            M.import_one(self.item,self.store,self.stage,FakeMirror,opener=Opener(Response(buf)))
        self.assertFalse(self.dest.exists())
        self.assertEqual(len(buf),self.part.stat().st_size)

    def test_truncated_download_part_is_preserved_and_resumed(self):
        cut = 90
        with self.assertRaisesRegex(ValueError,'incomplete download'):
            M.import_one(self.item,self.store,self.stage,FakeMirror,opener=Opener(Response(self.data[:cut])))
        self.assertEqual(self.data[:cut],self.part.read_bytes())
        r = Response(self.data[cut:],206, {'Content-Range':f'bytes {cut}-{len(self.data)-1}/{len(self.data)}'})
        result,n = M.import_one(self.item,self.store,self.stage,FakeMirror,opener=Opener(r))
        self.assertEqual((result,n),('VERIFIED_IMPORTED',len(self.data)-cut))

    def test_response_too_long_rejected(self):
        with self.assertRaisesRegex(ValueError,'exceeded expected byte count'):
            M.import_one(self.item,self.store,self.stage,FakeMirror,opener=Opener(Response(self.data+b'extra')))
        self.assertFalse(self.dest.exists())

    def test_compressed_http_response_rejected(self):
        with self.assertRaisesRegex(ValueError,'Content-Encoding'):
            M.import_one(self.item,self.store,self.stage,FakeMirror,
                opener=Opener(Response(self.data,headers={'Content-Encoding':'gzip'})))
        self.assertFalse(self.dest.exists())

    def test_stage_symlink_rejected(self):
        dest = self.stage/'bad'
        dest.write_bytes(b'data')
        self.part.symlink_to(dest)
        with self.assertRaisesRegex(ValueError,'partial symlink'):
            M.import_one(self.item,self.store,self.stage,FakeMirror,opener=Opener(Response(self.data)))
        self.assertFalse(self.dest.exists())

    def test_existing_object_never_overwritten(self):
        self.dest.parent.mkdir(parents=True)
        self.dest.write_bytes(self.data)
        out,n = M.import_one(self.item,self.store,self.stage,FakeMirror)
        self.assertEqual((out,n),('VERIFIED_REUSED',0))
        self.assertEqual(self.data,self.dest.read_bytes())

    def test_corrupted_existing_object_fails_closed(self):
        self.dest.parent.mkdir(parents=True)
        self.dest.write_bytes(b'bad')
        with self.assertRaisesRegex(ValueError,'byte count'):
            M.import_one(self.item,self.store,self.stage,FakeMirror)

    def test_fully_downloaded_valid_partial_without_network(self):
        self.write_partial(self.data)
        result,n = M.import_one(self.item,self.store,self.stage,FakeMirror)
        self.assertEqual((result,n),('VERIFIED_IMPORTED',0))
        self.assertFalse(self.part.exists())

    def test_wrong_deb_magic_rejected(self):
        bad=b'not-a-deb'+self.data[9:]
        item = {**self.item,'sha256':hashlib.sha256(bad).hexdigest()}
        with self.assertRaisesRegex(ValueError, 'DEB archive'):
            M.import_one(item,self.store,self.stage,FakeMirror,opener=Opener(Response(bad)))
        self.assertFalse(FakeMirror.object_path(self.store,item['sha256']).exists())

if __name__=='__main__': unittest.main()
