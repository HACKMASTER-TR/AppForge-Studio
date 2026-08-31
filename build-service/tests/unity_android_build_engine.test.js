import test from "node:test";
import assert from "node:assert/strict";
import {
  promises as fs
} from "fs";
import os from "os";
import path from "path";
import AdmZip from "adm-zip";

import {
  prepareUnityAndroidSource
} from "../src/unityAndroidBuildEngine.js";

test(
  "Unity project metadata is securely detected and prepared",
  async () => {
    const root =
      await fs.mkdtemp(
        path.join(
          os.tmpdir(),
          "appforge-unity-"
        )
      );

    try {
      const project =
        path.join(
          root,
          "project"
        );

      await fs.mkdir(
        path.join(
          project,
          "Assets",
          "Scenes"
        ),
        {
          recursive: true
        }
      );

      await fs.mkdir(
        path.join(
          project,
          "Assets",
          "Plugins",
          "Android"
        ),
        {
          recursive: true
        }
      );

      await fs.mkdir(
        path.join(
          project,
          "ProjectSettings"
        ),
        {
          recursive: true
        }
      );

      await fs.mkdir(
        path.join(
          project,
          "Packages"
        ),
        {
          recursive: true
        }
      );

      await fs.writeFile(
        path.join(
          project,
          "ProjectSettings",
          "ProjectVersion.txt"
        ),
`m_EditorVersion: 6000.0.60f1
m_EditorVersionWithRevision: 6000.0.60f1 (test-revision)
`
      );

      await fs.writeFile(
        path.join(
          project,
          "ProjectSettings",
          "EditorBuildSettings.asset"
        ),
`EditorBuildSettings:
  m_Scenes:
  - enabled: 1
    path: Assets/Scenes/Main.unity
  - enabled: 0
    path: Assets/Scenes/Disabled.unity
`
      );

      await fs.writeFile(
        path.join(
          project,
          "ProjectSettings",
          "ProjectSettings.asset"
        ),
`PlayerSettings:
  applicationIdentifier: {Standalone: com.example.desktop, Android: com.example.unityapp}
`
      );

      await fs.writeFile(
        path.join(
          project,
          "Packages",
          "manifest.json"
        ),
        JSON.stringify(
          {
            dependencies: {
              "com.unity.inputsystem":
                "1.11.2",
              "com.unity.ugui":
                "2.0.0"
            }
          }
        )
      );

      await fs.writeFile(
        path.join(
          project,
          "Assets",
          "Plugins",
          "Android",
          "mainTemplate.gradle"
        ),
        "// custom template\n"
      );

      await fs.writeFile(
        path.join(
          project,
          "Assets",
          "Scenes",
          "Main.unity"
        ),
        "%YAML 1.1\n"
      );

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

      const prepared =
        await prepareUnityAndroidSource(
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
        prepared.editorVersion,
        "6000.0.60f1"
      );

      assert.deepEqual(
        prepared.enabledScenes,
        [
          "Assets/Scenes/Main.unity"
        ]
      );

      assert.equal(
        prepared.packageCount,
        2
      );

      assert.equal(
        prepared.androidApplicationId,
        "com.example.unityapp"
      );

      assert.equal(
        prepared.hasAndroidPlugins,
        true
      );

      assert.deepEqual(
        prepared.gradleTemplates,
        [
          "mainTemplate.gradle"
        ]
      );

      assert.equal(
        prepared.requiresLicensedUnityEditor,
        true
      );

      assert.equal(
        prepared.buildReady,
        false
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
