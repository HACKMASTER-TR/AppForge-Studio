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
    if not (root/'src').is_dir() and not isinstance(tomllib.loads((root/'Cargo.toml').read_text()).get('workspace'),dict):raise ValueError('Rust src directory missing')
    def scan_error(error):raise error
    for current,dirs,names in os.walk(root,followlinks=False,onerror=scan_error):
        current=pathlib.Path(current)
        dirs[:]=[name for name in dirs if name!='.git' and not ((current==root or (current/'Cargo.toml').is_file()) and name in ignored)]
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
    namespace={'__name__':'appforge_rust_layout','__file__':str(pathlib.Path(__file__).with_name('rust-layout.py'))}
    script=pathlib.Path(namespace['__file__']);exec(compile(script.read_bytes(),str(script),'exec'),namespace)
    layout=namespace['resolve'](root);manifest=layout['manifest'];lock=tomllib.loads((root/'Cargo.lock').read_text())
    local={(m['package']['name'],m['package']['version']) for m in layout['packages'].values()}
    for package in lock.get('package',[]):
        if not package.get('source') and (package.get('name'),package.get('version')) not in local:raise ValueError('Unbound local Cargo package')
    if lock.get('version') not in (3,4):raise ValueError('Unsupported Cargo.lock')
    git_scope={'__name__':'trusted_git','__file__':str(pathlib.Path(__file__).with_name('rust-git.py'))}
    exec(compile(pathlib.Path(git_scope['__file__']).read_bytes(),git_scope['__file__'],'exec'),git_scope)
    git_pins=git_scope['pins'](root,lock);git_sources={p['source'] for p in git_pins}
    allowed={git_scope['source'](p['source'])[0] for p in git_pins}
    for m in layout['packages'].values():
        def check_git(v):
            if isinstance(v,dict):
                if 'git' in v and v['git'] not in allowed:raise ValueError('Unpinned Git dependency')
                for child in v.values():check_git(child)
            elif isinstance(v,list):
                for child in v:check_git(child)
        for section in ('dependencies','build-dependencies','dev-dependencies','target'):check_git(m.get(section,{}))
    for package in lock.get('package',[]):
        source=package.get('source','')
        if source in git_sources:continue
        if source and (source!='registry+https://github.com/rust-lang/crates.io-index' or len(package.get('checksum',''))!=64 or any(c not in '0123456789abcdef' for c in package.get('checksum',''))):
            raise ValueError('Only checksum-locked crates.io or explicit pinned Git bundles supported')
    return manifest

def build(root,target,prefix,source_consent=False,linker=None,vendor=None):
    if not source_consent:raise ValueError('Explicit source execution consent required (Cargo build.rs/proc macros execute code)')
    validate(root)
    if target not in TARGETS:raise ValueError('Unsupported Rust target')
    if target!='wasm32-unknown-unknown' and (not linker or not pathlib.Path(linker).is_file()):raise ValueError('Verified target linker required')
    if (root/'target').is_symlink() or (root/'target').resolve()!=root/'target':raise ValueError('Unsafe Cargo target directory')
    # Keep the actual project builder consistent with the verified Rust probe.
    # Cargo's child compiler must be resolved to this pinned toolchain.
    env=dict(os.environ,PATH=str(prefix/'bin')+os.pathsep+os.environ.get('PATH',''),CARGO_NET_OFFLINE='true',CARGO_TARGET_DIR=str(root/'target'),RUSTC=str(prefix/'bin/rustc'))
    if linker:env['CARGO_TARGET_'+target.upper().replace('-','_')+'_LINKER']=linker
    layout_scope={'__name__':'appforge_rust_layout','__file__':str(pathlib.Path(__file__).with_name('rust-layout.py'))}
    exec(compile(pathlib.Path(layout_scope['__file__']).read_bytes(),layout_scope['__file__'],'exec'),layout_scope)
    selected=layout_scope['resolve'](root)
    before=digest(root/'Cargo.lock');before_inputs=source_digest(root)
    args=[str(prefix/'bin/cargo'),'build','--frozen','--offline','--release','--target',target,'--message-format=json','--package',selected['manifest']['package']['name']]
    # Source execution is authorized, NOT claimed sandboxed. Caller supplies reviewed disposable copy.
    if vendor is not None:
        if vendor.is_symlink() or not vendor.is_dir():raise ValueError('Invalid Cargo vendor directory')
        args.extend(['--config','source.crates-io.replace-with="appforge-vendor"','--config','source.appforge-vendor.directory='+json.dumps(str(vendor))])
    git_scope={'__name__':'trusted_git','__file__':str(pathlib.Path(__file__).with_name('rust-git.py'))}
    exec(compile(pathlib.Path(git_scope['__file__']).read_bytes(),git_scope['__file__'],'exec'),git_scope)
    args.extend(git_scope['config'](root,vendor))
    p=subprocess.run(args,cwd=root,env=env,text=True,stdout=subprocess.PIPE)
    if p.returncode:
        # Cargo JSON diagnostics are on stdout; do not discard the actual failure
        # or dump source spans/rendered snippets into the operation journal.
        errors=[]
        for line in p.stdout.splitlines():
            try:event=json.loads(line)
            except json.JSONDecodeError:continue
            if event.get('reason')=='compiler-message' and event.get('message',{}).get('level')=='error':
                message=event['message'];errors.append(str(message.get('message',''))[:500])
                errors.extend(str(child.get('message',''))[:500] for child in message.get('children',[])[:3] if child.get('level') in ('note','help') and 'PATH=' not in str(child.get('message','')))
        raise ValueError('Cargo offline build failed (exit '+str(p.returncode)+'): '+'; '.join(errors)[:2000])
    artifacts=[]
    for line in p.stdout.splitlines():
        event=json.loads(line)
        if event.get('reason')!='compiler-artifact':continue
        # Cross builds also emit host proc-macro shared libraries. Only artifacts
        # of the reviewed root manifest can be application outputs.
        if pathlib.Path(event.get('manifest_path','')).absolute()!=selected['directory']/'Cargo.toml':continue
        for value in event.get('filenames',[]):
            f=pathlib.Path(value);f=f if f.is_absolute() else root/f
            if f.suffix not in ('.wasm','.so','.exe','.dll'):continue
            if f.is_symlink() or not f.resolve().is_relative_to(root/'target'/target):raise ValueError('Artifact escapes selected Cargo target')
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
