#!/usr/bin/env python3
"""Pinned Android target + explicit source-consent JNI preparation, offline only.
Caller owns disposable runtime/source stages and download/network approval.
"""
import argparse,hashlib,json,os,pathlib,shutil,struct,subprocess,tarfile,tempfile
HERE=pathlib.Path(__file__).parent
# App-shipped trusted bytes; never load project bytecode or Python modules.
def trusted(name):
    scope={'__name__':'appforge_trusted_tool','__file__':str(HERE/name)}
    exec(compile((HERE/name).read_bytes(),str(HERE/name),'exec'),scope)
    return scope

def sha(path):
    h=hashlib.sha256()
    with path.open('rb') as f:
        for b in iter(lambda:f.read(65536),b''):h.update(b)
    return h.hexdigest()

def defined_exports(path):
    if path.is_symlink() or not path.is_file() or path.stat().st_size>64*1024*1024:raise ValueError('Unsafe JNI library')
    b=path.read_bytes()
    if len(b)<64 or b[:6]!=b'\x7fELF\x02\x01' or struct.unpack_from('<HH',b,16)!=(3,183):raise ValueError('Expected ARM64 ELF shared library')
    ph=struct.unpack_from('<Q',b,32)[0];phsize,phcount=struct.unpack_from('<HH',b,54)
    if phsize!=56 or not 1<=phcount<=1024 or ph+phsize*phcount>len(b):raise ValueError('Invalid ELF program headers')
    loads=0
    for i in range(phcount):
        kind,flags,offset,va,pa,fs,ms,align=struct.unpack_from('<IIQQQQQQ',b,ph+i*phsize)
        if kind!=1:continue
        loads+=1
        if align<16384 or align&(align-1) or offset%16384!=va%16384 or offset+fs>len(b) or fs>ms:raise ValueError('Android16 requires 16KiB aligned ELF load segments')
    if not loads:raise ValueError('ELF load segments missing')
    off=struct.unpack_from('<Q',b,40)[0];size,count=struct.unpack_from('<HH',b,58)
    if size!=64 or not 1<=count<=4096 or off+size*count>len(b):raise ValueError('Invalid ELF sections')
    sections=[struct.unpack_from('<IIQQQQIIQQ',b,off+i*size) for i in range(count)]
    names=[];symbols_seen=0
    for section in sections:
        if section[1]!=11:continue
        start,length,link,entry=section[4],section[5],section[6],section[9]
        symbols_seen+=length//24
        if symbols_seen>100000 or entry!=24 or length%entry or start+length>len(b) or link>=count:raise ValueError('Invalid ELF dynsym')
        strings=sections[link]
        if strings[1]!=3 or strings[4]+strings[5]>len(b):raise ValueError('Invalid ELF dynstr')
        table=b[strings[4]:strings[4]+strings[5]]
        for pos in range(start,start+length,entry):
            name,info,other,index,value,sz=struct.unpack_from('<IBBHQQ',b,pos)
            if not index or info>>4 not in (1,2) or info&15!=2 or other&3 not in (0,3):continue
            text=table[name:name+1025]
            if name>=len(table) or b'\0' not in text:raise ValueError('Invalid ELF symbol name or length')
            symbol=text.split(b'\0',1)[0].decode('ascii',errors='strict')
            names.append(symbol)
    return sorted(set(names))

def exports(path):
    names=[symbol for symbol in defined_exports(path) if symbol.startswith('Java_') or symbol=='JNI_OnLoad']
    if not names:raise ValueError('No defined JNI export; C ABI alone is not JNI integration')
    return names

def dynamic_libraries(path):
    defined_exports(path)  # Bound ELF architecture, segment and section validation.
    b=path.read_bytes();off=struct.unpack_from('<Q',b,40)[0];count=struct.unpack_from('<H',b,60)[0]
    sections=[struct.unpack_from('<IIQQQQIIQQ',b,off+i*64) for i in range(count)]
    needed=[];soname=None;examined=0
    for section in sections:
        if section[1]!=6:continue
        start,size,link,width=section[4],section[5],section[6],section[9]
        if width!=16 or size%16 or size//16>4096 or start+size>len(b) or link>=count:raise ValueError('Invalid ELF dynamic table')
        examined+=size//16
        if examined>65536:raise ValueError('Dynamic table operation limit')
        strings=sections[link]
        if strings[1]!=3 or strings[4]+strings[5]>len(b):raise ValueError('Invalid dynamic string table')
        text=b[strings[4]:strings[4]+strings[5]]
        for pos in range(start,start+size,16):
            tag,value=struct.unpack_from('<qQ',b,pos)
            if tag not in (1,14,15,29):continue
            raw=text[value:value+1025]
            if value>=len(text) or b'\0' not in raw:raise ValueError('Invalid ELF dependency name')
            name=raw.split(b'\0',1)[0].decode('ascii')
            if tag in (15,29) and name:raise ValueError('Unreviewed Android RPATH/RUNPATH')
            if tag in (1,14):
                import re
                if not re.fullmatch(r'[A-Za-z0-9_+.-]+\.so',name):raise ValueError('Absolute/unsafe Android dependency name')
                if tag==1:needed.append(name)
                else:
                    if soname is not None:raise ValueError('Duplicate ELF SONAME')
                    soname=name
    return soname,sorted(set(needed))

def config(root):
    p=root/'appforge-rust-android.json'
    if p.is_symlink() or not p.is_file() or p.stat().st_size>4096:raise ValueError('Explicit appforge-rust-android.json required')
    j=json.loads(p.read_text());name=j.get('library','')
    import re
    if j.get('schema')!=1 or j.get('module')!='app' or j.get('minApi')!=26 or not re.fullmatch('[A-Za-z][A-Za-z0-9_]{0,63}',name):raise ValueError('Unsupported JNI binding: app module / ARM64 / API26 required')
    if not ((root/'app/build.gradle').is_file() or (root/'app/build.gradle.kts').is_file()):raise ValueError('Android app Gradle module required')
    return name

def install_target(prefix,cache,base=None,target='aarch64-linux-android'):
    if target not in ('aarch64-linux-android','x86_64-pc-windows-gnu'):raise ValueError('Unsupported native target')
    manifest=json.loads((HERE/('rust-android-target-v1.json' if target=='aarch64-linux-android' else 'rust-windows-target-v1.json')).read_text());a=manifest['artifact']
    if manifest['schema']!=1 or manifest['version']!='1.85.1' or manifest['target']!=target:raise ValueError('Unsupported target manifest')
    archive=cache/a['file']
    if archive.is_symlink() or not archive.is_file() or archive.stat().st_size>128*1024*1024 or sha(archive)!=a['sha256']:raise ValueError('Android Rust target SHA-256 mismatch or cache missing')
    if prefix.is_symlink() or prefix.resolve()!=prefix:raise ValueError('Unsafe Rust prefix')
    if base is not None:
        if prefix.exists() or base.is_symlink() or base.resolve()!=base or not base.is_dir():raise ValueError('Fresh private target prefix required')
        files=list(base.rglob('*'))
        if any(p.is_symlink() or not (p.is_file() or p.is_dir()) for p in files):raise ValueError('Unsafe base compiler files')
        total=sum(p.stat().st_size for p in files if p.is_file())
        if shutil.disk_usage(prefix.parent).free<total+512*1024*1024:raise ValueError('Insufficient private compiler space')
        shutil.copytree(base,prefix)
    version=subprocess.check_output([str(prefix/'bin/rustc'),'--version'],text=True)
    if not version.startswith('rustc 1.85.1 '):raise ValueError('Rust target/compiler version mismatch')
    # Install only in caller's disposable generation; never overlay an active toolchain.
    if (prefix/'lib/rustlib'/target).exists():raise ValueError('Target already present; fresh isolated stage required')
    installer=trusted('install-rust-module.py')
    with tarfile.open(archive,'r:xz') as t:
        members=t.getmembers()
        if len(members)>20000:raise ValueError('Target archive member limit')
        total=sum(m.size for m in members if m.isfile())
        if total>1024*1024*1024 or shutil.disk_usage(prefix).free<total*2+64*1024*1024:raise ValueError('Insufficient target staging space')
    with tempfile.TemporaryDirectory(prefix='rust-android-',dir=prefix.parent) as work:
        installer['extract'](archive,pathlib.Path(work))
        entries=list(pathlib.Path(work).iterdir())
        if len(entries)!=1:raise ValueError('Invalid target archive root')
        subprocess.run(['/bin/sh',str(entries[0]/'install.sh'),'--prefix='+str(prefix),'--disable-ldconfig'],check=True)
    result=subprocess.check_output([str(prefix/'bin/rustc'),'--print','target-libdir','--target',target],text=True).strip()
    target_dir=pathlib.Path(result)
    if not target_dir.resolve().is_relative_to(prefix) or not list(target_dir.glob('libstd-*.rlib')):raise ValueError('Native target not installed')

def discover_linker(sdk):
    # Use an already verified toolchain; no SDK download or arbitrary project linker.
    ndks=sdk/'ndk'
    for version in sorted(ndks.iterdir() if ndks.is_dir() else [],reverse=True):
        path=version/'toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android26-clang'
        if path.is_file() and os.access(path,os.X_OK):
            subprocess.run([str(path),'--version'],check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
            return str(path)
    raise ValueError('Verified Android ARM64 API26 NDK linker missing or incompatible with Linux host; no fallback')

def prepare(root,prefix,linker,vendor,export):
    builder=trusted('rust-project.py');builder['validate'](root);name=config(root)
    os.environ['RUSTFLAGS']='-C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wl,-z,common-page-size=16384'
    result=builder['build'](root,'aarch64-linux-android',prefix,True,linker,vendor)
    items=[a for a in result['artifacts'] if pathlib.Path(a['path']).name=='lib'+name+'.so']
    if len(result['artifacts'])!=1 or len(items)!=1:raise ValueError('Exactly the declared JNI shared library required')
    item=items[0];artifact=root/item['path'];symbols=exports(artifact)
    if export:
        dest=root/'appforge-rust'
        if dest.exists() or dest.is_symlink():raise ValueError('Preserve previous/foreign export; use fresh private copy')
        dest.mkdir();shutil.copyfile(artifact,dest/artifact.name)
        j=dict(schema=2,target='aarch64-linux-android',minApi=26,module='app',sourceSha256=builder['source_digest'](root),file=artifact.name,sha256=sha(artifact),jniExports=symbols)
        with (dest/'artifacts.json').open('w') as f:json.dump(j,f);f.flush();os.fsync(f.fileno())
    result['jniExports']=symbols
    return result

if __name__=='__main__':
    ap=argparse.ArgumentParser();ap.add_argument('project',nargs='?');ap.add_argument('--prefix',default='/opt/appforge-device/rust-1.85.1');ap.add_argument('--cache');ap.add_argument('--base-prefix');ap.add_argument('--prepare-target',action='store_true');ap.add_argument('--check-project',action='store_true');ap.add_argument('--linker');ap.add_argument('--sdk',default='/opt/appforge-device/android-sdk');ap.add_argument('--vendor');ap.add_argument('--export',action='store_true');ap.add_argument('--source-execution-consent',action='store_true');a=ap.parse_args()
    prefix=pathlib.Path(a.prefix).absolute()
    if a.prepare_target:install_target(prefix,pathlib.Path(a.cache),pathlib.Path(a.base_prefix).absolute() if a.base_prefix else None);print('ANDROID_RUST_TARGET_STAGED_NOT_READY')
    elif a.check_project:
        root=pathlib.Path(a.project).absolute();trusted('rust-project.py')['validate'](root);config(root);print('EXPLICIT_JNI_PROJECT_VALIDATED_NO_CODE_EXECUTION')
    else:
        if not a.source_execution_consent:raise ValueError('Explicit source execution consent required')
        root=pathlib.Path(a.project).absolute();print(json.dumps(prepare(root,prefix,a.linker or discover_linker(pathlib.Path(a.sdk)),pathlib.Path(a.vendor).absolute() if a.vendor else None,a.export)))
