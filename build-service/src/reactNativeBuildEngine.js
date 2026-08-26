import AdmZip from "adm-zip";
import {
  promises as fs
} from "fs";
import path from "path";
import { spawn } from "child_process";
import { existsSync } from "fs";
import { createSourceBuildEnv } from "./sourceBuildEnv.js";

const MAX_ZIP_ENTRIES =
  12_000;

const MAX_UNCOMPRESSED_BYTES =
  350 * 1024 * 1024;

function safeInside(
  root,
  candidate
) {
  const resolvedRoot =
    path.resolve(
      root
    );

  const resolvedCandidate =
    path.resolve(
      candidate
    );

  return (
    resolvedCandidate ===
      resolvedRoot ||
    resolvedCandidate.startsWith(
      resolvedRoot +
        path.sep
    )
  );
}

function ignoredPath(
  segments
) {
  const ignored =
    new Set([
      ".git",
      ".idea",
      ".gradle",
      "node_modules",
      "build",
      "dist"
    ]);

  return segments.some(
    segment =>
      ignored.has(
        segment
      )
  );
}

async function extractProjectZip(
  zipPath,
  destination
) {
  const zip =
    new AdmZip(
      zipPath
    );

  const entries =
    zip.getEntries();

  if (
    entries.length >
      MAX_ZIP_ENTRIES
  ) {
    throw new Error(
      "React Native / Expo projesinde çok fazla ZIP girdisi var."
    );
  }

  await fs.rm(
    destination,
    {
      recursive: true,
      force: true
    }
  );

  await fs.mkdir(
    destination,
    {
      recursive: true
    }
  );

  let totalBytes =
    0;

  for (
    const entry of
    entries
  ) {
    const rawName =
      String(
        entry.entryName ||
        ""
      )
        .replaceAll(
          "\\",
          "/"
        );

    if (
      !rawName ||
      rawName.startsWith(
        "/"
      ) ||
      rawName.includes(
        "\0"
      )
    ) {
      throw new Error(
        "React Native / Expo ZIP yolu güvenli değil."
      );
    }

    const normalized =
      path.posix.normalize(
        rawName
      );

    if (
      normalized ===
        ".." ||
      normalized.startsWith(
        "../"
      )
    ) {
      throw new Error(
        "React Native / Expo ZIP dizin dışına çıkmaya çalışıyor."
      );
    }

    const segments =
      normalized
        .split(
          "/"
        )
        .filter(
          Boolean
        );

    if (
      !segments.length ||
      ignoredPath(
        segments
      )
    ) {
      continue;
    }

    const target =
      path.join(
        destination,
        ...segments
      );

    if (
      !safeInside(
        destination,
        target
      )
    ) {
      throw new Error(
        "React Native / Expo ZIP hedef yolu güvenli değil."
      );
    }

    if (
      entry.isDirectory
    ) {
      await fs.mkdir(
        target,
        {
          recursive: true
        }
      );

      continue;
    }

    const data =
      entry.getData();

    totalBytes +=
      data.length;

    if (
      totalBytes >
        MAX_UNCOMPRESSED_BYTES
    ) {
      throw new Error(
        "React Native / Expo proje ZIP'i açıldığında boyut sınırını aşıyor."
      );
    }

    await fs.mkdir(
      path.dirname(
        target
      ),
      {
        recursive: true
      }
    );

    await fs.writeFile(
      target,
      data
    );
  }

  return {
    entries:
      entries.length,
    bytes:
      totalBytes
  };
}

async function walk(
  root,
  {
    maxDepth = 7,
    maxFiles = 8_000
  } = {}
) {
  const result =
    [];

  async function visit(
    dir,
    depth
  ) {
    if (
      depth >
        maxDepth ||
      result.length >=
        maxFiles
    ) {
      return;
    }

    const entries =
      await fs.readdir(
        dir,
        {
          withFileTypes:
            true
        }
      );

    for (
      const entry of
      entries
    ) {
      if (
        result.length >=
          maxFiles
      ) {
        break;
      }

      if (
        [
          ".git",
          ".idea",
          ".gradle",
          "node_modules",
          "build",
          "dist"
        ].includes(
          entry.name
        )
      ) {
        continue;
      }

      const full =
        path.join(
          dir,
          entry.name
        );

      if (
        entry.isDirectory()
      ) {
        await visit(
          full,
          depth + 1
        );
      } else if (
        entry.isFile()
      ) {
        result.push(
          full
        );
      }
    }
  }

  await visit(
    root,
    0
  );

  return result;
}

function dependenciesOf(
  packageJson
) {
  return {
    ...(
      packageJson
        ?.dependencies ||
      {}
    ),
    ...(
      packageJson
        ?.devDependencies ||
      {}
    )
  };
}

function hasDependency(
  packageJson,
  name
) {
  return Object.prototype.hasOwnProperty.call(
    dependenciesOf(
      packageJson
    ),
    name
  );
}

async function findProjectRoot(
  extractedRoot
) {
  const files =
    await walk(
      extractedRoot
    );

  const packages =
    files
      .filter(
        file =>
          path.basename(
            file
          )
            .toLowerCase() ===
          "package.json"
      )
      .sort(
        (
          a,
          b
        ) =>
          a.split(
            path.sep
          ).length -
          b.split(
            path.sep
          ).length
      );

  for (
    const packageFile of
    packages
  ) {
    let json;

    try {
      json =
        JSON.parse(
          await fs.readFile(
            packageFile,
            "utf8"
          )
        );
    } catch {
      continue;
    }

    if (
      hasDependency(
        json,
        "expo"
      ) ||
      hasDependency(
        json,
        "react-native"
      )
    ) {
      return {
        projectRoot:
          path.dirname(
            packageFile
          ),
        packageFile,
        packageJson:
          json
      };
    }
  }

  throw new Error(
    "package.json içinde expo veya react-native bağımlılığı bulunamadı."
  );
}

async function fileExists(
  file
) {
  try {
    return (
      await fs.stat(
        file
      )
    )
      .isFile();
  } catch {
    return false;
  }
}

async function dirExists(
  dir
) {
  try {
    return (
      await fs.stat(
        dir
      )
    )
      .isDirectory();
  } catch {
    return false;
  }
}

function packageManagerOf(
  projectRoot,
  files
) {
  const names =
    new Set(
      files.map(
        file =>
          path.basename(
            file
          )
            .toLowerCase()
      )
    );

  if (
    names.has(
      "pnpm-lock.yaml"
    )
  ) {
    return "pnpm";
  }

  if (
    names.has(
      "yarn.lock"
    )
  ) {
    return "yarn";
  }

  if (
    names.has(
      "package-lock.json"
    )
  ) {
    return "npm";
  }

  return "npm";
}

export async function prepareReactNativeSource({
  projectZip,
  workDir,
  onLog = null,
  cancelled = null
}) {
  if (
    !projectZip
  ) {
    throw new Error(
      "React Native / Expo kaynak ZIP'i eksik."
    );
  }

  const extractedRoot =
    path.join(
      workDir,
      "source"
    );

  if (
    cancelled
  ) {
    await cancelled();
  }

  const extracted =
    await extractProjectZip(
      projectZip,
      extractedRoot
    );

  const found =
    await findProjectRoot(
      extractedRoot
    );

  const files =
    await walk(
      found.projectRoot
    );

  const expo =
    hasDependency(
      found.packageJson,
      "expo"
    );

  const reactNative =
    hasDependency(
      found.packageJson,
      "react-native"
    );

  const androidDir =
    path.join(
      found.projectRoot,
      "android"
    );

  const hasAndroidDir =
    await dirExists(
      androidDir
    );

  const hasGradleSettings =
    (
      await fileExists(
        path.join(
          androidDir,
          "settings.gradle"
        )
      )
    ) ||
    (
      await fileExists(
        path.join(
          androidDir,
          "settings.gradle.kts"
        )
      )
    );

  const hasAndroidManifest =
    await fileExists(
      path.join(
        androidDir,
        "app",
        "src",
        "main",
        "AndroidManifest.xml"
      )
    );

  const nativeAndroidReady =
    hasAndroidDir &&
    hasGradleSettings &&
    hasAndroidManifest;

  const packageManager =
    packageManagerOf(
      found.projectRoot,
      files
    );

  const projectType =
    expo
      ? (
          nativeAndroidReady
            ? "expo-prebuilt"
            : "expo-managed"
        )
      : "react-native";

  if (
    onLog
  ) {
    await onLog(
      `⚛️ ${projectType} algılandı • paket yöneticisi: ${packageManager}`
    );
  }

  return {
    projectRoot:
      found.projectRoot,
    packageFile:
      found.packageFile,
    packageJson:
      found.packageJson,
    projectType,
    expo,
    reactNative,
    packageManager,
    androidDir,
    nativeAndroidReady,
    hasAndroidDir,
    hasGradleSettings,
    hasAndroidManifest,
    extractedEntries:
      extracted.entries,
    extractedBytes:
      extracted.bytes
  };
}

const RN_INSTALL_TIMEOUT_MS =
  12 * 60 * 1000;

const RN_BUILD_TIMEOUT_MS =
  20 * 60 * 1000;

const RN_LOG_LIMIT =
  300_000;

function npmInvocation(
  args
) {
  const candidates =
    [
      process.env.npm_execpath,
      process.env.PREFIX
        ? path.join(
            process.env.PREFIX,
            "lib",
            "node_modules",
            "npm",
            "bin",
            "npm-cli.js"
          )
        : null,
      "/usr/local/lib/node_modules/npm/bin/npm-cli.js",
      "/usr/lib/node_modules/npm/bin/npm-cli.js"
    ]
      .filter(
        Boolean
      );

  const npmCli =
    candidates.find(
      candidate =>
        existsSync(
          candidate
        )
    );

  if (
    npmCli
  ) {
    return {
      command:
        process.execPath,
      args: [
        npmCli,
        ...args
      ]
    };
  }

  return {
    command:
      "npm",
    args
  };
}

function sourceCommandEnv(
  projectRoot
) {
  return createSourceBuildEnv(
    {
      PATH:
        [
          path.dirname(
            process.execPath
          ),
          process.env.PATH,
          "/usr/local/bin",
          "/usr/bin",
          "/bin"
        ]
          .filter(
            Boolean
          )
          .join(
            path.delimiter
          ),
      HOME:
        path.join(
          projectRoot,
          ".appforge-home"
        ),
      TMPDIR:
        path.join(
          projectRoot,
          ".appforge-tmp"
        ),
      CI:
        "true",
      NODE_ENV:
        "development",
      EXPO_NO_GIT_STATUS:
        "1",
      NPM_CONFIG_AUDIT:
        "false",
      NPM_CONFIG_FUND:
        "false",
      NPM_CONFIG_UPDATE_NOTIFIER:
        "false",
      YARN_ENABLE_TELEMETRY:
        "0",
      COREPACK_ENABLE_DOWNLOAD_PROMPT:
        "0"
    }
  );
}

async function runSourceCommand({
  cwd,
  command,
  args,
  env,
  timeoutMs,
  onLog = null,
  cancelled = null
}) {
  await fs.mkdir(
    env.HOME,
    {
      recursive: true
    }
  );

  await fs.mkdir(
    env.TMPDIR,
    {
      recursive: true
    }
  );

  if (
    cancelled
  ) {
    await cancelled();
  }

  return new Promise(
    (
      resolve,
      reject
    ) => {
      const child =
        spawn(
          command,
          args,
          {
            cwd,
            env,
            shell:
              false,
            stdio: [
              "ignore",
              "pipe",
              "pipe"
            ]
          }
        );

      let output =
        "";

      const consume =
        chunk => {
          const text =
            String(
              chunk
            );

          if (
            output.length <
              RN_LOG_LIMIT
          ) {
            output +=
              text.slice(
                0,
                RN_LOG_LIMIT -
                  output.length
              );
          }

          if (
            onLog
          ) {
            const lines =
              text
                .split(
                  /\r?\n/
                )
                .map(
                  line =>
                    line.trim()
                )
                .filter(
                  Boolean
                )
                .slice(
                  0,
                  16
                );

            for (
              const line of
              lines
            ) {
              Promise.resolve(
                onLog(
                  line.slice(
                    0,
                    800
                  )
                )
              )
                .catch(
                  () => {}
                );
            }
          }
        };

      child.stdout.on(
        "data",
        consume
      );

      child.stderr.on(
        "data",
        consume
      );

      const timer =
        setTimeout(
          () => {
            child.kill(
              "SIGKILL"
            );
          },
          timeoutMs
        );

      child.on(
        "error",
        error => {
          clearTimeout(
            timer
          );
          reject(
            error
          );
        }
      );

      child.on(
        "close",
        code => {
          clearTimeout(
            timer
          );

          if (
            code ===
              0
          ) {
            resolve(
              output
            );
          } else {
            reject(
              new Error(
                `${command} ${args.join(" ")} başarısız (exit=${code}).\n` +
                output.slice(
                  -16_000
                )
              )
            );
          }
        }
      );
    }
  );
}

async function installJavaScriptDependencies(
  prepared,
  {
    onLog = null,
    cancelled = null
  } = {}
) {
  const env =
    sourceCommandEnv(
      prepared.projectRoot
    );

  let invocation;

  if (
    prepared.packageManager ===
      "pnpm"
  ) {
    invocation = {
      command:
        "pnpm",
      args: [
        "install",
        "--frozen-lockfile",
        "--ignore-scripts"
      ]
    };
  } else if (
    prepared.packageManager ===
      "yarn"
  ) {
    invocation = {
      command:
        "yarn",
      args: [
        "install",
        "--frozen-lockfile",
        "--ignore-scripts",
        "--non-interactive"
      ]
    };
  } else {
    const hasLock =
      await fileExists(
        path.join(
          prepared.projectRoot,
          "package-lock.json"
        )
      );

    invocation =
      npmInvocation(
        [
          hasLock
            ? "ci"
            : "install",
          "--ignore-scripts",
          "--include=dev",
          "--no-audit",
          "--no-fund"
        ]
      );
  }

  if (
    onLog
  ) {
    await onLog(
      `📦 ${prepared.packageManager} bağımlılık kurulumu başlıyor • lifecycle scripts kapalı.`
    );
  }

  await runSourceCommand(
    {
      cwd:
        prepared.projectRoot,
      command:
        invocation.command,
      args:
        invocation.args,
      env,
      timeoutMs:
        RN_INSTALL_TIMEOUT_MS,
      onLog,
      cancelled
    }
  );
}

async function expoPrebuildIfNeeded(
  prepared,
  {
    onLog = null,
    cancelled = null
  } = {}
) {
  if (
    prepared.projectType !==
      "expo-managed"
  ) {
    return prepared;
  }

  const expoCli =
    path.join(
      prepared.projectRoot,
      "node_modules",
      "expo",
      "bin",
      "cli"
    );

  if (
    !(
      await fileExists(
        expoCli
      )
    )
  ) {
    throw new Error(
      "Expo CLI node_modules içinde bulunamadı."
    );
  }

  if (
    onLog
  ) {
    await onLog(
      "🧩 Expo managed proje • android/ için expo prebuild başlıyor."
    );
  }

  await runSourceCommand(
    {
      cwd:
        prepared.projectRoot,
      command:
        process.execPath,
      args: [
        expoCli,
        "prebuild",
        "--platform",
        "android",
        "--no-install",
        "--clean"
      ],
      env:
        sourceCommandEnv(
          prepared.projectRoot
        ),
      timeoutMs:
        RN_BUILD_TIMEOUT_MS,
      onLog,
      cancelled
    }
  );

  const androidDir =
    path.join(
      prepared.projectRoot,
      "android"
    );

  const ready =
    (
      await dirExists(
        androidDir
      )
    ) &&
    (
      (
        await fileExists(
          path.join(
            androidDir,
            "settings.gradle"
          )
        )
      ) ||
      (
        await fileExists(
          path.join(
            androidDir,
            "settings.gradle.kts"
          )
        )
      )
    ) &&
    (
      await fileExists(
        path.join(
          androidDir,
          "app",
          "src",
          "main",
          "AndroidManifest.xml"
        )
      )
    );

  if (
    !ready
  ) {
    throw new Error(
      "Expo prebuild tamamlandı ancak geçerli Android native proje oluşmadı."
    );
  }

  return {
    ...prepared,
    androidDir,
    nativeAndroidReady:
      true,
    projectType:
      "expo-prebuilt-generated"
  };
}

function groovyQuote(
  value
) {
  return String(
    value ?? ""
  )
    .replaceAll(
      "\\",
      "\\\\"
    )
    .replaceAll(
      "'",
      "\\'"
    );
}

async function configureAndroidOverrides(
  prepared,
  {
    packageName,
    versionCode,
    versionName
  }
) {
  if (
    !prepared.nativeAndroidReady
  ) {
    throw new Error(
      "React Native / Expo Android native proje hazır değil."
    );
  }

  const appDir =
    path.join(
      prepared.androidDir,
      "app"
    );

  const kts =
    path.join(
      appDir,
      "build.gradle.kts"
    );

  const groovy =
    path.join(
      appDir,
      "build.gradle"
    );

  let appBuildFile =
    null;

  let kotlinDsl =
    false;

  if (
    await fileExists(
      kts
    )
  ) {
    appBuildFile =
      kts;
    kotlinDsl =
      true;
  } else if (
    await fileExists(
      groovy
    )
  ) {
    appBuildFile =
      groovy;
  }

  if (
    !appBuildFile
  ) {
    throw new Error(
      "React Native / Expo android/app/build.gradle(.kts) bulunamadı."
    );
  }

  const debugKeystore =
    "/opt/appforge-fast-runtime/debug.keystore";

  const override =
`android {
    defaultConfig {
        applicationId '${groovyQuote(packageName)}'
        versionCode ${Number(versionCode) || 1}
        versionName '${groovyQuote(versionName || "1.0.0")}'
    }

    signingConfigs {
        appforgeSourceDebug {
            storeFile file('${debugKeystore}')
            storePassword 'android'
            keyAlias 'androiddebugkey'
            keyPassword 'android'
        }
    }

    buildTypes {
        release {
            signingConfig signingConfigs.appforgeSourceDebug
        }
    }
}
`;

  const overrideFile =
    path.join(
      prepared.androidDir,
      "appforge-react-native-overrides.gradle"
    );

  await fs.writeFile(
    overrideFile,
    override,
    "utf8"
  );

  let appBuild =
    await fs.readFile(
      appBuildFile,
      "utf8"
    );

  if (
    !appBuild.includes(
      "appforge-react-native-overrides.gradle"
    )
  ) {
    const apply =
      kotlinDsl
        ? '\napply(from = rootProject.file("appforge-react-native-overrides.gradle"))\n'
        : '\napply from: rootProject.file("appforge-react-native-overrides.gradle")\n';

    appBuild =
      appBuild.trimEnd() +
      apply;

    await fs.writeFile(
      appBuildFile,
      appBuild,
      "utf8"
    );
  }

  return {
    appBuildFile,
    overrideFile,
    kotlinDsl
  };
}

function requestedGradleMajor(
  androidDir
) {
  const wrapperFile =
    path.join(
      androidDir,
      "gradle",
      "wrapper",
      "gradle-wrapper.properties"
    );

  if (
    !existsSync(
      wrapperFile
    )
  ) {
    return null;
  }

  try {
    const content =
      globalThis.process
        .getBuiltinModule(
          "fs"
        )
        .readFileSync(
          wrapperFile,
          "utf8"
        );

    const match =
      content.match(
        /gradle-(\d+)\.(\d+(?:\.\d+)?)-/i
      );

    return match
      ? Number(
          match[1]
        )
      : null;
  } catch {
    return null;
  }
}

export function selectReactNativeGradle(
  requestedMajor
) {
  if (
    Number(
      requestedMajor
    ) >=
      9
  ) {
    return (
      process.env.GRADLE_HOME
        ? path.join(
            process.env.GRADLE_HOME,
            "bin",
            "gradle"
          )
        : "gradle"
    );
  }

  return (
    process.env.REACT_NATIVE_GRADLE_HOME
      ? path.join(
          process.env.REACT_NATIVE_GRADLE_HOME,
          "bin",
          "gradle"
        )
      : "gradle"
  );
}

export function reactNativeBuildTargets(
  outputType
) {
  if (
    outputType ===
      "apk"
  ) {
    return [
      ":app:assembleRelease"
    ];
  }

  if (
    outputType ===
      "aab"
  ) {
    return [
      ":app:bundleRelease"
    ];
  }

  return [
    ":app:assembleRelease",
    ":app:bundleRelease"
  ];
}

async function findArtifact(
  prepared,
  task
) {
  const isApk =
    task.includes(
      "assemble"
    );

  const candidate =
    isApk
      ? path.join(
          prepared.androidDir,
          "app",
          "build",
          "outputs",
          "apk",
          "release",
          "app-release.apk"
        )
      : path.join(
          prepared.androidDir,
          "app",
          "build",
          "outputs",
          "bundle",
          "release",
          "app-release.aab"
        );

  if (
    await fileExists(
      candidate
    )
  ) {
    return candidate;
  }

  throw new Error(
    isApk
      ? "React Native / Expo release APK çıktısı bulunamadı."
      : "React Native / Expo release AAB çıktısı bulunamadı."
  );
}

export async function buildReactNativeArtifacts({
  prepared,
  outputType,
  packageName,
  versionCode,
  versionName,
  onLog = null,
  cancelled = null
}) {
  await installJavaScriptDependencies(
    prepared,
    {
      onLog,
      cancelled
    }
  );

  const nativePrepared =
    await expoPrebuildIfNeeded(
      prepared,
      {
        onLog,
        cancelled
      }
    );

  if (
    !nativePrepared.nativeAndroidReady
  ) {
    throw new Error(
      "React Native projesinde build edilebilir android/ klasörü bulunamadı."
    );
  }

  await configureAndroidOverrides(
    nativePrepared,
    {
      packageName,
      versionCode,
      versionName
    }
  );

  const major =
    requestedGradleMajor(
      nativePrepared.androidDir
    );

  const gradle =
    selectReactNativeGradle(
      major
    );

  if (
    onLog
  ) {
    await onLog(
      `🛠 React Native Gradle seçildi • wrapper major=${major || "unknown"} • ${gradle}`
    );
  }

  const env =
    sourceCommandEnv(
      nativePrepared.projectRoot
    );

  env.NODE_BINARY =
    process.execPath;

  env.GRADLE_USER_HOME =
    path.join(
      nativePrepared.projectRoot,
      ".appforge-gradle"
    );

  const result =
    {};

  for (
    const task of
    reactNativeBuildTargets(
      outputType
    )
  ) {
    if (
      cancelled
    ) {
      await cancelled();
    }

    if (
      onLog
    ) {
      await onLog(
        `⚛️ React Native / Expo Gradle task: ${task}`
      );
    }

    await runSourceCommand(
      {
        cwd:
          nativePrepared.projectRoot,
        command:
          gradle,
        args: [
          "-p",
          nativePrepared.androidDir,
          "--no-daemon",
          "--stacktrace",
          task
        ],
        env,
        timeoutMs:
          RN_BUILD_TIMEOUT_MS,
        onLog,
        cancelled
      }
    );

    const artifact =
      await findArtifact(
        nativePrepared,
        task
      );

    if (
      task.includes(
        "assemble"
      )
    ) {
      result.apk =
        artifact;
    } else {
      result.aab =
        artifact;
    }
  }

  if (
    onLog
  ) {
    await onLog(
      "✅ React Native / Expo Android artifactları hazır."
    );
  }

  return {
    ...result,
    projectType:
      nativePrepared.projectType
  };
}
