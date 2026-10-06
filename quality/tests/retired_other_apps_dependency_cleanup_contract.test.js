import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const build =
  await readFile(
    new URL(
      "../../android-app/app/build.gradle.kts",
      import.meta.url
    ),
    "utf8"
  );

const rootfs =
  await readFile(
    new URL(
      "../../android-app/app/src/main/java/com/appforge/studio/terminal/VerifiedLinuxRootfsInstaller.kt",
      import.meta.url
    ),
    "utf8"
  );

test(
  "Terminal rootfs keeps its shared commons-compress dependency",
  () => {
    assert.match(
      rootfs,
      /org\.apache\.commons\.compress\.archivers\.tar\.TarArchiveEntry/
    );

    assert.match(
      rootfs,
      /org\.apache\.commons\.compress\.archivers\.tar\.TarArchiveInputStream/
    );

    assert.match(
      rootfs,
      /org\.apache\.commons\.compress\.compressors\.gzip\.GzipCompressorInputStream/
    );

    assert.match(
      build,
      /org\.apache\.commons:commons-compress:1\.27\.1/
    );
  }
);

test(
  "retired VideoForge dependencies stay absent",
  () => {
    assert.doesNotMatch(
      build,
      /androidx\.appcompat:appcompat/
    );

    assert.doesNotMatch(
      build,
      /androidx\.media3:/
    );

    assert.doesNotMatch(
      build,
      /com\.google\.mlkit:(language-id|translate)/
    );

    assert.doesNotMatch(
      build,
      /sherpa-onnx/
    );
  }
);
