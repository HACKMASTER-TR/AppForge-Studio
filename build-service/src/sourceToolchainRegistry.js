import { promises as fs, existsSync } from "fs";
import path from "path";
import { execFile } from "child_process";
import { promisify } from "util";
import { fileURLToPath } from "url";

const execFileAsync = promisify(execFile);

const here = path.dirname(fileURLToPath(import.meta.url));
const defaultRegistryPath = path.resolve(here, "..", "source-worker-toolchain.json");

function clean(value) {
  return String(value ?? "").trim();
}

export function compareToolchainVersions(a, b) {
  const left = clean(a).split(/[.-]/).map(part => Number(String(part).match(/^\d+/)?.[0] || 0));
  const right = clean(b).split(/[.-]/).map(part => Number(String(part).match(/^\d+/)?.[0] || 0));
  const length = Math.max(left.length, right.length);
  for (let i = 0; i < length; i += 1) {
    const l = left[i] || 0;
    const r = right[i] || 0;
    if (l > r) return 1;
    if (l < r) return -1;
  }
  return 0;
}

export function normalizeAndroidApi(value) {
  const text = clean(value).replace(/^android-/i, "");
  if (!text) return null;
  return text.endsWith(".0") ? text.slice(0, -2) : text;
}

export function sourceToolchainCapability(kind, version) {
  const value = clean(version);
  if (!value) return null;
  switch (kind) {
    case "androidPlatform":
      return `android-api-${normalizeAndroidApi(value)}`;
    case "buildTools":
      return `build-tools-${value}`;
    case "ndk":
      return `ndk-${value}`;
    case "cmake":
      return `cmake-${value}`;
    case "gradle":
      return `gradle-${value}`;
    case "jdk":
      return `java-${value}`;
    case "framework":
      return `source-family-${value}`;
    default:
      return null;
  }
}

export async function loadSourceToolchainRegistry(registryPath = defaultRegistryPath) {
  const raw = await fs.readFile(registryPath, "utf8");
  const registry = JSON.parse(raw);
  assertSourceToolchainRegistry(registry);
  return registry;
}

export function assertSourceToolchainRegistry(registry) {
  if (!registry || Number(registry.schemaVersion) !== 2) {
    throw new Error("Source Worker toolchain registry schemaVersion=2 olmalı.");
  }
  for (const key of ["androidPlatforms", "buildTools", "ndk", "cmake", "gradle", "jdk"]) {
    if (!Array.isArray(registry[key]) || registry[key].length === 0) {
      throw new Error(`Source Worker toolchain registry ${key} listesi boş olamaz.`);
    }
  }
  if (!registry.frameworkFamilies || typeof registry.frameworkFamilies !== "object") {
    throw new Error("Source Worker toolchain registry frameworkFamilies eksik.");
  }
  if (!registry.agpCompatibility || typeof registry.agpCompatibility !== "object") {
    throw new Error("Source Worker toolchain registry agpCompatibility eksik.");
  }
  return registry;
}

function versionMajor(value) {
  const match = clean(value).match(/^(\d+)/);
  return match ? Number(match[1]) : null;
}

function agpFamilyKey(value) {
  const match = clean(value).match(/^(\d+)\.(\d+)/);
  return match ? `${Number(match[1])}.${Number(match[2])}` : null;
}

function supportedPlatform(registry, requested) {
  const normalized = normalizeAndroidApi(requested);
  return registry.androidPlatforms.some(value => normalizeAndroidApi(value) === normalized);
}

function selectGradle(registry, requested, familyDefault) {
  const versions = registry.gradle.map(clean).filter(Boolean);
  const exact = clean(requested);
  if (exact && versions.includes(exact)) return exact;

  const requestedMajor = versionMajor(exact);
  if (requestedMajor != null) {
    const sameMajor = versions
      .filter(value =>
        versionMajor(value) === requestedMajor &&
        compareToolchainVersions(value, exact) >= 0
      )
      .sort(compareToolchainVersions);
    if (sameMajor.length) return sameMajor[sameMajor.length - 1];
    return null;
  }

  return versions.includes(clean(familyDefault))
    ? clean(familyDefault)
    : null;
}

function selectCmake(registry, requested, constraint = "exact") {
  const value = clean(requested);
  if (!value) return null;
  if (registry.cmake.includes(value)) return value;
  if (constraint === "minimum") {
    return registry.cmake
      .filter(candidate => compareToolchainVersions(candidate, value) >= 0)
      .sort(compareToolchainVersions)[0] || null;
  }
  return null;
}

function issue(kind, requested, message, supported = []) {
  return { kind, requested: requested == null ? null : String(requested), message, supported };
}

export function preflightSourceToolchain({ inspection = {}, engine, registry }) {
  assertSourceToolchainRegistry(registry);
  const normalizedEngine = clean(engine).toLowerCase();
  const family = registry.frameworkFamilies[normalizedEngine];

  if (!family) {
    return {
      applies: false,
      ok: true,
      engine: normalizedEngine,
      registrySchemaVersion: registry.schemaVersion,
      capabilities: [],
      selected: {},
      issues: []
    };
  }

  const issues = [];
  const selected = {};

  if (inspection.compileSdk) {
    if (!supportedPlatform(registry, inspection.compileSdk)) {
      issues.push(issue(
        "android-platform",
        inspection.compileSdk,
        `Proje Android API ${inspection.compileSdk} (compileSdk) istiyor ancak production Source Worker registry bu API seviyesini desteklemiyor.`,
        registry.androidPlatforms
      ));
    } else {
      selected.compileSdk = normalizeAndroidApi(inspection.compileSdk);
    }
  }

  if (inspection.targetSdk != null && registry.targetSdk) {
    const target = Number(normalizeAndroidApi(inspection.targetSdk));
    const min = Number(registry.targetSdk.min ?? 1);
    const max = Number(registry.targetSdk.max ?? Number.MAX_SAFE_INTEGER);
    if (Number.isFinite(target) && (target < min || target > max)) {
      issues.push(issue(
        "target-sdk",
        inspection.targetSdk,
        `Proje targetSdk ${inspection.targetSdk} istiyor ancak production Source Worker registry targetSdk aralığı ${min}-${max}.`,
        [`${min}-${max}`]
      ));
    } else if (Number.isFinite(target)) {
      selected.targetSdk = String(target);
    }
  }

  if (inspection.buildToolsVersion) {
    if (!registry.buildTools.includes(clean(inspection.buildToolsVersion))) {
      issues.push(issue(
        "build-tools",
        inspection.buildToolsVersion,
        `Proje Build Tools ${inspection.buildToolsVersion} istiyor ancak production Source Worker registry bu sürümü desteklemiyor.`,
        registry.buildTools
      ));
    } else {
      selected.buildToolsVersion = clean(inspection.buildToolsVersion);
    }
  }

  if (inspection.ndkVersion) {
    if (!registry.ndk.includes(clean(inspection.ndkVersion))) {
      issues.push(issue(
        "ndk",
        inspection.ndkVersion,
        `Proje NDK ${inspection.ndkVersion} istiyor ancak production Source Worker registry bu sürümü desteklemiyor.`,
        registry.ndk
      ));
    } else {
      selected.ndkVersion = clean(inspection.ndkVersion);
    }
  }

  if (inspection.cmakeVersion) {
    const cmake = selectCmake(registry, inspection.cmakeVersion, inspection.cmakeConstraint);
    if (!cmake) {
      issues.push(issue(
        "cmake",
        inspection.cmakeVersion,
        `Proje CMake ${inspection.cmakeVersion}${inspection.cmakeConstraint === "minimum" ? "+" : ""} istiyor ancak production Source Worker registry bunu karşılamıyor.`,
        registry.cmake
      ));
    } else {
      selected.cmakeVersion = cmake;
    }
  }

  const agpKey = agpFamilyKey(inspection.androidGradlePluginVersion);
  const agpRule = agpKey ? registry.agpCompatibility[agpKey] : null;

  const gradleRequest =
    inspection.gradleWrapperVersion ||
    (
      agpRule
        ? agpRule.minGradle
        : null
    );

  const selectedGradle = selectGradle(
    registry,
    gradleRequest,
    family.defaultGradle
  );
  if (!selectedGradle) {
    issues.push(issue(
      "gradle",
      inspection.gradleWrapperVersion || family.defaultGradle,
      `Proje Gradle ${inspection.gradleWrapperVersion || family.defaultGradle} ailesini istiyor ancak production Source Worker registry uygun Gradle sürümü taşımıyor.`,
      registry.gradle
    ));
  } else {
    selected.gradle = selectedGradle;
  }

  let requiredJdk = clean(inspection.jdkMajor || family.defaultJdk);

  if (inspection.androidGradlePluginVersion && !agpRule) {
    issues.push(issue(
      "agp",
      inspection.androidGradlePluginVersion,
      `Proje Android Gradle Plugin ${inspection.androidGradlePluginVersion} istiyor ancak production Source Worker registry bu AGP ailesi için doğrulanmış uyumluluk tanımlamıyor.`,
      Object.keys(registry.agpCompatibility)
    ));
  }

  if (agpRule) {
    requiredJdk = clean(inspection.jdkMajor || agpRule.jdk || requiredJdk);
    if (selectedGradle) {
      const gradleMajorOk = versionMajor(selectedGradle) === versionMajor(agpKey);
      const minOk = compareToolchainVersions(selectedGradle, agpRule.minGradle) >= 0;
      if (!gradleMajorOk || !minOk) {
        issues.push(issue(
          "agp-gradle",
          `${inspection.androidGradlePluginVersion} / Gradle ${selectedGradle}`,
          `AGP ${inspection.androidGradlePluginVersion} en az Gradle ${agpRule.minGradle} gerektiriyor; production Source Worker seçimi ${selectedGradle} uyumlu değil.`,
          registry.gradle
        ));
      }
    }
  }

  if (requiredJdk) {
    if (!registry.jdk.includes(requiredJdk)) {
      issues.push(issue(
        "jdk",
        requiredJdk,
        `Proje JDK ${requiredJdk} istiyor ancak production Source Worker registry bu JDK sürümünü desteklemiyor.`,
        registry.jdk
      ));
    } else {
      selected.jdk = requiredJdk;
    }
  }

  if (inspection.compileSdk && inspection.androidGradlePluginVersion) {
    const apiRule = registry.androidApiCompatibility?.[normalizeAndroidApi(inspection.compileSdk)];
    if (apiRule?.minAgp && compareToolchainVersions(inspection.androidGradlePluginVersion, apiRule.minAgp) < 0) {
      issues.push(issue(
        "api-agp",
        `${inspection.compileSdk} / AGP ${inspection.androidGradlePluginVersion}`,
        `Android API ${inspection.compileSdk} için en az AGP ${apiRule.minAgp} gerekiyor; proje AGP ${inspection.androidGradlePluginVersion} kullanıyor.`,
        [apiRule.minAgp]
      ));
    }
  }

  const capabilities = [
    sourceToolchainCapability("framework", normalizedEngine),
    selected.compileSdk ? sourceToolchainCapability("androidPlatform", selected.compileSdk) : null,
    selected.buildToolsVersion ? sourceToolchainCapability("buildTools", selected.buildToolsVersion) : null,
    selected.ndkVersion ? sourceToolchainCapability("ndk", selected.ndkVersion) : null,
    selected.cmakeVersion ? sourceToolchainCapability("cmake", selected.cmakeVersion) : null,
    selected.gradle ? "gradle" : null,
    selected.gradle ? sourceToolchainCapability("gradle", selected.gradle) : null,
    selected.jdk ? sourceToolchainCapability("jdk", selected.jdk) : null
  ].filter(Boolean);

  return {
    applies: true,
    ok: issues.length === 0,
    engine: normalizedEngine,
    registrySchemaVersion: registry.schemaVersion,
    capabilities: [...new Set(capabilities)],
    selected,
    issues
  };
}

export function assertSourceToolchainSupported(result) {
  if (result?.ok !== false) return result;
  const first = result.issues?.[0];
  const error = new Error(
    `SOURCE_TOOLCHAIN_UNSUPPORTED: ${first?.message || "Production Source Worker registry proje toolchain gereksinimini karşılamıyor."}`
  );
  error.code = "SOURCE_TOOLCHAIN_UNSUPPORTED";
  error.statusCode = 422;
  error.toolchain = result;
  throw error;
}

function javaMajorFromOutput(text) {
  const match = String(text || "").match(/version\s+"(?:1\.)?(\d+)/i);
  return match ? String(Number(match[1])) : null;
}

async function installedJavaMajor() {
  try {
    const { stdout, stderr } = await execFileAsync("java", ["-version"], { timeout: 12000, maxBuffer: 1024 * 1024 });
    return javaMajorFromOutput(`${stdout}\n${stderr}`);
  } catch (error) {
    return javaMajorFromOutput(`${error?.stdout || ""}\n${error?.stderr || error?.message || ""}`);
  }
}

async function exists(target) {
  try {
    await fs.access(target);
    return true;
  } catch {
    return false;
  }
}

export async function inspectInstalledSourceWorkerToolchain({
  registry,
  sdkRoot = process.env.ANDROID_SDK_ROOT || process.env.ANDROID_HOME || "/opt/android-sdk",
  gradleRoot = process.env.SOURCE_GRADLE_ROOT || "/opt/gradle",
  runtime = false
} = {}) {
  assertSourceToolchainRegistry(registry);
  const checks = [];
  const errors = [];
  const capabilities = [];

  const addCheck = async (kind, version, target, capability) => {
    const present = await exists(target);
    checks.push({ kind, version, target, exists: present });
    if (present && capability) capabilities.push(capability);
    if (!present) errors.push(`${kind} ${version} eksik: ${target}`);
  };

  for (const version of registry.androidPlatforms) {
    await addCheck("android-platform", version, path.join(sdkRoot, "platforms", `android-${version}`, "android.jar"), sourceToolchainCapability("androidPlatform", version));
  }
  for (const version of registry.buildTools) {
    await addCheck("build-tools", version, path.join(sdkRoot, "build-tools", version, process.platform === "win32" ? "aapt2.exe" : "aapt2"), sourceToolchainCapability("buildTools", version));
  }
  for (const version of registry.ndk) {
    await addCheck("ndk", version, path.join(sdkRoot, "ndk", version, "source.properties"), sourceToolchainCapability("ndk", version));
  }
  for (const version of registry.cmake) {
    await addCheck("cmake", version, path.join(sdkRoot, "cmake", version, "bin", process.platform === "win32" ? "cmake.exe" : "cmake"), sourceToolchainCapability("cmake", version));
  }
  for (const version of registry.gradle) {
    await addCheck("gradle", version, path.join(gradleRoot, `gradle-${version}`, "bin", process.platform === "win32" ? "gradle.bat" : "gradle"), sourceToolchainCapability("gradle", version));
  }

  const javaMajor = await installedJavaMajor();
  for (const version of registry.jdk) {
    const present = javaMajor === clean(version);
    checks.push({ kind: "jdk", version, target: "java -version", exists: present, detected: javaMajor });
    if (present) capabilities.push(sourceToolchainCapability("jdk", version));
    else errors.push(`JDK ${version} eksik veya aktif değil (detected=${javaMajor || "unknown"}).`);
  }

  let sdkWritable = false;
  try {
    await fs.access(sdkRoot, fs.constants.W_OK);
    sdkWritable = true;
  } catch {
    sdkWritable = false;
  }
  if (runtime && sdkWritable) errors.push(`Runtime Android SDK yazılabilir olmamalı: ${sdkRoot}`);

  if (errors.length === 0) {
    capabilities.push("gradle");
    for (const framework of Object.keys(registry.frameworkFamilies)) {
      capabilities.push(sourceToolchainCapability("framework", framework));
    }
  }

  return {
    ok: errors.length === 0,
    runtime: Boolean(runtime),
    sdkRoot,
    sdkWritable,
    gradleRoot,
    javaMajor,
    registrySchemaVersion: registry.schemaVersion,
    capabilities: [...new Set(capabilities.filter(Boolean))],
    checks,
    errors
  };
}

export function sourceGradleBinary(version, fallback = "gradle") {
  const value = clean(version);
  if (!value) return fallback;
  const root = process.env.SOURCE_GRADLE_ROOT || "/opt/gradle";
  const target = path.join(
    root,
    `gradle-${value}`,
    "bin",
    process.platform === "win32" ? "gradle.bat" : "gradle"
  );

  if (existsSync(target)) return target;

  // Production dedicated Source Worker registry doctor bu yolu
  // startup sırasında doğrular. Local/inline geliştirmede ise mevcut
  // Gradle davranışını koruyup fallback launcher kullanılır.
  return process.env.SOURCE_BUILD_ISOLATION_MODE === "dedicated"
    ? target
    : fallback;
}
