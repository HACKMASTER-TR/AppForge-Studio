import {
  handlePlayIntegritySecurityRoute
} from './play_integrity_route.mjs';

const json =
  (
    value,
    status = 200
  ) =>
    new Response(
      JSON.stringify(value),
      {
        status,
        headers: {
          'content-type':
            'application/json; charset=utf-8',

          'cache-control':
            'no-store',

          'x-content-type-options':
            'nosniff',

          'referrer-policy':
            'no-referrer'
        }
      }
    );

const fail =
  (
    error,
    status
  ) =>
    json(
      {
        ok:
          false,

        error
      },
      status
    );

/**
 * PLAY_INTEGRITY_ISOLATED_STAGING_V4_1
 *
 * This Worker intentionally exposes ONLY:
 *
 *   GET  /health
 *   GET  /api/security/config
 *   POST /api/security/attest
 *
 * It has no D1 binding, no admin routes, no Pro routes,
 * no Play publishing routes and no production Custom Domain.
 */
export async function handleIntegrityStagingRequest(
  request,
  env,
  dependencies = {}
) {
  try {
    const {
      pathname
    } =
      new URL(
        request.url
      );

    if (
      pathname ===
        '/health' &&
      request.method ===
        'GET'
    ) {
      return json({
        ok:
          true,

        service:
          'appforge-integrity-staging',

        environment:
          'isolated-staging',

        database:
          'not_bound'
      });
    }

    if (
      pathname ===
        '/api/security/config' ||
      pathname ===
        '/api/security/attest'
    ) {
      return handlePlayIntegritySecurityRoute(
        request,
        env,
        dependencies,
        pathname
      );
    }

    if (
      pathname.startsWith(
        '/api/'
      )
    ) {
      return fail(
        'route_not_enabled_in_integrity_staging',
        404
      );
    }

    return fail(
      'not_found',
      404
    );
  } catch {
    return fail(
      'service_unavailable',
      503
    );
  }
}

export default {
  fetch:
    (
      request,
      env
    ) =>
      handleIntegrityStagingRequest(
        request,
        env
      )
};
