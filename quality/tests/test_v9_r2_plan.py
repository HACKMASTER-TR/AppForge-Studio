"""V9 Phase 2: staging plan remains read-only and cannot publish."""
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

TOOL = Path(__file__).resolve().parents[1] / 'tools' / 'appforge_r2_plan_v9.py'
spec = importlib.util.spec_from_file_location('v9_r2_plan', TOOL)
v9 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v9)
mirror = v9.load_mirror()


class V9R2PlanTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.root = Path(tmp.name)
        self.repo = self.root / 'repo'
        self.store = self.root / 'cache'
        assets = self.repo / mirror.ASSETS
        assets.mkdir(parents=True)
        self.package = b'Example fake DEB test data'
        self.sha = hashlib.sha256(self.package).hexdigest()
        self.indexes = {suite: (b'Fake signed index fixture ' + suite.encode()) for suite in mirror.SUITES}
        self.index_hashes = {suite: hashlib.sha256(body).hexdigest() for suite, body in self.indexes.items()}
        (assets / 'apt-snapshot-profile.tsv').write_text(''.join(f'{s}\t{self.index_hashes[s]}\n' for s in mirror.SUITES))
        (assets / 'apt-module-packages-v1.tsv').write_text(
            f'PYTHON_ANDROID\tarm64\tdemo\t1.0\tarm64\t{self.sha}\t{len(self.package)}\t'
            f'{mirror.ORIGIN}pool/main/d/demo/demo_1.0_arm64.deb\n')

    def write_cached(self):
        path = mirror.object_path(self.store, self.sha)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(self.package)
        return path

    def test_zero_objects_never_publishes(self):
        p = v9.plan(self.repo, self.store)
        self.assertEqual(5, p['object_count_expected'])
        self.assertEqual(0, p['object_count_verified'])
        self.assertEqual(5, p['object_count_missing'])
        self.assertFalse(p['r2_upload_allowed'])
        self.assertFalse(p['public_distribution_allowed'])
        self.assertFalse(p['android_client_enabled'])

    def test_one_present_becomes_deterministic_dry_run(self):
        self.write_cached()
        p = v9.plan(self.repo, self.store)
        self.assertEqual(1, p['object_count_verified'])
        self.assertEqual(4, p['object_count_missing'])
        self.assertEqual(p['objects'][0]['key'], f'objects/sha256/{self.sha[:2]}/{self.sha}')
        self.assertEqual(v9.canonical_json(p), v9.canonical_json(v9.plan(self.repo, self.store)))
        self.assertFalse(p['r2_upload_allowed'])

    def test_corruption_rejected(self):
        file = self.write_cached()
        file.write_bytes(b'X' * len(self.package))
        with self.assertRaisesRegex(ValueError, 'SHA-256 mismatch'):
            v9.plan(self.repo, self.store)

    def test_symlinked_object_parent_rejected(self):
        folder = self.store / 'objects' / 'sha256'
        folder.mkdir(parents=True)
        link = folder / self.sha[:2]
        link.symlink_to(self.root, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, 'symlink'):
            v9.plan(self.repo, self.store)

    def test_other_bucket_rejected(self):
        with self.assertRaisesRegex(ValueError, 'unapproved R2 bucket'):
            v9.plan(self.repo, self.store, 'production')

    def test_tampered_catalog_rejected(self):
        path = self.repo / mirror.ASSETS / 'apt-module-packages-v1.tsv'
        path.write_text(path.read_text().replace('snapshot.ubuntu.com', 'invalid.example'))
        with self.assertRaisesRegex(ValueError, 'unpinned package source'):
            v9.plan(self.repo, self.store)

    def test_even_complete_fixture_cannot_go_live(self):
        self.write_cached()
        for suite, body in self.indexes.items():
            index_file = self.root / f'{suite}.InRelease'
            index_file.write_bytes(body)
            mirror.import_object(self.repo, self.store, self.index_hashes[suite], index_file)
        p = v9.plan(self.repo, self.store)
        self.assertEqual(5, p['object_count_verified'])
        self.assertEqual(0, p['object_count_missing'])
        self.assertFalse(p['r2_upload_allowed'])
        self.assertEqual('NOT_IMPLEMENTED', p['packages_index_closure'])
        self.assertEqual('NOT_IMPLEMENTED', p['appforge_ed25519_release_signature'])

    def test_no_network_or_credentials_code(self):
        s = TOOL.read_text()
        for bad in ('boto3', 'requests.post(', 'put_object(', 'subprocess.run(', 'CLOUDFLARE_API_TOKEN', 'APPFORGE_R2_SECRET'):
            self.assertNotIn(bad, s)


if __name__ == '__main__':
    unittest.main()
