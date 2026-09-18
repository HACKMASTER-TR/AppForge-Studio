import test from "node:test";
import assert from "node:assert/strict";
import { promises as fs } from "fs";
import { inspectProjectToolchainFiles } from "../src/projectToolchainInspector.js";
import { preflightSourceToolchain, assertSourceToolchainSupported } from "../src/sourceToolchainRegistry.js";

const registry = JSON.parse(await fs.readFile(new URL("../source-worker-toolchain.json", import.meta.url), "utf8"));

function nexBrainFiles(overrides = {}) {
  return {
    "package.json": JSON.stringify({ dependencies: { expo: "~54.0.0", "react-native": "0.81.4" } }),
    "android/settings.gradle": "pluginManagement { repositories { gradlePluginPortal(); google(); mavenCentral() } }",
    "android/build.gradle": `buildscript { dependencies { classpath("com.android.tools.build:gradle:8.11.0") } }`,
    "android/app/build.gradle": `android {\n compileSdkVersion 36\n buildToolsVersion "36.0.0"\n ndkVersion "27.1.12297006"\n defaultConfig { minSdkVersion 24; targetSdkVersion 36 }\n externalNativeBuild { cmake { version "3.22.1" } }\n}`,
    "android/gradle/wrapper/gradle-wrapper.properties": "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.14.3-bin.zip",
    "android/app/CMakeLists.txt": "cmake_minimum_required(VERSION 3.22.1)",
    ...overrides
  };
}

test("NexBrain-like Expo project produces a routable capability set", () => {
  const inspection = inspectProjectToolchainFiles(nexBrainFiles(), { engine: "expo-android" });
  assert.equal(inspection.compileSdk, "36");
  assert.equal(inspection.targetSdk, "36");
  assert.equal(inspection.minSdk, "24");
  assert.equal(inspection.buildToolsVersion, "36.0.0");
  assert.equal(inspection.ndkVersion, "27.1.12297006");
  assert.equal(inspection.cmakeVersion, "3.22.1");
  assert.equal(inspection.gradleWrapperVersion, "8.14.3");
  assert.equal(inspection.androidGradlePluginVersion, "8.11.0");
  assert.equal(inspection.jdkMajor, "17");
  assert.equal(inspection.nativeAndroidProject, true);

  const result = preflightSourceToolchain({ inspection, engine: "expo-android", registry });
  assert.equal(result.ok, true, JSON.stringify(result.issues));
  for (const capability of [
    "source-family-expo-android",
    "android-api-36",
    "build-tools-36.0.0",
    "ndk-27.1.12297006",
    "cmake-3.22.1",
    "gradle-8.14.3",
    "java-17"
  ]) assert.ok(result.capabilities.includes(capability), capability);
});

for (const scenario of [
  ["unknown NDK", { "android/app/build.gradle": `android { compileSdk 36; ndkVersion "29.0.14033849" }` }, "ndk"],
  ["unknown Android API", { "android/app/build.gradle": `android { compileSdk 99 }` }, "android-platform"],
  ["unknown Build Tools", { "android/app/build.gradle": `android { compileSdk 36; buildToolsVersion "99.0.0" }` }, "build-tools"],
  ["unknown CMake", { "android/app/build.gradle": `android { compileSdk 36; externalNativeBuild { cmake { version "4.2.0" } } }` }, "cmake"]
]) {
  test(scenario[0] + " fails before Gradle", () => {
    const inspection = inspectProjectToolchainFiles(nexBrainFiles(scenario[1]), { engine: "android-gradle" });
    const result = preflightSourceToolchain({ inspection, engine: "android-gradle", registry });
    assert.equal(result.ok, false);
    assert.ok(result.issues.some(item => item.kind === scenario[2]), JSON.stringify(result.issues));
    assert.throws(() => assertSourceToolchainSupported(result), error => error?.code === "SOURCE_TOOLCHAIN_UNSUPPORTED" && error?.statusCode === 422);
  });
}

test("incompatible AGP and Gradle is rejected", () => {
  const inspection = inspectProjectToolchainFiles(nexBrainFiles({
    "android/build.gradle": `plugins { id("com.android.application") version "9.1.1" apply false }`,
    "android/gradle/wrapper/gradle-wrapper.properties": "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.14.3-bin.zip"
  }), { engine: "android-gradle" });
  const result = preflightSourceToolchain({ inspection, engine: "android-gradle", registry });
  assert.equal(result.ok, false);
  assert.ok(result.issues.some(item => item.kind === "agp-gradle"));
});

test("explicit unsupported JDK requirement is rejected", () => {
  const inspection = inspectProjectToolchainFiles(nexBrainFiles({
    "android/app/build.gradle": `java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }\nandroid { compileSdk 36 }`
  }), { engine: "android-gradle" });
  const result = preflightSourceToolchain({ inspection, engine: "android-gradle", registry });
  assert.equal(result.ok, false);
  assert.ok(result.issues.some(item => item.kind === "jdk"));
});

test("AGP can select a compatible trusted Gradle when wrapper metadata is absent", () => {
  const inspection = inspectProjectToolchainFiles({
    "android/settings.gradle": "rootProject.name = 'Legacy'",
    "android/build.gradle": `buildscript { dependencies { classpath("com.android.tools.build:gradle:8.11.0") } }`,
    "android/app/build.gradle": "android { compileSdk 36 }"
  }, { engine: "android-gradle" });
  const result = preflightSourceToolchain({ inspection, engine: "android-gradle", registry });
  assert.equal(result.ok, true, JSON.stringify(result.issues));
  assert.equal(result.selected.gradle, "8.14.3");
});

test("project without explicit toolchain versions stays compatible", () => {
  const inspection = inspectProjectToolchainFiles({
    "package.json": JSON.stringify({ dependencies: { expo: "~54.0.0", "react-native": "0.81.4" } })
  }, { engine: "expo-android" });
  const result = preflightSourceToolchain({ inspection, engine: "expo-android", registry });
  assert.equal(result.ok, true, JSON.stringify(result.issues));
  assert.equal(inspection.nativeAndroidProject, false);
  assert.equal(result.selected.gradle, "8.14.3");
  assert.equal(result.selected.jdk, "17");
});

test("non-v1 engines retain legacy routing behavior", () => {
  const result = preflightSourceToolchain({ inspection: {}, engine: "flutter", registry });
  assert.deepEqual(result.capabilities, []);
  assert.equal(result.applies, false);
  assert.equal(result.ok, true);
});

test("server preflight and native Android build consume the routed toolchain plan", async () => {
  const [server, engine] = await Promise.all([
    fs.readFile(new URL("../server.js", import.meta.url), "utf8"),
    fs.readFile(new URL("../src/buildEngine.js", import.meta.url), "utf8")
  ]);

  for (const marker of [
    "inspectProjectToolchainZip",
    "preflightSourceToolchain",
    "assertSourceToolchainSupported",
    "c.sourceToolchain",
    "queueAdmission"
  ]) assert.ok(server.includes(marker), `server missing ${marker}`);

  for (const marker of [
    "sourceGradleBinary",
    "routedGradleBin",
    "Toolchain Router • Gradle"
  ]) assert.ok(engine.includes(marker), `buildEngine missing ${marker}`);
});

test("managed Expo app config contributes toolchain requirements before prebuild", () => {
  const inspection = inspectProjectToolchainFiles({
    "package.json": JSON.stringify({
      dependencies: {
        expo: "~54.0.0",
        "react-native": "0.81.4"
      }
    }),
    "app.json": JSON.stringify({
      expo: {
        plugins: [
          [
            "expo-build-properties",
            {
              android: {
                compileSdkVersion: 36,
                targetSdkVersion: 36,
                minSdkVersion: 24,
                buildToolsVersion: "36.0.0",
                ndkVersion: "27.1.12297006"
              }
            }
          ]
        ]
      }
    })
  }, { engine: "expo-android" });

  assert.equal(inspection.compileSdk, "36");
  assert.equal(inspection.targetSdk, "36");
  assert.equal(inspection.minSdk, "24");
  assert.equal(inspection.buildToolsVersion, "36.0.0");
  assert.equal(inspection.ndkVersion, "27.1.12297006");

  const result =
    preflightSourceToolchain({
      inspection,
      engine: "expo-android",
      registry
    });

  assert.equal(result.ok, true, JSON.stringify(result.issues));
  for (const capability of [
    "android-api-36",
    "build-tools-36.0.0",
    "ndk-27.1.12297006",
    "gradle-8.14.3",
    "java-17"
  ]) {
    assert.ok(
      result.capabilities.includes(capability),
      capability
    );
  }
});
