import importlib.util
import pathlib
import tempfile
import unittest
from unittest import mock

ROOT=pathlib.Path(__file__).resolve().parents[1]
BUILDER=ROOT/'android-app/app/src/main/assets/device-build/rust-project.py'
INSTALLER=ROOT/'android-app/app/src/main/assets/device-build/install-rust-module.py'
spec=importlib.util.spec_from_file_location('appforge_rust_installer_probe',INSTALLER)
installer=importlib.util.module_from_spec(spec)
spec.loader.exec_module(installer)

class RustOfflineProbeRuntimeTest(unittest.TestCase):
    def test_cargo_uses_explicit_compiler_path_with_no_inherited_rustc(self):
        with tempfile.TemporaryDirectory(prefix='appforge-rust-path-') as directory:
            prefix=pathlib.Path(directory)/'rust-1.85.1'
            (prefix/'bin').mkdir(parents=True)
            rustc=prefix/'bin/rustc'
            cargo=prefix/'bin/cargo'
            rustc.write_text('test fixture')
            cargo.write_text('test fixture')
            invocations=[]
            def check_output(cmd, text=False):
                self.assertTrue(text)
                if cmd==[str(rustc),'--version']:return 'rustc 1.85.1 (test)\n'
                if cmd==[str(cargo),'--version']:return 'cargo 1.85.1 (test)\n'
                if cmd==[str(rustc),'-vV']:
                    return 'rustc 1.85.1 (test)\nhost: aarch64-unknown-linux-gnu\n'
                raise AssertionError('Unexpected compiler command: '+repr(cmd))
            def run(cmd,cwd=None,env=None,check=False):
                invocations.append(list(cmd))
                self.assertTrue(check)
                self.assertEqual(str(rustc),env.get('RUSTC'))
                self.assertTrue(env.get('CARGO_NET_OFFLINE')=='true')
                self.assertTrue(env.get('PATH').startswith(str(prefix/'bin')+':'))
                if 'generate-lockfile' in cmd:
                    (pathlib.Path(cwd)/'Cargo.lock').write_text('version = 4\n')
                elif 'build' in cmd:
                    artifact=pathlib.Path(cwd)/'target/wasm32-unknown-unknown/release/appforge_probe.wasm'
                    artifact.parent.mkdir(parents=True,exist_ok=True)
                    artifact.write_bytes(b'\0asm\1\0\0\0')
                else:raise AssertionError('Unexpected Cargo invocation: '+repr(cmd))
                return subprocess_completed(cmd)
            with mock.patch.object(installer.subprocess,'check_output',side_effect=check_output), \
                 mock.patch.object(installer.subprocess,'run',side_effect=run):
                result=installer.probe(prefix,'aarch64-unknown-linux-gnu')
            self.assertEqual(True,result['offlineReplay'])
            self.assertEqual(3,len(invocations))
            self.assertEqual(2,sum('build' in x for x in invocations))

def subprocess_completed(cmd):
    return {'args':cmd,'returncode':0}

class RustProjectCompilerEnvironmentTest(unittest.TestCase):
    def test_project_builder_also_pins_compiler_instead_of_searching_path(self):
        import ast
        import os
        with tempfile.TemporaryDirectory(prefix='appforge-rust-project-env-') as d:
            root=pathlib.Path(d)/'src'
            prefix=pathlib.Path(d)/'rust-1.85.1'
            function=next(n for n in ast.parse(BUILDER.read_text()).body if isinstance(n,ast.FunctionDef) and n.name=='build')
            statement=next(n for n in function.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='env' for t in n.targets))
            expression=ast.Module(body=[statement],type_ignores=[])
            values={}
            exec(compile(expression,str(BUILDER),'exec'),{'os':os,'prefix':prefix,'root':root},values)
            self.assertEqual(str(prefix/'bin/rustc'),values['env']['RUSTC'])
            self.assertEqual('true',values['env']['CARGO_NET_OFFLINE'])

if __name__=='__main__':unittest.main()
