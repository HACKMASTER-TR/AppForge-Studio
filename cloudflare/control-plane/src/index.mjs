import { verifyGoogleIdToken, subjectSha256, InvalidIdentity, IdentityProviderUnavailable } from './google_oidc.mjs';
import { handleAdminProCodes } from './admin_pro_codes.mjs';
import { handleProRedemption } from './pro_redemption.mjs';
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

      // Admin alone is account-based. Normal users stay accountless.
      // An email, old bearer, device ID or Play purchase NEVER confers admin.
      if (pathname === '/api/admin/system-status' || pathname === '/api/admin/google/verify' || proAdminRoute) {
        if ((pathname === '/api/admin/system-status' && request.method !== 'GET') ||
            (pathname === '/api/admin/google/verify' && request.method !== 'POST')) {
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
        if (proAdminRoute) {
          return handleAdminProCodes(
            request, env, verifiedAdminHash, pathname
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
