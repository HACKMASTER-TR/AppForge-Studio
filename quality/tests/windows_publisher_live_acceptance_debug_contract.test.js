import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here =
  path.dirname(
    fileURLToPath(
      import.meta.url
    )
  );

const repo =
  path.resolve(
    here,
    "../.."
  );

const screen =
  fs.readFileSync(
    path.join(
      repo,
      "android-app/app/src/main/java/com/appforge/studio/AdminOpsScreen.kt"
    ),
    "utf8"
  );

const harness =
  fs.readFileSync(
    path.join(
      repo,
      "android-app/app/src/main/java/com/appforge/studio/PublisherSigningLiveAcceptance.kt"
    ),
    "utf8"
  );

test(
  "live publisher acceptance UI is debug only and owner gated",
  () => {
    assert.match(
      screen,
      /if\s*\(\s*BuildConfig\.DEBUG\s*\)/
    );

    assert.match(
      screen,
      /PublisherSigningLiveAcceptance/
    );

    assert.match(
      screen,
      /if\s*\(\s*authorized\s*\)/
    );
  }
);

test(
  "live acceptance uses current server verified Google admin token without rendering it",
  () => {
    assert.match(
      harness,
      /currentGoogleIdToken/
    );

    assert.match(
      harness,
      /Authorization/
    );

    assert.doesNotMatch(
      harness,
      /println\s*\(/
    );

    assert.doesNotMatch(
      harness,
      /Log\.[a-zA-Z]+\s*\(/
    );
  }
);

test(
  "live acceptance proves mismatch valid consume and replay in safe order",
  () => {
    const mismatch =
      harness.indexOf(
        "ARTIFACT_HASH_MISMATCH=BLOCKED"
      );

    const consume =
      harness.indexOf(
        "SIGNING_GRANT_CONSUME=PASS"
      );

    const replay =
      harness.indexOf(
        "SIGNING_GRANT_REPLAY=BLOCKED"
      );

    assert.ok(
      mismatch >= 0 &&
      consume > mismatch &&
      replay > consume
    );

    assert.match(
      harness,
      /signing_grant_invalid/
    );

    assert.match(
      harness,
      /signing_grant_replay/
    );
  }
);

test(
  "acceptance harness uses only the two publisher signing endpoints",
  () => {
    assert.match(
      harness,
      /\/api\/admin\/windows-signing\/grant/
    );

    assert.match(
      harness,
      /\/api\/admin\/windows-signing\/consume/
    );

    assert.doesNotMatch(
      harness,
      /wrangler/
    );

    assert.doesNotMatch(
      harness,
      /migration/i
    );
  }
);
