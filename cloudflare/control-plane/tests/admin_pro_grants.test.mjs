import test from 'node:test';
import assert from 'node:assert/strict';

import {
  handleAdminProCodes
} from '../src/admin_pro_codes.mjs';

const adminHash = 'a'.repeat(64);

const installationId =
  '11111111-1111-4111-8111-111111111111';

const base =
  'https://appforge-control-plane.test';

function mockDb(changes = []) {
  const writes = [];

  return {
    writes,

    prepare(sql) {
      return {
        bind(...args) {
          return {
            async run() {
              writes.push({ sql, args });

              return {
                meta: {
                  changes: changes.shift() ?? 0
                }
              };
            },

            async all() {
              return {
                results: []
              };
            }
          };
        },

        async all() {
          return {
            results: []
          };
        }
      };
    }
  };
}

test(
  'verified administrator can revoke an active grant',
  async () => {
    const db = mockDb([1]);

    const response = await handleAdminProCodes(
      new Request(
        base +
        '/api/admin/pro-grants/' +
        installationId +
        '/revoke',
        { method: 'POST' }
      ),
      { DB: db },
      adminHash,
      '/api/admin/pro-grants/' +
      installationId +
      '/revoke'
    );

    assert.equal(response.status, 200);

    const body = await response.json();

    assert.equal(body.state, 'revoked');

    assert.match(
      db.writes[0].sql,
      /WHERE installation_id = \?\s+AND state = 'active'/
    );

    assert.equal(
      db.writes[0].args[1],
      adminHash
    );

    assert.equal(
      db.writes[0].args[3],
      installationId
    );
  }
);

test(
  'inactive grant cannot be revoked again',
  async () => {
    const db = mockDb([0]);

    const pathname =
      '/api/admin/pro-grants/' +
      installationId +
      '/revoke';

    const response = await handleAdminProCodes(
      new Request(
        base + pathname,
        { method: 'POST' }
      ),
      { DB: db },
      adminHash,
      pathname
    );

    assert.equal(response.status, 409);
  }
);

test(
  'unverified administrator cannot manage grants',
  async () => {
    const pathname =
      '/api/admin/pro-grants';

    const response = await handleAdminProCodes(
      new Request(base + pathname),
      { DB: mockDb() },
      '',
      pathname
    );

    assert.equal(response.status, 403);
  }
);

test(
  'grant listing does not return raw activation codes',
  async () => {
    const pathname =
      '/api/admin/pro-grants';

    const response = await handleAdminProCodes(
      new Request(base + pathname),
      { DB: mockDb() },
      adminHash,
      pathname
    );

    assert.equal(response.status, 200);

    const body = await response.json();

    assert.deepEqual(body.grants, []);
    assert.equal(body.code, undefined);
  }
);
