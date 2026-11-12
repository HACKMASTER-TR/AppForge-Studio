import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
TOOL = (ROOT / "quality/tools/v9_pinned_kotlin_compiler.py").read_text()
WORKFLOW = (ROOT / ".github/workflows/v9-offline-module-center-r2.yml").read_text()
SETTINGS = (ROOT / "android-app/settings.gradle.kts").read_text()


class KotlinCompilerMirrorContract(unittest.TestCase):
    def test_jetbrains_sha256_and_version_are_pinned(self):
        self.assertIn("9309638a2ee03e6bde9ef4b7444055a94b84ab906563675d71ff9aecb64da913", TOOL)
        self.assertIn('EXPECTED_SIZE = 60_352_373', TOOL)
        self.assertIn('VERSION = "2.4.10"', TOOL)
        self.assertIn('digest.hexdigest() != EXPECTED_SHA256', TOOL)
        self.assertIn('raise RuntimeError("KOTLIN_PINNED_HASH_MISMATCH")', TOOL)
        self.assertNotIn('verify=False', TOOL)
        self.assertNotIn('--insecure', TOOL)

    def test_official_pom_and_mirrors_scoped(self):
        self.assertIn('OFFICIAL_BASE = "https://repo.maven.apache.org/maven2/"', TOOL)
        self.assertIn('final.hostname != "repo.maven.apache.org"', TOOL)
        self.assertIn('"https://maven.wso2.org/nexus/content/repositories/central/"', TOOL)
        self.assertIn('"https://repository.liferay.com/nexus/content/repositories/gradle-plugins/"', TOOL)
        self.assertIn('includeModule("org.jetbrains.kotlin", "kotlin-compiler-embeddable")', SETTINGS)
        self.assertEqual(SETTINGS.count("appforgePinnedKotlinCompilerRepo"), 1)

    def test_every_build_job_uses_verified_ci_repo(self):
        self.assertEqual(WORKFLOW.count("      - name: Prepare SHA-256 pinned Kotlin compiler (CI only)"), 3)
        self.assertEqual(WORKFLOW.count('-PappforgePinnedKotlinCompilerRepo='), 3)
        self.assertIn('v9_pinned_kotlin_compiler.py', WORKFLOW)
        self.assertIn('KOTLIN_LOCAL_REPOSITORY=READY', TOOL)


if __name__ == '__main__':
    unittest.main()
