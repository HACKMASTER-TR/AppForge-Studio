import AdmZip from "adm-zip";
import {
  promises as fs
} from "fs";
import path from "path";
import { spawn } from "child_process";
import { createSourceBuildEnv } from "./sourceBuildEnv.js";

const MAX_ZIP_ENTRIES =
  10_000;

const MAX_UNCOMPRESSED_BYTES =
  300 * 1024 * 1024;

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

function ignoredFlutterPath(
  segments
) {
  const ignored =
    new Set([
      ".git",
      ".dart_tool",
      ".idea",
      "build"
    ]);

  return segments.some(
    segment =>
      ignored.has(
        segment
      )
  );
}

async function extractFlutterZip(
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
      "Flutter projesinde çok fazla ZIP girdisi var."
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
        "Flutter ZIP yolu güvenli değil."
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
        "Flutter ZIP dizin dışına çıkmaya çalışıyor."
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
      ignoredFlutterPath(
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
        "Flutter ZIP hedef yolu güvenli değil."
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
        "Flutter proje ZIP'i açıldığında boyut sınırını aşıyor."
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
          ".dart_tool",
          ".idea",
          "build"
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

async function findFlutterProjectRoot(
  extractedRoot
) {
  const files =
    await walk(
      extractedRoot
    );

  const pubspecFiles =
    files
      .filter(
        file =>
          path.basename(
            file
          )
            .toLowerCase() ===
          "pubspec.yaml"
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
    const pubspecFile of
    pubspecFiles
  ) {
    const root =
      path.dirname(
        pubspecFile
      );

    const mainFile =
      path.join(
        root,
        "lib",
        "main.dart"
      );

    try {
      const stat =
        await fs.stat(
          mainFile
        );

      if (
        stat.isFile()
      ) {
        return {
          projectRoot:
            root,
          pubspecFile,
          mainFile
        };
      }
    } catch {
    }
  }

  throw new Error(
    "Flutter projesinde pubspec.yaml ve lib/main.dart birlikte bulunamadı."
  );
}

function parseFlutterProjectName(
  pubspec
) {
  const match =
    String(
      pubspec ||
      ""
    )
      .match(
        /^name:\s*([a-zA-Z0-9_]+)\s*$/m
      );

  return match?.[1] ||
    null;
}

export async function prepareFlutterSource({
  projectZip,
  workDir,
  onLog = null,
  cancelled = null
}) {
  if (
    !projectZip
  ) {
    throw new Error(
      "Flutter kaynak ZIP'i eksik."
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
    await extractFlutterZip(
      projectZip,
      extractedRoot
    );

  const found =
    await findFlutterProjectRoot(
      extractedRoot
    );

  const pubspec =
    await fs.readFile(
      found.pubspecFile,
      "utf8"
    );

  const projectName =
    parseFlutterProjectName(
      pubspec
    );

  if (
    !projectName
  ) {
    throw new Error(
      "pubspec.yaml içinde geçerli Flutter proje adı bulunamadı."
    );
  }

  if (
    cancelled
  ) {
    await cancelled();
  }

  if (
    onLog
  ) {
    await onLog(
      `🦋 Flutter proje hazır • ${projectName}`
    );
  }

  return {
    projectRoot:
      found.projectRoot,
    pubspecFile:
      found.pubspecFile,
    mainFile:
      found.mainFile,
    projectName,
    extractedEntries:
      extracted.entries,
    extractedBytes:
      extracted.bytes
  };
}

const FLUTTER_COMMAND_TIMEOUT_MS =
  15 * 60 * 1000;

const FLUTTER_LOG_LIMIT =
  250_000;

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

async function runFlutterCommand({
  projectRoot,
  args,
  onLog = null,
  cancelled = null,
  timeoutMs = FLUTTER_COMMAND_TIMEOUT_MS
}) {
  if (
    cancelled
  ) {
    await cancelled();
  }

  const env =
    createSourceBuildEnv(
      {
        CI:
          "true",
        FLUTTER_SUPPRESS_ANALYTICS:
          "true",
        PUB_ENVIRONMENT:
          "appforge"
      }
    );

  const flutterArgs = [
    "--no-version-check",
    ...args
  ];

  return new Promise(
    (
      resolve,
      reject
    ) => {
      const child =
        spawn(
          "flutter",
          flutterArgs,
          {
            cwd:
              projectRoot,
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
              FLUTTER_LOG_LIMIT
          ) {
            output +=
              text.slice(
                0,
                FLUTTER_LOG_LIMIT -
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
                `flutter ${flutterArgs.join(" ")} başarısız (exit=${code}).\n` +
                output.slice(
                  -14_000
                )
              )
            );
          }
        }
      );
    }
  );
}

async function configureFlutterAndroidSigning(
  projectRoot,
  {
    packageName,
    signing,
    localKeystore
  }
) {
  const androidDir =
    path.join(
      projectRoot,
      "android"
    );

  const appDir =
    path.join(
      androidDir,
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

  try {
    if (
      (
        await fs.stat(
          kts
        )
      ).isFile()
    ) {
      appBuildFile =
        kts;
      kotlinDsl =
        true;
    }
  } catch {
  }

  if (
    !appBuildFile
  ) {
    try {
      if (
        (
          await fs.stat(
            groovy
          )
        ).isFile()
      ) {
        appBuildFile =
          groovy;
      }
    } catch {
    }
  }

  if (
    !appBuildFile
  ) {
    throw new Error(
      "Flutter Android app/build.gradle(.kts) bulunamadı."
    );
  }

  const customSigning =
    signing?.mode ===
      "CUSTOM";

  if (
    customSigning &&
    !localKeystore
  ) {
    throw new Error(
      "Flutter CUSTOM signing için keystore bulunamadı."
    );
  }

  const packageId =
    groovyQuote(
      packageName
    );

  let signingBlock;

  if (
    customSigning
  ) {
    signingBlock =
`
    signingConfigs {
        appforgeRelease {
            storeFile file('${groovyQuote(localKeystore)}')
            storePassword '${groovyQuote(signing?.storePassword)}'
            keyAlias '${groovyQuote(signing?.alias)}'
            keyPassword '${groovyQuote(signing?.keyPassword)}'
        }
    }

    buildTypes {
        release {
            signingConfig signingConfigs.appforgeRelease
        }
    }
`;
  } else {
    signingBlock =
`
    buildTypes {
        release {
            signingConfig signingConfigs.debug
        }
    }
`;
  }

  const override =
`android {
    defaultConfig {
        applicationId '${packageId}'
    }
${signingBlock}
}
`;

  const overrideFile =
    path.join(
      androidDir,
      "appforge-flutter-overrides.gradle"
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
      "appforge-flutter-overrides.gradle"
    )
  ) {
    const apply =
      kotlinDsl
        ? '\napply(from = rootProject.file("appforge-flutter-overrides.gradle"))\n'
        : '\napply from: rootProject.file("appforge-flutter-overrides.gradle")\n';

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
    kotlinDsl,
    customSigning
  };
}

export function flutterBuildTargets(
  outputType
) {
  if (
    outputType ===
      "apk"
  ) {
    return [
      "apk"
    ];
  }

  if (
    outputType ===
      "aab"
  ) {
    return [
      "appbundle"
    ];
  }

  return [
    "apk",
    "appbundle"
  ];
}

async function findBuiltFlutterArtifact(
  projectRoot,
  target
) {
  const candidates =
    target ===
      "apk"
      ? [
          path.join(
            projectRoot,
            "build",
            "app",
            "outputs",
            "flutter-apk",
            "app-release.apk"
          )
        ]
      : [
          path.join(
            projectRoot,
            "build",
            "app",
            "outputs",
            "bundle",
            "release",
            "app-release.aab"
          )
        ];

  for (
    const candidate of
    candidates
  ) {
    try {
      const stat =
        await fs.stat(
          candidate
        );

      if (
        stat.isFile() &&
        stat.size >
          0
      ) {
        return candidate;
      }
    } catch {
    }
  }

  throw new Error(
    target ===
      "apk"
      ? "Flutter release APK çıktısı bulunamadı."
      : "Flutter release AAB çıktısı bulunamadı."
  );
}

export async function buildFlutterArtifacts({
  prepared,
  outputType,
  packageName,
  versionCode,
  versionName,
  signing,
  localKeystore = null,
  onLog = null,
  cancelled = null
}) {
  if (
    !prepared?.projectRoot
  ) {
    throw new Error(
      "Flutter proje hazırlığı eksik."
    );
  }

  await configureFlutterAndroidSigning(
    prepared.projectRoot,
    {
      packageName,
      signing,
      localKeystore
    }
  );

  if (
    onLog
  ) {
    await onLog(
      "📦 flutter pub get başlatılıyor..."
    );
  }

  await runFlutterCommand(
    {
      projectRoot:
        prepared.projectRoot,
      args: [
        "pub",
        "get"
      ],
      onLog,
      cancelled
    }
  );

  const targets =
    flutterBuildTargets(
      outputType
    );

  const result =
    {};

  for (
    const target of
    targets
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
        target ===
          "apk"
          ? "🦋 Flutter release APK build başlıyor..."
          : "🦋 Flutter release AAB build başlıyor..."
      );
    }

    await runFlutterCommand(
      {
        projectRoot:
          prepared.projectRoot,
        args: [
          "build",
          target,
          "--release",
          "--no-pub",
          "--build-number",
          String(
            Number(
              versionCode
            ) ||
            1
          ),
          "--build-name",
          String(
            versionName ||
            "1.0.0"
          )
        ],
        onLog,
        cancelled
      }
    );

    const artifact =
      await findBuiltFlutterArtifact(
        prepared.projectRoot,
        target
      );

    if (
      target ===
        "apk"
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
      "✅ Flutter Android artifactları hazır."
    );
  }

  return result;
}
