import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

function text(path) {
  return fs.readFileSync(
    new URL(path, import.meta.url),
    "utf8"
  );
}

test("guest Home hides admin while Settings retains secure discovery", () => {
    const home = text(
        "../../android-app/app/src/main/java/com/appforge/studio/ui/StudioHomeV2.kt"
    );
    const settings = text(
        "../../android-app/app/src/main/java/com/appforge/studio/AppForgeSettingsScreens.kt"
    );
    const admin = text(
        "../../android-app/app/src/main/java/com/appforge/studio/AdminOpsScreen.kt"
    );

    assert.doesNotMatch(home, /YÖNETİCİ GİRİŞİ|OwnerAdminCard\s*\(/);
    assert.doesNotMatch(home, /TextButton\(onClick = onOpenAdmin\)/);
    assert.match(settings, /versionTapCount\s*>=\s*7/);
    assert.match(settings, /onOpenAdmin\(\)/);
    assert.match(admin, /GoogleAdminIdentityClient\(host, serverUrl\)\.signIn\(\)/);
    assert.match(admin, /adminApi\.systemStatus\(token\)/);
});
