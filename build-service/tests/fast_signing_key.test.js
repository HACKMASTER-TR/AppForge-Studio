import test from "node:test";
import assert from "node:assert/strict";
import { promises as fs } from "fs";
import os from "os";
import path from "path";

import {
  materializeFastDebugKeystore
} from "../src/fastSigningKey.js";

test(
  "materializes FAST debug keystore from base64",
  async () => {
    const root =
      await fs.mkdtemp(
        path.join(
          os.tmpdir(),
          "appforge-signing-"
        )
      );

    const target =
      path.join(
        root,
        "debug.keystore"
      );

    const original =
      Buffer.alloc(1024, 0x5a);

    const result =
      await materializeFastDebugKeystore({
        base64:
          original.toString("base64"),
        targetPath: target
      });

    assert.equal(
      result.materialized,
      true
    );

    const written =
      await fs.readFile(target);

    assert.deepEqual(
      written,
      original
    );

    const stat =
      await fs.stat(target);

    assert.equal(
      stat.mode & 0o777,
      0o600
    );

    await fs.rm(
      root,
      {
        recursive: true,
        force: true
      }
    );
  }
);

test(
  "does nothing when signing secret is absent",
  async () => {
    const result =
      await materializeFastDebugKeystore({
        base64: "",
        targetPath:
          "/tmp/not-created-debug.keystore"
      });

    assert.equal(
      result.materialized,
      false
    );
  }
);
