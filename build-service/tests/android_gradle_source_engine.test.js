import test from "node:test";
import assert from "node:assert/strict";
import { promises as fs } from "fs";
import os from "os";
import path from "path";
import AdmZip from "adm-zip";

import {
  prepareAndroidGradleProject
} from "../src/sourceBuildEngines.js";

test(
  "Android Gradle source ZIP becomes a buildable app module",
  async () => {
    const root =
      await fs.mkdtemp(
        path.join(
          os.tmpdir(),
          "appforge-android-gradle-"
        )
      );

    try {
      const source =
        path.join(
          root,
          "source"
        );

      await fs.mkdir(
        path.join(
          source,
          "app",
          "src",
          "main",
          "java",
          "com",
          "example",
          "sourceapp"
        ),
        {
          recursive: true
        }
      );

      await fs.mkdir(
        path.join(
          source,
          "app",
          "src",
          "main",
          "res",
          "values"
        ),
        {
          recursive: true
        }
      );

      await fs.writeFile(
        path.join(
          source,
          "settings.gradle.kts"
        ),
`pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "SourceApp"
include(":app")
`
      );

      await fs.writeFile(
        path.join(
          source,
          "build.gradle.kts"
        ),
`plugins {
    id("com.android.application") version "9.1.1" apply false
}
`
      );

      await fs.writeFile(
        path.join(
          source,
          "app",
          "build.gradle.kts"
        ),
`plugins {
    id("com.android.application")
}
android {
    namespace = "com.example.sourceapp"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.example.sourceapp"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }
}
`
      );

      await fs.writeFile(
        path.join(
          source,
          "app",
          "src",
          "main",
          "AndroidManifest.xml"
        ),
`<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:theme="@style/AppTheme">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
`
      );

      await fs.writeFile(
        path.join(
          source,
          "app",
          "src",
          "main",
          "res",
          "values",
          "styles.xml"
        ),
`<resources>
    <style name="AppTheme" parent="android:style/Theme.Material.Light.NoActionBar" />
</resources>
`
      );

      await fs.writeFile(
        path.join(
          source,
          "app",
          "src",
          "main",
          "java",
          "com",
          "example",
          "sourceapp",
          "MainActivity.java"
        ),
`package com.example.sourceapp;
import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView view = new TextView(this);
        view.setText("ANDROID_SOURCE_OK");
        setContentView(view);
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

      const androidDir =
        path.join(
          root,
          "android"
        );

      const result =
        await prepareAndroidGradleProject(
          {
            projectZip:
              zipPath,
            androidProjectDir:
              androidDir,
            config: {
              packageName:
                "com.example.overridden",
              versionCode:
                9,
              versionName:
                "2.0.0",
              signing: {
                mode:
                  "DEBUG"
              }
            }
          }
        );

      assert.equal(
        result.module,
        "app"
      );

      assert.equal(
        result.kotlinDsl,
        true
      );

      const appBuild =
        await fs.readFile(
          path.join(
            androidDir,
            "app",
            "build.gradle.kts"
          ),
          "utf8"
        );

      assert.match(
        appBuild,
        /appforge-source-overrides\.gradle/
      );

      const override =
        await fs.readFile(
          path.join(
            androidDir,
            "appforge-source-overrides.gradle"
          ),
          "utf8"
        );

      assert.match(
        override,
        /applicationId 'com\.example\.overridden'/
      );

      assert.match(
        override,
        /versionCode 9/
      );

      assert.match(
        override,
        /versionName '2\.0\.0'/
      );

      assert.match(
        override,
        /signingConfig signingConfigs\.debug/
      );

      const copiedJava =
        await fs.readFile(
          path.join(
            androidDir,
            "app",
            "src",
            "main",
            "java",
            "com",
            "example",
            "sourceapp",
            "MainActivity.java"
          ),
          "utf8"
        );

      assert.match(
        copiedJava,
        /ANDROID_SOURCE_OK/
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
