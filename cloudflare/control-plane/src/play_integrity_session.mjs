const encoder =
  new TextEncoder();

const decoder =
  new TextDecoder();

const SESSION_TTL_MS =
  120_000;

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

function base64UrlDecode(
  value
) {
  const padded =
    String(value)
      .replace(/-/g, '+')
      .replace(/_/g, '/')
      .padEnd(
        Math.ceil(
          value.length / 4
        ) * 4,
        '='
      );

  const binary =
    atob(padded);

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

async function sessionKey(
  env,
  usages
) {
  const secret =
    String(
      env?.PLAY_INTEGRITY_SESSION_SECRET ||
      ''
    );

  if (
    secret.length < 32
  ) {
    throw new Error(
      'integrity_session_secret_missing'
    );
  }

  return crypto.subtle.importKey(
    'raw',
    encoder.encode(
      secret
    ),
    {
      name:
        'HMAC',

      hash:
        'SHA-256'
    },
    false,
    usages
  );
}

export async function issueIntegritySession({
  env,
  action,
  requestHash,
  evaluation,
  nowMs = Date.now()
}) {
  if (
    evaluation
      ?.criticalActionAllowed !==
      true
  ) {
    throw new Error(
      'integrity_policy_not_satisfied'
    );
  }

  const payload = {
    v:
      1,

    action,

    requestHash,

    issuedAt:
      nowMs,

    expiresAt:
      nowMs +
      SESSION_TTL_MS,

    playRecognized:
      true
  };

  const encodedPayload =
    base64UrlBytes(
      encoder.encode(
        JSON.stringify(
          payload
        )
      )
    );

  const key =
    await sessionKey(
      env,
      [
        'sign'
      ]
    );

  const signature =
    await crypto.subtle.sign(
      'HMAC',
      key,
      encoder.encode(
        encodedPayload
      )
    );

  return (
    'v1.' +
    encodedPayload +
    '.' +
    base64UrlBytes(
      new Uint8Array(
        signature
      )
    )
  );
}

export async function verifyIntegritySession({
  env,
  session,
  expectedAction,
  nowMs = Date.now()
}) {
  if (
    typeof session !==
      'string'
  ) {
    return null;
  }

  const parts =
    session.split('.');

  if (
    parts.length !== 3 ||
    parts[0] !== 'v1'
  ) {
    return null;
  }

  let signature;

  try {
    signature =
      base64UrlDecode(
        parts[2]
      );
  } catch {
    return null;
  }

  const key =
    await sessionKey(
      env,
      [
        'verify'
      ]
    );

  const valid =
    await crypto.subtle.verify(
      'HMAC',
      key,
      signature,
      encoder.encode(
        parts[1]
      )
    );

  if (
    !valid
  ) {
    return null;
  }

  let payload;

  try {
    payload =
      JSON.parse(
        decoder.decode(
          base64UrlDecode(
            parts[1]
          )
        )
      );
  } catch {
    return null;
  }

  if (
    payload?.v !== 1 ||
    payload?.action !==
      expectedAction ||
    !Number.isSafeInteger(
      payload?.issuedAt
    ) ||
    !Number.isSafeInteger(
      payload?.expiresAt
    ) ||
    payload.expiresAt <=
      nowMs ||
    payload.issuedAt >
      nowMs + 30_000 ||
    payload.expiresAt -
      payload.issuedAt >
      SESSION_TTL_MS
  ) {
    return null;
  }

  return payload;
}
