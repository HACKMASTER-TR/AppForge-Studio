# V543 source candidate: pinned signer, APK 542-to-543 upgrade, no production release.
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

class V543InplaceUpgrade(unittest.TestCase):
    def setUp(self):
        self.reference=json.loads((ROOT/'quality/fixtures/appforge-v9r2isolated3-v542-reference.json').read_text())
        self.module=load(ROOT/'quality/tools/verify_apk_upgrade.py')
        self.old={k:self.reference[k] for k in ('package','version_code','version_name','min_sdk','target_sdk','certificate_sha256')}
        self.new=dict(self.old,version_code=543,version_name='5.0.43-isolated3-upgrade')
    def test_v542_is_exact_signed_reference_for_543(self):
        ref=self.reference
        self.assertEqual(ref['version_code'],542)
        self.assertEqual(ref['upgrade_version_code'],543)
        self.assertEqual(ref['source_run_id'],44487197643)
        self.assertEqual(ref['source_artifact_id'],14511267369)
        self.assertEqual(ref['apk_sha256'],'b0399428f35724a56b8e8476da9838cb7063f1881a69aed1b5a006626967bd4b')
        result=self.module.check(ref,self.old,self.new,ref['apk_sha256'],'a'*64,'b'*40)
        self.assertTrue(result['update_compatible'])
        for key,val in [('package','other'),('certificate_sha256','c'*64),('version_code',542)]:
            with self.subTest(key=key),self.assertRaises(ValueError):
                self.module.check(ref,self.old,dict(self.new,**{key:val}),ref['apk_sha256'],'a'*64,'b'*40)
    def test_workflow_is_new_and_separately_opted_in(self):
        gradle=(ROOT/'android-app/app/build.gradle.kts').read_text()
        workflow=(ROOT/'.github/workflows/v9-isolated3-inplace-upgrade-v543.yml').read_text()
        old=(ROOT/'.github/workflows/v9-isolated3-inplace-upgrade-v542.yml').read_text()
        self.assertIn('output.versionCode.set(543)',gradle)
        self.assertIn('output.versionName.set("5.0.43-isolated3-upgrade")',gradle)
        self.assertIn('appforgeV9R2Isolated3UpgradeV543',gradle)
        self.assertIn('run-id: 44487197643',workflow)
        self.assertIn('AppForge-v542-upgrade-b08c3da476643d989acb87198ac5d23266c29265',workflow)
        self.assertIn('appforge-v9r2isolated3-v542-reference.json',workflow)
        self.assertIn('V9R2Isolated3UpgradeV543=true',workflow)
        self.assertIn('storage_release_manifest_v543.py',workflow)
        self.assertIn('actions/download-artifact@v4',workflow)
        self.assertIn('AppForge-v543-upgrade-${{ github.sha }}',workflow)
        self.assertIn('APPFORGE_V9R2_ISOLATED2_KEYSTORE_B64',workflow)
        self.assertIn('run-id: 44469897683',old)
        self.assertIn('-PappforgeV9R2Isolated3UpgradeV542=true',old)
        self.assertNotIn('workflow_dispatch:',workflow)
        self.assertNotIn('gh release create',workflow)
    def test_v543_manifest_accepts_only_correct_version(self):
        module=load(ROOT/'quality/tools/storage_release_manifest_v543.py')
        import tempfile,hashlib
        with tempfile.TemporaryDirectory() as temp:
            apk=Path(temp)/'example.apk';apk.write_bytes(b'test-only')
            info={'package':module.PACKAGE,'certificate_sha256':module.CERT,
                  'version_code':543,'source_sha':'a'*40,
                  'reference_sha256':self.reference['apk_sha256'],
                  'apk_sha256':hashlib.sha256(b'test-only').hexdigest(),
                  'update_compatible':True}
            report=module.create(info,apk)
            self.assertEqual(report['version_code'],543)
            with self.assertRaises(ValueError):
                module.create(dict(info,version_code=542),apk)

if __name__=='__main__': unittest.main()
