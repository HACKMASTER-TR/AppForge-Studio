import test from "node:test";
import assert from "node:assert/strict";

import {
  applyNodeRemoteBackendConfig
} from "../src/nodeRemoteBackendEngine.js";

test(
  "Node remote contract becomes URL-mode build config",
  () => {
    const config = {
      sourceMode:
        "LOCAL",
      sourceBuildEngine:
        "remote-backend",
      sourceTechnology:
        "nodejs",
      webUrl:
        ""
    };

    const result =
      applyNodeRemoteBackendConfig(
        config,
        {
          framework:
            "express",
          contract: {
            backendUrl:
              "https://api.example.com/app/",
            healthPath:
              "/health",
            openExternalLinks:
              true
          }
        }
      );

    assert.equal(
      result.sourceMode,
      "URL"
    );

    assert.equal(
      result.webUrl,
      "https://api.example.com/app/"
    );

    assert.deepEqual(
      result.nodeRemoteBackend,
      {
        framework:
          "express",
        backendUrl:
          "https://api.example.com/app/",
        healthPath:
          "/health",
        openExternalLinks:
          true
      }
    );
  }
);

test(
  "Node live router refuses missing contract",
  () => {
    assert.throws(
      () =>
        applyNodeRemoteBackendConfig(
          {
            sourceMode:
              "LOCAL"
          },
          {
            framework:
              "nodejs",
            contract:
              null
          }
        ),
      /kontratı hazır değil/
    );
  }
);
