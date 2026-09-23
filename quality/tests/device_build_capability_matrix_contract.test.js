import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here =
  path.dirname(
    fileURLToPath(import.meta.url)
  );

const repo =
  path.resolve(
    here,
    "../.."
  );

const capabilities =
  fs.readFileSync(
    path.join(
      repo,
      "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildCapabilities.kt"
    ),
    "utf8"
  );

const engine =
  fs.readFileSync(
    path.join(
      repo,
      "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"
    ),
    "utf8"
  );

test(
  "current proven device engines remain READY",
  () => {
    for (
      const marker of [
        '"webview-static"',
        '"node-web"',
        '"android-gradle"',
        '"python-android"'
      ]
    ) {
      assert.match(
        capabilities,
        new RegExp(
          marker.replaceAll(
            '"',
            '\\"'
          )
        )
      );
    }

    assert.match(
      capabilities,
      /DeviceBuildSupport\.READY/
    );
  }
);

test(
  "future engine matrix records all requested families",
  () => {
    for (
      const marker of [
        "android-ndk",
        "react-native",
        "expo",
        "flutter",
        "dart",
        "dotnet-android",
        "dotnet-maui",
        "windows-web",
        "unity"
      ]
    ) {
      assert.equal(
        capabilities.includes(
          `"${marker}"`
        ),
        true,
        `Missing capability marker: ${marker}`
      );
    }
  }
);

test(
  "artifact model includes APK AAB and Windows EXE",
  () => {
    for (
      const marker of [
        "APK",
        "AAB",
        "WINDOWS_EXE"
      ]
    ) {
      assert.match(
        capabilities,
        new RegExp(
          `DeviceArtifactKind\\.${marker}`
        )
      );
    }

    assert.match(
      capabilities,
      /"all"/
    );

    assert.match(
      capabilities,
      /"apk\+aab\+exe"/
    );
  }
);

test(
  "unvalidated engines cannot silently become READY",
  () => {
    assert.match(
      capabilities,
      /EXPERIMENTAL/
    );

    assert.match(
      capabilities,
      /PLANNED/
    );

    assert.match(
      capabilities,
      /EXTERNAL_TOOL_REQUIRED/
    );

    assert.match(
      engine,
      /requestedOutputs/
    );

    assert.match(
      engine,
      /unavailable/
    );
  }
);

test(
  "Unity is not falsely advertised as local-ready",
  () => {
    const unity =
      capabilities.slice(
        capabilities.indexOf(
          'engine =\n                    "unity"'
        )
      );

    assert.match(
      unity,
      /EXTERNAL_TOOL_REQUIRED/
    );
  }
);
