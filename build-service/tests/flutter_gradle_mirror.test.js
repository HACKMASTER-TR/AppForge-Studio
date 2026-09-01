import test from "node:test";
import assert from "node:assert/strict";

import {
  promises as fs
} from "fs";

import os from "os";
import path from "path";

import {
  patchFlutterSettingsForWritablePlugin,
  prepareWritableFlutterGradlePlugin
} from "../src/flutterGradleMirror.js";

test(
  "Flutter Kotlin settings uses writable Gradle mirror",
  () => {
    const source =
`pluginManagement {
    val flutterSdkPath = "/opt/flutter"
    includeBuild("$flutterSdkPath/packages/flutter_tools/gradle")
}
`;

    const result =
      patchFlutterSettingsForWritablePlugin(
        source
      );

    assert.equal(
      result.usesMirror,
      true
    );

    assert.equal(
      result.changed,
      true
    );

    assert.match(
      result.content,
      /includeBuild\("\.\.\/\.appforge\/flutter-tools-gradle"\)/
    );
  }
);

test(
  "Flutter Groovy settings uses writable Gradle mirror",
  () => {
    const source =
`pluginManagement {
    includeBuild "$flutterSdkPath/packages/flutter_tools/gradle"
}
`;

    const result =
      patchFlutterSettingsForWritablePlugin(
        source
      );

    assert.equal(
      result.usesMirror,
      true
    );

    assert.equal(
      result.changed,
      true
    );
  }
);

test(
  "Flutter Gradle plugin is copied from trusted SDK",
  async () => {
    const root =
      await fs.mkdtemp(
        path.join(
          os.tmpdir(),
          "appforge-flutter-mirror-"
        )
      );

    try {
      const flutterRoot =
        path.join(
          root,
          "flutter"
        );

      const trustedGradle =
        path.join(
          flutterRoot,
          "packages",
          "flutter_tools",
          "gradle"
        );

      await fs.mkdir(
        trustedGradle,
        {
          recursive: true
        }
      );

      await fs.writeFile(
        path.join(
          trustedGradle,
          "build.gradle.kts"
        ),
        "// TRUSTED_FLUTTER_GRADLE\n",
        "utf8"
      );

      const projectRoot =
        path.join(
          root,
          "project"
        );

      const androidDir =
        path.join(
          projectRoot,
          "android"
        );

      await fs.mkdir(
        androidDir,
        {
          recursive: true
        }
      );

      await fs.writeFile(
        path.join(
          androidDir,
          "settings.gradle.kts"
        ),
`pluginManagement {
    val flutterSdkPath = "/opt/flutter"
    includeBuild("$flutterSdkPath/packages/flutter_tools/gradle")
}
`,
        "utf8"
      );

      const result =
        await prepareWritableFlutterGradlePlugin(
          {
            projectRoot,
            flutterRoot
          }
        );

      assert.equal(
        result.mirrored,
        true
      );

      const mirrorFile =
        await fs.readFile(
          path.join(
            projectRoot,
            ".appforge",
            "flutter-tools-gradle",
            "build.gradle.kts"
          ),
          "utf8"
        );

      assert.match(
        mirrorFile,
        /TRUSTED_FLUTTER_GRADLE/
      );

      const settings =
        await fs.readFile(
          path.join(
            androidDir,
            "settings.gradle.kts"
          ),
          "utf8"
        );

      assert.match(
        settings,
        /includeBuild\("\.\.\/\.appforge\/flutter-tools-gradle"\)/
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
