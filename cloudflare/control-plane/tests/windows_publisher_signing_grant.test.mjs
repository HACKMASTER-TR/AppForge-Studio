import test from 'node:test';
import assert from 'node:assert/strict';
import {
  generateKeyPairSync,
  sign,
  createHash
} from 'node:crypto';
import { handleRequest } from '../src/index.mjs';

const WEB_ID =
  '123456-web.apps.googleusercontent.com';

const ANDROID_ID =
  '123456-android.apps.googleusercontent.com';

const {
  publicKey,
  privateKey
} =
  generateKeyPairSync(
    'rsa',
    {
      modulusLength: 2048
    }
  );

const jwk = {
  ...publicKey.export({
    format: 'jwk'
  }),
  kid: 'test-key',
  use: 'sig',
  alg: 'RS256'
};

const keys =
  async () => ({
    ok: true,
    json: async () => ({
      keys: [jwk]
    })
  });

const b64 =
  value =>
    Buffer.from(
      typeof value === 'string'
        ? value
        : JSON.stringify(value)
    ).toString('base64url');

function signedToken() {
  const now =
    Math.floor(
      Date.now() / 1000
    );

  const a =
    b64({
      alg: 'RS256',
      kid: 'test-key',
      typ: 'JWT'
    });

  const b =
    b64({
      iss:
        'https://accounts.google.com',
      aud:
        WEB_ID,
      azp:
        ANDROID_ID,
      sub:
        'publisher-owner-sub',
      iat:
        now,
      exp:
        now + 1200
    });

  const signature =
    sign(
      'RSA-SHA256',
      Buffer.from(
        `${a}.${b}`
      ),
      privateKey
    ).toString(
      'base64url'
    );

  return `${a}.${b}.${signature}`;
}

const adminHash =
  createHash('sha256')
    .update(
      'publisher-owner-sub'
    )
    .digest('hex');

function database() {
  const audit =
    new Map();

  return {
    prepare(query) {

      if (
        query.includes(
          'FROM admin_identities'
        )
      ) {
        return {
          bind(value) {
            assert.equal(
              value,
              adminHash
            );

            return {
              async first() {
                return {
                  state: 'active'
                };
              }
            };
          }
        };
      }

      if (
        query.includes(
          'INSERT INTO audit_events'
        )
      ) {
        return {
          bind(
            id,
            eventKind,
            actorReferenceHash,
            createdAt
          ) {
            return {
              async run() {
                if (
                  audit.has(id)
                ) {
                  throw Error(
                    'duplicate'
                  );
                }

                audit.set(
                  id,
                  {
                    id,
                    eventKind,
                    actorReferenceHash,
                    createdAt
                  }
                );

                return {
                  meta: {
                    changes: 1
                  }
                };
              }
            };
          }
        };
      }

      if (
        query.includes(
          'SELECT created_at'
        )
      ) {
        return {
          bind(
            id,
            eventKind,
            actorReferenceHash
          ) {
            return {
              async first() {
                const row =
                  audit.get(id);

                if (
                  !row ||
                  row.eventKind !==
                    eventKind ||
                  row.actorReferenceHash !==
                    actorReferenceHash
                ) {
                  return null;
                }

                return {
                  created_at:
                    row.createdAt
                };
              }
            };
          }
        };
      }

      if (
        query.includes(
          'SELECT id'
        )
      ) {
        return {
          bind(
            id,
            eventKind
          ) {
            return {
              async first() {
                const row =
                  audit.get(id);

                if (
                  !row ||
                  row.eventKind !==
                    eventKind
                ) {
                  return null;
                }

                return {
                  id: row.id
                };
              }
            };
          }
        };
      }

      throw Error(
        `unexpected SQL ${query}`
      );
    }
  };
}

const environment =
  db => ({
    GOOGLE_WEB_CLIENT_ID:
      WEB_ID,

    GOOGLE_ANDROID_CLIENT_ID:
      ANDROID_ID,

    DB:
      db
  });

const baseRequest = {
  purpose:
    'windows-publisher-signing-v1',

  buildId:
    'local-1234567890abcdef',

  artifactSha256:
    'a'.repeat(64),

  requestNonce:
    'N'.repeat(43)
};

async function call(
  db,
  pathname,
  body,
  authenticated = true
) {
  const headers = {
    'content-type':
      'application/json'
  };

  if (authenticated) {
    headers.authorization =
      `Bearer ${signedToken()}`;
  }

  return handleRequest(
    new Request(
      `https://worker.test${pathname}`,
      {
        method: 'POST',
        headers,
        body:
          JSON.stringify(body)
      }
    ),
    environment(db),
    {
      fetchKeys:
        keys
    }
  );
}

test(
  'publisher grant is admin verified artifact bound and one time',
  async () => {
    const db =
      database();

    const issued =
      await call(
        db,
        '/api/admin/windows-signing/grant',
        baseRequest
      );

    assert.equal(
      issued.status,
      201
    );

    const grant =
      await issued.json();

    assert.equal(
      grant.ok,
      true
    );

    assert.equal(
      grant.purpose,
      baseRequest.purpose
    );

    assert.equal(
      grant.buildId,
      baseRequest.buildId
    );

    assert.equal(
      grant.artifactSha256,
      baseRequest.artifactSha256
    );

    assert.equal(
      grant.requestNonce,
      baseRequest.requestNonce
    );

    assert.equal(
      grant.expiresAt -
        grant.issuedAt,
      120
    );

    const consumed =
      await call(
        db,
        '/api/admin/windows-signing/consume',
        grant
      );

    assert.equal(
      consumed.status,
      200
    );

    assert.equal(
      (await consumed.json()).consumed,
      true
    );

    const replay =
      await call(
        db,
        '/api/admin/windows-signing/consume',
        grant
      );

    assert.equal(
      replay.status,
      409
    );

    assert.equal(
      (await replay.json()).error,
      'signing_grant_replay'
    );
  }
);

test(
  'artifact mutation invalidates issued grant',
  async () => {
    const db =
      database();

    const grant =
      await (
        await call(
          db,
          '/api/admin/windows-signing/grant',
          baseRequest
        )
      ).json();

    grant.artifactSha256 =
      'b'.repeat(64);

    const result =
      await call(
        db,
        '/api/admin/windows-signing/consume',
        grant
      );

    assert.equal(
      result.status,
      403
    );

    assert.equal(
      (await result.json()).error,
      'signing_grant_invalid'
    );
  }
);

test(
  'missing verified admin identity cannot issue grant',
  async () => {
    const result =
      await call(
        database(),
        '/api/admin/windows-signing/grant',
        baseRequest,
        false
      );

    assert.equal(
      result.status,
      401
    );
  }
);
