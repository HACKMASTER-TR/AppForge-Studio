const PLAY_INTEGRITY_SCOPE =
  'https://www.googleapis.com/auth/playintegrity';

const GOOGLE_TOKEN_ENDPOINT =
  'https://oauth2.googleapis.com/token';

const encoder =
  new TextEncoder();

let cachedToken = null;

function base64UrlBytes(
  bytes
) {
  let binary = '';

  for (
    let offset = 0;
    offset < bytes.length;
    offset += 0x8000
  ) {
    binary += String.fromCharCode(
      ...bytes.subarray(
        offset,
        Math.min(
          offset + 0x8000,
          bytes.length
        )
      )
    );
  }

  return btoa(binary)
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/g, '');
}

function base64UrlJson(
  value
) {
  return base64UrlBytes(
    encoder.encode(
      JSON.stringify(value)
    )
  );
}

function privateKeyDer(
  pem
) {
  const normalized =
    String(pem || '')
      .replace(/\\n/g, '\n');

  const body =
    normalized
      .replace(
        /-----BEGIN PRIVATE KEY-----/g,
        ''
      )
      .replace(
        /-----END PRIVATE KEY-----/g,
        ''
      )
      .replace(
        /\s+/g,
        ''
      );

  if (
    body.length < 100
  ) {
    throw new Error(
      'google_service_account_key_invalid'
    );
  }

  const binary =
    atob(body);

  const bytes =
    new Uint8Array(
      binary.length
    );

  for (
    let i = 0;
    i < binary.length;
    i += 1
  ) {
    bytes[i] =
      binary.charCodeAt(i);
  }

  return bytes;
}

export function buildServiceAccountClaims(
  email,
  nowSeconds
) {
  return {
    iss:
      email,

    scope:
      PLAY_INTEGRITY_SCOPE,

    aud:
      GOOGLE_TOKEN_ENDPOINT,

    iat:
      nowSeconds,

    exp:
      nowSeconds + 3600
  };
}

async function createAssertion(
  env,
  nowSeconds
) {
  const email =
    String(
      env?.PLAY_INTEGRITY_SERVICE_ACCOUNT_EMAIL ||
      ''
    ).trim();

  const privateKey =
    String(
      env?.PLAY_INTEGRITY_SERVICE_ACCOUNT_PRIVATE_KEY ||
      ''
    );

  const keyId =
    String(
      env?.PLAY_INTEGRITY_SERVICE_ACCOUNT_KEY_ID ||
      ''
    ).trim();

  if (
    !/^[^@\s]+@[^@\s]+$/.test(
      email
    )
  ) {
    throw new Error(
      'google_service_account_email_missing'
    );
  }

  if (
    !privateKey.includes(
      'PRIVATE KEY'
    )
  ) {
    throw new Error(
      'google_service_account_private_key_missing'
    );
  }

  const header = {
    alg:
      'RS256',

    typ:
      'JWT'
  };

  if (
    keyId
  ) {
    header.kid =
      keyId;
  }

  const claims =
    buildServiceAccountClaims(
      email,
      nowSeconds
    );

  const signingInput =
    [
      base64UrlJson(
        header
      ),
      base64UrlJson(
        claims
      )
    ].join('.');

  const cryptoKey =
    await crypto.subtle.importKey(
      'pkcs8',
      privateKeyDer(
        privateKey
      ),
      {
        name:
          'RSASSA-PKCS1-v1_5',

        hash:
          'SHA-256'
      },
      false,
      [
        'sign'
      ]
    );

  const signature =
    await crypto.subtle.sign(
      'RSASSA-PKCS1-v1_5',
      cryptoKey,
      encoder.encode(
        signingInput
      )
    );

  return (
    signingInput +
    '.' +
    base64UrlBytes(
      new Uint8Array(
        signature
      )
    )
  );
}

export async function getPlayIntegrityAccessToken(
  env,
  dependencies = {}
) {
  const nowSeconds =
    typeof dependencies.nowSeconds ===
      'function'
      ? dependencies.nowSeconds()
      : Math.floor(
          Date.now() / 1000
        );

  const issuer =
    String(
      env?.PLAY_INTEGRITY_SERVICE_ACCOUNT_EMAIL ||
      ''
    ).trim();

  if (
    cachedToken &&
    cachedToken.issuer ===
      issuer &&
    cachedToken.expiresAt >
      nowSeconds + 90
  ) {
    return cachedToken.token;
  }

  const assertion =
    typeof dependencies.createAssertion ===
      'function'
      ? await dependencies.createAssertion(
          env,
          nowSeconds
        )
      : await createAssertion(
          env,
          nowSeconds
        );

  const fetchImpl =
    dependencies.fetch ||
    fetch;

  const form =
    new URLSearchParams();

  form.set(
    'grant_type',
    'urn:ietf:params:oauth:grant-type:jwt-bearer'
  );

  form.set(
    'assertion',
    assertion
  );

  let response;

  try {
    response =
      await fetchImpl(
        GOOGLE_TOKEN_ENDPOINT,
        {
          method:
            'POST',

          headers: {
            'content-type':
              'application/x-www-form-urlencoded'
          },

          body:
            form.toString()
        }
      );
  } catch {
    throw new Error(
      'google_oauth_unavailable'
    );
  }

  if (
    !response.ok
  ) {
    throw new Error(
      'google_oauth_rejected'
    );
  }

  let payload;

  try {
    payload =
      await response.json();
  } catch {
    throw new Error(
      'google_oauth_invalid_response'
    );
  }

  const token =
    typeof payload?.access_token ===
      'string'
      ? payload.access_token
      : '';

  const expiresIn =
    Number(
      payload?.expires_in
    );

  if (
    token.length < 20 ||
    !Number.isFinite(
      expiresIn
    ) ||
    expiresIn < 60
  ) {
    throw new Error(
      'google_oauth_invalid_token'
    );
  }

  cachedToken = {
    issuer,
    token,
    expiresAt:
      nowSeconds +
      Math.min(
        expiresIn,
        3600
      )
  };

  return token;
}

export function resetPlayIntegrityAccessTokenCacheForTests() {
  cachedToken =
    null;
}
