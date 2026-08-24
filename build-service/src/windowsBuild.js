import AdmZip from "adm-zip";
import { promises as fs } from "fs";
import path from "path";
import { spawn } from "child_process";

import { query } from "./db.js";
import { config } from "./config.js";
import {
  materializeInput,
  deleteInput,
  putOutput
} from "./storage.js";
import { storeCache } from "./buildCache.js";
import { appendBuildLog } from "./buildLogs.js";

const ELECTRON_VERSION =
  "43.4.1";

function safeFileName(value) {
  const cleaned =
    String(value || "AppForgeApp")
      .normalize("NFKD")
      .replace(/[^\w.-]+/g, "-")
      .replace(/^-+|-+$/g, "")
      .slice(0, 80);

  return cleaned || "AppForgeApp";
}

function safeNpmName(value) {
  const cleaned =
    String(value || "appforge-app")
      .toLowerCase()
      .replace(/[^a-z0-9._-]+/g, "-")
      .replace(/^-+|-+$/g, "");

  return cleaned || "appforge-app";
}

function safeVersion(value) {
  const text =
    String(value || "1.0.0")
      .trim();

  return /^\d+\.\d+\.\d+([+-][0-9A-Za-z.-]+)?$/
    .test(text)
    ? text
    : "1.0.0";
}

export function preflightWindows(
  c,
  files = {}
) {
  const out = [];
  const ok =
    text =>
      out.push(`✅ ${text}`);
  const warn =
    text =>
      out.push(`⚠️ ${text}`);

  const appName =
    String(
      c.appName || ""
    ).trim();

  if (!appName) {
    throw new Error(
      "Windows uygulama adı gerekli."
    );
  }

  ok(
    "Windows uygulama adı geçerli."
  );

  const appId =
    String(
      c.packageName || ""
    ).trim();

  if (
    !/^[A-Za-z_]\w*(\.[A-Za-z_]\w*)+$/
      .test(appId)
  ) {
    throw new Error(
      "Geçersiz Windows app ID."
    );
  }

  ok(
    "Windows app ID geçerli."
  );

  if (
    !String(
      c.versionName || ""
    ).trim()
  ) {
    throw new Error(
      "Windows versionName gerekli."
    );
  }

  ok(
    "versionName dolu."
  );

  if (
    c.sourceMode ===
    "URL"
  ) {
    if (
      !/^https:\/\//i.test(
        c.webUrl || ""
      )
    ) {
      throw new Error(
        "Windows URL modu HTTPS gerektirir."
      );
    }

    ok(
      "HTTPS web kaynağı mevcut."
    );
  } else {
    if (
      !files.hasProject
    ) {
      throw new Error(
        "Windows için yerel proje ZIP'i eksik."
      );
    }

    ok(
      "Yerel proje kaynağı mevcut."
    );
  }

  ok(
    "Windows x64 hedefi."
  );

  ok(
    "Electron portable paketleme."
  );

  warn(
    "Windows kod imzası yok; SmartScreen uyarısı görülebilir."
  );

  return out;
}


async function log(
  buildId,
  line
) {
  const text =
    String(line || "");

  console.log(
    `[WINDOWS ${buildId}] ${text}`
  );

  await appendBuildLog(
    buildId,
    text
  );
}

async function updateBuild(
  buildId,
  fields
) {
  const allowed = {
    status: "status",
    progress: "progress",
    outputs: "outputs",
    startedAt: "started_at",
    completedAt: "completed_at",
    workerId: "worker_id",
    durationMs: "duration_ms",
    artifactManifest:
      "artifact_manifest"
  };

  const sets = [];
  const values = [buildId];

  let index = 2;

  for (
    const [key, value]
    of Object.entries(fields)
  ) {
    const column =
      allowed[key];

    if (!column) {
      continue;
    }

    if (
      key === "outputs" ||
      key === "artifactManifest"
    ) {
      sets.push(
        `${column} = $${index}::jsonb`
      );

      values.push(
        JSON.stringify(
          value || {}
        )
      );
    } else {
      sets.push(
        `${column} = $${index}`
      );

      values.push(value);
    }

    index += 1;
  }

  if (!sets.length) {
    return;
  }

  await query(
    `UPDATE appforge_builds
     SET ${sets.join(", ")}
     WHERE id = $1`,
    values
  );
}

async function throwIfCancelled(
  buildId
) {
  const result =
    await query(
      `SELECT cancel_requested
       FROM appforge_builds
       WHERE id = $1`,
      [buildId]
    );

  if (
    result.rows[0]
      ?.cancel_requested
  ) {
    const error =
      new Error(
        "Build kullanıcı tarafından iptal edildi."
      );

    error.code =
      "BUILD_CANCELLED";

    throw error;
  }
}

function safeEntryName(
  name
) {
  const normalized =
    String(name || "")
      .replaceAll("\\", "/");

  const parts =
    normalized
      .split("/")
      .filter(Boolean);

  if (
    normalized.startsWith("/") ||
    parts.includes("..")
  ) {
    throw new Error(
      "ZIP içinde güvensiz dosya yolu bulundu."
    );
  }

  return parts.join("/");
}

async function extractZipSafely(
  zipFile,
  targetDir
) {
  const zip =
    new AdmZip(zipFile);

  const entries =
    zip.getEntries();

  if (
    entries.length > 10000
  ) {
    throw new Error(
      "Proje çok fazla dosya içeriyor."
    );
  }

  const root =
    path.resolve(targetDir);

  await fs.mkdir(
    root,
    {
      recursive: true
    }
  );

  let totalBytes = 0;

  for (
    const entry of entries
  ) {
    const relative =
      safeEntryName(
        entry.entryName
      );

    if (!relative) {
      continue;
    }

    totalBytes +=
      Number(
        entry.header?.size ||
        0
      );

    if (
      totalBytes >
      500 * 1024 * 1024
    ) {
      throw new Error(
        "Açılmış proje 500 MB sınırını aşıyor."
      );
    }

    const destination =
      path.resolve(
        root,
        relative
      );

    if (
      destination !== root &&
      !destination.startsWith(
        root + path.sep
      )
    ) {
      throw new Error(
        "ZIP path traversal engellendi."
      );
    }

    if (entry.isDirectory) {
      await fs.mkdir(
        destination,
        {
          recursive: true
        }
      );

      continue;
    }

    await fs.mkdir(
      path.dirname(
        destination
      ),
      {
        recursive: true
      }
    );

    await fs.writeFile(
      destination,
      entry.getData()
    );
  }
}

async function collectFiles(
  root,
  relative = ""
) {
  const current =
    path.join(
      root,
      relative
    );

  const entries =
    await fs.readdir(
      current,
      {
        withFileTypes: true
      }
    );

  const found = [];

  for (
    const entry of entries
  ) {
    const child =
      path.join(
        relative,
        entry.name
      );

    if (
      entry.isDirectory()
    ) {
      found.push(
        ...await collectFiles(
          root,
          child
        )
      );
    } else {
      found.push(
        child
      );
    }
  }

  return found;
}

async function findStartPage(
  siteDir
) {
  const files =
    await collectFiles(
      siteDir
    );

  const html =
    files.filter(
      file =>
        file
          .toLowerCase()
          .endsWith(".html")
    );

  if (!html.length) {
    throw new Error(
      "Projede HTML başlangıç dosyası bulunamadı."
    );
  }

  const index =
    html
      .filter(
        file =>
          path.basename(file)
            .toLowerCase() ===
          "index.html"
      )
      .sort(
        (a, b) =>
          a.length -
          b.length
      )[0];

  return (
    index ||
    html.sort(
      (a, b) =>
        a.length -
        b.length
    )[0]
  ).replaceAll(
    "\\",
    "/"
  );
}

async function runBuilder(
  buildId,
  projectDir
) {
  const executable =
    path.join(
      process.cwd(),
      "node_modules",
      ".bin",
      process.platform ===
        "win32"
        ? "electron-builder.cmd"
        : "electron-builder"
    );

  await new Promise(
    (
      resolve,
      reject
    ) => {
      const child =
        spawn(
          executable,
          [
            "--win",
            "portable",
            "--x64",
            "--publish",
            "never"
          ],
          {
            cwd:
              projectDir,

            env: {
              ...process.env,

              CSC_IDENTITY_AUTO_DISCOVERY:
                "false"
            },

            stdio: [
              "ignore",
              "pipe",
              "pipe"
            ]
          }
        );

      const pipe =
        stream => {
          stream.on(
            "data",
            chunk => {
              const lines =
                String(chunk)
                  .split(
                    /\r?\n/
                  )
                  .map(
                    line =>
                      line.trim()
                  )
                  .filter(Boolean);

              for (
                const line
                of lines
              ) {
                log(
                  buildId,
                  line
                ).catch(
                  () => {}
                );
              }
            }
          );
        };

      pipe(child.stdout);
      pipe(child.stderr);

      child.once(
        "error",
        reject
      );

      child.once(
        "close",
        (
          code,
          signal
        ) => {
          if (
            code === 0
          ) {
            resolve();
          } else {
            reject(
              new Error(
                signal
                  ? `electron-builder sinyal ile kapandı: ${signal}`
                  : `electron-builder çıkış kodu: ${code}`
              )
            );
          }
        }
      );
    }
  );
}

async function findExe(
  directory
) {
  const files =
    await collectFiles(
      directory
    );

  const candidates =
    files
      .filter(
        file =>
          file
            .toLowerCase()
            .endsWith(".exe")
      )
      .filter(
        file =>
          !file
            .toLowerCase()
            .includes(
              "unpacked"
            )
      );

  if (!candidates.length) {
    throw new Error(
      "Windows EXE çıktısı bulunamadı."
    );
  }

  return path.join(
    directory,
    candidates[0]
  );
}

export async function executeWindowsBuild(
  job
) {
  const {
    buildId,
    workerId = null,
    cacheKey = null,
    config: c,
    projectRef,
    iconRef
  } = job;

  if (
    c.buildOutput !== "exe"
  ) {
    throw new Error(
      "Windows Worker yalnız EXE build kabul eder."
    );
  }

  const startedAtMs =
    Date.now();

  const work =
    path.join(
      config.workRoot,
      `${buildId}-windows`
    );

  const appDir =
    path.join(
      work,
      "electron-app"
    );

  const siteDir =
    path.join(
      appDir,
      "site"
    );

  let uploadedProject =
    null;

  let buildSucceeded =
    false;

  try {
    await fs.rm(
      work,
      {
        recursive: true,
        force: true
      }
    );

    await fs.mkdir(
      appDir,
      {
        recursive: true
      }
    );

    await updateBuild(
      buildId,
      {
        status:
          "building",
        progress:
          5,
        startedAt:
          new Date(),
        workerId
      }
    );

    await log(
      buildId,
      "🪟 Windows EXE build başlatıldı."
    );

    if (
      c.sourceMode ===
      "LOCAL"
    ) {
      if (!projectRef) {
        throw new Error(
          "EXE için yerel proje dosyası eksik."
        );
      }

      uploadedProject =
        await materializeInput(
          projectRef,
          path.join(
            work,
            "project.zip"
          )
        );

      await extractZipSafely(
        uploadedProject,
        siteDir
      );
    }

    await throwIfCancelled(
      buildId
    );

    const startPage =
      c.sourceMode ===
      "LOCAL"
        ? await findStartPage(
            siteDir
          )
        : null;

    if (
      c.sourceMode ===
        "URL" &&
      !/^https:\/\//i.test(
        c.webUrl || ""
      )
    ) {
      throw new Error(
        "Windows URL modu HTTPS gerektirir."
      );
    }

    const manifest = {
      format:
        "appforge-project",

      formatVersion:
        1,

      appName:
        c.appName,

      appId:
        c.packageName,

      versionName:
        c.versionName,

      sourceMode:
        c.sourceMode,

      webUrl:
        c.sourceMode ===
          "URL"
          ? c.webUrl
          : "",

      startPage:
        startPage || "",

      createdBy:
        "AppForge Studio",

      target:
        "windows-x64"
    };

    await fs.writeFile(
      path.join(
        appDir,
        "appforge-project.json"
      ),
      JSON.stringify(
        manifest,
        null,
        2
      )
    );

    const mainSource =
`const {
  app,
  BrowserWindow,
  shell
} = require("electron");

const fs = require("fs");
const path = require("path");

const manifest =
  JSON.parse(
    fs.readFileSync(
      path.join(
        __dirname,
        "appforge-project.json"
      ),
      "utf8"
    )
  );

function createWindow() {
  const win =
    new BrowserWindow({
      width: 1280,
      height: 800,
      minWidth: 640,
      minHeight: 480,
      backgroundColor:
        ${JSON.stringify(
          c.backgroundColor ||
          "#07101F"
        )},
      autoHideMenuBar: true,
      webPreferences: {
        nodeIntegration: false,
        contextIsolation: true,
        sandbox: true,
        webSecurity: true
      }
    });

  win.webContents
    .setWindowOpenHandler(
      ({ url }) => {
        if (
          /^https?:\\/\\//i
            .test(url)
        ) {
          shell.openExternal(url);
        }

        return {
          action: "deny"
        };
      }
    );

  if (
    manifest.sourceMode ===
    "URL"
  ) {
    win.loadURL(
      manifest.webUrl
    );
  } else {
    win.loadFile(
      path.join(
        __dirname,
        "site",
        manifest.startPage
      )
    );
  }
}

app.whenReady()
  .then(
    () => {
      createWindow();

      app.on(
        "activate",
        () => {
          if (
            BrowserWindow
              .getAllWindows()
              .length === 0
          ) {
            createWindow();
          }
        }
      );
    }
  );

app.on(
  "window-all-closed",
  () => {
    if (
      process.platform !==
      "darwin"
    ) {
      app.quit();
    }
  }
);
`;

    await fs.writeFile(
      path.join(
        appDir,
        "main.cjs"
      ),
      mainSource
    );

    await fs.writeFile(
      path.join(
        appDir,
        "before-build.cjs"
      ),
      `exports.default = async function () {
  return false;
};
`
    );

    const packageJson = {
      name:
        safeNpmName(
          c.appName
        ),

      version:
        safeVersion(
          c.versionName
        ),

      main:
        "main.cjs",

      devDependencies: {
        electron:
          ELECTRON_VERSION
      },

      build: {
        beforeBuild:
          "./before-build.cjs",

        files: [
          "main.cjs",
          "site/**/*",
          "appforge-project.json"
        ],
        appId:
          c.packageName,

        productName:
          c.appName ||
          "AppForge App",

        electronVersion:
          ELECTRON_VERSION,

        asar:
          true,

        npmRebuild:
          false,

        // Railway Windows Worker has a limited memory budget.
        // Store mode avoids memory-heavy 7-Zip maximum compression.
        compression:
          "store",

        directories: {
          output:
            "dist"
        },

        files: [
          "main.cjs",
          "appforge-project.json",
          "site/**/*"
        ],

        win: {
          signAndEditExecutable:
            false,

          target: [
            {
              target:
                "portable",

              arch: [
                "x64"
              ]
            }
          ]
        },

        portable: {
          artifactName:
            "AppForge-${version}-Windows.${ext}"
        }
      }
    };

    await fs.writeFile(
      path.join(
        appDir,
        "package.json"
      ),
      JSON.stringify(
        packageJson,
        null,
        2
      )
    );

    await updateBuild(
      buildId,
      {
        progress:
          30
      }
    );

    await log(
      buildId,
      "⚙️ Electron Windows portable paketi hazırlanıyor..."
    );

    await runBuilder(
      buildId,
      appDir
    );

    await throwIfCancelled(
      buildId
    );

    await updateBuild(
      buildId,
      {
        progress:
          90
      }
    );

    const exe =
      await findExe(
        path.join(
          appDir,
          "dist"
        )
      );

    const outputName =
      `${safeFileName(
        c.appName
      )}-${safeVersion(
        c.versionName
      )}.exe`;

    const out = {
      exe:
        await putOutput(
          buildId,
          outputName,
          exe
        )
    };

    const durationMs =
      Date.now() -
      startedAtMs;

    const artifactManifest = {
      workerId,
      durationMs,

      createdAt:
        new Date()
          .toISOString(),

      packageName:
        c.packageName,

      versionName:
        c.versionName,

      versionCode:
        c.versionCode,

      outputs:
        out,

      buildMode:
        "WINDOWS_ELECTRON_PORTABLE",

      platform:
        "windows",

      architecture:
        "x64",

      electronVersion:
        ELECTRON_VERSION
    };

    await updateBuild(
      buildId,
      {
        status:
          "success",

        progress:
          100,

        outputs:
          out,

        completedAt:
          new Date(),

        workerId,

        durationMs,

        artifactManifest
      }
    );

    if (cacheKey) {
      await storeCache({
        cacheKey,
        sourceBuildId:
          buildId,

        outputs:
          out,

        metadata: {
          packageName:
            c.packageName,

          versionName:
            c.versionName,

          versionCode:
            c.versionCode,

          buildOutput:
            "exe"
        }
      });
    }

    await log(
      buildId,
      "✅ Windows portable EXE başarıyla oluşturuldu."
    );

    buildSucceeded =
      true;
  } catch (error) {
    await log(
      buildId,
      `HATA: ${
        String(
          error?.message ||
          error
        )
      }`
    );

    throw error;
  } finally {
    if (buildSucceeded) {
      for (
        const ref
        of [
          projectRef,
          iconRef
        ]
      ) {
        if (!ref) {
          continue;
        }

        try {
          await deleteInput(
            ref
          );
        } catch {}
      }
    }

    try {
      await fs.rm(
        work,
        {
          recursive: true,
          force: true
        }
      );
    } catch {}
  }
}
