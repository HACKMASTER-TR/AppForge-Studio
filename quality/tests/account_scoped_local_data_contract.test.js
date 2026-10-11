import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const library =
  fs.readFileSync(
    new URL(
      "../../android-app/app/src/main/java/com/appforge/studio/io/ProjectLibrary.kt",
      import.meta.url
    ),
    "utf8"
  );

const main =
  fs.readFileSync(
    new URL(
      "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
      import.meta.url
    ),
    "utf8"
  );

const home =
  fs.readFileSync(
    new URL(
      "../../android-app/app/src/main/java/com/appforge/studio/ui/StudioHomeV2.kt",
      import.meta.url
    ),
    "utf8"
  );


test(
  "local project data remains account scoped",
  () => {
    assert.match(
      library,
      /activeAccountScope/
    );

    assert.match(
      library,
      /fun setAccountScope/
    );

    assert.match(
      library,
      /project_library/
    );

    assert.match(
      library,
      /project_trash/
    );

    assert.match(
      library,
      /build_history/
    );

    /*
     * Simplified StudioHomeV2 must still invalidate local
     * project state when the active account changes.
     */
    assert.match(
      home,
      /remember\(\s*accountEmail/
    );
  }
);

test(
  "local project data cannot reintroduce retired project-quota UI",
  () => {
    /*
     * Local project persistence must never claim or consume the
     * retired successful-project quota.
     */
    assert.doesNotMatch(
      main,
      /\.claimFreeProjectSlot\(/
    );

    /*
     * The current accountless product no longer exposes the
     * historical server/local trial counter to normal users.
     */
    assert.doesNotMatch(
      main,
      /serverFreeProjectUsed/
    );

    assert.doesNotMatch(
      main,
      /Deneme Hakkı/
    );

    assert.doesNotMatch(
      main,
      /yeni proje hakkın kaldı/
    );

    assert.doesNotMatch(
      main,
      /Ücretsiz denemede toplam/
    );
  }
);
