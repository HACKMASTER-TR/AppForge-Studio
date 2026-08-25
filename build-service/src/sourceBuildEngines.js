import AdmZip from "adm-zip";
import { promises as fs, existsSync } from "fs";
import path from "path";
import { spawn } from "child_process";

const MAX_ZIP_ENTRIES =
  10_000;

const MAX_UNCOMPRESSED_BYTES =
  180 * 1024 * 1024;

const MAX_LOG_CHARS =
  200_000;

const INSTALL_TIMEOUT_MS =
  6 * 60 * 1000;

const BUILD_TIMEOUT_MS =
  8 * 60 * 1000;

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
      "Node/Web projesinde çok fazla ZIP girdisi var."
    );
  }

  let totalBytes =
    0;

  await fs.mkdir(
    destination,
    {
      recursive: true
    }
  );

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
        "../"
      ) ||
      rawName.includes(
        "\0"
      )
    ) {
      throw new Error(
        "Güvensiz ZIP yolu algılandı."
      );
    }

    const normalized =
      path.posix.normalize(
        rawName
      );

    const segments =
      normalized
        .split(
          "/"
        )
        .filter(
          Boolean
        );

    if (
      segments.some(
        segment =>
          segment ===
            "node_modules"
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
        "ZIP dizin dışına çıkmaya çalışıyor."
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
        "Node/Web proje ZIP'i açıldığında boyut sınırını aşıyor."
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
}

async function walk(
  root,
  {
    maxDepth = 6,
    maxFiles = 6_000
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
          withFileTypes: true
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
        entry.name ===
          "node_modules" ||
        entry.name ===
          ".git"
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

async function findPackageRoot(
  extractedRoot
) {
  const direct =
    path.join(
      extractedRoot,
      "package.json"
    );

  try {
    const stat =
      await fs.stat(
        direct
      );

    if (
      stat.isFile()
    ) {
      return extractedRoot;
    }
  } catch {
    // continue
  }

  const files =
    await walk(
      extractedRoot,
      {
        maxDepth: 4,
        maxFiles: 2_000
      }
    );

  const packageFiles =
    files.filter(
      file =>
        path.basename(
          file
        ) ===
          "package.json"
    );

  if (
    packageFiles.length ===
      1
  ) {
    return path.dirname(
      packageFiles[0]
    );
  }

  if (
    packageFiles.length >
      1
  ) {
    const shallow =
      packageFiles
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
        )[0];

    return path.dirname(
      shallow
    );
  }

  throw new Error(
    "Node/Web projesinde package.json bulunamadı."
  );
}

function commandEnv(
  root
) {
  const allowedPath =
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
      );

  return {
    /*
     * Termux'ta LD_PRELOAD/PREFIX gibi değişkenler,
     * alt süreçlerin paket yöneticisi ve Node binary'lerini
     * çalıştırabilmesi için gereklidir.
     *
     * Worker/Docker ortamında da mevcut process environment
     * korunur; yalnız build'e özel değerleri override ederiz.
     */
    ...process.env,
    PATH:
      allowedPath,
    HOME:
      path.join(
        root,
        ".home"
      ),
    TMPDIR:
      path.join(
        root,
        ".tmp"
      ),
    CI:
      "true",
    NODE_ENV:
      "development",
    NPM_CONFIG_AUDIT:
      "false",
    NPM_CONFIG_FUND:
      "false",
    NPM_CONFIG_UPDATE_NOTIFIER:
      "false",
    NPM_CONFIG_PROGRESS:
      "false",
    NPM_CONFIG_PRODUCTION:
      "false",
    PUBLIC_URL:
      ".",
    NEXT_TELEMETRY_DISABLED:
      "1"
  };
}

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


async function runCommand({
  cwd,
  command,
  args,
  env,
  timeoutMs,
  onLog,
  cancelled
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

      let collected =
        "";

      let timer =
        setTimeout(
          () => {
            child.kill(
              "SIGKILL"
            );
          },
          timeoutMs
        );

      function consume(
        chunk
      ) {
        const text =
          String(
            chunk
          );

        if (
          collected.length <
            MAX_LOG_CHARS
        ) {
          collected +=
            text.slice(
              0,
              MAX_LOG_CHARS -
                collected.length
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
                12
              );

          for (
            const line of
            lines
          ) {
            Promise.resolve(
              onLog(
                line.slice(
                  0,
                  700
                )
              )
            )
              .catch(
                () => {}
              );
          }
        }
      }

      child.stdout.on(
        "data",
        consume
      );

      child.stderr.on(
        "data",
        consume
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
          timer =
            null;

          if (
            code ===
              0
          ) {
            resolve(
              collected
            );
          } else {
            reject(
              new Error(
                `${command} ${args.join(" ")} başarısız (exit=${code}).\n` +
                collected.slice(
                  -12_000
                )
              )
            );
          }
        }
      );
    }
  );
}

async function readPackageJson(
  projectRoot
) {
  const packageFile =
    path.join(
      projectRoot,
      "package.json"
    );

  let parsed;

  try {
    parsed =
      JSON.parse(
        await fs.readFile(
          packageFile,
          "utf8"
        )
      );
  } catch {
    throw new Error(
      "package.json geçerli JSON değil."
    );
  }

  if (
    !parsed ||
    typeof parsed !==
      "object"
  ) {
    throw new Error(
      "package.json okunamadı."
    );
  }

  return parsed;
}

async function locateStaticOutput(
  projectRoot,
  packageJson
) {
  const custom =
    packageJson
      ?.appforge
      ?.outputDir;

  const preferred =
    [
      custom,
      "dist",
      "build",
      "out"
    ]
      .filter(
        value =>
          typeof value ===
            "string" &&
          value.trim()
      )
      .map(
        value =>
          path.resolve(
            projectRoot,
            value
          )
      )
      .filter(
        value =>
          safeInside(
            projectRoot,
            value
          )
      );

  async function hasIndex(
    dir
  ) {
    try {
      const stat =
        await fs.stat(
          path.join(
            dir,
            "index.html"
          )
        );

      return stat.isFile();
    } catch {
      return false;
    }
  }

  for (
    const dir of
    preferred
  ) {
    if (
      await hasIndex(
        dir
      )
    ) {
      return dir;
    }

    try {
      const files =
        await walk(
          dir,
          {
            maxDepth: 4,
            maxFiles: 3_000
          }
        );

      const index =
        files.find(
          file =>
            path.basename(
              file
            ) ===
              "index.html"
        );

      if (
        index
      ) {
        return path.dirname(
          index
        );
      }
    } catch {
      // next candidate
    }
  }

  if (
    await hasIndex(
      projectRoot
    )
  ) {
    return projectRoot;
  }

  throw new Error(
    "Build tamamlandı ancak statik index.html çıktısı bulunamadı. " +
    "AppForge şu anda Node/Web projelerinde dist, build veya out gibi statik çıktıları destekler."
  );
}

async function makeWebViewRelative(
  outputDir
) {
  const indexFile =
    path.join(
      outputDir,
      "index.html"
    );

  let html =
    await fs.readFile(
      indexFile,
      "utf8"
    );

  html =
    html
      .replace(
        /\b(src|href)=["']\/(?!\/)/gi,
        '$1="./'
      );

  await fs.writeFile(
    indexFile,
    html,
    "utf8"
  );
}

async function zipStaticOutput(
  outputDir,
  zipPath
) {
  const zip =
    new AdmZip();

  zip.addLocalFolder(
    outputDir
  );

  await fs.mkdir(
    path.dirname(
      zipPath
    ),
    {
      recursive: true
    }
  );

  zip.writeZip(
    zipPath
  );

  const stat =
    await fs.stat(
      zipPath
    );

  if (
    stat.size <=
      0
  ) {
    throw new Error(
      "Derlenmiş web ZIP'i oluşturulamadı."
    );
  }
}

export async function buildNodeWebSource({
  projectZip,
  workDir,
  technology = "node-web",
  onLog = null,
  cancelled = null
}) {
  if (
    !projectZip
  ) {
    throw new Error(
      "Node/Web kaynak ZIP'i eksik."
    );
  }

  const sourceDir =
    path.join(
      workDir,
      "source"
    );

  await fs.rm(
    workDir,
    {
      recursive: true,
      force: true
    }
  );

  await fs.mkdir(
    sourceDir,
    {
      recursive: true
    }
  );

  await extractProjectZip(
    projectZip,
    sourceDir
  );

  const projectRoot =
    await findPackageRoot(
      sourceDir
    );

  const packageJson =
    await readPackageJson(
      projectRoot
    );

  if (
    !packageJson
      ?.scripts
      ?.build
  ) {
    throw new Error(
      "package.json içinde build scripti yok. Örn: \"scripts\": { \"build\": \"vite build\" }"
    );
  }

  if (
    onLog
  ) {
    await onLog(
      `🌐 Node/Web motoru • ${technology}`
    );
  }

  const env =
    commandEnv(
      workDir
    );

  const packageLock =
    path.join(
      projectRoot,
      "package-lock.json"
    );

  let hasLock =
    false;

  try {
    hasLock =
      (
        await fs.stat(
          packageLock
        )
      ).isFile();
  } catch {
    hasLock =
      false;
  }

  if (
    onLog
  ) {
    await onLog(
      hasLock
        ? "📦 npm ci başlatılıyor"
        : "📦 package-lock yok • npm install başlatılıyor"
    );
  }

  const installArgs =
    hasLock
      ? [
          "ci",
          "--include=dev",
          "--ignore-scripts",
          "--no-audit",
          "--no-fund"
        ]
      : [
          "install",
          "--include=dev",
          "--ignore-scripts",
          "--no-audit",
          "--no-fund"
        ];

  const installInvocation =
    npmInvocation(
      installArgs
    );

  await runCommand({
    cwd:
      projectRoot,
    command:
      installInvocation.command,
    args:
      installInvocation.args,
    env,
    timeoutMs:
      INSTALL_TIMEOUT_MS,
    onLog,
    cancelled
  });

  if (
    cancelled
  ) {
    await cancelled();
  }

  const dependencies =
    {
      ...(
        packageJson.dependencies ||
        {}
      ),
      ...(
        packageJson.devDependencies ||
        {}
      )
    };

  const viteBased =
    Boolean(
      dependencies.vite
    );

  const buildArgs =
    viteBased
      ? [
          "run",
          "build",
          "--",
          "--base=./"
        ]
      : [
          "run",
          "build"
        ];

  if (
    onLog
  ) {
    await onLog(
      viteBased
        ? "⚙️ npm run build • WebView için base=./"
        : "⚙️ npm run build"
    );
  }

  const buildInvocation =
    npmInvocation(
      buildArgs
    );

  await runCommand({
    cwd:
      projectRoot,
    command:
      buildInvocation.command,
    args:
      buildInvocation.args,
    env:
      {
        ...env,
        NODE_ENV:
          "production"
      },
    timeoutMs:
      BUILD_TIMEOUT_MS,
    onLog,
    cancelled
  });

  if (
    cancelled
  ) {
    await cancelled();
  }

  const outputDir =
    await locateStaticOutput(
      projectRoot,
      packageJson
    );

  await makeWebViewRelative(
    outputDir
  );

  const compiledZip =
    path.join(
      workDir,
      "compiled-web.zip"
    );

  await zipStaticOutput(
    outputDir,
    compiledZip
  );

  if (
    onLog
  ) {
    await onLog(
      `✅ Statik web çıktısı hazır • ${path.relative(projectRoot, outputDir) || "."}`
    );
  }

  return {
    projectZip:
      compiledZip,
    outputDir,
    technology,
    packageManager:
      "npm"
  };
}
