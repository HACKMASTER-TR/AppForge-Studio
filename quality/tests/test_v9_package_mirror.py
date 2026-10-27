"""Offline adversarial checks for the V9 mirror before any R2 upload."""
import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest

FILE = Path(__file__).resolve().parents[1] / "tools" / "appforge_package_mirror_v9.py"
spec = importlib.util.spec_from_file_location("v9", FILE)
v9 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v9)

class MirrorV9Test(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.repo = Path(self.tmp.name) / "repo"
        self.store = Path(self.tmp.name) / "cache"
        self.asset = self.repo / v9.ASSETS
        self.asset.mkdir(parents=True)
        self.index_body = b"-----BEGIN PGP SIGNED MESSAGE-----\nfixture-not-real-signed\n"
        self.body = b"test-pkg-bytes"
        self.pkgsha = hashlib.sha256(self.body).hexdigest()
        self.indexsha = hashlib.sha256(self.index_body).hexdigest()
        (self.asset / "apt-snapshot-profile.tsv").write_text("\n".join(f"{s}\t{self.indexsha}" for s in v9.SUITES) + "\n")
        (self.asset / "apt-module-packages-v1.tsv").write_text(
            f"RUST\tarm64\tdemo\t1.0\tarm64\t{self.pkgsha}\t{len(self.body)}\t{v9.ORIGIN}pool/main/d/demo/demo_1.0_arm64.deb\n")
    def test_pinned_catalog_and_pending_never_ready(self):
        spec, status = v9.inspect_store(self.repo, self.store)
        self.assertEqual(v9.SNAPSHOT, spec["snapshot"])
        self.assertEqual(5, status["total"])
        self.assertEqual(5, status["missing"])
        self.assertFalse(status["distribution_ready"])
        self.assertFalse(status["apt_ready"])
    def test_import_requires_pinned_sha_and_exact_byte_count(self):
        p = Path(self.tmp.name) / "package.deb"
        p.write_bytes(self.body)
        self.assertEqual("VERIFIED_IMPORTED", v9.import_object(self.repo, self.store, self.pkgsha, p))
        self.assertEqual("VERIFIED_REUSED", v9.import_object(self.repo, self.store, self.pkgsha, p))
        _, status = v9.inspect_store(self.repo, self.store)
        self.assertEqual(1, status["present"])
        self.assertEqual(4, status["missing"])
    def test_tampered_archive_rejected_and_existing_store_not_overwritten(self):
        p = Path(self.tmp.name) / "package.deb"
        p.write_bytes(b"fake")
        with self.assertRaisesRegex(ValueError, "length mismatch"):
            v9.import_object(self.repo, self.store, self.pkgsha, p)
        self.assertFalse(v9.object_path(self.store, self.pkgsha).exists())
    def test_corrupted_cache_causes_fail_closed(self):
        dest = v9.object_path(self.store, self.pkgsha)
        dest.parent.mkdir(parents=True)
        dest.write_bytes(b"X" * len(self.body))
        with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
            v9.inspect_store(self.repo, self.store)
    def test_optional_dependency_columns_from_real_catalog(self):
        file = self.asset / "apt-module-packages-v1.tsv"
        file.write_text(file.read_text().rstrip("\n") + "\tdepends\tpredepends\n")
        self.assertEqual(5, len(v9.manifest(self.repo)["objects"]))
    def test_untrusted_source_url_rejected(self):
        file = self.asset / "apt-module-packages-v1.tsv"
        txt = file.read_text()
        file.write_text(txt.replace("snapshot.ubuntu.com", "evil.example"))
        with self.assertRaisesRegex(ValueError, "unpinned package source"):
            v9.manifest(self.repo)
    def test_catalog_missing_index_fail_closed(self):
        profile = self.asset / "apt-snapshot-profile.tsv"
        profile.write_text(profile.read_text().replace("noble-backports", "wrong-suite"))
        with self.assertRaises(ValueError):
            v9.manifest(self.repo)
    def test_no_cloud_publish_or_live_apt_switch(self):
        code = FILE.read_text()
        self.assertNotIn("boto3", code)
        self.assertNotIn("archive.ubuntu.com", code)
        self.assertNotIn("s3.put_object", code)
        self.assertIn('"distribution_ready": False', code)

if __name__ == "__main__":
    unittest.main()
