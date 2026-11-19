"""Fail-closed V544-to-V545 signed in-place update regression checks."""
import importlib.util,json,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]

def load(path):
    spec=importlib.util.spec_from_file_location(path.stem,path)
    m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
    return m
class V545InplaceUpgrade(unittest.TestCase):
    def setUp(self):
        self.reference=json.loads((ROOT/'quality/fixtures/appforge-v9r2isolated3-v544-reference.json').read_text())
        self.module=load(ROOT/'quality/tools/verify_apk_upgrade.py')
        self.old={k:self.reference[k] for k in ('package','version_code','version_name','min_sdk','target_sdk','certificate_sha256')}
        self.new=dict(self.old,version_code=545,version_name='5.0.45-isolated3-upgrade')
    def test_reference_exact(self):
        r=self.reference
        self.assertEqual((r['version_code'],r['upgrade_version_code']),(544,545))
        self.assertEqual(r['source_run_id'],44532323370)
        self.assertEqual(r['source_artifact_id'],14534123932)
        self.assertEqual(r['apk_sha256'],'1b18f279e142eb1e26edea3513b09f9c4dbb5b4fb42d74e9a99deba15f2afc86')
        self.assertEqual(r['certificate_sha256'],'dd1dbb994df46f9815c08fe14758f60b39d5787be41a1123bbc2579589f73082')
        result=self.module.check(r,self.old,self.new,r['apk_sha256'],'a'*64,'b'*40)
        self.assertTrue(result['update_compatible'])
        for key,val in [('package','other'),('certificate_sha256','c'*64),('version_code',544),('target_sdk',36)]:
            with self.subTest(key=key),self.assertRaises(ValueError):
                self.module.check(r,self.old,dict(self.new,**{key:val}),r['apk_sha256'],'a'*64,'b'*40)
    def test_ci_and_source_guard(self):
        s=(ROOT/'.github/workflows/v9-isolated3-inplace-upgrade-v545.yml').read_text()
        gradle=(ROOT/'android-app/app/build.gradle.kts').read_text()
        assert 'run-id: 44532323370' in s
        assert 'AppForge-v544-upgrade-85f1b7e9255422bbf4c1afbb2a2a2394700e9268' in s
        assert 'V9R2Isolated3UpgradeV545=true' in s
        assert 'APPFORGE_V9R2_ISOLATED2_KEYSTORE_B64' in s
        assert 'android-play-release.yml' in s
        assert 'actions/download-artifact@v4' in s
        assert 'workflow_dispatch:' not in s
        assert 'appforgeV9R2Isolated3UpgradeV545' in gradle
    def test_manifest_is_immutable_and_exact_version(self):
        module=load(ROOT/'quality/tools/storage_release_manifest_v545.py')
        import tempfile,hashlib
        with tempfile.TemporaryDirectory() as tmp:
            apk=Path(tmp)/'test.apk';apk.write_bytes(b'test')
            e={'package':module.PACKAGE,'certificate_sha256':module.CERT,
               'version_code':545,'source_sha':'a'*40,
               'reference_sha256':self.reference['apk_sha256'],
               'apk_sha256':hashlib.sha256(b'test').hexdigest(),'update_compatible':True}
            result=module.create(e,apk)
            self.assertEqual(result['version_code'],545)
            with self.assertRaises(ValueError):module.create(dict(e,version_code=544),apk)
if __name__ == '__main__': unittest.main()
