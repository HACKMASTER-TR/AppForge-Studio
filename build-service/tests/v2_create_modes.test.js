import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const mainActivity =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
    import.meta.url
  );

test("Android client has Quick and Advanced create modes", async () => {
  const text =
    await readFile(
      mainActivity,
      "utf8"
    );

  assert.equal(
    text.includes(
      "Nasıl oluşturmak istersin?"
    ),
    true
  );

  assert.equal(
    text.includes(
      "Hızlı Oluştur"
    ),
    true
  );

  assert.equal(
    text.includes(
      "Gelişmiş Oluştur"
    ),
    true
  );
});

test("Quick mode generates a package name automatically", async () => {
  const text =
    await readFile(
      mainActivity,
      "utf8"
    );

  assert.equal(
    text.includes(
      'return "com.appforge.$safeName"'
    ),
    true
  );
});

test("Quick remote URL mode keeps native bridge disabled", async () => {
  const text =
    await readFile(
      mainActivity,
      "utf8"
    );

  assert.equal(
    text.includes(
      "remoteBridgeAllowed ="
    ),
    true
  );

  assert.equal(
    text.includes(
      "javascriptBridge =\n            base.sourceMode ==\n            SourceMode.LOCAL"
    ),
    true
  );
});
