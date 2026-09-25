import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const read = path =>
  fs.readFileSync(
    new URL("../../" + path, import.meta.url),
    "utf8"
  );

const build = read(
  "android-app/app/src/main/assets/device-build/build-node.sh"
);

const prepare = read(
  "android-app/app/src/main/assets/device-build/prepare-offline-pack.sh"
);

test(
  "node-web uses persistent AppForge npm cache",
  () => {
    assert.match(
      build,
      /CACHE="\/opt\/appforge-device\/npm-cache-v1"/
    );

    assert.match(
      build,
      /export NPM_CONFIG_CACHE="\$CACHE"/
    );

    assert.match(
      prepare,
      /export NPM_CONFIG_CACHE="\$ROOT\/npm-cache-v1"/
    );

    assert.doesNotMatch(
      build,
      /\/root\/\.npm/
    );
  }
);

test(
  "offline npm remains network closed",
  () => {
    assert.match(
      build,
      /npm ci[\s\S]*--offline/
    );

    assert.match(
      build,
      /npm install[\s\S]*--offline/
    );

    assert.match(
      build,
      /\[ "\$OFFLINE" != "1" \]/
    );
  }
);

test(
  "transient cacache rename failure gets one safe online retry",
  () => {
    assert.match(
      build,
      /APPFORGE_NPM_CACHE_TRANSIENT_RETRY/
    );

    assert.match(
      build,
      /APPFORGE_NPM_CACHE_RETRY=PASS/
    );

    assert.match(
      build,
      /rm -rf "\$CACHE\/_cacache\/tmp"/
    );

    assert.doesNotMatch(
      build,
      /rm -rf "\$CACHE"/
    );
  }
);


test(
  "node-web explicitly installs native optional packages",
  () => {
    const includes =
      build.match(
        /--include=optional/g
      ) ?? [];

    assert.equal(
      includes.length,
      4
    );

    assert.match(
      build,
      /APPFORGE_ESBUILD_BINARY=PASS/
    );

    assert.match(
      build,
      /APPFORGE_ROLLUP_BINARY=PASS/
    );

    assert.match(
      build,
      /APPFORGE_NODE_NATIVE_OPTIONALS=PASS/
    );

    assert.match(
      build,
      /esbuild\.transformSync/
    );

    assert.match(
      build,
      /await import\("rollup"\)/
    );

    assert.match(
      build,
      /--ignore-scripts/
    );
  }
);
