import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

const main = fs.readFileSync(
  "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
  "utf8"
);

const engine = fs.readFileSync(
  "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt",
  "utf8"
);

const manager = fs.readFileSync(
  "android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt",
  "utf8"
);

const screen = fs.readFileSync(
  "android-app/app/src/main/java/com/appforge/studio/OfflineBuildPackScreen.kt",
  "utf8"
);

test(
  "normal device build is blocked before build allocation when pack is missing",
  () => {
    const gate =
      engine.indexOf(
        "requireReadyForBuild"
      );

    const buildNumber =
      engine.indexOf(
        "AppForgeBuildNumberStore.next"
      );

    assert.ok(
      gate >= 0
    );

    assert.ok(
      buildNumber >= 0
    );

    assert.ok(
      gate < buildNumber
    );
  }
);

test(
  "builder performs a user-visible offline-pack preflight",
  () => {
    assert.match(
      main,
      /checkBuildRequirements/
    );

    assert.match(
      main,
      /BUILD_BLOCKED=YES/
    );

    assert.match(
      main,
      /OFFLINE_BUILD_READY=NO/
    );

    assert.match(
      main,
      /MISSING=/
    );

    assert.match(
      main,
      /openWorkspaceScreen\(\s*AppScreen\.OFFLINE_PACK\s*\)/
    );
  }
);

test(
  "offline pack requirements are engine and output aware",
  () => {
    assert.match(
      manager,
      /Android SDK \+ JDK \+ Gradle/
    );

    assert.match(
      manager,
      /Node\.js \+ npm/
    );

    assert.match(
      manager,
      /Python \+ Chaquopy/
    );

    assert.match(
      manager,
      /Expo SDK 54 \+ NDK\/CMake/
    );

    assert.match(
      manager,
      /Windows Native C\/C\+\+ toolchain/
    );

    assert.match(
      manager,
      /Windows Portable EXE Host/
    );
  }
);

test(
  "Expo base toolchain is part of the installable offline pack",
  () => {
    assert.match(
      manager,
      /expoToolchainReady/
    );

    assert.match(
      manager,
      /expo\.ready/
    );

    assert.match(
      manager,
      /expo-toolchain/
    );

    assert.match(
      screen,
      /Expo SDK 54 \/ React Native 0\.81/
    );

    assert.match(
      screen,
      /status\.expoToolchainReady/
    );
  }
);

test(
  "complete target readiness includes Expo Windows native and portable",
  () => {
    assert.match(
      manager,
      /currentAndroidEnginesReady[\s\S]*expoToolchainReady/
    );

    assert.match(
      manager,
      /completeTargetReady[\s\S]{0,180}windowsNativeToolchainReady[\s\S]{0,180}windowsExeReady/
    );
  }
);

test(
  "offline pack returns to the originating workspace",
  () => {
    assert.match(
      main,
      /AppScreen\.OFFLINE_PACK ->[\s\S]{0,220}returnFromWorkspace\(\)/
    );
  }
);

test(
  "Terminal workspace is not modified by this feature",
  () => {
    assert.doesNotMatch(
      screen,
      /TerminalWorkspaceScreen/
    );
  }
);
