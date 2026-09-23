import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const android = readFileSync(
  new URL(
    '../../android-app/app/src/main/java/com/appforge/studio/security/ProInstallationProof.kt',
    import.meta.url
  ),
  'utf8'
);

const server = readFileSync(
  new URL(
    '../../cloudflare/control-plane/src/device_proof.mjs',
    import.meta.url
  ),
  'utf8'
);

test('Pro private key is generated and retained in Android Keystore', () => {
  assert.match(android, /KeyStore\.getInstance\("AndroidKeyStore"\)/);

  assert.match(
    android,
    /KeyPairGenerator\.getInstance\(\s*KeyProperties\.KEY_ALGORITHM_EC,\s*"AndroidKeyStore"/
  );

  assert.match(
    android,
    /ECGenParameterSpec\("secp256r1"\)/
  );

  assert.match(
    android,
    /KeyProperties\.DIGEST_SHA256/
  );

  assert.match(
    android,
    /Signature\.getInstance\("SHA256withECDSA"\)/
  );

  assert.doesNotMatch(
    android,
    /StudioDeviceIdentity|ANDROID_ID|privateKey\.encoded/
  );
});

test('Android redemption message agrees with Worker protocol', () => {
  assert.match(
    android,
    /"appforge-pro-redeem-v1"/
  );

  assert.match(
    android,
    /"appforge-pro-status-v1"/
  );

  assert.match(
    server,
    /'appforge-pro-redeem-v1'/
  );

  assert.match(
    server,
    /'appforge-pro-status-v1'/
  );

  assert.match(
    android,
    /joinToString\("\\n"\)/
  );

  assert.match(
    android,
    /Base64\.URL_SAFE/
  );

  assert.match(
    android,
    /Base64\.NO_PADDING/
  );
});

test('Android proof cannot independently grant Pro', () => {
  assert.doesNotMatch(
    android,
    /active\s*=\s*true|grantPro|setProActive|SharedPreferences/
  );

  assert.match(
    android,
    /fun createRedeemProof\(/
  );

  assert.match(
    android,
    /fun createStatusProof\(/
  );

  assert.match(
    server,
    /signatureToP1363/
  );
});
