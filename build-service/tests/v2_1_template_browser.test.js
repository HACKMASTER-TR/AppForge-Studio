import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const mainActivity =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
    import.meta.url
  );

test("advanced template browser screen exists", async () => {
  const text =
    await readFile(
      mainActivity,
      "utf8"
    );

  assert.equal(
    text.includes("Hazır şablon kataloğu"),
    true
  );

  assert.equal(
    text.includes("Kategori gez, ilgili örnekleri filtrele"),
    true
  );

  assert.equal(
    text.includes("Arama eşleşmeleri"),
    true
  );
});

test("template categories are present", async () => {
  const text =
    await readFile(
      mainActivity,
      "utf8"
    );

  for (const label of [
    "Etkileşim",
    "Başlangıçlar",
    "Starter Libraries",
    "Reklamlar",
    "Cihaz",
    "Sensörler",
    "Sistem",
    "Panel"
  ]) {
    assert.equal(
      text.includes(label),
      true,
      `Missing ${label}`
    );
  }
});

test("template bottom sheet and direct apply exist", async () => {
  const text =
    await readFile(
      mainActivity,
      "utf8"
    );

  assert.equal(
    text.includes("ModalBottomSheet"),
    true
  );

  assert.equal(
    text.includes("ŞABLONU UYGULA"),
    true
  );

  assert.equal(
    text.includes("Sunucudaki gerçek şablonları almak için hesabına giriş yap"),
    true
  );
});
