import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const api =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/build/BuildApiClient.kt",
    import.meta.url
  );

const main =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
    import.meta.url
  );

const library =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/io/ProjectLibrary.kt",
    import.meta.url
  );

test(
  "Studio Build API understands EXE artifacts",
  async () => {
    const text =
      await readFile(
        api,
        "utf8"
      );

    assert.equal(
      text.includes(
        "exeAvailable"
      ),
      true
    );

    assert.equal(
      text.includes(
        '"exe" -> "exe"'
      ),
      true
    );
  }
);

test(
  "Studio exposes Windows EXE output",
  async () => {
    const text =
      await readFile(
        main,
        "utf8"
      );

    for (
      const marker of [
        '"exe"',
        "Windows EXE",
        "WINDOWS EXE'Yİ İNDİR",
        'createDownloadTicket('
      ]
    ) {
      assert.equal(
        text.includes(marker),
        true,
        `Missing Studio EXE marker: ${marker}`
      );
    }
  }
);

test(
  "Studio build history stores EXE artifact",
  async () => {
    const text =
      await readFile(
        library,
        "utf8"
      );

    assert.equal(
      text.includes(
        "exeUrl"
      ),
      true
    );

    assert.equal(
      text.includes(
        'put("exeUrl", b.exeUrl)'
      ),
      true
    );
  }
);

test(
  "Studio preserves EXE download extension",
  async () => {
    const text =
      await readFile(
        main,
        "utf8"
      );

    const start =
      text.indexOf(
        "private fun artifactDownloadName("
      );

    assert.notEqual(
      start,
      -1
    );

    const block =
      text.slice(
        start,
        start + 1800
      );

    assert.match(
      block,
      /"exe"\s*->\s*"exe"/
    );
  }
);

test(
  "Studio labels EXE logs as Windows logs",
  async () => {
    const text =
      await readFile(
        main,
        "utf8"
      );

    assert.equal(
      text.includes(
        '"Canlı Windows logu"'
      ),
      true
    );

    assert.match(
      text,
      /draft\.buildOutput\s*==\s*"exe"/
    );
  }
);
