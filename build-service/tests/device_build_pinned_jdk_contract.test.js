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
  "device Android toolchain does not install OpenJDK through apt",
  () => {
    const source =
      read(
        "android-app/app/src/main/assets/device-build/install-toolchain.sh"
      );

    const installArea =
      source.slice(
        source.indexOf("apt-get update"),
        source.indexOf('case "$ENGINE" in')
      );

    assert.doesNotMatch(
      installArea,
      /openjdk-17-jdk-headless/
    );
  }
);

test(
  "device build uses checksum-pinned Temurin JDK 17",
  () => {
    const source =
      read(
        "android-app/app/src/main/assets/device-build/install-toolchain.sh"
      );

    assert.match(
      source,
      /OpenJDK17U-jdk_aarch64_linux_hotspot_17\.0\.20\.1_1\.tar\.gz/
    );

    assert.match(
      source,
      /457b57af8f9c93ec39080bb8c764f559dc8c89a6da1a39d718a400b7890d3e41/
    );

    assert.match(
      source,
      /OpenJDK17U-jdk_x64_linux_hotspot_17\.0\.20\.1_1\.tar\.gz/
    );

    assert.match(
      source,
      /3808d1d15e3ec6bd5b84057fb5d84c33d8a1536a258146bcea2e603fc726e08e/
    );
  }
);

test(
  "broken OpenJDK cleanup preserves healthy installed packages",
  () => {
    const source =
      read(
        "android-app/app/src/main/assets/device-build/install-toolchain.sh"
      );

    assert.match(
      source,
      /case "\$status" in[\s\S]*""\|ii\*\)[\s\S]*;;/
    );

    assert.match(
      source,
      /--purge[\s\S]*--force-all/
    );
  }
);

test(
  "Gradle always receives pinned device JAVA_HOME",
  () => {
    const source =
      read(
        "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"
      );

    assert.match(
      source,
      /JAVA_HOME=\/opt\/appforge-device\/jdk-17/
    );

    assert.match(
      source,
      /PATH=\/opt\/appforge-device\/jdk-17\/bin/
    );
  }
);

test(
  "new deterministic JDK invalidates old toolchain ready marker",
  () => {
    const source =
      read(
        "android-app/app/src/main/assets/device-build/install-toolchain.sh"
      );

    assert.match(
      source,
      /READY="\$ROOT\/\.ready-v5"/
    );

    assert.match(
      source,
      /\[ -x "\$JAVA_HOME\/bin\/javac" \]/
    );
  }
);
