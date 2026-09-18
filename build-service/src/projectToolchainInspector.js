function clean(value) {
  const text = String(value ?? "").trim();
  return text || null;
}

function firstMatch(text, patterns) {
  for (const pattern of patterns) {
    const match = String(text || "").match(pattern);
    if (match?.[1]) return clean(match[1]);
  }
  return null;
}

function numericValue(value) {
  const match = String(value ?? "").match(/\d+(?:\.0)?/);
  return match ? match[0] : null;
}

function versionValue(value) {
  const match = String(value ?? "").match(/\d+(?:\.\d+){1,3}/);
  return match ? match[0] : null;
}

function normalizedPath(value) {
  return String(value || "").replaceAll("\\", "/").replace(/^\.\//, "").toLowerCase();
}

function isCandidatePath(name) {
  const value = normalizedPath(name);
  const base = value.split("/").at(-1);
  return [
    "build.gradle",
    "build.gradle.kts",
    "settings.gradle",
    "settings.gradle.kts",
    "gradle.properties",
    "gradle-wrapper.properties",
    "package.json",
    "app.json",
    "app.config.js",
    "app.config.ts",
    "app.config.json",
    "cmakelists.txt",
    "libs.versions.toml"
  ].includes(base);
}

function pathDepth(name) {
  return normalizedPath(name).split("/").filter(Boolean).length;
}

function chooseBest(files, predicate) {
  return Object.entries(files)
    .filter(([name]) => predicate(normalizedPath(name)))
    .sort(([a], [b]) => pathDepth(a) - pathDepth(b))[0] || null;
}

function parseProperties(contents) {
  const values = new Map();
  for (const text of contents) {
    for (const raw of String(text || "").split(/\r?\n/)) {
      const line = raw.trim();
      if (!line || line.startsWith("#") || line.startsWith("//")) continue;
      const match = line.match(/^([A-Za-z0-9_.-]+)\s*[=:]\s*["']?([^"'\s]+)["']?/);
      if (match) values.set(match[1], match[2]);
    }
  }
  return values;
}

function resolveReferenceValue(text, names, properties, kind = "numeric") {
  const converter = kind === "version" ? versionValue : numericValue;
  const directPatterns = names.flatMap(name => [
    new RegExp(`\\b${name}\\s*(?:=|:|\\s)\\s*["']?([0-9]+(?:\\.[0-9]+){0,3})["']?`, "i"),
    new RegExp(`\\b${name}Version\\s*(?:=|:|\\s)\\s*["']?([0-9]+(?:\\.[0-9]+){0,3})["']?`, "i")
  ]);
  const direct = firstMatch(text, directPatterns);
  if (direct) return converter(direct);

  for (const name of names) {
    const linePattern = new RegExp(`\\b${name}(?:Version)?\\b[^\\n]*`, "ig");
    const lines = String(text || "").match(linePattern) || [];
    for (const line of lines) {
      for (const [key, raw] of properties.entries()) {
        if (line.includes(key)) {
          const parsed = converter(raw);
          if (parsed) return parsed;
        }
      }
      const fallback = line.match(/[?:]\s*["']([0-9]+(?:\.[0-9]+){0,3})["']/);
      if (fallback?.[1]) return converter(fallback[1]);
    }
  }
  return null;
}

function parseVersionCatalog(text) {
  const versions = new Map();
  const plugins = [];
  let section = "";
  for (const raw of String(text || "").split(/\r?\n/)) {
    const line = raw.replace(/#.*$/, "").trim();
    if (!line) continue;
    const sectionMatch = line.match(/^\[([^\]]+)\]$/);
    if (sectionMatch) {
      section = sectionMatch[1].trim().toLowerCase();
      continue;
    }
    if (section === "versions") {
      const match = line.match(/^([A-Za-z0-9_.-]+)\s*=\s*["']([^"']+)["']/);
      if (match) versions.set(match[1], match[2]);
    } else if (section === "plugins") {
      const id = line.match(/id\s*=\s*["']([^"']+)["']/)?.[1] || null;
      const direct = line.match(/version\s*=\s*["']([^"']+)["']/)?.[1] || null;
      const ref = line.match(/version\.ref\s*=\s*["']([^"']+)["']/)?.[1] || null;
      if (id) plugins.push({ id, version: direct || (ref ? versions.get(ref) : null) || null });
    }
  }
  return { versions, plugins };
}

function parsePackageJson(text) {
  try {
    return JSON.parse(String(text || "{}"));
  } catch {
    return null;
  }
}

function dependencyVersion(pkg, name) {
  if (!pkg) return null;
  return clean(pkg.dependencies?.[name] || pkg.devDependencies?.[name] || null);
}

function parseGradleWrapper(text) {
  return firstMatch(text, [
    /distributionUrl\s*=.*?gradle-([0-9]+(?:\.[0-9]+){1,3})-(?:bin|all)\.zip/i,
    /gradle-([0-9]+(?:\.[0-9]+){1,3})-/i
  ]);
}

function parseAgpVersion(allText, versionCatalogs) {
  const direct = firstMatch(allText, [
    /com\.android\.tools\.build:gradle:([0-9]+(?:\.[0-9]+){1,3})/i,
    /id\s*\(?\s*["']com\.android\.(?:application|library)["']\s*\)?\s*version\s*["']([0-9]+(?:\.[0-9]+){1,3})["']/i,
    /alias\([^\n]*com\.android\.(?:application|library)[^\n]*\)[^\n]*version\s*["']([0-9]+(?:\.[0-9]+){1,3})["']/i
  ]);
  if (direct) return direct;
  for (const catalog of versionCatalogs) {
    const parsed = parseVersionCatalog(catalog);
    const plugin = parsed.plugins.find(item => /com\.android\.(application|library)/i.test(item.id));
    if (plugin?.version) return clean(plugin.version);
    for (const key of ["agp", "androidGradlePlugin", "android-gradle-plugin"]) {
      const value = parsed.versions.get(key);
      if (value) return clean(value);
    }
  }
  return null;
}

function parseJdkRequirement(allText) {
  const candidates = [];
  const patterns = [
    /JavaLanguageVersion\.of\(\s*(\d+)\s*\)/gi,
    /jvmToolchain\(\s*(\d+)\s*\)/gi,
    /toolchain[^\n]*languageVersion[^\n]*?(\d+)/gi,
    /org\.gradle\.java\.home[^\n]*?(?:jdk|java)[^0-9]*(\d+)/gi
  ];
  for (const pattern of patterns) {
    for (const match of String(allText || "").matchAll(pattern)) {
      candidates.push(Number(match[1]));
    }
  }
  const value = candidates.filter(Number.isFinite).sort((a, b) => b - a)[0];
  return value ? String(value) : null;
}

function parseCmake(allText) {
  const exact = firstMatch(allText, [
    /externalNativeBuild[\s\S]{0,1200}?cmake[\s\S]{0,600}?\bversion\s*(?:=|\s)\s*["']([0-9]+(?:\.[0-9]+){1,3})["']/i
  ]);
  if (exact) return { version: exact, constraint: "exact" };
  const minimum = firstMatch(allText, [
    /cmake_minimum_required\s*\(\s*VERSION\s+([0-9]+(?:\.[0-9]+){1,3})/i
  ]);
  return minimum ? { version: minimum, constraint: "minimum" } : { version: null, constraint: null };
}


function appConfigToolchainValues(contents) {
  const keys = [
    "compileSdkVersion",
    "targetSdkVersion",
    "minSdkVersion",
    "buildToolsVersion",
    "ndkVersion"
  ];

  const found = {};

  function visit(value) {
    if (
      value == null ||
      typeof value !== "object"
    ) {
      return;
    }

    if (Array.isArray(value)) {
      for (const item of value) visit(item);
      return;
    }

    for (const [key, item] of Object.entries(value)) {
      if (
        keys.includes(key) &&
        found[key] == null &&
        item != null
      ) {
        found[key] = String(item);
      }

      visit(item);
    }
  }

  for (const text of contents || []) {
    try {
      visit(JSON.parse(String(text || "{}")));
    } catch {
      for (const key of keys) {
        if (found[key] != null) continue;
        const match = String(text || "").match(
          new RegExp(`["']?${key}["']?\\s*[:=]\\s*["']?([0-9]+(?:\\.[0-9]+){0,3})`, "i")
        );
        if (match?.[1]) found[key] = match[1];
      }
    }
  }

  return {
    compileSdk: numericValue(found.compileSdkVersion),
    targetSdk: numericValue(found.targetSdkVersion),
    minSdk: numericValue(found.minSdkVersion),
    buildToolsVersion: versionValue(found.buildToolsVersion),
    ndkVersion: versionValue(found.ndkVersion)
  };
}

export function inspectProjectToolchainFiles(inputFiles, { engine = "" } = {}) {
  const files = Object.fromEntries(
    Object.entries(inputFiles || {})
      .filter(([name]) => isCandidatePath(name))
      .map(([name, content]) => [normalizedPath(name), String(content ?? "")])
  );

  const entries = Object.keys(files);
  const gradleTexts = Object.entries(files)
    .filter(([name]) => /(?:^|\/)(?:build|settings)\.gradle(?:\.kts)?$/.test(name) || name.endsWith("/gradle.properties") || name === "gradle.properties")
    .map(([, content]) => content);
  const propertyTexts = Object.entries(files)
    .filter(([name]) => name.endsWith("gradle.properties"))
    .map(([, content]) => content);
  const appConfigTexts = Object.entries(files)
    .filter(([name]) => {
      const base = name.split("/").at(-1);
      return [
        "app.json",
        "app.config.js",
        "app.config.ts",
        "app.config.json"
      ].includes(base);
    })
    .map(([, content]) => content);
  const appConfigToolchain = appConfigToolchainValues(appConfigTexts);
  const versionCatalogs = Object.entries(files)
    .filter(([name]) => name.endsWith("libs.versions.toml"))
    .map(([, content]) => content);
  const cmakeTexts = Object.entries(files)
    .filter(([name]) => name.endsWith("cmakelists.txt"))
    .map(([, content]) => content);

  const properties = parseProperties([...propertyTexts, ...gradleTexts]);
  const allGradle = gradleTexts.join("\n\n");
  const toolchainText = [...gradleTexts, ...appConfigTexts].join("\n\n");
  const allText = [...gradleTexts, ...appConfigTexts, ...cmakeTexts, ...propertyTexts].join("\n\n");

  const wrapperEntry = chooseBest(files, name => name.endsWith("gradle/wrapper/gradle-wrapper.properties") || name.endsWith("gradle-wrapper.properties"));
  const packageEntry = chooseBest(files, name => name.endsWith("package.json"));
  const pkg = packageEntry ? parsePackageJson(packageEntry[1]) : null;

  const compileSdk = appConfigToolchain.compileSdk || resolveReferenceValue(toolchainText, ["compileSdk", "compileSdkVersion"], properties, "numeric");
  const targetSdk = appConfigToolchain.targetSdk || resolveReferenceValue(toolchainText, ["targetSdk", "targetSdkVersion"], properties, "numeric");
  const minSdk = appConfigToolchain.minSdk || resolveReferenceValue(toolchainText, ["minSdk", "minSdkVersion"], properties, "numeric");
  const buildToolsVersion = appConfigToolchain.buildToolsVersion || resolveReferenceValue(toolchainText, ["buildTools", "buildToolsVersion"], properties, "version");
  const ndkVersion = appConfigToolchain.ndkVersion || resolveReferenceValue(toolchainText, ["ndk", "ndkVersion"], properties, "version");
  const cmake = parseCmake(allText);
  const gradleWrapperVersion = wrapperEntry ? parseGradleWrapper(wrapperEntry[1]) : null;
  const androidGradlePluginVersion = parseAgpVersion(allGradle, versionCatalogs);
  const explicitJdk = parseJdkRequirement(allText);
  const agpMajor = Number(androidGradlePluginVersion?.split(".")?.[0] || 0);
  const jdkMajor = explicitJdk || (agpMajor >= 8 ? "17" : null);

  const expoVersion = dependencyVersion(pkg, "expo");
  const reactNativeVersion = dependencyVersion(pkg, "react-native");

  const hasSettings = entries.some(name => /(?:^|\/)settings\.gradle(?:\.kts)?$/.test(name));
  const hasAppBuild = entries.some(name => /(?:^|\/)app\/build\.gradle(?:\.kts)?$/.test(name));
  const nativeAndroidProject = hasSettings && hasAppBuild;

  return {
    engine: clean(engine)?.toLowerCase() || null,
    compileSdk,
    targetSdk,
    minSdk,
    buildToolsVersion,
    ndkVersion,
    cmakeVersion: cmake.version,
    cmakeConstraint: cmake.constraint,
    gradleWrapperVersion,
    androidGradlePluginVersion,
    jdkMajor,
    expoVersion,
    reactNativeVersion,
    nativeAndroidProject,
    inspectedFiles: entries.length
  };
}

export async function inspectProjectToolchainZip(projectZip, options = {}) {
  if (!projectZip) throw new Error("Toolchain inspector için proje ZIP'i gerekli.");
  const { default: AdmZip } = await import("adm-zip");
  const zip = new AdmZip(projectZip);
  const entries = zip.getEntries();
  if (entries.length > 8000) {
    const error = new Error("Proje ZIP'i toolchain preflight için fazla sayıda dosya içeriyor.");
    error.code = "SOURCE_TOOLCHAIN_INSPECTION_LIMIT";
    error.statusCode = 400;
    throw error;
  }

  const files = {};
  let bytes = 0;
  let count = 0;
  for (const entry of entries) {
    if (entry.isDirectory || !isCandidatePath(entry.entryName)) continue;
    if (count >= 160) break;
    const declaredSize = Number(entry.header?.size || 0);
    if (declaredSize > 768 * 1024) continue;
    const data = entry.getData();
    if (data.length > 768 * 1024) continue;
    bytes += data.length;
    if (bytes > 6 * 1024 * 1024) {
      const error = new Error("Proje toolchain metadata sınırı aşıldı.");
      error.code = "SOURCE_TOOLCHAIN_INSPECTION_LIMIT";
      error.statusCode = 400;
      throw error;
    }
    files[entry.entryName] = data.toString("utf8");
    count += 1;
  }

  return inspectProjectToolchainFiles(files, options);
}
