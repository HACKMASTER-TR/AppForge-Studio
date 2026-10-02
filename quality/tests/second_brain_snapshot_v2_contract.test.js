import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import {
  spawnSync
} from "node:child_process";
import {
  fileURLToPath
} from "node:url";

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

const assetPath =
  path.join(
    repo,
    "android-app/app/src/main/assets/second_brain_snapshot.json"
  );

const generatorPath =
  path.join(
    repo,
    "scripts/generate-second-brain-snapshot.py"
  );

const screen =
  fs.readFileSync(
    path.join(
      repo,
      "android-app/app/src/main/java/com/appforge/studio/SecondBrainScreen.kt"
    ),
    "utf8"
  );

const agent =
  fs.readFileSync(
    path.join(
      repo,
      "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentSecondBrain.kt"
    ),
    "utf8"
  );

const bridge =
  fs.readFileSync(
    path.join(
      repo,
      "android-app/app/src/main/java/com/appforge/studio/ai/SecondBrainBridge.kt"
    ),
    "utf8"
  );

const generator =
  fs.readFileSync(
    generatorPath,
    "utf8"
  );

const snapshot =
  JSON.parse(
    fs.readFileSync(
      assetPath,
      "utf8"
    )
  );

test(
  "Second Brain V2 snapshot is repository derived and deterministic",
  () => {
    assert.equal(
      snapshot.schemaVersion,
      2
    );

    assert.equal(
      snapshot.authority,
      "REPOSITORY_DERIVED"
    );

    assert.match(
      snapshot.sourceBasisSha256,
      /^[0-9a-f]{64}$/
    );

    assert.equal(
      snapshot.generatedBy,
      "scripts/generate-second-brain-snapshot.py"
    );

    assert.doesNotMatch(
      generator,
      /datetime|time\.time|Date\.now/
    );

    assert.doesNotMatch(
      generator,
      /git\s+rev-parse/
    );
  }
);

test(
  "V2 removes stale subjective and pseudo-live legacy fields",
  () => {
    for (
      const key of [
        "head",
        "branch",
        "risk",
        "riskScore",
        "apiRoutes",
        "databaseTables",
        "tests",
        "liveGithub"
      ]
    ) {
      assert.equal(
        Object.hasOwn(
          snapshot,
          key
        ),
        false,
        key
      );
    }

    assert.equal(
      snapshot.liveState,
      "NOT_LIVE_QUERY"
    );
  }
);

test(
  "V2 records only reproducible repository coverage counts",
  () => {
    assert.ok(
      snapshot.sourceBasisFileCount > 0
    );

    assert.ok(
      snapshot.wikiPages > 0
    );

    assert.ok(
      snapshot.qualityContractFiles > 0
    );

    assert.ok(
      snapshot.androidUnitTestFiles > 0
    );

    assert.equal(
      snapshot.d1Migrations,
      5
    );

    assert.equal(
      snapshot.d1Ledger,
      "RECONCILED_0001_0005"
    );
  }
);

test(
  "V2 declares staging publisher acceptance without claiming production",
  () => {
    assert.equal(
      snapshot.publisherAuthorization,
      "STAGING_DEVICE_ACCEPTED"
    );

    assert.equal(
      snapshot.productionPublisherEndpoint,
      "DISABLED"
    );

    assert.equal(
      snapshot.releaseGate,
      "REVIEW_REQUIRED"
    );
  }
);

test(
  "V2 generator check reproduces exact committed snapshot",
  () => {
    const result =
      spawnSync(
        "python3",
        [
          "scripts/generate-second-brain-snapshot.py",
          "--check"
        ],
        {
          cwd:
            repo,
          encoding:
            "utf8"
        }
      );

    assert.equal(
      result.status,
      0,
      result.stdout +
        result.stderr
    );

    assert.match(
      result.stdout,
      /SECOND_BRAIN_SNAPSHOT_CHECK=PASS/
    );
  }
);

test(
  "Second Brain UI and AI consume schema V2",
  () => {
    for (
      const source of [
        screen,
        agent,
        bridge
      ]
    ) {
      assert.match(
        source,
        /schemaVersion/
      );

      assert.match(
        source,
        /sourceBasisSha256/
      );
    }

    assert.match(
      screen,
      /Bu snapshot canlı GitHub veya Cloudflare sorgusu değildir/
    );

    assert.doesNotMatch(
      screen,
      /riskScore|apiRoutes|databaseTables|liveGithub/
    );

    assert.doesNotMatch(
      agent,
      /riskScore|apiRoutes|databaseTables|liveGithub/
    );

    assert.doesNotMatch(
      bridge,
      /riskScore|apiRoutes|databaseTables|liveGithub/
    );
  }
);
