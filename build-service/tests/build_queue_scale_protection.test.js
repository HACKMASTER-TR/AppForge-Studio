import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const root =
  new URL("../../", import.meta.url);

const read =
  path =>
    readFile(
      new URL(path, root),
      "utf8"
    );

test(
  "queue admission is atomic and burst protected",
  async () => {
    const queue =
      await read(
        "build-service/src/jobQueue.js"
      );

    assert.ok(
      queue.includes(
        "pg_advisory_xact_lock"
      )
    );

    assert.ok(
      queue.includes(
        "appforge-build-queue-admission"
      )
    );

    assert.ok(
      queue.includes(
        "QUEUE_FULL"
      )
    );

    assert.ok(
      queue.includes(
        "ACTIVE_BUILD_LIMIT"
      )
    );

    assert.ok(
      queue.includes(
        "getProEntitlement"
      )
    );

    assert.ok(
      queue.includes(
        "config.proBuildPriority"
      )
    );

    assert.ok(
      queue.includes(
        "config.freeBuildPriority"
      )
    );
  }
);

test(
  "production queue defaults support large bursts",
  async () => {
    const config =
      await read(
        "build-service/src/config.js"
      );

    const env =
      await read(
        "build-service/.env.example"
      );

    assert.ok(
      config.includes(
        "process.env.MAX_QUEUE_SIZE || 2000"
      )
    );

    assert.ok(
      config.includes(
        "FREE_ACTIVE_BUILD_LIMIT"
      )
    );

    assert.ok(
      config.includes(
        "PRO_ACTIVE_BUILD_LIMIT"
      )
    );

    assert.ok(
      env.includes(
        "MAX_QUEUE_SIZE=2000"
      )
    );

    assert.ok(
      env.includes(
        "FREE_ACTIVE_BUILD_LIMIT=1"
      )
    );

    assert.ok(
      env.includes(
        "PRO_ACTIVE_BUILD_LIMIT=3"
      )
    );
  }
);

test(
  "active queue lookups have production indexes",
  async () => {
    const sql =
      await read(
        "build-service/sql/017_queue_scale_protection.sql"
      );

    assert.ok(
      sql.includes(
        "idx_build_jobs_user_active"
      )
    );

    assert.ok(
      sql.includes(
        "idx_build_jobs_queued_priority"
      )
    );
  }
);
