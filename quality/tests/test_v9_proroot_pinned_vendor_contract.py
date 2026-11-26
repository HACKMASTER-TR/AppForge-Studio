import hashlib
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
BASE = ROOT / "android-app/app/src/main/proroot-pinned/arm64-v8a"
GRADLE = (ROOT / "android-app/app/build.gradle.kts").read_text()
ASSETS = {
    "libproroot.so": (43624, "a4e74d75b66cdc02b080adfe863dbf9951c3b30610d77beddc95488d5fe5de01"),
    "libproroot-runtime.so": (375120, "8c47a0a7db32d84c179ebb5bf3640f655a3181860ece5886ae44d92858730c34"),
    "libproroot-bridge.so": (23624, "1c5bc9537a270e8bf8b1c70222813f57b60b828bfb5503ddf8fe37685092de2f"),
    "libproroot-linker.so": (79408, "51a0ec5bfed00e572a0de09e22d9057e2befc386b78e426613d3e0ab03f4ecee"),
    "libproroot-stub-loader.so": (144144, "06c6624db3bdc45b9ced151cd781df439a37b47731d244b93e9d6a58cd48cde0"),
}

class ProrootPinnedVendorContract(unittest.TestCase):
    def test_all_five_original_release_binaries_match_upstream_sha256(self):
        self.assertEqual({p.name for p in BASE.iterdir()}, set(ASSETS))
        for name, (length, digest) in ASSETS.items():
            f = BASE / name
            self.assertTrue(f.is_file() and not f.is_symlink())
            self.assertEqual(f.stat().st_size, length)
            self.assertEqual(hashlib.sha256(f.read_bytes()).hexdigest(), digest)

    def test_gradle_reuses_only_verified_source_before_network(self):
        self.assertIn('val appForgeVendoredProrootRoot = layout.projectDirectory.dir(', GRADLE)
        self.assertIn('APPFORGE_PROROOT_PINNED_SOURCE_INVALID', GRADLE)
        self.assertIn('!Files.isSymbolicLink(vendored.toPath()) && verified(vendored)', GRADLE)
        self.assertIn('if(!verified(cache)) promote(vendored,cache)', GRADLE)
        self.assertIn('appForgeSha256(file)==asset.sha256', GRADLE)
        self.assertIn('https://github.com/coderredlab/proroot/releases/download/', GRADLE)
        self.assertNotIn('trustAllCerts', GRADLE)

if __name__ == '__main__':
    unittest.main()
