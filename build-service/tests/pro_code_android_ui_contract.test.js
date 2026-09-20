import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const root = new URL(
  '../../android-app/app/src/main/java/com/appforge/studio/',
  import.meta.url
);

const read = relative =>
  readFileSync(
    new URL(relative, root),
    'utf8'
  );

const client = read(
  'security/ProCodeClient.kt'
);

const proof = read(
  'security/ProInstallationProof.kt'
);

const panels = read(
  'ProCodePanels.kt'
);

const admin = read(
  'AdminOpsScreen.kt'
);

const main = read(
  'MainActivity.kt'
);

test('Pro code is verified through challenge before unlocking', () => {
  assert.match(
    client,
    /return verifyStatus\(\)/
  );

  assert.match(
    client,
    /ProInstallationProof\.createRedeemProof/
  );

  assert.match(
    client,
    /ProInstallationProof\.createStatusProof/
  );

  assert.match(
    client,
    /"\/api\/pro\/code\/redeem"/
  );

  assert.match(
    client,
    /"\/api\/pro\/code\/challenge"/
  );

  assert.match(
    client,
    /"\/api\/pro\/code\/status"/
  );

  assert.match(
    client,
    /status\.optString\("entitlementKind"\)/
  );

  assert.match(
    client,
    /"admin_grant"/
  );

  assert.match(
    proof,
    /"AndroidKeyStore"/
  );

  assert.doesNotMatch(
    client,
    /StudioDeviceIdentity|ANDROID_ID/
  );
});

test('app revalidates accountless Pro instead of trusting local ID', () => {
  assert.match(
    main,
    /codeClient\.hasInstallation\(\)/
  );

  assert.match(
    main,
    /codeClient\.verifyStatus\(\)/
  );

  assert.match(
    main,
    /catch \(error: Exception\) \{\s*proStatus = null/
  );
});

test('admin and customer surfaces are connected', () => {
  assert.match(
    admin,
    /AdminProCodesPanel\(/
  );

  assert.match(
    panels,
    /fun AdminProCodesPanel\(/
  );

  assert.match(
    panels,
    /issueAdminCode/
  );

  assert.match(
    panels,
    /listAdminCodes/
  );

  assert.match(
    panels,
    /revokeUnusedCode/
  );

  assert.match(
    main,
    /ProCodeActivationPanel\(/
  );

  assert.match(
    panels,
    /redeemAndVerify/
  );

  assert.match(
    panels,
    /onVerified\(verified\)/
  );

  assert.match(
    panels,
    /Yönetici Pro yetkin sunucuda doğrulandı|Yönetici Pro yetkisi/
  );
});

test('admin code creation uses current server-verified Google token', () => {
  assert.match(
    panels,
    /OwnerAccessPolicy\s*\.currentGoogleIdToken\(\)/
  );

  assert.match(
    client,
    /"Authorization",\s*"Bearer \$adminToken"/
  );

  assert.doesNotMatch(
    panels,
    /saveSession|isOwnerEmail|admin\s*=\s*true/
  );
});
