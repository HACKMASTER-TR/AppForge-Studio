import test from "node:test";
import assert from "node:assert/strict";
import {
  promises as fs
} from "fs";
import path from "path";
import {
  fileURLToPath
} from "url";

import {
  requiredSourceWorkerCapabilities
} from "../src/sourceBuildIsolation.js";

test(
  "untrusted local source build receives isolation capability when required",
  () => {
    const capabilities =
      requiredSourceWorkerCapabilities(
        {
          payload: {
            config: {
              sourceMode:
                "LOCAL",
              sourceBuildEngine:
                "android-gradle"
            }
          },
          requiredCapabilities: [
            "android-api-37"
          ],
          requireIsolation:
            true,
          isolationCapability:
            "source-isolation-dedicated"
        }
      );

    assert.deepEqual(
      capabilities,
      [
        "android-api-37",
        "source-isolation-dedicated"
      ]
    );
  }
);

test(
  "existing capabilities are preserved and isolation capability is deduplicated",
  () => {
    const capabilities =
      requiredSourceWorkerCapabilities(
        {
          payload: {
            config: {
              sourceMode:
                "LOCAL",
              sourceBuildEngine:
                "flutter"
            }
          },
          requiredCapabilities: [
            "flutter",
            "source-isolation-dedicated",
            "source-isolation-dedicated"
          ],
          requireIsolation:
            true,
          isolationCapability:
            "source-isolation-dedicated"
        }
      );

    assert.deepEqual(
      capabilities,
      [
        "flutter",
        "source-isolation-dedicated"
      ]
    );
  }
);

test(
  "remote backend does not require source isolation capability",
  () => {
    const capabilities =
      requiredSourceWorkerCapabilities(
        {
          payload: {
            config: {
              sourceMode:
                "LOCAL",
              sourceBuildEngine:
                "remote-backend"
            }
          },
          requiredCapabilities: [],
          requireIsolation:
            true,
          isolationCapability:
            "source-isolation-dedicated"
        }
      );

    assert.deepEqual(
      capabilities,
      []
    );
  }
);

test(
  "URL mode does not require source isolation capability",
  () => {
    const capabilities =
      requiredSourceWorkerCapabilities(
        {
          payload: {
            config: {
              sourceMode:
                "URL",
              sourceBuildEngine:
                "node-web"
            }
          },
          requiredCapabilities: [],
          requireIsolation:
            true,
          isolationCapability:
            "source-isolation-dedicated"
        }
      );

    assert.deepEqual(
      capabilities,
      []
    );
  }
);

test(
  "compatibility mode keeps existing queue routing unchanged",
  () => {
    const capabilities =
      requiredSourceWorkerCapabilities(
        {
          payload: {
            config: {
              sourceMode:
                "LOCAL",
              sourceBuildEngine:
                "python-android"
            }
          },
          requiredCapabilities: [
            "python-3.11"
          ],
          requireIsolation:
            false,
          isolationCapability:
            "source-isolation-dedicated"
        }
      );

    assert.deepEqual(
      capabilities,
      [
        "python-3.11"
      ]
    );
  }
);

test(
  "job queue uses effective isolation capabilities for INSERT and event metadata",
  async () => {
    const here =
      path.dirname(
        fileURLToPath(
          import.meta.url
        )
      );

    const queueSource =
      await fs.readFile(
        path.resolve(
          here,
          "..",
          "src",
          "jobQueue.js"
        ),
        "utf8"
      );

    assert.ok(
      queueSource.includes(
        "requiredSourceWorkerCapabilities("
      )
    );

    assert.match(
      queueSource,
      /JSON\.stringify\(\s*effectiveRequiredCapabilities\s*\)/
    );

    assert.match(
      queueSource,
      /requiredCapabilities:\s*effectiveRequiredCapabilities/
    );
  }
);
