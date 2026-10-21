#!/usr/bin/env python3
"""Explicit-consent, offline Cargo artifact builder for reviewed workspace copies.
Output preparation is separate from app packaging; never downloads or injects JS.
"""
import argparse,hashlib,json,os,pathlib,subprocess,tomllib,shutil
TARGETS={'wasm32-unknown-unknown':'wasm','aarch64-linux-android':'elf','x86_64-pc-windows-gnu':'pe'}
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def input_files(root):
    ignored={'target','node_modules','.git','.gradle','.expo','.next','appforge-rust'}
    files=[];size=0
    if not (root/'src').is_dir():raise ValueError('Rust src directory missing')
    def scan_error(error):raise error
    for current,dirs,names in os.walk(root,followlinks=False,onerror=scan_error):
        current=pathlib.Path(current)
        dirs[:]=[name for name in dirs if name!='.git' and not (current==root and name in ignored)]
        for name in dirs:
            if (current/name).is_symlink():raise ValueError('Rust source symlink rejected')
        for name in names:
            path=current/name
            if path.is_symlink() or not path.is_file() or not path.resolve().is_relative_to(root):raise ValueError('Unsafe Rust source file/symlink')
            size+=path.stat().st_size;files.append(path)
            if len(files)>20000 or size>512*1024*1024:raise ValueError('Rust source input limit exceeded')
    return files

def source_digest(root):
    h=hashlib.sha256()
    for p in sorted(input_files(root),key=lambda p:p.relative_to(root).as_posix().encode('utf-16-be')):
        h.update(p.relative_to(root).as_posix().encode()+b'\0'+digest(p).encode()+b'\n')
    return h.hexdigest()
def export_wasm(root,result):
    if len(result['artifacts'])!=1 or result['artifacts'][0]['target']!='wasm32-unknown-unknown':raise ValueError('Exactly one WASM artifact required')
    item=result['artifacts'][0];src=root/item['path'];dest=root/'appforge-rust'
    if dest.is_symlink() or dest.exists():raise ValueError('Export directory exists; preserve old artifact and choose a fresh reviewed copy')
    dest.mkdir();shutil.copyfile(src,dest/src.name)
    receipt=dict(schema=2,target=item['target'],sourceSha256=source_digest(root),file=src.name,sha256=item['sha256'])
    with (dest/'artifacts.json').open('w') as f:json.dump(receipt,f);f.flush();os.fsync(f.fileno())
    return receipt

def validate(root):
    if root.is_symlink() or root.resolve()!=root:raise ValueError('Untrusted Rust project path')
    input_files(root)
    if any((root/name).stat().st_size>2*1024*1024 for name in ['Cargo.toml','Cargo.lock']):raise ValueError('Rust manifest/lock limit')
    manifest=tomllib.loads((root/'Cargo.toml').read_text());lock=tomllib.loads((root/'Cargo.lock').read_text())
    if not isinstance(manifest.get('package'),dict) or 'workspace' in manifest or manifest['package'].get('workspace'):
        raise ValueError('Cargo workspaces require a separate reviewed dependency resolver')
    def reject_external(value):
        if isinstance(value,dict):
            if any(k in value for k in ['path','git','workspace']):raise ValueError('Cargo path/git/workspace dependency requires separate source binding')
            for child in value.values():reject_external(child)
        elif isinstance(value,list):
            for child in value:reject_external(child)
    for section in ['dependencies','dev-dependencies','build-dependencies','target','patch','replace']:
        reject_external(manifest.get(section,{}))
    if manifest.get('lib',{}).get('path','src/lib.rs')!='src/lib.rs' or manifest['package'].get('build','build.rs') not in ('build.rs',False):
        raise ValueError('Custom Rust source/build path requires separate source binding')
    if lock.get('version') not in (3,4):raise ValueError('Unsupported Cargo.lock')
    for package in lock.get('package',[]):
        source=package.get('source','')
        if source and (source!='registry+https://github.com/rust-lang/crates.io-index' or len(package.get('checksum',''))!=64 or any(c not in '0123456789abcdef' for c in package.get('checksum',''))):
            raise ValueError('Only checksum-locked crates.io registry dependencies supported')
    return manifest

def build(root,target,prefix,source_consent=False,linker=None,vendor=None):
    if not source_consent:raise ValueError('Explicit source execution consent required (Cargo build.rs/proc macros execute code)')
    validate(root)
    if target not in TARGETS:raise ValueError('Unsupported Rust target')
    if target!='wasm32-unknown-unknown' and (not linker or not pathlib.Path(linker).is_file()):raise ValueError('Verified target linker required')
    env=dict(os.environ,PATH=str(prefix/'bin')+os.pathsep+os.environ.get('PATH',''),CARGO_NET_OFFLINE='true',CARGO_TARGET_DIR=str(root/'target'))
    if linker:env['CARGO_TARGET_'+target.upper().replace('-','_')+'_LINKER']=linker
    before=digest(root/'Cargo.lock');before_inputs=source_digest(root)
    args=[str(prefix/'bin/cargo'),'build','--frozen','--offline','--release','--target',target,'--message-format=json']
    # Source execution is authorized, NOT claimed sandboxed. Caller supplies reviewed disposable copy.
    if vendor is not None:
        if vendor.is_symlink() or not vendor.is_dir():raise ValueError('Invalid Cargo vendor directory')
        args.extend(['--config','source.crates-io.replace-with="appforge-vendor"','--config','source.appforge-vendor.directory='+json.dumps(str(vendor))])
    p=subprocess.run(args,cwd=root,env=env,text=True,stdout=subprocess.PIPE,check=True)
    artifacts=[]
    for line in p.stdout.splitlines():
        event=json.loads(line)
        if event.get('reason')!='compiler-artifact':continue
        for value in event.get('filenames',[]):
            f=pathlib.Path(value);f=f if f.is_absolute() else root/f
            if f.suffix not in ('.wasm','.so','.exe','.dll'):continue
            if f.is_symlink() or not f.resolve().is_relative_to((root/'target').resolve()):raise ValueError('Artifact escapes Cargo target')
            data=f.read_bytes();kind=TARGETS[target]
            if kind=='wasm' and data[:8]!=b'\0asm\x01\0\0\0':raise ValueError('Invalid WASM')
            if kind=='elf' and (data[:4]!=b'\x7fELF' or data[4:6]!=b'\x02\x01' or int.from_bytes(data[18:20],'little')!=183):raise ValueError('Expected Android ARM64 ELF')
            if kind=='pe':
                off=int.from_bytes(data[60:64],'little')
                if data[:2]!=b'MZ' or data[off:off+4]!=b'PE\0\0' or data[off+4:off+6]!=b'\x64\x86':raise ValueError('Expected Windows x64 PE')
            artifacts.append(dict(path=str(f.relative_to(root)),sha256=digest(f),bytes=f.stat().st_size,target=target))
    if not artifacts or before!=digest(root/'Cargo.lock') or before_inputs!=source_digest(root):raise ValueError('No supported artifact or Cargo.lock changed')
    return dict(state='ARTIFACT_BUILT_NOT_APP_ACCEPTED',artifacts=artifacts,lockSha256=before)
if __name__=='__main__':
    ap=argparse.ArgumentParser();ap.add_argument('project');ap.add_argument('--target',choices=TARGETS,required=True);ap.add_argument('--prefix',default='/opt/appforge-device/rust-1.85.1');ap.add_argument('--vendor');ap.add_argument('--export-wasm',action='store_true');ap.add_argument('--linker');ap.add_argument('--source-execution-consent',action='store_true');a=ap.parse_args()
    root=pathlib.Path(a.project).absolute();result=build(root,a.target,pathlib.Path(a.prefix),a.source_execution_consent,a.linker,pathlib.Path(a.vendor).absolute() if a.vendor else None)
    if a.export_wasm:result['export']=export_wasm(root,result)
    print(json.dumps(result))
