import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const gradle = await readFile(
  new URL(
    "../../android-app/app/build.gradle.kts",
    import.meta.url
  ),
  "utf8"
);

const gate = await readFile(
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/UpdateGateActivity.kt",
    import.meta.url
  ),
  "utf8"
);

const acceptance = await readFile(
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/PlayIntegrityAcceptanceActivity.kt",
    import.meta.url
  ),
  "utf8"
);

const manifest = await readFile(
  new URL(
    "../../android-app/app/src/main/AndroidManifest.xml",
    import.meta.url
  ),
  "utf8"
);

const sharing = await readFile(
  new URL(
    "../../.github/workflows/play-internal-app-sharing.yml",
    import.meta.url
  ),
  "utf8"
);

const ci = await readFile(
  new URL(
    "../../.github/workflows/google-play-platform-v1-ci.yml",
    import.meta.url
  ),
  "utf8"
);

test(
  "physical Integrity harness is explicit opt-in",
  () => {
    assert.match(
      gradle,
      /appforgePlayIntegrityAcceptance/
    );

    assert.match(
      gradle,
      /PLAY_INTEGRITY_ACCEPTANCE_HARNESS/
    );

    assert.match(
      gate,
      /PLAY_INTEGRITY_ACCEPTANCE_HARNESS/
    );

    assert.match(
      gate,
      /PlayIntegrityAcceptanceActivity/
    );

    assert.match(
      manifest,
      /android:name="\.PlayIntegrityAcceptanceActivity"/
    );

    assert.match(
      manifest,
      /PlayIntegrityAcceptanceActivity[\s\S]*android:exported="false"/
    );
  }
);

test(
  "physical harness uses real Standard Integrity client without printing credentials",
  () => {
    assert.match(
      acceptance,
      /StudioSecurityClient/
    );

    assert.match(
      acceptance,
      /physical_acceptance/
    );

    assert.match(
      acceptance,
      /STANDARD_INTEGRITY_TOKEN=PASS/
    );

    assert.match(
      acceptance,
      /GOOGLE_DECODE_INTEGRITY_TOKEN=PASS/
    );

    assert.match(
      acceptance,
      /TOKEN_VALUE=NOT_PRINTED/
    );

    assert.match(
      acceptance,
      /SESSION_VALUE=NOT_PRINTED/
    );
  }
);

test(
  "acceptance target is constrained to isolated workers.dev",
  () => {
    assert.match(
      gradle,
      /appforge-integrity-staging/
    );

    assert.match(
      acceptance,
      /appforge-integrity-staging/
    );

    assert.match(
      acceptance,
      /workers\\\\\.dev/
    );

    assert.doesNotMatch(
      acceptance,
      /appforge-control-plane\.[A-Za-z0-9.-]+\.workers\.dev/
    );
  }
);

test(
  "Internal App Sharing enables harness only by explicit workflow input",
  () => {
    assert.match(
      sharing,
      /integrity_acceptance/
    );

    assert.match(
      sharing,
      /integrity_staging_base_url/
    );

    assert.match(
      sharing,
      /-PappforgePlayIntegrityAcceptance=true/
    );

    assert.match(
      sharing,
      /-PappforgePlayIntegrityAcceptanceBaseUrl/
    );

    assert.match(
      sharing,
      /INTEGRITY_ACCEPTANCE_URL_GUARD=PASS/
    );

    assert.match(
      sharing,
      /PLAY_PRODUCTION_MUTATION=NONE/
    );
  }
);

test(
  "feature branch physical acceptance uses a marker-only push gate",
  () => {
    assert.match(
      sharing,
      /push:[\s\S]*feat\/google-play-platform-v1-20261004/
    );

    assert.match(
      sharing,
      /\.github\/\.play-integrity-physical-acceptance-request/
    );

    assert.match(
      sharing,
      /PHYSICAL_ACCEPTANCE_MARKER_GUARD=PASS/
    );

    assert.match(
      sharing,
      /share\(internal\): Play Integrity physical acceptance/
    );

    assert.match(
      sharing,
      /git rev-parse HEAD\^/
    );

    assert.match(
      sharing,
      /INTEGRITY_STAGING_PREFLIGHT=PASS/
    );

    assert.match(
      sharing,
      /APPFORGE_INTERNAL_SHARE_ACCEPTANCE/
    );

    assert.match(
      sharing,
      /PLAY_PRODUCTION_MUTATION=NONE/
    );
  }
);

test(
  "Google Play CI includes V4.7 contract",
  () => {
    assert.match(
      ci,
      /google_play_integrity_physical_acceptance_v4_7_contract\.test\.js/
    );
  }
);
