import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const files = [
  "appforge_bug7e_terminal_build_apk_home_contract.test.js",
  "appforge_apk_share_contract.test.js",
  "appforge_apk_public_download_contract.test.js",
];

test(
  "successful builds contracts do not regress to APK-only assumptions",
  async () => {
    for (const name of files) {
      const source = await readFile(
        new URL(name, import.meta.url),
        "utf8"
      );

      assert.doesNotMatch(
        source,
        /shareDownloadedApk/
      );

      assert.doesNotMatch(
        source,
        /endsWith\\("\\\\.apk",true\\)/
      );

      assert.doesNotMatch(
        source,
        /APKs only/i
      );
    }
  }
);
