import {
  decodeAndEvaluatePlayIntegrity,
  InvalidPlayIntegrityVerdict,
  PlayIntegrityUnavailable
} from './play_integrity_decode.mjs';

import {
  issueIntegritySession
} from './play_integrity_session.mjs';

const encoder =
  new TextEncoder();

const json =
  (
    value,
    status = 200
  ) =>
    new Response(
      JSON.stringify(
        value
      ),
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

export function integrityServerConfigured(
  env
) {
  const projectNumber =
    String(
      env?.PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER ||
      ''
    );

  return (
    /^[1-9][0-9]{5,19}$/.test(
      projectNumber
    ) &&
    /^[^@\s]+@[^@\s]+$/.test(
      String(
        env?.PLAY_INTEGRITY_SERVICE_ACCOUNT_EMAIL ||
        ''
      )
    ) &&
    String(
      env?.PLAY_INTEGRITY_SERVICE_ACCOUNT_PRIVATE_KEY ||
      ''
    ).includes(
      'PRIVATE KEY'
    ) &&
    String(
      env?.PLAY_INTEGRITY_SESSION_SECRET ||
      ''
    ).length >= 32
  );
}

export async function computeIntegrityRequestHash({
  action,
  nonce,
  timestamp
}) {
  const material =
    (
      'appforge-integrity-v1|' +
      action +
      '|' +
      nonce +
      '|' +
      timestamp
    );

  const digest =
    await crypto.subtle.digest(
      'SHA-256',
      encoder.encode(
        material
      )
    );

  let binary = '';

  const bytes =
    new Uint8Array(
      digest
    );

  for (
    const byte of bytes
  ) {
    binary +=
      String.fromCharCode(
        byte
      );
  }

  return btoa(binary)
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/g, '');
}

export async function handlePlayIntegritySecurityRoute(
  request,
  env,
  dependencies = {},
  pathname
) {
  if (
    pathname ===
      '/api/security/config'
  ) {
    if (
      request.method !==
        'GET'
    ) {
      return fail(
        'method_not_allowed',
        405
      );
    }

    const configured =
      integrityServerConfigured(
        env
      );

    const projectNumber =
      configured
        ? Number(
            env
              .PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER
          )
        : 0;

    return json({
      integrityEnabled:
        configured,

      cloudProjectNumber:
        projectNumber,

      proProductId:
        'appforge_pro_lifetime',

      proMonthlyProductId:
        '',

      quota10ProductId:
        '',

      quota25ProductId:
        '',

      quota50ProductId:
        '',

      strictProIntegrity:
        configured,

      serverDecode:
        configured
          ? 'configured'
          : 'secret_gated',

      advancedVerdicts:
        'console_gated'
    });
  }

  if (
    pathname !==
      '/api/security/attest'
  ) {
    return fail(
      'not_found',
      404
    );
  }

  if (
    request.method !==
      'POST'
  ) {
    return fail(
      'method_not_allowed',
      405
    );
  }

  if (
    !integrityServerConfigured(
      env
    )
  ) {
    return fail(
      'play_integrity_server_not_configured',
      503
    );
  }

  const declaredLength =
    Number(
      request.headers.get(
        'content-length'
      )
    ) || 0;

  if (
    declaredLength >
      65_536
  ) {
    return fail(
      'invalid_integrity_request',
      400
    );
  }

  const raw =
    await request.text();

  if (
    raw.length >
      65_536
  ) {
    return fail(
      'invalid_integrity_request',
      400
    );
  }

  let body;

  try {
    body =
      JSON.parse(
        raw
      );
  } catch {
    return fail(
      'invalid_integrity_request',
      400
    );
  }

  const integrityToken =
    body?.integrityToken;

  const requestHash =
    body?.requestHash;

  const action =
    body?.action;

  const nonce =
    body?.nonce;

  const timestamp =
    body?.timestamp;

  if (
    typeof integrityToken !==
      'string' ||
    integrityToken.length < 100 ||
    integrityToken.length >
      50_000 ||
    typeof requestHash !==
      'string' ||
    !/^[A-Za-z0-9_-]{43}$/.test(
      requestHash
    ) ||
    typeof action !==
      'string' ||
    !/^[a-z0-9_.:-]{1,64}$/.test(
      action
    ) ||
    typeof nonce !==
      'string' ||
    !/^[A-Za-z0-9_-]{32,128}$/.test(
      nonce
    ) ||
    !Number.isSafeInteger(
      timestamp
    )
  ) {
    return fail(
      'invalid_integrity_request',
      400
    );
  }

  const nowMs =
    typeof dependencies.nowMs ===
      'function'
      ? dependencies.nowMs()
      : Date.now();

  if (
    timestamp <
      nowMs - 120_000 ||
    timestamp >
      nowMs + 30_000
  ) {
    return fail(
      'integrity_request_stale',
      401
    );
  }

  const recomputedHash =
    await computeIntegrityRequestHash({
      action,
      nonce,
      timestamp
    });

  if (
    recomputedHash !==
      requestHash
  ) {
    return fail(
      'integrity_request_binding_mismatch',
      401
    );
  }

  let evaluation;

  try {
    const decode =
      dependencies
        .decodeAndEvaluatePlayIntegrity ||
      decodeAndEvaluatePlayIntegrity;

    evaluation =
      await decode({
        integrityToken,
        expectedRequestHash:
          requestHash,
        env,
        dependencies
      });
  } catch (
    error
  ) {
    if (
      error instanceof
        InvalidPlayIntegrityVerdict
    ) {
      return fail(
        'invalid_integrity_verdict',
        401
      );
    }

    if (
      error instanceof
        PlayIntegrityUnavailable
    ) {
      return fail(
        'play_integrity_unavailable',
        503
      );
    }

    return fail(
      'play_integrity_unavailable',
      503
    );
  }

  if (
    evaluation
      .criticalActionAllowed !==
      true
  ) {
    return json(
      {
        ok:
          false,

        error:
          'integrity_policy_denied',

        verdict: {
          requestVerified:
            evaluation
              .requestVerified ===
              true,

          playRecognized:
            evaluation
              .playRecognized ===
              true,

          meetsDeviceIntegrity:
            evaluation
              .meetsDeviceIntegrity ===
              true,

          optionalVerdictsReady:
            evaluation
              .optionalVerdictsReady ===
              true,

          appAccessRisk:
            evaluation
              .appAccessRisk ===
              true,

          playProtectVerdict:
            evaluation
              .playProtectVerdict,

          activityLevel:
            evaluation
              .activityLevel
        }
      },
      403
    );
  }

  try {
    const issue =
      dependencies
        .issueIntegritySession ||
      issueIntegritySession;

    const integritySession =
      await issue({
        env,
        action,
        requestHash,
        evaluation,
        nowMs
      });

    return json({
      ok:
        true,

      verified:
        true,

      integritySession,

      verdict: {
        playRecognized:
          true,

        meetsDeviceIntegrity:
          true,

        optionalVerdictsReady:
          true,

        appAccessRisk:
          false,

        playProtectVerdict:
          evaluation
            .playProtectVerdict,

        activityLevel:
          evaluation
            .activityLevel,

        sdkVersion:
          evaluation
            .sdkVersion,

        deviceRecall:
          evaluation
            .deviceRecall
      }
    });
  } catch {
    return fail(
      'integrity_session_unavailable',
      503
    );
  }
}
