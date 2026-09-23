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
  "Source Worker CI remains retired in device-only mode",
  async () => {
    const workflow = new URL(
      "../../.github/workflows/source-worker-image.yml",
      import.meta.url
    );

    await assert.rejects(
      fs.access(workflow),
      { code: "ENOENT" }
    );
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
    const workerDocker =
      await fs.readFile(
        path.join(
          repoRoot,
          "build-service",
          "Dockerfile.worker"
        ),
        "utf8"
      );

    assert.ok(
      workerDocker.includes(
        '"platforms;android-36"'
      ),
      "Dockerfile.worker: Android API 36 eksik"
    );

    assert.ok(
      workerDocker.includes(
        '"platforms;android-37.0"'
      ),
      "Dockerfile.worker: Android API 37.0 eksik"
    );

    const matrix =
      JSON.parse(
        await fs.readFile(
          path.join(
            repoRoot,
            "build-service",
            "source-worker-toolchain.json"
          ),
          "utf8"
        )
      );

    assert.ok(
      matrix.androidPlatforms.includes(
        "36"
      ),
      "Source Worker matrix: Android API 36 eksik"
    );

    assert.ok(
      matrix.androidPlatforms.includes(
        "37.0"
      ),
      "Source Worker matrix: Android API 37.0 eksik"
    );
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
        "mkdir -p /opt/appforge-flutter-tool-pub-cache",
        "cd /opt/flutter/packages/flutter_tools",
        "PUB_CACHE=/opt/appforge-flutter-tool-pub-cache dart pub get",
        "test -s .dart_tool/package_config.json",
        "! grep -F '/root/' .dart_tool/package_config.json",
        "file:///opt/appforge-flutter-tool-pub-cache/",
        "chmod -R a+rX",
        "chmod -R a-w /opt/appforge-flutter-tool-pub-cache"
      ]
    ) {
      assert.ok(
        docker.includes(marker),
        marker
      );
    }

    for (
      const marker of [
        "AppForge hardened read-only SDK guard",
        'desired_realm="${FLUTTER_REALM:-}"',
        "engine.stamp",
        "engine.realm",
        "APPFORGE_FLUTTER_READONLY_GUARD_OK",
        'stamp_file="$flutter_root/bin/cache/$artifact.stamp"',
        'version_file="$flutter_root/bin/internal/$artifact.version"',
        "libimobiledevice",
        "idevicescreenshot",
        "idevicesyslog",
        "libusbmuxd",
        "iproxy",
        "ios-deploy",
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

    for (
      const marker of [
        "Flutter failed to write",
        "Read-only file system",
        "Permission denied",
        "flutter_tool_package_config_readable",
        "flutter_tool_package_config_immutable_cache",
        "/opt/appforge-flutter-tool-pub-cache",
        "/root/.pub-cache",
        "TOOL_OK: flutter_pub_get_readonly_cache"
      ]
    ) {
      assert.ok(
        smoke.includes(marker),
        marker
      );
    }
  }
);
