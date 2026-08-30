import express from "express";
import { promises as fs } from "fs";
import path from "path";
import crypto from "crypto";

const DEFAULT_OPENAI_BASE = "https://api.openai.com/v1";
const VOICE_PALETTE = ["cedar", "marin", "coral", "sage", "onyx", "nova"];

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, Number(value)));
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function extractResponseText(payload) {
  if (typeof payload?.output_text === "string" && payload.output_text.trim()) {
    return payload.output_text.trim();
  }
  const chunks = [];
  for (const item of payload?.output || []) {
    for (const part of item?.content || []) {
      if (typeof part?.text === "string") chunks.push(part.text);
    }
  }
  return chunks.join("\n").trim();
}

function safeSegmentText(value) {
  return String(value || "").replace(/\s+/g, " ").trim().slice(0, 3800);
}

function srtTime(seconds) {
  const ms = Math.max(0, Math.round(Number(seconds || 0) * 1000));
  const h = Math.floor(ms / 3600000);
  const m = Math.floor((ms % 3600000) / 60000);
  const s = Math.floor((ms % 60000) / 1000);
  const r = ms % 1000;
  return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")},${String(r).padStart(3, "0")}`;
}

function voiceForSpeaker(speaker) {
  const input = String(speaker || "A");
  let hash = 0;
  for (let i = 0; i < input.length; i += 1) {
    hash = (hash * 31 + input.charCodeAt(i)) >>> 0;
  }
  return VOICE_PALETTE[hash % VOICE_PALETTE.length];
}

function atempoChain(factor) {
  let value = clamp(factor, 0.25, 4);
  const filters = [];
  while (value > 2) {
    filters.push("atempo=2.0");
    value /= 2;
  }
  while (value < 0.5) {
    filters.push("atempo=0.5");
    value /= 0.5;
  }
  filters.push(`atempo=${value.toFixed(4)}`);
  return filters.join(",");
}

async function openAiRequest(url, options, apiKey) {
  const response = await fetch(url, {
    ...options,
    headers: {
      Authorization: `Bearer ${apiKey}`,
      ...(options?.headers || {})
    }
  });
  if (!response.ok) {
    const text = await response.text().catch(() => "");
    throw new Error(`AI servisi hata verdi (${response.status}): ${text.slice(0, 900) || response.statusText}`);
  }
  return response;
}

async function transcribeDiarized(audioPath, cfg) {
  const bytes = await fs.readFile(audioPath);
  const form = new FormData();
  form.append("file", new Blob([bytes], { type: "audio/mpeg" }), "audio.mp3");
  form.append("model", cfg.transcribeModel);
  form.append("response_format", "diarized_json");
  form.append("chunking_strategy", "auto");

  const response = await openAiRequest(`${cfg.openAiBase}/audio/transcriptions`, {
    method: "POST",
    body: form
  }, cfg.apiKey);

  const data = await response.json();
  const segments = Array.isArray(data?.segments)
    ? data.segments
        .map((s, index) => ({
          id: String(s.id || `seg_${index + 1}`),
          speaker: String(s.speaker || "A").slice(0, 40),
          start: Math.max(0, Number(s.start || 0)),
          end: Math.max(0, Number(s.end || 0)),
          text: safeSegmentText(s.text)
        }))
        .filter((s) => s.text && s.end > s.start)
    : [];

  if (!segments.length) throw new Error("Videoda dublaj yapılabilecek konuşma bulunamadı.");
  return segments;
}

async function translateBatch(items, cfg) {
  const schema = {
    type: "object",
    additionalProperties: false,
    properties: {
      items: {
        type: "array",
        items: {
          type: "object",
          additionalProperties: false,
          properties: {
            id: { type: "string" },
            text: { type: "string" }
          },
          required: ["id", "text"]
        }
      }
    },
    required: ["items"]
  };

  const body = {
    model: cfg.translateModel,
    input: [
      {
        role: "system",
        content: [
          {
            type: "input_text",
            text: "You translate spoken dialogue into natural Turkish for dubbing. Preserve meaning, tone, names and numbers. Keep each translation concise enough to fit the original segment duration. Do not add commentary."
          }
        ]
      },
      {
        role: "user",
        content: [
          {
            type: "input_text",
            text: JSON.stringify({ items })
          }
        ]
      }
    ],
    text: {
      format: {
        type: "json_schema",
        name: "turkish_dub_segments",
        strict: true,
        schema
      }
    }
  };

  const response = await openAiRequest(`${cfg.openAiBase}/responses`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body)
  }, cfg.apiKey);
  const data = await response.json();
  const text = extractResponseText(data);
  if (!text) throw new Error("Türkçe çeviri yanıtı boş geldi.");
  const parsed = JSON.parse(text);
  return Array.isArray(parsed?.items) ? parsed.items : [];
}

async function translateSegments(segments, cfg, onProgress) {
  const output = [];
  const batchSize = 24;
  for (let i = 0; i < segments.length; i += batchSize) {
    const slice = segments.slice(i, i + batchSize);
    const items = slice.map((s) => ({ id: s.id, text: s.text }));
    const translated = await translateBatch(items, cfg);
    const byId = new Map(translated.map((item) => [String(item.id), safeSegmentText(item.text)]));
    for (const segment of slice) {
      output.push({ ...segment, translated: byId.get(segment.id) || segment.text });
    }
    const done = Math.min(1, (i + slice.length) / segments.length);
    onProgress?.(done);
  }
  return output;
}

async function synthesizeSpeech(text, voice, targetSeconds, outPath, cfg) {
  const target = Math.max(0.45, Number(targetSeconds || 1));
  const roughWordsPerSecond = 2.55;
  const words = String(text).trim().split(/\s+/).filter(Boolean).length;
  const estimatedSeconds = Math.max(0.6, words / roughWordsPerSecond);
  const speed = clamp(estimatedSeconds / target, 0.72, 1.35);

  const response = await openAiRequest(`${cfg.openAiBase}/audio/speech`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      model: cfg.ttsModel,
      voice,
      input: String(text).slice(0, 4096),
      response_format: "mp3",
      speed,
      instructions: `Doğal ve anlaşılır Türkçe dublaj yap. Yaklaşık ${target.toFixed(1)} saniyeye sığdır. Konuşmacının kimliğini veya gerçek sesini taklit etme; nötr bir yapay ses kullan.`
    })
  }, cfg.apiKey);

  const buffer = Buffer.from(await response.arrayBuffer());
  await fs.writeFile(outPath, buffer);
}

async function probeDuration(filePath, runProcess) {
  const result = await runProcess("ffprobe", [
    "-v", "error",
    "-show_entries", "format=duration",
    "-of", "default=noprint_wrappers=1:nokey=1",
    filePath
  ], { timeoutMs: 30_000 });
  const duration = Number(result.stdout.trim());
  return Number.isFinite(duration) ? duration : 0;
}

function writeSrt(segments) {
  return segments.map((s, index) => [
    index + 1,
    `${srtTime(s.start)} --> ${srtTime(s.end)}`,
    s.translated,
    ""
  ].join("\n")).join("\n");
}

export function createDubRouter(options) {
  const {
    assertPublicUrl,
    runProcess,
    tempRoot,
    ytdlpBin,
    maxFilesize,
    safeFilename,
    processTimeoutMs
  } = options;

  const router = express.Router();
  const jobs = new Map();
  const uploads = new Map();
  const active = new Set();

  const cfg = {
    apiKey: String(process.env.OPENAI_API_KEY || "").trim(),
    openAiBase: String(process.env.OPENAI_BASE_URL || DEFAULT_OPENAI_BASE).replace(/\/+$/, ""),
    transcribeModel: String(process.env.DUB_TRANSCRIBE_MODEL || "gpt-4o-transcribe-diarize"),
    translateModel: String(process.env.DUB_TRANSLATE_MODEL || "gpt-5.6-luna"),
    ttsModel: String(process.env.DUB_TTS_MODEL || "gpt-4o-mini-tts"),
    maxJobs: Math.max(1, Number(process.env.DUB_MAX_JOBS || 1)),
    maxDurationSeconds: Math.max(30, Number(process.env.DUB_MAX_DURATION_SECONDS || 1800)),
    maxSegments: Math.max(20, Number(process.env.DUB_MAX_SEGMENTS || 220)),
    maxUploadBytes: Math.max(16 * 1024 * 1024, Number(process.env.DUB_MAX_UPLOAD_MB || 256) * 1024 * 1024),
    uploadChunkBytes: Math.max(1 * 1024 * 1024, Math.min(16 * 1024 * 1024, Number(process.env.DUB_UPLOAD_CHUNK_MB || 8) * 1024 * 1024)),
    maxPendingUploads: Math.max(1, Number(process.env.DUB_MAX_PENDING_UPLOADS || 2)),
    originalVolume: clamp(process.env.DUB_ORIGINAL_VOLUME || 0.55, 0.05, 1),
    processTimeoutMs: Math.max(processTimeoutMs, Number(process.env.DUB_PROCESS_TIMEOUT_MS || 45 * 60_000))
  };

  function update(job, patch) {
    Object.assign(job, patch, { updatedAt: Date.now() });
  }

  async function cleanupJob(job) {
    if (job?.tempDir) {
      await fs.rm(job.tempDir, { recursive: true, force: true }).catch(() => {});
    }
  }

  function normalizeUploadName(value) {
    const base = path.basename(String(value || "video.mp4")).replace(/[\x00-\x1f]/g, "").trim();
    return (base || "video.mp4").slice(0, 180);
  }

  function uploadExtension(name) {
    const ext = path.extname(String(name || "")).toLowerCase();
    const allowed = new Set([".mp4", ".mov", ".webm", ".mkv", ".m4v"]);
    if (!allowed.has(ext)) {
      throw new Error("Desteklenen video türleri: MP4, MOV, WebM, MKV ve M4V.");
    }
    return ext;
  }

  async function inspectUploadedVideo(filePath) {
    const result = await runProcess("ffprobe", [
      "-v", "error",
      "-show_entries", "stream=codec_type",
      "-of", "csv=p=0",
      filePath
    ], { timeoutMs: 45_000 });
    const types = result.stdout.split(/\r?\n/).map((v) => v.trim()).filter(Boolean);
    if (!types.includes("video")) throw new Error("Yüklenen dosyada geçerli bir video akışı bulunamadı.");
    if (!types.includes("audio")) throw new Error("Yüklenen videoda dublaj yapılabilecek bir ses parçası bulunamadı.");
  }

  async function assembleUpload(upload) {
    const ext = uploadExtension(upload.name);
    const sourcePath = path.join(upload.tempDir, `source-upload${ext}`);
    const handle = await fs.open(sourcePath, "w");
    try {
      for (let i = 0; i < upload.expectedChunks; i += 1) {
        const partPath = path.join(upload.tempDir, `chunk-${String(i).padStart(5, "0")}.part`);
        const part = await fs.readFile(partPath);
        await handle.write(part);
      }
    } finally {
      await handle.close();
    }
    const stat = await fs.stat(sourcePath);
    if (stat.size !== upload.size) {
      throw new Error(`Video yüklemesi eksik veya bozuk (${stat.size}/${upload.size} bayt).`);
    }
    await inspectUploadedVideo(sourcePath);
    const names = await fs.readdir(upload.tempDir);
    await Promise.all(names.filter((name) => name.endsWith(".part")).map((name) => fs.rm(path.join(upload.tempDir, name), { force: true })));
    return sourcePath;
  }

  async function runJob(job) {
    active.add(job.id);
    try {
      update(job, { status: "running", progress: 5, message: "Video hazırlanıyor…" });
      let sourcePath = job.sourcePath || "";

      if (job.sourceKind !== "upload") {
        const sourceTemplate = path.join(job.tempDir, "source.%(ext)s");
        await runProcess(ytdlpBin, [
          "--no-playlist",
          "--no-cache-dir",
          "--socket-timeout", "20",
          "--retries", "2",
          "--fragment-retries", "2",
          "--max-filesize", maxFilesize,
          "--merge-output-format", "mp4",
          "-f", `${job.formatId}+bestaudio/best`,
          "-o", sourceTemplate,
          job.url
        ], { cwd: job.tempDir, timeoutMs: cfg.processTimeoutMs });

        const names = await fs.readdir(job.tempDir);
        const sourceName = names.find((name) => name.startsWith("source.") && !name.endsWith(".part") && !name.endsWith(".ytdl"));
        if (!sourceName) throw new Error("Kaynak video oluşturulamadı.");
        sourcePath = path.join(job.tempDir, sourceName);
      } else if (!sourcePath) {
        throw new Error("Yüklenen kaynak video bulunamadı.");
      }

      const duration = await probeDuration(sourcePath, runProcess);
      if (!duration) throw new Error("Video süresi okunamadı.");
      if (duration > cfg.maxDurationSeconds) {
        throw new Error(`Türkçe dublaj için video en fazla ${Math.round(cfg.maxDurationSeconds / 60)} dakika olabilir.`);
      }

      update(job, { progress: 14, message: "Konuşma sesi çıkarılıyor…" });
      const audioPath = path.join(job.tempDir, "speech-source.mp3");
      await runProcess("ffmpeg", [
        "-y", "-i", sourcePath,
        "-vn", "-ac", "1", "-ar", "16000", "-b:a", "64k",
        audioPath
      ], { cwd: job.tempDir, timeoutMs: 10 * 60_000 });

      update(job, { progress: 24, message: "Konuşmacılar ve cümleler algılanıyor…" });
      let segments = await transcribeDiarized(audioPath, cfg);
      if (segments.length > cfg.maxSegments) {
        throw new Error(`Bu videoda çok fazla konuşma parçası var (${segments.length}). Sınır ${cfg.maxSegments}.`);
      }
      job.speakers = [...new Set(segments.map((s) => s.speaker))].length;

      update(job, { progress: 38, message: "Konuşmalar Türkçeye çevriliyor…" });
      segments = await translateSegments(segments, cfg, (ratio) => {
        update(job, {
          progress: 38 + Math.round(ratio * 17),
          message: "Konuşmalar Türkçeye çevriliyor…"
        });
      });

      const subtitlePath = job.subtitles ? path.join(job.tempDir, "turkish.srt") : null;
      if (subtitlePath) await fs.writeFile(subtitlePath, writeSrt(segments), "utf8");

      update(job, { progress: 58, message: "Türkçe yapay sesler oluşturuluyor…" });
      const total = segments.length;
      let synthCount = 0;

      // Generate TTS ourselves first so progress can be reported, then perform a final FFmpeg mix.
      const prepared = [];
      for (let i = 0; i < total; i += 1) {
        const segment = segments[i];
        const rawPath = path.join(job.tempDir, `tts-${String(i).padStart(4, "0")}.mp3`);
        await synthesizeSpeech(segment.translated, voiceForSpeaker(segment.speaker), segment.end - segment.start, rawPath, cfg);
        const actual = await probeDuration(rawPath, runProcess);
        const target = Math.max(0.45, segment.end - segment.start);
        prepared.push({ ...segment, rawPath, tempo: actual > 0 ? clamp(actual / target, 0.25, 4) : 1 });
        synthCount += 1;
        update(job, {
          progress: 58 + Math.round((synthCount / total) * 27),
          message: `Türkçe sesler oluşturuluyor… ${synthCount}/${total}`
        });
        if (i > 0 && i % 20 === 0) await sleep(120);
      }

      update(job, { progress: 88, message: "Dublaj videoya yerleştiriliyor…" });
      const outputPath = path.join(job.tempDir, "output-tr-dub.mp4");

      const filterLines = [];
      prepared.forEach((item, i) => {
        const startMs = Math.max(0, Math.round(item.start * 1000));
        filterLines.push(`[${i + 1}:a]aresample=48000,${atempoChain(item.tempo)},adelay=${startMs}|${startMs},volume=1.15[d${i}]`);
      });
      const dubInputs = prepared.map((_, i) => `[d${i}]`).join("");
      filterLines.push(`${dubInputs}amix=inputs=${prepared.length}:duration=longest:normalize=0[dub]`);
      filterLines.push(`[0:a]aresample=48000,volume=${cfg.originalVolume.toFixed(2)}[orig]`);
      filterLines.push(`[orig][dub]sidechaincompress=threshold=0.025:ratio=10:attack=8:release=250[ducked]`);
      filterLines.push(`[ducked][dub]amix=inputs=2:duration=first:normalize=0,alimiter=limit=0.95[aout]`);
      const scriptPath = path.join(job.tempDir, "dub-filter.txt");
      await fs.writeFile(scriptPath, filterLines.join(";\n"), "utf8");

      const args = ["-y", "-i", sourcePath];
      prepared.forEach((item) => args.push("-i", item.rawPath));
      if (subtitlePath) args.push("-i", subtitlePath);
      args.push(
        "-filter_complex_script", scriptPath,
        "-map", "0:v:0",
        "-map", "[aout]"
      );
      if (job.sourceKind === "upload") {
        args.push("-c:v", "libx264", "-preset", "veryfast", "-crf", "20", "-pix_fmt", "yuv420p");
      } else {
        args.push("-c:v", "copy");
      }
      args.push(
        "-c:a", "aac",
        "-b:a", "192k",
        "-movflags", "+faststart"
      );
      if (subtitlePath) {
        const subtitleInput = prepared.length + 1;
        args.push("-map", `${subtitleInput}:0`, "-c:s", "mov_text", "-metadata:s:s:0", "language=tur");
      }
      args.push(outputPath);
      await runProcess("ffmpeg", args, { cwd: job.tempDir, timeoutMs: cfg.processTimeoutMs });

      job.outputPath = outputPath;
      update(job, {
        status: "done",
        progress: 100,
        message: "Türkçe dublaj hazır.",
        completedAt: Date.now()
      });
    } catch (error) {
      update(job, {
        status: "error",
        progress: 100,
        message: String(error?.message || error).slice(0, 1200)
      });
    } finally {
      active.delete(job.id);
    }
  }

  router.get("/health", (_req, res) => {
    res.json({
      ok: true,
      configured: Boolean(cfg.apiKey),
      activeJobs: active.size,
      maxJobs: cfg.maxJobs,
      transcribeModel: cfg.transcribeModel,
      translateModel: cfg.translateModel,
      ttsModel: cfg.ttsModel,
      voiceMode: "automatic-speaker-profile",
      uploadMode: "chunked",
      maxUploadMb: Math.round(cfg.maxUploadBytes / 1024 / 1024),
      uploadChunkMb: Math.round(cfg.uploadChunkBytes / 1024 / 1024),
      pendingUploads: uploads.size
    });
  });

  router.post("/jobs", async (req, res) => {
    try {
      if (!cfg.apiKey) {
        return res.status(503).json({ error: "Türkçe dublaj için OPENAI_API_KEY sunucuda ayarlanmamış." });
      }
      if (active.size >= cfg.maxJobs) {
        return res.status(503).json({ error: "Dublaj sunucusu şu anda dolu. Biraz sonra tekrar deneyin." });
      }

      const url = await assertPublicUrl(req.body?.url);
      const formatId = String(req.body?.formatId || "").trim();
      if (!/^[A-Za-z0-9._:+-]{1,80}$/.test(formatId)) throw new Error("Geçersiz kalite seçimi.");

      const jobId = crypto.randomUUID();
      const tempDir = await fs.mkdtemp(path.join(tempRoot, "dub-"));
      const job = {
        id: jobId,
        sourceKind: "url",
        sourcePath: null,
        url,
        formatId,
        title: safeFilename(req.body?.title || "video"),
        subtitles: req.body?.subtitles !== false,
        tempDir,
        outputPath: null,
        speakers: 0,
        status: "queued",
        progress: 1,
        message: "Dublaj kuyruğa alındı.",
        createdAt: Date.now(),
        updatedAt: Date.now()
      };
      jobs.set(jobId, job);
      runJob(job);
      return res.status(202).json({ id: jobId, status: job.status, progress: job.progress, message: job.message });
    } catch (error) {
      return res.status(400).json({ error: String(error?.message || error).slice(0, 1200) });
    }
  });

  router.post("/uploads/init", async (req, res) => {
    let tempDir = null;
    try {
      if (!cfg.apiKey) {
        return res.status(503).json({ error: "Türkçe dublaj için OPENAI_API_KEY sunucuda ayarlanmamış." });
      }
      if (active.size >= cfg.maxJobs) {
        return res.status(503).json({ error: "Dublaj sunucusu şu anda dolu. Biraz sonra tekrar deneyin." });
      }
      if (uploads.size >= cfg.maxPendingUploads) {
        return res.status(503).json({ error: "Video yükleme kuyruğu dolu. Biraz sonra tekrar deneyin." });
      }

      const name = normalizeUploadName(req.body?.name);
      uploadExtension(name);
      const size = Number(req.body?.size || 0);
      const mime = String(req.body?.type || "").slice(0, 120);
      if (!Number.isSafeInteger(size) || size <= 0) throw new Error("Video dosya boyutu geçersiz.");
      if (size > cfg.maxUploadBytes) {
        throw new Error(`Video en fazla ${Math.round(cfg.maxUploadBytes / 1024 / 1024)} MB olabilir.`);
      }
      if (mime && !mime.startsWith("video/") && mime !== "application/octet-stream") {
        throw new Error("Seçilen dosya geçerli bir video türü değil.");
      }

      const id = crypto.randomUUID();
      tempDir = await fs.mkdtemp(path.join(tempRoot, "upload-"));
      const expectedChunks = Math.ceil(size / cfg.uploadChunkBytes);
      uploads.set(id, {
        id, name, size, mime, tempDir, expectedChunks, received: new Set(),
        createdAt: Date.now(), updatedAt: Date.now()
      });
      return res.status(201).json({
        id, chunkSize: cfg.uploadChunkBytes, expectedChunks, maxUploadBytes: cfg.maxUploadBytes
      });
    } catch (error) {
      if (tempDir) await fs.rm(tempDir, { recursive: true, force: true }).catch(() => {});
      return res.status(400).json({ error: String(error?.message || error).slice(0, 1200) });
    }
  });

  const chunkParser = express.raw({ type: "application/octet-stream", limit: cfg.uploadChunkBytes + 1024 });

  router.post("/uploads/:id/chunks/:index", chunkParser, async (req, res) => {
    try {
      const upload = uploads.get(String(req.params.id));
      if (!upload) return res.status(404).json({ error: "Video yükleme oturumu bulunamadı veya süresi doldu." });
      const index = Number(req.params.index);
      if (!Number.isInteger(index) || index < 0 || index >= upload.expectedChunks) {
        throw new Error("Geçersiz video parçası numarası.");
      }
      if (!Buffer.isBuffer(req.body)) throw new Error("Video parçası alınamadı.");
      const remaining = upload.size - index * cfg.uploadChunkBytes;
      const expectedBytes = Math.min(cfg.uploadChunkBytes, remaining);
      if (req.body.length !== expectedBytes) {
        throw new Error(`Video parçası eksik (${req.body.length}/${expectedBytes} bayt).`);
      }
      const partPath = path.join(upload.tempDir, `chunk-${String(index).padStart(5, "0")}.part`);
      await fs.writeFile(partPath, req.body);
      upload.received.add(index);
      upload.updatedAt = Date.now();
      return res.json({ ok: true, index, receivedChunks: upload.received.size, expectedChunks: upload.expectedChunks });
    } catch (error) {
      return res.status(400).json({ error: String(error?.message || error).slice(0, 1200) });
    }
  });

  router.post("/uploads/:id/complete", async (req, res) => {
    try {
      if (active.size >= cfg.maxJobs) {
        return res.status(503).json({ error: "Dublaj sunucusu şu anda dolu. Yükleme hazır; biraz sonra tekrar deneyin." });
      }
      const upload = uploads.get(String(req.params.id));
      if (!upload) return res.status(404).json({ error: "Video yükleme oturumu bulunamadı veya süresi doldu." });
      if (upload.received.size !== upload.expectedChunks) {
        throw new Error(`Video yüklemesi tamamlanmadı (${upload.received.size}/${upload.expectedChunks} parça).`);
      }

      const sourcePath = await assembleUpload(upload);
      const jobId = crypto.randomUUID();
      const job = {
        id: jobId,
        sourceKind: "upload",
        sourcePath,
        url: null,
        formatId: null,
        title: safeFilename(path.parse(upload.name).name || "video"),
        subtitles: req.body?.subtitles !== false,
        tempDir: upload.tempDir,
        outputPath: null,
        speakers: 0,
        status: "queued",
        progress: 1,
        message: "Yüklenen video dublaj kuyruğuna alındı.",
        createdAt: Date.now(),
        updatedAt: Date.now()
      };
      uploads.delete(upload.id);
      jobs.set(jobId, job);
      runJob(job);
      return res.status(202).json({ id: jobId, status: job.status, progress: job.progress, message: job.message });
    } catch (error) {
      return res.status(400).json({ error: String(error?.message || error).slice(0, 1200) });
    }
  });

  router.get("/jobs/:id", (req, res) => {
    const job = jobs.get(String(req.params.id));
    if (!job) return res.status(404).json({ error: "Dublaj işi bulunamadı veya süresi doldu." });
    return res.json({
      id: job.id,
      status: job.status,
      progress: job.progress,
      message: job.message,
      speakers: job.speakers,
      ready: job.status === "done",
      downloadUrl: job.status === "done" ? `/api/dub/jobs/${job.id}/download` : null
    });
  });

  router.get("/jobs/:id/download", async (req, res) => {
    const job = jobs.get(String(req.params.id));
    if (!job || job.status !== "done" || !job.outputPath) {
      return res.status(404).json({ error: "Dublaj dosyası henüz hazır değil." });
    }
    const outName = `${safeFilename(job.title)}_TR_Dublaj.mp4`;
    return res.download(job.outputPath, outName);
  });

  setInterval(async () => {
    const cutoff = Date.now() - 2 * 60 * 60_000;
    const uploadCutoff = Date.now() - 60 * 60_000;
    for (const [id, job] of jobs) {
      if (!active.has(id) && job.updatedAt < cutoff) {
        jobs.delete(id);
        await cleanupJob(job);
      }
    }
    for (const [id, upload] of uploads) {
      if (upload.updatedAt < uploadCutoff) {
        uploads.delete(id);
        await fs.rm(upload.tempDir, { recursive: true, force: true }).catch(() => {});
      }
    }
  }, 15 * 60_000).unref();

  return router;
}
