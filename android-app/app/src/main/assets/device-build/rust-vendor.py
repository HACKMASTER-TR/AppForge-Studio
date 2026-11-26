#!/usr/bin/env python3
"""Lockfile-only, checksum verified Cargo directory-source cache adapter.
Never runs Cargo or project build scripts, never opens a network connection.
"""
import argparse,hashlib,json,os,pathlib,re,shutil,tarfile,tomllib

def sha(p):
 h=hashlib.sha256()
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
 return h.hexdigest()

def plan(root):
 script=pathlib.Path(__file__).with_name('rust-project.py')
 namespace={'__file__':str(script),'__name__':'trusted_rust_builder'}
 # Load the app-shipped source bytes explicitly: never reuse a project-poisoned .pyc.
 exec(compile(script.read_bytes(),str(script),'exec'),namespace)
 manifest=namespace['validate'](root);lock=tomllib.loads((root/'Cargo.lock').read_text())
 packages=lock.get('package',[])
 git_script=script.with_name('rust-git.py');git_scope={'__name__':'trusted_git','__file__':str(git_script)};exec(compile(git_script.read_bytes(),str(git_script),'exec'),git_scope)
 git_items=git_scope['pins'](root,lock)
 if len(packages)>2048:raise ValueError('Cargo package count exceeds 2048')
 layout_scope={'__name__':'trusted_layout','__file__':str(script.with_name('rust-layout.py'))}
 exec(compile(pathlib.Path(layout_scope['__file__']).read_bytes(),layout_scope['__file__'],'exec'),layout_scope)
 local_packages={(m['package']['name'],m['package']['version']) for m in layout_scope['resolve'](root)['packages'].values()}
 result={};local=set()
 for p in packages:
  if not p.get('source'):
   if (p.get('name'),p.get('version')) not in local_packages:raise ValueError('Unbound local Cargo package')
   identity=(p['name'],p['version'])
   if identity in local:raise ValueError('Duplicate locked local package')
   local.add(identity);continue
  if p['source'].startswith('git+'):continue
  name=p['name'];version=p['version'];checksum=p['checksum']
  if not re.fullmatch(r'[A-Za-z0-9_-]{1,64}',name) or len(version)>100 or not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+(?:[-+][A-Za-z0-9.-]+)?',version):raise ValueError('Invalid locked crate identity')
  key=name+'-'+version
  item=dict(name=name,version=version,sha256=checksum,url='https://static.crates.io/crates/'+name+'/'+key+'.crate')
  if key in result and result[key]!=item:raise ValueError('Conflicting Cargo crate identity')
  result[key]=item
 if (manifest['package']['name'],manifest['package']['version']) not in local:raise ValueError('Cargo.lock must bind selected package')
 return dict(schema=1,lockSha256=sha(root/'Cargo.lock'),artifacts=[result[k] for k in sorted(result)]+git_items)

def vendor_registry(root,cache,destination):
 items=[p for p in plan(root)['artifacts'] if p.get('kind')!='git-bundle']
 if destination.exists() or destination.is_symlink():raise ValueError('Vendor destination must be fresh')
 validated=[];required=0
 for item in items:
  archive=cache/(item['sha256']+'.crate')
  if archive.is_symlink() or archive.stat().st_size>64*1024*1024 or sha(archive)!=item['sha256']:raise ValueError('Crate SHA-256 or size mismatch')
  prefix=item['name']+'-'+item['version']
  with tarfile.open(archive,'r:gz') as t:
   members=t.getmembers()
   if len(members)>10000:raise ValueError('Crate file count limit')
   seen=set();crate_bytes=0
   for member in members:
    p=pathlib.PurePosixPath(member.name)
    if p.is_absolute() or '..' in p.parts or '\\' in member.name or not p.parts or p.parts[0]!=prefix or not (member.isdir() or member.isfile()) or p.as_posix() in seen or p.name=='.cargo-checksum.json':raise ValueError('Crate archive path/link/type rejected')
    seen.add(p.as_posix());crate_bytes+=member.size
   if crate_bytes>256*1024*1024:raise ValueError('Expanded crate size limit')
   required+=crate_bytes
  validated.append((archive,prefix,item['sha256']))
 if required>1024*1024*1024 or shutil.disk_usage(destination.parent).free<required+64*1024*1024:raise ValueError('Insufficient/bounded Cargo vendor staging space')
 destination.mkdir()
 for archive,prefix,checksum in validated:
  with tarfile.open(archive,'r:gz') as t:t.extractall(destination,filter='data')
  directory=destination/prefix
  if not directory.is_dir() or directory.is_symlink():raise ValueError('Missing crate root')
  files={p.relative_to(directory).as_posix():sha(p) for p in directory.rglob('*') if p.is_file()}
  with (directory/'.cargo-checksum.json').open('w') as f:json.dump(dict(files=files,package=checksum),f,sort_keys=True);f.flush();os.fsync(f.fileno())
 return dict(schema=1,packages=len(validated),lockSha256=sha(root/'Cargo.lock'),state='VENDOR_VERIFIED_NOT_OFFLINE_BUILD_READY')
def verify_registry(root,cache,directory):
 items=[p for p in plan(root)['artifacts'] if p.get('kind')!='git-bundle']
 if directory.is_symlink() or not directory.is_dir():raise ValueError('Unsafe vendor directory')
 names={item['name']+'-'+item['version'] for item in items}
 if {p.name for p in directory.iterdir()}!=names:raise ValueError('Unexpected vendor package')
 for item in items:
  archive=cache/(item['sha256']+'.crate');prefix=item['name']+'-'+item['version'];crate=directory/prefix
  if archive.is_symlink() or sha(archive)!=item['sha256'] or crate.is_symlink():raise ValueError('Vendor archive identity changed')
  expected={}
  with tarfile.open(archive,'r:gz') as t:
   members=t.getmembers()
   if len(members)>10000 or sum(m.size for m in members)>256*1024*1024:raise ValueError('Vendor archive verification limit')
   for member in members:
    path=pathlib.PurePosixPath(member.name)
    if path.is_absolute() or '..' in path.parts or not path.parts or path.parts[0]!=prefix or not (member.isdir() or member.isfile()):raise ValueError('Invalid vendor archive')
    if member.isfile():
     key=path.relative_to(prefix).as_posix()
     if key in expected:raise ValueError('Duplicate vendor path')
     h=hashlib.sha256()
     with t.extractfile(member) as stream:
      for b in iter(lambda:stream.read(1024*1024),b''):h.update(b)
     expected[key]=h.hexdigest()
  actual={}
  for path in crate.rglob('*'):
   if path.is_symlink():raise ValueError('Vendor symlink')
   if path.is_file() and path.name!='.cargo-checksum.json':actual[path.relative_to(crate).as_posix()]=sha(path)
  metadata=crate/'.cargo-checksum.json'
  if metadata.is_symlink() or metadata.stat().st_size>2*1024*1024 or actual!=expected or json.loads(metadata.read_text())!=dict(files=expected,package=item['sha256']):raise ValueError('Vendor content/checksum metadata changed')
 return dict(schema=1,packages=len(items),state='VENDOR_REVERIFIED_NOT_ANDROID_ACCEPTED')
def git_tool():
 p=pathlib.Path(__file__).with_name('rust-git.py');s={'__name__':'trusted_git','__file__':str(p)};exec(compile(p.read_bytes(),str(p),'exec'),s);return s

def vendor(root,cache,destination,prefix=pathlib.Path('/opt/appforge-device/rust-1.85.1'),metadata_consent=False):
 if any(p.get('kind')=='git-bundle' for p in plan(root)['artifacts']):return git_tool()['prepare'](root,cache,destination,vendor_registry,prefix,metadata_consent)
 return vendor_registry(root,cache,destination)

def verify(root,cache,directory,prefix=pathlib.Path('/opt/appforge-device/rust-1.85.1'),metadata_consent=False):
 if not any(p.get('kind')=='git-bundle' for p in plan(root)['artifacts']):return verify_registry(root,cache,directory)
 if directory.is_symlink() or not directory.is_dir():raise ValueError('Unsafe Git vendor directory')
 import tempfile
 with tempfile.TemporaryDirectory(prefix='git-reverify-',dir=directory.parent) as name:
  expected=pathlib.Path(name)/'vendor';vendor(root,cache,expected,prefix,metadata_consent)
  def inventory(folder):
   result={};size=0
   for p in folder.rglob('*'):
    if p.is_symlink():raise ValueError('Git vendor symlink rejected')
    if p.is_file():
     size+=p.stat().st_size
     if size>1024*1024*1024 or len(result)>=100000:raise ValueError('Git vendor inventory bound')
     result[p.relative_to(folder).as_posix()]=sha(p)
   return result
  if inventory(expected)!=inventory(directory):raise ValueError('Git vendor bytes/metadata changed')
 return dict(schema=1,state='GIT_VENDOR_REVERIFIED_NOT_OFFLINE_BUILD_READY')
if __name__=='__main__':
 ap=argparse.ArgumentParser();ap.add_argument('action',choices=['plan','vendor','verify']);ap.add_argument('project');ap.add_argument('--output',required=True);ap.add_argument('--cache');ap.add_argument('--prefix',default='/opt/appforge-device/rust-1.85.1');ap.add_argument('--offline-metadata-consent',action='store_true');a=ap.parse_args()
 root=pathlib.Path(a.project).absolute();output=pathlib.Path(a.output)
 if a.action=='plan':
  if output.exists() or output.is_symlink():raise ValueError('Plan output exists')
  with output.open('w') as f:json.dump(plan(root),f);f.flush();os.fsync(f.fileno())
 elif a.action=='vendor':print(json.dumps(vendor(root,pathlib.Path(a.cache),output,pathlib.Path(a.prefix),a.offline_metadata_consent)))
 else:print(json.dumps(verify(root,pathlib.Path(a.cache),output,pathlib.Path(a.prefix),a.offline_metadata_consent)))
