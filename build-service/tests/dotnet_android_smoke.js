import assert from "node:assert/strict";
import {
  promises as fs
} from "fs";
import os from "os";
import path from "path";
import AdmZip from "adm-zip";

import {
  prepareDotnetAndroidSource,
  buildDotnetAndroidArtifacts
} from "../src/dotnetAndroidBuildEngine.js";

const root =
  await fs.mkdtemp(
    path.join(
      os.tmpdir(),
      "appforge-dotnet-android-smoke-"
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
      "Smoke.csproj"
    ),
`<Project Sdk="Microsoft.NET.Sdk">
  <PropertyGroup>
    <TargetFramework>net10.0-android</TargetFramework>
    <OutputType>Exe</OutputType>
    <Nullable>enable</Nullable>
    <ImplicitUsings>enable</ImplicitUsings>
    <ApplicationId>com.appforge.dotnetandroidsmoke</ApplicationId>
    <ApplicationTitle>AppForge Dotnet Android Smoke</ApplicationTitle>
    <ApplicationVersion>1</ApplicationVersion>
    <ApplicationDisplayVersion>1.0.0</ApplicationDisplayVersion>
  </PropertyGroup>
</Project>
`
  );

  await fs.writeFile(
    path.join(
      source,
      "MainActivity.cs"
    ),
`using Android.App;
using Android.OS;
using Android.Widget;

namespace AppForge.DotnetAndroidSmoke;

[Activity(
    Label = "AppForge Dotnet Android Smoke",
    MainLauncher = true,
    Exported = true
)]
public class MainActivity : Activity
{
    protected override void OnCreate(Bundle? savedInstanceState)
    {
        base.OnCreate(savedInstanceState);

        SetContentView(
            new TextView(this)
            {
                Text = "APPFORGE_DOTNET_ANDROID_SMOKE_OK"
            }
        );
    }
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

  const artifacts =
    await buildDotnetAndroidArtifacts(
      {
        prepared,
        outputType:
          "apk",
        packageName:
          "com.appforge.dotnetandroidsmoke",
        versionCode:
          1,
        versionName:
          "1.0.0",
        appName:
          "AppForge Dotnet Android Smoke"
      }
    );

  assert.ok(
    artifacts.apk
  );

  const stat =
    await fs.stat(
      artifacts.apk
    );

  assert.ok(
    stat.size >
      0
  );

  console.log(
    `.NET Android smoke APK OK: ${stat.size} bytes`
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
