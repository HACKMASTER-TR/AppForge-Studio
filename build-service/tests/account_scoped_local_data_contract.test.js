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

    assert.match(
      main,
      /ProjectLibrary\.setAccountScope\(\s*context,\s*session\?\.userId/
    );

    assert.match(
      home,
      /remember\(\s*accountEmail\s*\)/
    );
  }
);


test(
  "local data cannot consume successful-project quota",
  () => {
    assert.doesNotMatch(
      main,
      /\.claimFreeProjectSlot\(/
    );

    assert.match(
      main,
      /projectQuota/
    );

    assert.match(
      main,
      /serverFreeProjectUsed/
    );
  }
);
