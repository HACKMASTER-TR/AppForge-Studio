import AdmZip from "adm-zip";
import {
  promises as fs
} from "fs";
import path from "path";
import {
  spawn
} from "child_process";
import {
  createSourceBuildEnv
} from "./sourceBuildEnv.js";

const MAX_ZIP_ENTRIES =
  12_000;

const MAX_UNCOMPRESSED_BYTES =
  350 * 1024 * 1024;

function safeInside(
  root,
  candidate
) {
  const a =
    path.resolve(
      root
    );

  const b =
    path.resolve(
      candidate
    );

  return (
    b ===
      a ||
    b.startsWith(
      a +
        path.sep
    )
  );
}

function ignored(
  segments
) {
  const names =
    new Set([
      ".git",
      ".idea",
      ".vs",
      "bin",
      "obj"
    ]);

  return segments.some(
    segment =>
      names.has(
        segment
      )
  );
}

async function extractZip(
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
      ".NET Android projesinde çok fazla ZIP girdisi var."
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

  let bytes =
    0;

  for (
    const entry of
    entries
  ) {
    const raw =
      String(
        entry.entryName ||
        ""
      )
        .replaceAll(
          "\\",
          "/"
        );

    if (
      !raw ||
      raw.startsWith(
        "/"
      ) ||
      raw.includes(
        "\0"
      )
    ) {
      throw new Error(
        ".NET Android ZIP yolu güvenli değil."
      );
    }

    const normalized =
      path.posix.normalize(
        raw
      );

    if (
      normalized ===
        ".." ||
      normalized.startsWith(
        "../"
      )
    ) {
      throw new Error(
        ".NET Android ZIP dizin dışına çıkmaya çalışıyor."
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
      ignored(
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
        ".NET Android ZIP hedef yolu güvenli değil."
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

    bytes +=
      data.length;

    if (
      bytes >
        MAX_UNCOMPRESSED_BYTES
    ) {
      throw new Error(
        ".NET Android ZIP açıldığında boyut sınırını aşıyor."
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
    bytes
  };
}

async function walk(
  root,
  {
    maxDepth = 8,
    maxFiles = 10_000
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

    let entries;

    try {
      entries =
        await fs.readdir(
          dir,
          {
            withFileTypes:
              true
          }
        );
    } catch {
      return;
    }

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
        ignored(
          [
            entry.name
          ]
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

function xmlValue(
  text,
  tag
) {
  return (
    String(
      text ||
      ""
    )
      .match(
        new RegExp(
          `<${tag}>\\s*([^<]+?)\\s*</${tag}>`,
          "i"
        )
      )?.[1]
      ?.trim() ||
    null
  );
}

function targetFrameworks(
  text
) {
  const single =
    xmlValue(
      text,
      "TargetFramework"
    );

  const many =
    xmlValue(
      text,
      "TargetFrameworks"
    );

  return [
    ...(
      single
        ? [
            single
          ]
        : []
    ),
    ...(
      many
        ? many
            .split(
              ";"
            )
            .map(
              value =>
                value.trim()
            )
            .filter(
              Boolean
            )
        : []
    )
  ]
    .filter(
      (
        value,
        index,
        all
      ) =>
        all.indexOf(
          value
        ) ===
        index
    );
}

function boolValue(
  text,
  tag
) {
  return (
    xmlValue(
      text,
      tag
    )
      ?.toLowerCase() ===
    "true"
  );
}

export async function prepareDotnetAndroidSource({
  projectZip,
  workDir,
  onLog = null,
  cancelled = null
}) {
  if (
    !projectZip
  ) {
    throw new Error(
      ".NET Android kaynak ZIP'i eksik."
    );
  }

  if (
    cancelled
  ) {
    await cancelled();
  }

  const sourceRoot =
    path.join(
      workDir,
      "source"
    );

  const extracted =
    await extractZip(
      projectZip,
      sourceRoot
    );

  const files =
    await walk(
      sourceRoot
    );

  const projects =
    files
      .filter(
        file =>
          path.extname(
            file
          )
            .toLowerCase() ===
          ".csproj"
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

  if (
    !projects.length
  ) {
    throw new Error(
      ".NET Android projesinde .csproj bulunamadı."
    );
  }

  let selected =
    null;

  for (
    const file of
    projects
  ) {
    const text =
      await fs.readFile(
        file,
        "utf8"
      );

    const frameworks =
      targetFrameworks(
        text
      );

    const androidTargets =
      frameworks.filter(
        framework =>
          framework
            .toLowerCase()
            .includes(
              "-android"
            )
      );

    const useMaui =
      boolValue(
        text,
        "UseMaui"
      ) ||
      text
        .toLowerCase()
        .includes(
          "microsoft.maui"
        );

    if (
      androidTargets.length &&
      !useMaui
    ) {
      selected = {
        projectFile:
          file,
        projectRoot:
          path.dirname(
            file
          ),
        text,
        frameworks,
        androidTargets,
        useMaui
      };

      break;
    }
  }

  if (
    !selected
  ) {
    throw new Error(
      "MAUI olmayan .NET Android target içeren .csproj bulunamadı."
    );
  }

  const net10Targets =
    selected.androidTargets
      .filter(
        target =>
          target
            .toLowerCase()
            .startsWith(
              "net10.0-android"
            )
      );

  const applicationId =
    xmlValue(
      selected.text,
      "ApplicationId"
    );

  const applicationTitle =
    xmlValue(
      selected.text,
      "ApplicationTitle"
    );

  const applicationVersion =
    xmlValue(
      selected.text,
      "ApplicationVersion"
    );

  const applicationDisplayVersion =
    xmlValue(
      selected.text,
      "ApplicationDisplayVersion"
    );

  const outputType =
    xmlValue(
      selected.text,
      "OutputType"
    );

  if (
    onLog
  ) {
    await onLog(
      `🔷 .NET Android foundation • ${selected.androidTargets.join(", ")}`
    );
  }

  return {
    projectFile:
      selected.projectFile,
    projectRoot:
      selected.projectRoot,
    targetFrameworks:
      selected.frameworks,
    androidTargets:
      selected.androidTargets,
    net10AndroidTargets:
      net10Targets,
    androidReady:
      net10Targets.length >
      0,
    useMaui:
      false,
    outputType,
    applicationId,
    applicationTitle,
    applicationVersion,
    applicationDisplayVersion,
    extractedEntries:
      extracted.entries,
    extractedBytes:
      extracted.bytes
  };
}

const DOTNET_ANDROID_TIMEOUT_MS =
  25 * 60 * 1000;

const DOTNET_ANDROID_LOG_LIMIT =
  300_000;

function dotnetAndroidEnv(
  projectRoot
) {
  return createSourceBuildEnv(
    {
      PATH:
        [
          "/usr/share/dotnet",
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
      DOTNET_ROOT:
        process.env.DOTNET_ROOT ||
        "/usr/share/dotnet",
      DOTNET_CLI_HOME:
        path.join(
          projectRoot,
          ".appforge-dotnet"
        ),
      NUGET_PACKAGES:
        path.join(
          projectRoot,
          ".appforge-nuget"
        ),
      DOTNET_CLI_TELEMETRY_OPTOUT:
        "1",
      DOTNET_NOLOGO:
        "1",
      NUGET_XMLDOC_MODE:
        "skip",
      CI:
        "true"
    }
  );
}

async function runDotnetAndroid({
  prepared,
  args,
  onLog = null,
  cancelled = null
}) {
  const env =
    dotnetAndroidEnv(
      prepared.projectRoot
    );

  for (
    const dir of [
      env.HOME,
      env.TMPDIR,
      env.DOTNET_CLI_HOME,
      env.NUGET_PACKAGES
    ]
  ) {
    await fs.mkdir(
      dir,
      {
        recursive: true
      }
    );
  }

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
          "dotnet",
          args,
          {
            cwd:
              prepared.projectRoot,
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
          const value =
            String(
              chunk
            );

          if (
            output.length <
              DOTNET_ANDROID_LOG_LIMIT
          ) {
            output +=
              value.slice(
                0,
                DOTNET_ANDROID_LOG_LIMIT -
                  output.length
              );
          }

          if (
            onLog
          ) {
            for (
              const line of
              value
                .split(
                  /\r?\n/
                )
                .map(
                  item =>
                    item.trim()
                )
                .filter(
                  Boolean
                )
                .slice(
                  0,
                  16
                )
            ) {
              Promise.resolve(
                onLog(
                  line.slice(
                    0,
                    900
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
          DOTNET_ANDROID_TIMEOUT_MS
        );

      child.once(
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

      child.once(
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
                `dotnet ${args.join(" ")} başarısız (exit=${code}).\n` +
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

export function dotnetAndroidOutputFormats(
  outputType
) {
  const normalized =
    String(
      outputType ||
      "apk"
    )
      .trim()
      .toLowerCase();

  if (
    normalized ===
      "apk"
  ) {
    return [
      "apk"
    ];
  }

  if (
    normalized ===
      "aab"
  ) {
    return [
      "aab"
    ];
  }

  return [
    "apk",
    "aab"
  ];
}

function selectNet10AndroidTarget(
  prepared
) {
  const target =
    prepared
      ?.net10AndroidTargets
      ?.find(
        value =>
          value
            .toLowerCase()
            .startsWith(
              "net10.0-android"
            )
      );

  if (
    !target
  ) {
    throw new Error(
      "Generic .NET Android canlı motoru için net10.0-android hedefi gerekli."
    );
  }

  return target;
}

async function collectDotnetFiles(
  root,
  {
    maxDepth = 10,
    maxFiles = 20_000
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

    let entries;

    try {
      entries =
        await fs.readdir(
          dir,
          {
            withFileTypes:
              true
          }
        );
    } catch {
      return;
    }

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

async function findDotnetAndroidArtifact(
  prepared,
  format
) {
  const files =
    await collectDotnetFiles(
      path.join(
        prepared.projectRoot,
        "bin"
      )
    );

  const extension =
    format ===
      "apk"
      ? ".apk"
      : ".aab";

  const candidates =
    files
      .filter(
        file =>
          path.extname(
            file
          )
            .toLowerCase() ===
          extension
      )
      .sort(
        (
          a,
          b
        ) => {
          const score =
            file => {
              const normalized =
                file
                  .replaceAll(
                    "\\",
                    "/"
                  )
                  .toLowerCase();

              let value =
                0;

              if (
                normalized.includes(
                  "/release/"
                )
              ) {
                value +=
                  10;
              }

              if (
                normalized.includes(
                  "/publish/"
                )
              ) {
                value +=
                  20;
              }

              if (
                path.basename(
                  file
                )
                  .toLowerCase()
                  .includes(
                    "-signed"
                  )
              ) {
                value +=
                  30;
              }

              return value;
            };

          return (
            score(
              b
            ) -
            score(
              a
            )
          );
        }
      );

  for (
    const candidate of
    candidates
  ) {
    const stat =
      await fs.stat(
        candidate
      );

    if (
      stat.size >
        0
    ) {
      return candidate;
    }
  }

  throw new Error(
    format ===
      "apk"
      ? ".NET Android release APK çıktısı bulunamadı."
      : ".NET Android release AAB çıktısı bulunamadı."
  );
}

export async function buildDotnetAndroidArtifacts({
  prepared,
  outputType,
  packageName,
  versionCode,
  versionName,
  appName = null,
  onLog = null,
  cancelled = null
}) {
  if (
    !prepared
      ?.androidReady
  ) {
    throw new Error(
      ".NET Android projesinde desteklenen Android target bulunamadı."
    );
  }

  const target =
    selectNet10AndroidTarget(
      prepared
    );

  if (
    onLog
  ) {
    await onLog(
      `🔷 .NET Android restore başlıyor • ${target}`
    );
  }

  await runDotnetAndroid(
    {
      prepared,
      args: [
        "restore",
        prepared.projectFile,
        `-p:TargetFrameworks=${target}`
      ],
      onLog,
      cancelled
    }
  );

  const debugKeystore =
    "/opt/appforge-source-debug.keystore";

  const result =
    {};

  for (
    const format of
    dotnetAndroidOutputFormats(
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
        `🔷 .NET Android publish • ${format.toUpperCase()}`
      );
    }

    const args =
      [
        "publish",
        prepared.projectFile,
        "-f",
        target,
        "-c",
        "Release",
        "--no-restore",
        `-p:TargetFrameworks=${target}`,
        `-p:AndroidPackageFormats=${format}`,
        `-p:ApplicationId=${packageName}`,
        `-p:ApplicationVersion=${Number(versionCode) || 1}`,
        `-p:ApplicationDisplayVersion=${String(versionName || "1.0.0")}`,
        "-p:AndroidKeyStore=true",
        `-p:AndroidSigningKeyStore=${debugKeystore}`,
        "-p:AndroidSigningStorePass=android",
        "-p:AndroidSigningKeyAlias=androiddebugkey",
        "-p:AndroidSigningKeyPass=android"
      ];

    if (
      String(
        appName ||
        ""
      )
        .trim()
    ) {
      args.push(
        `-p:ApplicationTitle=${String(appName).trim()}`
      );
    }

    await runDotnetAndroid(
      {
        prepared,
        args,
        onLog,
        cancelled
      }
    );

    const artifact =
      await findDotnetAndroidArtifact(
        prepared,
        format
      );

    if (
      format ===
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
      "✅ Generic .NET Android artifactları hazır."
    );
  }

  return {
    ...result,
    targetFramework:
      target
  };
}
