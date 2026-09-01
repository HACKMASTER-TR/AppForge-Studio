import test from "node:test";
import assert from "node:assert/strict";
import {
  promises as fs
} from "fs";
import os from "os";
import path from "path";
import AdmZip from "adm-zip";

import {
  prepareFlutterSource,
  extractFlutterFailureDiagnostics,
  appendFlutterOutputTail
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


test(
  "Flutter Gradle failure diagnostics preserve root cause",
  () => {
    const output =
`Running Gradle task 'assembleRelease'...
FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':app:compileReleaseJavaWithJavac'.
> Could not resolve dependency.
Caused by: java.lang.IllegalStateException: TEST_ROOT_CAUSE
Gradle task assembleRelease failed with exit code 1`;

    const diagnostics =
      extractFlutterFailureDiagnostics(
        output
      );

    const joined =
      diagnostics.join(
        "\n"
      );

    assert.match(
      joined,
      /What went wrong/
    );

    assert.match(
      joined,
      /Execution failed/
    );

    assert.match(
      joined,
      /TEST_ROOT_CAUSE/
    );
  }
);


test(
  "Flutter verbose output keeps final failure tail",
  () => {
    const prefix =
      "OLD_DATA_".repeat(
        20
      );

    const rootCause =
      "FINAL_GRADLE_ROOT_CAUSE";

    const retained =
      appendFlutterOutputTail(
        prefix,
        rootCause,
        64
      );

    assert.ok(
      retained.length <=
        64
    );

    assert.match(
      retained,
      /FINAL_GRADLE_ROOT_CAUSE/
    );

    assert.doesNotMatch(
      retained,
      /^OLD_DATA_OLD_DATA_/
    );
  }
);
