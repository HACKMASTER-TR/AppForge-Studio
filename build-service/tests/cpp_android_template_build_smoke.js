import assert from "node:assert/strict";
import {
  promises as fs
} from "fs";
import {
  spawn
} from "child_process";
import os from "os";
import path from "path";
import AdmZip from "adm-zip";

import {
  prepareCppAndroidProject
} from "../src/cppAndroidBuildEngine.js";

function run(
  command,
  args,
  cwd
) {
  return new Promise(
    (
      resolve,
      reject
    ) => {
      const child =
        spawn(
          command,
          args,
          {
            cwd,
            env: {
              ...process.env,
              GRADLE_USER_HOME:
                "/root/.gradle"
            },
            stdio:
              "inherit"
          }
        );

      child.on(
        "error",
        reject
      );

      child.on(
        "close",
        code => {
          if (
            code ===
              0
          ) {
            resolve();
          } else {
            reject(
              new Error(
                `Gradle smoke build exit=${code}`
              )
            );
          }
        }
      );
    }
  );
}

const root =
  await fs.mkdtemp(
    path.join(
      os.tmpdir(),
      "appforge-cpp-smoke-"
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
      "appforge_main.cpp"
    ),
`extern "C" const char* appforge_run() {
    return "CPP_SMOKE_OK";
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
    source
  );

  zip.writeZip(
    zipPath
  );

  const android =
    path.join(
      root,
      "android"
    );

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
        android,
      config: {
        appName:
          "AppForge C++ Smoke",
        packageName:
          "com.appforge.cppsmoke",
        versionCode:
          1,
        versionName:
          "1.0.0"
      }
    }
  );

  await run(
    "gradle",
    [
      "-p",
      android,
      "--no-daemon",
      "--stacktrace",
      ":app:assembleRelease"
    ],
    android
  );

  const apk =
    path.join(
      android,
      "app",
      "build",
      "outputs",
      "apk",
      "release",
      "app-release.apk"
    );

  const stat =
    await fs.stat(
      apk
    );

  assert.ok(
    stat.size >
      0
  );

  console.log(
    `C++ Android smoke APK OK: ${stat.size} bytes`
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
