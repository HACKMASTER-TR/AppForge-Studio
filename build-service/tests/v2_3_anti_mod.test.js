import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const server =
  new URL(
    "../server.js",
    import.meta.url
  );

const pro =
  new URL(
    "../src/proEntitlements.js",
    import.meta.url
  );

const integrity =
  new URL(
    "../src/studioIntegrity.js",
    import.meta.url
  );

const main =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
    import.meta.url
  );

const prefs =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/io/AppSettingsStore.kt",
    import.meta.url
  );

test("local proUnlocked switch is removed", async () => {
  const text =
    await readFile(
      prefs,
      "utf8"
    );

  assert.equal(
    text.includes(
      "proUnlocked"
    ),
    false
  );
});

test("official build service enforces pro config", async () => {
  const serverText =
    await readFile(
      server,
      "utf8"
    );

  const proText =
    await readFile(
      pro,
      "utf8"
    );

  assert.equal(
    serverText.includes(
      "enforceProForConfig"
    ),
    true
  );

  assert.equal(
    proText.includes(
      "custom_signing"
    ),
    true
  );

  assert.equal(
    proText.includes(
      "remote_native_bridge"
    ),
    true
  );
});

test("play integrity checks recognized licensed app and device", async () => {
  const text =
    await readFile(
      integrity,
      "utf8"
    );

  for (
    const verdict of [
      "PLAY_RECOGNIZED",
      "LICENSED",
      "MEETS_DEVICE_INTEGRITY"
    ]
  ) {
    assert.equal(
      text.includes(
        verdict
      ),
      true
    );
  }
});

test("android pro screen uses server security client", async () => {
  const text =
    await readFile(
      main,
      "utf8"
    );

  assert.equal(
    text.includes(
      "StudioSecurityClient"
    ),
    true
  );

  assert.equal(
    text.includes(
      "AppSettingsStore.updatePro"
    ),
    false
  );
});
