import test from "node:test";
import assert from "node:assert/strict";
import {
  promises as fs
} from "fs";
import os from "os";
import path from "path";
import AdmZip from "adm-zip";

import {
  prepareReactNativeSource
} from "../src/reactNativeBuildEngine.js";

async function makeZip(
  root,
  files
) {
  const project =
    path.join(
      root,
      "project"
    );

  for (
    const [
      relative,
      content
    ] of
    Object.entries(
      files
    )
  ) {
    const target =
      path.join(
        project,
        relative
      );

    await fs.mkdir(
      path.dirname(
        target
      ),
      {
        recursive: true
      }
    );

    await fs.writeFile(
      target,
      content
    );
  }

  const zipPath =
    path.join(
      root,
      "project.zip"
    );

  const zip =
    new AdmZip();

  zip.addLocalFolder(
    project
  );

  zip.writeZip(
    zipPath
  );

  return zipPath;
}

test(
  "React Native native Android project is classified",
  async () => {
    const root =
      await fs.mkdtemp(
        path.join(
          os.tmpdir(),
          "appforge-rn-"
        )
      );

    try {
      const zipPath =
        await makeZip(
          root,
          {
            "package.json":
              JSON.stringify(
                {
                  name:
                    "rn-test",
                  dependencies: {
                    react:
                      "19.0.0",
                    "react-native":
                      "0.80.0"
                  }
                }
              ),
            "package-lock.json":
              "{}",
            "android/settings.gradle":
              'rootProject.name = "RnTest"\ninclude(":app")\n',
            "android/app/src/main/AndroidManifest.xml":
              "<manifest />\n",
            "index.js":
              "console.log('RN_OK');\n"
          }
        );

      const prepared =
        await prepareReactNativeSource(
          {
            projectZip:
              zipPath,
            workDir:
              path.join(
                root,
                "work"
              )
          }
        );

      assert.equal(
        prepared.projectType,
        "react-native"
      );

      assert.equal(
        prepared.reactNative,
        true
      );

      assert.equal(
        prepared.nativeAndroidReady,
        true
      );

      assert.equal(
        prepared.packageManager,
        "npm"
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
  }
);

test(
  "Expo managed project is classified without pretending native Android exists",
  async () => {
    const root =
      await fs.mkdtemp(
        path.join(
          os.tmpdir(),
          "appforge-expo-"
        )
      );

    try {
      const zipPath =
        await makeZip(
          root,
          {
            "package.json":
              JSON.stringify(
                {
                  name:
                    "expo-test",
                  dependencies: {
                    expo:
                      "^54.0.0",
                    react:
                      "19.0.0",
                    "react-native":
                      "0.81.0"
                  }
                }
              ),
            "app.json":
              JSON.stringify(
                {
                  expo: {
                    name:
                      "Expo Test",
                    slug:
                      "expo-test"
                  }
                }
              ),
            "yarn.lock":
              "# test\n"
          }
        );

      const prepared =
        await prepareReactNativeSource(
          {
            projectZip:
              zipPath,
            workDir:
              path.join(
                root,
                "work"
              )
          }
        );

      assert.equal(
        prepared.projectType,
        "expo-managed"
      );

      assert.equal(
        prepared.expo,
        true
      );

      assert.equal(
        prepared.nativeAndroidReady,
        false
      );

      assert.equal(
        prepared.packageManager,
        "yarn"
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
  }
);
