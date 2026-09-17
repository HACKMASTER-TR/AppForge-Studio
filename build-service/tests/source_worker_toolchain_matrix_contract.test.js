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

const serviceRoot =
  path.resolve(
    here,
    ".."
  );

test(
  "Source Worker compatibility matrix covers supported Android source toolchains",
  async () => {
    const matrix =
      JSON.parse(
        await fs.readFile(
          path.join(
            serviceRoot,
            "source-worker-toolchain.json"
          ),
          "utf8"
        )
      );

    assert.equal(
      matrix.schemaVersion,
      1
    );

    for (
      const version of [
        "34",
        "35",
        "36",
        "37.0"
      ]
    ) {
      assert.ok(
        matrix.androidPlatforms.includes(
          version
        ),
        `Android platform ${version}`
      );
    }

    for (
      const version of [
        "34.0.0",
        "35.0.0",
        "36.0.0"
      ]
    ) {
      assert.ok(
        matrix.buildTools.includes(
          version
        ),
        `Build Tools ${version}`
      );
    }

    for (
      const version of [
        "26.1.10909125",
        "27.1.12297006",
        "28.2.13676358"
      ]
    ) {
      assert.ok(
        matrix.ndk.includes(
          version
        ),
        `NDK ${version}`
      );
    }

    assert.ok(
      matrix.cmake.includes(
        "3.22.1"
      )
    );

    assert.ok(
      matrix.gradle.includes(
        "8.14.3"
      )
    );

    assert.ok(
      matrix.gradle.includes(
        "9.3.1"
      )
    );
  }
);

test(
  "Source Worker image installs matrix before immutable runtime",
  async () => {
    const docker =
      await fs.readFile(
        path.join(
          serviceRoot,
          "Dockerfile.source-worker"
        ),
        "utf8"
      );

    for (
      const marker of [
        "COPY source-worker-toolchain.json /tmp/source-worker-toolchain.json",
        "appforge-source-sdk-packages.txt",
        "sdkmanager --channel=3",
        "source-worker-toolchain-doctor.js --strict",
        "USER 10001:10001"
      ]
    ) {
      assert.ok(
        docker.includes(
          marker
        ),
        marker
      );
    }
  }
);

test(
  "read-only runtime validates full Source Worker toolchain matrix",
  async () => {
    const smoke =
      await fs.readFile(
        path.join(
          serviceRoot,
          "scripts",
          "source-worker-runtime-smoke.sh"
        ),
        "utf8"
      );

    assert.match(
      smoke,
      /source-worker-toolchain-doctor\.js --strict --runtime/
    );
  }
);
