import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here =
  path.dirname(
    fileURLToPath(import.meta.url)
  );

const repo =
  path.resolve(
    here,
    "../.."
  );

const read = relative =>
  fs.readFileSync(
    path.join(repo, relative),
    "utf8"
  );

test(
  "device build uses dedicated versioned rootfs",
  () => {
    const runtime =
      read(
        "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildRuntimeV3.kt"
      );

    assert.match(
      runtime,
      /device-build\/runtime-v3/
    );

    assert.match(
      runtime,
      /device-build-runtime-v3/
    );

    assert.doesNotMatch(
      runtime,
      /terminal\/linux/
    );
  }
);

test(
  "device engine no longer reuses Terminal runtime manager",
  () => {
    const engine =
      read(
        "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"
      );

    assert.match(
      engine,
      /DeviceBuildRuntimeV3/
    );

    assert.doesNotMatch(
      engine,
      /AndroidLinuxRuntimeManager/
    );

    assert.doesNotMatch(
      engine,
      /ensureBaseEnvironment/
    );
  }
);

test(
  "runtime revision mismatch rebuilds only build runtime",
  () => {
    const runtime =
      read(
        "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildRuntimeV3.kt"
      );

    assert.match(
      runtime,
      /runtimeDirectory[\s\S]*deleteRecursively/
    );

    assert.match(
      runtime,
      /VerifiedLinuxRootfsInstaller/
    );

    assert.match(
      runtime,
      /LinuxRootfsMetadataCodec/
    );
  }
);

test(
  "toolchain revision follows clean runtime v3",
  () => {
    const installer =
      read(
        "android-app/app/src/main/assets/device-build/install-toolchain.sh"
      );

    assert.match(
      installer,
      /READY="\$ROOT\/\.ready-v4"/
    );
  }
);
