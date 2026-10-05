import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const read = path =>
  fs.readFileSync(
    new URL("../../" + path, import.meta.url),
    "utf8"
  );

const compat = read(
  "android-app/app/src/main/java/com/hackmaster/videoforge/MediaSourceCompat.kt"
);
const audio = read(
  "android-app/app/src/main/java/com/hackmaster/videoforge/AudioMedia.kt"
);
const storage = read(
  "android-app/app/src/main/java/com/hackmaster/videoforge/StorageGuard.kt"
);
const video = read(
  "android-app/app/src/main/java/com/hackmaster/videoforge/VideoForgeActivity.kt"
);
const engine = read(
  "android-app/app/src/main/java/com/hackmaster/videoforge/OfflineDubEngine.kt"
);

test("VideoForge keeps direct content URI and descriptor compatibility paths", () => {
  assert.match(compat, /direct\.setDataSource\(\s*context,\s*uri/);
  assert.match(compat, /openAssetFileDescriptor/);
  assert.match(compat, /afd\.fileDescriptor/);
  assert.match(compat, /afd\.startOffset/);
});

test("file URI processing uses an absolute local path before provider APIs", () => {
  assert.match(compat, /private fun localFile/);
  assert.match(
    compat,
    /localFile\(\s*uri\s*\)[\s\S]*MediaExtractor\(\)[\s\S]*file\.absolutePath/
  );
  assert.match(
    compat,
    /localFile\(\s*uri\s*\)[\s\S]*MediaMetadataRetriever\(\)[\s\S]*file\.absolutePath/
  );
});

test("processing preflight uses the real MediaExtractor parser", () => {
  assert.match(compat, /VIDEOFORGE_EXTRACTOR_PREFLIGHT_V1_2/);
  assert.match(compat, /fun probeForProcessing/);
  assert.match(compat, /MediaFormat\.KEY_MIME/);
  assert.match(compat, /MediaFormat\.KEY_DURATION/);
  assert.match(compat, /Videoda ses parçası bulunamadı/);
  assert.match(compat, /Videoda görüntü parçası bulunamadı/);
});

test("StorageGuard no longer hard-depends on MediaMetadataRetriever", () => {
  assert.match(storage, /MediaSourceCompat\.probeForProcessing/);
  assert.match(storage, /requireEnoughForDuration/);
  assert.doesNotMatch(storage, /MediaMetadataRetriever|openRetriever/);
});

test("single-video processing materializes one stable local source before FGS", () => {
  assert.match(video, /VIDEOFORGE_STABLE_LOCAL_PROCESSING_V1_2/);
  assert.match(
    video,
    /materializeForProcessing[\s\S]*StorageGuard\.requireEnough[\s\S]*ContextCompat\.startForegroundService/
  );
});

test("local copy is atomic and byte/hash verified", () => {
  assert.match(compat, /VIDEOFORGE_LOCAL_COPY_INTEGRITY_V1_2/);
  assert.match(compat, /\.part/);
  assert.match(compat, /copiedBytes/);
  assert.match(compat, /sourceStreamHash/);
  assert.match(compat, /localHash/);
  assert.match(compat, /sha256\(\s*target\s*\)/);
  assert.match(compat, /renameTo/);
});

test("decode and mux stay on MediaExtractor while rotation is best-effort metadata", () => {
  assert.match(audio, /MediaSourceCompat\.openExtractor/);
  assert.match(audio, /VIDEOFORGE_MUX_ROTATION_WITHOUT_RETRIEVER_V1_2/);
  assert.match(audio, /MediaFormat\.KEY_ROTATION/);
  assert.doesNotMatch(audio, /MediaSourceCompat\.openRetriever|MediaMetadataRetriever/);
});

test("decoded duration gets an exact second storage check", () => {
  assert.match(engine, /VIDEOFORGE_EXACT_DURATION_STORAGE_V1_2/);
  assert.match(engine, /requireEnoughForDuration/);
  assert.match(engine, /decodedRaw\.durationSeconds/);
});

test("stable processing source remains app-private and cache-bounded", () => {
  assert.match(compat, /videoforge-media-source-v3/);
  assert.match(compat, /context\.cacheDir/);
  assert.match(compat, /CACHE_MAX_AGE_MS/);
  assert.match(compat, /cleanupOldCopies/);
  assert.match(compat, /fun materializeForProcessing/);
  assert.match(compat, /Uri\.fromFile/);
});

test("media-open errors preserve a bounded lower-level reason", () => {
  assert.match(compat, /safeMediaError/);
  assert.match(compat, /Alt neden:/);
});
