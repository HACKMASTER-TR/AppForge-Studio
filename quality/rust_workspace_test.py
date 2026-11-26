import unittest,pathlib,tempfile,shutil,json
ROOT=pathlib.Path(__file__).resolve().parents[1];A=ROOT/'android-app/app/src/main/assets/device-build'
S={'__name__':'test_layout','__file__':str(A/'rust-layout.py')};exec(compile((A/'rust-layout.py').read_bytes(),S['__file__'],'exec'),S)
V={'__name__':'test_vendor','__file__':str(A/'rust-vendor.py')};exec(compile((A/'rust-vendor.py').read_bytes(),V['__file__'],'exec'),V)
class LayoutTests(unittest.TestCase):
 def setUp(self):self.t=tempfile.TemporaryDirectory();self.root=pathlib.Path(self.t.name)/'source';shutil.copytree(ROOT/'examples/rust-workspace',self.root)
 def tearDown(self):self.t.cleanup()
 def test_selected_package_and_inherited_dependency(self):
  j=S['resolve'](self.root);self.assertEqual(j['manifest']['package']['version'],'0.1.0');self.assertEqual(j['directory'],self.root/'crates/web');self.assertEqual(len(j['packages']),2);self.assertEqual(j['manifest']['dependencies']['appforge-common']['path'],'crates/common')
 def test_plan_binds_all_local_packages(self):self.assertEqual(V['plan'](self.root)['artifacts'],[])
 def test_virtual_workspace_requires_selection(self):(self.root/'appforge-rust-project.json').unlink();self.assertRaisesRegex(ValueError,'selection',S['resolve'],self.root)
 def test_unknown_selected_package(self):(self.root/'appforge-rust-project.json').write_text('{"schema":1,"package":"unknown"}');self.assertRaises(ValueError,S['resolve'],self.root)
 def test_future_schema(self):(self.root/'appforge-rust-project.json').write_text('{"schema":2,"package":"appforge-workspace-web"}');self.assertRaises(ValueError,S['resolve'],self.root)
 def test_selector_symlink(self):p=self.root/'appforge-rust-project.json';p.unlink();p.symlink_to(self.root/'Cargo.toml');self.assertRaises(ValueError,S['resolve'],self.root)
 def test_member_escape(self):(self.root/'Cargo.toml').write_text('[workspace]\nmembers=["../outside"]');self.assertRaises(ValueError,S['resolve'],self.root)
 def test_member_symlink(self):p=self.root/'crates/other';p.symlink_to(self.root/'crates/common',target_is_directory=True);self.assertRaises(ValueError,S['resolve'],self.root)
 def test_path_dependency_escape(self):(self.root/'crates/web/Cargo.toml').write_text('[package]\nname="web"\nversion="0.1.0"\n[dependencies]\nevil={path="../../../outside"}');self.assertRaises(ValueError,S['resolve'],self.root)
 def test_unbound_lock_package(self):p=self.root/'Cargo.lock';p.write_text(p.read_text()+'\n[[package]]\nname="unbound"\nversion="1.0.0"\n');self.assertRaises(ValueError,V['plan'],self.root)
 def test_duplicate_name(self):p=self.root/'crates/web/Cargo.toml';p.write_text(p.read_text().replace('appforge-workspace-web','appforge-common'));self.assertRaises(ValueError,S['resolve'],self.root)
 def test_custom_source_cannot_escape(self):p=self.root/'crates/web/Cargo.toml';p.write_text(p.read_text().replace('[lib]','[lib]\npath="../../../../outside.rs"'));self.assertRaises(ValueError,S['resolve'],self.root)
if __name__=='__main__':unittest.main()
