import test from "node:test";
import assert from "node:assert/strict";
import {
  readFile
} from "node:fs/promises";

const queueSource =
  new URL(
    "../src/jobQueue.js",
    import.meta.url
  );

test(
  "dedicated source worker cannot claim normal Android jobs",
  async () => {
    const source =
      await readFile(
        queueSource,
        "utf8"
      );

    assert.match(
      source,
      /NOT\s*\(\s*\$1::jsonb\s*\?\s*\$2::text\s*\)\s*OR\s*\(\s*j\.required_capabilities\s*\?\s*\$2::text\s*\)/s
    );

    assert.match(
      source,
      /config\.sourceBuildIsolationCapability/
    );
  }
);

test(
  "queue ETA excludes dedicated source worker from normal builds",
  async () => {
    const source =
      await readFile(
        queueSource,
        "utf8"
      );

    assert.match(
      source,
      /rw\.capabilities\s*\?\s*\$3::text/s
    );

    assert.match(
      source,
      /\$2::jsonb\s*\?\s*\$3::text/s
    );

    assert.match(
      source,
      /w\.capabilities\s*\?\s*\$5::text/s
    );

    assert.match(
      source,
      /\$4::jsonb\s*\?\s*\$5::text/s
    );

    assert.match(
      source,
      /q\.required_capabilities\s*\?\s*\$5::text/s
    );
  }
);
