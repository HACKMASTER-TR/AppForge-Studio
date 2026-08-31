import AdmZip from "adm-zip";
import {
  promises as fs
} from "fs";
import path from "path";

const MAX_ZIP_ENTRIES =
  12_000;

const MAX_UNCOMPRESSED_BYTES =
  300 * 1024 * 1024;

const CPP_EXTENSIONS =
  new Set([
    ".c",
    ".cc",
    ".cpp",
    ".cxx",
    ".h",
    ".hh",
    ".hpp",
    ".hxx"
  ]);

function safeInside(
  root,
  candidate
) {
  const resolvedRoot =
    path.resolve(
      root
    );

  const resolvedCandidate =
    path.resolve(
      candidate
    );

  return (
    resolvedCandidate ===
      resolvedRoot ||
    resolvedCandidate.startsWith(
      resolvedRoot +
        path.sep
    )
  );
}

function ignoredPath(
  segments
) {
  const ignored =
    new Set([
      ".git",
      ".idea",
      ".gradle",
      "build",
      "out",
      "cmake-build-debug",
      "cmake-build-release"
    ]);

  return segments.some(
    segment =>
      ignored.has(
        segment
      )
  );
}

async function extractCppZip(
  zipPath,
  destination
) {
  const zip =
    new AdmZip(
      zipPath
    );

  const entries =
    zip.getEntries();

  if (
    entries.length >
      MAX_ZIP_ENTRIES
  ) {
    throw new Error(
      "C/C++ projesinde çok fazla ZIP girdisi var."
    );
  }

  await fs.rm(
    destination,
    {
      recursive: true,
      force: true
    }
  );

  await fs.mkdir(
    destination,
    {
      recursive: true
    }
  );

  let totalBytes =
    0;

  for (
    const entry of
    entries
  ) {
    const rawName =
      String(
        entry.entryName ||
        ""
      )
        .replaceAll(
          "\\",
          "/"
        );

    if (
      !rawName ||
      rawName.startsWith(
        "/"
      ) ||
      rawName.includes(
        "\0"
      )
    ) {
      throw new Error(
        "C/C++ ZIP yolu güvenli değil."
      );
    }

    const normalized =
      path.posix.normalize(
        rawName
      );

    if (
      normalized ===
        ".." ||
      normalized.startsWith(
        "../"
      )
    ) {
      throw new Error(
        "C/C++ ZIP dizin dışına çıkmaya çalışıyor."
      );
    }

    const segments =
      normalized
        .split(
          "/"
        )
        .filter(
          Boolean
        );

    if (
      !segments.length ||
      ignoredPath(
        segments
      )
    ) {
      continue;
    }

    const target =
      path.join(
        destination,
        ...segments
      );

    if (
      !safeInside(
        destination,
        target
      )
    ) {
      throw new Error(
        "C/C++ ZIP hedef yolu güvenli değil."
      );
    }

    if (
      entry.isDirectory
    ) {
      await fs.mkdir(
        target,
        {
          recursive: true
        }
      );
      continue;
    }

    const data =
      entry.getData();

    totalBytes +=
      data.length;

    if (
      totalBytes >
        MAX_UNCOMPRESSED_BYTES
    ) {
      throw new Error(
        "C/C++ proje ZIP'i açıldığında boyut sınırını aşıyor."
      );
    }

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
      data
    );
  }

  return {
    entries:
      entries.length,
    bytes:
      totalBytes
  };
}

async function walk(
  root,
  {
    maxDepth = 8,
    maxFiles = 10_000
  } = {}
) {
  const result =
    [];

  async function visit(
    dir,
    depth
  ) {
    if (
      depth >
        maxDepth ||
      result.length >=
        maxFiles
    ) {
      return;
    }

    const entries =
      await fs.readdir(
        dir,
        {
          withFileTypes:
            true
        }
      );

    for (
      const entry of
      entries
    ) {
      if (
        result.length >=
          maxFiles
      ) {
        break;
      }

      if (
        [
          ".git",
          ".idea",
          ".gradle",
          "build",
          "out",
          "cmake-build-debug",
          "cmake-build-release"
        ].includes(
          entry.name
        )
      ) {
        continue;
      }

      const full =
        path.join(
          dir,
          entry.name
        );

      if (
        entry.isDirectory()
      ) {
        await visit(
          full,
          depth + 1
        );
      } else if (
        entry.isFile()
      ) {
        result.push(
          full
        );
      }
    }
  }

  await visit(
    root,
    0
  );

  return result;
}

async function findCppProjectRoot(
  extractedRoot
) {
  const files =
    await walk(
      extractedRoot
    );

  const cmakeFiles =
    files
      .filter(
        file =>
          path.basename(
            file
          )
            .toLowerCase() ===
          "cmakelists.txt"
      )
      .sort(
        (
          a,
          b
        ) =>
          a.split(
            path.sep
          ).length -
          b.split(
            path.sep
          ).length
      );

  if (
    cmakeFiles.length
  ) {
    return {
      projectRoot:
        path.dirname(
          cmakeFiles[0]
        ),
      cmakeFile:
        cmakeFiles[0],
      files
    };
  }

  const cppFiles =
    files
      .filter(
        file =>
          CPP_EXTENSIONS.has(
            path.extname(
              file
            )
              .toLowerCase()
          )
      );

  if (
    cppFiles.length
  ) {
    const root =
      path.dirname(
        cppFiles
          .sort(
            (
              a,
              b
            ) =>
              a.split(
                path.sep
              ).length -
              b.split(
                path.sep
              ).length
          )[0]
      );

    return {
      projectRoot:
        root,
      cmakeFile:
        null,
      files
    };
  }

  throw new Error(
    "C/C++ kaynak dosyası veya CMakeLists.txt bulunamadı."
  );
}

function relativeNames(
  root,
  files
) {
  return files
    .map(
      file =>
        path.relative(
          root,
          file
        )
          .replaceAll(
            "\\",
            "/"
          )
          .toLowerCase()
    );
}

export async function prepareCppAndroidSource({
  projectZip,
  workDir,
  onLog = null,
  cancelled = null
}) {
  if (
    !projectZip
  ) {
    throw new Error(
      "C/C++ kaynak ZIP'i eksik."
    );
  }

  const extractedRoot =
    path.join(
      workDir,
      "source"
    );

  if (
    cancelled
  ) {
    await cancelled();
  }

  const extracted =
    await extractCppZip(
      projectZip,
      extractedRoot
    );

  const found =
    await findCppProjectRoot(
      extractedRoot
    );

  const names =
    relativeNames(
      found.projectRoot,
      found.files
    );

  const cppFiles =
    found.files.filter(
      file =>
        CPP_EXTENSIONS.has(
          path.extname(
            file
          )
            .toLowerCase()
        )
    );

  const appForgeEntry =
    cppFiles.find(
      file =>
        [
          "appforge_main.c",
          "appforge_main.cc",
          "appforge_main.cpp",
          "appforge_main.cxx"
        ]
          .includes(
            path.basename(
              file
            )
              .toLowerCase()
          )
    ) ||
    null;

  const existingAndroidProject =
    (
      names.includes(
        "android/settings.gradle"
      ) ||
      names.includes(
        "android/settings.gradle.kts"
      ) ||
      names.includes(
        "settings.gradle"
      ) ||
      names.includes(
        "settings.gradle.kts"
      )
    ) &&
    names.some(
      name =>
        name.endsWith(
          "app/src/main/androidmanifest.xml"
        )
    );

  const mode =
    existingAndroidProject
      ? "android-gradle-native"
      : (
          appForgeEntry
            ? "appforge-native-entry"
            : (
                found.cmakeFile
                  ? "cmake-generic"
                  : "cpp-generic"
              )
        );

  if (
    onLog
  ) {
    await onLog(
      `🧩 C/C++ kaynak hazır • ${mode} • ${cppFiles.length} native dosya`
    );
  }

  return {
    projectRoot:
      found.projectRoot,
    cmakeFile:
      found.cmakeFile,
    cppFiles,
    appForgeEntry,
    existingAndroidProject,
    mode,
    extractedEntries:
      extracted.entries,
    extractedBytes:
      extracted.bytes
  };
}

const COPY_NATIVE_EXTENSIONS =
  new Set([
    ".c",
    ".cc",
    ".cpp",
    ".cxx",
    ".h",
    ".hh",
    ".hpp",
    ".hxx",
    ".inc",
    ".inl",
    ".ipp"
  ]);

const MAX_NATIVE_FILE_BYTES =
  24 * 1024 * 1024;

const MAX_NATIVE_COPY_BYTES =
  180 * 1024 * 1024;

function kotlinString(
  value
) {
  return String(
    value ?? ""
  )
    .replaceAll(
      "\\",
      "\\\\"
    )
    .replaceAll(
      "\"",
      "\\\""
    )
    .replaceAll(
      "$",
      "\\$"
    )
    .replaceAll(
      "\n",
      "\\n"
    )
    .replaceAll(
      "\r",
      ""
    );
}

function xmlText(
  value
) {
  return String(
    value ?? ""
  )
    .replaceAll(
      "&",
      "&amp;"
    )
    .replaceAll(
      "<",
      "&lt;"
    )
    .replaceAll(
      ">",
      "&gt;"
    )
    .replaceAll(
      "\"",
      "&quot;"
    )
    .replaceAll(
      "'",
      "&apos;"
    );
}

async function copyNativeSources(
  prepared,
  destination
) {
  const files =
    await walk(
      prepared.projectRoot,
      {
        maxDepth: 10,
        maxFiles: 12_000
      }
    );

  const nativeFiles =
    files.filter(
      file =>
        COPY_NATIVE_EXTENSIONS.has(
          path.extname(
            file
          )
            .toLowerCase()
        )
    );

  let totalBytes =
    0;

  let copied =
    0;

  for (
    const file of
    nativeFiles
  ) {
    const stat =
      await fs.stat(
        file
      );

    if (
      stat.size >
        MAX_NATIVE_FILE_BYTES
    ) {
      throw new Error(
        `C/C++ kaynak dosyası çok büyük: ${path.basename(file)}`
      );
    }

    totalBytes +=
      stat.size;

    if (
      totalBytes >
        MAX_NATIVE_COPY_BYTES
    ) {
      throw new Error(
        "C/C++ Android wrapper'a kopyalanan kaynaklar boyut sınırını aşıyor."
      );
    }

    const relative =
      path.relative(
        prepared.projectRoot,
        file
      );

    const target =
      path.join(
        destination,
        relative
      );

    if (
      !safeInside(
        destination,
        target
      )
    ) {
      throw new Error(
        "C/C++ kaynak kopyalama yolu güvenli değil."
      );
    }

    await fs.mkdir(
      path.dirname(
        target
      ),
      {
        recursive: true
      }
    );

    await fs.copyFile(
      file,
      target
    );

    copied +=
      1;
  }

  if (
    !copied
  ) {
    throw new Error(
      "Android wrapper'a aktarılacak C/C++ kaynak bulunamadı."
    );
  }

  return {
    copied,
    bytes:
      totalBytes
  };
}

export function cppAndroidBuildReady(
  prepared
) {
  return Boolean(
    prepared?.appForgeEntry
  );
}

export async function prepareCppAndroidProject({
  projectZip,
  workDir,
  androidProjectDir,
  config,
  onLog = null,
  cancelled = null
}) {
  const prepared =
    await prepareCppAndroidSource(
      {
        projectZip,
        workDir,
        onLog,
        cancelled
      }
    );

  if (
    !cppAndroidBuildReady(
      prepared
    )
  ) {
    throw new Error(
      "C/C++ Android motoru için appforge_main.c/cpp giriş dosyası gerekli. " +
      "Bu dosya extern \"C\" const char* appforge_run() fonksiyonunu sağlamalı."
    );
  }

  if (
    cancelled
  ) {
    await cancelled();
  }

  await fs.rm(
    androidProjectDir,
    {
      recursive: true,
      force: true
    }
  );

  const appDir =
    path.join(
      androidProjectDir,
      "app"
    );

  const javaDir =
    path.join(
      appDir,
      "src",
      "main",
      "java",
      "com",
      "appforge",
      "ndkruntime"
    );

  const valuesDir =
    path.join(
      appDir,
      "src",
      "main",
      "res",
      "values"
    );

  const cppDir =
    path.join(
      appDir,
      "src",
      "main",
      "cpp"
    );

  const userDir =
    path.join(
      cppDir,
      "user"
    );

  for (
    const dir of [
      javaDir,
      valuesDir,
      userDir
    ]
  ) {
    await fs.mkdir(
      dir,
      {
        recursive: true
      }
    );
  }

  const copied =
    await copyNativeSources(
      prepared,
      userDir
    );

  const appName =
    xmlText(
      config?.appName ||
      "AppForge Native"
    );

  const packageName =
    String(
      config?.packageName ||
      "com.appforge.nativeapp"
    );

  const versionCode =
    Number(
      config?.versionCode
    ) ||
    1;

  const versionName =
    kotlinString(
      config?.versionName ||
      "1.0.0"
    );

  await fs.writeFile(
    path.join(
      androidProjectDir,
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

rootProject.name = "AppForgeNativeRuntime"
include(":app")
`,
    "utf8"
  );

  await fs.writeFile(
    path.join(
      androidProjectDir,
      "build.gradle.kts"
    ),
`plugins {
    id("com.android.application") version "9.1.1" apply false
}
`,
    "utf8"
  );

  await fs.writeFile(
    path.join(
      androidProjectDir,
      "gradle.properties"
    ),
`org.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8
org.gradle.daemon=false
android.useAndroidX=true
`,
    "utf8"
  );

  await fs.writeFile(
    path.join(
      appDir,
      "build.gradle.kts"
    ),
`plugins {
    id("com.android.application")
}

android {
    namespace = "com.appforge.ndkruntime"
    compileSdk = 37
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "${kotlinString(packageName)}"
        minSdk = 26
        targetSdk = 37
        versionCode = ${versionCode}
        versionName = "${versionName}"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-fexceptions", "-frtti")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
`,
    "utf8"
  );

  await fs.writeFile(
    path.join(
      appDir,
      "src",
      "main",
      "AndroidManifest.xml"
    ),
`<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:allowBackup="false"
        android:label="${appName}"
        android:theme="@style/AppTheme">
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
`,
    "utf8"
  );

  await fs.writeFile(
    path.join(
      valuesDir,
      "styles.xml"
    ),
`<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="AppTheme" parent="android:style/Theme.Material.Light.NoActionBar">
        <item name="android:fontFamily">sans</item>
        <item name="android:windowLightStatusBar">true</item>
    </style>
</resources>
`,
    "utf8"
  );

  await fs.writeFile(
    path.join(
      javaDir,
      "NativeBridge.java"
    ),
`package com.appforge.ndkruntime;

public final class NativeBridge {
    static {
        System.loadLibrary("appforge_native");
    }

    private NativeBridge() {}

    public static native String run();
}
`,
    "utf8"
  );

  await fs.writeFile(
    path.join(
      javaDir,
      "MainActivity.java"
    ),
`package com.appforge.ndkruntime;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService executor =
        Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        TextView view = new TextView(this);
        view.setGravity(Gravity.CENTER);
        view.setPadding(32, 32, 32, 32);
        view.setText("C/C++ başlatılıyor...");
        setContentView(view);

        executor.execute(() -> {
            String result;

            try {
                result = NativeBridge.run();
            } catch (Throwable error) {
                result = "Native hata: " + error;
            }

            final String text =
                result == null || result.isBlank()
                    ? "C/C++ tamamlandı."
                    : result;

            runOnUiThread(() -> view.setText(text));
        });
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
`,
    "utf8"
  );

  await fs.writeFile(
    path.join(
      cppDir,
      "appforge_jni.cpp"
    ),
`#include <jni.h>
#include <string>

extern "C" const char* appforge_run();

extern "C"
JNIEXPORT jstring JNICALL
Java_com_appforge_ndkruntime_NativeBridge_run(
    JNIEnv* env,
    jclass
) {
    const char* result = appforge_run();

    if (result == nullptr) {
        result = "";
    }

    return env->NewStringUTF(result);
}
`,
    "utf8"
  );

  await fs.writeFile(
    path.join(
      cppDir,
      "CMakeLists.txt"
    ),
`cmake_minimum_required(VERSION 3.22.1)
project(AppForgeNativeRuntime LANGUAGES C CXX)

file(
    GLOB_RECURSE
    APPFORGE_USER_SOURCES
    CONFIGURE_DEPENDS
    "\${CMAKE_CURRENT_SOURCE_DIR}/user/*.c"
    "\${CMAKE_CURRENT_SOURCE_DIR}/user/*.cc"
    "\${CMAKE_CURRENT_SOURCE_DIR}/user/*.cpp"
    "\${CMAKE_CURRENT_SOURCE_DIR}/user/*.cxx"
)

file(
    GLOB_RECURSE
    APPFORGE_USER_HEADERS
    CONFIGURE_DEPENDS
    "\${CMAKE_CURRENT_SOURCE_DIR}/user/*.h"
    "\${CMAKE_CURRENT_SOURCE_DIR}/user/*.hh"
    "\${CMAKE_CURRENT_SOURCE_DIR}/user/*.hpp"
    "\${CMAKE_CURRENT_SOURCE_DIR}/user/*.hxx"
    "\${CMAKE_CURRENT_SOURCE_DIR}/user/*.inc"
    "\${CMAKE_CURRENT_SOURCE_DIR}/user/*.inl"
    "\${CMAKE_CURRENT_SOURCE_DIR}/user/*.ipp"
)

add_library(
    appforge_native
    SHARED
    appforge_jni.cpp
    \${APPFORGE_USER_SOURCES}
    \${APPFORGE_USER_HEADERS}
)

target_compile_features(
    appforge_native
    PRIVATE
    cxx_std_17
)

target_include_directories(
    appforge_native
    PRIVATE
    "\${CMAKE_CURRENT_SOURCE_DIR}/user"
)

foreach(header_file IN LISTS APPFORGE_USER_HEADERS)
    get_filename_component(header_dir "\${header_file}" DIRECTORY)
    target_include_directories(
        appforge_native
        PRIVATE
        "\${header_dir}"
    )
endforeach()

find_library(
    log_lib
    log
)

target_link_libraries(
    appforge_native
    \${log_lib}
)
`,
    "utf8"
  );

  if (
    onLog
  ) {
    await onLog(
      `✅ C/C++ JNI Android wrapper hazır • ${copied.copied} dosya • NDK 28.2`
    );
  }

  return {
    androidProjectDir,
    copiedFiles:
      copied.copied,
    copiedBytes:
      copied.bytes,
    entryFile:
      prepared.appForgeEntry,
    mode:
      "appforge-jni-wrapper"
  };
}
