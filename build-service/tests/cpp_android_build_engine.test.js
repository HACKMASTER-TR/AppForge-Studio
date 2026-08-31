import test from "node:test";
import assert from "node:assert/strict";
import {
  promises as fs
} from "fs";
import os from "os";
import path from "path";
import AdmZip from "adm-zip";

import {
  prepareCppAndroidSource
} from "../src/cppAndroidBuildEngine.js";

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
  "C++ AppForge native entry project is classified",
  async () => {
    const root =
      await fs.mkdtemp(
        path.join(
          os.tmpdir(),
          "appforge-cpp-"
        )
      );

    try {
      const zipPath =
        await makeZip(
          root,
          {
            "CMakeLists.txt":
`cmake_minimum_required(VERSION 3.22.1)
project(AppForgeNative)
add_library(appforge_native SHARED appforge_main.cpp)
`,
            "appforge_main.cpp":
`extern "C" const char* appforge_run() {
    return "CPP_OK";
}
`
          }
        );

      const prepared =
        await prepareCppAndroidSource(
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
        prepared.mode,
        "appforge-native-entry"
      );

      assert.ok(
        prepared.cmakeFile
      );

      assert.ok(
        prepared.appForgeEntry
      );

      assert.equal(
        prepared.cppFiles.length,
        1
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
  "Generic CMake project stays generic until Android wrapper is generated",
  async () => {
    const root =
      await fs.mkdtemp(
        path.join(
          os.tmpdir(),
          "appforge-cmake-"
        )
      );

    try {
      const zipPath =
        await makeZip(
          root,
          {
            "CMakeLists.txt":
`cmake_minimum_required(VERSION 3.22.1)
project(GenericNative)
add_library(generic SHARED main.cpp)
`,
            "main.cpp":
              "int answer() { return 42; }\n"
          }
        );

      const prepared =
        await prepareCppAndroidSource(
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
        prepared.mode,
        "cmake-generic"
      );

      assert.equal(
        prepared.appForgeEntry,
        null
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
