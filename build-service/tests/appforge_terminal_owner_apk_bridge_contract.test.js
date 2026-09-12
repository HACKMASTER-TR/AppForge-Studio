import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const read = path =>
  readFile(
    new URL(`../../${path}`, import.meta.url),
    "utf8"
  );

test(
  "appforge-apk and AppForge owner files share the workspace-root bridge",
  async () => {
    const [screen, owner, apk] =
      await Promise.all([
        read(
          "android-app/app/src/main/java/com/appforge/studio/terminal/TerminalWorkspaceScreen.kt"
        ),
        read(
          "android-app/app/src/main/java/com/appforge/studio/terminal/OwnerFilesPanel.kt"
        ),
        read(
          "android-app/app/src/main/assets/terminal/appforge-apk"
        )
      ]);

    // Linux helper always publishes into the selected /workspace root.
    assert.match(
      apk,
      /download_dir="\/workspace\/AppForgeDownloads"/
    );

    // Owner import MUST use selected workspace root.
    assert.match(
      screen,
      /OwnerFilesPanel\([\s\S]*?legacyWorkspace\s*=\s*workspace\s*\)/
    );

    // Never follow the PTY's current subdirectory for APK migration.
    assert.doesNotMatch(
      screen,
      /OwnerFilesPanel\([\s\S]*?legacyWorkspace\s*=\s*filesWorkspace\s*\)/
    );

    // Workspace AppForgeDownloads is migrated into the protected APK vault.
    assert.match(
      owner,
      /File\(\s*legacy,\s*"AppForgeDownloads"\s*\)/
    );

    assert.match(
      owner,
      /OwnerAccessPolicy[\s\S]*?\.apkRoot\(/
    );

    assert.match(
      owner,
      /moveLegacyItem\(\s*source,\s*apkRoot\s*\)/
    );
  }
);
