import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const importer = fs.readFileSync(
  new URL(
    "../../android-app/app/src/main/java/com/hackmaster/videoforge/UrlVideoImporter.kt",
    import.meta.url
  ),
  "utf8"
);

const activity = fs.readFileSync(
  new URL(
    "../../android-app/app/src/main/java/com/hackmaster/videoforge/VideoForgeActivity.kt",
    import.meta.url
  ),
  "utf8"
);

test("VideoForge validated downloader rejects HTML, HLS, DASH and JSON payloads", () => {
  assert.match(importer, /<!doctype html/i);
  assert.match(importer, /#extm3u/i);
  assert.match(importer, /<mpd/i);
  assert.match(importer, /JSON\/metin/i);
});

test("validated download requires Android MediaExtractor video track using FD", () => {
  assert.match(importer, /FileInputStream/);

  assert.match(
    importer,
    /extractor\.setDataSource\(\s*input\.fd\s*\)/
  );

  assert.match(
    importer,
    /mime\.startsWith\(\s*"video\/"/
  );

  assert.match(
    importer,
    /videoTrackFound/
  );
});

test("validated downloads are published only after validation through MediaStore", () => {
  const validateIndex =
    importer.indexOf("validateVideoTrack");

  const publishIndex =
    importer.indexOf("fun saveValidatedToDownloads");

  assert.ok(validateIndex >= 0);
  assert.ok(publishIndex > validateIndex);

  assert.match(
    importer,
    /MediaStore\.Downloads\.EXTERNAL_CONTENT_URI/
  );

  assert.match(
    importer,
    /MediaStore\.MediaColumns\.IS_PENDING/
  );
});

test("VideoForge download button no longer uses blind DownloadManager Request", () => {
  assert.match(
    activity,
    /UrlVideoImporter\.downloadValidated/
  );

  assert.match(
    activity,
    /UrlVideoImporter\.saveValidatedToDownloads/
  );

  assert.doesNotMatch(
    activity,
    /DownloadManager\.Request/
  );

  assert.doesNotMatch(
    activity,
    /\.setMimeType\(\s*"video\/\*"/
  );
});

test("temporary validated file is removed after UI download flow", () => {
  assert.match(
    activity,
    /downloaded\s*\?\s*\.file\s*\?\s*\.delete\(\)/
  );
});
