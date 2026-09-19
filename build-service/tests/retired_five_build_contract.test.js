import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const root =
  new URL(
    "../../",
    import.meta.url
  );

function read(relativePath) {
  return fs.readFileSync(
    new URL(
      relativePath,
      root
    ),
    "utf8"
  );
}

const main =
  read(
    "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
  );

const home =
  read(
    "android-app/app/src/main/java/com/appforge/studio/ui/StudioHomeV2.kt"
  );

test(
  "retired five-build stress tool is absent from production UI",
  () => {
    assert.doesNotMatch(
      main,
      /5 Build Testi/
    );

    assert.doesNotMatch(
      main,
      /startFiveParallelBuildTest/
    );

    assert.doesNotMatch(
      main,
      /fiveParallelBuildRunning/
    );

    assert.doesNotMatch(
      main,
      /ParallelBuildTestItem/
    );
  }
);

test(
  "retired five-build tester allow-list is gone",
  () => {
    assert.doesNotMatch(
      main,
      /fiveParallelBuildTesterEmails/
    );

    assert.doesNotMatch(
      main,
      /isFiveParallelBuildTester/
    );

    assert.doesNotMatch(
      main,
      /heyomert@gmail\.com/
    );
  }
);

test(
  "Admin Ops remains protected by active owner policy",
  () => {
    assert.match(
      main,
      /val isAdminOpsAccount\s*=\s*OwnerAccessPolicy[\s\S]{0,300}?isActiveOwner/
    );

    assert.match(
      home,
      /val fullAdmin\s*=\s*OwnerAccessPolicy[\s\S]{0,300}?isActiveOwner/
    );

    assert.match(
      home,
      /if\s*\(\s*fullAdmin\s*\)[\s\S]{0,1500}?onOpenAdmin/
    );
  }
);
