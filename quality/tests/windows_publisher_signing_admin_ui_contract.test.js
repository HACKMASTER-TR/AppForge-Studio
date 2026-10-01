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

test("publisher certificate UI remains owner-only", () => {
  const ui = read(
    "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
  );

  assert.match(
    ui,
    /windowsSigningAdmin/
  );

  assert.match(
    ui,
    /OwnerAccessPolicy[\s\S]*isActiveOwner/
  );

  assert.match(
    ui,
    /WindowsPublisherSigningAdminCard/
  );

  assert.match(
    ui,
    /Windows Yayıncı İmzası • Yönetici/
  );
});

test("publisher UI imports PFX or P12 with a transient password", () => {
  const ui = read(
    "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
  );

  assert.match(
    ui,
    /ActivityResultContracts[\s\S]*OpenDocument/
  );

  assert.match(
    ui,
    /PFX \/ P12 İÇE AKTAR/
  );

  assert.match(
    ui,
    /PasswordVisualTransformation/
  );

  assert.match(
    ui,
    /configureLocalPkcs12/
  );

  assert.match(
    ui,
    /passwordChars\.fill/
  );

  assert.match(
    ui,
    /certificateBytes[\s\S]*fill/
  );
});

test("publisher certificate document read is bounded to 10 MB", () => {
  const ui = read(
    "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
  );

  assert.match(
    ui,
    /WINDOWS_PUBLISHER_CERT_MAX_BYTES[\s\S]*10 \* 1024 \* 1024/
  );

  assert.match(
    ui,
    /readBoundedDocumentBytes/
  );

  assert.match(
    ui,
    /maxBytes \+ 1/
  );

  assert.match(
    ui,
    /10 MB sınırını aşıyor/
  );
});

test("PKCS12 is cryptographically parsed before encrypted storage", () => {
  const store = read(
    "android-app/app/src/main/java/com/appforge/studio/build/WindowsPublisherSigningStore.kt"
  );

  assert.match(
    store,
    /KeyStore[\s\S]*getInstance\([\s\S]*"PKCS12"/
  );

  assert.match(
    store,
    /isKeyEntry/
  );

  assert.match(
    store,
    /PrivateKey/
  );

  assert.match(
    store,
    /X509Certificate/
  );

  assert.match(
    store,
    /checkValidity/
  );

  assert.match(
    store,
    /getCertificateChain/
  );

  assert.match(
    store,
    /validatePkcs12[\s\S]*encrypt/
  );
});

test("admin can explicitly enable disable and remove publisher material", () => {
  const ui = read(
    "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
  );

  const policy = read(
    "android-app/app/src/main/java/com/appforge/studio/build/WindowsPublisherSigningPolicy.kt"
  );

  assert.match(
    ui,
    /setSigningEnabled/
  );

  assert.match(
    ui,
    /SERTİFİKAYI KALDIR/
  );

  assert.match(
    ui,
    /clearConfiguration/
  );

  assert.match(
    policy,
    /fun providerConfigured/
  );

  assert.match(
    policy,
    /requireActiveOwner/
  );
});

test("publisher secrets are not rendered or logged", () => {
  const ui = read(
    "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
  );

  assert.doesNotMatch(
    ui,
    /Text\(\s*certificatePassword\s*\)/
  );

  assert.doesNotMatch(
    ui,
    /println\([^)]*certificatePassword/
  );

  assert.match(
    ui,
    /Private key\/PFX kaynak koda, GitHub'a, proje ZIP'ine veya build loglarına yazılmaz/
  );
});
