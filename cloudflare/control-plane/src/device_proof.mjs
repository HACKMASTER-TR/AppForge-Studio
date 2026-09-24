/**
 * Pro installation proof-of-possession.
 *
 * Android keeps a P-256 private key in Android Keystore.
 * The server receives only its SPKI public key.
 *
 * No device ID, email or locally cached flag grants Pro.
 * This module verifies signatures; it grants no entitlement.
 */

const utf8 = new TextEncoder();

function decodeBase64Url(value, min, max) {
  if (
    typeof value !== 'string' ||
    value.length < min ||
    value.length > max ||
    !/^[A-Za-z0-9_-]+$/.test(value)
  ) {
    throw new Error('invalid_device_proof');
  }

  const padded = value
    .replaceAll('-', '+')
    .replaceAll('_', '/')
    .padEnd(Math.ceil(value.length / 4) * 4, '=');

  try {
    return Uint8Array.from(atob(padded), char => char.charCodeAt(0));
  } catch {
    throw new Error('invalid_device_proof');
  }
}

function normalizeInteger(bytes) {
  if (bytes.length < 1 || bytes.length > 33) {
    throw new Error('invalid_device_proof');
  }

  // ASN.1 INTEGER must be non-negative and minimally encoded.
  if (bytes[0] & 0x80) {
    throw new Error('invalid_device_proof');
  }

  if (
    bytes.length > 1 &&
    bytes[0] === 0 &&
    !(bytes[1] & 0x80)
  ) {
    throw new Error('invalid_device_proof');
  }

  const normalized =
    bytes.length === 33 && bytes[0] === 0
      ? bytes.slice(1)
      : bytes;

  if (normalized.length > 32) {
    throw new Error('invalid_device_proof');
  }

  const output = new Uint8Array(32);
  output.set(normalized, 32 - normalized.length);
  return output;
}

/**
 * Android SHA256withECDSA normally produces DER.
 * WebCrypto ECDSA verification expects 64-byte P1363.
 * Accept either encoding, without accepting malformed DER.
 */
export function signatureToP1363(signature) {
  if (!(signature instanceof Uint8Array)) {
    throw new Error('invalid_device_proof');
  }

  if (signature.length === 64) {
    return signature;
  }

  if (
    signature.length < 8 ||
    signature.length > 72 ||
    signature[0] !== 0x30 ||
    signature[1] !== signature.length - 2
  ) {
    throw new Error('invalid_device_proof');
  }

  let offset = 2;
  const parts = [];

  for (let i = 0; i < 2; i++) {
    if (signature[offset++] !== 0x02) {
      throw new Error('invalid_device_proof');
    }

    const length = signature[offset++];

    if (
      length < 1 ||
      length > 33 ||
      offset + length > signature.length
    ) {
      throw new Error('invalid_device_proof');
    }

    parts.push(
      normalizeInteger(signature.slice(offset, offset + length))
    );

    offset += length;
  }

  if (offset !== signature.length) {
    throw new Error('invalid_device_proof');
  }

  const output = new Uint8Array(64);
  output.set(parts[0], 0);
  output.set(parts[1], 32);
  return output;
}

export function redeemMessage({
  code,
  publicKey,
  nonce
}) {
  if (
    typeof code !== 'string' ||
    !/^AFPRO-[A-Za-z0-9_-]{43}$/.test(code) ||
    typeof publicKey !== 'string' ||
    !/^[A-Za-z0-9_-]{80,300}$/.test(publicKey) ||
    typeof nonce !== 'string' ||
    !/^[A-Za-z0-9_-]{22,86}$/.test(nonce)
  ) {
    throw new Error('invalid_device_proof');
  }

  return [
    'appforge-pro-redeem-v1',
    code,
    publicKey,
    nonce
  ].join('\n');
}

export function statusMessage({
  installationId,
  challengeId,
  nonce
}) {
  if (
    typeof installationId !== 'string' ||
    !/^[0-9a-f-]{36}$/.test(installationId) ||
    typeof challengeId !== 'string' ||
    !/^[0-9a-f-]{36}$/.test(challengeId) ||
    typeof nonce !== 'string' ||
    !/^[A-Za-z0-9_-]{22,86}$/.test(nonce)
  ) {
    throw new Error('invalid_device_proof');
  }

  return [
    'appforge-pro-status-v1',
    installationId,
    challengeId,
    nonce
  ].join('\n');
}

/** Separate signed domains prevent status proofs from redeeming new codes. */
export function ownershipMessage({ operation, installationId, challengeId, nonce, publicKey, code }) {
  if (!['recover', 'reactivate'].includes(operation) ||
      typeof publicKey !== 'string' || !/^[A-Za-z0-9_-]{80,300}$/.test(publicKey)) {
    throw new Error('invalid_device_proof');
  }
  // Reuse status format validation but NOT the status signing domain.
  statusMessage({ installationId, challengeId, nonce });
  if (operation === 'reactivate' &&
      (typeof code !== 'string' || !/^AFPRO-[A-Za-z0-9_-]{43}$/.test(code))) {
    throw new Error('invalid_device_proof');
  }
  if (operation === 'recover' && code !== undefined) {
    throw new Error('invalid_device_proof');
  }
  return [
    'appforge-pro-ownership-v1', operation, installationId,
    challengeId, nonce, publicKey,
    ...(operation === 'reactivate' ? [code] : [])
  ].join('\n');
}

export async function publicKeyThumbprint(publicKey) {
  const encoded = decodeBase64Url(publicKey, 80, 300);
  const digest = await crypto.subtle.digest(
    'SHA-256',
    encoded
  );

  return [...new Uint8Array(digest)]
    .map(byte => byte.toString(16).padStart(2, '0'))
    .join('');
}

export async function verifyDeviceSignature({
  publicKey,
  signature,
  message
}) {
  try {
    if (
      typeof message !== 'string' ||
      message.length < 10 ||
      message.length > 2048
    ) {
      return false;
    }

    const keyBytes = decodeBase64Url(
      publicKey, 80, 300
    );

    const signatureBytes = decodeBase64Url(
      signature, 80, 150
    );

    const publicCryptoKey = await crypto.subtle.importKey(
      'spki',
      keyBytes,
      {
        name: 'ECDSA',
        namedCurve: 'P-256'
      },
      false,
      ['verify']
    );

    return await crypto.subtle.verify(
      {
        name: 'ECDSA',
        hash: 'SHA-256'
      },
      publicCryptoKey,
      signatureToP1363(signatureBytes),
      utf8.encode(message)
    );
  } catch {
    return false;
  }
}
