import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const runtimePath =
  "../../android-app/app/src/main/java/com/appforge/studio/terminal/LinuxRuntimeFoundation.kt";

const readRuntime = () =>
  readFile(
    new URL(runtimePath, import.meta.url),
    "utf8"
  );

test(
  "Terminal pins Ubuntu Base 24.04.4 instead of incompatible 26.04",
  async () => {
    const source = await readRuntime();

    assert.match(
      source,
      /private const val UBUNTU_RELEASE\s*=\s*"24\.04\.4"/
    );

    assert.match(
      source,
      /https:\/\/cdimage\.ubuntu\.com\/ubuntu-base\/releases\/24\.04\/release/
    );

    assert.doesNotMatch(
      source,
      /ubuntu-base-26\.04/
    );

    assert.doesNotMatch(
      source,
      /"26\.04"/
    );
  }
);

test(
  "Ubuntu Base 24.04.4 archives remain pinned to official SHA-256 values",
  async () => {
    const source = await readRuntime();

    const expected = [
      [
        "ubuntu-base-24.04.4-base-arm64.tar.gz",
        "04207713ece899c3740823d33690441ad3a7f0ded1101aca744e2b0f37ac7ff2"
      ],
      [
        "ubuntu-base-24.04.4-base-armhf.tar.gz",
        "991520b47f6586f38a78505cf016e300b6191bb8ff86a0723481ec23a37ab7f4"
      ],
      [
        "ubuntu-base-24.04.4-base-amd64.tar.gz",
        "c1e67ef7b17a6300e136118bd1dc04725009cb376c1aad10abcf8cd453628d58"
      ]
    ];

    for (const [fileName, sha256] of expected) {
      assert.ok(
        source.includes(fileName),
        `missing ${fileName}`
      );

      assert.ok(
        source.includes(sha256),
        `missing SHA-256 for ${fileName}`
      );
    }
  }
);
