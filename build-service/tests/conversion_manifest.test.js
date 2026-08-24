import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const fullEngine =
  new URL(
    "../src/buildEngine.js",
    import.meta.url
  );

const fastEngine =
  new URL(
    "../src/fastBuild.js",
    import.meta.url
  );

test(
  "Android FULL build embeds AppForge conversion manifest",
  async () => {
    const text =
      await readFile(
        fullEngine,
        "utf8"
      );

    assert.equal(
      text.includes(
        "appforge-project.json"
      ),
      true
    );

    assert.equal(
      text.includes(
        '"appforge-project"'
      ),
      true
    );

    assert.equal(
      text.includes(
        '"assets/site"'
      ),
      true
    );
  }
);

test(
  "Android FAST APK embeds AppForge conversion manifest",
  async () => {
    const text =
      await readFile(
        fastEngine,
        "utf8"
      );

    assert.equal(
      text.includes(
        '"assets/appforge-project.json"'
      ),
      true
    );

    assert.equal(
      text.includes(
        "apkToExe:"
      ),
      true
    );

    assert.equal(
      text.includes(
        "exeToApk:"
      ),
      true
    );
  }
);


test(
  "Studio safely extracts AppForge project from APK",
  async () => {
    const file =
      new URL(
        "../../android-app/app/src/main/java/com/appforge/studio/io/AppForgeApkConversion.kt",
        import.meta.url
      );

    const text =
      await readFile(
        file,
        "utf8"
      );

    for (
      const marker of [
        '"assets/appforge-project.json"',
        '"assets/site/"',
        '"appforge-project"',
        '"apkToExe"',
        "MAX_SITE_BYTES",
        "safeRelativePath("
      ]
    ) {
      assert.equal(
        text.includes(marker),
        true,
        `Missing APK conversion marker: ${marker}`
      );
    }
  }
);

test(
  "Studio starts Windows build from converted APK",
  async () => {
    const file =
      new URL(
        "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
        import.meta.url
      );

    const text =
      await readFile(
        file,
        "utf8"
      );

    for (
      const marker of [
        "AppForgeApkConversion",
        "conversionApkUri",
        'buildOutput =\n                        "exe"',
        "startBuildWithDraft("
      ]
    ) {
      assert.equal(
        text.includes(marker),
        true,
        `Missing APK to EXE build marker: ${marker}`
      );
    }
  }
);


test(
  "APK conversion effect does not cancel itself before extraction",
  async () => {
    const file =
      new URL(
        "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
        import.meta.url
      );

    const text =
      await readFile(
        file,
        "utf8"
      );

    const start =
      text.indexOf(
        "LaunchedEffect(\n        conversionApkUri"
      );

    const end =
      text.indexOf(
        "\n\n\n    MaterialTheme(",
        start
      );

    assert.notEqual(start, -1);
    assert.notEqual(end, -1);

    const block =
      text.slice(
        start,
        end
      );

    const extractPos =
      block.indexOf(
        "AppForgeApkConversion"
      );

    const clearPos =
      block.indexOf(
        "conversionApkUri =\n                null"
      );

    assert.ok(
      extractPos >= 0
    );

    assert.ok(
      clearPos > extractPos,
      "conversionApkUri must only be cleared after APK extraction starts"
    );

    assert.equal(
      text.includes(
        "status: String"
      ),
      true
    );
  }
);


test(
  "Studio validates EXE before EXE to APK conversion",
  async () => {
    const file =
      new URL(
        "../../android-app/app/src/main/java/com/appforge/studio/io/AppForgeExeConversion.kt",
        import.meta.url
      );

    const text =
      await readFile(
        file,
        "utf8"
      );

    for (
      const marker of [
        '"APPFORGE-EXE-V1!"',
        '"Geçerli bir Windows EXE dosyası değil."',
        '"Bu EXE AppForge projesi değil. Otomatik EXE → APK dönüşümü desteklenmiyor."',
        "MAX_EXE_BYTES",
        "MAX_PAYLOAD_BYTES",
        "payloadOffset",
        "payloadLength"
      ]
    ) {
      assert.equal(
        text.includes(marker),
        true,
        `Missing EXE conversion marker: ${marker}`
      );
    }
  }
);


test(
  "Studio EXE picker starts AppForge EXE validation",
  async () => {
    const file =
      new URL(
        "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
        import.meta.url
      );

    const text =
      await readFile(
        file,
        "utf8"
      );

    for (
      const marker of [
        "AppForgeExeConversion",
        "conversionExeUri",
        '"EXE doğrulanıyor..."',
        "AppForge EXE doğrulandı"
      ]
    ) {
      assert.equal(
        text.includes(marker),
        true,
        `Missing EXE picker conversion marker: ${marker}`
      );
    }
  }
);
