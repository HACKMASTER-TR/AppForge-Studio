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

test(
  "Expo final Android project forces the final node_modules autolinking root",
  () => {
    assert.match(
      expo,
      /APPFORGE_EXPO_SEARCH_PATHS_V2/
    );

    assert.match(
      expo,
      /expoAutolinking\.searchPaths = \[\\"..\/node_modules\/expo\\", \\"..\/node_modules\/expo-modules-core\\"\]/
    );

    const searchPath =
      expo.indexOf(
        'expoAutolinking.searchPaths = [\\"../node_modules/expo\\", \\"../node_modules/expo-modules-core\\"]'
      );

    const useExpoModules =
      expo.indexOf(
        "expoAutolinking.useExpoModules"
      );

    assert.ok(
      searchPath >= 0
    );

    assert.ok(
      useExpoModules >= 0
    );
  }
);

test(
  "Expo final root must resolve expo and expo-modules-core before Gradle",
  () => {
    assert.match(
      expo,
      /APPFORGE_EXPO_FINAL_AUTOLINK_RESOLVE_V2/
    );

    assert.match(
      expo,
      /resolve[\s\S]{0,240}--platform android[\s\S]{0,240}--json[\s\S]{0,240}\.\.\/node_modules\/expo[\s\S]{0,160}\.\.\/node_modules\/expo-modules-core/
    );

    assert.match(
      expo,
      /APPFORGE_EXPO_AUTOLINK_EXPO/
    );

    assert.match(
      expo,
      /APPFORGE_EXPO_AUTOLINK_CORE/
    );

    assert.match(
      expo,
      /APPFORGE_EXPO_FINAL_AUTOLINK=PASS/
    );

    assert.match(
      expo,
      /Missing Expo autolink module/
    );

    assert.match(
      expo,
      /retained deleted prebuild path/
    );
  }
);

test(
  "Expo module resolver proves the expo Gradle project before app classpath bridge",
  () => {
    assert.match(
      expo,
      /APPFORGE_EXPO_GRADLE_PROJECT_EXPO=PASS/
    );

    assert.match(
      expo,
      /project\.name \|\| ""/
    );

    assert.match(
      expo,
      /=== "expo"/
    );

    assert.match(
      expo,
      /APPFORGE_EXPO_APP_CLASSPATH_AUTHORITY=EXPO_MODULE_RESOLVER/
    );

    assert.doesNotMatch(
      expo,
      /APPFORGE_EXPO_RN_AUTOLINK_VERIFY_V1/
    );

    assert.doesNotMatch(
      expo,
      /React Native autolinking did not expose Expo Android dependency/
    );

    assert.match(
      expo,
      /APPFORGE_EXPO_APP_CLASSPATH_BRIDGE_V1/
    );

    assert.match(
      expo,
      /autolinkLibrariesWithApp\(\)/
    );

    assert.match(
      expo,
      /implementation\(project\(":expo"\)\)/
    );

    const finalResolve =
      expo.indexOf(
        "APPFORGE_EXPO_FINAL_AUTOLINK=PASS"
      );

    const projectProof =
      expo.indexOf(
        "APPFORGE_EXPO_GRADLE_PROJECT_EXPO=PASS"
      );

    const bridge =
      expo.indexOf(
        "APPFORGE_EXPO_APP_CLASSPATH_BRIDGE_V1"
      );

    assert.ok(
      projectProof >= 0
    );

    assert.ok(
      finalResolve > projectProof
    );

    assert.ok(
      bridge > finalResolve
    );
  }
);


test(
  "Expo avoids broad PRoot node_modules enumeration",
  () => {
    assert.match(
      expo,
      /APPFORGE_EXPO_AUTOLINK_SEARCH_MODE=EXACT_MODULES/
    );

    assert.match(
      expo,
      /\.\.\/node_modules\/expo/
    );

    assert.match(
      expo,
      /\.\.\/node_modules\/expo-modules-core/
    );

    assert.doesNotMatch(
      expo,
      /expoAutolinking\.searchPaths = \[\\"..\/node_modules\\"\]/
    );
  }
);

test(
  "Expo npm and prebuild run explicitly with pinned Node 22",
  () => {
    assert.match(
      expo,
      /NPM_CLI="\$NODE_HOME\/lib\/node_modules\/npm\/bin\/npm-cli\.js"/
    );

    assert.match(
      expo,
      /APPFORGE_EXPO_NODE22_EXECUTION=PASS/
    );

    assert.match(
      expo,
      /"\$NODE_HOME\/bin\/node" "\$NPM_CLI" ci/
    );

    assert.match(
      expo,
      /"\$NODE_HOME\/bin\/node" "\$NPM_CLI" install/
    );

    assert.match(
      expo,
      /"\$NODE_HOME\/bin\/node"[\s\S]{0,160}"\$PREBUILD\/node_modules\/expo\/bin\/cli"[\s\S]{0,100}prebuild/
    );
  }
);
