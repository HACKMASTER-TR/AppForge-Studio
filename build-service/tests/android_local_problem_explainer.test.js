import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const root =
  new URL("../../", import.meta.url);

const read =
  path =>
    readFile(
      new URL(path, root),
      "utf8"
    );

test(
  "Android advisor recognizes local package validation",
  async () => {
    const source =
      await read(
        "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeBuildErrorAdvisor.kt"
      );

    assert.match(
      source,
      /geçerli package name gir/i,
      "Türkçe package-name doğrulama kuralı bulunamadı"
    );

    assert.match(
      source,
      /package name geçersiz/i,
      "Package-name geçersiz kuralı bulunamadı"
    );
  }
);

test(
  "unknown local user validation does not fall through as unknown build error",
  async () => {
    const source =
      await read(
        "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeBuildErrorAdvisor.kt"
      );

    assert.match(
      source,
      /Girilen bilgiler geçersiz veya eksik/,
      "Yerel doğrulama başlığı bulunamadı"
    );

    assert.match(
      source,
      /localValidationEvidence/,
      "Yerel doğrulama fallback kodu bulunamadı"
    );

    assert.match(
      source,
      /confidence\s*=\s*90/,
      "Yerel doğrulama güven değeri %90 bulunamadı"
    );
  }
);
