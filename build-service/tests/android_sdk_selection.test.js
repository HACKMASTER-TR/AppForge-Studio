import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const engine =
  fs.readFileSync(
    new URL("../src/buildEngine.js", import.meta.url),
    "utf8"
  );

const fast =
  fs.readFileSync(
    new URL("../src/fastBuild.js", import.meta.url),
    "utf8"
  );

test(
  "FULL build uses selected Android SDK",
  () => {
    assert.match(
      engine,
      /minSdk = \$\{resolveAndroidSdk\(c\)\.minSdk\}/
    );

    assert.match(
      engine,
      /targetSdk = \$\{resolveAndroidSdk\(c\)\.targetSdk\}/
    );
  }
);

test(
  "FAST manifest uses selected Android SDK",
  () => {
    assert.match(
      fast,
      /android:minSdkVersion="\$\{minSdk\}"/
    );

    assert.match(
      fast,
      /android:targetSdkVersion="\$\{targetSdk\}"/
    );
  }
);
