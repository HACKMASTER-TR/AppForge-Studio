#!/usr/bin/env python3
"""Offline-only pinned Rust installation. Caller owns isolated generation/activation.
No network requests, rustup, apt mutation, or READY marker based on presence alone.
"""
import argparse, hashlib, json, os, pathlib, platform, subprocess, tarfile, tempfile, shutil
VERSION = '1.85.1'

def sha(path):
    h=hashlib.sha256()
    with path.open('rb') as f:
        for b in iter(lambda:f.read(1024*1024),b''): h.update(b)
    return h.hexdigest()

def extract(archive,destination):
    with tarfile.open(archive,'r:xz') as t:
        for m in t.getmembers():
            p=pathlib.PurePosixPath(m.name)
            if p.is_absolute() or '..' in p.parts or not (m.isdir() or m.isfile()):
                raise ValueError('Rust archive path/link/type rejected')
        t.extractall(destination,filter='data')

def probe(prefix,host):
    rustc=prefix/'bin/rustc';cargo=prefix/'bin/cargo'
    rv=subprocess.check_output([str(rustc),'--version'],text=True).strip()
    cv=subprocess.check_output([str(cargo),'--version'],text=True).strip()
    if not rv.startswith('rustc '+VERSION+' ') or not cv.startswith('cargo '+VERSION+' '): raise ValueError('Rust version mismatch')
    # Real target compilation and Cargo.lock/offline replay, not --version alone.
    with tempfile.TemporaryDirectory(prefix='rust-probe-') as work:
        root=pathlib.Path(work);(root/'src').mkdir()
        (root/'Cargo.toml').write_text('[package]\nname="appforge_probe"\nversion="0.1.0"\nedition="2021"\n[lib]\ncrate-type=["cdylib"]\n')
        (root/'src/lib.rs').write_text('#[no_mangle] pub extern "C" fn appforge_add(a:i32,b:i32)->i32 {a+b}\n')
        env=dict(os.environ,PATH=str(prefix/'bin')+os.pathsep+os.environ.get('PATH',''),CARGO_HOME=str(root/'cargo-home'),CARGO_NET_OFFLINE='true')
        subprocess.run([str(cargo),'generate-lockfile','--offline'],cwd=root,env=env,check=True)
        command=[str(cargo),'build','--frozen','--offline','--release','--target','wasm32-unknown-unknown']
        for _ in range(2): subprocess.run(command,cwd=root,env=env,check=True)
        artifact=root/'target/wasm32-unknown-unknown/release/appforge_probe.wasm'
        if artifact.read_bytes()[:8]!=b'\0asm\x01\0\0\0':raise ValueError('Invalid WASM output')
        return dict(schema=1,host=host,rustc=rv,cargo=cv,wasmSha256=sha(artifact),cargoLockSha256=sha(root/'Cargo.lock'),offlineReplay=True,physicalAndroid='NOT_RUN')

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--prefix',default='/opt/appforge-device/rust-'+VERSION);ap.add_argument('--cache',default='/opt/appforge-device/cache');ap.add_argument('--verify',action='store_true');a=ap.parse_args()
    prefix=pathlib.Path(a.prefix).absolute();cache=pathlib.Path(a.cache)
    if prefix.is_symlink() or prefix.parent.resolve()!=prefix.parent:raise ValueError('Unsafe Rust namespace')
    host={'aarch64':'aarch64-unknown-linux-gnu','x86_64':'x86_64-unknown-linux-gnu'}.get(platform.machine())
    if host is None:raise ValueError('Unsupported Rust Linux host')
    manifest=json.loads(pathlib.Path(__file__).with_name('rust-toolchain-v1.json').read_text())
    if manifest['schema']!=1 or manifest['version']!=VERSION:raise ValueError('Unsupported Rust manifest')
    artifacts=[x for x in manifest['artifacts'] if x['host']==host]
    if len(artifacts)!=4:raise ValueError('Incomplete Rust components')
    if not a.verify:
        # Never overlay an existing unverified toolchain. Manager installs in staging.
        if prefix.exists():raise ValueError('Rust prefix exists; use verified generation update')
        for item in artifacts:
            archive=cache/item['file']
            if archive.is_symlink() or sha(archive)!=item['sha256']:raise ValueError('Rust archive SHA-256 mismatch')
        required=0
        for item in artifacts:
            with tarfile.open(cache/item['file'],'r:xz') as t:required+=sum(m.size for m in t.getmembers() if m.isfile())*2
        prefix.parent.mkdir(parents=True,exist_ok=True)
        if shutil.disk_usage(prefix.parent).free<required+64*1024*1024:raise ValueError('Insufficient Rust staging disk space')
        with tempfile.TemporaryDirectory(prefix='rust-extract-',dir=prefix.parent) as temp:
            for i,item in enumerate(artifacts):
                dest=pathlib.Path(temp)/str(i);dest.mkdir();extract(cache/item['file'],dest)
                entries=list(dest.iterdir())
                if len(entries)!=1:raise ValueError('Invalid Rust archive root')
                subprocess.run(['/bin/sh',str(entries[0]/'install.sh'),'--prefix='+str(prefix),'--disable-ldconfig'],check=True)
    result=probe(prefix,host)
    receipt=prefix/'.offline-probe.json'
    if a.verify:
        old=json.loads(receipt.read_text())
        for key in ['host','rustc','cargo','wasmSha256','cargoLockSha256','offlineReplay']:
            if old.get(key)!=result[key]:raise ValueError('Rust probe receipt mismatch: '+key)
    else:
        with (prefix/'.offline-probe.tmp').open('w') as f:json.dump(result,f);f.flush();os.fsync(f.fileno())
        os.replace(prefix/'.offline-probe.tmp',receipt)
    print(json.dumps(result))
if __name__=='__main__':main()
