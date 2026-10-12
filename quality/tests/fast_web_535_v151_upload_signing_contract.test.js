import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const read = p => fs.readFileSync(path.join(root,p),'utf8');
const prefix = 'android-app/app/src/main/java/com/appforge/studio/';
const key = read(prefix+'build/FastHtmlAabUploadKey.kt');
const engine = read(prefix+'build/DeviceBuildEngine.kt');
const apk = read(prefix+'build/FastHtmlApkPackager.kt');
const unsigned = read(prefix+'build/FastHtmlAabUnsignedPackager.kt');
const offline = read(prefix+'build/OfflineBuildPackManager.kt');
const ui = read(prefix+'MainActivity.kt');
test('V15.1 custom signing requires an imported backed-up PKCS12 with exact registered hash', () => {
  assert.match(key,/!managed.generated/);
  assert.match(key,/managed.sha256/);
  assert.match(key,/stored.equals\(actual, true\)/);
  assert.match(key,/"PKCS12"/);
  assert.match(key,/requireMatchingKey\(key, certificate.publicKey\)/);
  assert.match(key,/storePwd.fill/);
  assert.match(key,/keyPwd.fill/);
});
test('V15.1 forbids custom APK-only fast path and preserves 534 debug signer', () => {
  assert.match(apk,/uploadSigner \?: getOrCreateSigner\(\)/);
  assert.match(unsigned,/draft.signingMode == com.appforge.studio.model.SigningMode.CUSTOM/);
  assert.match(engine,/val customFastSigner = wantsAab && draft.signingMode == SigningMode.CUSTOM/);
  assert.match(engine,/if \(!wantsAab\)/);
  assert.match(offline,/FastHtmlAabUploadKey.requireSelection\(context, draft\)/);
});
test('V15.1 dual outputs have matching signer cert and fail closed on any error', () => {
  assert.match(engine,/verified.signerCertificates.size == 1/);
  assert.match(engine,/uploadIdentity.certificate.encoded/);
  assert.match(engine,/candidateApk\?\.delete\(\)/);
  assert.match(engine,/candidateSignedAab\?\.delete\(\)/);
  assert.match(engine,/state.apk = null/);
  assert.match(engine,/state.aab = null/);
  assert.match(engine,/checkCancelled\(state\)\s+state.progress = 100/);
});
test('V15.1 preflight reports accurate AAB and PKCS12 limits', () => {
  assert.match(ui,/Hızlı HTML APK\/AAB ayarları/);
  assert.match(ui,/Kasaya içe aktarılmış PKCS12/);
  assert.match(engine,/Play yükleme sertifikası Console/);
  assert.doesNotMatch(key,/HTTP|https:\/\//);
});
