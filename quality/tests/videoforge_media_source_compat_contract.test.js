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
    /videoforge-media-source-v2/
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
    /part\.length\(\) != sourceSize/
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
