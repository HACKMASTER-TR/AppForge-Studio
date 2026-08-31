import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const backup =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/io/ProjectBackupManager.kt",
    import.meta.url
  );

const main =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
    import.meta.url
  );

test(
  "backup importer supports single and library ZIP formats",
  async () => {
    const text =
      await readFile(
        backup,
        "utf8"
      );

    assert.match(
      text,
      /fun importManyFromUri\(/
    );

    assert.match(
      text,
      /projects\//
    );

    assert.match(
      text,
      /sourcePrefix/
    );
  }
);

test(
  "single project importer remains backward compatible",
  async () => {
    const text =
      await readFile(
        backup,
        "utf8"
      );

    assert.match(
      text,
      /fun importFromUri\(/
    );

    assert.match(
      text,
      /hasRootProject/
    );
  }
);

test(
  "Android import launcher saves multiple projects",
  async () => {
    const text =
      await readFile(
        main,
        "utf8"
      );

    assert.match(
      text,
      /importManyFromUri/
    );

    assert.match(
      text,
      /importedCount/
    );

    assert.match(
      text,
      /proje başarıyla içe aktarıldı/
    );
  }
);
