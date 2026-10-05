import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const compat = fs.readFileSync(
  new URL(
    "../../android-app/app/src/main/java/com/hackmaster/videoforge/MediaSourceCompat.kt",
    import.meta.url
  ),
  "utf8"
);

const audio = fs.readFileSync(
  new URL(
    "../../android-app/app/src/main/java/com/hackmaster/videoforge/AudioMedia.kt",
    import.meta.url
  ),
  "utf8"
);

const storage = fs.readFileSync(
  new URL(
    "../../android-app/app/src/main/java/com/hackmaster/videoforge/StorageGuard.kt",
    import.meta.url
  ),
  "utf8"
);

test("VideoForge keeps normal content URI opening first", () => {
  assert.match(
    compat,
    /direct\.setDataSource\(\s*context,\s*uri/
  );
});

test("VideoForge keeps descriptor fallback second", () => {
  assert.match(
    compat,
    /openAssetFileDescriptor/
  );

  assert.match(
    compat,
    /afd\.fileDescriptor/
  );

  assert.match(
    compat,
    /afd\.startOffset/
  );
});

test("VideoForge adds app-private local media copy as final fallback", () => {
  assert.match(
    compat,
    /context\.cacheDir/
  );

  assert.match(
    compat,
    /videoforge-media-source-v3/
  );

  assert.match(
    compat,
    /openInputStream/
  );

  assert.match(
    compat,
    /materializeLocalCopy/
  );
});

test("local copy is written atomically and validated", () => {
  assert.match(
    compat,
    /\.part/
  );

  assert.match(
    compat,
    /renameTo/
  );

  assert.match(
    compat,
    /!part\.isFile[\s\S]*part\.length\(\)\s*<=\s*0L/
  );

  assert.match(
    compat,
    /!target\.isFile[\s\S]*target\.length\(\)\s*<=\s*0L/
  );

  /*
   * Provider OpenableColumns.SIZE is advisory in V1.1.
   * Atomic copy validity is proven by a non-empty part and
   * final non-empty target, not strict metadata equality.
   */
  assert.doesNotMatch(
    compat,
    /part\.length\(\)\s*!=\s*sourceSize/
  );
});

test("MediaExtractor can use absolute local fallback path", () => {
  assert.match(
    compat,
    /local\.setDataSource\(\s*file\.absolutePath\s*\)/
  );
});

test("MediaMetadataRetriever can use absolute local fallback path", () => {
  const count =
    (
      compat.match(
        /local\.setDataSource\(\s*file\.absolutePath\s*\)/g
      ) || []
    ).length;

  assert.equal(
    count,
    2
  );
});

test("all VideoForge media consumers remain on shared compatibility layer", () => {
  assert.match(
    audio,
    /MediaSourceCompat\.openExtractor/
  );

  assert.match(
    audio,
    /MediaSourceCompat\.openRetriever/
  );

  assert.match(
    storage,
    /MediaSourceCompat\.openRetriever/
  );
});

test("old cache copies are bounded by cleanup policy", () => {
  assert.match(
    compat,
    /CACHE_MAX_AGE_MS/
  );

  assert.match(
    compat,
    /cleanupOldCopies/
  );
});

test("provider SIZE metadata no longer rejects a valid local copy", () => {
  assert.match(
    compat,
    /VIDEOFORGE_MEDIA_PROVIDER_SIZE_TOLERANCE_V1_1/
  );

  assert.doesNotMatch(
    compat,
    /yerel kopyası eksik\. Beklenen=/
  );
});

test("VideoForge can explicitly materialize a stable processing source", () => {
  assert.match(
    compat,
    /VIDEOFORGE_STABLE_LOCAL_SOURCE_V1_1/
  );

  assert.match(
    compat,
    /fun materializeForProcessing/
  );

  assert.match(
    compat,
    /Uri\.fromFile/
  );
});

test("media-open failure now preserves a bounded lower-level reason", () => {
  assert.match(
    compat,
    /safeMediaError/
  );

  assert.match(
    compat,
    /Alt neden:/
  );
});
