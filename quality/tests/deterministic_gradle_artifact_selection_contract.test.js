import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const selector = await readFile(
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/build/GradleArtifactSelector.kt",
    import.meta.url
  ),
  "utf8"
);

const engine = await readFile(
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt",
    import.meta.url
  ),
  "utf8"
);

function requireMarkers(
  source,
  markers
) {
  for (
    const marker of markers
  ) {
    assert.ok(
      source.includes(
        marker
      ),
      `missing marker: ${marker}`
    );
  }
}

test(
  "Gradle artifact selection is variant scoped instead of project mtime based",
  () => {
    requireMarkers(
      selector,
      [
        "APPFORGE_DETERMINISTIC_GRADLE_ARTIFACT_SELECTION_V22",
        "app/build/outputs/apk",
        "app/build/outputs/bundle",
        "\"output-metadata.json\"",
        "\"variantName\"",
        "\"outputFile\"",
        "normalizeVariant"
      ]
    );

    assert.equal(
      selector.includes(
        "lastModified"
      ),
      false
    );
  }
);

test(
  "APK selector uses AGP metadata and fails closed on equivalent outputs",
  () => {
    requireMarkers(
      selector,
      [
        "fun selectApk(",
        "\"elements\"",
        "\"filters\"",
        "filterCount",
        "lowestFilterCount",
        "preferred.size",
        "Birden fazla eşdeğer APK çıktısı bulundu"
      ]
    );

    assert.ok(
      selector.includes(
        "preferred.size =="
      )
    );

    assert.ok(
      selector.includes(
        "isInside("
      )
    );
  }
);

test(
  "AAB selector is limited to exact app bundle variant output",
  () => {
    requireMarkers(
      selector,
      [
        "fun selectAab(",
        "app/build/outputs/bundle",
        "variantDirectoryName",
        "\"aab\"",
        "Birden fazla AAB çıktısı bulundu"
      ]
    );
  }
);

test(
  "DeviceBuildEngine copies only selector approved APK and AAB",
  () => {
    const start =
      engine.indexOf(
        "APPFORGE_DETERMINISTIC_GRADLE_ARTIFACT_SELECTION_V22"
      );

    assert.notEqual(
      start,
      -1
    );

    const end =
      engine.indexOf(
        "private fun copyKeystore(",
        start
      );

    assert.notEqual(
      end,
      -1
    );

    const block =
      engine.slice(
        start,
        end
      );

    requireMarkers(
      block,
      [
        "GradleArtifactSelector",
        ".selectApk(",
        ".selectAab(",
        "Deterministik APK",
        "Deterministik AAB",
        "DeviceArtifactKind.APK",
        "DeviceArtifactKind.AAB"
      ]
    );

    assert.equal(
      block.includes(
        "project.walkTopDown()"
      ),
      false
    );

    assert.equal(
      block.includes(
        "maxByOrNull { it.lastModified() }"
      ),
      false
    );

    assert.equal(
      block.includes(
        "artifactFiles.filter"
      ),
      false
    );
  }
);

test(
  "ambiguous artifacts fail closed instead of choosing newest file",
  () => {
    requireMarkers(
      selector,
      [
        "preferred.size",
        "candidates.size",
        "error(",
        "yanlış artifact seçmemek için build durduruldu"
      ]
    );

    assert.equal(
      selector.includes(
        "maxByOrNull { it.lastModified() }"
      ),
      false
    );
  }
);
