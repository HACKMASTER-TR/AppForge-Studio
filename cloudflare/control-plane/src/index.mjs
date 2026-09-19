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

export default {
  async fetch(request, env) {
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

      // Admin needs verified Google identity and a server-side subject allow-list.
      // Neither device ID, bearer from old accounts, email, nor a Google Play
      // purchase can grant administrator access.
      if (pathname === '/api/admin/system-status' || pathname.startsWith('/api/admin/')) {
        return fail('admin_identity_not_configured', 503);
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
};
