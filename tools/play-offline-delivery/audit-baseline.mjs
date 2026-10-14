#!/usr/bin/env node
/**
 * V15.5 source-only audit. Performs ONLY synchronous file reads and stdout writes.
 * Does not access GitHub, subprocesses, build tools, user projects, device or Play.
 * Run from repository root:
 *     node tools/play-offline-delivery/audit-baseline.mjs
 * Or pass a checked-out repository directory as argument.
 * Snapshot checks are expected to change when feature implementation begins;
 * this is NOT a permanent CI regression gate.
 */
import fs from 'node:fs';
import path from 'node:path';

const repo = path.resolve(process.argv[2] || process.cwd());
const sources = {
  manager: 'android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt',
  runtime: 'android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildRuntimeV3.kt',
  linux: 'android-app/app/src/main/java/com/appforge/studio/terminal/LinuxRuntimeFoundation.kt',
  proroot: 'android-app/app/src/main/java/com/appforge/studio/terminal/ProrootRuntimeContract.kt',
  installer: 'android-app/app/src/main/assets/device-build/install-toolchain.sh',
  prewarm: 'android-app/app/src/main/assets/device-build/prepare-offline-pack.sh',
  node: 'android-app/app/src/main/assets/device-build/build-node.sh',
  expo: 'android-app/app/src/main/assets/device-build/build-expo.sh',
  native: 'android-app/app/src/main/assets/device-build/install-windows-native-toolchain.sh',
  winhost: 'android-app/app/src/main/java/com/appforge/studio/build/WindowsPortableHostStore.kt',
  gradle: 'android-app/app/build.gradle.kts',
  settings: 'android-app/settings.gradle.kts',
  play: 'android-app/app/src/main/java/com/appforge/studio/play/PlayPlatformCapabilities.kt',
  service: 'android-app/app/src/main/java/com/appforge/studio/play/PlayPlatformServices.kt',
  screen: 'android-app/app/src/main/java/com/appforge/studio/OfflineBuildPackScreen.kt',
  main: 'android-app/app/src/main/java/com/appforge/studio/MainActivity.kt',
  python: 'android-app/app/src/main/assets/device-build/python-template/build.gradle.kts',
};
const texts = {};
const filesMissing = [];
for (const [key, name] of Object.entries(sources)) {
  const full = path.join(repo, name);
  try { texts[key] = fs.readFileSync(full, 'utf8'); }
  catch { texts[key] = ''; filesMissing.push(name); }
}
const checks = [];
const add = (id, condition, evidence, severity = 'info') => {
  checks.push({ id, result: condition ? 'MATCH' : 'CHANGED_OR_MISSING', severity, evidence });
};
add('source.ubuntu.24_04_4', /24\.04\.4/.test(texts.linux), sources.linux);
add('source.proroot.arm64_only', /supportedAbi\s*=\s*"arm64-v8a"/.test(texts.proroot), sources.proroot, 'high');
add('source.jdk.temurin17_arm64', /OpenJDK17U-jdk_aarch64_linux_hotspot_17\.0\.20\.1_1\.tar\.gz/.test(texts.installer), sources.installer);
add('source.node.expo22', /NODE_VERSION="22\.23\.3"/.test(texts.installer), sources.installer);
add('source.node.web_apt_unpinned', /apt-get install -y --no-install-recommends nodejs npm/.test(texts.installer), sources.installer, 'medium');
add('source.gradle.9_3_1', /ensure_gradle "9\.3\.1"/.test(texts.installer), sources.installer);
add('source.gradle.8_14_3', /ensure_gradle "8\.14\.3"/.test(texts.installer), sources.installer);
add('source.agp.9_1_1', /version "9\.1\.1"/.test(texts.prewarm), sources.prewarm);
add('source.sdk.37_0', /platforms\/android-37\.0/.test(texts.installer), sources.installer);
add('source.build_tools.third_party_arm64', /Commit451\/android-arm-build-tools/.test(texts.installer), sources.installer, 'high');
add('source.expo.ndk_r27b', /EXPO_ARM64_NDK_VERSION="27\.1\.12297006"/.test(texts.installer), sources.installer);
add('source.expo.ndk_arm64_wrapper', /APPFORGE_EXPO_ARM64_NDK_CLANG_WRAPPER_V3/.test(texts.installer), sources.installer);
add('source.python.3_12', /python3\.12/.test(texts.installer), sources.installer);
add('source.python.chaquopy17', /version "17\.0\.0"/.test(texts.python), sources.python);
add('source.windows.native_mingw', /gcc-mingw-w64-x86-64-posix/.test(texts.native), sources.native);
add('source.windows.portable_host_size', /375_039_759L/.test(texts.winhost), sources.winhost);
add('routing.fast_html_bypass', /FastHtmlApkPolicy\.isCandidate/.test(texts.manager), sources.manager);
add('runtime.device_no_backup_isolation', /context\.noBackupFilesDir/.test(texts.runtime), sources.runtime);
add('readiness.marker_only_snapshot', /markerReady\(/.test(texts.manager), sources.manager, 'high');
add('size.download_estimate_not_measurement', /1_800_000_000L/.test(texts.manager), sources.manager);
add('size.installed_estimate_not_measurement', /4_500_000_000L/.test(texts.manager), sources.manager);
add('ux.first_run_skip', /ŞİMDİLİK ATLA/.test(texts.screen), sources.screen);
add('play.asset_delivery_code_ready', /"asset_delivery",\s*PlayCapabilityState\.CODE_READY/.test(texts.play), sources.play);
add('play.feature_delivery_code_ready', /"feature_delivery",\s*PlayCapabilityState\.CODE_READY/.test(texts.play), sources.play);
add('play.no_active_feature_or_asset_pack_module', !/dynamicFeatures\s*=|assetPacks\s*=/.test(texts.gradle) && !/com\.android\.(dynamic-feature|asset-pack)/.test(texts.settings), `${sources.gradle} + ${sources.settings}`, 'high');

const report = {
  audit: 'APPFORGE_V155_PLAY_OFFLINE_BASELINE_SOURCE_ONLY',
  repo_root: repo,
  source_basis: '63e496913332cf2e2ae96b2e67ae7414226f09fe',
  files_read: Object.keys(sources).length - filesMissing.length,
  files_missing: filesMissing,
  checks_total: checks.length,
  checks_matched: checks.filter(c => c.result === 'MATCH').length,
  caveat: 'Static source checks only; no policy, physical Android, or real Play delivery acceptance.',
  checks,
};
process.stdout.write(JSON.stringify(report, null, 2) + '\n');
if (filesMissing.length || checks.some(c => c.result !== 'MATCH')) process.exitCode = 2;
