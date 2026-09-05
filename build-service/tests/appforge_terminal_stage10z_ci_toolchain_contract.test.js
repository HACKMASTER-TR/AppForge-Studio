import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const asset = (name) =>
  new URL(
    `../../android-app/app/src/main/assets/terminal/${name}`,
    import.meta.url
  );

const workflowUrl =
  new URL(
    "../../.github/workflows/conversion-smoke.yml",
    import.meta.url
  );

test("Stage 10Z pins standalone development to Node 22", async () => {
  const repair =
    await readFile(
      asset("appforge-repair-tools"),
      "utf8"
    );

  assert.match(
    repair,
    /setup_22\.x/
  );

  assert.match(
    repair,
    /--allow-downgrades[\s\\]*nodejs/
  );

  assert.match(
    repair,
    /node-ci-22/
  );

  assert.match(
    repair,
    /process\.versions\.node/
  );
});

test("Stage 10Z doctor rejects a non CI Node major", async () => {
  const doctor =
    await readFile(
      asset("appforge-doctor"),
      "utf8"
    );

  assert.match(
    doctor,
    /node-ci-22/
  );

  assert.match(
    doctor,
    /process\.versions\.node/
  );

  assert.match(
    doctor,
    /Node\.js 22/
  );

  assert.match(
    doctor,
    /appforge-repair-tools/
  );
});

test("Stage 10Z tests self-bootstrap fresh clone dependencies", async () => {
  const helper =
    await readFile(
      asset("appforge-test"),
      "utf8"
    );

  assert.match(
    helper,
    /node_modules\/\.package-lock\.json/
  );

  assert.match(
    helper,
    /package-lock\.json -nt node_modules\/\.package-lock\.json/
  );

  assert.match(
    helper,
    /npm ci[\s\\]*--no-audit[\s\\]*--no-fund/
  );

  assert.match(
    helper,
    /AppForge testleri Node\.js 22 gerektiriyor/
  );
});

test("Stage 10Z standalone Node major matches Conversion Smoke CI", async () => {
  const workflow =
    await readFile(
      workflowUrl,
      "utf8"
    );

  assert.match(
    workflow,
    /node-version:\s*"22"/
  );

  assert.match(
    workflow,
    /run:\s*npm ci/
  );

  assert.match(
    workflow,
    /run:\s*npm test/
  );
});
