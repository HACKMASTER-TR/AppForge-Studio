import unittest,tempfile,pathlib,json,subprocess,sys,shutil
ROOT=pathlib.Path(__file__).resolve().parents[1];ASSETS=ROOT/'android-app/app/src/main/assets/device-build'
scope={'__name__':'rust_jsi_test','__file__':str(ASSETS/'rust-react-native.py')};exec(compile((ASSETS/'rust-react-native.py').read_bytes(),str(ASSETS/'rust-react-native.py'),'exec'),scope)
class RustJsiTests(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.root=pathlib.Path(self.tmp.name)/'source';shutil.copytree(ROOT/'examples/rust-expo-jsi',self.root)
 def tearDown(self):self.tmp.cleanup()
 def change(self,name,edit):
  p=self.root/name;j=json.loads(p.read_text());edit(j);p.write_text(json.dumps(j))
 def test_authored_binding(self):self.assertEqual(scope['config'](self.root)['library'],'appforge_jsi')
 def test_future_schema(self):self.change('appforge-rust-jsi.json',lambda j:j.update(schema=999));self.assertRaises(ValueError,scope['config'],self.root)
 def test_wrong_native_family(self):self.change('package.json',lambda j:j['dependencies'].update({'react-native':'0.82.0'}));self.assertRaises(ValueError,scope['config'],self.root)
 def test_unpinned_expo(self):self.change('package.json',lambda j:j['dependencies'].update(expo='^54.0.22'));self.assertRaises(ValueError,scope['config'],self.root)
 def test_bridgeless_not_mislabelled_supported(self):self.change('app.json',lambda j:j['expo'].update(newArchEnabled=True));self.assertRaises(ValueError,scope['config'],self.root)
 def test_missing_authored_registration(self):(self.root/'native/RustJsiPackage.java').unlink();self.assertRaises(ValueError,scope['config'],self.root)
 def test_symlink_bridge(self):
  p=self.root/'native/RustJsi.cpp';p.unlink();p.symlink_to(self.root/'Cargo.toml');self.assertRaises(ValueError,scope['config'],self.root)
 def test_wrong_api(self):self.change('appforge-rust-jsi.json',lambda j:j.update(minApi=21));self.assertRaises(ValueError,scope['config'],self.root)
 def test_no_implicit_source_execution(self):
  p=subprocess.run([sys.executable,'-I','-S',str(ASSETS/'rust-react-native.py'),str(self.root)],capture_output=True,text=True);self.assertNotEqual(p.returncode,0);self.assertIn('Explicit source execution consent',p.stderr)
if __name__=='__main__':unittest.main()
