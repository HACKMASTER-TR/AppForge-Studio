"""Opt-in source/ELF contract. No runtime acceptance, downloads or Git writes."""
from pathlib import Path
import hashlib
import json
import re
import subprocess
import sys

HERE = Path(__file__).resolve().parent
PINS = json.loads((HERE / 'provenance.json').read_text())


def require(condition, message):
    if not condition:
        raise ValueError(message)


def verify_file(path, expected):
    require(path.is_file() and not path.is_symlink(), 'missing/symlink: ' + str(path))
    require(hashlib.sha256(path.read_bytes()).hexdigest() == expected, 'hash drift: ' + str(path))


def verify_source(project):
    verify_file(HERE / 'RustRuntimeModule.h', PINS['candidate_sha256'])
    for item in PINS['authored_files']:
        verify_file(project / item['path'], item['sha256'])


def verify_rn(project):
    rn = project / 'node_modules/react-native'
    require(json.loads((rn / 'package.json').read_text())['version'] == PINS['rn_version'], 'RN drift')
    require(json.loads((project / 'node_modules/expo/package.json').read_text())['version'] == PINS['expo_version'], 'Expo drift')
    for item in PINS['files']:
        verify_file(rn / item['path'], item['sha256'])


def prepare(project, temp):
    project, temp = project.resolve(), temp.resolve()
    require(project != temp and project.is_relative_to(temp), 'outside runner temp')
    verify_file(HERE / 'RustRuntimeModule.h', PINS['candidate_sha256'])
    verify_rn(project)
    app = project / 'android/app'
    jni = app / 'src/main/jni'
    require(not jni.exists(), 'refusing existing JNI source')
    source = project / 'node_modules/react-native/ReactAndroid/cmake-utils/default-app-setup/OnLoad.cpp'
    text = source.read_text()
    anchor = '  return autolinking_cxxModuleProvider(name, jsInvoker);'
    require(text.count(anchor) == 1, 'provider anchor drift')
    text = '#include "RustRuntimeModule.h"\n' + text.replace(anchor,
        '  if (auto module = appforgeRuntimeProvider(name, jsInvoker)) return module;\n' + anchor)
    gradle = app / 'build.gradle'
    g = gradle.read_text()
    require(g.count('src/main/cpp/CMakeLists.txt') == 1, 'CMake anchor drift')
    apps = list((app / 'src/main/java').rglob('MainApplication.kt'))
    require(len(apps) == 1, 'application ambiguity')
    java = apps[0].read_text()
    legacy = 'add(com.appforge.rustjsi.RustJsiPackage())'
    require(java.count(legacy) == 1, 'legacy package anchor drift')
    jni.mkdir(parents=True)
    (jni / 'RustRuntimeModule.h').write_bytes((HERE / 'RustRuntimeModule.h').read_bytes())
    (jni / 'OnLoad.cpp').write_text(text)
    (jni / 'CMakeLists.txt').write_text('''cmake_minimum_required(VERSION 3.22.1)
project(appmodules)
add_library(appforge_jsi SHARED IMPORTED)
set_target_properties(appforge_jsi PROPERTIES IMPORTED_NO_SONAME TRUE IMPORTED_LOCATION "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libappforge_jsi.so")
include(${REACT_ANDROID_DIR}/cmake-utils/ReactNative-application.cmake)
target_link_libraries(${CMAKE_PROJECT_NAME} appforge_jsi)
target_link_options(${CMAKE_PROJECT_NAME} PRIVATE "-Wl,--no-undefined")
''')
    gradle.write_text(g.replace('src/main/cpp/CMakeLists.txt', 'src/main/jni/CMakeLists.txt'))
    apps[0].write_text(java.replace(legacy, '// Phase12.1 C++ Turbo provider.'))


def inspect_elf(readelf, nm, rust, bridge, output, abi):
    require(abi == 'arm64-v8a', 'unsupported ABI')
    output.mkdir(parents=True, exist_ok=True)
    rows = []
    for name, path in [('rust', rust), ('bridge', bridge)]:
        require(path.is_file() and path.stat().st_size > 0, 'absent library: ' + str(path))
        data = {}
        for key, cmd in [('header', [readelf, '-h', str(path)]),
                         ('dynamic', [readelf, '-d', str(path)]),
                         ('demangled', [nm, '-D', '-C', str(path)]),
                         ('symbols', [nm, '-D', str(path)])]:
            proc = subprocess.run(cmd, text=True, capture_output=True, check=True)
            data[key] = proc.stdout
            (output / (name + '-' + key + '.log')).write_text(proc.stdout)
        require(re.search(r'Machine:\s+AArch64\b', data['header']), 'incorrect architecture')
        require(re.search(r'Class:\s+ELF64\b', data['header']), 'incorrect ELF class')
        require(re.search(r'Type:\s+DYN\b', data['header']), 'not shared ELF')
        rows.append({'name': name, 'path': str(path), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
                     'needed': re.findall(r'\(NEEDED\).*?\[(.*?)\]', data['dynamic']),
                     'undefined': [line for line in data['symbols'].splitlines() if re.search(r'\bU\s', line)]})
        if name == 'rust':
            require(re.search(r'\bT\s+appforge_jsi_answer$', data['symbols'], re.M), 'missing real Rust export')
        else:
            require(re.search(r'\bT\s+JNI_OnLoad$', data['symbols'], re.M), 'missing JNI_OnLoad entry')
            require(re.search(r'\bU\s+appforge_jsi_answer$', data['symbols'], re.M), 'missing Rust import')
            require('libappforge_jsi.so' in rows[-1]['needed'], 'missing Rust DT_NEEDED')
            require('libreactnative.so' in rows[-1]['needed'], 'missing RN DT_NEEDED')
            require('libjsi.so' in rows[-1]['needed'], 'missing JSI DT_NEEDED')
            require('libc++_shared.so' in rows[-1]['needed'], 'missing C++ runtime DT_NEEDED')
            require('libfbjni.so' in rows[-1]['needed'], 'missing fbjni DT_NEEDED')
            require('nativeInstall' not in data['symbols'], 'unsafe legacy JNI fallback')
    (output / 'abi.json').write_text(json.dumps({'status': 'ABI_LINK_ONLY', 'runtime': 'NOT_RUN',
        'jni_entry': 'JNI_OnLoad', 'libraries': rows,
        'resolution_contract': 'Android loader resolves dynamic imports from DT_NEEDED libraries; --no-undefined verifies build closure. Runtime loader/registration remains NOT_RUN.'}, indent=2) + '\n')


if __name__ == '__main__':
    mode = sys.argv[1]
    if mode == 'source':
        verify_source(Path(sys.argv[2]))
    elif mode == 'locks':
        for item in PINS['authored_files']:
            if item['path'] in {'Cargo.lock', 'package-lock.json', 'Cargo.toml', 'package.json', 'src/lib.rs'}:
                verify_file(Path(sys.argv[2]) / item['path'], item['sha256'])
    elif mode == 'prepare':
        prepare(Path(sys.argv[2]), Path(sys.argv[3]))
    elif mode == 'elf':
        inspect_elf(sys.argv[2], sys.argv[3], Path(sys.argv[4]), Path(sys.argv[5]), Path(sys.argv[6]), sys.argv[7])
    else:
        raise ValueError('unsupported mode')
