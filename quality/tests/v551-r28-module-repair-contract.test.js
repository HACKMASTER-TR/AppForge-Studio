import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const source = p => fs.readFileSync(new URL('../../'+p, import.meta.url), 'utf8');
const win=source('android-app/app/src/main/assets/device-build/install-windows-native-toolchain.sh');
const cmake=source('android-app/app/src/main/assets/device-build/install-ndk-host-isolated-v7.sh');
const gradle=source('android-app/app/src/main/assets/device-build/prepare-offline-pack.sh');
test('Windows accepts only authentic exact-version Ubuntu driver and locked sh script',()=>{
  for(const x of ['SIGNED_ARCHIVE_VERIFIED=PASS','SIGNED_ARCHIVE_EXTRACTED=PASS',
    'APPFORGE_WINDOWS_${kind}_SIGNED_POSIX_SCRIPT=VERIFIED',
    'APPFORGE_WINDOWS_${kind}_SIGNED_ARM64_ELF=VERIFIED',
    "'#!/bin/sh'|'#!/bin/sh -e'",'dpkg-deb -f', 'sha256sum -c -',
    'SIGNED_DRIVER_RESTORED=PASS']) assert.ok(win.includes(x), x);
  for(const x of ['dpkg --force','rm -rf /var/lib/dpkg','apt-get -f install'])assert.ok(!win.includes(x),x);
});
test('CMake fallback uses only signed private modules and must pass real -P probe',()=>{
  for(const x of ['APPFORGE_NDK_HOST_PRIVATE_CMAKE_MODULE_LINK=CREATED',
     'APPFORGE_NDK_HOST_PRIVATE_CMAKE_MODULE_PROBE=PASS',
     'APPFORGE_NDK_HOST_CMAKE_NO_VERIFIED_LAUNCHER',
     '[ ! -L /share ]',
     'ln -s "$signed_cmake_data" /share/cmake-3.28',
     'APPFORGE_CMAKE_PROBE_MODE=loader-argv0',
     'sha256sum -c -']) assert.ok(cmake.includes(x),x);
  for(const x of ['LD_PRELOAD=','dpkg --force','apt-get install'])assert.ok(!cmake.includes(x),x);
});
test('Maven TLS retry pins TLSv1.2, retains trust, and is only attempted once',()=>{
  for(const x of ['gw_tls12=0','[ "$gw_tls12" -eq 0 ]',
   'Remote host terminated the handshake',
   '-Djdk.tls.client.protocols=TLSv1.2','-Dhttps.protocols=TLSv1.2',
   'APPFORGE_GRADLE_VERIFIED_TLS12_RETRY',
   'PKIX|certificate verify failed|certification path']) assert.ok(gradle.includes(x),x);
  for(const x of ['allowInsecureProtocol','https.protocols=TLSv1,','trustAll','checkServerTrusted() {}'])
    assert.ok(!gradle.includes(x),x);
});
