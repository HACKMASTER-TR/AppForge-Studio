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

// Each probe owns a temporary Git repository; production evidence stays untouched.
function basisFixtureProbe(extra) {
  const result = probeGenerator({}, `
import hashlib
import subprocess
import tempfile
from pathlib import Path
original_root = g.ROOT
with tempfile.TemporaryDirectory() as folder:
    g.ROOT = Path(folder)
    def write(rel, content="fixture"):
        target = g.ROOT / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")
    def git(*args):
        return subprocess.run(["git", *args], cwd=g.ROOT, check=True, capture_output=True)
    git("init", "-q")
    write(".gitignore", "ignored*\\n")
    for name in g.EXPECTED_MIGRATIONS:
        write("cloudflare/control-plane/migrations/" + name)
    write("docs/wiki/tracked.md")
    write("quality/tests/tracked.test.js")
    write("android-app/app/src/test/Tracked.kt")
    write(g.ASSET_REL)
    git("add", ".")
    write("docs/wiki/untracked.md")
    g.verify_current_contract = lambda: None
    g.require_marker = lambda *args: None
    def inventory():
        return g.canonical_basis_files(g.migration_files())
    def snapshot():
        return g.build_snapshot()
    baseline = snapshot()
    files = inventory()
${extra}
    g.ROOT = original_root
`);
  assert.equal(result.status, 0, result.stdout + result.stderr);
}

test("V2 basis policy version is committed into the source digest", () => {
  basisFixtureProbe(`
    assert g.BASIS_POLICY_VERSION == "SECOND_BRAIN_V2_BASIS_POLICY_V1"
    digest = hashlib.sha256(b"SECOND_BRAIN_V2_BASIS_POLICY_V1\\0")
    for rel in sorted(files):
        digest.update(rel.encode("utf-8") + b"\\0")
        digest.update(hashlib.sha256((g.ROOT / rel).read_bytes()).digest() + b"\\0")
    assert baseline["sourceBasisSha256"] == digest.hexdigest()
    g.BASIS_POLICY_VERSION = "synthetic-next-policy"
    assert snapshot()["sourceBasisSha256"] != baseline["sourceBasisSha256"]
`);
});

test("V2 inventory counts use only canonical basis regular files", () => {
  basisFixtureProbe(`
    assert baseline["sourceBasisFileCount"] == len(files)
    assert baseline["wikiPages"] == 2
    assert baseline["qualityContractFiles"] == 1
    assert baseline["androidUnitTestFiles"] == 1
    for key, prefix, suffix in [
        ("wikiPages", "docs/wiki/", ".md"),
        ("qualityContractFiles", "quality/tests/", ".test.js"),
        ("androidUnitTestFiles", "android-app/app/src/test/", ".kt")
    ]:
        assert baseline[key] == sum(rel.startswith(prefix) and rel.endswith(suffix) for rel in files)
    assert "docs/wiki/untracked.md" in files
`);
});

test("V2 ignored count-like fixtures cannot inflate coverage counts", () => {
  basisFixtureProbe(`
    for rel in ["docs/wiki/ignored.md", "quality/tests/ignored.test.js", "android-app/app/src/test/ignored.kt"]:
        write(rel)
        assert rel not in inventory()
    assert snapshot() == baseline
`);
});

test("V2 matching directories cannot inflate coverage counts", () => {
  basisFixtureProbe(`
    for rel in ["docs/wiki/directory.md", "quality/tests/directory.test.js", "android-app/app/src/test/directory.kt", "cloudflare/control-plane/migrations/directory.sql"]:
        (g.ROOT / rel).mkdir()
        assert rel not in inventory()
    assert snapshot() == baseline
`);
});

test("V2 migration validation inventory is included in basis coverage", () => {
  basisFixtureProbe(`
    migration = "cloudflare/control-plane/migrations/" + g.EXPECTED_MIGRATIONS[0]
    git("rm", "--cached", migration)
    write(".gitignore", "ignored*\\n" + migration + "\\n")
    assert migration not in g.repository_files()
    assert migration in inventory()
    assert set(g.migration_files()).issubset(inventory())
    assert snapshot()["d1Migrations"] == len(g.migration_files()) == 5
    original_state = g.migration_state
    original_basis = g.canonical_basis_files
    captured = []
    def capture_basis(migrations):
        captured.append(migrations)
        return original_basis(migrations)
    def capture_state(migrations):
        assert migrations is captured[-1]
        return original_state(migrations)
    g.canonical_basis_files = capture_basis
    g.migration_state = capture_state
    payload = snapshot()
    assert payload["sourceBasisFileCount"] == len(inventory())
`);
});

test("V2 unexpected ignored migration remains fail closed", () => {
  basisFixtureProbe(`
    for name in ["ignored_unexpected.sql", "unexpected.sql"]:
        rel = "cloudflare/control-plane/migrations/" + name
        write(rel)
        assert rel in inventory()
        assert g.basis_hash(inventory()) != baseline["sourceBasisSha256"]
        if name.startswith("ignored"):
            assert rel not in g.repository_files()
        try:
            snapshot()
        except SystemExit as error:
            assert str(error) == "STOP: D1_MIGRATION_SET_NOT_EXACT"
        else:
            raise AssertionError("Unexpected migration accepted")
        (g.ROOT / rel).unlink()
`);
});

test("V2 migration content changes the source basis digest", () => {
  basisFixtureProbe(`
    write("cloudflare/control-plane/migrations/" + g.EXPECTED_MIGRATIONS[0], "changed migration bytes")
    changed = snapshot()
    assert changed["sourceBasisSha256"] != baseline["sourceBasisSha256"]
    assert changed["sourceBasisFileCount"] == baseline["sourceBasisFileCount"]
    assert changed["d1Migrations"] == 5
`);
});

test("V2 limited attestation preserves intentional exclusions", () => {
  basisFixtureProbe(`
    # These paths may carry authoritative evidence; this digest deliberately covers a subset.
    excluded = ["AGENTS.md", ".appforge/state.json", "unselected/evidence.md",
        "docs/wiki/evidence.txt", "docs/wiki/ignored.md", "local.log", "build/artifact.bin", g.ASSET_REL]
    for rel in excluded:
        write(rel, "synthetic excluded fixture")
    git("add", "AGENTS.md", ".appforge/state.json", "unselected/evidence.md", "docs/wiki/evidence.txt", "local.log", "build/artifact.bin")
    for rel in excluded:
        assert rel not in inventory()
    assert snapshot() == baseline
    for rel in excluded:
        write(rel, "changed synthetic excluded fixture")
    assert snapshot() == baseline
    write("docs/wiki/tracked.md", "changed selected evidence")
    assert snapshot()["sourceBasisSha256"] != baseline["sourceBasisSha256"]
`);
});
