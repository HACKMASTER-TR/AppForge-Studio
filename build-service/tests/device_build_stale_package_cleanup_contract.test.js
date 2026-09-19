import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here =
  path.dirname(
    fileURLToPath(import.meta.url)
  );

const repo =
  path.resolve(
    here,
    "../.."
  );

const source =
  fs.readFileSync(
    path.join(
      repo,
      "android-app/app/src/main/assets/device-build/install-toolchain.sh"
    ),
    "utf8"
  );

test(
  "non-node device builds repair stale Node package states",
  () => {
    assert.match(
      source,
      /repair_stale_node_packages\(\)/
    );

    assert.match(
      source,
      /\[ "\$ENGINE" = "node-web" \] && return 0/
    );

    assert.match(
      source,
      /'node-\*'[\s\S]*'npm'/
    );
  }
);

test(
  "healthy Node packages are preserved",
  () => {
    assert.match(
      source,
      /case "\$status" in[\s\S]*ii\*\)[\s\S]*Healthy package/
    );
  }
);

test(
  "only stale Node packages are force-purged",
  () => {
    assert.match(
      source,
      /APPFORGE_REPAIR_STALE_NODE/
    );

    assert.match(
      source,
      /dpkg[\s\S]*--purge[\s\S]*--force-all/
    );
  }
);

test(
  "node-web keeps its Node toolchain",
  () => {
    assert.match(
      source,
      /case "\$ENGINE" in[\s\S]*node-web\)[\s\S]*nodejs npm/
    );
  }
);
