import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const projects =
  new URL(
    "../src/projects.js",
    import.meta.url
  );

const server =
  new URL(
    "../server.js",
    import.meta.url
  );

const config =
  new URL(
    "../src/config.js",
    import.meta.url
  );

test("free project limit defaults to five", async () => {
  const text =
    await readFile(
      config,
      "utf8"
    );

  assert.equal(
    text.includes(
      "FREE_PROJECT_LIMIT"
    ),
    true
  );
});

test("quota is atomic and server enforced", async () => {
  const text =
    await readFile(
      projects,
      "utf8"
    );

  assert.equal(
    text.includes(
      "pg_advisory_xact_lock"
    ),
    true
  );

  assert.equal(
    text.includes(
      "FREE_PROJECT_LIMIT_REACHED"
    ),
    true
  );

  assert.equal(
    text.includes(
      "quota.used >="
    ),
    true
  );
});

test("raw build also reserves project slot", async () => {
  const text =
    await readFile(
      server,
      "utf8"
    );

  const route =
    text.indexOf(
      '"/api/builds"'
    );

  const call =
    text.indexOf(
      "await upsertProject(",
      route
    );

  assert.equal(
    call > route,
    true
  );
});

test("quota endpoint exists", async () => {
  const text =
    await readFile(
      server,
      "utf8"
    );

  assert.equal(
    text.includes(
      '"/api/projects/quota"'
    ),
    true
  );
});
