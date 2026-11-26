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
  "runtime revision mismatch stages replacement without erasing the active build runtime",
  () => {
    const runtime =
      read(
        "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildRuntimeV3.kt"
      );

    // Diagnostic scratch cleanup is allowed only under the app cache. Active
    // runtimes, project trees and restore generations must never be deleted here.
    const deletes=runtime.match(/\w+\.deleteRecursively\(\)/g)??[];
    assert.ok(deletes.every(call=>call==='workspace.deleteRecursively()'));
    assert.match(runtime,/workspace\.canonicalFile\.parentFile==\s*app\.cacheDir\.canonicalFile/);
    assert.doesNotMatch(runtime, /(?:rootfsDirectory|runtimeDirectory|current|active|root)\.deleteRecursively\(/);
    const installer=read("android-app/app/src/main/java/com/appforge/studio/terminal/VerifiedLinuxRootfsInstaller.kt");
    assert.match(installer,/RootfsActivationTransaction/);

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
      /READY="\$ROOT\/\.ready-v5"/
    );
  }
);
