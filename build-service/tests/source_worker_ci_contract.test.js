import test from "node:test";
import assert from "node:assert/strict";
import {
  promises as fs
} from "fs";
import path from "path";
import {
  fileURLToPath
} from "url";

const here =
  path.dirname(
    fileURLToPath(
      import.meta.url
    )
  );

const repoRoot =
  path.resolve(
    here,
    "..",
    ".."
  );

test(
  "source worker CI builds and smokes the dedicated image",
  async () => {
    const workflow =
      await fs.readFile(
        path.join(
          repoRoot,
          ".github",
          "workflows",
          "source-worker-image.yml"
        ),
        "utf8"
      );

    for (
      const marker of [
        "Dockerfile.source-worker",
        "load: true",
        "ANDROID_SDK_LICENSE_ACCEPTED=true",
        "10001:10001",
        "--network none",
        "--read-only",
        "--cap-drop ALL",
        "no-new-privileges:true",
        "--pids-limit 256",
        "source-worker-runtime-smoke.sh",
        "ghcr.io/hackmaster-tr/appforge-source-worker",
        "docker push"
      ]
    ) {
      assert.ok(
        workflow.includes(
          marker
        ),
        marker
      );
    }
  }
);

test(
  "source worker runtime smoke verifies non-root and isolation attestation",
  async () => {
    const smoke =
      await fs.readFile(
        path.join(
          repoRoot,
          "build-service",
          "scripts",
          "source-worker-runtime-smoke.sh"
        ),
        "utf8"
      );

    for (
      const marker of [
        'test "$uid" = "10001"',
        'test "$gid" = "10001"',
        "SOURCE_BUILD_ISOLATION_MODE",
        "SOURCE_BUILD_REQUIRE_ISOLATION",
        "source-isolation-dedicated",
        "assertSourceBuildIsolation",
        "SOURCE_WORKER_RUNTIME_SMOKE_OK"
      ]
    ) {
      assert.ok(
        smoke.includes(
          marker
        ),
        marker
      );
    }
  }
);
