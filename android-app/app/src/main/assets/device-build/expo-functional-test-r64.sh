#!/bin/sh
# Expo owns Node, not Python. Keep the Rust and Windows acceptance paths unchanged.
# Fixed app-authored input and trusted, pinned SDK paths only; offline.
set -eu
ROOT=/opt/appforge-device
SDK="$ROOT/android-sdk"
NDK="$SDK/ndk/27.1.12297006"
HOST="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
BIN="$HOST/bin"
NODE="$ROOT/node-22.23.3/bin/node"
LINKER="$BIN/appforge-rust-api26-crt-absolute-r52"
WORK=$(mktemp -d /tmp/appforge-expo-functional.XXXXXXXX)
trap 'rm -rf -- "$WORK"' EXIT HUP INT TERM
export APPFORGE_DEVICE_OFFLINE=1
test -x "$NODE"
# Independently verify the same exact R50/R51/R52 wrapper bytes used by Rust.
"$NODE" - <<'JS_ROUTE'
const fs = require('node:fs');
const assert = require('node:assert/strict');
const root='/opt/appforge-device', sdk=root+'/android-sdk';
const ndk=sdk+'/ndk/27.1.12297006', host=ndk+'/toolchains/llvm/prebuilt/linux-x86_64';
const bin=host+'/bin', sys=host+'/sysroot', common=sys+'/usr/lib/aarch64-linux-android', api=common+'/26';
const clang=bin+'/clang', r50=bin+'/appforge-rust-aarch64-linux-android26-clang';
const r51=bin+'/appforge-rust-api26-crt-linker-r51', r52=bin+'/appforge-rust-api26-crt-absolute-r52';
function directory(p){assert(fs.lstatSync(p).isDirectory(),p);}
function regular(p,max=67108864){const s=fs.lstatSync(p);assert(s.isFile()&&s.size>0&&s.size<=max,p);}
function executable(p,max){regular(p,max);fs.accessSync(p,fs.constants.X_OK);}
assert.equal(process.versions.node,'22.23.3');
assert.equal(process.arch,'arm64');
for(const d of [sdk,ndk,host,bin,sys,common,api])directory(d);
regular(ndk+'/source.properties',8192);
assert.match(fs.readFileSync(ndk+'/source.properties','utf8'),/^Pkg\.Revision\s*=\s*27\.1\.12297006\s*$/m);
// Installer creates this witness with touch; zero bytes are expected.
const ready=fs.lstatSync(root+'/.expo-arm64-ndk-host-v3');assert(ready.isFile()&&ready.size<=4096,'NDK host witness');
executable(clang,32768);
const cc=fs.readFileSync(clang,'utf8');
for(const marker of ['# APPFORGE_EXPO_ARM64_NDK_CLANG_WRAPPER_V3','--ld-path=/usr/lib/llvm-18/bin/ld.lld','-resource-dir='])assert(cc.includes(marker),marker);
regular(sys+'/usr/include/unistd.h');
for(const f of ['crtbegin_so.o','crtend_so.o','libc.so','libdl.so'])regular(api+'/'+f);
for(const f of [r50,r51,r52])executable(f,4096);
assert.equal(fs.readFileSync(r50,'utf8'),['#!/bin/sh','# APPFORGE_R50_RUST_API26_SYSROOT_WRAPPER_V1','set -eu','exec "'+clang+'" --target=aarch64-linux-android26 --sysroot="'+sys+'" "$@"',''].join('\n'));
assert.equal(fs.readFileSync(r51,'utf8'),['#!/bin/sh','# APPFORGE_R51_RUST_API26_CRT_WRAPPER_V1','set -eu','exec "'+r50+'" -B"'+api+'" -L"'+api+'" -L"'+common+'" "$@"',''].join('\n'));
assert.equal(fs.readFileSync(r52,'utf8'),['#!/bin/sh','# APPFORGE_R52_API26_CRT_ABSOLUTE_V1','set -eu','case " $* " in','  *" -shared "*)','    exec "'+r51+'" -nostartfiles -Wl,-L,"'+api+'" -Wl,-L,"'+common+'" -Xlinker "'+api+'/crtbegin_so.o" "$@" -Xlinker "'+api+'/crtend_so.o"','    ;;','  *) exec "'+r51+'" "$@" ;;','esac',''].join('\n'));
console.log('APPFORGE_R64_EXPO_PINNED_API26_LINKER=VERIFIED');
JS_ROUTE
printf '%s\n' '#include <unistd.h>' 'int appforge_ndk_probe(void) { return (int)getpid(); }' > "$WORK/probe.c"
if ! "$LINKER" --target=aarch64-linux-android26 -shared -fPIC \
    -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384 \
    "$WORK/probe.c" -o "$WORK/probe.so";then
    echo 'APPFORGE_R64_EXPO_API26_LINK=FAIL' >&2
    exit 69
fi
"$NODE" - "$WORK/probe.so" <<'JS_ELF'
const fs=require('node:fs'), assert=require('node:assert/strict');
const path=process.argv[2], st=fs.lstatSync(path);
assert(st.isFile()&&st.size>=64&&st.size<=67108864,'Unsafe ELF');
const b=fs.readFileSync(path), n=b.length;
const u16=o=>b.readUInt16LE(o), u32=o=>b.readUInt32LE(o), u64=o=>{const x=b.readBigUInt64LE(o);assert(x<=BigInt(Number.MAX_SAFE_INTEGER));return Number(x);};
assert(b.subarray(0,6).equals(Buffer.from([127,69,76,70,2,1]))&&u16(16)===3&&u16(18)===183,'Expected ARM64 ELF shared library');
const ph=u64(32), pe=u16(54), pc=u16(56);
assert(pe===56&&pc>=1&&pc<=1024&&ph+pe*pc<=n,'Invalid program headers');
let loads=0;
for(let i=0;i<pc;i++){const p=ph+i*pe;if(u32(p)!==1)continue;loads++;
  const off=u64(p+8), va=u64(p+16), fsz=u64(p+32), msz=u64(p+40), align=u64(p+48);
  assert(align>=16384&&Number.isInteger(Math.log2(align))&&off%16384===va%16384&&off+fsz<=n&&fsz<=msz,'16KiB load alignment');
}
assert(loads>0,'No load segments');
const sh=u64(40), se=u16(58), sc=u16(60);
assert(se===64&&sc>=1&&sc<=4096&&sh+se*sc<=n,'Invalid section headers');
let seen=0, found=false;
for(let i=0;i<sc;i++){const s=sh+i*se;if(u32(s+4)!==11)continue;
  const off=u64(s+24), len=u64(s+32), link=u32(s+40), ent=u64(s+56);
  assert(ent===24&&len%ent===0&&off+len<=n&&link<sc,'Invalid dynsym');
  seen+=len/24;assert(seen<=100000,'Too many symbols');
  const t=sh+link*se, start=u64(t+24), size=u64(t+32);
  assert(u32(t+4)===3&&start+size<=n,'Invalid dynstr');
  for(let p=off;p<off+len;p+=ent){const name=u32(p),info=b[p+4],other=b[p+5],index=u16(p+6);
    if(!index||![1,2].includes(info>>4)||(info&15)!==2||![0,3].includes(other&3))continue;
    assert(name<size,'Invalid symbol name');
    const end=b.indexOf(0,start+name);
    assert(end>=start+name&&end<start+size&&end-start-name<=1024,'Invalid symbol string');
    if(b.toString('ascii',start+name,end)==='appforge_ndk_probe')found=true;
  }
}
assert(found,'Missing defined probe export');
console.log('APPFORGE_R64_EXPO_API26_ELF_16K=PASS');
JS_ELF
echo 'APPFORGE_R64_EXPO_NO_PYTHON_FUNCTIONAL_LINK=PASS'
echo 'APPFORGE_FUNCTIONAL_PASS:EXPO_REACT_NATIVE'
