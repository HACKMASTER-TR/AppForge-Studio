import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const root =
  new URL("../../", import.meta.url);

const read =
  path =>
    readFile(
      new URL(path, root),
      "utf8"
    );

test(
  "successful preflight checks do not trigger build diagnosis",
  async () => {
    const text =
      await read(
        "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeBuildErrorAdvisor.kt"
      );

    assert.ok(
      text.includes(
        "diagnosticPreflight"
      )
    );

    assert.ok(
      text.includes(
        'normalized.startsWith(\n                        "✅"'
      )
    );

    assert.ok(
      text.includes(
        "projede html başlangıç dosyası bulunamadı"
      )
    );
  }
);

test(
  "failed build does not display waiting stage",
  async () => {
    const text =
      await read(
        "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
      );

    assert.ok(
      text.includes(
        '"Derleme başarısız"'
      )
    );

    assert.ok(
      text.includes(
        '"Derleme iptal edildi"'
      )
    );

    assert.match(
      text,
      /logs\.isNotEmpty\(\)\s*&&\s*buildActive/
    );
  }
);
