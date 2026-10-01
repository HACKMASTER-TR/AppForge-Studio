import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here =
  path.dirname(
    fileURLToPath(import.meta.url)
  );

const repo =
  path.resolve(
    here,
    "../.."
  );

const read =
  relative =>
    fs.readFileSync(
      path.join(
        repo,
        relative
      ),
      "utf8"
    );

const policy =
  read(
    "android-app/app/src/main/java/com/appforge/studio/build/WindowsPublisherSigningPolicy.kt"
  );

const store =
  read(
    "android-app/app/src/main/java/com/appforge/studio/build/WindowsPublisherSigningStore.kt"
  );

const provider =
  read(
    "android-app/app/src/main/java/com/appforge/studio/build/WindowsPublisherSigningProvider.kt"
  );

const engine =
  read(
    "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"
  );

test("non-owner cannot configure enable clear or execute publisher signing", () => {
  const ownerGuards =
    policy.match(
      /OwnerAccessPolicy\s*[\s\S]{0,100}?\.requireActiveOwner/g
    ) || [];

  /*
   * configureLocalPkcs12
   * setSigningEnabled
   * clearConfiguration
   * applyIfRequested
   */
  assert.ok(
    ownerGuards.length >= 4,
    `expected at least four owner guards, got ${ownerGuards.length}`
  );

  assert.match(
    provider,
    /override fun sign\([\s\S]*?requireActiveOwner/
  );

  assert.match(
    store,
    /fun importPkcs12\([\s\S]*?requireActiveOwner/
  );

  assert.match(
    store,
    /fun materializeInto\([\s\S]*?requireActiveOwner/
  );
});

test("enabled signing with missing or unknown provider fails closed", () => {
  assert.match(
    policy,
    /WindowsPublisherSigningProviders[\s\S]*\.resolve/
  );

  assert.match(
    policy,
    /provider != null[\s\S]*provider\.isConfigured/
  );

  assert.match(
    policy,
    /Windows publisher signing etkin ancak güvenli sertifika sağlayıcısı henüz yapılandırılmadı/
  );

  assert.doesNotMatch(
    policy,
    /provider == null[\s\S]{0,200}?return/
  );
});

test("invalid or wrong-password PKCS12 is parsed before any encrypted store write", () => {
  const validateIndex =
    store.indexOf(
      "validatePkcs12("
    );

  const importIndex =
    store.indexOf(
      "fun importPkcs12("
    );

  const importValidateIndex =
    store.indexOf(
      "validatePkcs12(",
      importIndex
    );

  const firstWriteIndex =
    store.indexOf(
      "writeAtomic(",
      importValidateIndex
    );

  assert.ok(
    validateIndex >= 0
  );

  assert.ok(
    importValidateIndex > importIndex
  );

  assert.ok(
    firstWriteIndex > importValidateIndex,
    "PKCS12 must validate before encrypted-at-rest write"
  );

  assert.match(
    store,
    /KeyStore[\s\S]*getInstance\([\s\S]*"PKCS12"/
  );

  assert.match(
    store,
    /keyStore\.load\([\s\S]*password/
  );
});

test("expired or not-yet-valid X509 certificate fails closed", () => {
  assert.match(
    store,
    /X509Certificate/
  );

  assert.match(
    store,
    /certificate\.checkValidity\(\)/
  );

  /*
   * checkValidity is intentionally called from validatePkcs12.
   * validatePkcs12 is used both during import and immediately
   * before each signing materialization.
   */
  assert.match(
    store,
    /private fun validatePkcs12\([\s\S]*certificate\.checkValidity\(\)/
  );

  const calls =
    store.match(
      /validatePkcs12\(/g
    ) || [];

  assert.ok(
    calls.length >= 3,
    `expected definition + import + materialize validation, got ${calls.length}`
  );

  assert.match(
    store,
    /PUBLISHER_SIGNING_REVALIDATION_V1/
  );
});

test("PKCS12 must contain a private key and certificate chain", () => {
  assert.match(
    store,
    /isKeyEntry/
  );

  assert.match(
    store,
    /privateKey is[\s\S]*PrivateKey/
  );

  assert.match(
    store,
    /getCertificateChain/
  );

  assert.match(
    store,
    /!chain\.isNullOrEmpty/
  );
});

test("tampered encrypted publisher material cannot be silently consumed", () => {
  assert.match(
    store,
    /AES\/GCM\/NoPadding/
  );

  assert.match(
    store,
    /GCMParameterSpec/
  );

  assert.match(
    store,
    /encrypted\.size[\s\S]*IV_BYTES \+ 16/
  );

  assert.match(
    store,
    /cipher\.doFinal\([\s\S]*payload/
  );

  assert.doesNotMatch(
    store,
    /runCatching[\s\S]{0,250}?decrypt[\s\S]{0,250}?getOrNull/
  );
});

test("offline publisher signing request fails before osslsigncode execution", () => {
  const offlineCheck =
    provider.indexOf(
      "!offline"
    );

  const command =
    provider.indexOf(
      "osslsigncode sign"
    );

  assert.ok(
    offlineCheck >= 0
  );

  assert.ok(
    command > offlineCheck
  );

  assert.match(
    provider,
    /timestamp doğrulaması için internet bağlantısı gerekli/
  );
});

test("all signing workspace failures clean temporary signing material", () => {
  const tryIndex =
    provider.indexOf(
      "PUBLISHER_SIGNING_WORKSPACE_FAIL_CLOSED_V1"
    );

  const copyIndex =
    provider.indexOf(
      "target.copyTo(",
      tryIndex
    );

  const materializeIndex =
    provider.indexOf(
      ".materializeInto(",
      tryIndex
    );

  const finallyIndex =
    provider.indexOf(
      "} finally {",
      materializeIndex
    );

  const cleanupIndex =
    provider.indexOf(
      ".deleteRecursively()",
      finallyIndex
    );

  assert.ok(
    tryIndex >= 0
  );

  assert.ok(
    copyIndex > tryIndex
  );

  assert.ok(
    materializeIndex > copyIndex
  );

  assert.ok(
    finallyIndex > materializeIndex
  );

  assert.ok(
    cleanupIndex > finallyIndex
  );

  assert.match(
    provider,
    /umask 077/
  );

  assert.match(
    provider,
    /chmod 600 \/workspace\/signer\.pfx \/workspace\/pass\.txt/
  );
});

test("signature success requires osslsigncode verify and explicit PASS marker", () => {
  assert.match(
    provider,
    /osslsigncode verify/
  );

  assert.match(
    provider,
    /APPFORGE_WINDOWS_PUBLISHER_SIGNING=PASS/
  );

  assert.match(
    provider,
    /line\.trim\(\)[\s\S]*APPFORGE_WINDOWS_PUBLISHER_SIGNING=PASS/
  );

  assert.match(
    provider,
    /Windows publisher signing doğrulama işareti alınamadı/
  );
});

test("any requested signing failure deletes final EXE and clears artifact state", () => {
  assert.match(
    engine,
    /WindowsPublisherSigningPolicy[\s\S]*\.signingRequested/
  );

  assert.match(
    engine,
    /catch\s*\(\s*signingFailure:\s*Throwable\s*\)/
  );

  assert.match(
    engine,
    /finalWindowsExe[\s\S]{0,100}?\.delete\(\)/
  );

  assert.match(
    engine,
    /state\.exe\s*=\s*null/
  );

  assert.match(
    engine,
    /publisher signing fail-closed/
  );

  /*
   * There must be no catch path that changes a signing failure into
   * an unsigned-successful build.
   */
  assert.match(
    engine,
    /catch\s*\(\s*signingFailure:\s*Throwable\s*\)\s*\{[\s\S]{0,1600}?finalWindowsExe[\s\S]{0,300}?\.delete\(\)[\s\S]{0,600}?state\.exe\s*=\s*null[\s\S]{0,1200}?throw\s+signingFailure\s*\}/
  );

  assert.match(
    engine,
    /catch\s*\(\s*t:\s*Throwable\s*\)\s*\{[\s\S]{0,900}?state\.status\s*=\s*"failed"/
  );
});
