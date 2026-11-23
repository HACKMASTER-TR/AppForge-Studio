"""R55 resource regression: official r27b Clang resource directory must be staged."""
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
SCRIPT = (ROOT / 'tests/r55_arm64_reference_link_smoke.sh').read_text()
FLOW = (ROOT / '.github/workflows/r55-arm64-ndk-reference-link-ci.yml').read_text()


class ReferenceResourceContract(unittest.TestCase):
    def test_official_google_and_arm64_join(self):
        for marker in (
            "ZIP_RESOURCE='android-ndk-r27b/toolchains/llvm/prebuilt/linux-x86_64/lib/clang/*'",
            'cp -a "$OFFICIAL_HOST/lib/clang/." "$T/lib/clang/"',
            'RESOURCE="$("$T/bin/clang" -print-resource-dir)"',
            'include/stddef.h',
            'libclang_rt.builtins-aarch64-android.a',
            'lib/linux/aarch64/libunwind.a',
            'R55_OFFICIAL_CLANG_RESOURCES=PASS_PINNED_R27B',
        ):
            self.assertIn(marker, SCRIPT)
        self.assertLess(SCRIPT.index('R55_OFFICIAL_CLANG_RESOURCES=PASS_PINNED_R27B'),
                        SCRIPT.index('R55_C_API26_LINK'))
        self.assertIn('python3 tests/test_r55_arm64_resource_contract.py', FLOW)


if __name__ == '__main__':
    unittest.main(verbosity=2)
