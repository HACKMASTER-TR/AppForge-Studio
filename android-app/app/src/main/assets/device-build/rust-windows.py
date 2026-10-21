#!/usr/bin/env python3
"""Offline, explicit-consent Rust Windows native EXE adapter.
Uses only app-shipped helpers and an existing verified MinGW linker. No downloads.
"""
import argparse,json,os,pathlib,re,shutil,struct,subprocess
HERE=pathlib.Path(__file__).parent

def trusted(name):
    scope={'__name__':'appforge_trusted_tool','__file__':str(HERE/name)}
    exec(compile((HERE/name).read_bytes(),str(HERE/name),'exec'),scope)
    return scope

def binding(root):
    p=root/'appforge-rust-windows.json'
    if p.is_symlink() or not p.is_file() or p.stat().st_size>4096:raise ValueError('Explicit Windows Rust binding required')
    j=json.loads(p.read_text());library=j.get('schema')==2;kind='dll' if library else 'exe';name=j.get('library' if library else 'executable','')
    if j.get('schema') not in (1,2) or j.get('target')!='x86_64-pc-windows-gnu' or not re.fullmatch(r'[A-Za-z0-9_-]{1,64}\.'+kind,name):raise ValueError('Unsupported Windows native binding')
    if library and not re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]{0,127}',j.get('export','')):raise ValueError('Explicit DLL export required')
    return name,kind,j.get('export')

def config(root):return binding(root)[0]

def check_pe(file,kind='exe'):
    if file.is_symlink() or not file.is_file() or not 256<=file.stat().st_size<=128*1024*1024:raise ValueError('Unsafe Windows EXE')
    b=file.read_bytes()
    if b[:2]!=b'MZ':raise ValueError('Missing DOS header')
    off=struct.unpack_from('<I',b,60)[0]
    if off<64 or off+24>len(b) or b[off:off+4]!=b'PE\0\0':raise ValueError('Invalid PE header')
    machine,sections=struct.unpack_from('<HH',b,off+4);optional,flags=struct.unpack_from('<HH',b,off+20)
    if kind not in ('exe','dll') or machine!=0x8664 or not 1<=sections<=96 or optional<112 or not flags&2 or bool(flags&0x2000)!=(kind=='dll') or off+24+optional+sections*40>len(b):raise ValueError('Expected declared x64 EXE/DLL kind; no relabelling')
    if struct.unpack_from('<H',b,off+24)[0]!=0x20b:raise ValueError('Expected PE32+')
    for i in range(sections):
        size,start=struct.unpack_from('<II',b,off+24+optional+i*40+16)
        if start+size>len(b):raise ValueError('PE section escapes file')

def dll_exports(file):
    check_pe(file,'dll');b=file.read_bytes();off=struct.unpack_from('<I',b,60)[0];count=struct.unpack_from('<H',b,off+6)[0];width=struct.unpack_from('<H',b,off+20)[0];optional=off+24
    if width<120 or struct.unpack_from('<I',b,optional+108)[0]<1:raise ValueError('DLL export directory missing')
    export,size=struct.unpack_from('<II',b,optional+112)
    def rva(value,length=1,code=False):
        for i in range(count):
            section=optional+width+i*40;va=struct.unpack_from('<I',b,section+12)[0];raw,start=struct.unpack_from('<II',b,section+16);flags=struct.unpack_from('<I',b,section+36)[0]
            if value>=va and value+length<=va+raw and (not code or flags&0x20000000):return start+value-va
        raise ValueError('DLL RVA outside declared file/code sections')
    directory=rva(export,40);functions,names,table,pointers,ordinals=struct.unpack_from('<IIIII',b,directory+20)
    if not 1<=functions<=65536 or not 1<=names<=4096:raise ValueError('DLL export bounds')
    table=rva(table,functions*4);pointers=rva(pointers,names*4);ordinals=rva(ordinals,names*2);result=[]
    for i in range(names):
        ordinal=struct.unpack_from('<H',b,ordinals+i*2)[0]
        if ordinal>=functions:raise ValueError('DLL export ordinal outside function table')
        function=struct.unpack_from('<I',b,table+ordinal*4)[0]
        if export<=function<export+size:raise ValueError('Forwarded DLL export is not Rust code')
        rva(function,1,True);name_rva=struct.unpack_from('<I',b,pointers+i*4)[0];raw=bytearray()
        for pos in range(129):
            value=b[rva(name_rva+pos)]
            if value==0:break
            raw.append(value)
        else:raise ValueError('DLL export name bound')
        name=raw.decode('ascii')
        if not re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]{0,127}',name):raise ValueError('Unsafe DLL export name')
        result.append(name)
    return sorted(set(result))

def discover_linker():
    for name in ['/usr/bin/x86_64-w64-mingw32-gcc-posix','/usr/bin/x86_64-w64-mingw32-gcc']:
        if pathlib.Path(name).is_file() and os.access(name,os.X_OK):
            machine=subprocess.check_output([name,'-dumpmachine'],text=True).strip()
            if machine=='x86_64-w64-mingw32':return name
    raise ValueError('Verified MinGW x64 linker missing or incompatible; no host/linker fallback')

def prepare(root,prefix,linker,vendor,export):
    builder=trusted('rust-project.py');builder['validate'](root);name,kind,symbol=binding(root)
    if subprocess.check_output([linker,'-dumpmachine'],text=True).strip()!='x86_64-w64-mingw32':raise ValueError('Wrong Windows linker target')
    result=builder['build'](root,'x86_64-pc-windows-gnu',prefix,True,linker,vendor)
    if len(result['artifacts'])!=1 or pathlib.Path(result['artifacts'][0]['path']).name!=name:raise ValueError('Exactly the declared Windows EXE/DLL required')
    artifact=root/result['artifacts'][0]['path'];check_pe(artifact,kind)
    if kind=='dll' and symbol not in dll_exports(artifact):raise ValueError('Defined DLL export missing')
    if export:
        dest=root/'appforge-rust'
        if dest.exists() or dest.is_symlink():raise ValueError('Preserve previous/foreign export; use a fresh private copy')
        dest.mkdir();shutil.copyfile(artifact,dest/name)
        receipt=dict(schema=2,target='x86_64-pc-windows-gnu',sourceSha256=builder['source_digest'](root),file=name,sha256=builder['digest'](artifact),artifactKind=kind)
        with (dest/'artifacts.json').open('w') as f:json.dump(receipt,f);f.flush();os.fsync(f.fileno())
    return result

if __name__=='__main__':
    ap=argparse.ArgumentParser();ap.add_argument('project',nargs='?');ap.add_argument('--prefix',default='/opt/appforge-device/rust-1.85.1');ap.add_argument('--base-prefix');ap.add_argument('--cache');ap.add_argument('--prepare-target',action='store_true');ap.add_argument('--check-project',action='store_true');ap.add_argument('--linker');ap.add_argument('--vendor');ap.add_argument('--export',action='store_true');ap.add_argument('--source-execution-consent',action='store_true');a=ap.parse_args()
    if a.prepare_target:
        trusted('rust-android.py')['install_target'](pathlib.Path(a.prefix).absolute(),pathlib.Path(a.cache),pathlib.Path(a.base_prefix).absolute() if a.base_prefix else None,target='x86_64-pc-windows-gnu');print('WINDOWS_RUST_TARGET_STAGED_NOT_READY')
    elif a.check_project:
        root=pathlib.Path(a.project).absolute();trusted('rust-project.py')['validate'](root);config(root);print('WINDOWS_RUST_BINDING_VALIDATED_NO_EXECUTION')
    else:
        if not a.source_execution_consent:raise ValueError('Explicit source execution consent required')
        print(json.dumps(prepare(pathlib.Path(a.project).absolute(),pathlib.Path(a.prefix).absolute(),a.linker or discover_linker(),pathlib.Path(a.vendor).absolute() if a.vendor else None,a.export)))
