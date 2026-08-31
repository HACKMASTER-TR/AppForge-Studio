import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const root =
  new URL(
    "../../",
    import.meta.url
  );

const read =
  path =>
    readFile(
      new URL(
        path,
        root
      ),
      "utf8"
    );

test(
  "queue position follows worker priority ordering",
  async () => {
    const text =
      await read(
        "build-service/src/jobQueue.js"
      );

    assert.ok(
      text.includes(
        "export async function buildQueuePosition"
      )
    );

    assert.match(
      text,
      /q\.priority\s*<\s*\$1/
    );

    assert.match(
      text,
      /q\.created_at\s*<\s*\$2/
    );

    assert.ok(
      text.includes(
        "position"
      )
    );

    assert.ok(
      text.includes(
        "ahead"
      )
    );

    assert.match(
      text,
      /q\.required_capabilities\s*<@\s*w\.capabilities/
    );

    assert.match(
      text,
      /target\.required_capabilities/
    );
  }
);

test(
  "queue ETA uses compatible live workers and recent durations",
  async () => {
    const text =
      await read(
        "build-service/src/jobQueue.js"
      );

    assert.ok(
      text.includes(
        "compatibleWorkerSlots"
      )
    );

    assert.ok(
      text.includes(
        "required_capabilities"
      )
    );

    assert.ok(
      text.includes(
        "AVG(duration_ms)"
      )
    );

    assert.ok(
      text.includes(
        "estimatedWaitSeconds"
      )
    );

    assert.ok(
      text.includes(
        '"approximate"'
      )
    );
  }
);

test(
  "build detail API exposes queue position metadata",
  async () => {
    const server =
      await read(
        "build-service/server.js"
      );

    assert.ok(
      server.includes(
        "buildQueuePosition"
      )
    );

    assert.match(
      server,
      /build\.queue\s*=\s*await\s+buildQueuePosition/
    );
  }
);
