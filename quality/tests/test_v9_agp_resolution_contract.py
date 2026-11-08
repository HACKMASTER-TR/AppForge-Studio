import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
SCRIPT = (ROOT / "android-app/app/src/main/assets/device-build/prepare-offline-pack.sh").read_text()
DIAGNOSTICS = (ROOT / "android-app/app/src/main/java/com/appforge/studio/build/GradleFailureDiagnostics.kt").read_text()
WORKFLOW = (ROOT / ".github/workflows/v9-offline-module-center-r2.yml").read_text()


class AgpResolutionContract(unittest.TestCase):
    def test_official_marker_probe_and_one_bounded_refresh(self):
        self.assertIn("https://dl.google.com/dl/android/maven2/com/android/application/", SCRIPT)
        self.assertIn("APPFORGE_AGP_MAVEN_PROBE=REACHABLE", SCRIPT)
        self.assertIn('gw_refresh=0', SCRIPT)
        self.assertIn('"$gw_attempt" -eq 1', SCRIPT)
        self.assertIn("--refresh-dependencies", SCRIPT)
        self.assertIn('! grep -Eiq "$gw_unsafe"', SCRIPT)
        self.assertIn('"${APPFORGE_DEVICE_OFFLINE:-0}" != 1', SCRIPT)
        self.assertNotIn("--insecure", SCRIPT)

    def test_precise_secret_safe_failure_codes(self):
        self.assertIn('"AGP_PLUGIN_UNRESOLVED"', DIAGNOSTICS)
        self.assertIn('"CHAQUOPY_PLUGIN_UNRESOLVED"', DIAGNOSTICS)
        self.assertIn('"GRADLE_OFFLINE_CACHE_MISS"', DIAGNOSTICS)
        self.assertIn('"TLS_TRUST"', DIAGNOSTICS)

    def test_new_branch_has_full_push_gates(self):
        self.assertIn('codex/v9-r2-agp-resolution-fix-20261108', WORKFLOW)
        self.assertEqual(WORKFLOW.count("github.event_name == 'push' && ("), 2)


if __name__ == '__main__':
    unittest.main()
