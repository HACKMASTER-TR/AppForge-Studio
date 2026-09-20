import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const kotlin = name => fs.readFileSync(
  new URL('../../android-app/app/src/main/java/com/appforge/studio/' + name, import.meta.url),
  'utf8'
);
const client = kotlin('security/ProCodeClient.kt');
const key = kotlin('security/ProInstallationProof.kt');
const panel = kotlin('ProCodePanels.kt');
const main = kotlin('MainActivity.kt');
const worker = fs.readFileSync(
  new URL('../../cloudflare/control-plane/src/pro_redemption.mjs', import.meta.url), 'utf8'
);

test('same Keystore key is used for signed recovery and reactivation', () => {
  assert.match(key, /fun createOwnershipProof\(/);
  assert.match(key, /"appforge-pro-ownership-v1"/);
  assert.match(key, /sign\(parts\.joinToString\("\\n"\), pair\.private\)/);
  assert.match(client, /fun recoverInstallation\(/);
  assert.match(client, /private fun reactivateAndVerify\(/);
  assert.match(client, /return reactivateAndVerify\(code\)/);
  assert.doesNotMatch(client, /fun clearProKey|deleteEntry\(KEY_ALIAS\)/);
});

test('client does not unlock without a second fresh status call', () => {
  assert.match(client, /persistInstallation\(proof\.installationId\)/);
  assert.match(client, /return verifyStatus\(\)/);
  assert.match(client, /if \(!result\.optBoolean\("active"\)\)/);
  assert.match(panel, /KURULUMU ANAHTARLA KURTAR/);
});

test('account session does not suppress device entitlement verification', () => {
  assert.match(main, /val current =\s*session\s*\/\/ Clear previous entitlement/);
  assert.match(main, /if \(codeClient\.hasInstallation\(\)\) \{\s*try \{/);
  assert.match(main, /StudioSecurityClient\(/);
  assert.match(main, /delay\(60_000L\)/);
});

test('worker will not reactivate with client-chosen unchallenged signatures', () => {
  assert.match(worker, /ownershipChallenge\(env, body\)/);
  assert.match(worker, /pathname\.endsWith\('\/recover'\) \? 'recover' : 'reactivate'/);
  assert.match(worker, /consumed_at IS NULL AND expires_at > \?/);
  assert.match(worker, /receiptGuard\(env/);
});
