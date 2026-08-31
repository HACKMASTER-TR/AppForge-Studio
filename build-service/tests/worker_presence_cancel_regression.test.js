import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const read = path =>
  readFile(
    new URL(
      path,
      import.meta.url
    ),
    "utf8"
  );

test(
  "idle workers refresh their presence",
  async () => {
    const queue =
      await read(
        "../src/jobQueue.js"
      );

    const runtime =
      await read(
        "../src/workerRuntime.js"
      );

    assert.match(
      queue,
      /export async function touchWorker/
    );

    assert.match(
      queue,
      /last_seen_at = NOW\(\)/
    );

    assert.match(
      runtime,
      /touchWorker/
    );

    assert.match(
      runtime,
      /presenceTimers/
    );

    assert.match(
      runtime,
      /presenceEveryMs/
    );
  }
);

test(
  "cancel request also cancels requeued orphan jobs",
  async () => {
    const queue =
      await read(
        "../src/jobQueue.js"
      );

    assert.match(
      queue,
      /queuedCancellation/
    );

    assert.match(
      queue,
      /queuedCancellation\.rowCount > 0/
    );

    assert.match(
      queue,
      /AND status = 'queued'/
    );

    assert.match(
      queue,
      /status = 'cancelled'/
    );
  }
);
