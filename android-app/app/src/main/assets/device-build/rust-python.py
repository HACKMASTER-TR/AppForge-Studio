#!/usr/bin/env python3
"""Explicit PyO3 CPython3.12 ABI3 extension for Chaquopy ARM64/API26.
Never installs pip packages, executes online or claims a generic C library is PyO3.
"""
import argparse,hashlib,json,os,pathlib,re,shutil,zipfile
HERE=pathlib.Path(__file__).parent

def trusted(name):
    scope={'__name__':'appforge_trusted_tool','__file__':str(HERE/name)}
    exec(compile((HERE/name).read_bytes(),str(HERE/name),'exec'),scope)
    return scope

def config(root):
    p=root/'appforge-rust-python.json'
    if p.is_symlink() or not p.is_file() or p.stat().st_size>4096:raise ValueError('Explicit PyO3 binding required')
    j=json.loads(p.read_text());name=j.get('module','')
    if j.get('schema')!=1 or j.get('pythonVersion')!='3.12' or j.get('minApi')!=26 or not re.fullmatch('[A-Za-z_][A-Za-z0-9_]{0,63}',name):raise ValueError('Only Python3.12 ARM64/API26 module binding supported')
    manifest=trusted('rust-project.py')['validate'](root)
    library=manifest.get('lib',{}).get('name',manifest['package']['name'].replace('-','_'))
    dep=manifest.get('dependencies',{}).get('pyo3',{})
    if library!=name or not isinstance(dep,dict) or not re.fullmatch(r'=\d+\.\d+\.\d+',dep.get('version','')) or not {'extension-module','abi3-py312'}.issubset(dep.get('features',[])):raise ValueError('Exact PyO3 version and CPython3.12 stable ABI features required')
    return name

def python_library(root,cache):
    j=json.loads((HERE/'rust-python-target-v1.json').read_text());a=j['artifact']
    if j['schema']!=1 or j['pythonVersion']!='3.12.12' or j['abi']!='arm64-v8a' or j['pythonLibrary']!='jniLibs/arm64-v8a/libpython3.12.so':raise ValueError('Unknown Python target manifest')
    archive=cache/a['file']
    if archive.is_symlink() or not archive.is_file() or archive.stat().st_size>32*1024*1024 or hashlib.sha256(archive.read_bytes()).hexdigest()!=a['sha256']:raise ValueError('Python target cache SHA-256 mismatch')
    with zipfile.ZipFile(archive) as z:
        entries=[x for x in z.infolist() if x.filename==j['pythonLibrary']]
        if len(entries)!=1 or not 64<=entries[0].file_size<=16*1024*1024:raise ValueError('Invalid Python link library')
        data=z.read(entries[0])
    # No archive paths are extracted. Only one bounded verified library is staged.
    target=root/'target'
    if target.is_symlink():raise ValueError('Unsafe target path')
    target.mkdir(exist_ok=True);stage=target/'appforge-python-sdk'
    if stage.is_symlink():raise ValueError('Unsafe Python link stage')
    stage.mkdir(exist_ok=True);dest=stage/'libpython3.12.so'
    if dest.exists():
        if dest.is_symlink() or not dest.is_file() or dest.read_bytes()!=data:raise ValueError('Foreign Python library preserved')
    else:
        if shutil.disk_usage(stage).free<len(data)+16*1024*1024:raise ValueError('Insufficient Python target space')
        with dest.open('xb') as f:f.write(data);f.flush();os.fsync(f.fileno())
    return stage

def prepare(root,prefix,linker,vendor,export,cache):
    name=config(root);builder=trusted('rust-project.py');android=trusted('rust-android.py')
    # Do not discover the host Python ABI in a cross build.
    os.environ['PYO3_CROSS']='1';os.environ['PYO3_CROSS_PYTHON_VERSION']='3.12';os.environ['PYO3_CROSS_PYTHON_IMPLEMENTATION']='CPython'
    library=python_library(root,cache)
    # Use an explicit cross configuration for the pinned CPython ARM64 runtime,
    # never the host interpreter's sysconfig or an invented _sysconfigdata.py.
    config_file=library/'pyo3-config.txt'
    data=('implementation=CPython\nversion=3.12\nshared=true\nabi3=true\n'
          'pointer_width=64\nlib_name=python3.12\nlib_dir='+str(library)+'\n')
    if config_file.exists():
        if config_file.is_symlink() or config_file.read_text()!=data:raise ValueError('Foreign PyO3 config preserved')
    else:
        with config_file.open('x') as f:f.write(data);f.flush();os.fsync(f.fileno())
    os.environ['PYO3_CONFIG_FILE']=str(config_file)
    os.environ['RUSTFLAGS']='-C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wl,-z,common-page-size=16384'
    result=builder['build'](root,'aarch64-linux-android',prefix,True,linker,vendor)
    python_library(root,cache)
    if config_file.is_symlink() or config_file.read_text()!=data:raise ValueError('PyO3 cross config changed during source execution')
    if len(result['artifacts'])!=1 or pathlib.Path(result['artifacts'][0]['path']).name!='lib'+name+'.so':raise ValueError('Exactly the declared PyO3 extension required')
    artifact=root/result['artifacts'][0]['path'];symbols=android['defined_exports'](artifact)
    if 'PyInit_'+name not in symbols:raise ValueError('Real defined PyInit export missing; C ABI is not PyO3')
    if export:
        directory=root/'appforge-rust'
        if directory.exists() or directory.is_symlink():raise ValueError('Previous/foreign output preserved; fresh private copy required')
        directory.mkdir();dest=directory/(name+'.so');shutil.copyfile(artifact,dest)
        receipt=dict(schema=2,target='aarch64-linux-android',integration='pyo3',pythonVersion='3.12',minApi=26,sourceSha256=builder['source_digest'](root),file=dest.name,sha256=builder['digest'](artifact),module=name)
        with (directory/'artifacts.json').open('w') as f:json.dump(receipt,f);f.flush();os.fsync(f.fileno())
    return result

if __name__=='__main__':
    ap=argparse.ArgumentParser();ap.add_argument('project',nargs='?');ap.add_argument('--prefix',default='/opt/appforge-device/rust-1.85.1');ap.add_argument('--prepare-target',action='store_true');ap.add_argument('--base-prefix');ap.add_argument('--cache');ap.add_argument('--check-project',action='store_true');ap.add_argument('--linker');ap.add_argument('--sdk',default='/opt/appforge-device/android-sdk');ap.add_argument('--vendor');ap.add_argument('--export',action='store_true');ap.add_argument('--source-execution-consent',action='store_true');a=ap.parse_args()
    android=trusted('rust-android.py')
    if a.prepare_target:android['install_target'](pathlib.Path(a.prefix).absolute(),pathlib.Path(a.cache),pathlib.Path(a.base_prefix).absolute() if a.base_prefix else None)
    elif a.check_project:config(pathlib.Path(a.project).absolute());print('PYO3_BINDING_VALIDATED_NO_EXECUTION')
    else:
        if not a.source_execution_consent:raise ValueError('Explicit source execution consent required')
        print(json.dumps(prepare(pathlib.Path(a.project).absolute(),pathlib.Path(a.prefix).absolute(),a.linker or android['discover_linker'](pathlib.Path(a.sdk)),pathlib.Path(a.vendor).absolute() if a.vendor else None,a.export,pathlib.Path(a.cache or '/workspace/crate-cache'))))
