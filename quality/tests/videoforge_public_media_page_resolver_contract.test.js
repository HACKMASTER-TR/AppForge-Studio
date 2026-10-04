import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const resolver = fs.readFileSync(
  new URL(
    "../../android-app/app/src/main/java/com/hackmaster/videoforge/PublicMediaPageResolver.kt",
    import.meta.url
  ),
  "utf8"
);

const importer = fs.readFileSync(
  new URL(
    "../../android-app/app/src/main/java/com/hackmaster/videoforge/UrlVideoImporter.kt",
    import.meta.url
  ),
  "utf8"
);

test("public media resolver runs before adaptive and direct download", () => {
  const resolveIndex =
    importer.indexOf(
      "PublicMediaPageResolver.resolve"
    );

  const adaptiveIndex =
    importer.indexOf(
      "AdaptiveStreamDownloader.downloadIfAdaptive"
    );

  assert.ok(resolveIndex >= 0);
  assert.ok(adaptiveIndex > resolveIndex);

  assert.match(
    importer,
    /address\s*=\s*resolvedAddress/
  );

  assert.match(
    importer,
    /download\(\s*context,\s*resolvedAddress/
  );
});

test("resolver parses explicit video and source elements", () => {
  assert.ok(
    resolver.includes(
      "(?:video|source)"
    )
  );

  assert.match(
    resolver,
    /attrs\["src"\]/
  );

  assert.match(
    resolver,
    /attrs\["data-src"\]/
  );
});

test("resolver reads public video metadata declarations", () => {
  assert.match(
    resolver,
    /og:video/
  );

  assert.match(
    resolver,
    /og:video:url/
  );

  assert.match(
    resolver,
    /og:video:secure_url/
  );

  assert.match(
    resolver,
    /twitter:player:stream/
  );

  assert.match(
    resolver,
    /contenturl/
  );
});

test("resolver recognizes progressive HLS and DASH addresses", () => {
  for (const extension of [
    ".mp4",
    ".webm",
    ".mov",
    ".mkv",
    ".m4v",
    ".3gp",
    ".m3u8",
    ".mpd"
  ]) {
    assert.ok(
      resolver.includes(
        extension
      ),
      `missing ${extension}`
    );
  }

  assert.match(
    resolver,
    /mpegurl/
  );

  assert.match(
    resolver,
    /application\/dash\+xml/
  );
});

test("resolver accepts only bounded public HTTP HTTPS candidates", () => {
  assert.match(
    resolver,
    /PROBE_LIMIT/
  );

  assert.match(
    resolver,
    /HTML_LIMIT/
  );

  assert.match(
    resolver,
    /MAX_CANDIDATES/
  );

  assert.match(
    resolver,
    /data:/
  );

  assert.match(
    resolver,
    /blob:/
  );

  assert.match(
    resolver,
    /javascript:/
  );

  assert.match(
    resolver,
    /scheme\.equals\(\s*"https"/
  );
});

test("resolver does not execute page script or add provider bypass", () => {
  for (const forbidden of [
    /WebView/,
    /evaluateJavascript/,
    /javascriptEnabled/,
    /yt-dlp/i,
    /signatureCipher/i,
    /youtube.*cipher/i,
    /instagram.*extract/i,
    /tiktok.*extract/i
  ]) {
    assert.doesNotMatch(
      resolver,
      forbidden
    );
  }
});
