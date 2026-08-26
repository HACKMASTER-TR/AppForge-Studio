import test from "node:test";
import assert from "node:assert/strict";
import {
  promises as fs
} from "fs";
import os from "os";
import path from "path";
import AdmZip from "adm-zip";

import {
  prepareFlutterSource
} from "../src/flutterBuildEngine.js";

test(
  "Flutter source ZIP is validated and prepared",
  async () => {
    const root =
      await fs.mkdtemp(
        path.join(
          os.tmpdir(),
          "appforge-flutter-"
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
          "lib"
        ),
        {
          recursive: true
        }
      );

      await fs.writeFile(
        path.join(
          project,
          "pubspec.yaml"
        ),
`name: appforge_flutter_test
description: AppForge Flutter regression test
environment:
  sdk: ^3.0.0
dependencies:
  flutter:
    sdk: flutter
`
      );

      await fs.writeFile(
        path.join(
          project,
          "lib",
          "main.dart"
        ),
`import 'package:flutter/material.dart';

void main() {
  runApp(
    const MaterialApp(
      home: Text('FLUTTER_OK'),
    ),
  );
}
`
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
        await prepareFlutterSource(
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
        prepared.projectName,
        "appforge_flutter_test"
      );

      const main =
        await fs.readFile(
          prepared.mainFile,
          "utf8"
        );

      assert.match(
        main,
        /FLUTTER_OK/
      );

      assert.ok(
        prepared.extractedBytes >
          0
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
