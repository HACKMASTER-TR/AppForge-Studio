import AdmZip from "adm-zip";
import {
  promises as fs
} from "fs";
import path from "path";
import { spawn } from "child_process";
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
      ".vs",
      "bin",
      "obj"
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
      ".NET MAUI projesinde çok fazla ZIP girdisi var."
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
        ".NET MAUI ZIP yolu güvenli değil."
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
        ".NET MAUI ZIP dizin dışına çıkmaya çalışıyor."
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
        ".NET MAUI ZIP hedef yolu güvenli değil."
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
        ".NET MAUI proje ZIP'i açıldığında boyut sınırını aşıyor."
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
          ".vs",
          "bin",
          "obj"
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

function xmlValue(
  xml,
  tag
) {
  const expression =
    new RegExp(
      `<${tag}>\\s*([^<]+?)\\s*</${tag}>`,
      "i"
    );

  return (
    String(
      xml ||
      ""
    )
      .match(
        expression
      )?.[1]
      ?.trim() ||
    null
  );
}

function xmlBool(
  xml,
  tag
) {
  return (
    xmlValue(
      xml,
      tag
    )
      ?.toLowerCase() ===
    "true"
  );
}

function targetFrameworksOf(
  xml
) {
  const combined =
    [
      xmlValue(
        xml,
        "TargetFramework"
      ),
      xmlValue(
        xml,
        "TargetFrameworks"
      )
    ]
      .filter(
        Boolean
      )
      .join(
        ";"
      );

  return combined
    .split(
      ";"
    )
    .map(
      value =>
        value.trim()
    )
    .filter(
      Boolean
    );
}

async function findMauiProject(
  extractedRoot
) {
  const files =
    await walk(
      extractedRoot
    );

  const projectFiles =
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

  for (
    const projectFile of
    projectFiles
  ) {
    let xml;

    try {
      xml =
        await fs.readFile(
          projectFile,
          "utf8"
        );
    } catch {
      continue;
    }

    const lower =
      xml.toLowerCase();

    const useMaui =
      xmlBool(
        xml,
        "UseMaui"
      );

    const hasMauiReference =
      lower.includes(
        "microsoft.maui"
      );

    if (
      !useMaui &&
      !hasMauiReference
    ) {
      continue;
    }

    const targetFrameworks =
      targetFrameworksOf(
        xml
      );

    const androidTargets =
      targetFrameworks.filter(
        target =>
          target
            .toLowerCase()
            .includes(
              "-android"
            )
      );

    return {
      projectFile,
      projectRoot:
        path.dirname(
          projectFile
        ),
      xml,
      useMaui,
      hasMauiReference,
      targetFrameworks,
      androidTargets,
      projectName:
        path.basename(
          projectFile,
          path.extname(
            projectFile
          )
        ),
      applicationId:
        xmlValue(
          xml,
          "ApplicationId"
        ),
      applicationTitle:
        xmlValue(
          xml,
          "ApplicationTitle"
        ),
      applicationVersion:
        xmlValue(
          xml,
          "ApplicationVersion"
        ),
      applicationDisplayVersion:
        xmlValue(
          xml,
          "ApplicationDisplayVersion"
        )
    };
  }

  throw new Error(
    ".NET MAUI .csproj bulunamadı."
  );
}

export async function prepareDotnetMauiSource({
  projectZip,
  workDir,
  onLog = null,
  cancelled = null
}) {
  if (
    !projectZip
  ) {
    throw new Error(
      ".NET MAUI kaynak ZIP'i eksik."
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
    await findMauiProject(
      extractedRoot
    );

  const androidReady =
    found.androidTargets.length >
      0;

  if (
    onLog
  ) {
    await onLog(
      `🔷 .NET MAUI proje hazır • ${found.projectName} • ` +
      (
        androidReady
          ? found.androidTargets.join(", ")
          : "Android target yok"
      )
    );
  }

  return {
    projectRoot:
      found.projectRoot,
    projectFile:
      found.projectFile,
    projectName:
      found.projectName,
    targetFrameworks:
      found.targetFrameworks,
    androidTargets:
      found.androidTargets,
    androidReady,
    applicationId:
      found.applicationId,
    applicationTitle:
      found.applicationTitle,
    applicationVersion:
      found.applicationVersion,
    applicationDisplayVersion:
      found.applicationDisplayVersion,
    extractedEntries:
      extracted.entries,
    extractedBytes:
      extracted.bytes
  };
}

const DOTNET_TIMEOUT_MS =
  25 * 60 * 1000;

const DOTNET_LOG_LIMIT =
  300_000;

function dotnetSourceEnv(
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

async function runDotnet({
  prepared,
  args,
  onLog = null,
  cancelled = null
}) {
  const env =
    dotnetSourceEnv(
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
          const text =
            String(
              chunk
            );

          if (
            output.length <
              DOTNET_LOG_LIMIT
          ) {
            output +=
              text.slice(
                0,
                DOTNET_LOG_LIMIT -
                  output.length
              );
          }

          if (
            onLog
          ) {
            for (
              const line of
              text
                .split(
                  /\r?\n/
                )
                .map(
                  value =>
                    value.trim()
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
          DOTNET_TIMEOUT_MS
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

export function dotnetMauiOutputFormats(
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
    prepared.androidTargets.find(
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
      ".NET MAUI canlı motoru için net10.0-android hedefi gerekli."
    );
  }

  return target;
}

async function collectFiles(
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

async function findPublishedArtifact(
  prepared,
  format
) {
  const files =
    await collectFiles(
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
          extension &&
          file
            .replaceAll(
              "\\",
              "/"
            )
            .toLowerCase()
            .includes(
              "/publish/"
            )
      )
      .sort(
        (
          a,
          b
        ) => {
          const aSigned =
            path.basename(
              a
            )
              .toLowerCase()
              .includes(
                "-signed"
              )
              ? 1
              : 0;

          const bSigned =
            path.basename(
              b
            )
              .toLowerCase()
              .includes(
                "-signed"
              )
              ? 1
              : 0;

          return (
            bSigned -
            aSigned
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
      ? ".NET MAUI release APK çıktısı bulunamadı."
      : ".NET MAUI release AAB çıktısı bulunamadı."
  );
}

export async function buildDotnetMauiArtifacts({
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
    !prepared?.androidReady
  ) {
    throw new Error(
      ".NET MAUI projesinde Android target bulunamadı."
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
      `🔷 .NET MAUI restore başlıyor • ${target}`
    );
  }

  await runDotnet(
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
    dotnetMauiOutputFormats(
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
        `🔷 .NET MAUI publish • ${format.toUpperCase()}`
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

    await runDotnet(
      {
        prepared,
        args,
        onLog,
        cancelled
      }
    );

    const artifact =
      await findPublishedArtifact(
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
      "✅ .NET MAUI Android artifactları hazır."
    );
  }

  return {
    ...result,
    targetFramework:
      target
  };
}
