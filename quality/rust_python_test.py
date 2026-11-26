import unittest,tempfile,pathlib,json,subprocess,sys
ASSETS=pathlib.Path(__file__).resolve().parents[1]/'android-app/app/src/main/assets/device-build'
scope={'__name__':'rust_python_test','__file__':str(ASSETS/'rust-python.py')};exec(compile((ASSETS/'rust-python.py').read_bytes(),str(ASSETS/'rust-python.py'),'exec'),scope)
class PythonRustTests(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.root=pathlib.Path(self.tmp.name);(self.root/'src').mkdir();(self.root/'src/lib.rs').write_text('//fixture')
  self.manifest='[package]\nname="native"\nversion="0.1.0"\n[lib]\ncrate-type=["cdylib"]\n[dependencies]\npyo3={version="=0.23.5",features=["extension-module","abi3-py312"]}\n'
  (self.root/'Cargo.toml').write_text(self.manifest);(self.root/'Cargo.lock').write_text('version=4\n');self.config=dict(schema=1,module='native',pythonVersion='3.12',minApi=26);self.save()
 def save(self):(self.root/'appforge-rust-python.json').write_text(json.dumps(self.config))
 def tearDown(self):self.tmp.cleanup()
 def test_explicit_pyo3_binding(self):self.assertEqual(scope['config'](self.root),'native')
 def test_python_version_mismatch(self):self.config['pythonVersion']='3.13';self.save();self.assertRaises(ValueError,scope['config'],self.root)
 def test_traversal_module(self):self.config['module']='../native';self.save();self.assertRaises(ValueError,scope['config'],self.root)
 def test_future_schema(self):self.config['schema']=999;self.save();self.assertRaises(ValueError,scope['config'],self.root)
 def test_c_abi_without_pyo3_rejected(self):(self.root/'Cargo.toml').write_text(self.manifest.split('[dependencies]')[0]);self.assertRaises(ValueError,scope['config'],self.root)
 def test_unpinned_pyo3_rejected(self):(self.root/'Cargo.toml').write_text(self.manifest.replace('=0.23.5','0.23'));self.assertRaises(ValueError,scope['config'],self.root)
 def test_wrong_python_abi_feature(self):(self.root/'Cargo.toml').write_text(self.manifest.replace('abi3-py312','abi3-py39'));self.assertRaises(ValueError,scope['config'],self.root)
 def test_symlink_config(self):p=self.root/'appforge-rust-python.json';p.unlink();p.symlink_to(self.root/'Cargo.toml');self.assertRaises(ValueError,scope['config'],self.root)
 def test_corrupt_target_zip_rejected(self):
  j=json.loads((ASSETS/'rust-python-target-v1.json').read_text());(self.root/j['artifact']['file']).write_bytes(b'bad');self.assertRaisesRegex(ValueError,'SHA-256',scope['python_library'],self.root,self.root);self.assertFalse((self.root/'target').exists())
 def test_no_implicit_source_execution(self):
  p=subprocess.run([sys.executable,'-I','-S',str(ASSETS/'rust-python.py'),str(self.root)],capture_output=True,text=True);self.assertNotEqual(p.returncode,0);self.assertIn('Explicit source execution consent',p.stderr)
if __name__=='__main__':unittest.main()
