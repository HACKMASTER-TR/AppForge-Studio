import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const workflow = await readFile(
  new URL(
    "../../.github/workflows/android-play-release.yml",
    import.meta.url
  ),
  "utf8"
);

test(
  "manual Play dispatch is internal-only and cannot select Production",
  () => {
    assert.match(
      workflow,
      /EVENT_NAME:[\s\S]*github\.event_name/
    );

    assert.match(
      workflow,
      /if \[ "\$EVENT_NAME" = "workflow_dispatch" \]; then[\s\S]*APPFORGE_PLAY_TRACK=internal/
    );

    assert.match(
      workflow,
      /PLAY_PRODUCTION_GATE=MANUAL_INTERNAL_ONLY/
    );

    assert.doesNotMatch(
      workflow,
      /vars\.APPFORGE_PLAY_TRACK/
    );
  }
);

test(
  "Production Play publishing requires a strict AppForge release tag",
  () => {
    assert.match(
      workflow,
      /RELEASE_DRAFT/
    );

    assert.match(
      workflow,
      /RELEASE_PRERELEASE/
    );

    assert.match(
      workflow,
      /Draft release cannot publish to Production/
    );

    assert.match(
      workflow,
      /Prerelease cannot publish to Production/
    );

    assert.ok(
      workflow.includes(
        String.raw`^appforge-v([0-9]+)\.([0-9]+)\.([0-9]+)-([0-9]+)$`
      )
    );

    assert.match(
      workflow,
      /PLAY_PRODUCTION_GATE=PASS/
    );

    assert.match(
      workflow,
      /APPFORGE_PLAY_TRACK=production/
    );
  }
);

test(
  "Production release tag must resolve to current protected main",
  () => {
    assert.match(
      workflow,
      /refs\/remotes\/origin\/main\^\{commit\}/
    );

    assert.match(
      workflow,
      /refs\/tags\/\$RELEASE_TAG\^\{commit\}/
    );

    assert.match(
      workflow,
      /\[ "\$TAG_SHA" = "\$MAIN_SHA" \]/
    );

    assert.match(
      workflow,
      /Release tag does not point to current protected main/
    );
  }
);

test(
  "release tag version and Android version metadata must match",
  () => {
    assert.match(
      workflow,
      /PLAY_VERSION_NAME_MATCH=PASS/
    );

    assert.match(
      workflow,
      /PLAY_VERSION_CODE_MATCH=PASS/
    );

    assert.match(
      workflow,
      /versionCode/
    );

    assert.match(
      workflow,
      /versionName/
    );
  }
);

test(
  "Play uploader consumes only the fail-closed resolved track",
  () => {
    assert.match(
      workflow,
      /track:\s*\$\{\{\s*env\.APPFORGE_PLAY_TRACK\s*\}\}/
    );

    assert.match(
      workflow,
      /status:\s*completed/
    );

    assert.match(
      workflow,
      /types:\s*\n\s*-\s*published/
    );

    assert.doesNotMatch(
      workflow,
      /github\.event_name\s*==\s*'release'\s*&&\s*'production'/
    );
  }
);
