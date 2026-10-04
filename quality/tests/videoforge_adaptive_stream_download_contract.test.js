import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const adaptive = fs.readFileSync(
  new URL(
    "../../android-app/app/src/main/java/com/hackmaster/videoforge/AdaptiveStreamDownloader.kt",
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

const gradle = fs.readFileSync(
  new URL(
    "../../android-app/app/build.gradle.kts",
    import.meta.url
  ),
  "utf8"
);

test("VideoForge includes pinned Media3 HLS DASH and Transformer modules", () => {
  assert.match(
    gradle,
    /androidx\.media3:media3-common:1\.11\.1/
  );

  assert.match(
    gradle,
    /androidx\.media3:media3-effect:1\.11\.1/
  );

  assert.match(
    gradle,
    /androidx\.media3:media3-transformer:1\.11\.1/
  );

  assert.match(
    gradle,
    /androidx\.media3:media3-exoplayer-hls:1\.11\.1/
  );

  assert.match(
    gradle,
    /androidx\.media3:media3-exoplayer-dash:1\.11\.1/
  );
});

test("adaptive resolver runs before progressive direct download", () => {
  const adaptiveIndex =
    importer.indexOf(
      "AdaptiveStreamDownloader.downloadIfAdaptive"
    );

  const directIndex =
    importer.indexOf(
      "val downloaded ="
    );

  assert.ok(
    adaptiveIndex >= 0
  );

  assert.ok(
    directIndex > adaptiveIndex
  );
});

test("HLS support is VOD-only and rejects encrypted playlists", () => {
  assert.match(
    adaptive,
    /#EXTM3U/i
  );

  assert.match(
    adaptive,
    /#EXT-X-ENDLIST/i
  );

  assert.match(
    adaptive,
    /#EXT-X-KEY:/i
  );

  assert.match(
    adaptive,
    /#EXT-X-SESSION-KEY:/i
  );

  assert.match(
    adaptive,
    /Canlı HLS akışları/
  );

  assert.match(
    adaptive,
    /Şifreli\/DRM korumalı HLS/
  );
});

test("DASH support is VOD-only and rejects ContentProtection", () => {
  assert.match(
    adaptive,
    /MimeTypes\.APPLICATION_MPD/
  );

  assert.match(
    adaptive,
    /type\\s\*=/
  );

  assert.match(
    adaptive,
    /contentprotection/i
  );

  assert.match(
    adaptive,
    /Canlı DASH akışları/
  );

  assert.match(
    adaptive,
    /Şifreli\/DRM korumalı DASH/
  );
});

test("adaptive streams are exported to MP4 by Transformer", () => {
  assert.match(
    adaptive,
    /Transformer\.Builder/
  );

  assert.match(
    adaptive,
    /EditedMediaItem\.Builder/
  );

  assert.match(
    adaptive,
    /transformer\.start\(\s*editedItem/
  );

  assert.doesNotMatch(
    adaptive,
    /transformer\.start\(\s*item\s*,/
  );

  assert.match(
    adaptive,
    /adaptive-\$\{System\.currentTimeMillis\(\)\}\.mp4/
  );
});

test("provider-page bypass downloaders are not introduced", () => {
  assert.doesNotMatch(
    adaptive,
    /yt-dlp/i
  );

  assert.doesNotMatch(
    adaptive,
    /youtube.*extract/i
  );

  assert.doesNotMatch(
    adaptive,
    /instagram.*extract/i
  );

  assert.doesNotMatch(
    adaptive,
    /tiktok.*extract/i
  );
});
