"""V542 signed in-place update: no legacy rewrite, reference and signer pinning."""
import importlib.util
import json
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[2]
def load(path):
    spec=importlib.util.spec_from_file_location(path.stem,path)
    obj=importlib.util.module_from_spec(spec)
    spec.loader.exec_module(obj)
    return obj

class V542InplaceUpgrade(unittest.TestCase):
    def setUp(self):
        self.reference=json.loads((ROOT/'quality/fixtures/appforge-v9r2isolated3-v541-reference.json').read_text())
        self.module=load(ROOT/'quality/tools/verify_apk_upgrade.py')
        self.old={k:self.reference[k] for k in ('package','version_code','version_name','min_sdk','target_sdk','certificate_sha256')}
        self.new=dict(self.old,version_code=542,version_name='5.0.42-isolated3-upgrade')
    def test_signed_541_is_exact_reference_for_542(self):
        ref=self.reference
        self.assertEqual(ref['version_code'],541)
        self.assertEqual(ref['upgrade_version_code'],542)
        self.assertEqual(ref['source_run_id'],44469897683)
        self.assertEqual(ref['apk_sha256'],'51b60a9f889b45c1a3e99215dad120b98f3b2f97c67f0dfb4736c9e9b6625c93')
        result=self.module.check(ref,self.old,self.new,ref['apk_sha256'],'a'*64,'b'*40)
        self.assertTrue(result['update_compatible'])
        for key,val in [('package','other'),('certificate_sha256','c'*64),('version_code',541)]:
            with self.subTest(key=key),self.assertRaises(ValueError):
                self.module.check(ref,self.old,dict(self.new,**{key:val}),ref['apk_sha256'],'a'*64,'b'*40)
    def test_separate_build_opt_in_and_reference_workflow(self):
        gradle=(ROOT/'android-app/app/build.gradle.kts').read_text()
        workflow=(ROOT/'.github/workflows/v9-isolated3-inplace-upgrade-v542.yml').read_text()
        old=(ROOT/'.github/workflows/v9-isolated3-inplace-upgrade.yml').read_text()
        self.assertIn('output.versionCode.set(542)',gradle)
        self.assertIn('output.versionName.set("5.0.42-isolated3-upgrade")',gradle)
        self.assertIn('appforgeV9R2Isolated3UpgradeV542',gradle)
        self.assertIn('run-id: 44469897683',workflow)
        self.assertIn('appforge-v9r2isolated3-v541-reference.json',workflow)
        self.assertIn('V9R2Isolated3UpgradeV542=true',workflow)
        self.assertIn('storage_release_manifest_v542.py',workflow)
        self.assertIn('actions/download-artifact@v4',workflow)
        self.assertIn('AppForge-v542-upgrade-${{ github.sha }}',workflow)
        self.assertIn('APPFORGE_V9R2_ISOLATED2_KEYSTORE_B64',workflow)
        self.assertIn('run-id: 44285015575',old)
        self.assertIn('-PappforgeV9R2Isolated3Upgrade=true',old)
        self.assertNotIn('workflow_dispatch:',workflow)
        self.assertNotIn('gh release create',workflow)
    def test_v542_manifest_is_exact_not_v541(self):
        module=load(ROOT/'quality/tools/storage_release_manifest_v542.py')
        import tempfile,hashlib
        with tempfile.TemporaryDirectory() as temp:
            apk=Path(temp)/'example.apk';apk.write_bytes(b'test-only')
            info={'package':module.PACKAGE,'certificate_sha256':module.CERT,
                  'version_code':542,'source_sha':'a'*40,
                  'reference_sha256':self.reference['apk_sha256'],
                  'apk_sha256':hashlib.sha256(b'test-only').hexdigest(),
                  'update_compatible':True}
            report=module.create(info,apk)
            self.assertEqual(report['version_code'],542)
            with self.assertRaises(ValueError):
                module.create(dict(info,version_code=541),apk)

if __name__=='__main__': unittest.main()
