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
class RustTurboTests(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.root=pathlib.Path(self.tmp.name)/'source';shutil.copytree(ROOT/'examples/rust-expo-turbo',self.root)
 def tearDown(self):self.tmp.cleanup()
 def test_explicit_turbo_config(self):self.assertEqual(scope['config'](self.root)['bridgeLibrary'],'appmodules')
 def test_turbo_needs_authored_provider(self):(self.root/'native/OnLoad.cpp').unlink();self.assertRaises(ValueError,scope['config'],self.root)
 def test_turbo_flag_must_match(self):
  p=self.root/'app.json';j=json.loads(p.read_text());j['expo']['newArchEnabled']=False;p.write_text(json.dumps(j));self.assertRaises(ValueError,scope['config'],self.root)
 def test_legacy_cannot_masquerade_as_turbo(self):
  p=self.root/'appforge-rust-jsi.json';j=json.loads(p.read_text());j['bridgeLibrary']='appforge_rust_jsi';p.write_text(json.dumps(j));self.assertRaises(ValueError,scope['config'],self.root)
 def test_standalone_has_authored_android_and_no_expo(self):
  shutil.rmtree(self.root);shutil.copytree(ROOT/'examples/rust-react-native-turbo',self.root);self.assertEqual(scope['config'](self.root)['expoSdk'],0);self.assertNotIn('expo',json.loads((self.root/'package.json').read_text())['dependencies'])
 def test_standalone_architecture_flag_cannot_be_ambiguous(self):
  shutil.rmtree(self.root);shutil.copytree(ROOT/'examples/rust-react-native-turbo',self.root);p=self.root/'android/gradle.properties';p.write_text(p.read_text()+'\nnewArchEnabled=false\n');self.assertRaises(ValueError,scope['config'],self.root)
 def test_standalone_rejects_unprepared_abis_before_build(self):
  shutil.rmtree(self.root);shutil.copytree(ROOT/'examples/rust-react-native-turbo',self.root);p=self.root/'android/gradle.properties';p.write_text(p.read_text().replace('reactNativeArchitectures=arm64-v8a','reactNativeArchitectures=arm64-v8a,armeabi-v7a'));self.assertRaisesRegex(ValueError,'exactly arm64-v8a',scope['config'],self.root)
if __name__=='__main__':unittest.main()
