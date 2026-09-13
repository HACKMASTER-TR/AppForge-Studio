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


test(
  "backup restore uses production build service fallback",
  async () => {
    const text =
      await readFile(
        backup,
        "utf8"
      );

    assert.match(
      text,
      /import com\.appforge\.studio\.model\.DEFAULT_BUILD_SERVICE_URL/
    );

    assert.match(
      text,
      /"buildServiceUrl",\s*DEFAULT_BUILD_SERVICE_URL/
    );

    assert.match(
      text,
      /\.ifBlank\s*\{\s*DEFAULT_BUILD_SERVICE_URL\s*\}/
    );

    assert.doesNotMatch(
      text,
      /http:\/\/10\.0\.2\.2:8080/
    );
  }
);
