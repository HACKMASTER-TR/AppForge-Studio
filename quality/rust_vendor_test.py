import pathlib,tempfile,unittest,importlib.util,sys,hashlib,tarfile,io,json
from unittest.mock import patch
sys.dont_write_bytecode=True
BASE=pathlib.Path(__file__).resolve().parents[1]/'android-app/app/src/main/assets/device-build'
spec=importlib.util.spec_from_file_location('rust_vendor',BASE/'rust-vendor.py');vendor=importlib.util.module_from_spec(spec);spec.loader.exec_module(vendor)
class RustVendorTests(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.root=pathlib.Path(self.tmp.name);self.src=self.root/'source';(self.src/'src').mkdir(parents=True);self.cache=self.root/'cache';self.cache.mkdir()
  (self.src/'Cargo.toml').write_text('[package]\nname="demo"\nversion="0.1.0"\n[dependencies]\none="=0.1.0"\n');(self.src/'src/lib.rs').write_text('pub fn demo() {}')
  self.make_archive()
 def tearDown(self):self.tmp.cleanup()
 def lock(self,checksum,source='registry+https://github.com/rust-lang/crates.io-index'):
  (self.src/'Cargo.lock').write_text('version=4\n[[package]]\nname="demo"\nversion="0.1.0"\n[[package]]\nname="one"\nversion="0.1.0"\nsource='+json.dumps(source)+'\nchecksum='+json.dumps(checksum)+'\n')
 def make_archive(self,kind='regular'):
  f=self.root/'temp.crate'
  with tarfile.open(f,'w:gz') as t:
   for name,data in [('one-0.1.0/Cargo.toml',b'[package]\nname="one"\nversion="0.1.0"'),('one-0.1.0/src/lib.rs',b'pub fn one() {}')]:
    m=tarfile.TarInfo(name);m.size=len(data);t.addfile(m,io.BytesIO(data))
   if kind!='regular':
    m=tarfile.TarInfo('one-0.1.0/../escape' if kind=='traversal' else 'one-0.1.0/escape');m.size=1
    if kind=='symlink':m.type=tarfile.SYMTYPE;m.linkname='/etc/passwd';m.size=0;t.addfile(m)
    else:t.addfile(m,io.BytesIO(b'x'))
  checksum=hashlib.sha256(f.read_bytes()).hexdigest();target=self.cache/(checksum+'.crate');f.replace(target);self.lock(checksum);return target
 def test_plan_has_only_fixed_public_registry_urls(self):
  p=vendor.plan(self.src);self.assertEqual(1,len(p['artifacts']));self.assertEqual('https://static.crates.io/crates/one/one-0.1.0.crate',p['artifacts'][0]['url'])
 def test_cargo_directory_checksum_manifest_matches_every_file(self):
  dest=self.root/'vendor';result=vendor.vendor(self.src,self.cache,dest);self.assertEqual(1,result['packages']);self.assertNotIn('READY',result['state'].replace('NOT_OFFLINE_BUILD_READY',''))
  directory=dest/'one-0.1.0';data=json.loads((directory/'.cargo-checksum.json').read_text());self.assertEqual(2,len(data['files']))
  for name,digest in data['files'].items():self.assertEqual(digest,hashlib.sha256((directory/name).read_bytes()).hexdigest())
 def test_missing_cache_rejected(self):
  for f in self.cache.iterdir():f.unlink()
  with self.assertRaises(FileNotFoundError):vendor.vendor(self.src,self.cache,self.root/'vendor')
 def test_bad_sha_rejected_before_extract(self):
  next(self.cache.iterdir()).write_text('corrupt')
  with self.assertRaisesRegex(ValueError,'SHA-256'):vendor.vendor(self.src,self.cache,self.root/'vendor')
  self.assertFalse((self.root/'vendor').exists())
 def test_traversal_rejected_even_when_archive_hash_matches_lock(self):
  self.make_archive('traversal')
  with self.assertRaisesRegex(ValueError,'path/link/type'):vendor.vendor(self.src,self.cache,self.root/'vendor')
 def test_symlink_rejected_even_when_archive_hash_matches_lock(self):
  self.make_archive('symlink')
  with self.assertRaises(ValueError):vendor.vendor(self.src,self.cache,self.root/'vendor')
 def test_existing_vendor_and_user_files_preserved(self):
  dest=self.root/'vendor';dest.mkdir();(dest/'foreign').write_text('keep')
  with self.assertRaises(ValueError):vendor.vendor(self.src,self.cache,dest)
  self.assertEqual('keep',(dest/'foreign').read_text())
 def test_wrong_registry_rejected(self):
  self.lock('a'*64,'registry+https://untrusted.invalid/index')
  with self.assertRaises(ValueError):vendor.plan(self.src)
 def test_unbound_local_package_rejected(self):
  (self.src/'Cargo.lock').write_text('version=4\n[[package]]\nname="outside"\nversion="0.1.0"\n')
  with self.assertRaisesRegex(ValueError,'Unbound'):vendor.plan(self.src)
 def test_disk_full_does_not_create_vendor_directory(self):
  class Usage:free=0
  with patch.object(vendor.shutil,'disk_usage',return_value=Usage()):
   with self.assertRaisesRegex(ValueError,'space'):vendor.vendor(self.src,self.cache,self.root/'vendor')
  self.assertFalse((self.root/'vendor').exists())
 def test_reverification_accepts_exact_vendor(self):
  dest=self.root/'vendor';vendor.vendor(self.src,self.cache,dest);self.assertEqual(1,vendor.verify(self.src,self.cache,dest)['packages'])
 def test_reverification_rejects_source_and_metadata_forgery(self):
  dest=self.root/'vendor';vendor.vendor(self.src,self.cache,dest);crate=dest/'one-0.1.0';path=crate/'src/lib.rs';path.write_text('tampered')
  metadata=crate/'.cargo-checksum.json';m=json.loads(metadata.read_text());m['files']['src/lib.rs']=vendor.sha(path);metadata.write_text(json.dumps(m))
  with self.assertRaisesRegex(ValueError,'changed'):vendor.verify(self.src,self.cache,dest)
 def test_reverification_rejects_extra_package(self):
  dest=self.root/'vendor';vendor.vendor(self.src,self.cache,dest);(dest/'foreign').mkdir()
  with self.assertRaises(ValueError):vendor.verify(self.src,self.cache,dest)
if __name__=='__main__':unittest.main()
