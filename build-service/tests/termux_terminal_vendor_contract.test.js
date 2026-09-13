import test from "node:test";
import assert from "node:assert/strict";
import { promises as fs } from "fs";
import path from "path";
import { fileURLToPath } from "url";

const here = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(here, "..", "..");

test("Termux terminal core vendor contract", async () => {
  const settings = await fs.readFile(
    path.join(repoRoot, "android-app", "settings.gradle.kts"),
    "utf8",
  );

  const rootGradle = await fs.readFile(
    path.join(repoRoot, "android-app", "build.gradle.kts"),
    "utf8",
  );

  const version = (
    await fs.readFile(
      path.join(
        repoRoot,
        "third_party",
        "termux-terminal",
        "SOURCE_VERSION",
      ),
      "utf8",
    )
  ).trim();

  assert.equal(
    version,
    "4584488513c099f2e98bcfcd00f006d213248729",
  );

  assert.match(
    settings,
    /include\(":termux-terminal-emulator"\)/,
  );

  assert.match(
    settings,
    /include\(":termux-terminal-view"\)/,
  );

  assert.match(
    rootGradle,
    /id\("com\.android\.library"\).*9\.1\.1/,
  );

  await fs.access(
    path.join(
      repoRoot,
      "android-app",
      "termux-terminal-emulator",
      "src",
      "main",
      "java",
    ),
  );

  await fs.access(
    path.join(
      repoRoot,
      "android-app",
      "termux-terminal-view",
      "src",
      "main",
      "java",
    ),
  );

  await fs.access(
    path.join(
      repoRoot,
      "third_party",
      "termux-terminal",
      "TERMUX_LICENSE.md",
    ),
  );
});
