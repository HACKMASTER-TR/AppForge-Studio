import test from 'node:test';
import assert from 'node:assert/strict';
import {
  generateKeyPairSync,
  randomBytes,
  sign
} from 'node:crypto';

import { handleProRedemption } from '../src/pro_redemption.mjs';
import {
  redeemMessage,
  statusMessage
} from '../src/device_proof.mjs';

const pair = generateKeyPairSync('ec', {
  namedCurve: 'prime256v1'
});

const publicKey = pair.publicKey
  .export({ type: 'spki', format: 'der' })
  .toString('base64url');

const signMessage = message =>
  sign(
    'sha256',
    Buffer.from(message),
    pair.privateKey
  ).toString('base64url');

const code = 'AFPRO-' +
  randomBytes(32).toString('base64url');

const nonce = randomBytes(32).toString('base64url');

const request = (route, body, method = 'POST') =>
  new Request('https://worker.test' + route, {
    method,
    headers: { 'content-type': 'application/json' },
    body: method === 'POST'
      ? JSON.stringify(body)
      : undefined
  });

const invoke = (route, body, env) =>
  handleProRedemption(
    request(route, body),
    env,
    route
  );

test('Pro routes require D1 and POST', async () => {
  const route = '/api/pro/code/redeem';

  assert.equal(
    (await invoke(route, {}, {})).status,
    503
  );

  assert.equal(
    (await handleProRedemption(
      request(route, {}, 'GET'),
      { DB: { prepare() {} } },
      route
    )).status,
    405
  );
});

test('fake installation, malformed code and signature fail closed', async () => {
  const env = {
    DB: {
      prepare() {
        throw Error('DB should not be queried');
      },
      batch() {
        throw Error('batch should not run');
      }
    }
  };

  assert.equal(
    (await invoke(
      '/api/pro/code/redeem',
      { code: '123', publicKey, nonce, signature: 'fake' },
      env
    )).status,
    400
  );

  assert.equal(
    (await invoke(
      '/api/pro/code/redeem',
      { code, publicKey, nonce, signature: 'fake' },
      env
    )).status,
    401
  );

  assert.equal(
    (await invoke(
      '/api/pro/code/status',
      {
        installationId: 'spoofed',
        challengeId: 'spoofed',
        nonce,
        signature: 'fake'
      },
      env
    )).status,
    400
  );
});

test('valid signed redemption cannot proceed without a batch transaction', async () => {
  const message = redeemMessage({
    code, publicKey, nonce
  });

  const response = await invoke(
    '/api/pro/code/redeem',
    {
      code,
      publicKey,
      nonce,
      signature: signMessage(message)
    },
    {
      DB: {
        prepare() {
          throw Error('must not read before batch availability');
        }
      }
    }
  );

  assert.equal(response.status, 503);
});

test('status cannot be granted by an installation ID alone', async () => {
  const installationId =
    '11111111-1111-4111-8111-111111111111';
  const challengeId =
    '22222222-2222-4222-8222-222222222222';

  const response = await invoke(
    '/api/pro/code/status',
    {
      installationId,
      challengeId,
      nonce,
      signature: 'fake'
    },
    {
      DB: {
        prepare() {
          return {
            bind() {
              return {
                async first() {
                  return null;
                }
              };
            }
          };
        }
      }
    }
  );

  assert.equal(response.status, 409);
  assert.equal(
    JSON.stringify(await response.json())
      .includes('"active":true'),
    false
  );
});

test('status signature binds the exact challenge and installation', () => {
  const installationId =
    '11111111-1111-4111-8111-111111111111';
  const challengeId =
    '22222222-2222-4222-8222-222222222222';

  const message = statusMessage({
    installationId,
    challengeId,
    nonce
  });

  assert.notEqual(
    message,
    statusMessage({
      installationId,
      challengeId:
        '33333333-3333-4333-8333-333333333333',
      nonce
    })
  );
});
