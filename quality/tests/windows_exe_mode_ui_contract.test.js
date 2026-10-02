import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import {
  fileURLToPath
} from "node:url";

const here =
  path.dirname(
    fileURLToPath(
      import.meta.url
    )
  );

const repo =
  path.resolve(
    here,
    "../.."
  );

const read =
  relative =>
    fs.readFileSync(
      path.join(
        repo,
        relative
      ),
      "utf8"
    );


test(
  "Windows EXE output UI distinguishes Portable and Native engines",
  () => {
    const main =
      read(
        "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
      );

    for (
      const marker of [
        "windowsExeModeLabel",
        "windowsExeModeRoute",
        "windowsExeModeState",
        "windowsExeDownloadButtonLabel",
        "Windows EXE motoru",
        "Windows Portable EXE",
        "Windows Native EXE",
        "PORTABLE • AppForge Generic Host",
        "NATIVE • C/C++ • CMake + MinGW-w64",
        '"EXPERIMENTAL"',
        '"READY"'
      ]
    ) {
      assert.ok(
        main.includes(
          marker
        ),
        `missing Windows EXE mode marker: ${marker}`
      );
    }
  }
);


test(
  "Windows EXE result screen uses selected engine label",
  () => {
    const main =
      read(
        "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
      );

    assert.match(
      main,
      /windowsExeArtifactLabel\s*=\s*[\s\S]{0,180}?windowsExeModeLabel/
    );

    assert.match(
      main,
      /✓ \$windowsExeArtifactLabel hazır/
    );

    assert.match(
      main,
      /windowsExeDownloadButtonText\s*=\s*[\s\S]{0,180}?windowsExeDownloadButtonLabel/
    );

    assert.match(
      main,
      /Text\(\s*windowsExeDownloadButtonText\s*\)/
    );
  }
);


test(
  "build history persists the selected Windows EXE output mode",
  () => {
    const library =
      read(
        "android-app/app/src/main/java/com/appforge/studio/io/ProjectLibrary.kt"
      );

    for (
      const marker of [
        'val buildOutput: String = ""',
        "buildOutput = draft.buildOutput",
        'put("buildOutput", b.buildOutput)',
        'o.optString("buildOutput", "")'
      ]
    ) {
      assert.ok(
        library.includes(
          marker
        ),
        `missing build history output marker: ${marker}`
      );
    }
  }
);


test(
  "old build history remains compatible when buildOutput is absent",
  () => {
    const library =
      read(
        "android-app/app/src/main/java/com/appforge/studio/io/ProjectLibrary.kt"
      );

    assert.match(
      library,
      /buildOutput\s*=\s*o\.optString\("buildOutput", ""\)/
    );
  }
);


test(
  "Native remains experimental while Portable remains the ready web target",
  () => {
    const capabilities =
      read(
        "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildCapabilities.kt"
      );

    assert.match(
      capabilities,
      /engine\s*=\s*[\s\S]{0,40}?"windows-native"[\s\S]{0,500}?DeviceBuildSupport\.EXPERIMENTAL/
    );

    assert.match(
      capabilities,
      /engine\s*=\s*[\s\S]{0,40}?"webview-static"[\s\S]{0,500}?DeviceArtifactKind\.WINDOWS_EXE[\s\S]{0,300}?DeviceBuildSupport\.READY/
    );
  }
);
