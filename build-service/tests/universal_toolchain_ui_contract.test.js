import test from "node:test";
import assert from "node:assert/strict";
import { promises as fs } from "node:fs";

const mainActivity =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
    import.meta.url
  );

const runtimeState =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/BuildRuntimeState.kt",
    import.meta.url
  );

const advisor =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/ai/AppForgeBuildErrorAdvisor.kt",
    import.meta.url
  );

test(
  "Builder preserves toolchain preflight state without exposing internal UI copy",
  async () => {
    const [main, runtime, errorAdvisor] =
      await Promise.all([
        fs.readFile(mainActivity, "utf8"),
        fs.readFile(runtimeState, "utf8"),
        fs.readFile(advisor, "utf8")
      ]);

    for (const marker of [
      "toolchainPreflight",
      "generalPreflight",
      "buildProjectKey",
      "buildMatchesCurrentProject",
      "Worker capability seti"
    ]) {
      assert.ok(
        main.includes(marker),
        `MainActivity missing ${marker}`
      );
    }

    assert.equal(
      main.includes(
        "Universal Toolchain Preflight"
      ),
      false,
      "Internal toolchain heading must stay out of normal Builder UI"
    );

    assert.ok(
      main.includes(
        "TEKNİK AYRINTILARI GÖSTER"
      ),
      "Technical diagnostics must remain explicitly accessible"
    );

    assert.ok(
      runtime.includes("val buildProjectKey"),
      "BuildRuntimeState must bind a build to its source project"
    );

    assert.ok(
      errorAdvisor.includes("source_toolchain_unsupported"),
      "Android Error Assistant must classify router failures"
    );
  }
);

test(
  "managed Expo config is part of toolchain inspection evidence",
  async () => {
    const inspector =
      await fs.readFile(
        new URL(
          "../src/projectToolchainInspector.js",
          import.meta.url
        ),
        "utf8"
      );

    assert.ok(
      inspector.includes("appConfigTexts")
    );

    assert.ok(
      inspector.includes("toolchainText")
    );
  }
);
