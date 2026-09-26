import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

function text(path) {
  return fs.readFileSync(
    new URL(path, import.meta.url),
    "utf8"
  );
}

test("guest Home hides admin unless verified owner state is active", () => {
    const home = text(
        "../../android-app/app/src/main/java/com/appforge/studio/ui/StudioHomeV2.kt"
    );
    const settings = text(
        "../../android-app/app/src/main/java/com/appforge/studio/AppForgeSettingsScreens.kt"
    );
    const admin = text(
        "../../android-app/app/src/main/java/com/appforge/studio/AdminOpsScreen.kt"
    );

    assert.doesNotMatch(
        home,
        /YÖNETİCİ GİRİŞİ|OwnerAdminCard\s*\(/
    );

    assert.match(
        home,
        /val fullAdmin[\s\S]{0,220}?OwnerAccessPolicy\.isActiveOwner/
    );

    assert.match(
        home,
        /if\s*\(fullAdmin\)[\s\S]{0,180}?TextButton\(onClick = onOpenAdmin\)/
    );

    assert.match(
        home,
        /Text\("Yönetici"\)/
    );

    assert.match(settings, /versionTapCount\s*>=\s*7/);
    assert.match(settings, /onOpenAdmin\(\)/);
    assert.match(admin, /GoogleAdminIdentityClient\(host, serverUrl\)\.signIn\(\)/);
    assert.match(admin, /adminApi\.systemStatus\(token\)/);
});
