import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const asset = (name) =>
  new URL(
    `../../android-app/app/src/main/assets/terminal/${name}`,
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
