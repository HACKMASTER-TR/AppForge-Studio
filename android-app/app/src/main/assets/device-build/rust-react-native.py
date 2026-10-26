#!/usr/bin/env python3
"""Opt-in RN0.81/Expo54 legacy-bridge JSI Rust payload preparation.
The project authors its C++/Java registration/plugin. No generated JS injection.
"""
import argparse,json,os,pathlib,shutil
HERE=pathlib.Path(__file__).parent

def trusted(name):
    scope={'__name__':'appforge_trusted_tool','__file__':str(HERE/name)}
    exec(compile((HERE/name).read_bytes(),str(HERE/name),'exec'),scope)
    return scope

def config(root):
    p=root/'appforge-rust-jsi.json'
    if p.is_symlink() or not p.is_file() or p.stat().st_size>4096:raise ValueError('Explicit Rust JSI binding required')
    j=json.loads(p.read_text())
    schema=j.get('schema');turbo=j.get('architecture')=='turbomodule' if schema==2 else False
    expected={'schema':schema,'library':'appforge_jsi','bridgeLibrary':'appmodules' if turbo else 'appforge_rust_jsi','reactNative':'0.81','expoSdk':j.get('expoSdk'),'minApi':26}
    if schema==2:expected['architecture']=j.get('architecture')
    if schema not in (1,2) or j!=expected or j['expoSdk'] not in (0,54) or schema==1 and j['expoSdk']!=54 or schema==2 and j['architecture'] not in ('legacy','turbomodule'):raise ValueError('Explicit reviewed RN0.81 architecture binding required')
    trusted('rust-project.py')['validate'](root)
    names=['RustTurboModule.cpp','RustTurboModule.h','OnLoad.cpp','CMakeLists.txt'] if turbo else ['RustJsi.cpp','RustJsiModule.java','RustJsiPackage.java','CMakeLists.txt']
    for name in names:
        p=root/'native'/name
        if p.is_symlink() or not p.is_file() or not 1<=p.stat().st_size<=128*1024:raise ValueError('Authored native JSI bridge/registration missing')
    package_file=root/'package.json'
    if not package_file.is_file() or package_file.stat().st_size>1024*1024:raise ValueError('Package manifest bound')
    package=json.loads(package_file.read_text());deps=package.get('dependencies',{})
    import re
    if not re.fullmatch(r'0\.81\.\d+',deps.get('react-native','')):raise ValueError('Exact reviewed React Native family required')
    if j['expoSdk']==54:
        if not re.fullmatch(r'54\.\d+\.\d+',deps.get('expo','')):raise ValueError('Exact reviewed Expo family required')
        app_file=root/'app.json'
        if not app_file.is_file() or app_file.stat().st_size>1024*1024:raise ValueError('Expo app manifest bound')
        app=json.loads(app_file.read_text()).get('expo',{})
        if app.get('newArchEnabled') is not turbo:raise ValueError('Architecture flag must match explicit Rust binding')
    else:
        prop=root/'android/gradle.properties'
        if prop.is_symlink() or not prop.is_file() or prop.stat().st_size>65536:raise ValueError('Authored standalone React Native Android project required')
        flags=re.findall(r'^newArchEnabled\s*=\s*(true|false)\s*$',prop.read_text(),re.M)
        if flags!=['true' if turbo else 'false']:raise ValueError('Standalone architecture flag must match Rust binding')
    return j

def prepare(root,prefix,linker,vendor,export):
    binding=config(root);builder=trusted('rust-project.py');android=trusted('rust-android.py')
    os.environ['RUSTFLAGS']='-C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wl,-z,common-page-size=16384 -C link-arg=-Wl,-soname,libappforge_jsi.so'
    result=builder['build'](root,'aarch64-linux-android',prefix,True,linker,vendor)
    if len(result['artifacts'])!=1 or pathlib.Path(result['artifacts'][0]['path']).name!='libappforge_jsi.so':raise ValueError('Exactly the declared Rust JSI payload required')
    artifact=root/result['artifacts'][0]['path']
    if 'appforge_jsi_answer' not in android['defined_exports'](artifact) or android['dynamic_libraries'](artifact)[0]!='libappforge_jsi.so':raise ValueError('Defined Rust JSI export / SONAME missing')
    if export:
        dest=root/'appforge-rust'
        if dest.exists() or dest.is_symlink():raise ValueError('Previous/foreign export preserved; fresh private copy required')
        dest.mkdir();shutil.copyfile(artifact,dest/artifact.name)
        receipt=dict(schema=2,target='aarch64-linux-android',integration=('react-native-turbomodule' if binding.get('architecture')=='turbomodule' else 'react-native-jsi'),minApi=26,sourceSha256=builder['source_digest'](root),file=artifact.name,sha256=builder['digest'](artifact))
        with (dest/'artifacts.json').open('w') as f:json.dump(receipt,f);f.flush();os.fsync(f.fileno())
    return result

if __name__=='__main__':
    ap=argparse.ArgumentParser();ap.add_argument('project',nargs='?');ap.add_argument('--prefix',default='/opt/appforge-device/rust-1.85.1');ap.add_argument('--base-prefix');ap.add_argument('--prepare-target',action='store_true');ap.add_argument('--cache');ap.add_argument('--check-project',action='store_true');ap.add_argument('--linker');ap.add_argument('--sdk',default='/opt/appforge-device/expo-sdk-36');ap.add_argument('--vendor');ap.add_argument('--export',action='store_true');ap.add_argument('--source-execution-consent',action='store_true');a=ap.parse_args();android=trusted('rust-android.py')
    if a.prepare_target:android['install_target'](pathlib.Path(a.prefix).absolute(),pathlib.Path(a.cache),pathlib.Path(a.base_prefix).absolute() if a.base_prefix else None)
    elif a.check_project:config(pathlib.Path(a.project).absolute());print('AUTHORED_JSI_BINDINGS_VALIDATED_NO_EXECUTION')
    else:
        if not a.source_execution_consent:raise ValueError('Explicit source execution consent required')
        print(json.dumps(prepare(pathlib.Path(a.project).absolute(),pathlib.Path(a.prefix).absolute(),a.linker or android['discover_linker'](pathlib.Path(a.sdk)),pathlib.Path(a.vendor).absolute() if a.vendor else None,a.export)))
