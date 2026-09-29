import fs from "node:fs";
import test from "node:test";
import assert from "node:assert/strict";

const engineUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt",
  import.meta.url
);

const source = fs.readFileSync(
  engineUrl,
  "utf8"
);

test(
  "Gradle builds anchor execution to the selected project directory",
  () => {
    assert.match(
      source,
      /val relativeProject = project\.relativeTo\(workspace\)\.invariantSeparatorsPath/
    );

    assert.match(
      source,
      /APPFORGE_GRADLE_PROJECT_DIR/
    );

    assert.match(
      source,
      /APPFORGE_GRADLE_PROJECT_CWD/
    );

    assert.match(
      source,
      /-p \. --no-daemon --stacktrace/
    );
  }
);

test(
  "Gradle project root is validated before execution",
  () => {
    assert.match(
      source,
      /APPFORGE_GRADLE_PROJECT_DIR_MISSING/
    );

    assert.match(
      source,
      /APPFORGE_GRADLE_SETTINGS_MISSING/
    );

    assert.match(
      source,
      /settings\.gradle/
    );

    assert.match(
      source,
      /settings\.gradle\.kts/
    );
  }
);

test(
  "contract resolves DeviceBuildEngine independently from process cwd",
  () => {
    assert.equal(
      engineUrl.protocol,
      "file:"
    );

    assert.ok(
      source.includes(
        "private fun buildGradleProject("
      )
    );
  }
);
