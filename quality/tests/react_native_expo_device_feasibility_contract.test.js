import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const detector = await readFile(
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/io/ProjectTechnologyDetector.kt",
    import.meta.url
  ),
  "utf8"
);

const capabilities = await readFile(
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildCapabilities.kt",
    import.meta.url
  ),
  "utf8"
);

const engine = await readFile(
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt",
    import.meta.url
  ),
  "utf8"
);

const agentPreparer = await readFile(
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentBuildProjectPreparer.kt",
    import.meta.url
  ),
  "utf8"
);

test(
  "React Native and Expo detection does not falsely claim device readiness",
  () => {
    assert.match(
      detector,
      /id = "expo"[\s\S]{0,240}buildEngine = "expo"[\s\S]{0,180}buildReady = false/
    );

    assert.match(
      detector,
      /id = "react-native"[\s\S]{0,240}buildEngine = "react-native"[\s\S]{0,180}buildReady = false/
    );

    assert.doesNotMatch(
      detector,
      /buildEngine = "expo-android"/
    );

    assert.doesNotMatch(
      detector,
      /buildEngine = "react-native-android"/
    );
  }
);

test(
  "React Native and Expo remain zero-output experimental capabilities",
  () => {
    for (const name of ["react-native", "expo"]) {
      const marker =
        `engine =\n                    "${name}"`;

      const start =
        capabilities.indexOf(marker);

      assert.ok(
        start >= 0,
        `Missing ${name} capability`
      );

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
  }
);

test(
  "React Native stays blocked while Expo acceptance is debug gated",
  () => {
    assert.doesNotMatch(
      engine,
      /"react-native"\s*->/
    );

    assert.match(
      engine,
      /"expo"\s*->\s*buildExpoProject/
    );

    const expoBranches =
      engine.match(
        /"expo"\s*->/g
      ) ?? [];

    assert.equal(
      expoBranches.length,
      1,
      "Expo must have exactly one execution branch"
    );

    assert.match(
      engine,
      /val expoAcceptanceProbe\s*=\s*BuildConfig\.DEBUG[\s\S]{0,220}?sourceEngine == "expo"[\s\S]{0,220}?draft\.sourceTechnology == "expo"/
    );

    assert.match(
      engine,
      /capability\.support\s*==\s*DeviceBuildSupport\.READY\s*\|\|\s*expoAcceptanceProbe/
    );

    assert.match(
      engine,
      /unavailable\.isEmpty\(\)\s*\|\|\s*expoAcceptanceProbe/
    );
  }
);

test(
  "Unified Agent may package Expo source without advertising device readiness",
  () => {
    assert.doesNotMatch(
      agentPreparer,
      /expo-android/
    );

    assert.match(
      agentPreparer,
      /AppForgeAgentPlatform\.REACT_NATIVE\s*->\s*"expo"/
    );

    assert.match(
      agentPreparer,
      /val experimentalExpoSource\s*=[\s\S]{0,500}?analysis\.technologyId\s*==\s*"expo"[\s\S]{0,300}?analysis\.buildEngine\s*==\s*"expo"/
    );

    assert.match(
      agentPreparer,
      /analysis\.buildReady\s*\|\|\s*experimentalExpoSource/
    );
  }
);
