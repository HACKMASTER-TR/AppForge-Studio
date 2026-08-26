import AdmZip from "adm-zip";
import { promises as fs } from "fs";
import path from "path";
import { spawn } from "child_process";
import { existsSync } from "fs";
import { createSourceBuildEnv } from "./sourceBuildEnv.js";

const MAX_ZIP_ENTRIES = 12000;
const MAX_UNCOMPRESSED_BYTES = 250 * 1024 * 1024;

function safeInside(root, candidate) {
  const a = path.resolve(root);
  const b = path.resolve(candidate);
  return b === a || b.startsWith(a + path.sep);
}

function ignored(segments) {
  const names = new Set([
    ".git", ".idea", ".next", ".nuxt", ".output",
    "node_modules", "dist", "build", "out"
  ]);
  return segments.some(x => names.has(x));
}

async function extractZip(zipPath, destination) {
  const zip = new AdmZip(zipPath);
  const entries = zip.getEntries();

  if (entries.length > MAX_ZIP_ENTRIES) {
    throw new Error("Next.js / Nuxt projesinde çok fazla ZIP girdisi var.");
  }

  await fs.rm(destination, { recursive: true, force: true });
  await fs.mkdir(destination, { recursive: true });

  let total = 0;

  for (const entry of entries) {
    const raw = String(entry.entryName || "").replaceAll("\\", "/");

    if (!raw || raw.startsWith("/") || raw.includes("\0")) {
      throw new Error("Next.js / Nuxt ZIP yolu güvenli değil.");
    }

    const normalized = path.posix.normalize(raw);

    if (normalized === ".." || normalized.startsWith("../")) {
      throw new Error("Next.js / Nuxt ZIP dizin dışına çıkmaya çalışıyor.");
    }

    const segments = normalized.split("/").filter(Boolean);

    if (!segments.length || ignored(segments)) {
      continue;
    }

    const target = path.join(destination, ...segments);

    if (!safeInside(destination, target)) {
      throw new Error("Next.js / Nuxt ZIP hedef yolu güvenli değil.");
    }

    if (entry.isDirectory) {
      await fs.mkdir(target, { recursive: true });
      continue;
    }

    const data = entry.getData();
    total += data.length;

    if (total > MAX_UNCOMPRESSED_BYTES) {
      throw new Error("Next.js / Nuxt ZIP açıldığında boyut sınırını aşıyor.");
    }

    await fs.mkdir(path.dirname(target), { recursive: true });
    await fs.writeFile(target, data);
  }

  return { entries: entries.length, bytes: total };
}

async function walk(root, depth = 0, result = []) {
  if (depth > 6 || result.length >= 6000) return result;

  let entries;
  try {
    entries = await fs.readdir(root, { withFileTypes: true });
  } catch {
    return result;
  }

  for (const entry of entries) {
    if (result.length >= 6000) break;
    if (ignored([entry.name])) continue;

    const full = path.join(root, entry.name);

    if (entry.isDirectory()) {
      await walk(full, depth + 1, result);
    } else if (entry.isFile()) {
      result.push(full);
    }
  }

  return result;
}

async function findPackageRoot(root) {
  const files = await walk(root);

  const candidates = files
    .filter(file => path.basename(file).toLowerCase() === "package.json")
    .sort((a, b) => a.split(path.sep).length - b.split(path.sep).length);

  if (!candidates.length) {
    throw new Error("Next.js / Nuxt projesinde package.json bulunamadı.");
  }

  return path.dirname(candidates[0]);
}

async function readJson(file) {
  try {
    return JSON.parse(await fs.readFile(file, "utf8"));
  } catch {
    throw new Error("package.json geçerli JSON değil.");
  }
}

async function firstText(root, names) {
  for (const name of names) {
    const file = path.join(root, name);

    try {
      const stat = await fs.stat(file);

      if (stat.isFile() && stat.size <= 2 * 1024 * 1024) {
        return { file, text: await fs.readFile(file, "utf8") };
      }
    } catch {}
  }

  return { file: null, text: "" };
}

function deps(packageJson) {
  return {
    ...(packageJson?.dependencies || {}),
    ...(packageJson?.devDependencies || {})
  };
}

function nextStaticReady(packageJson, configText) {
  if (packageJson?.appforge?.staticExport === true) {
    return true;
  }

  const compact = String(configText || "").replace(/\s+/g, "");

  return (
    compact.includes('output:"export"') ||
    compact.includes("output:'export'")
  );
}

function nuxtGenerateScript(packageJson) {
  const scripts = packageJson?.scripts || {};

  for (const [name, command] of Object.entries(scripts)) {
    const text = String(command || "").toLowerCase().replace(/\s+/g, " ").trim();

    if (
      (name === "generate" || name === "build:static") &&
      (text.includes("nuxt generate") || text.includes("nuxi generate"))
    ) {
      return name;
    }
  }

  return null;
}

export async function prepareFrameworkStaticSource({
  projectZip,
  workDir,
  technology = null,
  onLog = null,
  cancelled = null
}) {
  if (!projectZip) {
    throw new Error("Next.js / Nuxt kaynak ZIP'i eksik.");
  }

  if (cancelled) await cancelled();

  const source = path.join(workDir, "source");
  const extracted = await extractZip(projectZip, source);
  const projectRoot = await findPackageRoot(source);
  const packageFile = path.join(projectRoot, "package.json");
  const packageJson = await readJson(packageFile);
  const dependencies = deps(packageJson);

  const isNext = Boolean(dependencies.next);
  const isNuxt = Boolean(dependencies.nuxt);

  if (!isNext && !isNuxt) {
    throw new Error("Proje Next.js veya Nuxt olarak doğrulanamadı.");
  }

  const nextConfig = await firstText(projectRoot, [
    "next.config.js", "next.config.mjs", "next.config.cjs", "next.config.ts"
  ]);

  const nuxtConfig = await firstText(projectRoot, [
    "nuxt.config.ts", "nuxt.config.js", "nuxt.config.mjs"
  ]);

  const framework = isNext ? "nextjs" : "nuxt";
  const generateScript = isNuxt ? nuxtGenerateScript(packageJson) : null;
  const staticReady = isNext
    ? nextStaticReady(packageJson, nextConfig.text)
    : Boolean(generateScript);

  const command = isNext
    ? (packageJson?.scripts?.build ? "npm run build" : null)
    : (generateScript ? `npm run ${generateScript}` : null);

  const outputDir = isNext ? "out" : ".output/public";

  const reason = staticReady
    ? (
        isNext
          ? "Next.js static export yapılandırması bulundu."
          : "Nuxt static generate scripti bulundu."
      )
    : (
        isNext
          ? "Next.js SSR olabilir; output: 'export' veya appforge.staticExport=true gerekli."
          : "Nuxt SSR olabilir; nuxt/nuxi generate scripti gerekli."
      );

  if (onLog) {
    await onLog(
      `🌐 ${framework} static export foundation • ${staticReady ? "hazır" : "kapalı"}`
    );
  }

  return {
    technology: technology || framework,
    framework,
    projectRoot,
    packageFile,
    packageJson,
    staticReady,
    command,
    outputDir,
    reason,
    nextConfigFile: nextConfig.file,
    nuxtConfigFile: nuxtConfig.file,
    generateScript,
    extractedEntries: extracted.entries,
    extractedBytes: extracted.bytes
  };
}

const FRAMEWORK_INSTALL_TIMEOUT_MS =
  8 * 60 * 1000;

const FRAMEWORK_BUILD_TIMEOUT_MS =
  12 * 60 * 1000;

const FRAMEWORK_LOG_LIMIT =
  250_000;

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

  const cli =
    candidates.find(
      candidate =>
        existsSync(
          candidate
        )
    );

  if (
    cli
  ) {
    return {
      command:
        process.execPath,
      args: [
        cli,
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

function frameworkEnv(
  root
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
          root,
          ".appforge-home"
        ),
      TMPDIR:
        path.join(
          root,
          ".appforge-tmp"
        ),
      CI:
        "true",
      NODE_ENV:
        "production",
      NEXT_TELEMETRY_DISABLED:
        "1",
      NUXT_TELEMETRY_DISABLED:
        "1",
      NPM_CONFIG_AUDIT:
        "false",
      NPM_CONFIG_FUND:
        "false",
      NPM_CONFIG_UPDATE_NOTIFIER:
        "false",
      NPM_CONFIG_PROGRESS:
        "false",
      NPM_CONFIG_PRODUCTION:
        "false"
    }
  );
}

async function runFrameworkCommand({
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

      let collected =
        "";

      const consume =
        chunk => {
          const value =
            String(
              chunk
            );

          if (
            collected.length <
              FRAMEWORK_LOG_LIMIT
          ) {
            collected +=
              value.slice(
                0,
                FRAMEWORK_LOG_LIMIT -
                  collected.length
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
              collected
            );
          } else {
            reject(
              new Error(
                `${command} ${args.join(" ")} başarısız (exit=${code}).\n` +
                collected.slice(
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

export function frameworkStaticOutputDirectory(
  framework
) {
  if (
    framework ===
      "nextjs"
  ) {
    return "out";
  }

  if (
    framework ===
      "nuxt"
  ) {
    return ".output/public";
  }

  throw new Error(
    `Bilinmeyen static framework: ${framework}`
  );
}

function relativeAssetText(
  content,
  extension
) {
  let value =
    String(
      content ||
      ""
    );

  if (
    extension ===
      ".html"
  ) {
    value =
      value
        .replace(
          /\b(src|href)=["']\/(?!\/)/gi,
          '$1="./'
        )
        .replace(
          /\b(srcset)=["']\/(?!\/)/gi,
          '$1="./'
        );
  }

  if (
    extension ===
      ".css"
  ) {
    value =
      value.replace(
        /url\(\s*(['"]?)\/(?!\/)/gi,
        "url($1./"
      );
  }

  return value;
}

export async function rewriteFrameworkStaticAssets(
  outputDir
) {
  const files =
    await walk(
      outputDir
    );

  let changed =
    0;

  for (
    const file of
    files
  ) {
    const extension =
      path.extname(
        file
      )
        .toLowerCase();

    if (
      ![
        ".html",
        ".css"
      ].includes(
        extension
      )
    ) {
      continue;
    }

    const stat =
      await fs.stat(
        file
      );

    if (
      stat.size >
        4 * 1024 * 1024
    ) {
      continue;
    }

    const before =
      await fs.readFile(
        file,
        "utf8"
      );

    const after =
      relativeAssetText(
        before,
        extension
      );

    if (
      before !==
        after
    ) {
      await fs.writeFile(
        file,
        after,
        "utf8"
      );

      changed +=
        1;
    }
  }

  return changed;
}

async function zipOutput(
  outputDir,
  target
) {
  const zip =
    new AdmZip();

  zip.addLocalFolder(
    outputDir
  );

  await fs.mkdir(
    path.dirname(
      target
    ),
    {
      recursive: true
    }
  );

  zip.writeZip(
    target
  );

  const stat =
    await fs.stat(
      target
    );

  if (
    stat.size <=
      0
  ) {
    throw new Error(
      "Framework static output ZIP oluşturulamadı."
    );
  }
}

export async function buildFrameworkStaticSource({
  projectZip,
  workDir,
  technology = null,
  onLog = null,
  cancelled = null
}) {
  const prepared =
    await prepareFrameworkStaticSource(
      {
        projectZip,
        workDir,
        technology,
        onLog,
        cancelled
      }
    );

  if (
    !prepared.staticReady
  ) {
    throw new Error(
      prepared.reason
    );
  }

  if (
    !prepared.command
  ) {
    throw new Error(
      `${prepared.framework} için static build komutu bulunamadı.`
    );
  }

  const env =
    frameworkEnv(
      workDir
    );

  const hasLock =
    await fileExists(
      path.join(
        prepared.projectRoot,
        "package-lock.json"
      )
    );

  const install =
    npmInvocation(
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
          ]
    );

  if (
    onLog
  ) {
    await onLog(
      hasLock
        ? "📦 Framework npm ci • lifecycle scripts kapalı"
        : "📦 Framework npm install • lifecycle scripts kapalı"
    );
  }

  await runFrameworkCommand(
    {
      cwd:
        prepared.projectRoot,
      command:
        install.command,
      args:
        install.args,
      env,
      timeoutMs:
        FRAMEWORK_INSTALL_TIMEOUT_MS,
      onLog,
      cancelled
    }
  );

  const buildArgs =
    prepared.framework ===
      "nextjs"
      ? [
          "run",
          "build"
        ]
      : [
          "run",
          prepared.generateScript
        ];

  const build =
    npmInvocation(
      buildArgs
    );

  if (
    onLog
  ) {
    await onLog(
      `⚙️ ${prepared.framework} static build başlıyor`
    );
  }

  await runFrameworkCommand(
    {
      cwd:
        prepared.projectRoot,
      command:
        build.command,
      args:
        build.args,
      env,
      timeoutMs:
        FRAMEWORK_BUILD_TIMEOUT_MS,
      onLog,
      cancelled
    }
  );

  if (
    cancelled
  ) {
    await cancelled();
  }

  const relativeOutput =
    frameworkStaticOutputDirectory(
      prepared.framework
    );

  const outputDir =
    path.resolve(
      prepared.projectRoot,
      relativeOutput
    );

  if (
    !safeInside(
      prepared.projectRoot,
      outputDir
    ) ||
    !(
      await dirExists(
        outputDir
      )
    )
  ) {
    throw new Error(
      `${prepared.framework} static output bulunamadı: ${relativeOutput}`
    );
  }

  if (
    !(
      await fileExists(
        path.join(
          outputDir,
          "index.html"
        )
      )
    )
  ) {
    throw new Error(
      `${prepared.framework} static output içinde index.html bulunamadı.`
    );
  }

  const changed =
    await rewriteFrameworkStaticAssets(
      outputDir
    );

  const compiledZip =
    path.join(
      workDir,
      "compiled-web.zip"
    );

  await zipOutput(
    outputDir,
    compiledZip
  );

  if (
    onLog
  ) {
    await onLog(
      `✅ ${prepared.framework} static WebView çıktısı hazır • ${relativeOutput} • ${changed} dosya göreli asset için düzeltildi`
    );
  }

  return {
    projectZip:
      compiledZip,
    outputDir,
    technology:
      prepared.framework,
    framework:
      prepared.framework,
    packageManager:
      "npm"
  };
}
