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
      "RECORDED_RECONCILIATION_0001_0005__LIVE_TARGET_NOT_VERIFIED"
    );
  }
);

test(
  "V2 scopes recorded historical publisher and environment claims",
  () => {
    assert.equal(
      snapshot.publisherAuthorization,
      "RECORDED_HISTORICAL_STAGING_DEVICE_ACCEPTANCE__CURRENT_HEAD_NOT_INFERRED"
    );

    assert.equal(
      snapshot.productionPublisherEndpoint,
      "REPOSITORY_RECORDED_DISABLED_PENDING_REVIEW__LIVE_PRODUCTION_NOT_VERIFIED"
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
  "Second Brain UI and AI bridge consume schema V2",
  () => {
    for (
      const source of [
        screen,
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
      bridge,
      /riskScore|apiRoutes|databaseTables|liveGithub/
    );
  }
);

// Override reads in memory; never mutate repository evidence or generated assets.
function probeGenerator(overrides = {}, extra = "") {
  const result = spawnSync(
    "python3",
    ["-B", "-c", `
import importlib.util
import json
import sys
spec = importlib.util.spec_from_file_location("snapshot", sys.argv[1])
g = importlib.util.module_from_spec(spec)
spec.loader.exec_module(g)
overrides = json.load(sys.stdin)
original_text = g.text
g.text = lambda rel: overrides.get(rel, original_text(rel))
${extra}
print(g.rendered(), end="")
`, generatorPath],
    {
      cwd: repo,
      encoding: "utf8",
      input: JSON.stringify(overrides)
    }
  );
  return result;
}

function assertScopedLabels(payload) {
  assert.equal(payload.schemaVersion, 2);
  for (const [key, scoped, old] of [
    ["publisherAuthorization",
      "RECORDED_HISTORICAL_STAGING_DEVICE_ACCEPTANCE__CURRENT_HEAD_NOT_INFERRED",
      "STAGING_DEVICE_ACCEPTED"],
    ["d1Ledger",
      "RECORDED_RECONCILIATION_0001_0005__LIVE_TARGET_NOT_VERIFIED",
      "RECONCILED_0001_0005"],
    ["productionPublisherEndpoint",
      "REPOSITORY_RECORDED_DISABLED_PENDING_REVIEW__LIVE_PRODUCTION_NOT_VERIFIED",
      "DISABLED"]
  ]) {
    assert.equal(typeof payload[key], "string", key);
    assert.equal(payload[key], scoped, key);
    assert.notEqual(payload[key], old, key);
  }
  assert.equal(payload.d1Migrations, 5);
  assert.equal(payload.liveState, "NOT_LIVE_QUERY");
}

test("V2 emits scoped labels rather than complete unqualified status values", () => {
  assertScopedLabels(snapshot);
  const result = probeGenerator();
  assert.equal(result.status, 0, result.stdout + result.stderr);
  assertScopedLabels(JSON.parse(result.stdout));
});

const recordedMarkers = [
  ["docs/wiki/Hot_Context.md",
    "D1 migration ledger reconciliation is complete",
    "D1_LEDGER_WIKI_EVIDENCE_MISSING"],
  ["docs/wiki/03_Architecture/Windows_Publisher_Authorization.md",
    "Physical acceptance proved:",
    "PUBLISHER_PHYSICAL_EVIDENCE_MISSING"],
  ["docs/wiki/03_Architecture/Windows_Publisher_Authorization.md",
    "Production custom-domain signing remains disabled",
    "PRODUCTION_PUBLISHER_BOUNDARY_MISSING"]
];

test("V2 markers remain repository contract guards, not current acceptance evidence", () => {
  for (const [rel, marker, code] of recordedMarkers) {
    const source = fs.readFileSync(path.join(repo, rel), "utf8");
    const result = probeGenerator({
      [rel]: source.replaceAll(marker, "[repository marker removed]")
    });
    assert.notEqual(result.status, 0, marker);
    assert.ok(result.stderr.includes(code), result.stderr);
  }
});

test("V2 contradictory, superseding and failure prose cannot promote recorded markers", () => {
  for (const prose of [
    "Contradiction: current physical acceptance and live environment state are unknown.",
    "Superseded: this historical record does not apply to current HEAD or the deployed target.",
    "FAIL: current device acceptance failed; live D1 verification failed; production state is unverified."
  ]) {
    const overrides = {};
    for (const [rel] of recordedMarkers) {
      overrides[rel] = prose + "\n" +
        fs.readFileSync(path.join(repo, rel), "utf8") + "\n" + prose;
    }
    const result = probeGenerator(overrides);
    assert.equal(result.status, 0, result.stdout + result.stderr);
    assertScopedLabels(JSON.parse(result.stdout));
  }
});

test("V2 non-acceptance current-state records cannot promote unrelated status labels", () => {
  const rel = "docs/wiki/01_Project/current_state.json";
  for (const state of ["UNKNOWN", "NOT_RUN", "NOT_APPLICABLE"]) {
    const manifest = JSON.parse(fs.readFileSync(path.join(repo, rel), "utf8"));
    for (const workstream of manifest.workstreams) {
      for (const stage of Object.keys(workstream.stages)) {
        workstream.stages[stage] = {
          state,
          reason: "Synthetic non-acceptance record; no current or live acceptance."
        };
      }
    }
    const result = probeGenerator({ [rel]: JSON.stringify(manifest) });
    assert.equal(result.status, 0, result.stdout + result.stderr);
    assertScopedLabels(JSON.parse(result.stdout));
  }
});

test("V2 exact migration inventory is independent of recorded ledger semantics", () => {
  const result = probeGenerator({}, `
g.EXPECTED_MIGRATIONS = g.EXPECTED_MIGRATIONS + ["0006_unexpected.sql"]
`);
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /D1_MIGRATION_SET_NOT_EXACT/);
});

test("V2 scoped generation retains determinism, self-exclusion and freshness rejection", () => {
  const result = probeGenerator({}, `
import contextlib
import io
import tempfile
from pathlib import Path
assert g.ASSET_REL not in g.repository_files()
expected = g.rendered()
assert expected == g.rendered()
with tempfile.TemporaryDirectory() as folder:
    g.ASSET = Path(folder) / "snapshot.json"
    g.ASSET.write_text(expected, encoding="utf-8")
    sys.argv = [sys.argv[0], "--check"]
    with contextlib.redirect_stdout(io.StringIO()) as output:
        g.main()
    assert "SECOND_BRAIN_SNAPSHOT_CHECK=PASS" in output.getvalue()
    g.ASSET.write_text(expected + "\\n", encoding="utf-8")
    try:
        g.main()
    except SystemExit as error:
        assert str(error) == "STOP: SNAPSHOT_OUT_OF_DATE"
    else:
        raise AssertionError("Stale snapshot accepted")
`);
  assert.equal(result.status, 0, result.stdout + result.stderr);
  assertScopedLabels(JSON.parse(result.stdout));
});
