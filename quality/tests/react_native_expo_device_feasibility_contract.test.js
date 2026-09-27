import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const detector = await readFile(new URL("../../android-app/app/src/main/java/com/appforge/studio/io/ProjectTechnologyDetector.kt", import.meta.url), "utf8");
const capabilities = await readFile(new URL("../../android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildCapabilities.kt", import.meta.url), "utf8");
const engine = await readFile(new URL("../../android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt", import.meta.url), "utf8");
const agentPreparer = await readFile(new URL("../../android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentBuildProjectPreparer.kt", import.meta.url), "utf8");

test("Expo detector promotes only accepted SDK54 RN0.81 family", () => {
  assert.match(detector, /id = "expo"[\s\S]{0,240}buildEngine = "expo"/);
  assert.match(detector, /hasMajorMinor\(expoSpec, 54, 0\)/);
  assert.match(detector, /hasMajorMinor\(reactNativeSpec, 0, 81\)/);
  assert.match(detector, /buildReady = acceptedExpo/);
  assert.match(detector, /id = "react-native"[\s\S]{0,240}buildEngine = "react-native"[\s\S]{0,180}buildReady = false/);
});

test("Expo APK AAB is READY while standalone React Native stays experimental", () => {
  const e = capabilities.slice(capabilities.indexOf('engine =\n                    "expo"'), capabilities.indexOf('engine =\n                    "expo"') + 1200);
  assert.match(e, /DeviceArtifactKind\.APK/);
  assert.match(e, /DeviceArtifactKind\.AAB/);
  assert.match(e, /DeviceBuildSupport\.READY/);
  const r = capabilities.slice(capabilities.indexOf('engine =\n                    "react-native"'), capabilities.indexOf('engine =\n                    "react-native"') + 1000);
  assert.match(r, /readyOutputs\s*=\s*\n\s*emptySet\(\)/);
  assert.match(r, /DeviceBuildSupport\.EXPERIMENTAL/);
});

test("Expo uses normal READY gate and standalone React Native stays blocked", () => {
  assert.doesNotMatch(engine, /expoAcceptanceProbe/);
  assert.doesNotMatch(engine, /BuildConfig\.DEBUG[\s\S]{0,220}sourceEngine == "expo"/);
  assert.match(engine, /sourceEngine == "expo"[\s\S]{0,220}draft\.sourceBuildReady/);
  assert.match(engine, /"expo"\s*->\s*buildExpoProject/);
  assert.doesNotMatch(engine, /"react-native"\s*->/);
});

test("Unified Agent keeps canonical Expo engine naming", () => {
  assert.doesNotMatch(agentPreparer, /expo-android/);
  assert.match(agentPreparer, /AppForgeAgentPlatform\.REACT_NATIVE\s*->\s*"expo"/);
});
