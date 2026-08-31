import test from "node:test";
import assert from "node:assert/strict";
import { promises as fs } from "fs";
import os from "os";
import path from "path";
import AdmZip from "adm-zip";

import {
  preparePythonAndroidProject
} from "../src/sourceBuildEngines.js";

test(
  "Python source ZIP becomes a Chaquopy Android project",
  async () => {
    const root =
      await fs.mkdtemp(
        path.join(
          os.tmpdir(),
          "appforge-python-engine-"
        )
      );

    try {
      const source =
        path.join(
          root,
          "source"
        );

      await fs.mkdir(
        source,
        {
          recursive: true
        }
      );

      await fs.writeFile(
        path.join(
          source,
          "main.py"
        ),
        [
          "def main():",
          "    return 'PYTHON_OK'"
        ].join(
          "\n"
        )
      );

      await fs.writeFile(
        path.join(
          source,
          "helper.py"
        ),
        "VALUE = 42\n"
      );

      await fs.writeFile(
        path.join(
          source,
          "requirements.txt"
        ),
        "# empty test requirements\n"
      );

      const zipPath =
        path.join(
          root,
          "project.zip"
        );

      const zip =
        new AdmZip();

      zip.addLocalFolder(
        source
      );

      zip.writeZip(
        zipPath
      );

      const androidDir =
        path.join(
          root,
          "android"
        );

      const result =
        await preparePythonAndroidProject(
          {
            projectZip:
              zipPath,
            androidProjectDir:
              androidDir,
            config: {
              appName:
                "Python Test",
              packageName:
                "com.example.pythontest",
              versionCode:
                7,
              versionName:
                "1.2.3",
              signing: {
                mode:
                  "DEBUG"
              }
            }
          }
        );

      assert.equal(
        result.entryModule,
        "main"
      );

      assert.ok(
        result.sourceFiles >=
          2
      );

      const gradle =
        await fs.readFile(
          path.join(
            androidDir,
            "app",
            "build.gradle.kts"
          ),
          "utf8"
        );

      assert.match(
        gradle,
        /applicationId = "com\.example\.pythontest"/
      );

      assert.match(
        gradle,
        /versionCode = 7/
      );

      assert.match(
        gradle,
        /versionName = "1\.2\.3"/
      );

      assert.match(
        gradle,
        /com\.chaquo\.python/
      );

      const copiedMain =
        await fs.readFile(
          path.join(
            androidDir,
            "app",
            "src",
            "main",
            "python",
            "main.py"
          ),
          "utf8"
        );

      assert.match(
        copiedMain,
        /PYTHON_OK/
      );

      const bridge =
        await fs.readFile(
          path.join(
            androidDir,
            "app",
            "src",
            "main",
            "python",
            "appforge_entry.py"
          ),
          "utf8"
        );

      assert.match(
        bridge,
        /import_module\("main"\)/
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
