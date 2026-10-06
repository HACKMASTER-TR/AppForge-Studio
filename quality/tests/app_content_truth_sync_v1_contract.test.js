import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const read =
  path =>
    readFile(
      new URL(
        `../../${path}`,
        import.meta.url
      ),
      "utf8"
    );

test(
  "normal AppForge user-facing account copy is accountless",
  async () => {
    const main =
      await read(
        "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
      );

    assert.match(
      main,
      /AppForge Hesapsız Kullanım/
    );

    assert.match(
      main,
      /Normal kullanım için AppForge hesabı oluşturulmaz/
    );

    assert.match(
      main,
      /label = "Kullanım"[\s\S]*?value = "Hesapsız"/
    );

    assert.doesNotMatch(
      main,
      /hesabına giriş yap/
    );
  }
);

test(
  "retired monthly Pro and quota package copy cannot return",
  async () => {
    const main =
      await read(
        "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
      );

    assert.doesNotMatch(
      main,
      /Pro Aylık/
    );

    assert.doesNotMatch(
      main,
      /\+10, \+25 veya \+50/
    );

    assert.match(
      main,
      /Pro Ömür Boyu/
    );
  }
);

test(
  "privacy policy describes the current accountless product",
  async () => {
    const privacy =
      await read(
        "docs/privacy.html"
      );

    assert.match(
      privacy,
      /Hesapsız normal kullanım/
    );

    assert.match(
      privacy,
      /normal kullanımı için AppForge kullanıcı\s+hesabı oluşturulmaz/
    );

    assert.match(
      privacy,
      /tek seferlik Pro Ömür Boyu/
    );

    assert.doesNotMatch(
      privacy,
      /Hesap e-posta adresi, görünen ad ve oturum bilgileri/
    );

    assert.doesNotMatch(
      privacy,
      /Hesabımı Sil/
    );
  }
);

test(
  "obsolete password account deletion endpoint is no longer advertised",
  async () => {
    const page =
      await read(
        "docs/delete-account.html"
      );

    assert.match(
      page,
      /Veri Silme ve Hesapsız Kullanım/
    );

    assert.match(
      page,
      /silinecek ayrı bir\s+AppForge kullanıcı hesabı bulunmaz/
    );

    assert.doesNotMatch(
      page,
      /api\/auth\/delete-account/
    );

    assert.doesNotMatch(
      page,
      /AppForge hesap parolası/
    );

    assert.doesNotMatch(
      page,
      /<input/
    );
  }
);

test(
  "real admin Google sign-in and lifetime billing remain documented in source",
  async () => {
    const admin =
      await read(
        "android-app/app/src/main/java/com/appforge/studio/AdminOpsScreen.kt"
      );

    const pro =
      await read(
        "android-app/app/src/main/java/com/appforge/studio/ProPurchasesActivity.kt"
      );

    assert.match(
      admin,
      /GOOGLE İLE YÖNETİCİ GİRİŞİ/
    );

    assert.match(
      pro,
      /Pro Ömür Boyu/
    );

    assert.match(
      pro,
      /Tek seferlik Google Play ödemesi/
    );

    assert.match(
      pro,
      /Aylık abonelik veya ek paket yok/
    );
  }
);
