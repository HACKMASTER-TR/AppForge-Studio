import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import worker from '../src/index.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..');
const source = fs.readFileSync(path.join(root, 'src/index.mjs'), 'utf8');
const sql = fs.readFileSync(path.join(root, 'migrations/0001_accountless_control_plane.sql'), 'utf8');
const req = (pathname, init) => new Request(`https://appforge-control-plane.test${pathname}`, init);
const db = result => ({prepare(statement) {
  assert.equal(statement, 'SELECT 1 AS ok');
  return { async first() { if (result instanceof Error) throw result; return result; } };
}});

// These are staging contract tests, NOT Google Play/Google admin acceptance.
test('health checks the live DB binding', async () => {
  const result = await worker.fetch(req('/health'), {DB: db({ok: 1})});
  assert.equal(result.status, 200);
  assert.equal((await result.json()).database, 'reachable');
});
test('health fails closed when DB missing', async () => {
  assert.equal((await worker.fetch(req('/health'), {})).status, 503);
});
test('health fails closed when DB query throws', async () => {
  assert.equal((await worker.fetch(req('/health'), {DB: db(new Error('sensitive SQL detail'))})).status, 503);
});
test('staging policy does not force updates or claim another Play version', async () => {
  const response = await worker.fetch(req('/api/client/android/policy?versionCode=529'), {});
  assert.equal(response.status, 200);
  const data = await response.json();
  assert.deepEqual([data.state, data.latestVersionCode, data.minSupportedVersionCode], ['NORMAL', 529, 1]);
});
test('invalid or missing version does not become a forced policy', async () => {
  for (const version of ['', '0', '-1', 'abc', '1000001', '1.2']) {
    const response = await worker.fetch(req('/api/client/android/policy?versionCode='+version), {});
    assert.equal(response.status, 400, version);
  }
});
test('retired account routes do not accept registrations, logins or fake bearer', async () => {
  for (const pathname of ['/api/auth/register','/api/auth/login','/api/auth/me','/api/auth/forgot-password','/api/auth/api-tokens']) {
    const response = await worker.fetch(req(pathname, {
      method: 'POST', headers: { Authorization: 'Bearer faked', 'X-AppForge-Device-ID': 'spoofed' }, body: '{}'
    }), {});
    assert.equal(response.status, 410, pathname);
  }
});
test('admin is never granted by email, device ID or obsolete bearer', async () => {
  const response = await worker.fetch(req('/api/admin/system-status', {headers: {
    'X-AppForge-Device-ID': 'anyone-can-spoof', 'X-AppForge-Email': 'admin@example.test',
    Authorization: 'Bearer old-token'
  }}), {DB:db({ok:1})});
  assert.equal(response.status, 503);
  assert.equal((await response.json()).error, 'admin_identity_not_configured');
});
test('Pro is neither granted nor silently denied without verification', async () => {
  for (const p of ['/api/pro/status','/api/pro/activate']) {
    const response = await worker.fetch(req(p, {method: 'POST', body:'{"purchaseToken":"fake"}', headers: {
      'Authorization':'Bearer arbitrary', 'X-AppForge-Device-ID':'arbitrary'
    }}), {});
    assert.equal(response.status, 503);
    const text = await response.text();
    assert.doesNotMatch(text, /"active"\s*:\s*(true|false)/);
  }
});
test('installation bootstrap and build APIs are not accidentally enabled', async () => {
  for (const p of ['/api/device/bootstrap','/api/builds','/api/projects/quota']) {
    assert.equal((await worker.fetch(req(p, {method:'POST',body:'{}'}), {})).status, 503);
  }
});
test('unrelated URL is 404 and never leaks secrets', async () => {
  const response = await worker.fetch(req('/not-an-api'), {});
  assert.equal(response.status, 404);
  assert.doesNotMatch(await response.text(), /password|token|sensitive SQL detail/);
});
test('account/password implementation is removed from staged Worker and schema', () => {
  assert.doesNotMatch(sql, /\bCREATE TABLE\s+(?:IF NOT EXISTS\s+)?(?:accounts|sessions|email_tokens|entitlements)\s*\(/i);
  assert.doesNotMatch(source, /hashPassword|verifyPassword|jwt\.sign|Google Play.*verified/i);
  assert.match(sql, /CREATE TABLE IF NOT EXISTS play_purchase_records/);
  assert.match(sql, /CREATE TABLE IF NOT EXISTS admin_identities/);
});

// One lifetime product only. This is a *staging contract*, not live Play proof.
test('schema permits only the proposed single lifetime in-app product', () => {
  assert.match(sql, /product_id\s+TEXT\s+NOT NULL\s+CHECK\(product_id = 'appforge_pro_lifetime'\)/);
  assert.doesNotMatch(sql, /subscription_state|subscription_period|expires_at|renewal_at|monthly_price/i);
  assert.match(sql, /acknowledged_at INTEGER/);
  assert.match(sql, /revoked_at INTEGER/);
});
test('deprecated monthly and add-on billing endpoints cannot sell', async () => {
  for (const p of ['/api/pro/monthly','/api/pro/subscribe','/api/quota/addons/redeem']) {
    const response = await worker.fetch(req(p,{method:'POST',body:'{}'}),{});
    assert.equal(response.status,410,p);
    assert.equal((await response.json()).error,'legacy_billing_product_retired');
  }
});
test('lifetime purchase and status remain unavailable before real Play verification', async () => {
  for (const p of ['/api/pro/activate','/api/pro/status']) {
    const response = await worker.fetch(req(p,{method:'POST',body:'{"productId":"appforge_pro_lifetime","purchaseToken":"fake"}'}),{});
    assert.equal(response.status,503,p);
    assert.doesNotMatch(await response.text(), /"active"\s*:\s*true/);
  }
});
