import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const entry = await readFile(
  new URL(
    "../../cloudflare/control-plane/src/integrity_staging_index.mjs",
    import.meta.url
  ),
  "utf8"
);

const workflow = await readFile(
  new URL(
    "../../.github/workflows/play-integrity-staging-deploy.yml",
    import.meta.url
  ),
  "utf8"
);

test(
  "Integrity staging exposes only health config and attest",
  () => {
    assert.match(
      entry,
      /\/health/
    );

    assert.match(
      entry,
      /\/api\/security\/config/
    );

    assert.match(
      entry,
      /\/api\/security\/attest/
    );

    assert.doesNotMatch(
      entry,
      /\/api\/admin\//
    );

    assert.doesNotMatch(
      entry,
      /\/api\/pro\//
    );
  }
);

test(
  "staging deploy targets a separate Worker",
  () => {
    assert.match(
      workflow,
      /name = "appforge-integrity-staging"/
    );

    assert.match(
      workflow,
      /main = "src\/integrity_staging_index\.mjs"/
    );

    assert.doesNotMatch(
      workflow,
      /name = "appforge-control-plane"/
    );

    assert.doesNotMatch(
      workflow,
      /routes\s*=/
    );

    assert.doesNotMatch(
      workflow,
      /\[\[d1_databases\]\]/
    );
  }
);

test(
  "production surfaces remain outside isolated deploy",
  () => {
    assert.match(
      workflow,
      /APPFORGE_CONTROL_PLANE_DEPLOY=NONE/
    );

    assert.match(
      workflow,
      /CUSTOM_DOMAIN_MUTATION=NONE/
    );

    assert.match(
      workflow,
      /PRODUCTION_ROUTE_MUTATION=NONE/
    );

    assert.match(
      workflow,
      /PLAY_PRODUCTION=UNTOUCHED/
    );
  }
);
