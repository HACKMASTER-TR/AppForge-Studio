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
  "Gradle canonicalizes project and workspace before guest path calculation",
  () => {
    assert.match(
      source,
      /val safeWorkspace =\s*workspace\s*\.canonicalFile/
    );

    assert.match(
      source,
      /val safeProject =\s*project\s*\.canonicalFile/
    );

    assert.match(
      source,
      /safeProject\s*\.relativeTo\(\s*safeWorkspace\s*\)/
    );

    assert.match(
      source,
      /\.ifBlank\s*\{\s*"\."\s*\}/
    );
  }
);

test(
  "Universal Android mounts exact Android project as PRoot workspace",
  () => {
    assert.match(
      source,
      /project = androidProject,\s*workspace = androidProject,/
    );

    assert.match(
      source,
      /APPFORGE_GRADLE_MOUNT_SCOPE/
    );

    assert.match(
      source,
      /PROJECT_ROOT/
    );
  }
);

test(
  "Gradle validates mounted project before execution",
  () => {
    assert.match(
      source,
      /APPFORGE_GRADLE_WORKSPACE_MOUNT=\/workspace/
    );

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
      /APPFORGE_GRADLE_PROJECT_CWD/
    );

    assert.match(
      source,
      /-p \. --no-daemon --stacktrace/
    );
  }
);

test(
  "contract remains independent from process cwd",
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
