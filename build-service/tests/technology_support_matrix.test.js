import test from "node:test";
import assert from "node:assert/strict";
import {
  promises as fs
} from "fs";
import path from "path";
import {
  fileURLToPath
} from "url";

const here =
  path.dirname(
    fileURLToPath(
      import.meta.url
    )
  );

const repoRoot =
  path.resolve(
    here,
    "..",
    ".."
  );

const detectorPath =
  path.join(
    repoRoot,
    "android-app",
    "app",
    "src",
    "main",
    "java",
    "com",
    "appforge",
    "studio",
    "io",
    "ProjectTechnologyDetector.kt"
  );

const buildEnginePath =
  path.join(
    repoRoot,
    "build-service",
    "src",
    "buildEngine.js"
  );

async function sources() {
  return {
    detector:
      await fs.readFile(
        detectorPath,
        "utf8"
      ),
    build:
      await fs.readFile(
        buildEnginePath,
        "utf8"
      )
  };
}

test(
  "build-ready Android detector engines exist in server router",
  async () => {
    const {
      detector,
      build
    } =
      await sources();

    const engines =
      [
        "webview-static",
        "node-web",
        "python-android",
        "android-gradle",
        "flutter",
        "react-native-android",
        "expo-android",
        "android-ndk",
        "dotnet-maui-android",
        "dotnet-android",
        "remote-backend"
      ];

    for (
      const engine of
      engines
    ) {
      assert.ok(
        detector.includes(
          `buildEngine = "${engine}"`
        ),
        `Android detector ${engine} motorunu tanımıyor.`
      );

      assert.ok(
        build.includes(
          `"${engine}"`
        ),
        `Server router ${engine} motorunu tanımıyor.`
      );
    }
  }
);

test(
  "technology families keep their intended readiness contract",
  async () => {
    const {
      detector
    } =
      await sources();

    const literalMarkers =
      [
        'id = "unity"',
        'id = "flutter"',
        'id = "expo"',
        'id = "react-native"',
        'id = "dotnet-maui"',
        'id = "dotnet-android"',
        'id = "dotnet-windows"',
        'id = "dotnet-csharp"',
        'id = "python-django"',
        'id = "python-flask"',
        'id = "python"',
        'id = "nextjs"',
        'id = "nuxt"',
        'id = "angular"',
        'id = "svelte"',
        'id = "vue"',
        'id = "react"',
        'id = "vite"',
        'id = "php"',
        'id = "cpp"',
        'id = "nodejs"',
        'id = "web-static"'
      ];

    for (
      const marker of
      literalMarkers
    ) {
      assert.ok(
        detector.includes(
          marker
        ),
        `Teknoloji detector marker eksik: ${marker}`
      );
    }

    assert.ok(
      detector.includes(
        'id = if (kotlin) "android-kotlin" else "android-java"'
      ),
      "Native Android Kotlin/Java dinamik detector kontratı eksik."
    );

    assert.match(
      detector,
      /id = "nodejs"[\s\S]*?buildReady = hasRemoteContract/,
      "Node.js remote backend contract varsa buildReady olmalı."
    );

    assert.match(
      detector,
      /id = "php"[\s\S]*?buildReady = hasRemoteContract/,
      "PHP remote backend contract varsa buildReady olmalı."
    );

    assert.match(
      detector,
      /id = "dotnet-windows"[\s\S]*?buildReady = false/,
      "WPF/WinForms/Windows-only .NET fail-closed kalmalı."
    );

    assert.match(
      detector,
      /id = "dotnet-csharp"[\s\S]*?buildReady = false/,
      "Android target içermeyen generic .NET fail-closed kalmalı."
    );

    assert.match(
      detector,
      /id = "unity"[\s\S]*?buildEngine = "unity-android"[\s\S]*?buildReady = false/,
      "Unity client detector dedicated licensed worker gate açılmadan fail-closed kalmalı."
    );
  }
);

test(
  "server router contains live handlers for every non-static source engine",
  async () => {
    const {
      build
    } =
      await sources();

    const liveMarkers =
      [
        "buildNodeWebSource(",
        "preparePythonAndroidProject(",
        "preparePythonWebAndroidProject(",
        "prepareAndroidGradleProject(",
        "prepareFlutterSource(",
        "buildFlutterArtifacts(",
        "prepareReactNativeSource(",
        "buildReactNativeArtifacts(",
        "prepareCppAndroidProject(",
        "prepareDotnetMauiSource(",
        "buildDotnetMauiArtifacts(",
        "prepareDotnetAndroidSource(",
        "buildDotnetAndroidArtifacts(",
        "preparePhpRemoteBackendSource(",
        "applyPhpRemoteBackendConfig(",
        "prepareNodeRemoteBackendSource(",
        "applyNodeRemoteBackendConfig("
      ];

    for (
      const marker of
      liveMarkers
    ) {
      assert.ok(
        build.includes(
          marker
        ),
        `Canlı server route eksik: ${marker}`
      );
    }
  }
);

test(
  "untrusted source engines remain blocked from CUSTOM signing",
  async () => {
    const {
      build
    } =
      await sources();

    const engines =
      [
        "node-web",
        "python-android",
        "android-gradle",
        "flutter",
        "react-native-android",
        "expo-android",
        "android-ndk",
        "dotnet-maui-android",
        "dotnet-android",
        "unity-android"
      ];

    for (
      const engine of
      engines
    ) {
      assert.ok(
        build.includes(
          `"${engine}"`
        ),
        `CUSTOM signing güvenlik listesinde/routerda ${engine} görünmüyor.`
      );
    }

    assert.ok(
      build.includes(
        'c.signing?.mode ==='
      ) &&
      build.includes(
        '"CUSTOM"'
      ),
      "CUSTOM signing koşulu bulunamadı."
    );

    assert.ok(
      build.includes(
        "Kaynak kod çalıştıran build motorlarında CUSTOM signing"
      ) &&
      build.includes(
        "izole imzalama servisi tamamlanana kadar güvenlik nedeniyle kapalı"
      ),
      "Untrusted source CUSTOM signing fail-closed mesajı bulunamadı."
    );
  }
);
