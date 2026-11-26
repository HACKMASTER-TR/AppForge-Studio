"""Fail-closed V543-to-V544 signed in-place update regression checks."""
import importlib.util,json,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]

def load(path):
    spec=importlib.util.spec_from_file_location(path.stem,path)
    m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
    return m
class V544InplaceUpgrade(unittest.TestCase):
    def setUp(self):
        self.reference=json.loads((ROOT/'quality/fixtures/appforge-v9r2isolated3-v543-reference.json').read_text())
        self.module=load(ROOT/'quality/tools/verify_apk_upgrade.py')
        self.old={k:self.reference[k] for k in ('package','version_code','version_name','min_sdk','target_sdk','certificate_sha256')}
        self.new=dict(self.old,version_code=544,version_name='5.0.44-isolated3-upgrade')
    def test_reference_exact(self):
        r=self.reference
        self.assertEqual((r['version_code'],r['upgrade_version_code']),(543,544))
        self.assertEqual(r['source_run_id'],44511479352)
        self.assertEqual(r['source_artifact_id'],14524272396)
        self.assertEqual(r['apk_sha256'],'5267c55a50ef8daeeee3c371ea55bae2a3a2109395729bd71662956856ae4b97')
        self.assertEqual(r['certificate_sha256'],'dd1dbb994df46f9815c08fe14758f60b39d5787be41a1123bbc2579589f73082')
        result=self.module.check(r,self.old,self.new,r['apk_sha256'],'a'*64,'b'*40)
        self.assertTrue(result['update_compatible'])
        for key,val in [('package','other'),('certificate_sha256','c'*64),('version_code',543),('target_sdk',36)]:
            with self.subTest(key=key),self.assertRaises(ValueError):
                self.module.check(r,self.old,dict(self.new,**{key:val}),r['apk_sha256'],'a'*64,'b'*40)
    def test_ci_and_source_guard(self):
        s=(ROOT/'.github/workflows/v9-isolated3-inplace-upgrade-v544.yml').read_text()
        gradle=(ROOT/'android-app/app/build.gradle.kts').read_text()
        assert 'run-id: 44511479352' in s
        assert 'AppForge-v543-upgrade-0db6e1e70682b07ce03d68f5df561f484d0b9efc' in s
        assert 'V9R2Isolated3UpgradeV544=true' in s
        assert 'APPFORGE_V9R2_ISOLATED2_KEYSTORE_B64' in s
        assert 'android-play-release.yml' in s
        assert 'actions/download-artifact@v4' in s
        assert 'workflow_dispatch:' not in s
        assert 'appforgeV9R2Isolated3UpgradeV544' in gradle
    def test_manifest_is_immutable_and_exact_version(self):
        module=load(ROOT/'quality/tools/storage_release_manifest_v544.py')
        import tempfile,hashlib
        with tempfile.TemporaryDirectory() as tmp:
            apk=Path(tmp)/'test.apk';apk.write_bytes(b'test')
            e={'package':module.PACKAGE,'certificate_sha256':module.CERT,
               'version_code':544,'source_sha':'a'*40,
               'reference_sha256':self.reference['apk_sha256'],
               'apk_sha256':hashlib.sha256(b'test').hexdigest(),'update_compatible':True}
            result=module.create(e,apk)
            self.assertEqual(result['version_code'],544)
            with self.assertRaises(ValueError):module.create(dict(e,version_code=543),apk)
if __name__ == '__main__': unittest.main()
