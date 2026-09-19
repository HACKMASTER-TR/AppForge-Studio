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
  "Android supports guest mode and visible account entry",
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
      /AppScreen\.ACCOUNT/
    );

    assert.match(
      home,
      /accountEmail:\s*String\?/
    );

    assert.match(
      home,
      /val loggedIn\s*=/
    );

    assert.match(
      home,
      /onClick\s*=\s*onOpenAccount/
    );

    assert.match(
      home,
      /GİRİŞ YAP/
    );

    assert.match(
      home,
      /OwnerAccessPolicy/
    );

    assert.match(
      home,
      /fullAdmin/
    );
  }
);
