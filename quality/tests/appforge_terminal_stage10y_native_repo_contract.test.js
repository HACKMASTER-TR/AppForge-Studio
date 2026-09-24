import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const asset = (name) =>
  new URL(
    `../../android-app/app/src/main/assets/terminal/${name}`,
    import.meta.url
  );

const prorootUrl =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/terminal/ProrootRuntimeContract.kt",
    import.meta.url
  );

test("Stage 10Y keeps Git development repository on native Linux filesystem", async () => {
  const source =
    await readFile(
      asset("appforge-project"),
      "utf8"
    );

  assert.match(
    source,
    /repo="\/root\/AppForge-Studio"/
  );

  assert.doesNotMatch(
    source,
    /repo="\/workspace\/AppForge-Studio"/
  );

  assert.match(
    source,
    /git clone[\s\S]*?"\$url"[\s\S]*?"\$repo"/
  );

  assert.match(
    source,
    /partial\.\$\(date \+%s\)/
  );
});

test("Stage 10Y preserves Android Files exchange workspace bind", async () => {
  const source =
    await readFile(
      prorootUrl,
      "utf8"
    );

  assert.match(
    source,
    /\$\{safeWorkspace\.absolutePath\}:\/workspace/
  );
});

test("Stage 10Y doctor and readiness detect native repository outside current cwd", async () => {
  for (const name of [
    "appforge-doctor",
    "appforge-ready"
  ]) {
    const source =
      await readFile(
        asset(name),
        "utf8"
      );

    assert.match(
      source,
      /native_repo="\/root\/AppForge-Studio"/
    );

    assert.match(
      source,
      /git -C "\$native_repo"/
    );
  }
});

test("Stage 10Y test CI and APK helpers use native repository fallback", async () => {
  for (const name of [
    "appforge-test",
    "appforge-ci",
    "appforge-apk"
  ]) {
    const source =
      await readFile(
        asset(name),
        "utf8"
      );

    assert.match(
      source,
      /native_repo="\/root\/AppForge-Studio"/
    );

    assert.match(
      source,
      /\[ -d "\$native_repo\/\.git" \]/
    );
  }
});

test("Stage 10Y readiness executes Git checks against resolved repository", async () => {
  const source =
    await readFile(
      asset("appforge-ready"),
      "utf8"
    );

  assert.match(
    source,
    /git -C "\$repo_root"[\s\S]*?diff[\s\S]*?--check/
  );

  assert.match(
    source,
    /git -C "\$repo_root"[\s\S]*?ls-remote/
  );

  assert.match(
    source,
    /git -C "\$repo_root"[\s\S]*?push[\s\S]*?--dry-run/
  );
});
