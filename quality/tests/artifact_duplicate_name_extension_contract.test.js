import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const source = await readFile(
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
    import.meta.url
  ),
  "utf8"
);

function section(startMarker, endMarker) {
  const start = source.indexOf(startMarker);

  assert.notEqual(
    start,
    -1,
    `missing start marker: ${startMarker}`
  );

  const end = source.indexOf(
    endMarker,
    start + startMarker.length
  );

  assert.notEqual(
    end,
    -1,
    `missing end marker: ${endMarker}`
  );

  return source.slice(
    start,
    end
  );
}

test(
  "duplicate artifact suffix is inserted before extension",
  () => {
    const resolver = section(
      "private fun uniqueArtifactDownloadName(",
      "private fun downloadArtifactToDownloads("
    );

    assert.match(
      source,
      /ARTIFACT_DUPLICATE_EXTENSION_ORDER_V21_7/
    );

    assert.match(
      resolver,
      /lastIndexOf\([\s\S]{0,80}'\.'[\s\S]{0,80}\)/
    );

    assert.match(
      resolver,
      /"\$stem \(\$index\)\$extension"/
    );

    assert.doesNotMatch(
      resolver,
      /"\$safeName \(\$index\)"/
    );
  }
);

test(
  "AAB and EXE public downloads resolve duplicate name before insert",
  () => {
    const block = section(
      "private fun downloadArtifactToDownloads(",
      "private fun downloadArtifactToUri("
    );

    assert.match(
      block,
      /val displayName =[\s\S]{0,400}uniqueArtifactDownloadName\([\s\S]{0,400}requestedFileName =[\s\S]{0,100}fileName/
    );

    assert.match(
      block,
      /DISPLAY_NAME,[\s\S]{0,160}displayName/
    );
  }
);

test(
  "APK public download uses same collision resolver",
  () => {
    const block = section(
      "private fun publishApkToDownloads(",
      "private fun installCachedApk("
    );

    assert.match(
      block,
      /val displayName =[\s\S]{0,400}uniqueArtifactDownloadName\([\s\S]{0,400}requestedFileName =[\s\S]{0,100}fileName/
    );

    assert.match(
      block,
      /DISPLAY_NAME,[\s\S]{0,240}displayName/
    );
  }
);

test(
  "all three requested artifact extensions remain supported",
  () => {
    for (
      const extension of [
        "apk",
        "aab",
        "exe"
      ]
    ) {
      assert.ok(
        source.includes(
          `"${extension}"`
        ),
        `missing ${extension} artifact route`
      );
    }
  }
);
