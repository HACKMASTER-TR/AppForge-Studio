'use strict';
const fs=require('fs'), path=require('path');
const base=__dirname;
const checks=[
  ['separate settings modules', 'settings.gradle.kts', 'include(":app", ":nativeprobe")'],
  ['base registers feature', 'app/build.gradle.kts', 'dynamicFeatures += setOf(":nativeprobe")'],
  ['feature dynamic plugin', 'nativeprobe/build.gradle.kts','id("com.android.dynamic-feature")'],
  ['feature Play delivery classpath', 'nativeprobe/build.gradle.kts','implementation("com.google.android.play:feature-delivery:2.1.0")'],
  ['PFD on-demand manifest', 'nativeprobe/src/main/AndroidManifest.xml', '<dist:on-demand />'],
  ['no feature exports', 'nativeprobe/src/main/AndroidManifest.xml', 'android:exported="false"'],
  ['SplitCompat bootstrap', 'app/src/main/java/com/appforge/v155proof/ProofApplication.java', 'SplitCompatApplication'],
  ['SplitInstallManager', 'app/src/main/java/com/appforge/v155proof/MainActivity.java','SplitInstallManagerFactory'],
  ['feature activity', 'nativeprobe/src/main/java/com/appforge/v155proof/nativeprobe/ProbeActivity.java','SplitCompat.installActivity'],
  ['native library loading', 'nativeprobe/src/main/java/com/appforge/v155proof/nativeprobe/ProbeActivity.java','System.loadLibrary("v155proof")'],
  ['JNI proof result', 'nativeprobe/src/main/cpp/probe.c','APPFORGE_V155_PFD_NATIVE_PASS'],
  ['no production workflow', 'ci-build-example.yml.txt', 'NON-ACTIVE']
];
let pass=0;
for(const [label,p,needle] of checks) {
  const ok=fs.readFileSync(path.join(base,p),'utf8').includes(needle);
  console.log('CONTRACT_'+label.replace(/[^a-zA-Z0-9]/g,'_').toUpperCase()+'='+(ok?'PASS':'FAIL'));
  if(ok)pass++;
}
console.log('PFD_FIXTURE_CONTRACTS='+pass+'/'+checks.length);
if(pass!==checks.length)process.exitCode=1;
