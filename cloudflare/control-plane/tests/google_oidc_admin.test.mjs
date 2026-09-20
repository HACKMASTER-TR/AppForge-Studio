import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, sign, createHash } from 'node:crypto';
import { handleRequest } from '../src/index.mjs';

const NONCE = 'Z'.repeat(43);
const WEB_ID = '123456-web.apps.googleusercontent.com';
const ANDROID_ID = '123456-android.apps.googleusercontent.com';
const { publicKey, privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const jwk = { ...publicKey.export({ format: 'jwk' }), kid: 'test-key', use: 'sig', alg: 'RS256' };
const keys = async url => {
  assert.equal(url, 'https://www.googleapis.com/oauth2/v3/certs');
  return { ok: true, json: async () => ({ keys: [jwk] }) };
};
const b64 = input => Buffer.from(typeof input === 'string' ? input : JSON.stringify(input)).toString('base64url');
function signedToken(overrides = {}, header = {}) {
  const now = Math.floor(Date.now() / 1000);
  const a = b64({ alg: 'RS256', kid: 'test-key', typ: 'JWT', ...header });
  const b = b64({ iss: 'https://accounts.google.com', aud: WEB_ID,
    azp: ANDROID_ID, sub: 'owner-google-sub-stable', nonce: NONCE, iat: now, exp: now + 1200,
    ...overrides });
  return `${a}.${b}.${sign('RSA-SHA256', Buffer.from(`${a}.${b}`), privateKey).toString('base64url')}`;
}
const hash = createHash('sha256').update('owner-google-sub-stable').digest('hex');
const env = (state = 'active') => ({
  GOOGLE_WEB_CLIENT_ID: WEB_ID, GOOGLE_ANDROID_CLIENT_ID: ANDROID_ID,
  DB: { prepare(query) {
    assert.equal(query, 'SELECT state FROM admin_identities WHERE google_subject_hash = ?');
    return { bind(value) {
      assert.equal(value, hash);
      return { async first() { return { state }; } };
    }};
  }}
});
const get = token => new Request('https://worker.test/api/admin/system-status', {
  headers: { authorization: `Bearer ${token}`, 'X-AppForge-Email': 'owner@example.test',
    'X-AppForge-Device-ID': 'spoofable' }
});
const post = token => new Request('https://worker.test/api/admin/google/verify', {
  method: 'POST', headers: { 'content-type': 'application/json' },
  body: JSON.stringify({ idToken: token, nonce: NONCE })
});
const call = (request, environment = env(), opts = { fetchKeys: keys }) =>
  handleRequest(request, environment, opts);

test('real RS256 Google signature and D1 provisioned sub enable only two admin identity routes', async () => {
  const token = signedToken();
  for (const req of [get(token), post(token)]) {
    const result = await call(req);
    assert.equal(result.status, 200);
    const body = await result.json();
    assert.equal(body.adminVerified, true);
    assert.equal(body.ok, true);
    assert.equal(body.sub, undefined);
    assert.equal(body.email, undefined);
    assert.equal(result.headers.get('cache-control'), 'no-store');
  }
  assert.equal((await call(new Request('https://worker.test/api/admin/users', {
    headers: { Authorization: `Bearer ${token}` }
  }))).status, 503);
});

test('no OAuth config or D1 => 503 instead of admin', async () => {
  assert.equal((await call(get(signedToken()), {})).status, 503);
  assert.equal((await call(get(signedToken()), { GOOGLE_WEB_CLIENT_ID: WEB_ID })).status, 503);
});

test('old bearer, plain email, device ID, unsigned JWT cannot grant', async () => {
  for (const token of ['old-token', 'bearer', b64({ alg: 'none' })+'.'+b64({ sub: 'owner-google-sub-stable' })+'.xxx']) {
    const result = await call(get(token));
    assert.equal(result.status, 401);
    assert.equal((await result.json()).error, 'invalid_identity');
  }
});

test('signed identity not explicitly active in server D1 cannot become admin', async () => {
  assert.equal((await call(get(signedToken()), env('disabled'))).status, 403);
});

test('D1 allowlist outage fails closed, not forbidden or granted', async () => {
  const failing = env();
  failing.DB.prepare = () => { throw Error('private DB diagnostics'); };
  const result = await call(get(signedToken()), failing);
  assert.equal(result.status, 503);
  assert.doesNotMatch(await result.text(), /private DB diagnostics/);
});

test('reject wrong audience/issuer/presenter, expired or future claims', async () => {
  const now = Math.floor(Date.now()/1000);
  for (const claims of [
    { aud: 'different.apps.googleusercontent.com' }, { iss: 'https://not-google.test' },
    { azp: 'different.apps.googleusercontent.com' },
    { exp: now - 1 }, { iat: now + 120 }, { iat: now - 3700 },
    { sub: '' }, { exp: 'forever' }
  ]) {
    assert.equal((await call(get(signedToken(claims)))).status, 401, JSON.stringify(claims));
  }
});

test('reject altered signature and alg substitution', async () => {
  const token = signedToken();
  const a = token.split('.');
  a[2] = a[2].slice(0, -3) + 'abc';
  assert.equal((await call(get(a.join('.')))).status, 401);
  assert.equal((await call(get(signedToken({}, { alg: 'HS256' })))).status, 401);
});

test('Cloudflare JWKS uses manual redirects and rejects redirect responses', async () => {
  const token = signedToken();

  const redirected = await call(get(token), env(), {
    fetchKeys: async (url, init) => {
      assert.equal(url, 'https://www.googleapis.com/oauth2/v3/certs');
      assert.equal(init.redirect, 'manual');
      return { ok: false, status: 302 };
    }
  });

  assert.equal(redirected.status, 503);
  assert.deepEqual(await redirected.json(), {
    ok: false,
    error: 'identity_provider_unavailable'
  });

  const valid = await call(get(token), env(), {
    fetchKeys: async (url, init) => {
      assert.equal(init.redirect, 'manual');
      return keys(url);
    }
  });

  assert.equal(valid.status, 200);
});

test('Google JWKS outage must be 503 without revealing claims', async () => {
  const result = await call(get(signedToken()), env(), {
    fetchKeys: async () => { throw Error('network outage with private data'); }
  });
  assert.equal(result.status, 503);
  assert.deepEqual(await result.json(), { ok: false, error: 'identity_provider_unavailable' });
});

test('invalid HTTP method or oversized identity body does not grant admin', async () => {
  const token = signedToken();
  assert.equal((await call(new Request('https://worker.test/api/admin/system-status', {
    method: 'POST', body: '{}'
  }))).status, 405);
  assert.equal((await call(new Request('https://worker.test/api/admin/google/verify', {
    method: 'POST', body: 'x'.repeat(15000)
  }))).status, 400);
  assert.equal((await call(new Request('https://worker.test/api/admin/google/verify', {
    method: 'GET', headers: { authorization: `Bearer ${token}` }
  }))).status, 405);
});

test('Google admin cannot accidentally enable Pro, accounts or remote builds', async () => {
  for (const path of ['/api/pro/activate', '/api/security/config', '/api/builds']) {
    assert.equal((await call(new Request(`https://worker.test${path}`, {
      method: 'POST', headers: { Authorization: `Bearer ${signedToken()}` }, body: '{}'
    }))).status, 503);
  }
  assert.equal((await call(new Request('https://worker.test/api/auth/login', {
    method: 'POST', body: '{}'
  }))).status, 410);
});


test('Google admin POST binds an unguessable client nonce to signed ID token', async () => {
  assert.equal((await call(new Request('https://worker.test/api/admin/google/verify', {
    method: 'POST', body: JSON.stringify({ idToken: signedToken() })
  }))).status, 401);
  assert.equal((await call(new Request('https://worker.test/api/admin/google/verify', {
    method: 'POST', body: JSON.stringify({ idToken: signedToken(), nonce: 'Y'.repeat(43) })
  }))).status, 401);
});
