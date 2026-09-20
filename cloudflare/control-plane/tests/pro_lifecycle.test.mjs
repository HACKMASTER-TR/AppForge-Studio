import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, randomBytes, sign } from 'node:crypto';
import { handleProRedemption } from '../src/pro_redemption.mjs';
import { ownershipMessage, statusMessage, verifyDeviceSignature } from '../src/device_proof.mjs';

const pair = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
const publicKey = pair.publicKey.export({ type: 'spki', format: 'der' }).toString('base64url');
const code = 'AFPRO-' + randomBytes(32).toString('base64url');
const nonce = randomBytes(32).toString('base64url');
const installationId = '11111111-1111-4111-8111-111111111111';
const challengeId = '22222222-2222-4222-8222-222222222222';
const signature = text => sign('sha256', Buffer.from(text), pair.privateKey).toString('base64url');
const invoke = (pathname, body, DB) => handleProRedemption(
  new Request('https://worker.test' + pathname, {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify(body)
  }), { DB }, pathname
);

test('recovery and reactivation have different signed domains and bind code', async () => {
  const common = { installationId, challengeId, nonce, publicKey };
  const recover = ownershipMessage({ ...common, operation: 'recover' });
  const reactivate = ownershipMessage({ ...common, operation: 'reactivate', code });
  assert.notEqual(recover, reactivate);
  assert.notEqual(recover, statusMessage(common));
  assert.equal(await verifyDeviceSignature({ publicKey, signature: signature(recover), message: recover }), true);
  assert.equal(await verifyDeviceSignature({ publicKey, signature: signature(recover), message: reactivate }), false);
  assert.equal(await verifyDeviceSignature({ publicKey, signature: signature(reactivate), message: reactivate }), true);
  assert.throws(() => ownershipMessage({ ...common, operation: 'recover', code }));
  assert.throws(() => ownershipMessage({ ...common, operation: 'reactivate' }));
});

test('no installation proof by public ID or unsigned ownership message', async () => {
  const DB = { prepare() { return { bind() { return { async first() { return { id: installationId }; } }; } }; } };
  for (const operation of ['recover','reactivate']) {
    const path = '/api/pro/code/' + operation;
    const response = await invoke(path, { installationId, challengeId, nonce,
      publicKey, code, signature: 'invalid' }, DB);
    assert.notEqual(response.status, 200);
    assert.doesNotMatch(await response.text(), /"active":true/);
  }
});

test('unknown public key cannot obtain an ownership challenge', async () => {
  const DB = { prepare() { return { bind() { return { async first() { return null; } }; } }; } };
  const response = await invoke('/api/pro/code/ownership-challenge',
    { publicKey, requestNonce: nonce }, DB);
  assert.equal(response.status, 404);
  assert.doesNotMatch(await response.text(), /"active":true/);
});

test('recovery returns no active Pro for a revoked grant', async () => {
  let reads = 0;
  const DB = { prepare(sql) {
    return { bind() { return {
      async first() {
        reads++;
        return reads === 1 ? { id: installationId } : { state: 'revoked' };
      },
      async run() { return { meta: { changes: 1 } }; }
    }; } };
  } };
  const message = ownershipMessage({ operation:'recover', installationId,
    challengeId, nonce, publicKey });
  const response = await invoke('/api/pro/code/recover',
    { installationId, challengeId, nonce, publicKey,
      signature: signature(message) }, DB);
  assert.equal(response.status, 200);
  const data = await response.json();
  assert.equal(data.active, false);
  assert.equal(data.installationId, installationId);
});

test('recovery rejects already-consumed challenge and cannot grant Pro', async () => {
  const DB = { prepare() { return { bind() { return {
    async first() { return { id: installationId }; },
    async run() { return { meta: { changes: 0 } }; }
  }; } }; } };
  const message = ownershipMessage({ operation:'recover', installationId,
    challengeId, nonce, publicKey });
  const response = await invoke('/api/pro/code/recover',
    { installationId, challengeId, nonce, publicKey,
      signature: signature(message) }, DB);
  assert.equal(response.status, 409);
  assert.doesNotMatch(await response.text(), /"active":true/);
});
