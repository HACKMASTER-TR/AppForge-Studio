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

const service = fs.readFileSync(
  new URL(
    "../../android-app/app/src/main/java/com/hackmaster/videoforge/DubForegroundService.kt",
    import.meta.url
  ),
  "utf8"
);

test("VideoForge retries content URIs through AssetFileDescriptor", () => {
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

  assert.match(
    compat,
    /afd\.declaredLength/
  );
});

test("extractor has direct URI path plus descriptor fallback", () => {
  assert.match(
    compat,
    /direct\.setDataSource\(\s*context,\s*uri,\s*null\s*\)/
  );

  assert.match(
    compat,
    /fallback\.setDataSource/
  );
});

test("metadata retriever has direct URI path plus descriptor fallback", () => {
  assert.match(
    compat,
    /direct\.setDataSource\(\s*context,\s*uri\s*\)/
  );

  assert.match(
    compat,
    /fun openRetriever/
  );
});

test("all VideoForge media reads use compatibility layer", () => {
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

  assert.doesNotMatch(
    audio,
    /setDataSource\(context,\s*(?:uri|inputUri)/
  );

  assert.doesNotMatch(
    storage,
    /setDataSource\(context,\s*uri/
  );
});

test("media source failures are converted into user-friendly errors", () => {
  assert.match(
    service,
    /setDataSource/
  );

  assert.match(
    service,
    /Video açılamadı/
  );
});
