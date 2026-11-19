"""Pin V540 and treat V541 as an identity-only candidate until real device acceptance."""
import json
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[2]
class V541ReferenceContract(unittest.TestCase):
    def test_reference_is_pinned(self):
        f=json.loads((ROOT/'quality/fixtures/appforge-v9r2isolated3-v540-reference.json').read_text())
        self.assertEqual(f['version_code'],540)
        self.assertEqual(f['upgrade_version_code'],541)
        self.assertEqual(f['apk_sha256'],'d702b8c3a069f0151e541dc478d76f5c1e49d3ee10fcc1014eade36172aa13c3')
    def test_no_adb_in_ci(self):
        wf=(ROOT/'.github/workflows/v9-isolated3-inplace-upgrade.yml').read_text()
        self.assertIn('gh release download appforge-v540-ci-reference',wf)
        self.assertIn("'physical_device':'NOT_RUN'",wf)
        self.assertNotIn('reactivecircus/android-emulator-runner',wf)
        self.assertNotIn('emulator-5554',wf)
        self.assertNotIn('gh release create',wf)
        build=(ROOT/'android-app/app/build.gradle.kts').read_text()
        self.assertIn('output.versionCode.set(541)',build)
        self.assertIn('5.0.41-isolated3-upgrade',build)
if __name__=='__main__':unittest.main()
