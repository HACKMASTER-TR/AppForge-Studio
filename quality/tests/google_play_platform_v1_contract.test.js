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

const services = await readFile(
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/play/PlayPlatformServices.kt",
    import.meta.url
  ),
  "utf8"
);

const registry = await readFile(
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/play/PlayPlatformCapabilities.kt",
    import.meta.url
  ),
  "utf8"
);

const matrix = await readFile(
  new URL(
    "../../.appforge/play-platform-v1.json",
    import.meta.url
  ),
  "utf8"
);

const existingRelease = await readFile(
  new URL(
    "../../.github/workflows/android-play-release.yml",
    import.meta.url
  ),
  "utf8"
);

test("Google Play runtime libraries are pinned", () => {
  for (const dependency of [
    'com.google.android.play:integrity:1.6.0',
    'com.google.android.play:app-update:2.1.0',
    'com.android.billingclient:billing-ktx:9.1.0',
    'com.google.android.play:review:2.0.2',
    'com.android.installreferrer:installreferrer:2.2',
    'com.google.android.play:feature-delivery:2.1.0',
    'com.google.android.play:asset-delivery:2.3.0'
  ]) {
    assert.ok(
      gradle.includes(dependency),
      dependency
    );
  }
});

test("Play runtime service layer includes review, referrer and delivery", () => {
  assert.match(services, /ReviewManagerFactory/);
  assert.match(services, /InstallReferrerClient/);
  assert.match(services, /SplitInstallManagerFactory/);
  assert.match(services, /AssetPackManagerFactory/);
  assert.match(services, /No raw referrer is persisted here/);
  assert.match(services, /No review prompt is fired automatically/);
});

test("capability registry never confuses code-ready with active", () => {
  assert.match(registry, /PlayCapabilityState\.ACTIVE/);
  assert.match(registry, /PlayCapabilityState\.CODE_READY/);
  assert.match(registry, /PlayCapabilityState\.CONSOLE_GATED/);
  assert.match(registry, /PlayCapabilityState\.API_GATED/);
  assert.match(registry, /PlayCapabilityState\.POST_RELEASE_DATA/);

  assert.match(
    registry,
    /"in_app_review", PlayCapabilityState\.CODE_READY/
  );

  assert.match(
    registry,
    /"play_app_signing", PlayCapabilityState\.CONSOLE_GATED/
  );
});

test("master matrix defaults to no Play Production mutation", () => {
  const parsed = JSON.parse(matrix);

  assert.equal(
    parsed.production_mutation_default,
    false
  );

  assert.equal(
    parsed.play_upload_default,
    false
  );

  assert.equal(
    parsed.distribution.production,
    "explicit_approval_only"
  );
});

test("existing manual release remains internal-only", () => {
  assert.match(
    existingRelease,
    /PLAY_PRODUCTION_GATE=MANUAL_INTERNAL_ONLY/
  );

  assert.match(
    existingRelease,
    /APPFORGE_PLAY_TRACK=internal/
  );
});
