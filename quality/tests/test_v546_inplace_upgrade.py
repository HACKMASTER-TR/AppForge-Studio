"""Fail-closed V545-to-V546 signed in-place update regression checks."""
import importlib.util,json,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]

def load(path):
    spec=importlib.util.spec_from_file_location(path.stem,path)
    m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
    return m
class V546InplaceUpgrade(unittest.TestCase):
    def setUp(self):
        self.reference=json.loads((ROOT/'quality/fixtures/appforge-v9r2isolated3-v545-reference.json').read_text())
        self.module=load(ROOT/'quality/tools/verify_apk_upgrade.py')
        self.old={k:self.reference[k] for k in ('package','version_code','version_name','min_sdk','target_sdk','certificate_sha256')}
        self.new=dict(self.old,version_code=546,version_name='5.0.46-isolated3-upgrade')
    def test_reference_exact(self):
        r=self.reference
        self.assertEqual((r['version_code'],r['upgrade_version_code']),(545,546))
        self.assertEqual(r['source_run_id'],44543147932)
        self.assertEqual(r['source_artifact_id'],14540001228)
        self.assertEqual(r['apk_sha256'],'2c1b3e10f0bb1117c019a1fe7f04c067ed5aac96cfd1cb22f02bb9a2256f9205')
        self.assertEqual(r['certificate_sha256'],'dd1dbb994df46f9815c08fe14758f60b39d5787be41a1123bbc2579589f73082')
        result=self.module.check(r,self.old,self.new,r['apk_sha256'],'a'*64,'b'*40)
        self.assertTrue(result['update_compatible'])
        for key,val in [('package','other'),('certificate_sha256','c'*64),('version_code',545),('target_sdk',36)]:
            with self.subTest(key=key),self.assertRaises(ValueError):
                self.module.check(r,self.old,dict(self.new,**{key:val}),r['apk_sha256'],'a'*64,'b'*40)
    def test_ci_and_source_guard(self):
        s=(ROOT/'.github/workflows/v9-isolated3-inplace-upgrade-v546.yml').read_text()
        gradle=(ROOT/'android-app/app/build.gradle.kts').read_text()
        assert 'run-id: 44543147932' in s
        assert 'AppForge-v545-upgrade-7f7ef8617d6715d6d4b64782782d64e780d5364e' in s
        assert 'V9R2Isolated3UpgradeV546=true' in s
        assert 'APPFORGE_V9R2_ISOLATED2_KEYSTORE_B64' in s
        assert 'android-play-release.yml' in s
        assert 'actions/download-artifact@v4' in s
        assert 'workflow_dispatch:' not in s
        assert 'appforgeV9R2Isolated3UpgradeV546' in gradle
    def test_manifest_is_immutable_and_exact_version(self):
        module=load(ROOT/'quality/tools/storage_release_manifest_v546.py')
        import tempfile,hashlib
        with tempfile.TemporaryDirectory() as tmp:
            apk=Path(tmp)/'test.apk';apk.write_bytes(b'test')
            e={'package':module.PACKAGE,'certificate_sha256':module.CERT,
               'version_code':546,'source_sha':'a'*40,
               'reference_sha256':self.reference['apk_sha256'],
               'apk_sha256':hashlib.sha256(b'test').hexdigest(),'update_compatible':True}
            result=module.create(e,apk)
            self.assertEqual(result['version_code'],546)
            with self.assertRaises(ValueError):module.create(dict(e,version_code=545),apk)
if __name__ == '__main__': unittest.main()
