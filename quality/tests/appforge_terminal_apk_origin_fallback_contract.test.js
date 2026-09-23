import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const sourceUrl =
  new URL(
    "../../android-app/app/src/main/assets/terminal/appforge-apk",
    import.meta.url
  );

test(
  "appforge-apk falls back from a scratch repo without GitHub origin",
  async () => {
    const source = await readFile(sourceUrl, "utf8");

    assert.match(
      source,
      /native_repo="\/root\/AppForge-Studio"/
    );

    assert.match(
      source,
      /is_github_origin\(\)/
    );

    assert.match(
      source,
      /https:\/\/github\.com\/\*\|git@github\.com:\*\|ssh:\/\/git@github\.com\/\*/
    );

    assert.match(
      source,
      /if ! is_github_origin "\$origin" &&[\s\S]*?"\$repo_root" != "\$native_repo"/
    );

    assert.match(
      source,
      /native_origin="\$\([\s\S]*?git -C "\$native_repo"[\s\S]*?remote get-url origin/
    );

    assert.match(
      source,
      /if is_github_origin "\$native_origin"; then[\s\S]*?repo_root="\$native_repo"[\s\S]*?origin="\$native_origin"/
    );

    assert.match(
      source,
      /if ! is_github_origin "\$origin"; then[\s\S]*?GitHub origin bulunamadı/
    );

    // Var olan indirme köprüsü değişmemeli.
    assert.match(
      source,
      /download_dir="\/workspace\/AppForgeDownloads"/
    );
  }
);
