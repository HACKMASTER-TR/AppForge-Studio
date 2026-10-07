import { verifyGoogleIdToken, subjectSha256, InvalidIdentity, IdentityProviderUnavailable } from './google_oidc.mjs';
import { handleAdminProCodes } from './admin_pro_codes.mjs';
import { handleProRedemption } from './pro_redemption.mjs';
import { handlePlayIntegritySecurityRoute } from './play_integrity_route.mjs';
import { handleAdminAiChat } from './admin_ai_router.mjs';
/**
 * AppForge accountless control-plane staging.
 * No normal-user login, registration, synthetic admin or Pro entitlement.
 * DO NOT switch the production custom domain to this staging Worker.
 */
const json = (value, status = 200) => new Response(JSON.stringify(value), {
  status,
  headers: {
    'content-type': 'application/json; charset=utf-8',
    'cache-control': 'no-store',
    'x-content-type-options': 'nosniff',
    'referrer-policy': 'no-referrer'
  }
});
const fail = (code, status = 503) => json({ ok: false, error: code }, status);
const ready = env => typeof env?.DB?.prepare === 'function';

const WINDOWS_SIGNING_PURPOSE =
  'windows-publisher-signing-v1';

const WINDOWS_SIGNING_GRANT_TTL_SECONDS =
  120;

const windowsSigningGrantRoute = pathname =>
  pathname === '/api/admin/windows-signing/grant' ||
  pathname === '/api/admin/windows-signing/consume';

const validWindowsSigningRequest = body =>
  body &&
  body.purpose === WINDOWS_SIGNING_PURPOSE &&
  typeof body.buildId === 'string' &&
  /^[A-Za-z0-9._:-]{1,160}$/.test(body.buildId) &&
  typeof body.artifactSha256 === 'string' &&
  /^[0-9a-f]{64}$/.test(body.artifactSha256) &&
  typeof body.requestNonce === 'string' &&
  /^[A-Za-z0-9_-]{43,128}$/.test(body.requestNonce);

async function signingSha256(value) {
  const bytes =
    new TextEncoder()
      .encode(value);

  const digest =
    await crypto.subtle.digest(
      'SHA-256',
      bytes
    );

  return [...new Uint8Array(digest)]
    .map(
      byte =>
        byte
          .toString(16)
          .padStart(2, '0')
    )
    .join('');
}

async function windowsSigningBinding(
  verifiedAdminHash,
  grant
) {
  return signingSha256(
    [
      verifiedAdminHash,
      grant.grantId,
      grant.purpose,
      grant.buildId,
      grant.artifactSha256,
      grant.requestNonce,
      String(grant.issuedAt),
      String(grant.expiresAt)
    ].join('\n')
  );
}

async function handleWindowsSigningAuthorization(
  request,
  env,
  verifiedAdminHash,
  pathname
) {
  if (request.method !== 'POST') {
    return fail(
      'method_not_allowed',
      405
    );
  }

  if (
    (Number(
      request.headers.get(
        'content-length'
      )
    ) || 0) > 4096
  ) {
    return fail(
      'invalid_signing_request',
      400
    );
  }

  const raw =
    await request.text();

  if (raw.length > 4096) {
    return fail(
      'invalid_signing_request',
      400
    );
  }

  let body;

  try {
    body =
      JSON.parse(raw);
  } catch {
    return fail(
      'invalid_signing_request',
      400
    );
  }

  if (
    !validWindowsSigningRequest(body)
  ) {
    return fail(
      'invalid_signing_request',
      400
    );
  }

  const now =
    Math.floor(
      Date.now() / 1000
    );

  if (
    pathname ===
      '/api/admin/windows-signing/grant'
  ) {
    const grant = {
      grantId:
        crypto.randomUUID(),

      purpose:
        WINDOWS_SIGNING_PURPOSE,

      buildId:
        body.buildId,

      artifactSha256:
        body.artifactSha256,

      requestNonce:
        body.requestNonce,

      issuedAt:
        now,

      expiresAt:
        now +
        WINDOWS_SIGNING_GRANT_TTL_SECONDS
    };

    const binding =
      await windowsSigningBinding(
        verifiedAdminHash,
        grant
      );

    try {
      const saved =
        await env.DB.prepare(
          `INSERT INTO audit_events
           (id, event_kind, actor_reference_hash, created_at)
           VALUES (?, ?, ?, ?)`
        ).bind(
          `windows-signing-grant-issue:${grant.grantId}`,
          'windows_signing_grant_issued',
          binding,
          grant.issuedAt
        ).run();

      if (
        saved?.meta?.changes !== 1
      ) {
        return fail(
          'signing_grant_storage_unavailable',
          503
        );
      }
    } catch {
      return fail(
        'signing_grant_storage_unavailable',
        503
      );
    }

    return json(
      {
        ok: true,
        ...grant
      },
      201
    );
  }

  if (
    typeof body.grantId !== 'string' ||
    !/^[0-9a-f-]{36}$/.test(
      body.grantId
    ) ||
    !Number.isSafeInteger(
      body.issuedAt
    ) ||
    !Number.isSafeInteger(
      body.expiresAt
    )
  ) {
    return fail(
      'invalid_signing_grant',
      400
    );
  }

  const grant = {
    grantId:
      body.grantId,

    purpose:
      body.purpose,

    buildId:
      body.buildId,

    artifactSha256:
      body.artifactSha256,

    requestNonce:
      body.requestNonce,

    issuedAt:
      body.issuedAt,

    expiresAt:
      body.expiresAt
  };

  if (
    grant.issuedAt > now + 60 ||
    grant.issuedAt < now - 300 ||
    grant.expiresAt <= now ||
    grant.expiresAt -
      grant.issuedAt < 1 ||
    grant.expiresAt -
      grant.issuedAt > 180
  ) {
    return fail(
      'signing_grant_expired',
      409
    );
  }

  const binding =
    await windowsSigningBinding(
      verifiedAdminHash,
      grant
    );

  const issueId =
    `windows-signing-grant-issue:${grant.grantId}`;

  const consumeId =
    `windows-signing-grant-use:${grant.grantId}`;

  try {
    const issued =
      await env.DB.prepare(
        `SELECT created_at
         FROM audit_events
         WHERE id = ?
           AND event_kind = ?
           AND actor_reference_hash = ?`
      ).bind(
        issueId,
        'windows_signing_grant_issued',
        binding
      ).first();

    if (
      Number(
        issued?.created_at
      ) !== grant.issuedAt
    ) {
      return fail(
        'signing_grant_invalid',
        403
      );
    }

    const alreadyUsed =
      await env.DB.prepare(
        `SELECT id
         FROM audit_events
         WHERE id = ?
           AND event_kind = ?`
      ).bind(
        consumeId,
        'windows_signing_grant_consumed'
      ).first();

    if (
      alreadyUsed?.id
    ) {
      return fail(
        'signing_grant_replay',
        409
      );
    }

    const consumed =
      await env.DB.prepare(
        `INSERT INTO audit_events
         (id, event_kind, actor_reference_hash, created_at)
         VALUES (?, ?, ?, ?)`
      ).bind(
        consumeId,
        'windows_signing_grant_consumed',
        binding,
        now
      ).run();

    if (
      consumed?.meta?.changes !== 1
    ) {
      return fail(
        'signing_grant_storage_unavailable',
        503
      );
    }

  } catch {
    return fail(
      'signing_grant_storage_unavailable',
      503
    );
  }

  return json({
    ok: true,
    consumed: true,
    grantId:
      grant.grantId
  });
}

async function health(env) {
  if (!ready(env)) return json({ ok: false, service: 'appforge-control-plane', database: 'binding_missing' }, 503);
  try {
    const probe = await env.DB.prepare('SELECT 1 AS ok').first();
    if (probe?.ok !== 1) throw Error('db');
    return json({ ok: true, service: 'appforge-control-plane', database: 'reachable' });
  } catch {
    return json({ ok: false, service: 'appforge-control-plane', database: 'unavailable' }, 503);
  }
}

/** Staging only: protected endpoints require an ID token on EACH request. */
export async function handleRequest(request, env, dependencies = {}) {

    try {
      const { pathname, searchParams } = new URL(request.url);
      if (pathname === '/health' && request.method === 'GET') return health(env);

      if (
        pathname === '/api/security/config' ||
        pathname === '/api/security/attest'
      ) {
        return handlePlayIntegritySecurityRoute(
          request,
          env,
          dependencies,
          pathname
        );
      }

      if (pathname === '/api/client/android/policy' && request.method === 'GET') {
        // STAGING ONLY: no fabricated Play release / forced-update policy.
        // Not a substitute for a real release-policy integration.
        const version = searchParams.get('versionCode');
        if (!version || !/^[1-9][0-9]{0,6}$/.test(version)) return fail('invalid_version_code', 400);
        const current = Number(version);
        if (!Number.isSafeInteger(current) || current > 1_000_000) return fail('invalid_version_code', 400);
        return json({
          state: 'NORMAL',
          minSupportedVersionCode: 1,
          latestVersionCode: current,
          message: '',
          playStoreUrl: ''
        });
      }

      // Obsolete Phase 2 password endpoints cannot silently survive the pivot.
      if (pathname === '/api/auth' || pathname.startsWith('/api/auth/')) {
        return fail('account_endpoints_retired', 410);
      }

      const proAdminRoute =
        pathname === '/api/admin/pro-codes' ||
        pathname.startsWith('/api/admin/pro-codes/') ||
        pathname === '/api/admin/pro-grants' ||
        pathname.startsWith('/api/admin/pro-grants/');

      const publisherSigningRoute =
        windowsSigningGrantRoute(
          pathname
        );

      const aiAdminRoute =
        pathname === '/api/admin/ai/chat';

      // Admin alone is account-based. Normal users stay accountless.
      // An email, old bearer, device ID or Play purchase NEVER confers admin.
      if (
        pathname === '/api/admin/system-status' ||
        pathname === '/api/admin/google/verify' ||
        proAdminRoute ||
        publisherSigningRoute ||
        aiAdminRoute
      ) {
        if (
          (pathname === '/api/admin/system-status' && request.method !== 'GET') ||
          (pathname === '/api/admin/google/verify' && request.method !== 'POST') ||
          (publisherSigningRoute && request.method !== 'POST') ||
          (aiAdminRoute && request.method !== 'POST')
        ) {
          return fail('method_not_allowed', 405);
        }
        if (!env?.GOOGLE_WEB_CLIENT_ID || !env?.GOOGLE_ANDROID_CLIENT_ID || !ready(env)) {
          return fail('admin_identity_not_configured', 503);
        }
        let token = '';
        let expectedNonce;
        if (pathname !== '/api/admin/google/verify') {
          const authorization = request.headers.get('authorization') || '';
          if (/^Bearer [A-Za-z0-9_.-]{50,12000}$/.test(authorization)) {
            token = authorization.slice(7);
          }
        } else {
          if ((Number(request.headers.get('content-length')) || 0) > 14000) {
            return fail('invalid_identity', 400);
          }
          const body = await request.text();
          if (body.length > 14000) return fail('invalid_identity', 400);
          try {
            const submitted = JSON.parse(body);
            token = submitted?.idToken;
            expectedNonce = submitted?.nonce;
          } catch { /* reject below */ }
          if (typeof expectedNonce !== 'string' ||
              !/^[A-Za-z0-9_-]{40,128}$/.test(expectedNonce)) {
            return fail('invalid_identity', 401);
          }
        }
        if (typeof token !== 'string' || token.length < 50) return fail('invalid_identity', 401);
        let identity;
        try {
          identity = await verifyGoogleIdToken(token, env.GOOGLE_WEB_CLIENT_ID,
            env.GOOGLE_ANDROID_CLIENT_ID, { ...dependencies, expectedNonce });
        } catch (error) {
          if (error instanceof IdentityProviderUnavailable) return fail('identity_provider_unavailable', 503);
          if (error instanceof InvalidIdentity) return fail('invalid_identity', 401);
          return fail('identity_provider_unavailable', 503);
        }
        let verifiedAdminHash = '';
        try {
          const hash = await subjectSha256(identity.sub);
          verifiedAdminHash = hash;
          const owner = await env.DB.prepare(
            'SELECT state FROM admin_identities WHERE google_subject_hash = ?'
          ).bind(hash).first();
          if (owner?.state !== 'active') return fail('admin_forbidden', 403);
        } catch {
          return fail('admin_allowlist_unavailable', 503);
        }
        if (pathname === '/api/admin/google/verify') {
          return json({ ok: true, adminVerified: true, expiresAt: identity.exp });
        }

        if (publisherSigningRoute) {
          return handleWindowsSigningAuthorization(
            request,
            env,
            verifiedAdminHash,
            pathname
          );
        }

        if (proAdminRoute) {
          return handleAdminProCodes(
            request, env, verifiedAdminHash, pathname
          );
        }

        if (aiAdminRoute) {
          return handleAdminAiChat(
            request,
            env,
            dependencies
          );
        }

        return json({ ok: true, adminVerified: true, expiresAt: identity.exp });
      }
      if (pathname === '/api/admin' || pathname.startsWith('/api/admin/')) {
        // No account-management functions are enabled until separately audited.
        return fail('admin_operation_not_migrated', 503);
      }

      if (
        pathname === '/api/pro/code/redeem' ||
        pathname === '/api/pro/code/challenge' ||
        pathname === '/api/pro/code/ownership-challenge' ||
        pathname === '/api/pro/code/recover' ||
        pathname === '/api/pro/code/reactivate' ||
        pathname === '/api/pro/code/status'
      ) {
        return handleProRedemption(request, env, pathname);
      }

      // Monthly subscription and add-on routes are retired in the one-product model.
      if (pathname.startsWith('/api/quota/addons/') ||
          pathname === '/api/pro/subscribe' || pathname === '/api/pro/monthly') {
        return fail('legacy_billing_product_retired', 410);
      }

      // In particular: never return { active: false } for a verification outage
      // and never return { active: true } without Google server verification.
      if (pathname === '/api/pro/status' || pathname.startsWith('/api/pro/') ||
          pathname.startsWith('/api/quota/') || pathname.startsWith('/api/projects/quota') ||
          pathname.startsWith('/api/security/') || pathname.startsWith('/api/device/')) {
        return fail('play_or_device_verification_not_configured', 503);
      }

      if (pathname.startsWith('/api/')) return fail('control_plane_not_migrated', 503);
      return fail('not_found', 404);
    } catch {
      return fail('service_unavailable', 503);
    }
}

export default { fetch: (request, env) => handleRequest(request, env) };
