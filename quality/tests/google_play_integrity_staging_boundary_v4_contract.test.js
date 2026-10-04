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
  "Wrangler config is colocated with the control-plane source tree",
  () => {
    const expected =
      'CONFIG="$GITHUB_WORKSPACE/cloudflare/control-plane/.wrangler-integrity-staging-ci.toml"';

    const occurrences =
      workflow.split(expected).length - 1;

    assert.equal(
      occurrences,
      2
    );

    assert.doesNotMatch(
      workflow,
      /CONFIG="\$RUNNER_TEMP\/appforge-integrity-staging\.toml"/
    );

    assert.match(
      workflow,
      /main = "src\/integrity_staging_index\.mjs"/
    );
  }
);

test(
  "first isolated deploy uploads code and secrets atomically",
  () => {
    assert.doesNotMatch(
      workflow,
      /wrangler@4 secret put/
    );

    assert.doesNotMatch(
      workflow,
      /wrangler secret put/
    );

    assert.match(
      workflow,
      /--secrets-file/
    );

    assert.match(
      workflow,
      /ATOMIC_CODE_AND_SECRETS_DEPLOY=PASS/
    );

    assert.match(
      workflow,
      /EPHEMERAL_SECRET_FILE_REMOVED=PASS/
    );
  }
);

test(
  "secret payload is dry-run before live deploy and failures are safely diagnosed",
  () => {
    assert.match(
      workflow,
      /SECRET_PAYLOAD_DRY_RUN=PASS/
    );

    assert.match(
      workflow,
      /--dry-run[\s\S]*--secrets-file/
    );

    assert.match(
      workflow,
      /WRANGLER SAFE DEPLOY DIAGNOSTIC/
    );

    assert.match(
      workflow,
      /safe_deploy_diag/
    );

    assert.match(
      workflow,
      /REDACTED_PEM/
    );

    assert.doesNotMatch(
      workflow,
      /cat "\$DEPLOY_LOG"/
    );
  }
);

test(
  "live smoke derives the workers.dev URL from the successful deploy",
  () => {
    assert.doesNotMatch(
      workflow,
      /appforge-integrity-staging\.28550040284a\.workers\.dev/
    );

    assert.match(
      workflow,
      /DEPLOYMENT_URL_DISCOVERY=PASS/
    );

    assert.match(
      workflow,
      /INTEGRITY_STAGING_BASE/
    );

    assert.match(
      workflow,
      /\$GITHUB_ENV/
    );

    assert.ok(
      workflow.includes(
        'BASE="${INTEGRITY_STAGING_BASE:?missing staging base URL}"'
      )
    );

    const atomicPassCount =
      workflow.split(
        `echo "ATOMIC_CODE_AND_SECRETS_DEPLOY=PASS"`
      ).length - 1;

    assert.equal(
      atomicPassCount,
      1
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
