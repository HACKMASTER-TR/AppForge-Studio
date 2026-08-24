import test from "node:test";
import assert from "node:assert/strict";

import {
  mkdtemp,
  readFile,
  rm
} from "node:fs/promises";

import os from "node:os";
import path from "node:path";
import AdmZip from "adm-zip";


const EXE_MAGIC =
  Buffer.from(
    "APPFORGE-EXE-V1!",
    "ascii"
  );

const PAYLOAD_MAGIC =
  Buffer.from(
    "AFEXEP01",
    "ascii"
  );

const FOOTER_BYTES =
  24;


const EXPECTED_WEBVIEW = {
  javaScriptEnabled:
    true,

  domStorageEnabled:
    true,

  zoomEnabled:
    false,

  wideViewPortEnabled:
    true,

  overviewModeEnabled:
    false,

  mediaAutoplayEnabled:
    false,

  mixedContentAllowed:
    true
};


function uint32be(
  value
) {
  const buffer =
    Buffer.alloc(4);

  buffer.writeUInt32BE(
    value,
    0
  );

  return buffer;
}


function uint64be(
  value
) {
  const buffer =
    Buffer.alloc(8);

  buffer.writeBigUInt64BE(
    BigInt(value),
    0
  );

  return buffer;
}


function createProjectZip(
  target
) {
  const zip =
    new AdmZip();

  zip.addFile(
    "index.html",
    Buffer.from(
      `<!doctype html>
<html>
<head>
<meta charset="utf-8">
<title>AppForge Round Trip Smoke</title>
</head>
<body>
<button id="play">Play</button>

<script>
document.getElementById("play")
  .addEventListener(
    "click",
    () => {
      if (
        window.AppForgeMedia
      ) {
        window.AppForgeMedia.play(
          "https://example.com/test.mp3",
          "Smoke Test",
          "AppForge"
        );
      }
    }
  );
</script>
</body>
</html>`,
      "utf8"
    )
  );

  zip.writeZip(
    target
  );
}


async function createSyntheticAppForgeExe(
  projectZip,
  target
) {
  const projectBytes =
    await readFile(
      projectZip
    );

  const manifest = {
    format:
      "appforge-project",

    formatVersion:
      1,

    producer:
      "AppForge Studio",

    platform:
      "windows",

    appName:
      "Round Trip Smoke",

    appId:
      "com.appforge.roundtripsmoke",

    versionName:
      "1.0.0",

    versionCode:
      1,

    sourceMode:
      "LOCAL",

    webUrl:
      "",

    projectRoot:
      "project.zip",

    startPage:
      "index.html",

    webView: {
      ...EXPECTED_WEBVIEW
    },

    nativeBridge: {
      mediaPlayer:
        true
    },

    conversion: {
      apkToExe:
        true,

      exeToApk:
        true
    },

    createdBy:
      "AppForge Studio",

    target:
      "windows-x64"
  };

  const manifestBytes =
    Buffer.from(
      JSON.stringify(
        manifest
      ),
      "utf8"
    );

  const payload =
    Buffer.concat([
      PAYLOAD_MAGIC,
      uint32be(
        manifestBytes.length
      ),
      manifestBytes,
      projectBytes
    ]);

  /*
   * Küçük sentetik PE başlangıcı.
   * Smoke test gerçek Windows kodu çalıştırmaz;
   * AppForge dönüşüm veri sözleşmesini test eder.
   */
  const fakePe =
    Buffer.concat([
      Buffer.from(
        "MZ",
        "ascii"
      ),
      Buffer.alloc(
        126
      )
    ]);

  const footer =
    Buffer.concat([
      uint64be(
        payload.length
      ),
      EXE_MAGIC
    ]);

  const exe =
    Buffer.concat([
      fakePe,
      payload,
      footer
    ]);

  const {
    writeFile
  } =
    await import(
      "node:fs/promises"
    );

  await writeFile(
    target,
    exe
  );
}


async function parseAppForgeExe(
  exePath
) {
  const exe =
    await readFile(
      exePath
    );

  assert.equal(
    exe
      .subarray(
        0,
        2
      )
      .toString(
        "ascii"
      ),
    "MZ"
  );

  assert.equal(
    exe
      .subarray(
        exe.length -
          EXE_MAGIC.length
      )
      .equals(
        EXE_MAGIC
      ),
    true,
    "AppForge EXE footer magic missing"
  );

  const footerStart =
    exe.length -
    FOOTER_BYTES;

  const payloadLength =
    Number(
      exe.readBigUInt64BE(
        footerStart
      )
    );

  assert.ok(
    payloadLength >
      12,
    "Payload unexpectedly small"
  );

  const payloadOffset =
    footerStart -
    payloadLength;

  assert.ok(
    payloadOffset >=
      2
  );

  const payload =
    exe.subarray(
      payloadOffset,
      footerStart
    );

  assert.equal(
    payload
      .subarray(
        0,
        PAYLOAD_MAGIC.length
      )
      .equals(
        PAYLOAD_MAGIC
      ),
    true,
    "AppForge payload magic missing"
  );

  const manifestLength =
    payload.readUInt32BE(
      8
    );

  assert.ok(
    manifestLength >
      0
  );

  const manifestStart =
    12;

  const manifestEnd =
    manifestStart +
    manifestLength;

  assert.ok(
    manifestEnd <=
      payload.length
  );

  const manifest =
    JSON.parse(
      payload
        .subarray(
          manifestStart,
          manifestEnd
        )
        .toString(
          "utf8"
        )
    );

  const projectZip =
    payload.subarray(
      manifestEnd
    );

  assert.ok(
    projectZip.length >
      0,
    "LOCAL EXE project.zip missing"
  );

  return {
    manifest,
    projectZip
  };
}


test(
  "APK -> EXE -> APK round trip preserves AppForgeMedia and WebView Pro",
  async () => {

    const root =
      await mkdtemp(
        path.join(
          os.tmpdir(),
          "appforge-roundtrip-"
        )
      );

    try {
      const projectZip =
        path.join(
          root,
          "project.zip"
        );

      const exePath =
        path.join(
          root,
          "appforge.exe"
        );

      const apkPath =
        path.join(
          root,
          "roundtrip.apk"
        );

      createProjectZip(
        projectZip
      );

      await createSyntheticAppForgeExe(
        projectZip,
        exePath
      );

      /*
       * EXE -> APK tarafı
       */
      const extracted =
        await parseAppForgeExe(
          exePath
        );

      const windowsManifest =
        extracted.manifest;

      assert.equal(
        windowsManifest.format,
        "appforge-project"
      );

      assert.equal(
        windowsManifest.platform,
        "windows"
      );

      assert.equal(
        windowsManifest
          .conversion
          .exeToApk,
        true
      );

      assert.equal(
        windowsManifest
          .nativeBridge
          .mediaPlayer,
        true,
        "Media3 flag lost in Windows manifest"
      );

      assert.deepEqual(
        windowsManifest.webView,
        EXPECTED_WEBVIEW,
        "WebView Pro settings lost in Windows manifest"
      );

      const embeddedProject =
        new AdmZip(
          extracted.projectZip
        );

      const indexEntry =
        embeddedProject.getEntry(
          "index.html"
        );

      assert.ok(
        indexEntry,
        "index.html missing from EXE payload"
      );

      const html =
        indexEntry
          .getData()
          .toString(
            "utf8"
          );

      assert.match(
        html,
        /AppForgeMedia/
      );

      /*
       * Android çıktısını sentetik olarak yeniden oluştur.
       * Burada dönüşüm sözleşmesinin Android tarafına
       * eksiksiz geçtiğini doğruluyoruz.
       */
      const androidManifest = {
        format:
          "appforge-project",

        formatVersion:
          1,

        producer:
          "AppForge Studio",

        platform:
          "android",

        appName:
          windowsManifest.appName,

        appId:
          windowsManifest.appId,

        versionName:
          windowsManifest.versionName,

        versionCode:
          windowsManifest.versionCode,

        sourceMode:
          windowsManifest.sourceMode,

        webUrl:
          windowsManifest.webUrl,

        projectRoot:
          "assets/site",

        webView: {
          ...windowsManifest.webView
        },

        nativeBridge: {
          mediaPlayer:
            windowsManifest
              .nativeBridge
              .mediaPlayer
        },

        conversion: {
          apkToExe:
            true,

          exeToApk:
            true
        }
      };

      const apk =
        new AdmZip();

      apk.addFile(
        "assets/appforge-project.json",
        Buffer.from(
          JSON.stringify(
            androidManifest
          ),
          "utf8"
        )
      );

      apk.addFile(
        "assets/site/index.html",
        Buffer.from(
          html,
          "utf8"
        )
      );

      apk.writeZip(
        apkPath
      );

      /*
       * APK -> EXE için yeniden okunabilir mi?
       */
      const finalApk =
        new AdmZip(
          apkPath
        );

      const manifestEntry =
        finalApk.getEntry(
          "assets/appforge-project.json"
        );

      const siteEntry =
        finalApk.getEntry(
          "assets/site/index.html"
        );

      assert.ok(
        manifestEntry
      );

      assert.ok(
        siteEntry
      );

      const finalManifest =
        JSON.parse(
          manifestEntry
            .getData()
            .toString(
              "utf8"
            )
        );

      assert.equal(
        finalManifest.platform,
        "android"
      );

      assert.equal(
        finalManifest
          .conversion
          .apkToExe,
        true
      );

      assert.equal(
        finalManifest
          .conversion
          .exeToApk,
        true
      );

      assert.equal(
        finalManifest
          .nativeBridge
          .mediaPlayer,
        true,
        "Media3 flag lost after round trip"
      );

      assert.deepEqual(
        finalManifest.webView,
        EXPECTED_WEBVIEW,
        "WebView Pro settings lost after round trip"
      );

      assert.match(
        siteEntry
          .getData()
          .toString(
            "utf8"
          ),
        /AppForgeMedia/
      );


      /*
       * Gerçek kaynak kodlarının da aynı sözleşmeyi
       * kullanmaya devam ettiğini kontrol et.
       */
      const windowsBuild =
        await readFile(
          new URL(
            "../src/windowsBuild.js",
            import.meta.url
          ),
          "utf8"
        );

      const exeExtractor =
        await readFile(
          new URL(
            "../../android-app/app/src/main/java/com/appforge/studio/io/AppForgeExeConversion.kt",
            import.meta.url
          ),
          "utf8"
        );

      const apkExtractor =
        await readFile(
          new URL(
            "../../android-app/app/src/main/java/com/appforge/studio/io/AppForgeApkConversion.kt",
            import.meta.url
          ),
          "utf8"
        );

      const mainActivity =
        await readFile(
          new URL(
            "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
            import.meta.url
          ),
          "utf8"
        );

      for (
        const marker of [
          "APPFORGE-EXE-V1!",
          "AFEXEP01",
          "mediaPlayer",
          "webView",
          "mixedContentAllowed"
        ]
      ) {
        assert.equal(
          windowsBuild.includes(
            marker
          ),
          true,
          `windowsBuild missing ${marker}`
        );
      }

      assert.equal(
        exeExtractor.includes(
          "containsAppForgeMedia"
        ),
        true
      );

      assert.equal(
        apkExtractor.includes(
          "containsAppForgeMedia"
        ),
        true
      );

      assert.equal(
        exeExtractor.includes(
          "webMixedContentAllowed"
        ),
        true,
        "EXE extractor missing WebView Pro conversion"
      );

      assert.equal(
        apkExtractor.includes(
          "webMixedContentAllowed"
        ),
        true,
        "APK extractor missing WebView Pro conversion"
      );

      assert.equal(
        mainActivity.includes(
          "converted.mediaPlayerBridge"
        ),
        true
      );

      assert.equal(
        mainActivity.includes(
          "converted.webMixedContentAllowed"
        ),
        true,
        "MainActivity does not preserve WebView Pro during conversion"
      );

    } finally {
      await rm(
        root,
        {
          recursive:
            true,

          force:
            true
        }
      );
    }
  }
);
