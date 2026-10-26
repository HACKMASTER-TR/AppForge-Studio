import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {spawnSync} from 'node:child_process';
const base=new URL('../../',import.meta.url);
const read=p=>fs.readFileSync(new URL(p,base),'utf8');
test('Rust safety tests exercise lock, consent, archives and artifact protection',()=>{
 const result=spawnSync('python3',['quality/rust_integration_test.py'],{cwd:base,encoding:'utf8'});
 assert.equal(result.status,0,result.stdout+result.stderr);
});
test('Rust native module reuses verified downloader and existing generation ownership',()=>{
 const manager=read('android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt');
 assert.match(manager,/id==OfflineModuleId.RUST/);assert.match(manager,/install-rust-module.py/);
 assert.match(manager,/VerifiedModuleDownload.download/);assert.match(manager,/ModuleOwnershipStore\(staged\).record/);
});
test('Compact module screen keeps technical controls behind details and distinct navigation',()=>{
 const ui=read('android-app/app/src/main/java/com/appforge/studio/OfflineBuildPackScreen.kt');
 assert.match(ui,/ModuleCompactHeader/);assert.match(ui,/if\(expanded && lifecycle!=null\)/);
 for(const section of ['network','history','diagnostics'])assert.ok(ui.includes(`section=="${section}"`));
 assert.match(ui,/rememberSaveable/);assert.match(ui,/pendingLifecycle=module.id to "rollback"/);
});
test('Cargo vendor adapter validates real directory-source checksums and fails closed',()=>{
 const result=spawnSync('python3',['quality/rust_vendor_test.py'],{cwd:base,encoding:'utf8'});
 assert.equal(result.status,0,result.stdout+result.stderr);
});
test('In-app Rust execution separates managed downloads from network-disconnected source execution',()=>{
 const prep=read('android-app/app/src/main/java/com/appforge/studio/build/ProjectDependencyPreparation.kt');
 const card=read('android-app/app/src/main/java/com/appforge/studio/ProjectDependencyPreparationCard.kt');
 assert.match(prep,/VerifiedModuleDownload.download\(url,sha,archive,OfflineModuleId.RUST/);
 assert.match(prep,/connectivity.allNetworks.isEmpty/);assert.match(prep,/networkSeen.get/);
 assert.match(prep,/RustPreparedProjects\(storeRoot\).publish/);assert.match(prep,/stage\?\.close\(\)/);
 assert.match(prep,/CARGO|Cargo/);assert.match(card,/allowMobileOnce/);assert.match(card,/store\(\).restore\(draft\)/);
});

test('Rust Python prerequisite uses existing pinned catalog without Android SDK installation',()=>{
 const script=read('android-app/app/src/main/assets/device-build/install-rust-prerequisites.sh');
 const catalog=read('android-app/app/src/main/assets/device-build/apt-module-packages-v1.tsv');
 assert.match(script,/APPFORGE_MODULE_ID:-.*RUST/);assert.match(script,/as_configure_original/);
 assert.match(script,/as_owner=RUST/);assert.match(script,/python3=3.12.3-0ubuntu2.1/);
 assert.match(script,/appforge_snapshot_apt_get install/);assert.ok(catalog.includes('python3\t3.12.3-0ubuntu2.1'));
 assert.doesNotMatch(script,/install-toolchain|sdkmanager|install-node|allow-unauthenticated|--insecure/);
});
test('Android Rust validates real ELF JNI exports, target integrity and separate consent',()=>{
 const result=spawnSync('python3',['quality/rust_android_test.py'],{cwd:base,encoding:'utf8'});
 assert.equal(result.status,0,result.stdout+result.stderr);
 const engine=read('android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt');
 assert.match(engine,/RustProjectPolicy.stageAndroidJni\(draft,project\)/);
 assert.match(engine,/RustProjectPolicy.verifyAndroidJniPackage\(draft,kind,file\)/);
 const prep=read('android-app/app/src/main/java/com/appforge/studio/build/ProjectDependencyPreparation.kt');
 assert.match(prep,/--base-prefix \/opt\/appforge-device\/rust-1.85.1/);
 assert.match(prep,/compilerSnapshot==RuntimeGenerationStore/);
});
test('JNI publication checks real SDK and target stays outside active module ownership',()=>{
 const engine=read('android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt');
 assert.match(engine,/applicationInfo\?\.minSdkVersion/);
 assert.match(engine,/FastHtmlAabProtoManifest.minimumSdkVersion\(bytes\)>=26/);
 const prep=read('android-app/app/src/main/java/com/appforge/studio/build/ProjectDependencyPreparation.kt');
 assert.match(prep,/val compilerName="rust-android-toolchain-\$targetIndex"/);
 assert.match(prep,/--prefix \/workspace\/\$compilerName/);
 const target=JSON.parse(read('android-app/app/src/main/assets/device-build/rust-android-target-v1.json'));
 assert.equal(target.artifact.sha256,'3a165858f18b13d5eeed93a57c7c658f4a7ab03ce3e47239e932a59d1b2b454f');
 assert.equal(target.target,'aarch64-linux-android');
});
test('Rust JNI example ships verified Gradle checksums and locking without a trust bypass',()=>{
 const root='examples/rust-android-jni/';
 const provenance=JSON.parse(read(root+'gradle/trust-provenance.json'));
 const metadata=read(root+'gradle/verification-metadata.xml');
 assert.equal(provenance.artifactsVerified,300);
 assert.match(metadata,/<verify-metadata>true<\/verify-metadata>/);
 assert.doesNotMatch(metadata,/<trusted-artifacts>|<trust /);
 assert.match(read(root+'app/build.gradle.kts'),/dependencyLocking \{ lockAllConfigurations\(\) \}/);
 assert.match(read(root+'app/gradle.lockfile'),/org.jetbrains.kotlin:kotlin-stdlib:2.2.10=/);
});
test('Portable Rust payload is checked before packaging and after final signing',()=>{
 const p=read('android-app/app/src/main/java/com/appforge/studio/build/WindowsPortableExePackager.kt');
 assert.match(p,/RustProjectPolicy.requireWebSite/);assert.match(p,/RustProjectPolicy.verifyWebArchive/);
 assert.match(p,/verifyRustFinalArtifact\(draft,part\)/);
 const engine=read('android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt');
 assert.match(engine,/verifyRustFinalArtifact\(draft,finalWindowsExe\)/);
});
test('Windows Rust adapter rejects DLL relabelling, unsafe PE and implicit source execution',()=>{
 const r=spawnSync('python3',['-B','quality/rust_windows_test.py'],{cwd:base,encoding:'utf8'});
 assert.equal(r.status,0,r.stdout+r.stderr);
 const p=read('android-app/app/src/main/java/com/appforge/studio/build/ProjectDependencyPreparation.kt');
 assert.match(p,/val targetId=RustProjectPolicy.preparationTarget\(targetDraft\)/);
 assert.doesNotMatch(p,/require\(draft.sourceBuildEngine in setOf\("webview-static","node-web"\)\)/);
 const engine=read('android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt');
 assert.match(engine,/RustProjectPolicy.stageWindowsNative\(draft,canonicalWorkspace\)/);
 assert.match(engine,/RustProjectPolicy.digest\(target\)==RustProjectPolicy.digest\(RustProjectPolicy.requireWindowsNative\(draft\)\)/);
});
test('PyO3 adapter requires exact Python ABI, locked crate and verified target library',()=>{
 const r=spawnSync('python3',['-B','quality/rust_python_test.py'],{cwd:base,encoding:'utf8'});
 assert.equal(r.status,0,r.stdout+r.stderr);
 const engine=read('android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt');
 assert.match(engine,/RustProjectPolicy.stagePythonModule\(draft,project\)/);
 assert.match(engine,/RustProjectPolicy.verifyPythonPackage\(draft,kind,file\)/);
});

test('Universal Rust prepares all selected roots and publishes only one verified private project',()=>{
 const prep=read('android-app/app/src/main/java/com/appforge/studio/build/ProjectDependencyPreparation.kt');
 assert.match(prep,/for\(\(targetIndex,pair\) in projects.withIndex\(\)\)/);
 assert.match(prep,/RustPreparedProjects\(storeRoot\).publish\(draft,workspace,plan.fingerprint\)/);
 const engine=read('android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt');
 assert.match(engine,/RustProjectPolicy.stageAndroidJni\(androidDraft,androidProject\)/);
 assert.match(engine,/draft = androidDraft/);
 const portable=read('android-app/app/src/main/java/com/appforge/studio/build/WindowsPortableExePackager.kt');
 assert.match(portable,/RustProjectPolicy.webPayloadDraft\(draft\)/);
});
test('DLL export stays a revalidated library and never bypasses EXE publication',()=>{
 const card=read('android-app/app/src/main/java/com/appforge/studio/ProjectDependencyPreparationCard.kt');
 assert.match(card,/CreateDocument\("application\/vnd.microsoft.portable-executable"\)/);
 assert.match(card,/store\(\).restoreLibrary\(draft\)/);
 assert.match(card,/copied==size/);
 const store=read('android-app/app/src/main/java/com/appforge/studio/build/RustPreparedProjects.kt');
 assert.match(store,/PREPARED_LIBRARY_NOT_APPLICATION/);
 const policy=read('android-app/app/src/main/java/com/appforge/studio/build/RustProjectPolicy.kt');
 assert.match(policy,/require\(!isWindowsLibrary\(draft\)\)/);
 assert.match(policy,/windowsDllExports\(file\)/);
});
test('Expo Rust requires authored registration, reviewed runtime family and explicit legacy bridge',()=>{
 const r=spawnSync('python3',['-B','quality/rust_jsi_test.py'],{cwd:base,encoding:'utf8'});
 assert.equal(r.status,0,r.stdout+r.stderr);
 const plugin=read('examples/rust-expo-jsi/withRustJsi.cjs');
 assert.match(plugin,/minSdkVersion 26/);assert.match(plugin,/ANDROID_STL=c\+\+_shared/);
 const policy=read('android-app/app/src/main/java/com/appforge/studio/build/RustProjectPolicy.kt');
 assert.match(policy,/androidDynamicLibraries\(file\).first=="libappforge_jsi.so"/);
 assert.match(policy,/libappforge_jsi.so","libjsi.so/);
});

test('Cargo workspace resolver binds members, inherited fields and reviewed local paths',()=>{
 const r=spawnSync('python3',['-B','quality/rust_workspace_test.py'],{cwd:base,encoding:'utf8'});
 assert.equal(r.status,0,r.stdout+r.stderr);
 const prep=read('android-app/app/src/main/java/com/appforge/studio/build/ProjectDependencyPreparation.kt');
 assert.match(prep,/"rust-layout.py"/);
});
test('Git bundles require pinned objects, consent and file-only Git transport',()=>{
 const r=spawnSync('python3',['-B','quality/rust_git_test.py'],{cwd:base,encoding:'utf8'});
 assert.equal(r.status,0,r.stdout+r.stderr);
 const prep=read('android-app/app/src/main/java/com/appforge/studio/build/ProjectDependencyPreparation.kt');
 assert.match(prep,/gitMetadata && !offlineVerification/);
 assert.match(prep,/--offline-metadata-consent/);
});

test('Rust native module probes its real linker without Node or Expo installation',()=>{
 const script=read('android-app/app/src/main/assets/device-build/install-toolchain.sh');
 assert.match(script,/ENGINE.*rust-native/);assert.match(script,/rust-android.py --probe-linker --sdk/);
 const module=read('android-app/app/src/main/java/com/appforge/studio/build/OfflineModulePlan.kt');
 assert.match(module,/RUST_ANDROID_NDK.*setOf\(OfflineModuleId.ANDROID_CORE,OfflineModuleId.RUST\)/);
 const manager=read('android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt');
 assert.match(manager,/ModuleRecipeCompatibility.accepts/);
 assert.match(manager,/ModuleOwnershipStore\(root\).verifyNamespace\(id\)/);
});
test('Standalone RN consumes authored native project and explicit TurboModule registration',()=>{
 const script=read('android-app/app/src/main/assets/device-build/prepare-rn-rust.sh');
 assert.match(script,/ci --offline/);assert.doesNotMatch(script,/expo\/bin\/cli/);
 const provider=read('examples/rust-react-native-turbo/native/OnLoad.cpp');
 assert.match(provider,/NativeAppForgeRust/);
 assert.match(read('examples/rust-react-native-turbo/android/gradle.properties'),/^newArchEnabled=true$/m);
 assert.match(read('android-app/app/src/main/java/com/appforge/studio/build/ProjectDependencyEvidence.kt'),/"android-ndk","react-native","universal-cross-platform"/);
});
