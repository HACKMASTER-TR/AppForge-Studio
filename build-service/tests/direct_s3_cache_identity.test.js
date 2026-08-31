import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const server =
  readFileSync(
    new URL(
      "../server.js",
      import.meta.url
    ),
    "utf8"
  );

test(
  "direct S3 project cache identity uses canonical ZIP content",
  () => {
    assert.ok(
      server.includes(
        "async function directProjectContentIdentity("
      )
    );

    assert.ok(
      server.includes(
        "await materializeInput("
      )
    );

    assert.ok(
      server.includes(
        "return await projectContentSha256("
      )
    );

    assert.ok(
      server.includes(
        "? await directProjectContentIdentity("
      )
    );

    assert.equal(
      server.includes(
        "directInputCacheIdentity("
      ),
      false
    );
  }
);
