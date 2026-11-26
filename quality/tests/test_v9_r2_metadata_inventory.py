"""Read-only inventory against genuine pinned Ubuntu signing fixtures."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

PATH = Path(__file__).resolve().parents[1] / 'tools/v9_r2_metadata_inventory.py'
spec = importlib.util.spec_from_file_location('metadata_inventory_test', PATH)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
ROOT = PATH.parents[2]
RESOURCES = ROOT / 'android-app/app/src/test/resources/apt-snapshot'
KEYRING = RESOURCES / 'ubuntu-archive-keyring.gpg'


class InventoryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rows = module.inventory(ROOT, RESOURCES, KEYRING)

    def test_genuine_signed_inventory_has_32_references_and_30_unique_objects(self):
        self.assertEqual(32, len(self.rows))
        self.assertEqual(30, len({row['sha256'] for row in self.rows}))
        self.assertEqual({'arm64', 'amd64'}, {row['architecture'] for row in self.rows})

    def test_native_architecture_selection_requires_exact_16_signed_indexes(self):
        rows = module.inventory(ROOT, RESOURCES, KEYRING, ('arm64',))
        self.assertEqual(16, len(rows))
        self.assertTrue(all('/binary-arm64/Packages' in row['index'] for row in rows))

    def test_foreign_architecture_is_rejected_before_inventory(self):
        with self.assertRaises(ValueError):
            module.inventory(ROOT, RESOURCES, KEYRING, ('i386',))

    def test_corrupt_cache_is_not_available_or_invented(self):
        with tempfile.TemporaryDirectory() as temp:
            rows = module.inspect_local(self.rows, temp)
            self.assertTrue(all(row['status'] == 'MISSING' for row in rows))
            sha = self.rows[0]['sha256']
            (Path(temp) / sha).write_bytes(b'corrupt')
            rows = module.inspect_local(self.rows, temp)
            self.assertTrue(all(row['status'] == 'FAIL' for row in rows if row['sha256'] == sha))

    def test_real_signed_empty_index_reuses_exact_empty_bytes(self):
        with tempfile.TemporaryDirectory() as temp:
            row = next(row for row in self.rows if row['bytes'] == 0)
            (Path(temp) / row['sha256']).write_bytes(b'')
            self.assertEqual('PASS', module.inspect_local([row], temp)[0]['status'])

    def test_symlinked_cache_is_rejected_without_modifying_target(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            protected = root / 'project.py'
            protected.write_text('preserve')
            (root / self.rows[0]['sha256']).symlink_to(protected)
            with self.assertRaises(ValueError):
                module.inspect_local(self.rows, root)
            self.assertEqual('preserve', protected.read_text())


if __name__ == '__main__':
    unittest.main()
