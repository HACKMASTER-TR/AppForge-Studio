import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
SOURCE = (ROOT / "android-app/app/build.gradle.kts").read_text()


class ProrootTransientRetryContract(unittest.TestCase):
    def test_http_retry_is_bounded_and_narrow(self):
        self.assertIn("var attempt = 1", SOURCE)
        self.assertIn("attempt >= 4", SOURCE)
        self.assertIn("HTTP (429|502|503|504)", SOURCE)
        self.assertIn("if (match == null || attempt >= 4) throw failure", SOURCE)
        self.assertIn("APPFORGE_PROROOT_HTTP_RETRY", SOURCE)

    def test_pinned_asset_integrity_is_preserved(self):
        self.assertIn("appForgeSha256(temporary) == expectedSha256", SOURCE)
        self.assertIn("require(total == expectedSize)", SOURCE)
        self.assertIn("appForgeTrustedProrootHost(current.host.orEmpty())", SOURCE)
        self.assertIn('"https://github.com/coderredlab/proroot/releases/download/', SOURCE)
        self.assertNotIn("setDefaultHostnameVerifier", SOURCE)


if __name__ == "__main__":
    unittest.main()
