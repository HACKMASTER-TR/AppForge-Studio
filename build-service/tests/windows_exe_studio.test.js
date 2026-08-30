import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const api =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/build/BuildApiClient.kt",
    import.meta.url
  );

const main =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
    import.meta.url
  );

const library =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/io/ProjectLibrary.kt",
    import.meta.url
  );

test(
  "Studio Build API understands EXE artifacts",
  async () => {
    const text =
      await readFile(
        api,
        "utf8"
      );

    assert.equal(
      text.includes(
        "exeAvailable"
      ),
      true
    );

    assert.equal(
      text.includes(
        '"exe" -> "exe"'
      ),
      true
    );
  }
);

test(
  "Studio exposes Windows EXE output",
  async () => {
    const text =
      await readFile(
        main,
        "utf8"
      );

    for (
      const marker of [
        '"exe"',
        "Windows EXE",
        "WINDOWS EXE'Yİ İNDİR",
        'createDownloadTicket('
      ]
    ) {
      assert.equal(
        text.includes(marker),
        true,
        `Missing Studio EXE marker: ${marker}`
      );
    }
  }
);

test(
  "Studio build history stores EXE artifact",
  async () => {
    const text =
      await readFile(
        library,
        "utf8"
      );

    assert.equal(
      text.includes(
        "exeUrl"
      ),
      true
    );

    assert.equal(
      text.includes(
        'put("exeUrl", b.exeUrl)'
      ),
      true
    );
  }
);

test(
  "Studio preserves EXE download extension",
  async () => {
    const text =
      await readFile(
        main,
        "utf8"
      );

    const start =
      text.indexOf(
        "private fun artifactDownloadName("
      );

    assert.notEqual(
      start,
      -1
    );

    const block =
      text.slice(
        start,
        start + 1800
      );

    assert.match(
      block,
      /"exe"\s*->\s*"exe"/
    );
  }
);

test(
  "Studio hides raw EXE logs from normal users",
  async () => {
    const text =
      await readFile(
        main,
        "utf8"
      );

    assert.equal(
      text.includes(
        '"Canlı Windows logu"'
      ),
      false
    );

    assert.equal(
      text.includes(
        '"Canlı Gradle logu"'
      ),
      false
    );

    // Ham Windows/Gradle log arayüzü artık normal kullanıcıdan gizli.
    // EXE desteği build katmanında devam eder; UI log etiketi beklenmez.
  }
);

test(
  "Studio exposes APK EXE conversion entry",
  async () => {
    const text =
      await readFile(
        main,
        "utf8"
      );

    for (
      const marker of [
        "AppScreen.CONVERSION",
        '"Dönüşüm"',
        '"APK → Windows EXE"',
        '"EXE → Android APK"',
        "ConversionScreen("
      ]
    ) {
      assert.equal(
        text.includes(marker),
        true,
        `Missing conversion UI marker: ${marker}`
      );
    }
  }
);

test(
  "Studio conversion screen has APK and EXE file pickers",
  async () => {
    const text =
      await readFile(
        main,
        "utf8"
      );

    for (
      const marker of [
        "conversionApkPicker",
        "conversionExePicker",
        "application/vnd.android.package-archive",
        'arrayOf("*/*")',
        '"Seçilen APK: $selectedApkName"',
        '"Seçilen EXE: $selectedExeName"'
      ]
    ) {
      assert.equal(
        text.includes(marker),
        true,
        `Missing conversion picker marker: ${marker}`
      );
    }
  }
);


test(
  "Home create dialog exposes conversion option",
  async () => {
    const home =
      new URL(
        "../../android-app/app/src/main/java/com/appforge/studio/StudioHomeScreen.kt",
        import.meta.url
      );

    const text =
      await readFile(
        home,
        "utf8"
      );

    for (
      const marker of [
        "onCreateConversion",
        "onConversion",
        '"Dönüşüm"',
        '"APK → Windows EXE veya EXE → Android APK dönüşüm araçları."'
      ]
    ) {
      assert.equal(
        text.includes(marker),
        true,
        `Missing home conversion marker: ${marker}`
      );
    }
  }
);


test(
  "Studio saves Windows EXE through Storage Access Framework",
  async () => {
    const text =
      await readFile(
        main,
        "utf8"
      );

    for (
      const marker of [
        "val exeSaveLauncher",
        "ActivityResultContracts.CreateDocument(",
        "downloadArtifactToUri(",
        '"Windows EXE indiriliyor..."',
        '"✅ Windows EXE başarıyla kaydedildi."'
      ]
    ) {
      assert.equal(
        text.includes(marker),
        true,
        `Missing EXE save marker: ${marker}`
      );
    }
  }
);


test(
  "Studio stores build artifacts in AppForge Studio Downloads folder",
  async () => {
    const text =
      await readFile(
        main,
        "utf8"
      );

    for (
      const marker of [
        'APPFORGE_DOWNLOAD_FOLDER',
        '"AppForge Studio"',
        'downloadArtifactToDownloads(',
        'MediaStore.Downloads.EXTERNAL_CONTENT_URI',
        'MediaStore.MediaColumns.RELATIVE_PATH',
        'Environment.DIRECTORY_DOWNLOADS',
        '"✅ Windows EXE Downloads/AppForge Studio klasörüne kaydedildi."'
      ]
    ) {
      assert.equal(
        text.includes(marker),
        true,
        `Missing AppForge Downloads marker: ${marker}`
      );
    }
  }
);
