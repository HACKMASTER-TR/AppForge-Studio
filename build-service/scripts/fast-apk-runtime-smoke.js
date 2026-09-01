import {
  promises as fs
} from "fs";

import path from "path";
import os from "os";

import {
  buildFastApk
} from "../src/fastBuild.js";

const root =
  await fs.mkdtemp(
    path.join(
      os.tmpdir(),
      "appforge-fast-smoke-"
    )
  );

try {
  const workDir =
    path.join(
      root,
      "work"
    );

  const apk =
    await buildFastApk({
      workDir,
      siteDir: null,

      config: {
        appName:
          "AppForge Production Smoke",

        packageName:
          "com.appforge.production.smoke",

        sourceMode:
          "URL",

        webUrl:
          "https://example.com",

        minSdk: 26,
        targetSdk: 37,

        versionCode: 1,
        versionName: "1.0.0",

        orientation:
          "unspecified",

        signing: {
          mode: "DEBUG"
        },

        features: {
          fileUpload: true,
          downloads: true,
          notifications: false,
          camera: false,
          location: false
        },

        nativeBridge: {
          enabled: false
        },

        webView: {
          javaScriptEnabled: true,
          domStorageEnabled: true,
          zoomEnabled: false,
          wideViewPortEnabled: true,
          overviewModeEnabled: true,
          mediaAutoplayEnabled: true,
          mixedContentAllowed: false
        },

        branding: {
          showWatermark: false
        }
      }
    });

  const stat =
    await fs.stat(
      apk
    );

  if (
    !stat.isFile() ||
    stat.size < 1024
  ) {
    throw new Error(
      "FAST APK smoke boş/geçersiz APK oluşturdu."
    );
  }

  console.log(
    JSON.stringify(
      {
        ok: true,
        apk,
        bytes: stat.size
      },
      null,
      2
    )
  );
} finally {
  await fs.rm(
    root,
    {
      recursive: true,
      force: true
    }
  );
}
