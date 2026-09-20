import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
const root = new URL('../../android-app/app/', import.meta.url);
const read = name => readFile(new URL(`src/main/java/com/appforge/studio/${name}`, root), 'utf8');

test('owner entitlement requires server-verified memory identity, never a stored admin flag', async () => {
  const owner = await read('security/OwnerAccessPolicy.kt');
  assert.match(owner, /fun isActiveOwner[\s\S]{0,180}?currentGoogleIdToken\(\) != null/);
  assert.match(owner, /@Volatile private var googleIdToken: String\? = null/);
  assert.match(owner, /currentGoogleIdToken\(\)/);
  assert.match(owner, /validUntilMs/);
  assert.match(owner, /clearVerifiedGoogleAdmin/);
  const currentGate = owner.slice(owner.indexOf('fun isActiveOwner('), owner.indexOf('fun requireActiveOwner('));
  assert.doesNotMatch(currentGate, /SecureAccountStore|isOwnerEmail|sharedPreferences/);
});

test('only explicit Google button can start credential flow; server verifies before remembering admin token', async () => {
  const google = await read('security/GoogleAdminIdentityClient.kt');
  assert.match(google, /GetSignInWithGoogleOption/);
  assert.match(google, /CredentialManager/);
  assert.match(google, /\.setNonce\(nonce\)/);
  assert.match(google, /api\/admin\/google\/verify/);
  assert.match(google, /rememberVerifiedGoogleAdmin\(activity, idToken, expiresAt, endpoint\)/);
  assert.ok(google.indexOf('verify(idToken, nonce)') < google.indexOf('rememberVerifiedGoogleAdmin'));
  assert.doesNotMatch(google, /SecureAccountStore|saveSession|saveAccessToken|BuildService/);
  const gradle = await readFile(new URL('build.gradle.kts', root), 'utf8');
  assert.match(gradle, /credentials-play-services-auth:1\.6\.0/);
  assert.match(gradle, /googleid:1\.2\.1/);
  assert.doesNotMatch(gradle, /GOOGLE_CLIENT_SECRET/);
});

test('home entry replaces legacy login; Terminal and Second Brain require owner', async () => {
  const [home, main, ops] = await Promise.all([
    read('ui/StudioHomeV2.kt'), read('MainActivity.kt'), read('AdminOpsScreen.kt')
  ]);
  assert.match(home, /YÖNETİCİ GİRİŞİ/);
  assert.doesNotMatch(home, /GİRİŞ YAP/);
  assert.match(home, /if \(fullAdmin\)[\s\S]*OwnerAdminCard/);
  assert.match(main, /terminalOwner[\s\S]*AppScreen\.TERMINAL/);
  assert.match(main, /AppScreen\.SECOND_BRAIN[\s\S]*isAdminOpsAccount/);
  assert.match(ops, /GoogleAdminIdentityClient\(host, serverUrl\)\.signIn\(\)/);
  assert.match(ops, /OwnerAccessPolicy\.currentGoogleIdToken\(\)/);
  assert.match(ops, /SecureAccountStore\.loadVerifiedGoogleAdmin/);
  assert.match(ops, /OwnerAccessPolicy\.rememberVerifiedGoogleAdmin/);
  assert.match(ops, /AdminAuthorizationDenied/);
  assert.doesNotMatch(ops, /"HESAP YÖNETİMİ"/);
});
