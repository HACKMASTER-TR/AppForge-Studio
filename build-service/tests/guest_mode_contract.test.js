import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

function text(path) {
  return fs.readFileSync(
    new URL(path, import.meta.url),
    "utf8"
  );
}

test(
  "Android supports guest mode and account-aware home badge",
  () => {
    const main =
      text(
        "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
      );

    const home =
      text(
        "../../android-app/app/src/main/java/com/appforge/studio/ui/StudioHomeV2.kt"
      );

    assert.match(
      main,
      /if\s*\(\s*session\s*==\s*null\s*\)/
    );

    assert.match(
      main,
      /AppScreen\.ACCOUNT/
    );

    assert.match(
      main,
      /accountEmail\s*=\s*session\s*\?\.email/s
    );

    assert.match(
      home,
      /accountEmail:\s*String\?/
    );

    assert.match(
      home,
      /"login"\s+to\s+"GİRİŞ YAP"/
    );

    assert.match(
      home,
      /28550040284a@gmail\.com/
    );

    assert.match(
      home,
      /fullAdmin\s*->\s*"ADMIN"/s
    );

    assert.match(
      home,
      /!loggedIn\s*->\s*onOpenAccount\(\)/s
    );
  }
);
