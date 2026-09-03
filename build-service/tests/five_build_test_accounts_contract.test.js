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

test(
  "five parallel build tester allows both authorized test accounts",
  () => {
    const source =
      read(
        "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
      );

    assert.match(
      source,
      /28550040284a@gmail\.com/
    );

    assert.match(
      source,
      /heyomert@gmail\.com/
    );

    assert.match(
      source,
      /fiveParallelBuildTesterEmails/
    );
  }
);

test(
  "five build tester access does not grant admin role",
  () => {
    const source =
      read(
        "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
      );

    assert.match(
      source,
      /isFiveParallelBuildTester/
    );

    assert.doesNotMatch(
      source,
      /heyomert@gmail\.com[\s\S]{0,120}role\s*=\s*"admin"/
    );
  }
);
