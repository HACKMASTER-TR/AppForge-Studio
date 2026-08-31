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
        "uid=10001,gid=10001,mode=0700",
        "uid=10001,gid=10001,mode=0750",
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
        "SOURCE_WORKER_RUNTIME_SMOKE_FAIL",
        "SOURCE_WORKER_TOOLCHAIN_SMOKE_OK",
        "run_tool node node --version",
        "run_tool python python3 --version",
        "run_tool java java -version",
        "run_tool gradle gradle --version",
        "run_tool dotnet dotnet --info",
        "run_tool flutter flutter --no-version-check --version",
        "run_tool cmake cmake --version",
        "run_tool ninja ninja --version",
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

test(
  "source worker cache hardening keeps mutable caches UID-specific",
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

    const docker =
      await fs.readFile(
        path.join(
          repoRoot,
          "build-service",
          "Dockerfile.source-worker"
        ),
        "utf8"
      );

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
        "--tmpfs /app/user-cache/10001:",
        "mode=0700,size=1024m"
      ]
    ) {
      assert.ok(
        workflow.includes(marker),
        marker
      );
    }

    for (
      const marker of [
        "COREPACK_HOME=/opt/appforge-corepack",
        "COREPACK_ENABLE_NETWORK=0",
        "APPFORGE_USER_CACHE_ROOT=/app/user-cache/10001",
        "GRADLE_USER_HOME=/app/user-cache/10001/gradle",
        "NPM_CONFIG_CACHE=/app/user-cache/10001/npm",
        "PIP_CACHE_DIR=/app/user-cache/10001/pip",
        "DOTNET_CLI_HOME=/app/user-cache/10001/dotnet",
        "NUGET_PACKAGES=/app/user-cache/10001/nuget",
        "PUB_CACHE=/app/user-cache/10001/pub",
        "FLUTTER_ALREADY_LOCKED=true"
      ]
    ) {
      assert.ok(
        docker.includes(marker),
        marker
      );
    }

    for (
      const marker of [
        "SOURCE_WORKER_CACHE_HARDENING_OK",
        "ensure_cache_dir",
        "cache_owner",
        "cache_mode",
        "COREPACK_ENABLE_NETWORK",
        'code="$?"'
      ]
    ) {
      assert.ok(
        smoke.includes(marker),
        marker
      );
    }
  }
);


test(
  "source worker CI contract stays outside build-service Docker context",
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

      assert.equal(
        docker.includes(
          "RUN node --test tests/source_worker_ci_contract.test.js"
        ),
        false,
        `${dockerName}: repository-level CI contract Docker context içinde çalıştırılmamalı.`
      );
    }
  }
);


test(
  "source worker keeps Flutter engine metadata compatible with read-only runtime",
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

    const patch =
      await fs.readFile(
        path.join(
          repoRoot,
          "build-service",
          "scripts",
          "patch-flutter-readonly-sdk.sh"
        ),
        "utf8"
      );

    assert.ok(
      docker.includes(
        "RUN sh scripts/patch-flutter-readonly-sdk.sh"
      ),
      "Flutter read-only patch Docker image build'inde uygulanmalı."
    );

    for (
      const marker of [
        "AppForge hardened read-only SDK guard",
        'desired_realm="${FLUTTER_REALM:-}"',
        "engine.stamp",
        "engine.realm",
        "APPFORGE_FLUTTER_READONLY_GUARD_OK",
        "libimobiledevice.stamp",
        "libimobiledevice.version",
        "idevicescreenshot",
        "idevicesyslog",
        "APPFORGE_FLUTTER_READONLY_UNIVERSAL_CACHE_OK"
      ]
    ) {
      assert.ok(
        patch.includes(marker),
        marker
      );
    }

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

    assert.ok(
      smoke.includes(
        "flutter --no-version-check pub get --offline"
      ),
      "Salt-okunur runtime smoke gerçek flutter pub get çalıştırmalı."
    );
  }
);
