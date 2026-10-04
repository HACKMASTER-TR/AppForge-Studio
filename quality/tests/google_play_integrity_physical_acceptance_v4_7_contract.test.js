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

const securityClient = await readFile(
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/security/StudioSecurityClient.kt",
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

const playRelease = await readFile(
  new URL(
    "../../.github/workflows/android-play-release.yml",
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
  "physical acceptance disables R8 while normal release stays minified",
  () => {
    assert.match(
      gradle,
      /isMinifyEnabled\s*=\s*[\s\S]*!appforgePlayIntegrityAcceptance/
    );

    assert.match(
      gradle,
      /isShrinkResources\s*=\s*[\s\S]*!appforgePlayIntegrityAcceptance/
    );

    assert.match(
      gradle,
      /providers\.gradleProperty\("appforgePlayIntegrityAcceptance"\)[\s\S]*orNull == "true"/
    );
  }
);

test(
  "policy denial exposes only a safe verdict summary",
  () => {
    assert.match(
      securityClient,
      /class StudioSecurityApiException/
    );

    assert.match(
      securityClient,
      /safeVerdict/
    );

    assert.match(
      securityClient,
      /integrity_policy_denied/
    );

    assert.match(
      acceptance,
      /REQUEST_VERIFIED=/
    );

    assert.match(
      acceptance,
      /PLAY_RECOGNIZED=/
    );

    assert.match(
      acceptance,
      /MEETS_DEVICE_INTEGRITY=/
    );

    assert.match(
      acceptance,
      /OPTIONAL_VERDICTS_READY=/
    );

    assert.match(
      acceptance,
      /APP_ACCESS_RISK=/
    );

    assert.match(
      acceptance,
      /PLAY_PROTECT_VERDICT=/
    );

    assert.match(
      acceptance,
      /ACTIVITY_LEVEL=/
    );

    assert.match(
      acceptance,
      /TOKEN_VALUE=NOT_PRINTED/
    );

    assert.match(
      acceptance,
      /SESSION_VALUE=NOT_PRINTED/
    );

    assert.doesNotMatch(
      acceptance,
      /integrityToken=.*\$\{/
    );
  }
);

test(
  "physical acceptance can use guarded real Play Internal testing track",
  () => {
    assert.match(
      playRelease,
      /push:[\s\S]*feat\/google-play-platform-v1-20261004/
    );

    assert.match(
      playRelease,
      /\.github\/\.play-integrity-internal-track-acceptance-request/
    );

    assert.match(
      playRelease,
      /PHYSICAL_ACCEPTANCE_INTERNAL_TRACK_MARKER_GUARD=PASS/
    );

    assert.match(
      playRelease,
      /PLAY_PRODUCTION_GATE=PHYSICAL_ACCEPTANCE_INTERNAL_ONLY/
    );

    assert.match(
      playRelease,
      /APPFORGE_PLAY_TRACK=internal/
    );

    assert.match(
      playRelease,
      /APPFORGE_PLAY_INTEGRITY_ACCEPTANCE=true/
    );

    assert.match(
      playRelease,
      /APPFORGE_PLAY_INTEGRITY_ACCEPTANCE_VERSION_CODE=530/
    );

    assert.match(
      playRelease,
      /-PappforgePlayIntegrityAcceptance=true/
    );

    assert.match(
      playRelease,
      /-PappforgePlayIntegrityAcceptanceVersionCode/
    );

    assert.match(
      playRelease,
      /track:\s*internal/
    );

    assert.match(
      playRelease,
      /PLAY_PRODUCTION_MUTATION=NONE/
    );

    assert.match(
      gradle,
      /appforgePlayIntegrityAcceptanceVersionCode/
    );
  }
);

test(
  "physical acceptance does not require unrelated GitHub OAuth secret",
  () => {
    assert.match(
      playRelease,
      /required=\([\s\S]*APPFORGE_RELEASE_KEYSTORE_B64[\s\S]*APPFORGE_RELEASE_STORE_PASSWORD[\s\S]*APPFORGE_RELEASE_KEY_PASSWORD/
    );

    assert.match(
      playRelease,
      /APPFORGE_PLAY_INTEGRITY_ACCEPTANCE:-false/
    );

    assert.match(
      playRelease,
      /required\+=\([\s\S]*APPFORGE_GITHUB_OAUTH_CLIENT_ID/
    );

    assert.match(
      playRelease,
      /GITHUB_OAUTH_SECRET_REQUIREMENT=PHYSICAL_ACCEPTANCE_NOT_REQUIRED/
    );

    assert.match(
      playRelease,
      /GITHUB_OAUTH_SECRET_REQUIREMENT=NORMAL_RELEASE_REQUIRED/
    );
  }
);

test(
  "Play version resolution uses a stable base version source",
  () => {
    assert.match(
      gradle,
      /val appforgeBaseVersionCode\s*=\s*529/
    );

    assert.match(
      gradle,
      /versionCode\s*=[\s\S]*appforgeBaseVersionCode/
    );

    const stableResolverCount =
      (
        playRelease.match(
          /appforgeBaseVersionCode/g
        ) || []
      ).length;

    assert.ok(
      stableResolverCount >= 2
    );

    const legacyResolver =
      String.raw`r"\bversionCode\s*=\s*(\d+)",`;

    assert.equal(
      playRelease.includes(
        legacyResolver
      ),
      false
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
