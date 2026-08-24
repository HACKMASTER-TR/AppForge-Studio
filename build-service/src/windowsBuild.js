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

              /*
               * Railway Windows Worker düşük bellekli.
               * electron-builder'ın Node heap'inin
               * bütün cgroup belleğini tüketmesini engelle.
               */
              NODE_OPTIONS:
                "--max-old-space-size=256",

              UV_THREADPOOL_SIZE:
                "2",

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

const EXE_CONVERSION_MAGIC =
  Buffer.from(
    "APPFORGE-EXE-V1!",
    "ascii"
  );

const EXE_PAYLOAD_MAGIC =
  Buffer.from(
    "AFEXEP01",
    "ascii"
  );

const EXE_MAX_PAYLOAD_BYTES =
  536_870_912;

const EXE_MAX_MANIFEST_BYTES =
  262_144;


async function appendFileToFile(
  sourcePath,
  targetPath
) {
  const source =
    await fs.open(
      sourcePath,
      "r"
    );

  const target =
    await fs.open(
      targetPath,
      "a"
    );

  const buffer =
    Buffer.allocUnsafe(
      256 * 1024
    );

  try {
    let position =
      0;

    while (true) {
      const {
        bytesRead
      } =
        await source.read(
          buffer,
          0,
          buffer.length,
          position
        );

      if (
        bytesRead === 0
      ) {
        break;
      }

      let written =
        0;

      while (
        written <
        bytesRead
      ) {
        const result =
          await target.write(
            buffer,
            written,
            bytesRead - written,
            null
          );

        if (
          result.bytesWritten <= 0
        ) {
          throw new Error(
            "EXE dönüşüm paketi yazılamadı."
          );
        }

        written +=
          result.bytesWritten;
      }

      position +=
        bytesRead;
    }

  } finally {
    await source.close();

    await target.close();
  }
}


async function appendAppForgeConversionPayload(
  exePath,
  manifest,
  projectZip
) {
  const manifestBytes =
    Buffer.from(
      JSON.stringify(
        manifest
      ),
      "utf8"
    );

  if (
    manifestBytes.length >
    EXE_MAX_MANIFEST_BYTES
  ) {
    throw new Error(
      "EXE dönüşüm manifesti çok büyük."
    );
  }

  if (
    manifest.sourceMode ===
      "LOCAL" &&
    !projectZip
  ) {
    throw new Error(
      "Yerel EXE dönüşüm projesi eksik."
    );
  }

  const projectBytes =
    projectZip
      ? (
          await fs.stat(
            projectZip
          )
        ).size
      : 0;

  /*
   * Payload yapısı:
   *
   * AFEXEP01        8 byte
   * manifestLength  4 byte uint32 BE
   * manifest JSON
   * project.zip     LOCAL ise
   *
   * Ardından footer:
   *
   * payloadLength   8 byte uint64 BE
   * APPFORGE-EXE-V1! 16 byte
   */
  const payloadLength =
    12 +
    manifestBytes.length +
    projectBytes;

  if (
    payloadLength >
    EXE_MAX_PAYLOAD_BYTES
  ) {
    throw new Error(
      "EXE dönüşüm paketi 512 MB sınırını aşıyor."
    );
  }

  const payloadHeader =
    Buffer.alloc(
      12
    );

  EXE_PAYLOAD_MAGIC.copy(
    payloadHeader,
    0
  );

  payloadHeader.writeUInt32BE(
    manifestBytes.length,
    8
  );

  await fs.appendFile(
    exePath,
    payloadHeader
  );

  await fs.appendFile(
    exePath,
    manifestBytes
  );

  if (
    projectZip
  ) {
    await appendFileToFile(
      projectZip,
      exePath
    );
  }

  const footer =
    Buffer.alloc(
      8 +
      EXE_CONVERSION_MAGIC.length
    );

  footer.writeBigUInt64BE(
    BigInt(
      payloadLength
    ),
    0
  );

  EXE_CONVERSION_MAGIC.copy(
    footer,
    8
  );

  await fs.appendFile(
    exePath,
    footer
  );

  return payloadLength;
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

      producer:
        "AppForge Studio",

      platform:
        "windows",

      appName:
        c.appName,

      appId:
        c.packageName,

      versionName:
        c.versionName,

      versionCode:
        Math.max(
          1,
          Number(
            c.versionCode ||
            1
          )
        ),

      sourceMode:
        c.sourceMode ===
          "URL"
          ? "URL"
          : "LOCAL",

      webUrl:
        c.sourceMode ===
          "URL"
          ? c.webUrl
          : "",

      projectRoot:
        c.sourceMode ===
          "LOCAL"
          ? "project.zip"
          : null,

      startPage:
        startPage || "",

      /*
       * Bunlar Android WebView ayarlarıdır.
       * Windows Electron runtime bunları kullanmaz;
       * APK -> EXE -> APK dönüşümünde kaybolmamaları
       * için EXE manifestinde saklanırlar.
       */
      webView: {
        javaScriptEnabled:
          c.webView?.javaScriptEnabled !== false,

        domStorageEnabled:
          c.webView?.domStorageEnabled !== false,

        zoomEnabled:
          c.webView?.zoomEnabled !== false,

        wideViewPortEnabled:
          c.webView?.wideViewPortEnabled !== false,

        overviewModeEnabled:
          c.webView?.overviewModeEnabled !== false,

        mediaAutoplayEnabled:
          c.webView?.mediaAutoplayEnabled !== false,

        mixedContentAllowed:
          c.webView?.mixedContentAllowed === true
      },

      nativeBridge: {
        mediaPlayer:
          Boolean(
            c.nativeBridge?.enabled &&
            c.nativeBridge?.mediaPlayer
          )
      },

      conversion: {
        apkToExe:
          true,

        exeToApk:
          true
      },

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

    const preloadSource =
`"use strict";

const {
  contextBridge
} = require("electron");

const APP_VERSION =
  ${JSON.stringify(
    String(
      c.versionName ||
      "1.0.0"
    )
  )};

const START_PAGE =
  ${JSON.stringify(
    startPage || ""
  )};

let audio =
  null;

let playlist =
  [];

let currentIndex =
  -1;


function safeText(
  value,
  maxLength
) {
  return String(
    value == null
      ? ""
      : value
  ).slice(
    0,
    maxLength
  );
}


function mediaUrl(
  value
) {
  const text =
    safeText(
      value,
      8192
    ).trim();

  if (!text) {
    throw new Error(
      "Medya URL'si boş."
    );
  }

  const resolved =
    new URL(
      text,
      document.baseURI
    );

  /*
   * İnternet medyası yalnız HTTPS.
   */
  if (
    resolved.protocol ===
    "https:"
  ) {
    return resolved.href;
  }

  /*
   * LOCAL AppForge projelerinde relative medya
   * file:// URL'sine dönüşür.
   *
   * Sadece paketlenmiş site/ ağacının altında kalan
   * dosyalara izin ver. Böylece proje Windows'un
   * diğer yerel dosyalarını okuyamaz.
   */
  if (
    resolved.protocol ===
      "file:" &&
    document.location.protocol ===
      "file:"
  ) {
    const startParts =
      String(
        START_PAGE ||
        ""
      )
        .split("/")
        .filter(Boolean);

    /*
     * index.html dosyasını çıkar.
     * Örn:
     *   pages/player/index.html
     * site köküne çıkmak için 2 seviye gerekir.
     */
    const depth =
      Math.max(
        0,
        startParts.length - 1
      );

    const upward =
      "../".repeat(
        depth
      );

    const siteRoot =
      new URL(
        "./" + upward,
        document.baseURI
      );

    if (
      resolved.href.startsWith(
        siteRoot.href
      )
    ) {
      return resolved.href;
    }

    throw new Error(
      "Yerel medya site klasörünün dışında."
    );
  }

  throw new Error(
    "Medya yalnız HTTPS veya paketlenmiş yerel dosya olabilir."
  );
}


function dispatch(
  name,
  detail
) {
  try {
    window.dispatchEvent(
      new CustomEvent(
        name,
        {
          detail
        }
      )
    );
  } catch {}
}


function currentItem() {
  if (
    currentIndex < 0 ||
    currentIndex >=
      playlist.length
  ) {
    return null;
  }

  return playlist[
    currentIndex
  ];
}


function stateObject() {
  const item =
    currentItem();

  const position =
    audio &&
    Number.isFinite(
      audio.currentTime
    )
      ? Math.max(
          0,
          Math.round(
            audio.currentTime *
            1000
          )
        )
      : 0;

  return {
    playing:
      Boolean(
        audio &&
        !audio.paused &&
        !audio.ended
      ),

    index:
      currentIndex < 0
        ? 0
        : currentIndex,

    count:
      playlist.length,

    position,

    title:
      item
        ? item.title
        : "",

    artist:
      item
        ? item.artist
        : ""
  };
}


function emitState() {
  dispatch(
    "appforge-media-state",
    stateObject()
  );
}


function emitError(
  error
) {
  dispatch(
    "appforge-media-error",
    {
      message:
        safeText(
          error &&
          error.message
            ? error.message
            : error,
          600
        )
    }
  );
}


function updateMediaSessionState() {
  if (
    !audio ||
    !navigator.mediaSession
  ) {
    return;
  }

  try {
    navigator.mediaSession
      .playbackState =
        audio.paused
          ? "paused"
          : "playing";
  } catch {}

  try {
    if (
      typeof navigator
        .mediaSession
        .setPositionState ===
        "function" &&
      Number.isFinite(
        audio.duration
      ) &&
      audio.duration > 0 &&
      Number.isFinite(
        audio.currentTime
      )
    ) {
      navigator.mediaSession
        .setPositionState({
          duration:
            audio.duration,

          playbackRate:
            Number.isFinite(
              audio.playbackRate
            )
              ? audio.playbackRate
              : 1,

          position:
            Math.min(
              audio.duration,
              Math.max(
                0,
                audio.currentTime
              )
            )
        });
    }
  } catch {}
}


function applyMetadata(
  item
) {
  if (
    !navigator.mediaSession ||
    typeof MediaMetadata !==
      "function"
  ) {
    return;
  }

  try {
    navigator.mediaSession
      .metadata =
        new MediaMetadata({
          title:
            item.title ||
            "",

          artist:
            item.artist ||
            "",

          album:
            "",

          artwork:
            item.artwork
              ? [
                  {
                    src:
                      item.artwork
                  }
                ]
              : []
        });
  } catch {}
}


function ensureAudio() {
  if (audio) {
    return audio;
  }

  audio =
    new Audio();

  audio.preload =
    "metadata";

  audio.addEventListener(
    "play",
    () => {
      updateMediaSessionState();
      emitState();
    }
  );

  audio.addEventListener(
    "pause",
    () => {
      updateMediaSessionState();
      emitState();
    }
  );

  audio.addEventListener(
    "loadedmetadata",
    () => {
      updateMediaSessionState();
      emitState();
    }
  );

  audio.addEventListener(
    "timeupdate",
    () => {
      updateMediaSessionState();
    }
  );

  audio.addEventListener(
    "error",
    () => {
      emitError(
        "Windows medya oynatıcısı dosyayı açamadı."
      );
    }
  );

  audio.addEventListener(
    "ended",
    () => {
      if (
        currentIndex + 1 <
        playlist.length
      ) {
        playIndex(
          currentIndex + 1,
          true
        );
      } else {
        updateMediaSessionState();
        emitState();
      }
    }
  );

  return audio;
}


function playIndex(
  index,
  autoplay = true
) {
  if (
    index < 0 ||
    index >=
      playlist.length
  ) {
    return;
  }

  currentIndex =
    index;

  const item =
    playlist[
      currentIndex
    ];

  const player =
    ensureAudio();

  player.src =
    item.url;

  player.load();

  applyMetadata(
    item
  );

  if (
    autoplay !== false
  ) {
    player
      .play()
      .catch(
        emitError
      );
  } else {
    player.pause();
    emitState();
  }
}


const mediaBridge = {

  play(
    url,
    title = "",
    artist = "",
    artwork = ""
  ) {
    const item = {
      url:
        mediaUrl(
          url
        ),

      title:
        safeText(
          title,
          500
        ),

      artist:
        safeText(
          artist,
          500
        ),

      artwork:
        artwork
          ? mediaUrl(
              artwork
            )
          : ""
    };

    playlist =
      [
        item
      ];

    playIndex(
      0,
      true
    );
  },


  setPlaylist(
    items,
    startIndex = 0,
    autoplay = true
  ) {
    if (
      !Array.isArray(
        items
      )
    ) {
      throw new Error(
        "Playlist bir dizi olmalı."
      );
    }

    const cleaned =
      [];

    for (
      const item of
      items.slice(
        0,
        250
      )
    ) {
      if (
        !item ||
        typeof item !==
          "object"
      ) {
        continue;
      }

      try {
        cleaned.push({
          url:
            mediaUrl(
              item.url
            ),

          title:
            safeText(
              item.title,
              500
            ),

          artist:
            safeText(
              item.artist,
              500
            ),

          artwork:
            item.artwork
              ? mediaUrl(
                  item.artwork
                )
              : ""
        });
      } catch {}
    }

    if (
      cleaned.length ===
      0
    ) {
      throw new Error(
        "Playlist içinde geçerli medya yok."
      );
    }

    playlist =
      cleaned;

    const index =
      Math.max(
        0,
        Math.min(
          playlist.length - 1,
          Math.trunc(
            Number(
              startIndex
            ) || 0
          )
        )
      );

    playIndex(
      index,
      autoplay !== false
    );
  },


  pause() {
    const player =
      ensureAudio();

    player.pause();
  },


  resume() {
    const player =
      ensureAudio();

    if (
      !player.src &&
      playlist.length > 0
    ) {
      playIndex(
        Math.max(
          0,
          currentIndex
        ),
        true
      );

      return;
    }

    player
      .play()
      .catch(
        emitError
      );
  },


  next() {
    if (
      currentIndex + 1 <
      playlist.length
    ) {
      playIndex(
        currentIndex + 1,
        true
      );
    }
  },


  previous() {
    if (
      currentIndex > 0
    ) {
      playIndex(
        currentIndex - 1,
        true
      );
    }
  },


  stop() {
    if (audio) {
      audio.pause();

      try {
        audio.currentTime =
          0;
      } catch {}

      audio.removeAttribute(
        "src"
      );

      audio.load();
    }

    playlist =
      [];

    currentIndex =
      -1;

    try {
      if (
        navigator.mediaSession
      ) {
        navigator.mediaSession
          .metadata =
            null;

        navigator.mediaSession
          .playbackState =
            "none";
      }
    } catch {}

    emitState();
  },


  state() {
    const state =
      stateObject();

    dispatch(
      "appforge-media-state",
      state
    );

    return state;
  }
};


function mediaAction(
  name,
  handler
) {
  if (
    !navigator.mediaSession
  ) {
    return;
  }

  try {
    navigator.mediaSession
      .setActionHandler(
        name,
        handler
      );
  } catch {}
}


mediaAction(
  "play",
  () =>
    mediaBridge.resume()
);

mediaAction(
  "pause",
  () =>
    mediaBridge.pause()
);

mediaAction(
  "nexttrack",
  () =>
    mediaBridge.next()
);

mediaAction(
  "previoustrack",
  () =>
    mediaBridge.previous()
);

mediaAction(
  "stop",
  () =>
    mediaBridge.stop()
);

mediaAction(
  "seekbackward",
  details => {
    const player =
      ensureAudio();

    const offset =
      Number(
        details &&
        details.seekOffset
      ) || 10;

    try {
      player.currentTime =
        Math.max(
          0,
          player.currentTime -
          offset
        );
    } catch {}
  }
);

mediaAction(
  "seekforward",
  details => {
    const player =
      ensureAudio();

    const offset =
      Number(
        details &&
        details.seekOffset
      ) || 10;

    try {
      const target =
        player.currentTime +
        offset;

      player.currentTime =
        Number.isFinite(
          player.duration
        )
          ? Math.min(
              player.duration,
              target
            )
          : target;
    } catch {}
  }
);

mediaAction(
  "seekto",
  details => {
    const player =
      ensureAudio();

    const target =
      Number(
        details &&
        details.seekTime
      );

    if (
      Number.isFinite(
        target
      )
    ) {
      try {
        player.currentTime =
          Math.max(
            0,
            target
          );
      } catch {}
    }
  }
);


const frozenMedia =
  Object.freeze(
    mediaBridge
  );


contextBridge
  .exposeInMainWorld(
    "AppForgeMedia",
    frozenMedia
  );


try {
  contextBridge
    .exposeInMainWorld(
      "AppForge",
      Object.freeze({
        media:
          frozenMedia,

        platform() {
          return "windows";
        },

        appVersion() {
          return APP_VERSION;
        },

        adsRemoved() {
          return false;
        }
      })
    );
} catch {}


dispatch(
  "appforge-media-ready",
  {
    platform:
      "windows"
  }
);
`;

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
        preload:
          path.join(
            __dirname,
            "preload.cjs"
          ),
        nodeIntegration: false,
        contextIsolation: true,
        sandbox: true,
        webSecurity: true,
        backgroundThrottling: false
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
        "preload.cjs"
      ),
      preloadSource
    );

    await log(
      buildId,
      "🎵 Windows AppForgeMedia + MediaSession bridge hazır."
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
          "preload.cjs",
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

        /*
         * Büyük LOCAL projelerde ASAR oluşturmak
         * electron-builder RAM kullanımını yükseltiyor.
         * Portable paket zaten tek EXE çıktısı veriyor.
         */
        asar:
          false,

        npmRebuild:
          false,

        // Railway Windows Worker has a limited memory budget.
        // Store mode avoids memory-heavy 7-Zip compression.
        compression:
          "store",

        directories: {
          output:
            "dist"
        },

        files: [
          "main.cjs",
          "preload.cjs",
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

    const conversionPayloadBytes =
      await appendAppForgeConversionPayload(
        exe,
        manifest,
        uploadedProject
      );

    await log(
      buildId,
      `🔁 EXE → APK dönüşüm paketi eklendi (${Math.ceil(
        conversionPayloadBytes /
        1024
      )} KB).`
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
