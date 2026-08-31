import test from "node:test";
import assert from "node:assert/strict";
import {
  mkdtemp,
  mkdir,
  readFile,
  rm,
  writeFile
} from "node:fs/promises";
import os from "node:os";
import path from "node:path";

import {
  installGradleAndroidSafeArea,
  installDotnetAndroidSafeArea,
  installUnityAndroidSafeArea
} from "../src/androidSafeArea.js";

test(
  "Gradle Android safe-area injects provider and manifest entry",
  async () => {
    const root =
      await mkdtemp(
        path.join(
          os.tmpdir(),
          "appforge-safe-area-"
        )
      );

    try {
      const main =
        path.join(
          root,
          "app",
          "src",
          "main"
        );

      await mkdir(
        main,
        {
          recursive: true
        }
      );

      await writeFile(
        path.join(
          main,
          "AndroidManifest.xml"
        ),
        `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:label="Test">
    </application>
</manifest>
`,
        "utf8"
      );

      const result =
        await installGradleAndroidSafeArea(
          {
            androidProjectDir:
              root
          }
        );

      const manifest =
        await readFile(
          result.manifestFile,
          "utf8"
        );

      const provider =
        await readFile(
          result.providerFile,
          "utf8"
        );

      assert.match(
        manifest,
        /AppForgeSafeAreaProvider/
      );

      assert.match(
        manifest,
        /\$\{applicationId\}\.appforge\.safearea/
      );

      assert.match(
        provider,
        /setOnApplyWindowInsetsListener/
      );

      assert.match(
        provider,
        /getSystemWindowInsetTop/
      );
    } finally {
      await rm(
        root,
        {
          recursive: true,
          force: true
        }
      );
    }
  }
);

test(
  ".NET Android safe-area emits lifecycle provider",
  async () => {
    const root =
      await mkdtemp(
        path.join(
          os.tmpdir(),
          "appforge-dotnet-safe-area-"
        )
      );

    try {
      const result =
        await installDotnetAndroidSafeArea(
          {
            projectRoot:
              root,
            packageName:
              "com.appforge.test"
          }
        );

      const source =
        await readFile(
          result.sourceFile,
          "utf8"
        );

      assert.match(
        source,
        /IActivityLifecycleCallbacks/
      );

      assert.match(
        source,
        /SetOnApplyWindowInsetsListener/
      );

      assert.match(
        source,
        /com\.appforge\.test\.appforge\.safearea/
      );
    } finally {
      await rm(
        root,
        {
          recursive: true,
          force: true
        }
      );
    }
  }
);

test(
  "Unity Android safe-area emits runtime bootstrap",
  async () => {
    const root =
      await mkdtemp(
        path.join(
          os.tmpdir(),
          "appforge-unity-safe-area-"
        )
      );

    try {
      const result =
        await installUnityAndroidSafeArea(
          {
            projectRoot:
              root
          }
        );

      const source =
        await readFile(
          result.sourceFile,
          "utf8"
        );

      assert.match(
        source,
        /RuntimeInitializeOnLoadMethod/
      );

      assert.match(
        source,
        /status_bar_height/
      );

      assert.match(
        source,
        /navigation_bar_height/
      );
    } finally {
      await rm(
        root,
        {
          recursive: true,
          force: true
        }
      );
    }
  }
);
