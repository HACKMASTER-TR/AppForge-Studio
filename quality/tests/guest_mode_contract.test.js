import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

function text(path) {
  return fs.readFileSync(
    new URL(path, import.meta.url),
    "utf8"
  );
}

test("Android guest mode exposes Google admin entry", () => {
    const home = text(
        "../../android-app/app/src/main/java/com/appforge/studio/ui/StudioHomeV2.kt"
    );

    assert.match(home, /YÖNETİCİ GİRİŞİ/);
    assert.match(home, /onOpenAdmin/);
    assert.doesNotMatch(home, /GİRİŞ YAP/);
});
