"""Source-only Stage 5E.6 safety contract tests. No APK, services or network."""
import importlib.util
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
PROBE = ROOT / 'quality/v9-stage5e6-keystore-probe'
ACTIVITY = PROBE / 'src/main/java/com/appforge/studio/security/V9S5E6KeystoreProbeActivity.kt'

class S5E6Contract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.activity = ACTIVITY.read_text()
        cls.manifest = (PROBE / 'src/main/AndroidManifest.xml').read_text()
        cls.gradle = (PROBE / 'build.gradle.kts').read_text()
        cls.wf = (ROOT / '.github/workflows/v9-stage5e6-android-keystore-compile-only.yml').read_text()

    def test_no_internet(self):
        self.assertNotIn('android.permission.INTERNET', self.manifest)
        self.assertNotIn('<uses-permission', self.manifest)

    def test_isolated_uid(self):
        self.assertIn('applicationId = "com.appforge.v9.keystoreprobe"', self.gradle)
        self.assertNotIn('applicationId = "com.appforge.studio"', self.gradle)

    def test_backup_denied(self):
        self.assertIn('android:allowBackup="false"', self.manifest)

    def test_android_keystore_only(self):
        self.assertIn('KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")', self.activity)

    def test_p256(self):
        self.assertIn('ECGenParameterSpec("secp256r1")', self.activity)

    def test_private_export_denied(self):
        self.assertIn('entry.privateKey.encoded == null', self.activity)

    def test_exact_production_signer(self):
        self.assertIn('V9AdminKeystoreRequestSigner(alias)', self.activity)

    def test_tamper_denied(self):
        self.assertIn('Tampered body accepted', self.activity)
        self.assertIn('Changed proof ID accepted', self.activity)

    def test_disposable_alias_cleanup(self):
        self.assertIn('finally {', self.activity)
        self.assertIn('store.deleteEntry(alias)', self.activity)

    def test_no_auto_admin_registration(self):
        self.assertIn('S5E6_ADMIN_ENROLLMENT=NO', self.activity)
        self.assertNotIn('decideDevice(', self.activity)

    def test_ci_never_assemble(self):
        self.assertNotIn(':app:assemble', self.wf)
        self.assertNotIn(':app:bundle', self.wf)

    def test_ci_fail_closed(self):
        self.assertIn('persist-credentials: false', self.wf)
        self.assertIn('contents: read', self.wf)
        self.assertNotIn('secrets.', self.wf)

if __name__ == '__main__':
    unittest.main()
