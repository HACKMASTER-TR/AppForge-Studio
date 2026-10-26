import pathlib,tempfile,unittest,json,os
from unittest.mock import patch
ROOT=pathlib.Path(__file__).resolve().parents[1];A=ROOT/'android-app/app/src/main/assets/device-build';S={'__name__':'test_git','__file__':str(A/'rust-git.py')};exec(compile((A/'rust-git.py').read_bytes(),S['__file__'],'exec'),S)
class GitPolicyTests(unittest.TestCase):
 def setUp(self):
  self.t=tempfile.TemporaryDirectory();self.root=pathlib.Path(self.t.name);self.source='git+https://example.invalid/repo?rev='+('a'*40)+'#'+('a'*40);self.lock={'package':[{'source':self.source}]};self.pin={'source':self.source,'url':'https://example.invalid/repo.bundle','sha256':'b'*64};self.save()
 def tearDown(self):self.t.cleanup()
 def save(self):(self.root/'appforge-rust-sources.json').write_text(json.dumps({'schema':1,'sources':[self.pin]}))
 def test_exact_lock_and_bundle_pin(self):self.assertEqual(S['pins'](self.root,self.lock)[0]['version'],'a'*40)
 def test_no_manifest_cannot_enable_git(self):(self.root/'appforge-rust-sources.json').unlink();self.assertRaises(ValueError,S['pins'],self.root,self.lock)
 def test_hash_required(self):self.pin['sha256']='wrong';self.save();self.assertRaises(ValueError,S['pins'],self.root,self.lock)
 def test_source_credentials_rejected(self):self.assertRaises(ValueError,S['source'],self.source.replace('example.invalid','token@example.invalid'))
 def test_url_query_token_not_persisted(self):self.pin['url']+='?token=example';self.save();self.assertRaises(ValueError,S['pins'],self.root,self.lock)
 def test_file_git_protocol_rejected(self):self.assertRaises(ValueError,S['source'],'git+file:///tmp/repo#'+'a'*40)
 def test_unpinned_branch_rejected(self):self.assertRaises(ValueError,S['source'],'git+https://example.invalid/repo#main')
 def test_unknown_source_rejected(self):self.lock['package'][0]['source']=self.source.replace('a'*40,'c'*40);self.assertRaises(ValueError,S['pins'],self.root,self.lock)
 def test_unknown_schema_rejected(self):(self.root/'appforge-rust-sources.json').write_text('{"schema":99,"sources":[]}');self.assertRaises(ValueError,S['pins'],self.root,self.lock)
 def test_symlink_manifest_rejected(self):p=self.root/'appforge-rust-sources.json';p.unlink();p.symlink_to(self.root/'other');self.assertRaises(ValueError,S['pins'],self.root,self.lock)
 def test_metadata_needs_separate_consent(self):self.assertRaisesRegex(ValueError,'consent',S['prepare'],self.root,self.root,self.root/'vendor',None,self.root,False)
 def test_git_environment_cannot_inherit_transport_override(self):
  with patch.dict(os.environ,{'GIT_CONFIG_COUNT':'1','GIT_CONFIG_KEY_0':'protocol.https.allow','GIT_CONFIG_VALUE_0':'always','GIT_EXEC_PATH':'/project/bin','CARGO_NET_GIT_FETCH_WITH_CLI':'false'}):
   e=S['environment'](self.root,[]);self.assertNotIn('GIT_EXEC_PATH',e);d={e['GIT_CONFIG_KEY_'+str(i)]:e['GIT_CONFIG_VALUE_'+str(i)] for i in range(int(e['GIT_CONFIG_COUNT']))};self.assertEqual(d['protocol.https.allow'],'never');self.assertEqual(d['core.hooksPath'],'/dev/null');self.assertEqual(e['CARGO_NET_GIT_FETCH_WITH_CLI'],'true')
 def test_local_bundle_pin_is_verified_even_if_cache_could_exist(self):
  import hashlib
  f=self.root/'dependency.bundle';f.write_bytes(b'fixture');self.pin.pop('url');self.pin.update(path=f.name,sha256=hashlib.sha256(f.read_bytes()).hexdigest());self.save();self.assertEqual(S['pins'](self.root,self.lock)[0]['localPath'],f.name)
  f.write_bytes(b'changed');self.assertRaises(ValueError,S['pins'],self.root,self.lock)
 def test_local_bundle_cannot_escape(self):
  self.pin.pop('url');self.pin['path']='../dependency.bundle';self.save();self.assertRaises(ValueError,S['pins'],self.root,self.lock)
 def test_local_bundle_symlink_rejected(self):
  target=self.root/'real';target.write_bytes(b'fixture');(self.root/'dependency.bundle').symlink_to(target);self.pin.pop('url');self.pin['path']='dependency.bundle';self.save();self.assertRaises(ValueError,S['pins'],self.root,self.lock)
 def test_export_ignore_cannot_hide_unsafe_manifest(self):
  tree=self.root/'tree';tree.mkdir();(tree/'Cargo.toml').write_text('[package]\nname="bad"\nversion="0.1.0"\n[dependencies]\nevil={git="https://unreviewed.invalid/repo"}\n');(tree/'.gitattributes').write_text('Cargo.toml export-ignore\n');self.assertRaises(ValueError,S['validate_tree'],tree,set())
 def test_merged_locked_revision_is_reachable_to_cargo_fetch_heads(self):
  import subprocess
  original=self.root/'original';original.mkdir()
  def git(*args):return subprocess.check_output(['git',*args],cwd=original,stderr=subprocess.DEVNULL,text=True).strip()
  git('init','-q');f=original/'source';f.write_text('first');git('add','.');git('-c','user.name=Fixture','-c','user.email=fixture@example.invalid','commit','-qm','first')
  private=self.root/'private.git';git('clone','--bare',str(original),str(private))
  f.write_text('second');git('add','.');git('-c','user.name=Fixture','-c','user.email=fixture@example.invalid','commit','-qm','second');commit=git('rev-parse','HEAD')
  S['merge_locked_revision'](private,original,commit,S['environment'](self.root/'home',[]))
  heads=git('ls-remote','--heads',str(private));self.assertIn(commit+'\trefs/heads/appforge-locked/'+commit,heads)
  self.assertNotIn('refs/heads/appforge-locked/',git('show-ref'))
if __name__=='__main__':unittest.main()
