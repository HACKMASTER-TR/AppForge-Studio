"""Phase 3B: deterministic offline tests for signed Packages.xz acquisition."""
import hashlib
import importlib.util
import lzma
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SCRIPT = Path(__file__).resolve().parents[1] / 'tools' / 'appforge_signed_index_seed_v9.py'
SPEC = importlib.util.spec_from_file_location('v9_seed', SCRIPT)
v9 = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(v9)


def task(payload=b'test signed index\n'):
    zipped = lzma.compress(payload, format=lzma.FORMAT_XZ)
    return {'suite':'noble', 'component':'main', 'arch':'arm64',
            'index':'main/binary-arm64/Packages', 'sha256':hashlib.sha256(payload).hexdigest(),
            'size':len(payload), 'xz_sha256':hashlib.sha256(zipped).hexdigest(),
            'xz_size':len(zipped), 'url':'https://snapshot.ubuntu.com/ubuntu/20261018T000000Z/dists/noble/main/binary-arm64/Packages.xz'}, zipped


class IndexSeedTests(unittest.TestCase):
    def test_bounded_xz_roundtrip(self):
        x, zipped = task(b'Package: abc\n')
        self.assertEqual(v9.bounded_unxz(zipped,x['size']), b'Package: abc\n')

    def test_concatenated_signed_xz_streams(self):
        a, b = b'first\n' * 4000, b'second\n' * 3000
        content = lzma.compress(a) + lzma.compress(b)
        self.assertEqual(v9.bounded_unxz(content, len(a) + len(b)), a + b)

    def test_signed_xz_four_byte_stream_padding(self):
        a, b = b'alpha', b'beta'
        content = lzma.compress(a) + b'\x00' * 4 + lzma.compress(b) + b'\x00' * 8
        self.assertEqual(v9.bounded_unxz(content, len(a) + len(b)), a + b)

    def test_signed_xz_nonstream_tail_rejected(self):
        content = lzma.compress(b'a') + b'INJECTED'
        with self.assertRaisesRegex(ValueError, 'trailing'):
            v9.bounded_unxz(content, 1)

    def test_xz_invalid_padding_rejected(self):
        content = lzma.compress(b'a') + b'\x00' * 3
        with self.assertRaisesRegex(ValueError, 'padding'):
            v9.bounded_unxz(content, 1)

    def test_concatenated_xz_over_64kb_input(self):
        import random
        first = random.Random(20261027).randbytes(90000)
        second = b'tail\n' * 500
        content = lzma.compress(first) + lzma.compress(second)
        self.assertGreater(len(content), 65536)
        self.assertEqual(v9.bounded_unxz(content, len(first) + len(second)), first + second)

    def test_truncated_xz_rejected(self):
        x, zipped = task()
        with self.assertRaisesRegex(ValueError, 'XZ'):
            v9.bounded_unxz(zipped[:-2],x['size'])

    def test_lzma_bomb_rejected(self):
        zipped = lzma.compress(b'x'*100000)
        with self.assertRaisesRegex(ValueError, 'size exceeded'):
            v9.bounded_unxz(zipped, 10)

    def test_wrong_decompressed_size_rejected(self):
        x,zipped = task()
        with self.assertRaisesRegex(ValueError, 'length mismatch'):
            v9.bounded_unxz(zipped,x['size']+1)

    def test_only_signed_sha256_is_imported(self):
        x,_ = task()
        with tempfile.TemporaryDirectory() as d:
            self.assertEqual(v9.immutable_import(d,x,b'test signed index\n'),'IMPORTED')
            self.assertTrue(v9.existing(d,x))
            self.assertEqual(v9.immutable_import(d,x,b'test signed index\n'),'VERIFIED_REUSED')
            with self.assertRaisesRegex(ValueError, 'SHA256'):
                v9.immutable_import(d,x,b'fake signed index\n')

    def test_termux_exclusive_create_and_no_overwrite(self):
        x,_ = task()
        with tempfile.TemporaryDirectory() as d, patch.object(v9.os, 'link', side_effect=AssertionError('hardlink forbidden'), create=True):
            self.assertEqual(v9.immutable_import(d, x, b'test signed index\n'), 'IMPORTED')
            self.assertEqual(v9.immutable_import(d, x, b'test signed index\n'), 'VERIFIED_REUSED')
            src = Path(d) / 'candidate'
            dst = Path(d) / 'occupied'
            src.write_bytes(b'new')
            dst.write_bytes(b'original')
            with self.assertRaises(FileExistsError):
                v9.link_exclusive(src, dst)
            self.assertEqual(dst.read_bytes(), b'original')

    def test_zero_length_signed_index_is_allowed(self):
        x,_ = task(b'')
        with tempfile.TemporaryDirectory() as d:
            self.assertEqual(v9.immutable_import(d,x,b''),'IMPORTED')
            self.assertTrue(v9.existing(d,x))

    def test_plan_real_release_zero_and_unrelated_large_metadata(self):
        sha = 'a'*64
        meta = f'Origin: Ubuntu\nSuite: noble\nCodename: noble\nDate: Mon, 26 Oct 2026 00:00:00 UTC\nSHA256:\n {v9.EMPTY} 0 restricted/binary-arm64/Packages\n {sha} 9999999999999 Contents-arm64\n'.encode()
        fields, rows = v9.signed.read_release_fields(meta)
        self.assertEqual(rows['restricted/binary-arm64/Packages'],(v9.EMPTY,0))
        self.assertEqual(rows['Contents-arm64'],(sha,9999999999999))

    def test_plan_rejects_bad_zero_hash(self):
        rows={}
        for suite in v9.signed.SUITES:
            tab={}
            for component in v9.signed.COMPONENTS:
                prefix=f'{component}/binary-arm64/Packages'
                tab[prefix]=('a'*64,0)
                tab[prefix+'.xz']=('b'*64,32)
            rows[suite]=tab
        with patch.object(v9.signed,'release_tables',return_value=rows):
            with self.assertRaisesRegex(ValueError, 'zero length'):
                v9.plan(None,None,None,'arm64')

    def test_plan_contains_16_without_network(self):
        rows={}
        for suite in v9.signed.SUITES:
            tab={}
            for component in v9.signed.COMPONENTS:
                prefix=f'{component}/binary-arm64/Packages'
                tab[prefix]=(v9.EMPTY,0)
                tab[prefix+'.xz']=('b'*64,32)
            rows[suite]=tab
        with patch.object(v9.signed,'release_tables',return_value=rows):
            p=v9.plan(None,None,None,'arm64')
        self.assertEqual(len(p),16)
        self.assertTrue(all(x['url'].startswith('https://snapshot.ubuntu.com/ubuntu/20261018T000000Z/dists/') for x in p))

    def test_run_zero_indexes_no_network(self):
        rows={}
        for suite in v9.signed.SUITES:
            tab={}
            for component in v9.signed.COMPONENTS:
                prefix=f'{component}/binary-arm64/Packages'
                tab[prefix]=(v9.EMPTY,0)
                tab[prefix+'.xz']=('b'*64,32)
            rows[suite]=tab
        with tempfile.TemporaryDirectory() as d, patch.object(v9.signed,'release_tables',return_value=rows),patch.object(v9,'fetch',side_effect=AssertionError('unexpected network')):
            total,num=v9.run(None,d,None,'arm64',max_downloads=0)
        self.assertEqual((total,num),(16,0))

if __name__ == '__main__':
    unittest.main()
