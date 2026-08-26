import AdmZip from "adm-zip";
import { promises as fs, existsSync } from "fs";
import path from "path";
import { spawn } from "child_process";
import { fileURLToPath } from "url";
import { createSourceBuildEnv } from "./sourceBuildEnv.js";
import {
  buildFrameworkStaticSource
} from "./frameworkStaticBuildEngine.js";

const SOURCE_ENGINE_DIR =
  path.dirname(
    fileURLToPath(
      import.meta.url
    )
  );

const BUILD_SERVICE_ROOT =
  path.resolve(
    SOURCE_ENGINE_DIR,
    ".."
  );

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

  return createSourceBuildEnv(
    {
      /*
       * Termux için PREFIX / LD_PRELOAD helper tarafından
       * allowlist ile korunur. Worker servis secret'ları
       * kullanıcı package build scriptlerine aktarılmaz.
       */
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
    }
  );
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
  const normalizedTechnology =
    String(
      technology ||
      ""
    )
      .trim()
      .toLowerCase();

  if (
    normalizedTechnology ===
      "nextjs" ||
    normalizedTechnology ===
      "nuxt"
  ) {
    return buildFrameworkStaticSource(
      {
        projectZip,
        workDir,
        technology:
          normalizedTechnology,
        onLog,
        cancelled
      }
    );
  }

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

function kotlinString(
  value
) {
  return JSON.stringify(
    String(
      value ?? ""
    )
  );
}

function xmlText(
  value
) {
  return String(
    value ?? ""
  )
    .replaceAll(
      "&",
      "&amp;"
    )
    .replaceAll(
      "<",
      "&lt;"
    )
    .replaceAll(
      ">",
      "&gt;"
    )
    .replaceAll(
      "\"",
      "&quot;"
    )
    .replaceAll(
      "'",
      "&apos;"
    );
}

function pythonPathIgnored(
  relativePath
) {
  const segments =
    relativePath
      .replaceAll(
        "\\",
        "/"
      )
      .split(
        "/"
      )
      .filter(
        Boolean
      );

  const ignored =
    new Set([
      ".git",
      ".idea",
      ".gradle",
      ".venv",
      "venv",
      "__pycache__",
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

function pythonResourceAllowed(
  file
) {
  const ext =
    path.extname(
      file
    )
      .toLowerCase();

  return new Set([
    ".py",
    ".json",
    ".txt",
    ".csv",
    ".yaml",
    ".yml",
    ".toml",
    ".ini",
    ".cfg",
    ".md"
  ])
    .has(
      ext
    );
}

async function findPythonProjectRoot(
  extractedRoot
) {
  const files =
    await walk(
      extractedRoot,
      {
        maxDepth: 7,
        maxFiles: 6_000
      }
    );

  const entries =
    files
      .filter(
        file => {
          const name =
            path.basename(
              file
            )
              .toLowerCase();

          return (
            name ===
              "main.py" ||
            name ===
              "app.py"
          );
        }
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
    !entries.length
  ) {
    throw new Error(
      "Python projesinde main.py veya app.py bulunamadı."
    );
  }

  const entryFile =
    entries[0];

  return {
    projectRoot:
      path.dirname(
        entryFile
      ),
    entryFile
  };
}

async function copyPythonSources(
  projectRoot,
  pythonDest
) {
  const files =
    await walk(
      projectRoot,
      {
        maxDepth: 10,
        maxFiles: 8_000
      }
    );

  let copied =
    0;

  let copiedBytes =
    0;

  const maxFileBytes =
    20 * 1024 * 1024;

  const maxTotalBytes =
    120 * 1024 * 1024;

  for (
    const file of
    files
  ) {
    const relative =
      path.relative(
        projectRoot,
        file
      );

    if (
      pythonPathIgnored(
        relative
      ) ||
      !pythonResourceAllowed(
        file
      )
    ) {
      continue;
    }

    if (
      path.basename(
        file
      ) ===
        "appforge_entry.py"
    ) {
      continue;
    }

    const stat =
      await fs.stat(
        file
      );

    if (
      stat.size >
        maxFileBytes
    ) {
      throw new Error(
        `Python kaynak dosyası çok büyük: ${relative}`
      );
    }

    copiedBytes +=
      stat.size;

    if (
      copiedBytes >
        maxTotalBytes
    ) {
      throw new Error(
        "Python kaynakları toplam boyut sınırını aşıyor."
      );
    }

    const target =
      path.join(
        pythonDest,
        relative
      );

    if (
      !safeInside(
        pythonDest,
        target
      )
    ) {
      throw new Error(
        "Python kaynak yolu güvenli değil."
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

    await fs.copyFile(
      file,
      target
    );

    copied +=
      1;
  }

  if (
    copied ===
      0
  ) {
    throw new Error(
      "Paketlenecek Python kaynağı bulunamadı."
    );
  }

  return {
    copied,
    copiedBytes
  };
}

function pythonEntryModule(
  projectRoot,
  entryFile
) {
  const relative =
    path.relative(
      projectRoot,
      entryFile
    )
      .replaceAll(
        "\\",
        "/"
      )
      .replace(
        /\.py$/i,
        ""
      );

  return relative
    .split(
      "/"
    )
    .filter(
      Boolean
    )
    .join(
      "."
    );
}

async function writePythonEntryBridge(
  pythonDest,
  moduleName
) {
  const code =
`import importlib
import traceback


def run():
    try:
        module = importlib.import_module(${JSON.stringify(moduleName)})

        entry = getattr(
            module,
            "main",
            None,
        )

        if callable(entry):
            result = entry()
            return "" if result is None else str(result)

        value = getattr(
            module,
            "APPFORGE_OUTPUT",
            None,
        )

        if value is not None:
            return str(value)

        return (
            "Python projesi yüklendi.\\n"
            "Giriş modülünde main() fonksiyonu veya "
            "APPFORGE_OUTPUT değeri tanımlayabilirsin."
        )

    except Exception:
        return traceback.format_exc()
`;

  await fs.writeFile(
    path.join(
      pythonDest,
      "appforge_entry.py"
    ),
    code,
    "utf8"
  );
}

async function preparePythonRequirements(
  projectRoot,
  appDir
) {
  const candidates =
    [
      path.join(
        projectRoot,
        "requirements.txt"
      ),
      path.join(
        path.dirname(
          projectRoot
        ),
        "requirements.txt"
      )
    ];

  let sourceFile =
    null;

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
        stat.isFile()
      ) {
        sourceFile =
          candidate;
        break;
      }
    } catch {
    }
  }

  const target =
    path.join(
      appDir,
      "requirements.txt"
    );

  if (
    sourceFile
  ) {
    const stat =
      await fs.stat(
        sourceFile
      );

    if (
      stat.size >
        512 * 1024
    ) {
      throw new Error(
        "requirements.txt beklenenden büyük."
      );
    }

    await fs.copyFile(
      sourceFile,
      target
    );

    return true;
  }

  await fs.writeFile(
    target,
    "# AppForge Python Runtime\\n",
    "utf8"
  );

  return false;
}

async function writePythonAndroidGradle(
  appDir,
  {
    packageName,
    versionCode,
    versionName,
    signing,
    localKeystore
  }
) {
  const customSigning =
    signing?.mode ===
      "CUSTOM";

  if (
    customSigning &&
    !localKeystore
  ) {
    throw new Error(
      "Python Android custom signing için keystore bulunamadı."
    );
  }

  const signingConfig =
    customSigning
      ? `
    signingConfigs {
        create("release") {
            storeFile = file(${kotlinString(localKeystore)})
            storePassword = ${kotlinString(signing?.storePassword)}
            keyAlias = ${kotlinString(signing?.alias)}
            keyPassword = ${kotlinString(signing?.keyPassword)}
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig =
                signingConfigs.getByName("release")
        }
    }
`
      : `
    buildTypes {
        getByName("release") {
            signingConfig =
                signingConfigs.getByName("debug")
        }
    }
`;

  const gradle =
`plugins {
    id("com.android.application")
    id("com.chaquo.python")
}

android {
    namespace = "com.appforge.pythonruntime"
    compileSdk = 37

    defaultConfig {
        applicationId = ${kotlinString(packageName)}
        minSdk = 26
        targetSdk = 37
        versionCode = ${Number(versionCode) || 1}
        versionName = ${kotlinString(versionName || "1.0.0")}

        ndk {
            abiFilters += listOf(
                "arm64-v8a",
                "x86_64"
            )
        }
    }
${signingConfig}
}

chaquopy {
    defaultConfig {
        version = "3.11"

        buildPython(
            "/usr/bin/python3"
        )

        pip {
            install(
                "-r",
                "requirements.txt"
            )
        }
    }
}
`;

  await fs.writeFile(
    path.join(
      appDir,
      "build.gradle.kts"
    ),
    gradle,
    "utf8"
  );
}

async function patchPythonManifest(
  appDir,
  appName
) {
  const manifestFile =
    path.join(
      appDir,
      "src",
      "main",
      "AndroidManifest.xml"
    );

  let manifest =
    await fs.readFile(
      manifestFile,
      "utf8"
    );

  manifest =
    manifest.replace(
      'android:label="AppForge Python"',
      `android:label="${xmlText(appName || "Python App")}"`
    );

  await fs.writeFile(
    manifestFile,
    manifest,
    "utf8"
  );
}

export async function preparePythonAndroidProject({
  projectZip,
  androidProjectDir,
  config,
  localKeystore = null,
  onLog = null,
  cancelled = null
}) {
  if (
    !projectZip
  ) {
    throw new Error(
      "Python kaynak ZIP'i eksik."
    );
  }

  const templateRoot =
    path.join(
      BUILD_SERVICE_ROOT,
      "python-android-template"
    );

  try {
    const stat =
      await fs.stat(
        templateRoot
      );

    if (
      !stat.isDirectory()
    ) {
      throw new Error();
    }
  } catch {
    throw new Error(
      "Python Android runtime template bulunamadı."
    );
  }

  const workRoot =
    path.join(
      path.dirname(
        androidProjectDir
      ),
      "python-source"
    );

  const extractedRoot =
    path.join(
      workRoot,
      "source"
    );

  await fs.rm(
    workRoot,
    {
      recursive: true,
      force: true
    }
  );

  await fs.mkdir(
    extractedRoot,
    {
      recursive: true
    }
  );

  if (
    cancelled
  ) {
    await cancelled();
  }

  await extractProjectZip(
    projectZip,
    extractedRoot
  );

  const {
    projectRoot,
    entryFile
  } =
    await findPythonProjectRoot(
      extractedRoot
    );

  const moduleName =
    pythonEntryModule(
      projectRoot,
      entryFile
    );

  if (
    onLog
  ) {
    await onLog(
      `🐍 Python giriş modülü: ${moduleName}`
    );
  }

  await fs.rm(
    androidProjectDir,
    {
      recursive: true,
      force: true
    }
  );

  await fs.cp(
    templateRoot,
    androidProjectDir,
    {
      recursive: true,
      force: true
    }
  );

  const appDir =
    path.join(
      androidProjectDir,
      "app"
    );

  const pythonDest =
    path.join(
      appDir,
      "src",
      "main",
      "python"
    );

  await fs.rm(
    pythonDest,
    {
      recursive: true,
      force: true
    }
  );

  await fs.mkdir(
    pythonDest,
    {
      recursive: true
    }
  );

  const copied =
    await copyPythonSources(
      projectRoot,
      pythonDest
    );

  await writePythonEntryBridge(
    pythonDest,
    moduleName
  );

  const hasRequirements =
    await preparePythonRequirements(
      projectRoot,
      appDir
    );

  await writePythonAndroidGradle(
    appDir,
    {
      packageName:
        config?.packageName,
      versionCode:
        config?.versionCode,
      versionName:
        config?.versionName,
      signing:
        config?.signing,
      localKeystore
    }
  );

  await patchPythonManifest(
    appDir,
    config?.appName
  );

  if (
    cancelled
  ) {
    await cancelled();
  }

  if (
    onLog
  ) {
    await onLog(
      `✅ Python proje hazır • ${copied.copied} dosya` +
        (
          hasRequirements
            ? " • requirements.txt"
            : ""
        )
    );
  }

  return {
    androidProjectDir,
    entryModule:
      moduleName,
    sourceFiles:
      copied.copied,
    sourceBytes:
      copied.copiedBytes,
    hasRequirements
  };
}

function groovySingleQuoted(
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

async function findAndroidGradleProjectRoot(
  extractedRoot
) {
  const files =
    await walk(
      extractedRoot,
      {
        maxDepth: 7,
        maxFiles: 8_000
      }
    );

  const settings =
    files
      .filter(
        file => {
          const name =
            path.basename(
              file
            )
              .toLowerCase();

          return (
            name ===
              "settings.gradle" ||
            name ===
              "settings.gradle.kts"
          );
        }
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
    const settingsFile of
    settings
  ) {
    const root =
      path.dirname(
        settingsFile
      );

    const manifest =
      path.join(
        root,
        "app",
        "src",
        "main",
        "AndroidManifest.xml"
      );

    try {
      const stat =
        await fs.stat(
          manifest
        );

      if (
        stat.isFile()
      ) {
        return root;
      }
    } catch {
    }
  }

  throw new Error(
    "Android Gradle projesinde settings.gradle(.kts) ve app/src/main/AndroidManifest.xml birlikte bulunamadı."
  );
}

async function cleanupImportedAndroidProject(
  androidProjectDir
) {
  const targets =
    [
      path.join(
        androidProjectDir,
        ".gradle"
      ),
      path.join(
        androidProjectDir,
        "build"
      ),
      path.join(
        androidProjectDir,
        "app",
        "build"
      ),
      path.join(
        androidProjectDir,
        "local.properties"
      )
    ];

  for (
    const target of
    targets
  ) {
    await fs.rm(
      target,
      {
        recursive: true,
        force: true
      }
    );
  }

  const sdk =
    process.env.ANDROID_SDK_ROOT ||
    process.env.ANDROID_HOME ||
    "";

  if (
    sdk
  ) {
    const escaped =
      String(
        sdk
      )
        .replaceAll(
          "\\",
          "\\\\"
        );

    await fs.writeFile(
      path.join(
        androidProjectDir,
        "local.properties"
      ),
      `sdk.dir=${escaped}\n`,
      "utf8"
    );
  }
}

async function writeAndroidSourceOverrides(
  androidProjectDir,
  config,
  localKeystore
) {
  const appDir =
    path.join(
      androidProjectDir,
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
    const stat =
      await fs.stat(
        kts
      );

    if (
      stat.isFile()
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
      const stat =
        await fs.stat(
          groovy
        );

      if (
        stat.isFile()
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
      "Android app modülünde build.gradle veya build.gradle.kts bulunamadı."
    );
  }

  const signing =
    config?.signing ||
    {};

  const customSigning =
    signing.mode ===
      "CUSTOM";

  if (
    customSigning &&
    !localKeystore
  ) {
    throw new Error(
      "Android source CUSTOM signing için keystore bulunamadı."
    );
  }

  const packageName =
    groovySingleQuoted(
      config?.packageName
    );

  const versionName =
    groovySingleQuoted(
      config?.versionName ||
      "1.0.0"
    );

  const versionCode =
    Number(
      config?.versionCode
    ) ||
    1;

  let signingBlock;

  if (
    customSigning
  ) {
    signingBlock =
`
    signingConfigs {
        appforgeRelease {
            storeFile file('${groovySingleQuoted(localKeystore)}')
            storePassword '${groovySingleQuoted(signing.storePassword)}'
            keyAlias '${groovySingleQuoted(signing.alias)}'
            keyPassword '${groovySingleQuoted(signing.keyPassword)}'
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

  const overrideScript =
`android {
    defaultConfig {
        applicationId '${packageName}'
        versionCode ${versionCode}
        versionName '${versionName}'
    }
${signingBlock}
}
`;

  const overrideFile =
    path.join(
      androidProjectDir,
      "appforge-source-overrides.gradle"
    );

  await fs.writeFile(
    overrideFile,
    overrideScript,
    "utf8"
  );

  let appBuild =
    await fs.readFile(
      appBuildFile,
      "utf8"
    );

  const marker =
    "appforge-source-overrides.gradle";

  if (
    !appBuild.includes(
      marker
    )
  ) {
    appBuild =
      appBuild;
  }

  if (
    !appBuild.includes(
      marker
    )
  ) {
    const applyLine =
      kotlinDsl
        ? '\napply(from = rootProject.file("appforge-source-overrides.gradle"))\n'
        : '\napply from: rootProject.file("appforge-source-overrides.gradle")\n';

    appBuild =
      appBuild.trimEnd() +
      applyLine;
  }

  await fs.writeFile(
    appBuildFile,
    appBuild,
    "utf8"
  );

  return {
    appBuildFile,
    kotlinDsl,
    customSigning
  };
}

export async function prepareAndroidGradleProject({
  projectZip,
  androidProjectDir,
  config,
  localKeystore = null,
  onLog = null,
  cancelled = null
}) {
  if (
    !projectZip
  ) {
    throw new Error(
      "Android kaynak ZIP'i eksik."
    );
  }

  const workRoot =
    path.join(
      path.dirname(
        androidProjectDir
      ),
      "android-source"
    );

  const extractedRoot =
    path.join(
      workRoot,
      "source"
    );

  await fs.rm(
    workRoot,
    {
      recursive: true,
      force: true
    }
  );

  await fs.mkdir(
    extractedRoot,
    {
      recursive: true
    }
  );

  if (
    cancelled
  ) {
    await cancelled();
  }

  await extractProjectZip(
    projectZip,
    extractedRoot
  );

  const projectRoot =
    await findAndroidGradleProjectRoot(
      extractedRoot
    );

  if (
    onLog
  ) {
    await onLog(
      "🤖 Android Gradle proje kökü bulundu."
    );
  }

  await fs.rm(
    androidProjectDir,
    {
      recursive: true,
      force: true
    }
  );

  await fs.cp(
    projectRoot,
    androidProjectDir,
    {
      recursive: true,
      force: true
    }
  );

  await cleanupImportedAndroidProject(
    androidProjectDir
  );

  const override =
    await writeAndroidSourceOverrides(
      androidProjectDir,
      config,
      localKeystore
    );

  if (
    cancelled
  ) {
    await cancelled();
  }

  if (
    onLog
  ) {
    await onLog(
      `✅ Android source hazır • ${override.kotlinDsl ? "Kotlin DSL" : "Groovy"} • ` +
        `${override.customSigning ? "CUSTOM signing" : "DEBUG signing"}`
    );
  }

  return {
    androidProjectDir,
    module:
      "app",
    kotlinDsl:
      override.kotlinDsl,
    customSigning:
      override.customSigning
  };
}
