import test from 'node:test';
import assert from 'node:assert/strict';
import {
  generateKeyPairSync,
  sign,
  randomBytes,
  createHash
} from 'node:crypto';

import {
  signatureToP1363,
  redeemMessage,
  statusMessage,
  publicKeyThumbprint,
  verifyDeviceSignature
} from '../src/device_proof.mjs';

const pair = generateKeyPairSync('ec', {
  namedCurve: 'prime256v1'
});

const anotherPair = generateKeyPairSync('ec', {
  namedCurve: 'prime256v1'
});

const publicKey = pair.publicKey
  .export({ type: 'spki', format: 'der' })
  .toString('base64url');

const otherPublicKey = anotherPair.publicKey
  .export({ type: 'spki', format: 'der' })
  .toString('base64url');

const code = 'AFPRO-' + randomBytes(32).toString('base64url');
const nonce = randomBytes(32).toString('base64url');

const message = redeemMessage({
  code, publicKey, nonce
});

function signatureFor(value, privateKey, encoding = 'der') {
  return sign(
    'sha256',
    Buffer.from(value),
    {
      key: privateKey,
      dsaEncoding: encoding
    }
  ).toString('base64url');
}

test('Android-compatible DER ECDSA signature verifies', async () => {
  assert.equal(
    await verifyDeviceSignature({
      publicKey,
      message,
      signature: signatureFor(message, pair.privateKey)
    }),
    true
  );
});

test('P1363 ECDSA signature also verifies', async () => {
  const raw = signatureFor(
    message, pair.privateKey, 'ieee-p1363'
  );

  assert.equal(
    await verifyDeviceSignature({
      publicKey,
      message,
      signature: raw
    }),
    true
  );
});

test('changed code, nonce and public key cannot reuse proof', async () => {
  const signature = signatureFor(
    message, pair.privateKey
  );

  assert.equal(
    await verifyDeviceSignature({
      publicKey,
      signature,
      message: message + '\nchanged'
    }),
    false
  );

  assert.equal(
    await verifyDeviceSignature({
      publicKey: otherPublicKey,
      signature,
      message
    }),
    false
  );

  const otherNonce = randomBytes(32).toString('base64url');

  assert.equal(
    await verifyDeviceSignature({
      publicKey,
      signature,
      message: redeemMessage({
        code,
        publicKey,
        nonce: otherNonce
      })
    }),
    false
  );
});

test('malformed signatures fail closed', async () => {
  assert.throws(
    () => signatureToP1363(
      Uint8Array.of(0x30, 0x02, 0x02, 0x00)
    ),
    /invalid_device_proof/
  );

  assert.equal(
    await verifyDeviceSignature({
      publicKey,
      message,
      signature: 'not-valid!'
    }),
    false
  );
});

test('thumbprint is SHA-256 of the SPKI public key', async () => {
  const expected = createHash('sha256')
    .update(
      pair.publicKey.export({
        type: 'spki',
        format: 'der'
      })
    )
    .digest('hex');

  assert.equal(
    await publicKeyThumbprint(publicKey),
    expected
  );
});

test('status proof binds installation and server challenge', () => {
  const status = statusMessage({
    installationId:
      '11111111-1111-4111-8111-111111111111',
    challengeId:
      '22222222-2222-4222-8222-222222222222',
    nonce
  });

  assert.match(
    status,
    /^appforge-pro-status-v1\n/
  );

  assert.throws(
    () => statusMessage({
      installationId: 'spoofed-id',
      challengeId:
        '22222222-2222-4222-8222-222222222222',
      nonce
    }),
    /invalid_device_proof/
  );
});
