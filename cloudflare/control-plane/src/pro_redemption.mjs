import {
  redeemMessage,
  ownershipMessage,
  statusMessage,
  publicKeyThumbprint,
  verifyDeviceSignature
} from './device_proof.mjs';

/**
 * Staging Pro activation.
 *
 * A code is a one-time bearer secret. Later status calls additionally
 * require proof of the installation's P-256 private key.
 *
 * This is NOT Google Play purchase verification.
 */

const headers = {
  'content-type': 'application/json; charset=utf-8',
  'cache-control': 'no-store',
  'x-content-type-options': 'nosniff'
};

const reply = (body, status = 200) =>
  new Response(JSON.stringify(body), { status, headers });

const fail = (error, status) =>
  reply({ ok: false, error }, status);

const hash = async value => {
  const bytes = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest('SHA-256', bytes);

  return [...new Uint8Array(digest)]
    .map(byte => byte.toString(16).padStart(2, '0'))
    .join('');
};

const randomNonce = () =>
  btoa(String.fromCharCode(
    ...crypto.getRandomValues(new Uint8Array(32))
  ))
    .replaceAll('+', '-')
    .replaceAll('/', '_')
    .replaceAll('=', '');

async function parseBody(request) {
  const length = Number(request.headers.get('content-length'));

  if (Number.isFinite(length) && length > 4096) {
    throw Error('invalid_request');
  }

  const text = await request.text();

  if (text.length > 4096) {
    throw Error('invalid_request');
  }

  const body = JSON.parse(text);

  if (
    body === null ||
    typeof body !== 'object' ||
    Array.isArray(body)
  ) {
    throw Error('invalid_request');
  }

  return body;
}

const uuid = value =>
  typeof value === 'string' &&
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(value);

const nonceFormat = value =>
  typeof value === 'string' &&
  /^[A-Za-z0-9_-]{22,86}$/.test(value);

// An INSERT with a NULL value in an explicit NOT NULL column forces
// D1.batch() to ROLLBACK on a failed conditional operation.
function receiptGuard(env, { redemptionId, codeId, installationId, now }) {
  return env.DB.prepare(
    `INSERT INTO pro_redemption_receipts
       (redemption_id, activation_code_id, installation_id, created_at)
     VALUES (
       (SELECT CASE WHEN
         EXISTS (SELECT 1 FROM pro_activation_codes c
                 WHERE c.id = ? AND c.state = 'redeemed'
                   AND c.redemption_id = ?)
         AND EXISTS (SELECT 1 FROM pro_admin_grants g
                 WHERE g.installation_id = ? AND g.activation_code_id = ?
                   AND g.state = 'active')
         THEN ? ELSE NULL END), ?, ?, ?)`
  ).bind(codeId, redemptionId, installationId, codeId,
         redemptionId, codeId, installationId, now);
}

async function redeem(env, body) {
  const { code, publicKey, nonce, signature } = body;

  let message;

  try {
    message = redeemMessage({ code, publicKey, nonce });
  } catch {
    return fail('invalid_device_proof', 400);
  }

  if (
    !(await verifyDeviceSignature({
      publicKey, signature, message
    }))
  ) {
    return fail('invalid_device_proof', 401);
  }

  const codeHash = await hash(code);
  const thumbprint = await publicKeyThumbprint(publicKey);
  const now = Math.floor(Date.now() / 1000);

  try {
    // A prior activation with this key must be recovered, not silently
    // replaced with another installation or a generic storage error.
    const existingKey = await env.DB.prepare(
      `SELECT i.id FROM installations i JOIN pro_installation_keys k
       ON k.installation_id = i.id WHERE i.key_thumbprint = ?
       AND k.public_key_spki = ? AND i.state = 'active'`
    ).bind(thumbprint, publicKey).first();
    if (existingKey?.id) return fail('installation_exists_recover_first', 409);
    const issued = await env.DB.prepare(
      `SELECT id
       FROM pro_activation_codes
       WHERE code_hash = ?
         AND state = 'issued'
         AND expires_at > ?`
    ).bind(codeHash, now).first();

    if (!issued?.id) {
      return fail('code_unavailable', 409);
    }

    const installationId = crypto.randomUUID();
    const redemptionId = crypto.randomUUID();

    /*
     * Only the transaction that changes this exact code to 'redeemed'
     * may create the installation/key/grant rows.
     *
     * All four statements run atomically in D1 batch(). A SQL
     * constraint failure must roll the entire batch back.
     */
    const results = await env.DB.batch([
      env.DB.prepare(
        `UPDATE pro_activation_codes
         SET state = 'redeemed',
             redeemed_at = ?,
             redeemed_by_thumbprint = ?,
             redemption_id = ?,
             updated_at = ?
         WHERE id = ?
           AND state = 'issued'
           AND expires_at > ?`
      ).bind(
        now, thumbprint, redemptionId,
        now, issued.id, now
      ),

      env.DB.prepare(
        `INSERT INTO installations
           (id, key_thumbprint, state, created_at, last_seen_at)
         SELECT ?, ?, 'active', ?, ?
         WHERE EXISTS (
           SELECT 1 FROM pro_activation_codes
           WHERE id = ?
             AND state = 'redeemed'
             AND redemption_id = ?
             AND redeemed_by_thumbprint = ?
         )`
      ).bind(
        installationId, thumbprint, now, now,
        issued.id, redemptionId, thumbprint
      ),

      env.DB.prepare(
        `INSERT INTO pro_installation_keys
           (installation_id, public_key_spki)
         SELECT ?, ?
         WHERE EXISTS (
           SELECT 1 FROM pro_activation_codes
           WHERE id = ?
             AND state = 'redeemed'
             AND redemption_id = ?
             AND redeemed_by_thumbprint = ?
         )`
      ).bind(
        installationId, publicKey,
        issued.id, redemptionId, thumbprint
      ),

      env.DB.prepare(
        `INSERT INTO pro_admin_grants
           (installation_id, activation_code_id,
            state, granted_at, updated_at)
         SELECT ?, ?, 'active', ?, ?
         WHERE EXISTS (
           SELECT 1 FROM pro_activation_codes
           WHERE id = ?
             AND state = 'redeemed'
             AND redemption_id = ?
             AND redeemed_by_thumbprint = ?
         )`
      ).bind(
        installationId, issued.id, now, now,
        issued.id, redemptionId, thumbprint
      ),
      receiptGuard(env, {
        redemptionId, codeId: issued.id, installationId, now
      })
    ]);

    if (
      !Array.isArray(results) ||
      results.length !== 5 ||
      results.some(item => item?.meta?.changes !== 1)
    ) {
      return fail('code_unavailable', 409);
    }

    return reply({
      ok: true,
      installationId,
      active: true,
      source: 'admin_code'
    }, 201);
  } catch {
    return fail('pro_storage_unavailable', 503);
  }
}

async function challenge(env, body) {
  const { installationId, requestNonce } = body;

  if (!uuid(installationId) || !nonceFormat(requestNonce)) {
    return fail('invalid_request', 400);
  }

  const now = Math.floor(Date.now() / 1000);

  try {
    const record = await env.DB.prepare(
      `SELECT g.installation_id
       FROM pro_admin_grants g
       JOIN installations i
         ON i.id = g.installation_id
       WHERE g.installation_id = ?
         AND g.state = 'active'
         AND i.state = 'active'`
    ).bind(installationId).first();

    if (!record) {
      return fail('installation_unavailable', 404);
    }

    const challengeId = crypto.randomUUID();
    const nonce = randomNonce();
    const nonceHash = await hash(nonce);
    const requestNonceHash = await hash(requestNonce);
    const expiresAt = now + 120;

    const written = await env.DB.prepare(
      `INSERT INTO installation_challenges
         (id, installation_id, nonce_hash,
          request_nonce_hash, created_at, expires_at)
       VALUES (?, ?, ?, ?, ?, ?)`
    ).bind(
      challengeId,
      installationId,
      nonceHash,
      requestNonceHash,
      now,
      expiresAt
    ).run();

    if (written?.meta?.changes !== 1) {
      return fail('pro_storage_unavailable', 503);
    }

    return reply({
      ok: true,
      challengeId,
      nonce,
      expiresAt
    });
  } catch {
    return fail('pro_storage_unavailable', 503);
  }
}

/** Look up a public key; this response is not an entitlement. */
async function ownershipChallenge(env, body) {
  const { publicKey, requestNonce } = body;
  if (typeof publicKey !== 'string' || !/^[A-Za-z0-9_-]{80,300}$/.test(publicKey) ||
      !nonceFormat(requestNonce)) return fail('invalid_request', 400);
  try {
    const thumbprint = await publicKeyThumbprint(publicKey);
    const installation = await env.DB.prepare(
      `SELECT i.id FROM installations i JOIN pro_installation_keys k
       ON k.installation_id = i.id WHERE i.key_thumbprint = ?
         AND i.state = 'active' AND k.public_key_spki = ?`
    ).bind(thumbprint, publicKey).first();
    if (!installation?.id) return fail('installation_unavailable', 404);
    const now = Math.floor(Date.now() / 1000);
    // Small per-installation staging cap; production needs edge rate limiting.
    const count = await env.DB.prepare(
      `SELECT COUNT(*) AS pending FROM installation_challenges
       WHERE installation_id = ? AND expires_at > ? AND consumed_at IS NULL`
    ).bind(installation.id, now).first();
    if (Number(count?.pending) >= 12) return fail('challenge_rate_limited', 429);
    const challengeId = crypto.randomUUID();
    const nonce = randomNonce();
    const saved = await env.DB.prepare(
      `INSERT INTO installation_challenges
       (id, installation_id, nonce_hash, request_nonce_hash, created_at, expires_at)
       VALUES (?, ?, ?, ?, ?, ?)`
    ).bind(challengeId, installation.id, await hash(nonce),
      await hash(requestNonce), now, now + 120).run();
    if (saved?.meta?.changes !== 1) return fail('pro_storage_unavailable', 503);
    return reply({ ok: true, installationId: installation.id,
      challengeId, nonce, expiresAt: now + 120 });
  } catch { return fail('pro_storage_unavailable', 503); }
}

/** Consume a short-lived challenge after verifying the *stored* key. */
async function ownership(env, body, operation) {
  const { installationId, challengeId, nonce, publicKey, signature, code } = body;
  let message;
  try {
    message = ownershipMessage({ operation, installationId,
      challengeId, nonce, publicKey, code });
  } catch { return fail('invalid_device_proof', 400); }
  if (!uuid(installationId)) return fail('invalid_device_proof', 400);
  const now = Math.floor(Date.now() / 1000);
  try {
    const thumbprint = await publicKeyThumbprint(publicKey);
    const registered = await env.DB.prepare(
      `SELECT i.id FROM installations i JOIN pro_installation_keys k
       ON k.installation_id = i.id WHERE i.id = ? AND i.key_thumbprint = ?
       AND k.public_key_spki = ? AND i.state = 'active'`
    ).bind(installationId, thumbprint, publicKey).first();
    if (!registered?.id) return fail('installation_unavailable', 404);
    if (!await verifyDeviceSignature({ publicKey, signature, message }))
      return fail('invalid_device_proof', 401);
    const nonceHash = await hash(nonce);
    if (operation === 'recover') {
      const ownershipId = crypto.randomUUID();
      const used = await env.DB.prepare(
        `UPDATE installation_challenges SET consumed_at = ?, consumption_id = ?
         WHERE id = ? AND installation_id = ? AND nonce_hash = ?
         AND consumed_at IS NULL AND expires_at > ?`
      ).bind(now, ownershipId, challengeId, installationId, nonceHash, now).run();
      if (used?.meta?.changes !== 1) return fail('challenge_unavailable', 409);
      const grant = await env.DB.prepare(
        `SELECT state FROM pro_admin_grants WHERE installation_id = ?`
      ).bind(installationId).first();
      // A revoked grant can be recovered as an ID, but NEVER as Pro.
      if (!grant) return fail('installation_unavailable', 404);
      return reply({ ok: true, installationId,
        active: grant.state === 'active', source: 'admin_code' });
    }
    if (typeof env.DB.batch !== 'function') return fail('pro_storage_unavailable', 503);
    const codeHash = await hash(code);
    const issued = await env.DB.prepare(
      `SELECT id FROM pro_activation_codes WHERE code_hash = ?
       AND state = 'issued' AND expires_at > ?`
    ).bind(codeHash, now).first();
    if (!issued?.id) return fail('code_unavailable', 409);
    const redemptionId = crypto.randomUUID();
    const results = await env.DB.batch([
      env.DB.prepare(
        `UPDATE installation_challenges SET consumed_at = ?, consumption_id = ?
         WHERE id = ? AND installation_id = ? AND nonce_hash = ?
         AND consumed_at IS NULL AND expires_at > ?
         AND EXISTS (SELECT 1 FROM pro_admin_grants
                     WHERE installation_id = ? AND state = 'revoked')`
      ).bind(now, redemptionId, challengeId, installationId, nonceHash, now, installationId),
      env.DB.prepare(
        `UPDATE pro_activation_codes SET state = 'redeemed', redeemed_at = ?,
         redeemed_by_thumbprint = ?, redemption_id = ?, updated_at = ?
         WHERE id = ? AND state = 'issued' AND expires_at > ?
         AND EXISTS (SELECT 1 FROM installation_challenges
                     WHERE id = ? AND installation_id = ?
                       AND nonce_hash = ? AND consumption_id = ?)`
      ).bind(now, thumbprint, redemptionId, now, issued.id, now,
        challengeId, installationId, nonceHash, redemptionId),
      env.DB.prepare(
        `UPDATE pro_admin_grants SET activation_code_id = ?, state = 'active',
          granted_at = ?, updated_at = ?, revoked_at = NULL,
          revoked_by_hash = NULL
         WHERE installation_id = ? AND state = 'revoked'
         AND EXISTS (SELECT 1 FROM pro_activation_codes
                     WHERE id = ? AND state = 'redeemed'
                       AND redemption_id = ? AND redeemed_by_thumbprint = ?)`
      ).bind(issued.id, now, now, installationId,
        issued.id, redemptionId, thumbprint),
      receiptGuard(env, { redemptionId, codeId: issued.id, installationId, now })
    ]);
    if (!Array.isArray(results) || results.length !== 4) {
      return fail('pro_storage_unavailable', 503);
    }

    /*
     * D1 statement metadata is not the entitlement authority.
     * In particular, the 0005 archive trigger may make an otherwise
     * successful reactivation unsuitable for an exact
     * meta.changes === 1 assertion.
     *
     * The receiptGuard above remains the atomic rollback guard.
     * After a successful batch, prove the committed entitlement by
     * reading the exact code + current grant + receipt transaction.
     */
    const committed = await env.DB.prepare(
      `SELECT c.id AS code_id,
              g.installation_id,
              g.activation_code_id,
              g.state AS grant_state,
              r.redemption_id AS receipt_redemption_id
       FROM pro_activation_codes c
       JOIN pro_admin_grants g
         ON g.activation_code_id = c.id
       JOIN pro_redemption_receipts r
         ON r.activation_code_id = c.id
        AND r.installation_id = g.installation_id
       WHERE c.id = ?
         AND c.state = 'redeemed'
         AND c.redemption_id = ?
         AND c.redeemed_by_thumbprint = ?
         AND g.installation_id = ?
         AND g.state = 'active'
         AND r.redemption_id = ?`
    ).bind(
      issued.id,
      redemptionId,
      thumbprint,
      installationId,
      redemptionId
    ).first();

    if (
      committed?.code_id !== issued.id ||
      committed?.installation_id !== installationId ||
      committed?.activation_code_id !== issued.id ||
      committed?.grant_state !== 'active' ||
      committed?.receipt_redemption_id !== redemptionId
    ) {
      return fail('code_unavailable', 409);
    }

    return reply({ ok: true, installationId,
      active: true, source: 'admin_code' }, 201);
  } catch { return fail('pro_storage_unavailable', 503); }
}

async function status(env, body) {
  const {
    installationId,
    challengeId,
    nonce,
    signature
  } = body;

  let message;

  try {
    message = statusMessage({
      installationId,
      challengeId,
      nonce
    });
  } catch {
    return fail('invalid_device_proof', 400);
  }

  const now = Math.floor(Date.now() / 1000);

  try {
    const challengeRecord = await env.DB.prepare(
      `SELECT nonce_hash
       FROM installation_challenges
       WHERE id = ?
         AND installation_id = ?
         AND consumed_at IS NULL
         AND expires_at > ?`
    ).bind(
      challengeId, installationId, now
    ).first();

    if (
      !challengeRecord ||
      challengeRecord.nonce_hash !== await hash(nonce)
    ) {
      return fail('challenge_unavailable', 409);
    }

    const key = await env.DB.prepare(
      `SELECT k.public_key_spki, i.key_thumbprint
       FROM pro_installation_keys k
       JOIN installations i
         ON i.id = k.installation_id
       WHERE k.installation_id = ?
         AND i.state = 'active'`
    ).bind(installationId).first();

    if (
      !key?.public_key_spki ||
      key.key_thumbprint !==
        await publicKeyThumbprint(key.public_key_spki)
    ) {
      return fail('installation_unavailable', 404);
    }

    if (
      !(await verifyDeviceSignature({
        publicKey: key.public_key_spki,
        signature,
        message
      }))
    ) {
      return fail('invalid_device_proof', 401);
    }

    const used = await env.DB.prepare(
      `UPDATE installation_challenges
       SET consumed_at = ?
       WHERE id = ?
         AND installation_id = ?
         AND nonce_hash = ?
         AND consumed_at IS NULL
         AND expires_at > ?`
    ).bind(
      now, challengeId, installationId,
      challengeRecord.nonce_hash, now
    ).run();

    if (used?.meta?.changes !== 1) {
      return fail('challenge_unavailable', 409);
    }

    // Entitlement is checked AFTER consuming the challenge.
    // An admin revocation must immediately affect later status calls.
    const grant = await env.DB.prepare(
      `SELECT g.state
       FROM pro_admin_grants g
       JOIN installations i
         ON i.id = g.installation_id
       WHERE g.installation_id = ?
         AND g.state = 'active'
         AND i.state = 'active'`
    ).bind(installationId).first();

    if (!grant) {
      return fail('installation_unavailable', 404);
    }

    return reply({
      ok: true,
      active: true,
      source: 'admin_code',
      entitlementKind: 'admin_grant'
    });
  } catch {
    return fail('pro_storage_unavailable', 503);
  }
}

export async function handleProRedemption(
  request,
  env,
  pathname
) {
  if (request.method !== 'POST') {
    return fail('method_not_allowed', 405);
  }

  if (typeof env?.DB?.prepare !== 'function') {
    return fail('pro_storage_unavailable', 503);
  }

  let body;

  try {
    body = await parseBody(request);
  } catch {
    return fail('invalid_request', 400);
  }

  if (pathname === '/api/pro/code/redeem') {
    if (typeof env.DB.batch !== 'function') {
      return fail('pro_storage_unavailable', 503);
    }

    return redeem(env, body);
  }

  if (pathname === '/api/pro/code/challenge') {
    return challenge(env, body);
  }

  if (pathname === '/api/pro/code/ownership-challenge') {
    return ownershipChallenge(env, body);
  }
  if (pathname === '/api/pro/code/recover' || pathname === '/api/pro/code/reactivate') {
    return ownership(env, body,
      pathname.endsWith('/recover') ? 'recover' : 'reactivate');
  }

  if (pathname === '/api/pro/code/status') {
    return status(env, body);
  }

  return fail('not_found', 404);
}
