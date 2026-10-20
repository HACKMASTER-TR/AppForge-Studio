import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(
  fileURLToPath(import.meta.url)
);

const repo = path.resolve(
  here,
  "../.."
);

const installer = fs.readFileSync(
  path.join(
    repo,
    "android-app/app/src/main/assets/device-build/install-toolchain.sh"
  ),
  "utf8"
);

test(
  "ARM64 uses pinned Linux-glibc build tools",
  () => {
    assert.match(
      installer,
      /Commit451\/android-arm-build-tools/
    );

    assert.match(
      installer,
      /platform-tools-36\.0\.0/
    );

    assert.doesNotMatch(
      installer,
      /Qjj7679\/build-tools-36-aarch64/
    );
  }
);

test(
  "all native ARM64 tools are pinned",
  () => {
    const digests = [
      "7512ff7e381bea6fd310b6f6e347422c8fda21e07c6e3f0162742e95eb9d7f98",
      "8c97356b8bba8f7aad44cfd408e3ee24c66a1258244b5d08af1c9249f50dd659",
      "e8856fb24b10095eb6e940c577ce96d89ddbfc45aa0f7eeaef1597ef68f11a12",
      "fb7f0c3c87dbd4243d7e1277ba64ded2399389244058b6a4c969cc615360766c"
    ];

    for (const digest of digests) {
      assert.ok(
        installer.includes(digest),
        `Missing pinned digest: ${digest}`
      );
    }

    for (
      const tool of [
        "aapt2",
        "aidl",
        "zipalign",
        "split-select"
      ]
    ) {
      assert.match(
        installer,
        new RegExp(
          `install_arm64_tool \\\\?\\n?\\s*${tool}`
        )
      );
    }
  }
);

test(
  "old toolchain cannot be accepted",
  () => {
    assert.match(
      installer,
      /READY="\$ROOT\/\.ready-v5"/
    );

    assert.match(
      installer,
      /aapt2" version/
    );
  }
);

test(
  "SDK 37 resource parsing is tested before READY",
  () => {
    assert.match(
      installer,
      /APPFORGE_AAPT2_PLATFORM_37_SMOKE_START/
    );

    assert.match(
      installer,
      /dump resources/
    );

    assert.match(
      installer,
      /APPFORGE_AAPT2_PLATFORM_37_SMOKE_PASS/
    );

    assert.ok(
      installer.indexOf(
        "APPFORGE_AAPT2_PLATFORM_37_SMOKE_PASS"
      ) <
      installer.lastIndexOf(
        'touch "$READY"'
      )
    );
  }
);

test(
  "host architecture remains explicit",
  () => {
    assert.match(
      installer,
      /aarch64\|arm64/
    );

    assert.match(
      installer,
      /x86_64\|amd64/
    );

    assert.match(
      installer,
      /APPFORGE_AAPT2_ABI_MISMATCH/
    );
  }
);


test(
  "AGP 9.1 API 37 uses the android-37.0 platform layout",
  () => {
    assert.match(
      installer,
      /platforms\/android-37\.0/
    );

    assert.doesNotMatch(
      installer,
      /platforms\/android-37\//
    );

    assert.match(
      installer,
      /READY="\$ROOT\/\.ready-v5"/
    );

    assert.match(
      installer,
      /android-37\.0\/android\.jar/
    );

    assert.match(
      installer,
      /android-37\.0\/source\.properties/
    );
  }
);
