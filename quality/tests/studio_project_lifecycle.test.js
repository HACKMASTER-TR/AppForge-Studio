import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const projectLibrary =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/io/ProjectLibrary.kt",
    import.meta.url
  );

const iconProcessor =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/io/AppIconProcessor.kt",
    import.meta.url
  );

const main =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
    import.meta.url
  );

const home =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/StudioHomeScreen.kt",
    import.meta.url
  );

test("local AI requires an explicitly selected saved project", async () => {
  const ui = await readFile(main, "utf8");

  for (const marker of [
    "Analiz edilen proje",
    "currentProjectId",
    "onSelectProject",
    "availableProjects",
    "Önce analiz projesini seç",
    "currentProjectId != null"
  ]) {
    assert.equal(ui.includes(marker), true, marker);
  }
});

test("deleted projects remain recoverable for thirty days", async () => {
  const library = await readFile(projectLibrary, "utf8");
  const ui = await readFile(home, "utf8");

  for (const marker of [
    "TRASH_RETENTION_MS",
    "30L * 24L * 60L * 60L * 1000L",
    "loadTrash",
    "restoreDeleted",
    "deletePermanently",
    "purgeExpiredTrash",
    "cleanupProjectFiles"
  ]) {
    assert.equal(library.includes(marker), true, marker);
  }

  assert.equal(ui.includes("Geri Dönüşüm Kutusu"), true);
  assert.equal(ui.includes("30 gün sonra otomatik silinecek"), true);
  assert.equal(ui.includes("Geri yükle"), true);
});

test("uploaded photos become orientation-safe adaptive launcher PNGs", async () => {
  const processor = await readFile(iconProcessor, "utf8");
  const ui = await readFile(main, "utf8");

  for (const marker of [
    "ImageDecoder",
    "ExifInterface",
    "OUTPUT_SIZE = 1024",
    "SAFE_CONTENT_SIZE = 640",
    "Bitmap.CompressFormat.PNG",
    "prepared-icons"
  ]) {
    assert.equal(processor.includes(marker), true, marker);
  }

  assert.equal(ui.includes('arrayOf("image/*")'), true);
  assert.equal(ui.includes("AppIconProcessor"), true);
});
