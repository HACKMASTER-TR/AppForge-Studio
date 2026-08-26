import test from "node:test";
import assert from "node:assert/strict";

import {
  applyPhpRemoteBackendConfig
} from "../src/phpRemoteBackendEngine.js";

test(
  "PHP remote contract becomes trusted URL-mode build config",
  () => {
    const config =
      {
        sourceMode:
          "LOCAL",
        sourceBuildEngine:
          "remote-backend",
        sourceTechnology:
          "php",
        webUrl:
          ""
      };

    const result =
      applyPhpRemoteBackendConfig(
        config,
        {
          framework:
            "laravel",
          contract: {
            backendUrl:
              "https://example.com/app/",
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
      "https://example.com/app/"
    );

    assert.deepEqual(
      result.phpRemoteBackend,
      {
        framework:
          "laravel",
        backendUrl:
          "https://example.com/app/",
        healthPath:
          "/health",
        openExternalLinks:
          true
      }
    );
  }
);

test(
  "PHP live router refuses missing remote contract",
  () => {
    assert.throws(
      () =>
        applyPhpRemoteBackendConfig(
          {
            sourceMode:
              "LOCAL"
          },
          {
            framework:
              "php",
            contract:
              null
          }
        ),
      /kontratı hazır değil/
    );
  }
);
