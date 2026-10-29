"""Explicit opt-in: python3 -B .../test_contract.py. Uses no fabricated native PASS."""
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
spec = importlib.util.spec_from_file_location('contract', HERE / 'contract.py')
c = importlib.util.module_from_spec(spec)
spec.loader.exec_module(c)


class Contract(unittest.TestCase):
    def test_authored_source_and_pin(self):
        c.verify_source(ROOT / 'examples/rust-expo-jsi')
        with tempfile.TemporaryDirectory(dir=ROOT / '.appforge/tmp/v10/phase12-1') as temp:
            p = Path(temp) / 'candidate'
            p.write_bytes((HERE / 'RustRuntimeModule.h').read_bytes() + b'\n')
            with self.assertRaisesRegex(ValueError, 'hash drift'):
                c.verify_file(p, c.PINS['candidate_sha256'])

    def test_reject_missing_library(self):
        with self.assertRaisesRegex(ValueError, 'absent library'):
            c.inspect_elf('/unavailable/readelf', '/unavailable/nm', Path('/absent.so'), Path('/absent.so'),
                          ROOT / '.appforge/tmp/v10/phase12-1/negative-elf', 'arm64-v8a')

    def test_reject_wrong_abi_before_tools(self):
        with self.assertRaisesRegex(ValueError, 'unsupported ABI'):
            c.inspect_elf('/unavailable/readelf', '/unavailable/nm', Path('/absent.so'), Path('/absent.so'),
                          ROOT / '.appforge/tmp/v10/phase12-1/negative-elf', 'x86_64')
        env = os.environ.copy()
        env['CANDIDATE_ABI'] = 'x86_64'
        p = subprocess.run(['bash', str(ROOT / 'scripts/v10/phase12-abi-link-candidate.sh')],
                           cwd=ROOT, env=env, capture_output=True)
        self.assertEqual(p.returncode, 64)

    def test_elf_malicious_outputs_rejected(self):
        # Synthetic tool output tests validator rejection only, never native acceptance.
        from unittest.mock import patch
        from types import SimpleNamespace
        with tempfile.TemporaryDirectory(dir=ROOT / '.appforge/tmp/v10/phase12-1') as temp:
            root = Path(temp)
            lib = root / 'negative.so'
            lib.write_bytes(b'negative-control-only')
            def result(cmd, **kwargs):
                if '-h' in cmd:
                    return SimpleNamespace(stdout='Class: ELF64\nType: DYN\nMachine: Advanced Micro Devices X86-64\n')
                return SimpleNamespace(stdout='')
            with patch.object(c.subprocess, 'run', side_effect=result):
                with self.assertRaisesRegex(ValueError, 'incorrect architecture'):
                    c.inspect_elf('readelf', 'nm', lib, lib, root, 'arm64-v8a')
            def no_export(cmd, **kwargs):
                if '-h' in cmd:
                    return SimpleNamespace(stdout='Class: ELF64\nType: DYN\nMachine: AArch64\n')
                return SimpleNamespace(stdout='')
            with patch.object(c.subprocess, 'run', side_effect=no_export):
                with self.assertRaisesRegex(ValueError, 'missing real Rust export'):
                    c.inspect_elf('readelf', 'nm', lib, lib, root, 'arm64-v8a')

    def test_prepare_cannot_touch_repository(self):
        with self.assertRaisesRegex(ValueError, 'outside runner temp'):
            c.prepare(ROOT / 'examples/rust-expo-jsi', ROOT / '.appforge/tmp/v10/phase12-1')

    def test_real_headers_and_compiler_negative_controls(self):
        project = ROOT / '.appforge/tmp/v10/phase11/sdk54-newarch'
        c.verify_rn(project)
        common = project / 'node_modules/react-native/ReactCommon'
        base = ['clang++', '-std=c++20', '-fsyntax-only', '-Wall', '-Wextra', '-Werror']
        base += ['-I' + str(common / p) for p in ['jsi', 'callinvoker', 'runtimeexecutor', 'react/nativemodule/core', '.']]
        original = (HERE / 'RustRuntimeModule.h').read_text()
        cases = [(original, True), (original.replace('installJSIBindingsWithRuntime(jsi::Runtime& rt)',
                  'installJSIBindingsWithRuntime(jsi::Runtime* rt)'), False),
                 (original + '\nvoid bad(const facebook::react::RustRuntimeModule& m) { auto copy=m; }\n', False)]
        out = ROOT / '.appforge/tmp/v10/phase12-1'
        for index, (text, expected) in enumerate(cases):
            source = out / ('compiler-control-' + str(index) + '.cpp')
            source.write_text(text)
            proc = subprocess.run(base + [str(source)], capture_output=True)
            (out / ('compiler-control-' + str(index) + '.log')).write_bytes(proc.stdout + proc.stderr)
            self.assertEqual(proc.returncode == 0, expected)

    def test_real_generated_project_adaptation(self):
        with tempfile.TemporaryDirectory(dir=ROOT / '.appforge/tmp/v10/phase12-1') as temp:
            project = Path(temp) / 'sdk54'
            project.mkdir()
            source = ROOT / '.appforge/tmp/v10/phase11/sdk54-newarch'
            shutil.copytree(source / 'android', project / 'android')
            # Dependency tree is only read; all generated source writes stay in temp.
            (project / 'node_modules').symlink_to(source / 'node_modules', target_is_directory=True)
            c.prepare(project, Path(temp))
            jni = project / 'android/app/src/main/jni'
            onload = (jni / 'OnLoad.cpp').read_text()
            self.assertIn('JNIEXPORT jint JNICALL JNI_OnLoad', onload)
            self.assertIn('appforgeRuntimeProvider(name, jsInvoker)', onload)
            self.assertIn('return autolinking_cxxModuleProvider(name, jsInvoker);', onload)
            app = next((project / 'android/app/src/main/java').rglob('MainApplication.kt'))
            self.assertNotIn('add(com.appforge.rustjsi.RustJsiPackage())', app.read_text())
            self.assertIn('IMPORTED_NO_SONAME TRUE', (jni / 'CMakeLists.txt').read_text())
            self.assertNotIn('src/main/cpp/CMakeLists.txt', (project / 'android/app/build.gradle').read_text())

    def test_safety_and_workflow_contract(self):
        source = (HERE / 'RustRuntimeModule.h').read_text()
        # Strip comments so explanatory mentions do not hide an unsafe fallback.
        import re
        code = re.sub(r'//[^\n]*|/\*.*?\*/', '', source, flags=re.S)
        for forbidden in ['reinterpret_cast', 'jlong', 'JavaScriptContextHolder', '[this]', 'Runtime*']:
            self.assertNotIn(forbidden, code)
        workflow = (ROOT / '.github/workflows/v10-phase12-1-abi-link-candidate.yml').read_text()
        self.assertIn('contents: read', workflow)
        self.assertIn('persist-credentials: false', workflow)
        self.assertIn("github.ref == 'refs/heads/feat/v10-oss-unified-20261028'", workflow)
        self.assertEqual(len(re.findall(r'uses: [^\n]+@[0-9a-f]{40}\b', workflow)), 4)
        for forbidden in ['workflow_dispatch', 'upload-artifact', 'pull_request', 'assemble', 'bundleRelease']:
            self.assertNotIn(forbidden, workflow)
        runner = (ROOT / 'scripts/v10/phase12-abi-link-candidate.sh').read_text()
        self.assertIn('externalNativeBuildDebug', runner)
        self.assertIn('build --locked --release --target aarch64-linux-android', runner)
        self.assertIn('elf "$ndk/llvm-readelf" "$ndk/llvm-nm"', runner)
        self.assertIn('"-Wl,--no-undefined"', (HERE / 'contract.py').read_text())


if __name__ == '__main__':
    unittest.main()
