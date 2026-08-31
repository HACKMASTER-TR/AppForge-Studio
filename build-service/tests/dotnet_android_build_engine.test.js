import test from "node:test";
import assert from "node:assert/strict";
import {
  promises as fs
} from "fs";
import os from "os";
import path from "path";
import AdmZip from "adm-zip";

import {
  prepareDotnetAndroidSource
} from "../src/dotnetAndroidBuildEngine.js";

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
  ".NET Android net10 source is detected without MAUI",
  async () => {
    const root =
      await fs.mkdtemp(
        path.join(
          os.tmpdir(),
          "appforge-dotnet-android-"
        )
      );

    try {
      const zipPath =
        await makeZip(
          root,
          {
            "Demo.csproj":
`<Project Sdk="Microsoft.NET.Sdk">
  <PropertyGroup>
    <TargetFramework>net10.0-android</TargetFramework>
    <OutputType>Exe</OutputType>
    <ApplicationId>com.example.dotnetandroid</ApplicationId>
    <ApplicationTitle>Dotnet Android</ApplicationTitle>
    <ApplicationVersion>7</ApplicationVersion>
    <ApplicationDisplayVersion>1.7.0</ApplicationDisplayVersion>
  </PropertyGroup>
</Project>
`,
            "MainActivity.cs":
              "public class MainActivity {}\n"
          }
        );

      const prepared =
        await prepareDotnetAndroidSource(
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
        prepared.androidReady,
        true
      );

      assert.equal(
        prepared.useMaui,
        false
      );

      assert.deepEqual(
        prepared.net10AndroidTargets,
        [
          "net10.0-android"
        ]
      );

      assert.equal(
        prepared.applicationId,
        "com.example.dotnetandroid"
      );

      assert.equal(
        prepared.applicationTitle,
        "Dotnet Android"
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
  "MAUI project is not claimed by generic .NET Android engine",
  async () => {
    const root =
      await fs.mkdtemp(
        path.join(
          os.tmpdir(),
          "appforge-dotnet-maui-guard-"
        )
      );

    try {
      const zipPath =
        await makeZip(
          root,
          {
            "Maui.csproj":
`<Project Sdk="Microsoft.NET.Sdk">
  <PropertyGroup>
    <TargetFramework>net10.0-android</TargetFramework>
    <UseMaui>true</UseMaui>
  </PropertyGroup>
</Project>
`
          }
        );

      await assert.rejects(
        () =>
          prepareDotnetAndroidSource(
            {
              projectZip:
                zipPath,
              workDir:
                path.join(
                  root,
                  "work"
                )
            }
          ),
        /MAUI olmayan/
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
