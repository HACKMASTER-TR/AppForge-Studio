import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, "../..");
const read = relative =>
  fs.readFileSync(path.join(repo, relative), "utf8");

test("publisher signing uses an owner-only provider registry", () => {
  const policy = read(
    "android-app/app/src/main/java/com/appforge/studio/build/WindowsPublisherSigningPolicy.kt"
  );
  const provider = read(
    "android-app/app/src/main/java/com/appforge/studio/build/WindowsPublisherSigningProvider.kt"
  );

  assert.match(policy, /requireActiveOwner/);
  assert.match(policy, /configureLocalPkcs12/);
  assert.match(policy, /setSigningEnabled/);
  assert.match(
    policy,
    /WindowsPublisherSigningProviders[\s\S]*\.resolve/
  );

  assert.match(
    provider,
    /interface WindowsPublisherSigningProvider/
  );
  assert.match(
    provider,
    /LocalPkcs12WindowsPublisherSigningProvider/
  );
  assert.match(provider, /requireActiveOwner/);
});

test("PKCS12 material is encrypted at rest with Android Keystore AES GCM", () => {
  const store = read(
    "android-app/app/src/main/java/com/appforge/studio/build/WindowsPublisherSigningStore.kt"
  );

  assert.match(store, /AndroidKeyStore/);
  assert.match(
    store,
    /KeyProperties\.KEY_ALGORITHM_AES/
  );
  assert.match(store, /BLOCK_MODE_GCM/);
  assert.match(store, /AES\/GCM\/NoPadding/);
  assert.match(store, /noBackupFilesDir/);
  assert.match(store, /signer\.pkcs12\.enc/);
  assert.match(store, /signer\.pass\.enc/);

  assert.doesNotMatch(
    store,
    /BEGIN PRIVATE KEY|BEGIN RSA PRIVATE KEY/
  );
});

test("osslsigncode password is file-fed and signature is verified", () => {
  const provider = read(
    "android-app/app/src/main/java/com/appforge/studio/build/WindowsPublisherSigningProvider.kt"
  );

  assert.match(provider, /osslsigncode sign/);
  assert.match(
    provider,
    /-readpass \/workspace\/pass\.txt/
  );

  assert.doesNotMatch(
    provider,
    /\s-pass\s+[^\n]/
  );

  assert.match(provider, /-h sha256/);
  assert.match(
    provider,
    /-ts http:\/\/timestamp\.digicert\.com/
  );
  assert.match(provider, /osslsigncode verify/);
  assert.match(
    provider,
    /APPFORGE_WINDOWS_PUBLISHER_SIGNING=PASS/
  );
});

test("signing secrets use a separate workspace and are deleted", () => {
  const provider = read(
    "android-app/app/src/main/java/com/appforge/studio/build/WindowsPublisherSigningProvider.kt"
  );

  assert.match(
    provider,
    /windows-publisher-signing/
  );
  assert.match(provider, /materializeInto/);
  assert.match(
    provider,
    /finally[\s\S]*signingWorkspace[\s\S]*deleteRecursively/
  );
});

test("engine removes unsigned EXE when requested signing fails", () => {
  const engine = read(
    "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"
  );

  const calls =
    engine.match(
      /WindowsPublisherSigningPolicy\s*[\s\S]{0,100}?\.applyIfRequested/g
    ) || [];

  assert.equal(calls.length, 1);

  assert.match(engine, /signingRequested/);
  assert.match(
    engine,
    /finalWindowsExe[\s\S]*delete\(\)/
  );
  assert.match(
    engine,
    /state\.exe\s*=\s*null/
  );
  assert.match(
    engine,
    /publisher signing fail-closed/
  );
});
