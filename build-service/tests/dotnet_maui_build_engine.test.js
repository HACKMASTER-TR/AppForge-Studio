import test from "node:test";
import assert from "node:assert/strict";
import {
  promises as fs
} from "fs";
import os from "os";
import path from "path";
import AdmZip from "adm-zip";

import {
  prepareDotnetMauiSource
} from "../src/dotnetMauiBuildEngine.js";

test(
  ".NET MAUI Android project is detected and prepared",
  async () => {
    const root =
      await fs.mkdtemp(
        path.join(
          os.tmpdir(),
          "appforge-maui-"
        )
      );

    try {
      const project =
        path.join(
          root,
          "project"
        );

      await fs.mkdir(
        project,
        {
          recursive: true
        }
      );

      await fs.writeFile(
        path.join(
          project,
          "SampleMaui.csproj"
        ),
`<Project Sdk="Microsoft.NET.Sdk.Razor">
  <PropertyGroup>
    <TargetFrameworks>net10.0-android;net10.0-ios</TargetFrameworks>
    <UseMaui>true</UseMaui>
    <SingleProject>true</SingleProject>
    <ApplicationTitle>Sample MAUI</ApplicationTitle>
    <ApplicationId>com.example.samplemaui</ApplicationId>
    <ApplicationDisplayVersion>2.0</ApplicationDisplayVersion>
    <ApplicationVersion>7</ApplicationVersion>
  </PropertyGroup>
</Project>
`
      );

      await fs.writeFile(
        path.join(
          project,
          "MauiProgram.cs"
        ),
`using Microsoft.Maui;
namespace SampleMaui;
public static class MauiProgram {}
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
        await prepareDotnetMauiSource(
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
        "SampleMaui"
      );

      assert.equal(
        prepared.androidReady,
        true
      );

      assert.deepEqual(
        prepared.androidTargets,
        [
          "net10.0-android"
        ]
      );

      assert.equal(
        prepared.applicationId,
        "com.example.samplemaui"
      );

      assert.equal(
        prepared.applicationVersion,
        "7"
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
  ".NET MAUI project without Android target stays not Android-ready",
  async () => {
    const root =
      await fs.mkdtemp(
        path.join(
          os.tmpdir(),
          "appforge-maui-noandroid-"
        )
      );

    try {
      const project =
        path.join(
          root,
          "project"
        );

      await fs.mkdir(
        project,
        {
          recursive: true
        }
      );

      await fs.writeFile(
        path.join(
          project,
          "NoAndroid.csproj"
        ),
`<Project Sdk="Microsoft.NET.Sdk">
  <PropertyGroup>
    <TargetFramework>net10.0-ios</TargetFramework>
    <UseMaui>true</UseMaui>
  </PropertyGroup>
</Project>
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
        await prepareDotnetMauiSource(
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
        false
      );

      assert.deepEqual(
        prepared.androidTargets,
        []
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
