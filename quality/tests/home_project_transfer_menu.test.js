import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const home =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/StudioHomeScreen.kt",
    import.meta.url
  );

const main =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
    import.meta.url
  );

const backup =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/io/ProjectBackupManager.kt",
    import.meta.url
  );

test(
  "home more menu exposes project transfer actions",
  async () => {
    const text =
      await readFile(home, "utf8");

    assert.match(
      text,
      /Proje İçe Aktar \(ZIP\)/
    );

    assert.match(
      text,
      /Tüm Projeleri Dışa Aktar \(ZIP\)/
    );

    assert.match(
      text,
      /Tümünü Android Projesi Olarak Dışa Aktar \(ZIP\)/
    );
  }
);

test(
  "home project transfer actions are wired",
  async () => {
    const text =
      await readFile(main, "utf8");

    assert.match(
      text,
      /allProjectsExportLauncher/
    );

    assert.match(
      text,
      /allAndroidProjectsExportLauncher/
    );

    assert.match(
      text,
      /backupImportLauncher/
    );
  }
);

test(
  "backup manager supports library and Android source export",
  async () => {
    const text =
      await readFile(backup, "utf8");

    assert.match(
      text,
      /fun exportAllProjectsToUri\(/
    );

    assert.match(
      text,
      /fun exportAllAndroidProjectsToUri\(/
    );

    assert.match(
      text,
      /settings\.gradle\.kts/
    );

    assert.match(
      text,
      /AndroidManifest\.xml/
    );
  }
);
