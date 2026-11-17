"""Upgrade identity guard tests; actual APK signatures are inspected separately."""
import copy
import importlib.util
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('verify_apk_upgrade', ROOT/'quality/tools/verify_apk_upgrade.py')
module = importlib.util.module_from_spec(spec);spec.loader.exec_module(module)

class ApkUpgradeTest(unittest.TestCase):
    def setUp(self):
        self.reference=json.loads((ROOT/'quality/fixtures/appforge-v9r2isolated3-reference.json').read_text())
        self.old={k:self.reference[k] for k in ['package','version_code','version_name','min_sdk','target_sdk','certificate_sha256']}
        self.new=dict(self.old,version_code=536,version_name='5.0.36-isolated3-upgrade')
    def verify(self, **overrides):
        values=dict(reference=self.reference,old=self.old,new=self.new,old_hash=self.reference['apk_sha256'],new_hash='a'*64,source_sha='b'*40);values.update(overrides)
        return module.check(**values)
    def test_matching_original_identity_and_higher_version_pass(self):
        result=self.verify();self.assertTrue(result['update_compatible']);self.assertEqual(result['physical_device'],'NOT_RUN')
    def test_standard_package_is_not_an_update(self):
        with self.assertRaisesRegex(ValueError,'UPDATE_PACKAGE_MISMATCH'):self.verify(new=dict(self.new,package='com.appforge.studio'))
    def test_same_package_with_different_signer_is_rejected(self):
        with self.assertRaisesRegex(ValueError,'UPDATE_CERTIFICATE_MISMATCH'):self.verify(new=dict(self.new,certificate_sha256='c'*64))
    def test_version_must_increase_to_the_reviewed_code(self):
        for version in [533,534,535,537]:
            with self.subTest(version=version),self.assertRaisesRegex(ValueError,'UPDATE_VERSION_MUST_INCREASE'):self.verify(new=dict(self.new,version_code=version))
    def test_modified_reference_is_rejected(self):
        with self.assertRaisesRegex(ValueError,'REFERENCE_APK_HASH_MISMATCH'):self.verify(old_hash='d'*64)
        with self.assertRaisesRegex(ValueError,'REFERENCE_IDENTITY_MISMATCH'):self.verify(old=dict(self.old,package='com.example.other'))
    def test_sdk_change_is_not_silently_accepted(self):
        with self.assertRaisesRegex(ValueError,'UPDATE_SDK_MISMATCH'):self.verify(new=dict(self.new,min_sdk=28))
    def test_source_identity_required(self):
        with self.assertRaisesRegex(ValueError,'INVALID_SOURCE_SHA'):self.verify(source_sha='unknown')
    def test_workflow_is_bound_to_original_signer_and_debug_identity(self):
        workflow=(ROOT/'.github/workflows/v9-isolated3-inplace-upgrade.yml').read_text()
        self.assertIn('APPFORGE_V9R2_ISOLATED2_KEYSTORE_B64',workflow)
        self.assertIn(self.reference['certificate_sha256'],workflow)
        self.assertIn('-PappforgeV9R2Isolated3Acceptance=true -PappforgeV9R2Isolated3Upgrade=true',workflow)
        self.assertNotIn('workflow_dispatch:',workflow)
        self.assertNotIn('gh release',workflow)
        self.assertIn('verify_apk_upgrade.py',workflow)
        self.assertIn('git merge-base --is-ancestor bf934eb4650550f9a6349dee965f4416977bdc77 HEAD',workflow)
        self.assertIn('git rev-parse HEAD:android-app/app/src/main/assets/device-build/install-ndk-host-isolated-v7.sh',workflow)
        self.assertIn('34ceb30a3e9df189db7fc724c2b7241394b631c2',workflow)
        self.assertIn('git rev-parse HEAD:android-app/app/src/main/assets/device-build/install-toolchain.sh',workflow)
        self.assertIn('01d07c8330f304957f10b43d2cd10fed24a8ca52',workflow)
        self.assertIn('git diff --name-only 404e0edb404fbf4826b48f6a7d7007b40508568a HEAD -- android-app/app/src/main/assets/device-build/install-rust-prerequisites.sh',workflow)
        self.assertIn('V9ReleaseSignatureVerifier.java',workflow)

if __name__=='__main__':unittest.main()
