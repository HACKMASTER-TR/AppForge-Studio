import pathlib
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
SCRIPT = (ROOT / "android-app/app/src/main/assets/device-build/prepare-offline-pack.sh").read_text()
DIAGNOSTICS = (ROOT / "android-app/app/src/main/java/com/appforge/studio/build/GradleFailureDiagnostics.kt").read_text()
WORKFLOW = (ROOT / ".github/workflows/v9-offline-module-center-r2.yml").read_text()


class AgpResolutionContract(unittest.TestCase):
    def test_official_marker_probe_and_one_bounded_refresh(self):
        self.assertIn("https://dl.google.com/dl/android/maven2/com/android/application/", SCRIPT)
        self.assertIn("APPFORGE_AGP_MAVEN_PROBE=REACHABLE", SCRIPT)
        self.assertIn('gw_refresh=0', SCRIPT)
        self.assertIn('"$gw_attempt" -eq 1', SCRIPT)
        self.assertIn("--refresh-dependencies", SCRIPT)
        self.assertIn('! grep -Eiq "$gw_unsafe"', SCRIPT)
        self.assertIn('"${APPFORGE_DEVICE_OFFLINE:-0}" != 1', SCRIPT)
        self.assertNotIn("--insecure", SCRIPT)

    def test_probe_classifies_curl_failures_without_persisting_raw_output(self):
        probe = SCRIPT[SCRIPT.index("agp_marker_reachable()"):SCRIPT.index("gradle_warmup()")]
        for status, http, expected in [
            (0, "200", "REACHABLE"), (6, "000", "DNS"), (7, "000", "CONNECT"),
            (28, "000", "TIMEOUT"), (60, "000", "TLS"), (22, "403", "HTTP_403"),
            (22, "404", "HTTP_404"), (22, "503", "HTTP_503"),
            (0, "raw response", "UNREACHABLE"),
        ]:
            with self.subTest(status=status, http=http):
                result = subprocess.run(
                    ["sh", "-eu", "-c", probe + """
                    curl() { printf '%s' "$http"; return "$status"; }
                    status=$1; http=$2
                    agp_marker_reachable
                    """, "probe", str(status), http],
                    env={"PATH": "/usr/bin:/bin", "APPFORGE_DEVICE_OFFLINE": "0"},
                    capture_output=True, text=True,
                )
                self.assertEqual(result.stdout, f"APPFORGE_AGP_MAVEN_PROBE={expected}\n")
                self.assertEqual(result.returncode, int(expected != "REACHABLE"))

    def test_warmup_records_probe_only_online_and_preserves_failure_exit(self):
        functions = SCRIPT[SCRIPT.index("agp_marker_reachable()"):SCRIPT.index('case "$MODE"')]
        for offline, status, http, attempts in [
            ("0", 60, "000", 1), ("1", 0, "200", 1), ("0", 0, "200", 2),
        ]:
            with self.subTest(offline=offline, status=status), tempfile.TemporaryDirectory() as temp:
                result = subprocess.run(
                    ["sh", "-eu", "-c", functions + """
                    gradle() {
                        printf '%s\n' "FAILURE: Gradle warmup failed" \
                          "Plugin [id: 'com.android.application', version: '9.1.1'] was not found"
                        return 1
                    }
                    curl() {
                        touch "$probe_file"
                        printf '%s' "$http"
                        return "$status"
                    }
                    agp_jvm_probe() {
                        echo 'APPFORGE_AGP_JVM_MARKER=TLS'
                    }
                    GRADLE=gradle
                    GRADLE_USER_HOME=$1
                    probe_file=$1/probe
                    status=$2; http=$3
                    gradle_warmup "$1" "$1/gradle.log" ANDROID 62
                    """, "warmup", temp, str(status), http],
                    env={"PATH": "/usr/bin:/bin", "APPFORGE_DEVICE_OFFLINE": offline},
                    capture_output=True, text=True,
                )
                self.assertEqual(result.returncode, 62, result.stderr)
                self.assertEqual((pathlib.Path(temp) / "probe").exists(), offline == "0")
                log = (pathlib.Path(temp) / "gradle.log").read_text()
                self.assertEqual("APPFORGE_AGP_MAVEN_PROBE=TLS" in log, status == 60)
                self.assertEqual(result.stdout.count("APPFORGE_AGP_RESOLUTION_RETRY="), attempts - 1)
                self.assertIn(f"APPFORGE_GRADLE_ATTEMPTS={attempts}", result.stdout)
                self.assertEqual("APPFORGE_AGP_JVM_MARKER=TLS" in log, attempts == 2)

    def test_precise_secret_safe_failure_codes(self):
        self.assertIn('"AGP_PLUGIN_UNRESOLVED"', DIAGNOSTICS)
        self.assertIn('"CHAQUOPY_PLUGIN_UNRESOLVED"', DIAGNOSTICS)
        self.assertIn('"GRADLE_OFFLINE_CACHE_MISS"', DIAGNOSTICS)
        self.assertIn('"TLS_TRUST"', DIAGNOSTICS)

    def test_build_jvm_uses_jdk_trust_and_apt_keeps_explicit_verification(self):
        sources = ROOT / "android-app/app/src/main/java/com/appforge/studio/build"
        for name in ("AndroidRuntimeTrust.kt", "OfflineBuildPackManager.kt",
                     "ProjectDependencyPreparation.kt", "DeviceBuildEngine.kt"):
            source = (sources / name).read_text()
            self.assertNotIn("AndroidRuntimeTrust.shellEnvironment", source)
            self.assertNotIn("-Djavax.net.ssl.trustStore=", source)
        trust = (sources / "AndroidRuntimeTrust.kt").read_text()
        for setting in ("CaInfo", "Verify-Peer", "Verify-Host"):
            self.assertIn("Acquire::https::" + setting, trust)

    def test_module_sdk_preferences_and_debug_key_stay_in_operation_workspace(self):
        source = (ROOT / "android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt").read_text()
        execute = source.split("private suspend fun execute(", 1)[1]
        self.assertIn('File(workspace.canonicalFile,".appforge-sdk-user/.android")', execute)
        self.assertIn("ANDROID_USER_HOME=/workspace/.appforge-sdk-user/.android", execute)
        self.assertIn("ANDROID_SDK_HOME=/workspace/.appforge-sdk-user", execute)

    def test_new_branch_has_full_push_gates(self):
        self.assertIn('codex/v9-r2-agp-resolution-fix-20261108', WORKFLOW)
        self.assertEqual(WORKFLOW.count("github.event_name == 'push' && ("), 2)


if __name__ == '__main__':
    unittest.main()
