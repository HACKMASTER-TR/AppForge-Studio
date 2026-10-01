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
        '"python-android"',
        '"expo"'
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
        "windows-native",
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
        "WINDOWS_EXE",
        "WINDOWS_NATIVE_EXE"
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


test("physically accepted Expo SDK54 path exposes APK and AAB only", () => {
  const start = capabilities.indexOf('engine =\n                    "expo"');
  const block = capabilities.slice(start, start + 1200);
  assert.match(block, /DeviceArtifactKind\.APK/);
  assert.match(block, /DeviceArtifactKind\.AAB/);
  assert.match(block, /DeviceBuildSupport\.READY/);
  assert.doesNotMatch(block, /WINDOWS_EXE/);
});
