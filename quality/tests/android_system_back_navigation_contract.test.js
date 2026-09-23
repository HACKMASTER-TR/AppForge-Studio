import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const mainActivity = await readFile(
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
    import.meta.url
  ),
  "utf8"
);

const home = await readFile(
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/ui/StudioHomeV2.kt",
    import.meta.url
  ),
  "utf8"
);

test(
  "AppForge centralizes real route back navigation",
  () => {
    assert.match(
      mainActivity,
      /LATE_APP_ROUTE_BACK_HANDLER_V1/
    );

    assert.match(
      mainActivity,
      /navigateAppSystemBack\(\)/
    );

    assert.match(
      mainActivity,
      /visibleScreen !=[\s\S]*AppScreen\.HOME[\s\S]*visibleScreen !=[\s\S]*AppScreen\.TERMINAL/
    );
  }
);

test(
  "Home system back only opens explicit exit confirmation",
  () => {
    assert.match(
      mainActivity,
      /HOME_EXIT_CONFIRMATION_V1/
    );

    assert.match(
      mainActivity,
      /showExitConfirmation/
    );

    assert.match(
      mainActivity,
      /Uygulamadan çıkmak istediğinize emin misiniz\?/
    );

    assert.match(
      mainActivity,
      /hostActivity[\s\S]{0,300}\?\.finish\(\)/
    );
  }
);

test("normal AppForge routes use the late central back handler", () => {
  assert.match(
    mainActivity,
    /LATE_APP_ROUTE_BACK_HANDLER_V1/
  );

  assert.match(
    mainActivity,
    /visibleScreen !=[\s\S]*AppScreen\.HOME[\s\S]*visibleScreen !=[\s\S]*AppScreen\.TERMINAL/
  );

  assert.match(
    mainActivity,
    /BackHandler \{[\s\S]*navigateAppSystemBack\(\)/
  );
});

test(
  "Builder route still preserves step-aware navigation",
  () => {
    assert.match(
      mainActivity,
      /AppScreen\.BUILDER/
    );

    assert.match(
      mainActivity,
      /navigateAppSystemBack|step/
    );
  }
);

test(
  "simplified Home does not depend on the legacy Successful Builds nested folder",
  () => {
    assert.match(
      home,
      /builds|successfulBuilds/
    );

    assert.doesNotMatch(
      home,
      /successfulApkFolderOpen/
    );
  }
);

test(
  "Builder system back remains handled by the central back flow",
  () => {
    assert.match(
      mainActivity,
      /navigateAppSystemBack\(\)/
    );

    assert.match(
      mainActivity,
      /AppScreen\.BUILDER/
    );
  }
);
