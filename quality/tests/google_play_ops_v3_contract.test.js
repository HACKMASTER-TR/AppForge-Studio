import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const releaseWorkflow = await readFile(
  new URL(
    "../../.github/workflows/android-play-release.yml",
    import.meta.url
  ),
  "utf8"
);

const rollout = await readFile(
  new URL(
    "../../.github/workflows/play-production-ops-v3.yml",
    import.meta.url
  ),
  "utf8"
);

const recovery = await readFile(
  new URL(
    "../../.github/workflows/play-app-recovery-v3.yml",
    import.meta.url
  ),
  "utf8"
);

const integrity = await readFile(
  new URL(
    "../../cloudflare/control-plane/src/play_integrity_policy.mjs",
    import.meta.url
  ),
  "utf8"
);

test(
  "future Production uploads are draft-first",
  () => {
    assert.match(
      releaseWorkflow,
      /track:\s*production[\s\S]*status:\s*draft/
    );

    assert.match(
      releaseWorkflow,
      /PLAY_PRODUCTION_INITIAL_STATE=DRAFT/
    );
  }
);

test(
  "staged rollout default action is read-only audit",
  () => {
    assert.match(
      rollout,
      /default:\s*audit/
    );

    for (
      const action of [
        "start_5",
        "advance_20",
        "advance_50",
        "halt",
        "resume",
        "complete"
      ]
    ) {
      assert.ok(
        rollout.includes(action),
        action
      );
    }

    assert.match(
      rollout,
      /APPFORGE_PLAY_\$\{ACTION_UPPER\}_\$\{VERSION_CODE\}/
    );

    assert.match(
      rollout,
      /:validate/
    );

    assert.match(
      rollout,
      /:commit/
    );

    assert.doesNotMatch(
      rollout,
      /upload-google-play/
    );
  }
);

test(
  "App Recovery defaults to list and mutations require confirmation",
  () => {
    assert.match(
      recovery,
      /default:\s*list/
    );

    assert.match(
      recovery,
      /APPFORGE_RECOVERY_CREATE_DRAFT_/
    );

    assert.match(
      recovery,
      /:deploy/
    );

    assert.match(
      recovery,
      /:cancel/
    );

    assert.match(
      recovery,
      /isRemoteInAppUpdateRequested/
    );

    assert.match(
      recovery,
      /isAllUsersRequested/
    );
  }
);

test(
  "advanced Integrity critical policy is fail-closed",
  () => {
    assert.match(
      integrity,
      /MEETS_DEVICE_INTEGRITY/
    );

    assert.match(
      integrity,
      /appAccessRiskVerdict/
    );

    assert.match(
      integrity,
      /playProtectVerdict/
    );

    assert.match(
      integrity,
      /recentDeviceActivity/
    );

    assert.match(
      integrity,
      /criticalActionAllowed/
    );

    assert.match(
      integrity,
      /optionalVerdictsReady/
    );
  }
);
