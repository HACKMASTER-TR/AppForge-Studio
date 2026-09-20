/**
 * Staging admin-issued Pro codes.
 * Caller MUST first verify Google OIDC and active D1 admin identity.
 * Issuance does not grant Pro. Redemption is a separate, gated stage.
 */

const headers = {
  'content-type': 'application/json; charset=utf-8',
  'cache-control': 'no-store',
  'x-content-type-options': 'nosniff'
};

const reply = (data, status = 200) =>
  new Response(JSON.stringify(data), { status, headers });

const fail = (error, status) =>
  reply({ ok: false, error }, status);

const bytesToBase64Url = bytes =>
  btoa(String.fromCharCode(...bytes))
    .replaceAll('+', '-')
    .replaceAll('/', '_')
    .replaceAll('=', '');

const sha256 = async value => {
  const digest = await crypto.subtle.digest(
    'SHA-256',
    new TextEncoder().encode(value)
  );
  return [...new Uint8Array(digest)]
    .map(byte => byte.toString(16).padStart(2, '0'))
    .join('');
};

export async function handleAdminProCodes(
  request,
  env,
  verifiedAdminHash,
  pathname
) {
  if (
    typeof verifiedAdminHash !== 'string' ||
    !/^[a-f0-9]{64}$/.test(verifiedAdminHash)
  ) {
    return fail('admin_forbidden', 403);
  }

  if (typeof env?.DB?.prepare !== 'function') {
    return fail('pro_code_storage_unavailable', 503);
  }


  // Admin-granted Pro entitlements.
  // The caller was authenticated by index.mjs.

  if (pathname === '/api/admin/pro-grants') {
    if (request.method !== 'GET') {
      return fail('method_not_allowed', 405);
    }

    try {
      const result = await env.DB.prepare(
        `SELECT installation_id, activation_code_id, state, granted_at, revoked_at
         FROM (
           SELECT installation_id, activation_code_id, state,
                  granted_at, revoked_at FROM pro_admin_grants
           UNION ALL
           SELECT installation_id, activation_code_id, 'revoked' AS state,
                  granted_at, revoked_at FROM pro_admin_grant_history
         ) ORDER BY granted_at DESC LIMIT 50`
      ).all();

      return reply({
        ok: true,
        grants: result?.results ?? []
      });
    } catch {
      return fail('pro_grant_storage_unavailable', 503);
    }
  }

  const grantRevokeMatch = pathname.match(
    /^\/api\/admin\/pro-grants\/([0-9a-f-]{36})\/revoke$/
  );

  if (grantRevokeMatch) {
    if (request.method !== 'POST') {
      return fail('method_not_allowed', 405);
    }

    try {
      const now = Math.floor(Date.now() / 1000);

      const changed = await env.DB.prepare(
        `UPDATE pro_admin_grants
         SET state = 'revoked',
             revoked_at = ?,
             revoked_by_hash = ?,
             updated_at = ?
         WHERE installation_id = ?
           AND state = 'active'`
      ).bind(
        now,
        verifiedAdminHash,
        now,
        grantRevokeMatch[1]
      ).run();

      if (changed?.meta?.changes !== 1) {
        return fail('grant_not_active', 409);
      }

      return reply({
        ok: true,
        installationId: grantRevokeMatch[1],
        state: 'revoked',
        revokedAt: now
      });
    } catch {
      return fail('pro_grant_storage_unavailable', 503);
    }
  }

  if (pathname.startsWith('/api/admin/pro-grants/')) {
    return fail('not_found', 404);
  }

  if (pathname === '/api/admin/pro-codes') {
    if (request.method === 'POST') {
      try {
        // One code: 256 bits of random secret.
        // Valid for redemption for seven days; not a 7-day Pro plan.
        const secret = 'AFPRO-' + bytesToBase64Url(
          crypto.getRandomValues(new Uint8Array(32))
        );

        const hash = await sha256(secret);
        const id = crypto.randomUUID();
        const now = Math.floor(Date.now() / 1000);
        const expiresAt = now + 7 * 24 * 60 * 60;

        const saved = await env.DB.prepare(
          `INSERT INTO pro_activation_codes
           (id, code_hash, state, created_by_hash,
            created_at, expires_at, updated_at)
           VALUES (?, ?, 'issued', ?, ?, ?, ?)`
        ).bind(
          id, hash, verifiedAdminHash, now, expiresAt, now
        ).run();

        if (saved?.meta?.changes !== 1) {
          return fail('pro_code_storage_unavailable', 503);
        }

        // Plaintext is returned ONCE. No plaintext in D1 or audit logs.
        return reply({
          ok: true,
          id,
          code: secret,
          state: 'issued',
          createdAt: now,
          expiresAt
        }, 201);
      } catch {
        return fail('pro_code_storage_unavailable', 503);
      }
    }

    if (request.method === 'GET') {
      try {
        const result = await env.DB.prepare(
          `SELECT id, state, created_at, expires_at,
                  redeemed_at, revoked_at
           FROM pro_activation_codes
           ORDER BY created_at DESC
           LIMIT 50`
        ).all();

        return reply({
          ok: true,
          codes: result?.results ?? []
        });
      } catch {
        return fail('pro_code_storage_unavailable', 503);
      }
    }

    return fail('method_not_allowed', 405);
  }

  const match = pathname.match(
    /^\/api\/admin\/pro-codes\/([0-9a-f-]{36})\/revoke$/
  );

  if (!match) {
    return fail('not_found', 404);
  }

  if (request.method !== 'POST') {
    return fail('method_not_allowed', 405);
  }

  try {
    const now = Math.floor(Date.now() / 1000);

    const changed = await env.DB.prepare(
      `UPDATE pro_activation_codes
       SET state = 'revoked',
           revoked_by_hash = ?,
           revoked_at = ?,
           updated_at = ?
       WHERE id = ? AND state = 'issued'`
    ).bind(
      verifiedAdminHash, now, now, match[1]
    ).run();

    if (changed?.meta?.changes !== 1) {
      return fail('code_not_issued', 409);
    }

    return reply({
      ok: true,
      id: match[1],
      state: 'revoked'
    });
  } catch {
    return fail('pro_code_storage_unavailable', 503);
  }
}
