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


test(
  "Android advisor contains specialized local validation rules",
  async () => {
    const source =
      await read(
        "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeBuildErrorAdvisor.kt"
      );

    const expected = [
      "Min / Hedef SDK ayarı geçersiz",
      "Uygulama adı eksik",
      "Web URL geçersiz",
      "Deep Link bilgisi eksik",
      "AdMob App ID eksik",
      "Google Play Billing ayarı eksik veya geçersiz",
      "Keystore / imza bilgisi hatası",
      "Firebase yapılandırması eksik veya uyumsuz",
      "min sdk 26 ile 37 arasında olmalı",
      "hedef sdk 26 ile 37 arasında olmalı",
      "deep link scheme gerekli",
      "deep link host gerekli",
      "admob app id gerekli",
      "en az bir ürün veya abonelik id gerekli"
    ];

    for (const value of expected) {
      assert.ok(
        source.toLowerCase().includes(value.toLowerCase()),
        `Eksik Android hata açıklaması: ${value}`
      );
    }
  }
);
