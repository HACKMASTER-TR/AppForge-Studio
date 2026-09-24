import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { handleAdminProCodes } from '../src/admin_pro_codes.mjs';

const adminHash = 'a'.repeat(64);
const rows = new Map();

const db = {
  prepare(sql) {
    return {
      // D1 SELECT uses prepare(...).all() without bind().
      async all() {
        return this.bind().all();
      },
      bind(...args) {
        return {
          async run() {
            if (sql.includes('INSERT INTO pro_activation_codes')) {
              const [id, codeHash, actor, now, expiresAt] = args;
              assert.equal(actor, adminHash);
              assert.equal(codeHash.length, 64);
              assert.equal(rows.has(id), false);

              rows.set(id, {
                id,
                code_hash: codeHash,
                state: 'issued',
                created_at: now,
                expires_at: expiresAt,
                redeemed_at: null,
                revoked_at: null
              });

              return { meta: { changes: 1 } };
            }

            if (sql.includes('UPDATE pro_activation_codes')) {
              const [, now, , id] = args;
              const record = rows.get(id);

              if (!record || record.state !== 'issued') {
                return { meta: { changes: 0 } };
              }

              record.state = 'revoked';
              record.revoked_at = now;
              return { meta: { changes: 1 } };
            }

            throw Error('unexpected test SQL');
          },

          async all() {
            assert.match(sql, /SELECT id, state/);
            return {
              results: [...rows.values()].map(
                ({
                  id, state, created_at, expires_at,
                  redeemed_at, revoked_at
                }) => ({
                  id, state, created_at, expires_at,
                  redeemed_at, revoked_at
                })
              )
            };
          }
        };
      }
    };
  }
};

const call = (path, method = 'GET', actor = adminHash, env = { DB: db }) =>
  handleAdminProCodes(
    new Request('https://worker.test' + path, { method }),
    env,
    actor,
    path
  );

test('invalid admin identity cannot manage codes', async () => {
  const result = await call('/api/admin/pro-codes', 'POST', '');
  assert.equal(result.status, 403);
  assert.equal(rows.size, 0);
});

test('missing D1 fails closed', async () => {
  const result = await call(
    '/api/admin/pro-codes', 'POST', adminHash, {}
  );
  assert.equal(result.status, 503);
});

test('issue, list and revoke without storing plaintext codes', async () => {
  const created = await call('/api/admin/pro-codes', 'POST');
  assert.equal(created.status, 201);

  const issued = await created.json();
  assert.match(issued.code, /^AFPRO-[A-Za-z0-9_-]{43}$/);

  const record = rows.get(issued.id);
  assert.ok(record);

  const expectedHash = createHash('sha256')
    .update(issued.code)
    .digest('hex');

  assert.equal(record.code_hash, expectedHash);
  assert.equal(JSON.stringify(record).includes(issued.code), false);

  const listed = await call('/api/admin/pro-codes');
  assert.equal(listed.status, 200);
  const list = await listed.json();

  assert.equal(list.codes.some(x => x.id === issued.id), true);
  assert.equal(JSON.stringify(list).includes(issued.code), false);
  assert.equal(JSON.stringify(list).includes(expectedHash), false);

  const path = `/api/admin/pro-codes/${issued.id}/revoke`;
  const revoked = await call(path, 'POST');

  assert.equal(revoked.status, 200);
  assert.equal(rows.get(issued.id).state, 'revoked');

  const again = await call(path, 'POST');
  assert.equal(again.status, 409);
});

test('unknown route and wrong method fail closed', async () => {
  assert.equal(
    (await call('/api/admin/pro-codes', 'DELETE')).status,
    405
  );
  assert.equal(
    (await call('/api/admin/pro-codes/unknown/revoke', 'POST')).status,
    404
  );
});
