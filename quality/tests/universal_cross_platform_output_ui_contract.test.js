import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, "../..");
const main = fs.readFileSync(
  path.join(repo, "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"),
  "utf8"
);
const caps = fs.readFileSync(
  path.join(repo, "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildCapabilities.kt"),
  "utf8"
);

test("universal import defaults to all three outputs", () => {
  assert.match(
    main,
    /analysis\.buildEngine\s*==\s*"universal-cross-platform"[\s\S]{0,180}?"all"/
  );
});

test("universal output selector exposes ALL and keeps EXE enabled", () => {
  assert.match(main, /val universalProject\s*=/);
  assert.match(main, /add\("all"\)/);
  assert.match(main, /"all"\s*->\s*"TÜMÜ"/);
  assert.match(
    main,
    /setOf\([\s\S]{0,180}?"webview-static"[\s\S]{0,180}?"node-web"[\s\S]{0,180}?"universal-cross-platform"/
  );
});

test("all output has a clear APK AAB EXE summary", () => {
  assert.match(main, /"APK \+ AAB \+ Windows EXE"/);
  assert.match(
    main,
    /Universal projede Android APK\/AAB ve Windows Portable EXE aynı kaynak paketinden birlikte oluşturulur\./
  );
});

test("all output receives the AAB signing reminder", () => {
  assert.match(
    main,
    /draft\.buildOutput\s*==\s*"both"[\s\S]{0,180}?draft\.buildOutput\s*==\s*"all"/
  );
});

test("engine still maps all to APK AAB and Windows EXE", () => {
  const allCase = caps.match(
    /"all",[\s\S]{0,80}?"apk\+aab\+exe"[\s\S]{0,300}?DeviceArtifactKind\.APK[\s\S]{0,180}?DeviceArtifactKind\.AAB[\s\S]{0,180}?DeviceArtifactKind\.WINDOWS_EXE/
  );
  assert.ok(allCase);
});
