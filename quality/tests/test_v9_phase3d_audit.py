"""Offline adversarial tests for Phase 3D audit manifest; no R2 calls."""
import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest
from types import SimpleNamespace
from unittest.mock import patch

SCRIPT = Path(__file__).resolve().parents[1] / 'tools' / 'appforge_phase3d_audit_v9.py'
spec = importlib.util.spec_from_file_location('phase3d_audit', SCRIPT)
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)

H = hashlib.sha256(b'fixture').hexdigest()

class AuditTests(unittest.TestCase):
    def setUp(self):
        self.t = tempfile.TemporaryDirectory()
        self.addCleanup(self.t.cleanup)
        self.root = Path(self.t.name)

    def test_canonical_json_stable_order(self):
        self.assertEqual(audit.canonical({'z':1,'a':2}), audit.canonical({'a':2,'z':1}))
        self.assertTrue(audit.canonical({'a': 2}).endswith(b'\n'))

    def test_canonical_ascii_only(self):
        self.assertIn(b'\\u00fc', audit.canonical({'t':'ü'}))

    def test_cas_entry_pinned_key(self):
        items = {}
        audit.add_cas_entry(items,H,7,'deb')
        self.assertEqual(items[H]['key'],f'objects/sha256/{H[:2]}/{H}')
        self.assertEqual(items[H]['bytes'],7)

    def test_duplicate_digest_role_is_deduplicated(self):
        items = {}
        audit.add_cas_entry(items,H,7,'Packages')
        audit.add_cas_entry(items,H,7,'Packages')
        audit.add_cas_entry(items,H,7,'InRelease')
        self.assertEqual(len(items),1)
        self.assertEqual(items[H]['roles'],['InRelease','Packages'])

    def test_same_digest_different_length_rejected(self):
        items = {}
        audit.add_cas_entry(items,H,7,'deb')
        with self.assertRaisesRegex(ValueError,'conflicting'):
            audit.add_cas_entry(items,H,8,'deb')

    def test_wrong_digest_and_kind_rejected(self):
        with self.assertRaises(ValueError):
            audit.add_cas_entry({},'f'*63,2,'deb')
        with self.assertRaises(ValueError):
            audit.add_cas_entry({},H,2,'untrusted')

    def test_negative_and_non_int_size_rejected(self):
        with self.assertRaises(ValueError):
            audit.add_cas_entry({},H,-1,'deb')
        with self.assertRaises(ValueError):
            audit.add_cas_entry({},H,'1','deb')
        with self.assertRaises(ValueError):
            audit.add_cas_entry({},H,1 << 32,'deb')

    def test_written_manifest_reuse_without_mutation(self):
        p=self.root/'audit.json'
        self.assertEqual(audit.write_exclusive_or_identical(p,b'original'), 'CREATED')
        self.assertEqual(audit.write_exclusive_or_identical(p,b'original'), 'VERIFIED_REUSED')
        self.assertEqual(p.read_bytes(),b'original')

    def test_existing_different_manifest_not_overwritten(self):
        p=self.root/'audit.json'
        p.write_bytes(b'original')
        with self.assertRaisesRegex(ValueError,'differs'):
            audit.write_exclusive_or_identical(p,b'untrusted')
        self.assertEqual(p.read_bytes(),b'original')

    def test_manifest_symlink_refused(self):
        target=self.root/'target'
        target.write_bytes(b'secret')
        (self.root/'audit.json').symlink_to(target)
        with self.assertRaisesRegex(ValueError,'symlink'):
            audit.write_exclusive_or_identical(self.root/'audit.json',b'new')
        self.assertEqual(target.read_bytes(),b'secret')

    def test_normal_sha_object_verified(self):
        root=self.root/'mirror'
        obj=root/'objects'/'sha256'/H[:2]/H
        obj.parent.mkdir(parents=True)
        obj.write_bytes(b'fixture')
        self.assertEqual(audit.check_object_path(root,H),obj)
        class M:
            @staticmethod
            def sha_file(p):
                return hashlib.sha256(p.read_bytes()).hexdigest()
        self.assertEqual(audit.verify_storage(root,[{'sha256':H,'bytes':7}],M),7)

    def test_corrupted_sha_object_rejected(self):
        root=self.root/'mirror'
        obj=root/'objects'/'sha256'/H[:2]/H
        obj.parent.mkdir(parents=True)
        obj.write_bytes(b'bad data')
        class M:
            @staticmethod
            def sha_file(p):
                return hashlib.sha256(p.read_bytes()).hexdigest()
        with self.assertRaisesRegex(ValueError,'SHA-256 mismatch'):
            audit.verify_storage(root,[{'sha256':H,'bytes':8}],M)

    def test_object_symlink_ancestor_rejected(self):
        root=self.root/'mirror'
        other=self.root/'other'
        other.mkdir()
        root.mkdir()
        (root/'objects').symlink_to(other, target_is_directory=True)
        with self.assertRaisesRegex(ValueError,'symlink'):
            audit.check_object_path(root,H)

    def test_hardlinked_object_rejected(self):
        root=self.root/'mirror'
        obj=root/'objects'/'sha256'/H[:2]/H
        obj.parent.mkdir(parents=True)
        obj.write_bytes(b'fixture')
        # Android/Termux may prohibit hardlink(), so simulate the actual
        # security predicate (st_nlink > 1) without weakening the assertion.
        original_stat = Path.stat
        def pretend_hardlinked(path, *args, **kwargs):
            info = original_stat(path, *args, **kwargs)
            if path == obj:
                return SimpleNamespace(st_mode=info.st_mode,
                                       st_nlink=2, st_size=info.st_size)
            return info
        with patch.object(Path, 'stat', pretend_hardlinked):
            with self.assertRaisesRegex(ValueError, 'hardlinked'):
                audit.check_object_path(root, H)

    def test_no_publication_code(self):
        data = SCRIPT.read_text()
        for forbidden in ('boto3','put_object(', 'requests.post(', 'aws s3', 'r2.put('):
            self.assertNotIn(forbidden, data)
        self.assertIn("'r2_upload_allowed': False", data)
        self.assertIn("'appforge_ed25519_release_signature': 'NOT_PRESENT'",data)

if __name__ == '__main__':
    unittest.main()
