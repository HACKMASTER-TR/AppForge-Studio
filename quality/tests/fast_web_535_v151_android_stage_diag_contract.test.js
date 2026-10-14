import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const key = fs.readFileSync(path.join(root,'android-app/app/src/main/java/com/appforge/studio/build/FastHtmlAabUploadKey.kt'),'utf8');
const engine = fs.readFileSync(path.join(root,'android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt'),'utf8');
test('V15.1 diagnostics isolate Android PKCS12 load, key access and signer setup', () => {
  for (const stage of ['PKCS12_PROVIDER','PKCS12_LOAD','PKCS12_ALIAS','PKCS12_PRIVATE_KEY','PKCS12_CERTIFICATE','PKCS12_KEY_PAIR','PKCS12_SIGNER_SETUP']) {
    assert.match(key,new RegExp('Stage\\.'+stage));
  }
  assert.match(key,/catch \(_: Exception\)/);
  assert.match(key,/IllegalStateException\("APPFORGE_FAST_AAB_STAGE_\$\{stage\.name\}"\)/);
  assert.doesNotMatch(key,/throw IllegalStateException\("APPFORGE_FAST_AAB_STAGE_\$\{stage\.name\}",/);
  assert.match(key,/storePwd\.fill/);
  assert.match(key,/keyPwd\.fill/);
});
test('V15.1 diagnostic milestones are custom-key-only and leave fail-closed cleanup', () => {
  for (const stage of ['KEY_OPEN_OK','UNSIGNED_OK','SIGNED_OK']) {
    assert.match(engine,new RegExp('APPFORGE_FAST_AAB_STAGE_'+stage));
  }
  assert.match(engine,/if \(uploadIdentity != null\)/);
  assert.match(engine,/candidateApk\?\.delete\(\)/);
  assert.match(engine,/candidateSignedAab\?\.delete\(\)/);
  assert.match(engine,/state\.apk = null/);
  assert.match(engine,/state\.aab = null/);
});
test('V15.1.1 bounds and closes file input before classified PKCS12 load', () => {
  assert.match(key,/MAX_KEYSTORE_BYTES = 10L \* 1024L \* 1024L/);
  assert.match(key,/guarded\(Stage\.PKCS12_INPUT_OPEN\) \{ path\.inputStream\(\) \}/);
  assert.match(key,/guarded\(Stage\.PKCS12_INPUT_READ\) \{\s*input\.use/);
  assert.match(key,/require\(size in 256\.\.MAX_KEYSTORE_BYTES\)/);
  assert.match(key,/ByteArray\(size\.toInt\(\)\)/);
  assert.match(key,/while \(offset < buffer\.size\)/);
  assert.match(key,/it\.read\(buffer, offset, buffer\.size - offset\)/);
  assert.match(key,/check\(count > 0\)/);
  assert.match(key,/check\(it\.read\(\) == -1\)/);
  assert.match(key,/check\(it\.read\(\) == -1\)[\s}]*guardedPkcs12Load \{\s*store\.load\(ByteArrayInputStream\(bytes\), storePwd\)/);
  assert.doesNotMatch(key,/path\.inputStream\(\)\.use \{ store\.load/);
  assert.match(key,/finally \{\s*p12Bytes\?\.fill\(0\)\s*storePwd\.fill/);
});
test('V15.1.1 load categories are fixed and never inspect exception text', () => {
  const helper = key.slice(key.indexOf('internal fun <T> guardedPkcs12Load'),
    key.indexOf('fun requireSelection'));
  for (const [type, category] of [
    ['IOException','IO_EXCEPTION'], ['CertificateException','CERTIFICATE_EXCEPTION'],
    ['NoSuchAlgorithmException','NO_SUCH_ALGORITHM_EXCEPTION'],
    ['RuntimeException','RUNTIME_EXCEPTION']
  ]) assert.ok(helper.includes(`is ${type} -> "${category}"`));
  assert.match(helper,/else -> "OTHER"/);
  assert.ok(helper.includes('"APPFORGE_FAST_AAB_STAGE_${Stage.PKCS12_LOAD.name}_$category"'));
  assert.doesNotMatch(helper,/failure\.|\.message|\.cause|toString|stackTrace|print|log/i);
});
