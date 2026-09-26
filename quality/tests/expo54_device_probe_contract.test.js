import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

const engine =
  fs.readFileSync(
    new URL(
      "../../android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt",
      import.meta.url
    ),
    "utf8"
  );

const installer =
  fs.readFileSync(
    new URL(
      "../../android-app/app/src/main/assets/device-build/install-toolchain.sh",
      import.meta.url
    ),
    "utf8"
  );

const expo =
  fs.readFileSync(
    new URL(
      "../../android-app/app/src/main/assets/device-build/build-expo.sh",
      import.meta.url
    ),
    "utf8"
  );

const capabilities =
  fs.readFileSync(
    new URL(
      "../../android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildCapabilities.kt",
      import.meta.url
    ),
    "utf8"
  );

test(
  "Expo acceptance engine stays debug-only",
  () => {
    assert.match(
      engine,
      /BuildConfig\.DEBUG[\s\S]{0,180}sourceEngine == "expo"/
    );

    assert.match(
      engine,
      /"expo" -> buildExpoProject/
    );
  }
);

test(
  "Expo capability remains experimental with zero accepted outputs",
  () => {
    const start =
      capabilities.indexOf(
        'engine =\n                    "expo"'
      );

    assert.ok(start >= 0);

    const block =
      capabilities.slice(
        start,
        start + 1000
      );

    assert.match(
      block,
      /readyOutputs\s*=\s*\n\s*emptySet\(\)/
    );

    assert.match(
      block,
      /DeviceBuildSupport\.EXPERIMENTAL/
    );
  }
);

test(
  "device runtime pins verified Node 22.23.3",
  () => {
    assert.match(
      installer,
      /NODE_VERSION="22\.23\.3"/
    );

    assert.match(
      installer,
      /a44aeb94849a299b22df10b9e622ec2f605c2183501bc40590705131de7c740f/
    );

    assert.match(
      installer,
      /df450af89261115ef9f9e3830c3eeb2cc9213b63c720b1af623cb5dcbe2e02de/
    );
  }
);

test(
  "Expo 54 probe disables native architecture risks before Gradle",
  () => {
    assert.match(
      expo,
      /expo\.startsWith\("54\."\)/
    );

    assert.match(
      expo,
      /rn\.startsWith\("0\.81\."\)/
    );

    assert.match(
      expo,
      /newArchEnabled[\s\S]{0,100}false/
    );

    assert.match(
      expo,
      /hermesEnabled[\s\S]{0,100}false/
    );

    assert.match(
      expo,
      /reactNativeArchitectures[\s\S]{0,100}arm64-v8a/
    );
  }
);

test(
  "Expo prebuild uses rootfs-native staging instead of the PRoot workspace bind",
  () => {
    assert.match(
      expo,
      /PREBUILD_ROOT="\$ROOT\/expo-prebuild-work"/
    );

    assert.match(
      expo,
      /APPFORGE_EXPO_PREBUILD_FS=NATIVE_ROOTFS/
    );

    assert.match(
      expo,
      /--exclude='\.\/node_modules'/
    );

    assert.match(
      expo,
      /ln -s[\s\S]{0,120}SOURCE\/node_modules[\s\S]{0,120}PREBUILD\/node_modules/
    );

    assert.match(
      expo,
      /MainApplication\.kt/
    );

    assert.match(
      expo,
      /APPFORGE_EXPO_MAIN_APPLICATION=PASS/
    );

    assert.match(
      expo,
      /APPFORGE_EXPO_ANDROID_COPYBACK=PASS/
    );
  }
);

test(
  "Expo properties are finalized on native staging before Android copyback",
  () => {
    assert.match(
      expo,
      /set_prop_native\(\)/
    );

    assert.match(
      expo,
      /file="\$PREBUILD\/android\/gradle\.properties"/
    );

    assert.doesNotMatch(
      expo,
      /^[ \t]*sed[ \t]+-i(?:[ \t]|$)/m
    );

    const nativeMarker =
      expo.indexOf(
        "APPFORGE_EXPO_NATIVE_PROPERTIES=PASS"
      );

    const copybackMarker =
      expo.indexOf(
        "APPFORGE_EXPO_ANDROID_COPYBACK=PASS"
      );

    assert.ok(nativeMarker >= 0);
    assert.ok(copybackMarker > nativeMarker);

    assert.match(
      expo,
      /newArchEnabled=false/
    );

    assert.match(
      expo,
      /hermesEnabled=false/
    );

    assert.match(
      expo,
      /reactNativeArchitectures=arm64-v8a/
    );
  }
);

test(
  "failed Kotlin builds preserve compiler diagnostics before the normal log tail",
  () => {
    assert.match(
      engine,
      /val compilerDiagnostics/
    );

    assert.match(
      engine,
      /value\.startsWith\("e:"\)/
    );

    assert.match(
      engine,
      /value\.contains\("Unresolved reference"\)/
    );

    assert.match(
      engine,
      /APPFORGE_COMPILER_DIAGNOSTICS_BEGIN/
    );

    assert.match(
      engine,
      /\.take\(\s*80\s*\)/
    );

    assert.match(
      engine,
      /\.takeLast\(\s*120\s*\)/
    );
  }
);
