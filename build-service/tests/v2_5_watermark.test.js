import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const server =
  new URL(
    "../server.js",
    import.meta.url
  );

const workspace =
  new URL(
    "../src/workspaceBuild.js",
    import.meta.url
  );

const pro =
  new URL(
    "../src/proEntitlements.js",
    import.meta.url
  );

const engine =
  new URL(
    "../src/buildEngine.js",
    import.meta.url
  );

test("branding is server authoritative", async () => {
  const text =
    await readFile(
      pro,
      "utf8"
    );

  assert.equal(
    text.includes(
      "applyServerBranding"
    ),
    true
  );

  assert.equal(
    text.includes(
      'text:\n      "Built with AppForge"'
    ),
    true
  );

  assert.equal(
    text.includes(
      "showWatermark:\n      !entitlement.active"
    ),
    true
  );
});

test("raw and workspace builds apply branding before cache", async () => {
  const serverText =
    await readFile(
      server,
      "utf8"
    );

  const workspaceText =
    await readFile(
      workspace,
      "utf8"
    );

  assert.equal(
    serverText.indexOf(
      "applyServerBranding"
    ) <
    serverText.indexOf(
      "computeCacheKey",
      serverText.indexOf(
        "applyServerBranding"
      )
    ),
    true
  );

  assert.equal(
    workspaceText.indexOf(
      "applyServerBranding"
    ) <
    workspaceText.indexOf(
      "computeCacheKey",
      workspaceText.indexOf(
        "applyServerBranding"
      )
    ),
    true
  );
});

test("free watermark is native Android overlay", async () => {
  const text =
    await readFile(
      engine,
      "utf8"
    );

  assert.equal(
    text.includes(
      "TextView(this)"
    ),
    true
  );

  assert.equal(
    text.includes(
      "Gravity.START or"
    ),
    true
  );

  assert.equal(
    text.includes(
      "Gravity.BOTTOM"
    ),
    true
  );

  assert.equal(
    text.includes(
      '"Built with AppForge"'
    ),
    true
  );
});
