import assert from "node:assert/strict";
import {
  promises as fs
} from "fs";
import {
  spawn
} from "child_process";
import os from "os";
import path from "path";

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
              DOTNET_CLI_TELEMETRY_OPTOUT:
                "1",
              DOTNET_NOLOGO:
                "1"
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
                `.NET MAUI smoke command exit=${code}`
              )
            );
          }
        }
      );
    }
  );
}

async function findApk(
  root
) {
  const result =
    [];

  async function visit(
    dir
  ) {
    let entries;

    try {
      entries =
        await fs.readdir(
          dir,
          {
            withFileTypes:
              true
          }
        );
    } catch {
      return;
    }

    for (
      const entry of
      entries
    ) {
      const full =
        path.join(
          dir,
          entry.name
        );

      if (
        entry.isDirectory()
      ) {
        await visit(
          full
        );
      } else if (
        entry.isFile() &&
        entry.name
          .toLowerCase()
          .endsWith(
            ".apk"
          )
      ) {
        result.push(
          full
        );
      }
    }
  }

  await visit(
    root
  );

  return result[0] ||
    null;
}

const root =
  await fs.mkdtemp(
    path.join(
      os.tmpdir(),
      "appforge-maui-smoke-"
    )
  );

try {
  const project =
    path.join(
      root,
      "AppForgeMauiSmoke"
    );

  await run(
    "dotnet",
    [
      "new",
      "maui",
      "-n",
      "AppForgeMauiSmoke",
      "-o",
      project,
      "--no-restore"
    ],
    root
  );

  const projectFile =
    path.join(
      project,
      "AppForgeMauiSmoke.csproj"
    );

  await run(
    "dotnet",
    [
      "restore",
      projectFile,
      "-p:TargetFrameworks=net10.0-android"
    ],
    project
  );

  await run(
    "dotnet",
    [
      "publish",
      projectFile,
      "-f",
      "net10.0-android",
      "-c",
      "Release",
      "--no-restore",
      "-p:TargetFrameworks=net10.0-android",
      "-p:AndroidPackageFormats=apk",
      "-p:ApplicationId=com.appforge.mauismoke",
      "-p:ApplicationVersion=1",
      "-p:ApplicationDisplayVersion=1.0.0",
      "-p:AndroidKeyStore=true",
      "-p:AndroidSigningKeyStore=/opt/appforge-source-debug.keystore",
      "-p:AndroidSigningStorePass=android",
      "-p:AndroidSigningKeyAlias=androiddebugkey",
      "-p:AndroidSigningKeyPass=android"
    ],
    project
  );

  const apk =
    await findApk(
      path.join(
        project,
        "bin",
        "Release"
      )
    );

  assert.ok(
    apk,
    "MAUI smoke APK bulunamadı"
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
    `.NET MAUI Android smoke APK OK: ${stat.size} bytes`
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
