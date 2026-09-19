import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
const mainUrl=new URL("../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",import.meta.url);
const homeUrl=new URL("../../android-app/app/src/main/java/com/appforge/studio/ui/StudioHomeV2.kt",import.meta.url);
const folderUrl=new URL("../../android-app/app/src/main/java/com/appforge/studio/ui/DownloadedApkFolder.kt",import.meta.url);
const terminalUrl=new URL("../../android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt",import.meta.url);
test("admin five-build remains protected",async()=>{const s=await readFile(mainUrl,"utf8");assert.match(s,/!isAdminOpsAccount/);assert.match(s,/5 Build Testi • Maks\. 3 Paralel/)});
test("successful build result UI remains",async()=>{const s=await readFile(mainUrl,"utf8");assert.match(s,/builderBuildOutputReady/);assert.match(s,/APK'YI TEKRAR İNDİR/);assert.match(s,/APK'YI KUR/)});
test("step10 home moved beside back",async()=>{const s=await readFile(mainUrl,"utf8");assert.match(s,/navigationIcon[\s\S]{0,300}if \(step != 10\)/);assert.match(s,/Text\("Geri"\)[\s\S]{0,800}⌂ Ana Sayfa/)});
test(
  "home uses one successful-build surface",
  async () => {
    const s =
      await readFile(
        homeUrl,
        "utf8"
      );

    assert.match(
      s,
      /builds|successfulBuilds/
    );

    assert.doesNotMatch(
      s,
      /successfulApkFolderOpen/
    );
  }
);

test("successful builds keeps MediaStore delivery and typed artifacts",async()=>{const s=await readFile(folderUrl,"utf8");assert.match(s,/MediaStore\.Downloads\.EXTERNAL_CONTENT_URI/);assert.match(s,/RELATIVE_PATH/);assert.match(s,/AppForgeStudio/);assert.match(s,/AppForge Studio/);assert.match(s,/ANDROID_APK/);assert.match(s,/ANDROID_AAB/);assert.match(s,/WINDOWS_PORTABLE_EXE/);assert.match(s,/ProjectLibrary\s*\.\s*loadBuilds/);assert.match(s,/sortedByDescending/);assert.match(s,/sortedBy/);assert.match(s,/onInstall/)});
test(
  "installer keeps permission-return continuation",
  async () => {
    const s =
      await readFile(
        mainUrl,
        "utf8"
      );

    assert.match(
      s,
      /override\s+fun\s+onResume\s*\(\s*\)/
    );

    assert.match(
      s,
      /pending_apk_path/
    );

    assert.match(
      s,
      /canRequestPackageInstalls\(\)/
    );

    assert.match(
      s,
      /installCachedApk\(/
    );
  }
);

test("BUG7 IME guards remain",async()=>{const s=await readFile(terminalUrl,"utf8");assert.match(s,/TERMINAL_IME_GEOMETRY_SETTLE_MS\s*=\s*160L/);assert.match(s,/WindowInsets\.ime\.only/)});
