import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const client = fs.readFileSync(
  new URL(
    '../../android-app/app/src/main/java/' +
      'com/appforge/studio/security/ProCodeClient.kt',
    import.meta.url
  ),
  'utf8'
);

const panel = fs.readFileSync(
  new URL(
    '../../android-app/app/src/main/java/' +
      'com/appforge/studio/ProCodePanels.kt',
    import.meta.url
  ),
  'utf8'
);

test('Android lists admin Pro grants', () => {
  assert.match(
    client,
    /fun listAdminGrants\(/
  );

  assert.match(
    client,
    /"\/api\/admin\/pro-grants"/
  );

  assert.match(
    client,
    /getJSONArray\("grants"\)/
  );
});

test('Android revokes only the selected grant', () => {
  assert.match(
    client,
    /fun revokeAdminGrant\(/
  );

  assert.match(
    client,
    /"\/revoke"/
  );

  assert.match(
    client,
    /result\.optString\("installationId"\)/
  );
});

test('grant revoke requires explicit UI confirmation', () => {
  assert.match(
    panel,
    /pendingRevoke = grant/
  );

  assert.match(
    panel,
    /AlertDialog\(/
  );

  assert.match(
    panel,
    /client\.revokeAdminGrant\(/
  );

  assert.match(
    panel,
    /Text\("VAZGEÇ"\)/
  );
});

test('previous Pro code operations remain', () => {
  for (const method of [
    'issueAdminCode',
    'listAdminCodes',
    'revokeUnusedCode',
    'redeemAndVerify',
    'verifyStatus'
  ]) {
    assert.ok(
      client.includes(`fun ${method}(`),
      method
    );
  }
});
