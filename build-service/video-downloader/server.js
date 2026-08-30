import express from "express";
import { promises as fs } from "fs";
import path from "path";
import os from "os";
import dns from "dns/promises";
import net from "net";
import crypto from "crypto";
import { spawn } from "child_process";
import { createDubRouter } from "./dub.js";

const app = express();
const PORT = Number(process.env.PORT || 8081);
const YTDLP_BIN = process.env.YTDLP_BIN || "yt-dlp";
const MAX_FILESIZE = process.env.MAX_FILESIZE || "750M";
const MAX_DOWNLOADS = Math.max(1, Number(process.env.MAX_DOWNLOADS || 2));
const PROCESS_TIMEOUT_MS = Math.max(30_000, Number(process.env.PROCESS_TIMEOUT_MS || 15 * 60_000));
const VIDEO_API_TOKEN = String(process.env.VIDEO_API_TOKEN || "").trim();
const TEMP_ROOT = process.env.TEMP_ROOT || path.join(os.tmpdir(), "appforge-video");
const PUBLIC_ROOT = path.resolve("./public");

let activeDownloads = 0;
const ipBuckets = new Map();

app.disable("x-powered-by");
app.use(express.json({ limit: "64kb" }));

app.use((req, res, next) => {
  res.set("Access-Control-Allow-Origin", "*");
  res.set("Access-Control-Allow-Headers", "Content-Type, X-Video-Token");
  res.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  res.set("Cross-Origin-Resource-Policy", "cross-origin");
  res.set("X-Content-Type-Options", "nosniff");
  res.set("Referrer-Policy", "no-referrer");
  if (req.method === "OPTIONS") return res.status(204).end();
  return next();
});

function clientIp(req) {
  return String(req.headers["x-forwarded-for"] || req.socket.remoteAddress || "unknown")
    .split(",")[0]
    .trim();
}

function rateLimit(req, res, next) {
  const now = Date.now();
  const key = clientIp(req);
  const current = ipBuckets.get(key);

  if (!current || now >= current.resetAt) {
    ipBuckets.set(key, { count: 1, resetAt: now + 60_000 });
    return next();
  }

  current.count += 1;
  if (current.count > 40) {
    res.set("Retry-After", String(Math.max(1, Math.ceil((current.resetAt - now) / 1000))));
    return res.status(429).json({ error: "Çok fazla istek. Biraz sonra tekrar deneyin." });
  }
  return next();
}

function requireToken(req, res, next) {
  if (!VIDEO_API_TOKEN) return next();

  const supplied = String(
    req.get("x-video-token") ||
    req.query.token ||
    ""
  );

  const a = Buffer.from(supplied);
  const b = Buffer.from(VIDEO_API_TOKEN);

  if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) {
    return res.status(401).json({ error: "Video API anahtarı geçersiz." });
  }
  return next();
}

function isPrivateIp(address) {
  if (net.isIPv4(address)) {
    const p = address.split(".").map(Number);
    return (
      p[0] === 0 ||
      p[0] === 10 ||
      p[0] === 127 ||
      (p[0] === 169 && p[1] === 254) ||
      (p[0] === 172 && p[1] >= 16 && p[1] <= 31) ||
      (p[0] === 192 && p[1] === 168) ||
      (p[0] === 100 && p[1] >= 64 && p[1] <= 127) ||
      (p[0] >= 224)
    );
  }

  if (net.isIPv6(address)) {
    const value = address.toLowerCase();
    return (
      value === "::1" ||
      value === "::" ||
      value.startsWith("fe80:") ||
      value.startsWith("fc") ||
      value.startsWith("fd") ||
      value.startsWith("ff")
    );
  }

  return true;
}

async function assertPublicUrl(raw) {
  let url;
  try {
    url = new URL(String(raw || ""));
  } catch {
    throw new Error("Geçersiz bağlantı.");
  }

  if (!["http:", "https:"].includes(url.protocol)) {
    throw new Error("Yalnızca http/https bağlantıları desteklenir.");
  }

  if (!url.hostname || url.username || url.password) {
    throw new Error("Geçersiz bağlantı.");
  }

  const host = url.hostname.toLowerCase();
  if (host === "localhost" || host.endsWith(".local")) {
    throw new Error("Yerel ağ adresleri desteklenmez.");
  }

  if (net.isIP(host) && isPrivateIp(host)) {
    throw new Error("Yerel/özel ağ adresleri desteklenmez.");
  }

  const resolved = await dns.lookup(host, { all: true, verbatim: true });
  if (!resolved.length || resolved.some((item) => isPrivateIp(item.address))) {
    throw new Error("Yerel/özel ağ adresleri desteklenmez.");
  }

  return url.toString();
}

function runProcess(command, args, { cwd, timeoutMs = PROCESS_TIMEOUT_MS } = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      cwd,
      shell: false,
      windowsHide: true,
      env: {
        ...process.env,
        HOME: process.env.HOME || os.tmpdir(),
        XDG_CACHE_HOME: process.env.XDG_CACHE_HOME || os.tmpdir()
      }
    });

    let stdout = "";
    let stderr = "";
    let settled = false;

    const timer = setTimeout(() => {
      if (settled) return;
      child.kill("SIGKILL");
      reject(new Error("İşlem zaman aşımına uğradı."));
    }, timeoutMs);

    child.stdout?.on("data", (chunk) => {
      stdout += chunk.toString();
      if (stdout.length > 12 * 1024 * 1024) {
        stdout = stdout.slice(-12 * 1024 * 1024);
      }
    });

    child.stderr?.on("data", (chunk) => {
      stderr += chunk.toString();
      if (stderr.length > 2 * 1024 * 1024) {
        stderr = stderr.slice(-2 * 1024 * 1024);
      }
    });

    child.on("error", (error) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      reject(error);
    });

    child.on("close", (code) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);

      if (code === 0) {
        resolve({ stdout, stderr });
      } else {
        const message = stderr.trim() || `İşlem başarısız oldu (${code}).`;
        reject(new Error(message.slice(0, 1500)));
      }
    });
  });
}

function durationText(seconds) {
  const total = Math.max(0, Math.floor(Number(seconds || 0)));
  if (!total) return "";
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  return h
    ? `${h}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`
    : `${m}:${String(s).padStart(2, "0")}`;
}

function safeFilename(name) {
  return String(name || "video")
    .replace(/[<>:"/\\|?*\x00-\x1f]/g, "_")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 120) || "video";
}

async function cleanupTempRoot() {
  await fs.mkdir(TEMP_ROOT, { recursive: true });
  const now = Date.now();
  const names = await fs.readdir(TEMP_ROOT).catch(() => []);

  await Promise.all(
    names.map(async (name) => {
      const full = path.join(TEMP_ROOT, name);
      try {
        const stat = await fs.stat(full);
        if (now - stat.mtimeMs > 2 * 60 * 60_000) {
          await fs.rm(full, { recursive: true, force: true });
        }
      } catch {}
    })
  );
}

await cleanupTempRoot();
setInterval(() => cleanupTempRoot().catch(() => {}), 30 * 60_000).unref();

app.get("/health", (_req, res) => {
  res.json({
    ok: true,
    service: "AppForge Video Downloader",
    version: "2.0.0",
    activeDownloads,
    maxDownloads: MAX_DOWNLOADS,
    protected: Boolean(VIDEO_API_TOKEN),
    dubbing: true
  });
});

app.get("/", (_req, res) => {
  res.sendFile(path.join(PUBLIC_ROOT, "index.html"));
});

app.use("/assets", express.static(path.join(PUBLIC_ROOT, "assets"), {
  fallthrough: true,
  maxAge: "1h"
}));

app.post("/api/info", rateLimit, requireToken, async (req, res) => {
  try {
    const url = await assertPublicUrl(req.body?.url);

    const { stdout } = await runProcess(YTDLP_BIN, [
      "--dump-single-json",
      "--skip-download",
      "--no-playlist",
      "--no-warnings",
      "--no-cache-dir",
      "--socket-timeout", "15",
      "--retries", "2",
      url
    ], { timeoutMs: 90_000 });

    const info = JSON.parse(stdout);
    const seen = new Set();

    const formats = (info.formats || [])
      .filter((f) =>
        f &&
        f.format_id &&
        f.vcodec &&
        f.vcodec !== "none" &&
        Number(f.height || 0) > 0
      )
      .sort((a, b) => {
        const height = Number(b.height || 0) - Number(a.height || 0);
        if (height) return height;
        return Number(b.fps || 0) - Number(a.fps || 0);
      })
      .filter((f) => {
        const key = [
          f.height || 0,
          Math.round(Number(f.fps || 0)),
          f.ext || "",
          f.acodec && f.acodec !== "none" ? "audio" : "silent"
        ].join(":");
        if (seen.has(key)) return false;
        seen.add(key);
        return true;
      })
      .slice(0, 16)
      .map((f) => ({
        id: String(f.format_id),
        height: Number(f.height || 0),
        fps: Number(f.fps || 0),
        label: `${f.height}p${f.fps ? ` ${Math.round(f.fps)}fps` : ""}`,
        ext: String(f.ext || ""),
        filesize: Number(f.filesize || f.filesize_approx || 0) || null,
        hasAudio: Boolean(f.acodec && f.acodec !== "none")
      }));

    if (!formats.length) {
      throw new Error("İndirilebilir video kalitesi bulunamadı.");
    }

    return res.json({
      title: String(info.title || "Video").slice(0, 300),
      uploader: String(info.uploader || info.channel || "").slice(0, 200),
      duration: Number(info.duration || 0) || null,
      durationText: durationText(info.duration),
      thumbnail: String(info.thumbnail || ""),
      webpageUrl: String(info.webpage_url || url),
      formats
    });
  } catch (error) {
    return res.status(400).json({
      error: String(error?.message || error).slice(0, 1500)
    });
  }
});

app.get("/api/download", rateLimit, requireToken, async (req, res) => {
  if (activeDownloads >= MAX_DOWNLOADS) {
    return res.status(503).json({
      error: "İndirme sunucusu şu anda dolu. Biraz sonra tekrar deneyin."
    });
  }

  let tempDir = null;
  activeDownloads += 1;

  try {
    const url = await assertPublicUrl(req.query.url);
    const formatId = String(req.query.formatId || "").trim();

    if (!/^[A-Za-z0-9._:+-]{1,80}$/.test(formatId)) {
      throw new Error("Geçersiz kalite seçimi.");
    }

    tempDir = await fs.mkdtemp(path.join(TEMP_ROOT, "job-"));
    const outputTemplate = path.join(tempDir, "video.%(ext)s");

    await runProcess(YTDLP_BIN, [
      "--no-playlist",
      "--no-cache-dir",
      "--socket-timeout", "20",
      "--retries", "2",
      "--fragment-retries", "2",
      "--max-filesize", MAX_FILESIZE,
      "--merge-output-format", "mp4",
      "-f", `${formatId}+bestaudio/best`,
      "-o", outputTemplate,
      url
    ], { cwd: tempDir });

    const names = await fs.readdir(tempDir);
    const fileName = names.find((name) =>
      name.startsWith("video.") &&
      !name.endsWith(".part") &&
      !name.endsWith(".ytdl")
    );

    if (!fileName) {
      throw new Error("Video dosyası oluşturulamadı.");
    }

    let title = "video";
    try {
      const result = await runProcess(YTDLP_BIN, [
        "--print", "%(title)s",
        "--skip-download",
        "--no-playlist",
        "--no-warnings",
        "--no-cache-dir",
        url
      ], { timeoutMs: 45_000 });
      title = safeFilename(result.stdout.trim());
    } catch {}

    const fullPath = path.join(tempDir, fileName);
    const ext = path.extname(fullPath) || ".mp4";
    const outName = `${title}${ext}`;

    return res.download(fullPath, outName, async () => {
      activeDownloads = Math.max(0, activeDownloads - 1);
      if (tempDir) {
        await fs.rm(tempDir, { recursive: true, force: true }).catch(() => {});
      }
    });
  } catch (error) {
    activeDownloads = Math.max(0, activeDownloads - 1);
    if (tempDir) {
      await fs.rm(tempDir, { recursive: true, force: true }).catch(() => {});
    }
    if (!res.headersSent) {
      return res.status(400).json({
        error: String(error?.message || error).slice(0, 1500)
      });
    }
  }
});

app.use("/api/dub", rateLimit, requireToken, createDubRouter({
  assertPublicUrl,
  runProcess,
  tempRoot: TEMP_ROOT,
  ytdlpBin: YTDLP_BIN,
  maxFilesize: MAX_FILESIZE,
  safeFilename,
  processTimeoutMs: PROCESS_TIMEOUT_MS
}));

app.use((error, _req, res, _next) => {
  if (res.headersSent) return;
  res.status(500).json({
    error: String(error?.message || "Sunucu hatası").slice(0, 500)
  });
});

app.listen(PORT, "0.0.0.0", () => {
  console.log(`AppForge Video Downloader listening on :${PORT}`);
});
