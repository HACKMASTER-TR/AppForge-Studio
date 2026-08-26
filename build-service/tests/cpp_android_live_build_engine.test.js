import test from "node:test";
import assert from "node:assert/strict";
import {
  promises as fs
} from "fs";
import os from "os";
import path from "path";
import AdmZip from "adm-zip";

import {
  prepareCppAndroidProject,
  cppAndroidBuildReady
} from "../src/cppAndroidBuildEngine.js";

async function createSourceZip(
  root
) {
  const src =
    path.join(
      root,
      "source"
    );

  await fs.mkdir(
    path.join(
      src,
      "include"
    ),
    {
      recursive: true
    }
  );

  await fs.writeFile(
    path.join(
      src,
      "appforge_main.cpp"
    ),
`#include "include/message.hpp"

extern "C" const char* appforge_run() {
    return appforge_message();
}
`
  );

  await fs.writeFile(
    path.join(
      src,
      "include",
      "message.hpp"
    ),
`#pragma once
inline const char* appforge_message() {
    return "CPP_ANDROID_OK";
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
    src
  );

  zip.writeZip(
    zipPath
  );

  return zipPath;
}

test(
  "C++ source becomes AppForge JNI Android project",
  async () => {
    const root =
      await fs.mkdtemp(
        path.join(
          os.tmpdir(),
          "appforge-cpp-live-"
        )
      );

    try {
      const zipPath =
        await createSourceZip(
          root
        );

      const result =
        await prepareCppAndroidProject(
          {
            projectZip:
              zipPath,
            workDir:
              path.join(
                root,
                "work"
              ),
            androidProjectDir:
              path.join(
                root,
                "android"
              ),
            config: {
              appName:
                "C++ Test",
              packageName:
                "com.example.cpptest",
              versionCode:
                7,
              versionName:
                "2.1.0"
            }
          }
        );

      assert.equal(
        result.mode,
        "appforge-jni-wrapper"
      );

      assert.ok(
        result.copiedFiles >=
          2
      );

      const appBuild =
        await fs.readFile(
          path.join(
            root,
            "android",
            "app",
            "build.gradle.kts"
          ),
          "utf8"
        );

      assert.match(
        appBuild,
        /applicationId = "com\.example\.cpptest"/
      );

      assert.match(
        appBuild,
        /versionCode = 7/
      );

      assert.match(
        appBuild,
        /ndkVersion = "28\.2\.13676358"/
      );

      assert.match(
        appBuild,
        /version = "3\.22\.1"/
      );

      const cmake =
        await fs.readFile(
          path.join(
            root,
            "android",
            "app",
            "src",
            "main",
            "cpp",
            "CMakeLists.txt"
          ),
          "utf8"
        );

      assert.match(
        cmake,
        /appforge_jni\.cpp/
      );

      assert.doesNotMatch(
        cmake,
        /add_subdirectory/
      );

      const copiedEntry =
        await fs.readFile(
          path.join(
            root,
            "android",
            "app",
            "src",
            "main",
            "cpp",
            "user",
            "appforge_main.cpp"
          ),
          "utf8"
        );

      assert.match(
        copiedEntry,
        /appforge_run/
      );

      assert.equal(
        cppAndroidBuildReady(
          {
            appForgeEntry:
              "/tmp/appforge_main.cpp"
          }
        ),
        true
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
