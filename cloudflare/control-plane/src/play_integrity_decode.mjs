import {
  getPlayIntegrityAccessToken
} from './google_service_account_oauth.mjs';

import {
  evaluateAdvancedPlayIntegrity
} from './play_integrity_policy.mjs';

const PACKAGE_NAME =
  'com.appforge.studio';

const MAX_TOKEN_AGE_MS =
  120_000;

const MAX_FUTURE_SKEW_MS =
  30_000;

export class PlayIntegrityUnavailable
  extends Error {}

export class InvalidPlayIntegrityVerdict
  extends Error {}

const safeIntegerFrom =
  value => {
    const number =
      Number(value);

    return Number.isSafeInteger(
      number
    )
      ? number
      : null;
  };

export async function decodeAndEvaluatePlayIntegrity({
  integrityToken,
  expectedRequestHash,
  env,
  dependencies = {}
}) {
  if (
    typeof integrityToken !==
      'string' ||
    integrityToken.length < 100 ||
    integrityToken.length > 50_000
  ) {
    throw new InvalidPlayIntegrityVerdict(
      'integrity_token_invalid'
    );
  }

  if (
    typeof expectedRequestHash !==
      'string' ||
    !/^[A-Za-z0-9_-]{43}$/.test(
      expectedRequestHash
    )
  ) {
    throw new InvalidPlayIntegrityVerdict(
      'request_hash_invalid'
    );
  }

  let accessToken;

  try {
    accessToken =
      typeof dependencies.getAccessToken ===
        'function'
        ? await dependencies.getAccessToken(
            env,
            dependencies
          )
        : await getPlayIntegrityAccessToken(
            env,
            dependencies
          );
  } catch {
    throw new PlayIntegrityUnavailable(
      'play_integrity_auth_unavailable'
    );
  }

  const fetchImpl =
    dependencies.fetch ||
    fetch;

  let response;

  try {
    response =
      await fetchImpl(
        (
          'https://playintegrity.googleapis.com/' +
          'v1/' +
          PACKAGE_NAME +
          ':decodeIntegrityToken'
        ),
        {
          method:
            'POST',

          headers: {
            authorization:
              `Bearer ${accessToken}`,

            'content-type':
              'application/json'
          },

          body:
            JSON.stringify({
              integrityToken
            })
        }
      );
  } catch {
    throw new PlayIntegrityUnavailable(
      'play_integrity_decode_unavailable'
    );
  }

  if (
    !response.ok
  ) {
    throw new PlayIntegrityUnavailable(
      'play_integrity_decode_rejected'
    );
  }

  let decoded;

  try {
    decoded =
      await response.json();
  } catch {
    throw new PlayIntegrityUnavailable(
      'play_integrity_decode_invalid_json'
    );
  }

  const payload =
    decoded?.tokenPayloadExternal;

  if (
    !payload ||
    typeof payload !==
      'object'
  ) {
    throw new InvalidPlayIntegrityVerdict(
      'token_payload_missing'
    );
  }

  const request =
    payload.requestDetails;

  if (
    !request ||
    typeof request !==
      'object'
  ) {
    throw new InvalidPlayIntegrityVerdict(
      'request_details_missing'
    );
  }

  if (
    request.requestPackageName !==
      PACKAGE_NAME
  ) {
    throw new InvalidPlayIntegrityVerdict(
      'request_package_mismatch'
    );
  }

  if (
    request.requestHash !==
      expectedRequestHash
  ) {
    throw new InvalidPlayIntegrityVerdict(
      'request_hash_mismatch'
    );
  }

  const timestampMillis =
    safeIntegerFrom(
      request.timestampMillis
    );

  if (
    timestampMillis === null
  ) {
    throw new InvalidPlayIntegrityVerdict(
      'request_timestamp_invalid'
    );
  }

  const nowMs =
    typeof dependencies.nowMs ===
      'function'
      ? dependencies.nowMs()
      : Date.now();

  if (
    timestampMillis <
      nowMs -
        MAX_TOKEN_AGE_MS ||
    timestampMillis >
      nowMs +
        MAX_FUTURE_SKEW_MS
  ) {
    throw new InvalidPlayIntegrityVerdict(
      'request_timestamp_stale'
    );
  }

  const app =
    payload.appIntegrity &&
    typeof payload.appIntegrity ===
      'object'
      ? payload.appIntegrity
      : {};

  const playRecognized =
    app.appRecognitionVerdict ===
      'PLAY_RECOGNIZED';

  const appPackageMatches =
    !app.packageName ||
    app.packageName ===
      PACKAGE_NAME;

  const versionCode =
    safeIntegerFrom(
      app.versionCode
    );

  const advanced =
    evaluateAdvancedPlayIntegrity(
      payload
    );

  const criticalActionAllowed =
    playRecognized &&
    appPackageMatches &&
    advanced
      .criticalActionAllowed;

  return {
    requestVerified:
      true,

    playRecognized,

    appPackageMatches,

    versionCode,

    ...advanced,

    criticalActionAllowed
  };
}
