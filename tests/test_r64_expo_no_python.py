#!/usr/bin/env python3
"""Expo-only functional gate: actual Node ELF validator positive/negative fixtures."""
from pathlib import Path
import re, shutil, struct, subprocess, tempfile
root=Path(__file__).resolve().parents[1]
base=root/'android-app/app/src/main/assets/device-build'
path=base/'expo-functional-test-r64.sh'
s=path.read_text()
subprocess.run(['sh','-n',str(path)],check=True)
assert '/usr/bin/python' not in s and 'rust-android.py' not in s
assert 'appforge-rust-api26-crt-absolute-r52' in s
assert 'APPFORGE_R64_EXPO_NO_PYTHON_FUNCTIONAL_LINK=PASS' in s
assert 'APPFORGE_FUNCTIONAL_PASS:EXPO_REACT_NATIVE' in s
for filename in ['OfflineBuildPackManager.kt','IsolatedModuleInstallerV3.kt']:
    kt=(root/'android-app/app/src/main/java/com/appforge/studio/build'/filename).read_text()
    assert 'id == OfflineModuleId.EXPO_REACT_NATIVE' in kt
    assert '/bin/sh /workspace/runtime/expo-functional-test-r64.sh' in kt
    assert '/bin/sh /workspace/runtime/module-functional-test.sh' in kt
g=(root/'android-app/app/build.gradle.kts').read_text()
assert 'output.versionCode.set(581)' in g and '5.0.79-r64-expo-repair' in g
# Execute the real parser from the shipped script, not a duplicate implementation.
node=shutil.which('node')
assert node,'Node required for regression test'
route=re.search(r"(?ms)^.*<<'JS_ROUTE'\n(.*?)^JS_ROUTE$",s)
elf=re.search(r"(?ms)^.*<<'JS_ELF'\n(.*?)^JS_ELF$",s)
assert route and elf
for script in (route.group(1),elf.group(1)):
    subprocess.run([node,'--check','-'],input=script,text=True,check=True)
with tempfile.TemporaryDirectory() as td:
    p=Path(td)/'probe.so'
    # Minimal ELF64/ARM64 ET_DYN parser fixture, not a real compiler output.
    b=bytearray(512)
    b[:6]=b'\x7fELF\x02\x01'
    struct.pack_into('<HH',b,16,3,183)
    struct.pack_into('<QQ',b,32,64,128)
    struct.pack_into('<HHHH',b,54,56,1,64,3)
    struct.pack_into('<IIQQQQQQ',b,64,1,5,0,0,0,512,512,16384)
    struct.pack_into('<IIQQQQIIQQ',b,192,0,11,0,0,320,48,2,0,8,24)
    names=b'\0appforge_ndk_probe\0'
    struct.pack_into('<IIQQQQIIQQ',b,256,0,3,0,0,384,len(names),0,0,1,0)
    struct.pack_into('<IBBHQQ',b,344,1,0x12,0,1,1,1)
    b[384:384+len(names)]=names
    for label,offset,fmt,val,expected in [('valid',0,None,None,True),('wrong-machine',18,'<H',62,False),('wrong-type',16,'<H',2,False),('bad-align',112,'<Q',4096,False),('missing-export',350,'<H',0,False),('bad-dynsym',192+56,'<Q',0,False)]:
        x=bytearray(b)
        if fmt:struct.pack_into(fmt,x,offset,val)
        p.write_bytes(x)
        res=subprocess.run([node,'-',str(p)],input=elf.group(1),text=True,capture_output=True)
        assert (res.returncode==0)==expected,(label,res.stdout,res.stderr)
print('R64_EXPO_NO_PYTHON_CONTRACTS=PASS_NOT_PHYSICAL_ACCEPTANCE')
