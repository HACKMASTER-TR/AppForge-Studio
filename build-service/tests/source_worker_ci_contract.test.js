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

test(
  "dedicated source worker Dockerfile uses Corepack and a single source command",
  async () => {
    const docker =
      await fs.readFile(
        path.join(
          repoRoot,
          "build-service",
          "Dockerfile.source-worker"
        ),
        "utf8"
      );

    for (
      const marker of [
        "corepack prepare yarn@1.22.22 --activate",
        "corepack prepare pnpm@10.17.1 --activate",
        'CMD ["npm", "run", "worker:source"]'
      ]
    ) {
      assert.ok(
        docker.includes(
          marker
        ),
        marker
      );
    }

    assert.equal(
      docker.includes(
        "npm install -g yarn@1.22.22"
      ),
      false,
      "Node 22 image içinde mevcut Yarn shim'i npm -g ile ezilmemeli."
    );

    assert.equal(
      docker.includes(
        'CMD ["npm", "run", "worker"]'
      ),
      false,
      "Dedicated source image içinde normal Worker CMD kalmamalı."
    );

    const sourceCmdCount =
      docker
        .split(
          'CMD ["npm", "run", "worker:source"]'
        )
        .length -
      1;

    assert.equal(
      sourceCmdCount,
      1,
      "Dedicated source image tam bir worker:source CMD içermeli."
    );
  }
);

test(
  "worker images keep Android API 36 for .NET Android and API 37 for AppForge builds",
  async () => {
    for (
      const dockerName of [
        "Dockerfile.worker",
        "Dockerfile.source-worker"
      ]
    ) {
      const docker =
        await fs.readFile(
          path.join(
            repoRoot,
            "build-service",
            dockerName
          ),
          "utf8"
        );

      assert.ok(
        docker.includes(
          '"platforms;android-36"'
        ),
        `${dockerName}: Android API 36 eksik`
      );

      assert.ok(
        docker.includes(
          '"platforms;android-37.0"'
        ),
        `${dockerName}: Android API 37.0 eksik`
      );
    }
  }
);

test(
  "technology matrix stays in repository CI and outside build-service-only Docker context",
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

    const matrixCommand =
      "node --test tests/technology_support_matrix.test.js";

    assert.ok(
      workflow.includes(
        matrixCommand
      ),
      "Technology matrix GitHub Actions regression aşamasında kalmalı."
    );

    for (
      const dockerName of [
        "Dockerfile.worker",
        "Dockerfile.source-worker"
      ]
    ) {
      const docker =
        await fs.readFile(
          path.join(
            repoRoot,
            "build-service",
            dockerName
          ),
          "utf8"
        );

      assert.equal(
        docker.includes(
          `RUN ${matrixCommand}`
        ),
        false,
        `${dockerName}: build-service-only Docker context cross-repo matrix testini çalıştırmamalı.`
      );
    }
  }
);
