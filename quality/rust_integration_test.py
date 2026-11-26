import importlib.util,pathlib,tempfile,unittest,tarfile,io,sys
sys.dont_write_bytecode=True
BASE=pathlib.Path(__file__).resolve().parents[1]/'android-app/app/src/main/assets/device-build'
def load(name,file):
 s=importlib.util.spec_from_file_location(name,BASE/file);m=importlib.util.module_from_spec(s);s.loader.exec_module(m);return m
builder=load('rust_builder','rust-project.py');installer=load('rust_installer','install-rust-module.py')
class RustSafetyTests(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory();self.root=pathlib.Path(self.temp.name);(self.root/'src').mkdir();(self.root/'src/lib.rs').write_text('pub fn demo() {}');(self.root/'Cargo.toml').write_text('[package]\nname="demo"\nversion="0.1.0"\n');(self.root/'Cargo.lock').write_text('version=4\n')
 def tearDown(self):self.temp.cleanup()
 def test_consent_precedes_execution(self):
  with self.assertRaisesRegex(ValueError,'consent'):builder.build(self.root,'wasm32-unknown-unknown',self.root)
 def test_missing_lock_rejected(self):
  (self.root/'Cargo.lock').unlink()
  with self.assertRaises(FileNotFoundError):builder.validate(self.root)
 def test_git_source_rejected(self):
  (self.root/'Cargo.lock').write_text('version=4\n[[package]]\nname="other"\nsource="git+https://example.invalid/other"\n')
  with self.assertRaisesRegex(ValueError,'checksum-locked'):builder.validate(self.root)
 def test_bad_checksum_rejected(self):
  (self.root/'Cargo.lock').write_text('version=4\n[[package]]\nname="other"\nsource="registry+https://github.com/rust-lang/crates.io-index"\nchecksum="invalid"\n')
  with self.assertRaises(ValueError):builder.validate(self.root)
 def test_symlink_rejected(self):
  (self.root/'src/link.rs').symlink_to(self.root/'Cargo.lock')
  with self.assertRaisesRegex(ValueError,'symlink'):builder.validate(self.root)
 def test_source_hash_changes(self):
  before=builder.source_digest(self.root);(self.root/'build.rs').write_text('fn main() {}');self.assertNotEqual(before,builder.source_digest(self.root))
 def test_existing_target_symlink_cannot_redirect_output(self):
  foreign=self.root/'foreign';foreign.mkdir();(foreign/'keep').write_text('user data');(self.root/'target').symlink_to(foreign,target_is_directory=True)
  with self.assertRaisesRegex(ValueError,'target directory'):builder.build(self.root,'wasm32-unknown-unknown',self.root,True)
  self.assertEqual((foreign/'keep').read_text(),'user data')
 def test_unknown_target_rejected(self):
  with self.assertRaisesRegex(ValueError,'Unsupported'):builder.build(self.root,'untrusted',self.root,True)
 def test_native_linker_required(self):
  with self.assertRaisesRegex(ValueError,'linker'):builder.build(self.root,'aarch64-linux-android',self.root,True)
 def test_export_preserves_existing_data(self):
  (self.root/'appforge-rust').mkdir();(self.root/'appforge-rust/user').write_text('keep')
  with self.assertRaises(ValueError):builder.export_wasm(self.root,{'artifacts':[{'target':'wasm32-unknown-unknown','path':'demo.wasm'}]})
  self.assertEqual('keep',(self.root/'appforge-rust/user').read_text())
 def archive(self,name,link=False):
  p=self.root/'test.tar.xz'
  with tarfile.open(p,'w:xz') as t:
   m=tarfile.TarInfo(name)
   if link:m.type=tarfile.SYMTYPE;m.linkname='/etc/passwd';t.addfile(m)
   else:m.size=1;t.addfile(m,io.BytesIO(b'x'))
  return p
 def test_traversal_archive_rejected(self):
  with self.assertRaises(ValueError):installer.extract(self.archive('../escape'),self.root/'out')
 def test_archive_symlink_rejected(self):
  with self.assertRaises(ValueError):installer.extract(self.archive('link',True),self.root/'out')
 def test_absolute_archive_rejected(self):
  with self.assertRaises(ValueError):installer.extract(self.archive('/escape'),self.root/'out')
if __name__=='__main__':unittest.main()
