import test from "node:test";
import assert from "node:assert/strict";
import { promises as fs } from "fs";
import os from "os";
import path from "path";
import AdmZip from "adm-zip";

import {
  extractZipSafely,
  preflight,
  resolveSourceBuildEngine
} from "../src/buildEngine.js";

test(
  "unknown local project safely falls back when an HTML entry page exists",
  () => {
    const source =
      resolveSourceBuildEngine(
        {
          sourceMode: "LOCAL",
          sourceTechnology: "unknown",
          sourceTechnologyLabel: "Bilinmeyen proje",
          sourceBuildEngine: "unknown",
          sourceBuildReady: false,
          sourceHasWebStartPage: true
        }
      );

    assert.deepEqual(
      source,
      {
        technology: "web-static",
        label: "HTML / CSS / JavaScript",
        engine: "webview-static",
        ready: true
      }
    );

    assert.doesNotThrow(
      () =>
        preflight(
          {
            appName: "Fallback Web",
            packageName: "com.appforge.fallback",
            versionCode: 1,
            versionName: "1.0.0",
            sourceMode: "LOCAL",
            sourceTechnology: "unknown",
            sourceTechnologyLabel: "Bilinmeyen proje",
            sourceBuildEngine: "unknown",
            sourceBuildReady: false,
            sourceHasWebStartPage: true
          },
          {
            hasProject: true
          }
        )
    );

    assert.equal(
      resolveSourceBuildEngine(
        {
          sourceMode: "LOCAL",
          sourceTechnology: "dotnet-windows",
          sourceBuildEngine: "unknown",
          sourceHasWebStartPage: true
        }
      ).engine,
      "unknown",
      "HTML documentation must not turn an unsupported native project into a WebView app"
    );
  }
);

test(
  "alternate nested HTML entry page becomes root index.html",
  async () => {
    const work =
      await fs.mkdtemp(
        path.join(
          os.tmpdir(),
          "appforge-web-fallback-"
        )
      );

    try {
      const zipPath =
        path.join(
          work,
          "project.zip"
        );

      const destination =
        path.join(
          work,
          "site"
        );

      const zip =
        new AdmZip();

      zip.addFile(
        "deep/site/main.html",
        Buffer.from(
          "<!doctype html><script src=\"./app.js\"></script>"
        )
      );

      zip.addFile(
        "deep/site/app.js",
        Buffer.from(
          "document.body.textContent = 'ready';"
        )
      );

      zip.writeZip(
        zipPath
      );

      await extractZipSafely(
        zipPath,
        destination
      );

      assert.match(
        await fs.readFile(
          path.join(
            destination,
            "index.html"
          ),
          "utf8"
        ),
        /app\.js/
      );

      assert.equal(
        await fs.readFile(
          path.join(
            destination,
            "app.js"
          ),
          "utf8"
        ),
        "document.body.textContent = 'ready';"
      );
    } finally {
      await fs.rm(
        work,
        {
          recursive: true,
          force: true
        }
      );
    }
  }
);

test(
  "Android client rescans saved projects and classifies this build error",
  async () => {
    const repoRoot =
      path.resolve(
        process.cwd(),
        ".."
      );

    const client =
      await fs.readFile(
        path.join(
          repoRoot,
          "android-app/app/src/main/java/com/appforge/studio/build/BuildApiClient.kt"
        ),
        "utf8"
      );

    const detector =
      await fs.readFile(
        path.join(
          repoRoot,
          "android-app/app/src/main/java/com/appforge/studio/io/ProjectTechnologyDetector.kt"
        ),
        "utf8"
      );

    const importer =
      await fs.readFile(
        path.join(
          repoRoot,
          "android-app/app/src/main/java/com/appforge/studio/io/ProjectImporter.kt"
        ),
        "utf8"
      );

    const advisor =
      await fs.readFile(
        path.join(
          repoRoot,
          "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeBuildErrorAdvisor.kt"
        ),
        "utf8"
      );

    assert.match(
      client,
      /findLocalWebStartPage/
    );

    assert.match(
      client,
      /sourceHasWebStartPage/
    );

    assert.match(
      detector,
      /MAX_DEPTH = 20/
    );

    assert.match(
      detector,
      /"html",\s*"htm"/
    );

    assert.match(
      importer,
      /it\.extension\.lowercase\(\)/
    );

    assert.match(
      advisor,
      /Proje başlangıç sayfası algılanamadı/
    );
  }
);
