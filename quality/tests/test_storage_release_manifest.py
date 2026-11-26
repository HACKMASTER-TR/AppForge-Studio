import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
def module(name):
    spec=importlib.util.spec_from_file_location(name,ROOT/'quality/tools'/f'{name}.py')
    result=importlib.util.module_from_spec(spec);spec.loader.exec_module(result);return result
manifest=module('storage_release_manifest')
upgrade=module('verify_apk_upgrade')

class StorageReleaseTest(unittest.TestCase):
    def test_actual_v540_reference_requires_v541_and_same_identity(self):
        ref=json.loads((ROOT/'quality/fixtures/appforge-v9r2isolated3-v540-reference.json').read_text())
        old={k:ref[k] for k in ['package','version_code','version_name','min_sdk','target_sdk','certificate_sha256']}
        new=dict(old,version_code=541)
        good=upgrade.check(ref,old,new,ref['apk_sha256'],'a'*64,'b'*40)
        self.assertTrue(good['update_compatible'])
        for key,value in [('package','com.example.other'),('certificate_sha256','c'*64),('version_code',540)]:
            with self.subTest(key=key),self.assertRaises(ValueError):
                upgrade.check(ref,old,dict(new,**{key:value}),ref['apk_sha256'],'a'*64,'b'*40)

    def test_v541_workflow_uses_existing_verified_v540_reference(self):
        workflow=(ROOT/'.github/workflows/v9-isolated3-inplace-upgrade.yml').read_text()
        fixture=json.loads((ROOT/'quality/fixtures/appforge-v9r2isolated3-v540-reference.json').read_text())
        self.assertIn('actions/download-artifact@v4',workflow)
        self.assertIn('run-id: 44285015575',workflow)
        self.assertIn('name: AppForge-v9r2isolated3-upgrade-366de04d35c107eebc38e5e95f36e1765cda38ba',workflow)
        self.assertIn('appforge-v9r2isolated3-v540-reference.json',workflow)
        self.assertIn(fixture['apk_sha256'],workflow)
        self.assertNotIn('run-id: 44054962868',workflow)
        self.assertEqual(fixture['apk_sha256'],'d702b8c3a069f0151e541dc478d76f5c1e49d3ee10fcc1014eade36172aa13c3')
        self.assertEqual(fixture['upgrade_version_code'],541)
        self.assertIn('physical_device',workflow)

    def test_manifest_rejects_changed_apk_or_unverified_identity(self):
        with tempfile.TemporaryDirectory() as d:
            apk=Path(d)/'fixture.apk';apk.write_bytes(b'fixture-only-not-a-real-apk')
            evidence=dict(package=manifest.PACKAGE,certificate_sha256=manifest.CERT,version_code=541,
                          apk_sha256=hashlib.sha256(apk.read_bytes()).hexdigest(),update_compatible=True,
                          source_sha='a'*40,reference_sha256='b'*64)
            result=manifest.create(evidence,apk)
            self.assertEqual(result['apk_bytes'],apk.stat().st_size)
            self.assertEqual(result['physical_device'],'NOT_RUN')
            for key,value in [('package','other'),('certificate_sha256','c'*64),('version_code',540),('update_compatible',False)]:
                with self.subTest(key=key),self.assertRaises(ValueError):manifest.create(dict(evidence,**{key:value}),apk)
            apk.write_bytes(b'modified')
            with self.assertRaises(ValueError):manifest.create(evidence,apk)

if __name__=='__main__':unittest.main()
