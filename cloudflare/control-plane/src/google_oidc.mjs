/**
 * Google ID-token verification for the STAGING admin control plane.
 * Trust only Google's fixed HTTPS JWKS endpoint; no JWT payload or client
 * supplied URL can select the verification key source.
 */
const GOOGLE_JWKS = 'https://www.googleapis.com/oauth2/v3/certs';
const ISSUERS = new Set(['https://accounts.google.com', 'accounts.google.com']);
const utf8 = new TextDecoder();
let cached = null;

export class InvalidIdentity extends Error {}
export class IdentityProviderUnavailable extends Error {}

const decoded = part => {
  if (typeof part !== 'string' || !/^[A-Za-z0-9_-]+$/.test(part)) {
    throw new InvalidIdentity('invalid_encoding');
  }
  try {
    const bytes = Uint8Array.from(atob(part.replace(/-/g, '+').replace(/_/g, '/')
      .padEnd(Math.ceil(part.length / 4) * 4, '=')), char => char.charCodeAt(0));
    return JSON.parse(utf8.decode(bytes));
  } catch {
    throw new InvalidIdentity('invalid_json');
  }
};

async function keys(fetchKeys) {
  if (!fetchKeys && cached && cached.expiresAt > Date.now()) return cached.value;
  let result;
  try {
    result = await (fetchKeys || ((url, init) => fetch(url, init)))(
      GOOGLE_JWKS, {
        method: 'GET',
        redirect: 'manual',
        signal: AbortSignal.timeout(5_000)
      }
    );
    if (!result.ok) throw new Error('Google JWKS response');
    const document = await result.json();
    if (!Array.isArray(document?.keys) || document.keys.length < 1 || document.keys.length > 12) {
      throw new Error('Google JWKS format');
    }
    if (!fetchKeys) cached = { value: document.keys, expiresAt: Date.now() + 300_000 };
    return document.keys;
  } catch {
    throw new IdentityProviderUnavailable('jwks_unavailable');
  }
}

/** `expectedAudience` and `expectedPresenter` come ONLY from Worker env. */
export async function verifyGoogleIdToken(token, expectedAudience, expectedPresenter, options = {}) {
  if (!expectedAudience || !expectedPresenter ||
      !expectedAudience.endsWith('.apps.googleusercontent.com') ||
      !expectedPresenter.endsWith('.apps.googleusercontent.com')) {
    throw new IdentityProviderUnavailable('oauth_not_configured');
  }
  if (typeof token !== 'string' || token.length > 12000 || token.length < 50) {
    throw new InvalidIdentity('invalid_token');
  }
  const parts = token.split('.');
  if (parts.length !== 3) throw new InvalidIdentity('invalid_token');
  const header = decoded(parts[0]);
  const claims = decoded(parts[1]);
  if (header?.alg !== 'RS256' || typeof header.kid !== 'string' ||
      !header.kid || header.kid.length > 256 || header.crit !== undefined) {
    throw new InvalidIdentity('invalid_header');
  }
  const now = Math.floor(Date.now() / 1000);
  if (!ISSUERS.has(claims?.iss) || claims.aud !== expectedAudience ||
      (claims.azp !== undefined && claims.azp !== expectedPresenter) ||
      typeof claims.sub !== 'string' || !/^[\x21-\x7e]{1,255}$/.test(claims.sub) ||
      !Number.isInteger(claims.iat) || !Number.isInteger(claims.exp) ||
      claims.iat > now + 60 || claims.iat < now - 3600 ||
      claims.exp <= now || claims.exp > now + 3700 || claims.exp <= claims.iat) {
    throw new InvalidIdentity('invalid_claims');
  }
  if (options.expectedNonce !== undefined &&
      claims.nonce !== options.expectedNonce) {
    throw new InvalidIdentity('nonce_mismatch');
  }
  const trustedKeys = await keys(options.fetchKeys);
  const jwk = trustedKeys.find(key => key.kid === header.kid &&
    key.kty === 'RSA' && key.alg === 'RS256' && key.use === 'sig');
  if (!jwk) throw new InvalidIdentity('unknown_signing_key');
  let signature;
  try {
    const signaturePart = parts[2];
    if (!/^[A-Za-z0-9_-]+$/.test(signaturePart)) throw new Error('signature format');
    signature = Uint8Array.from(atob(signaturePart.replace(/-/g, '+').replace(/_/g, '/')
      .padEnd(Math.ceil(signaturePart.length / 4) * 4, '=')), char => char.charCodeAt(0));
    const key = await crypto.subtle.importKey('jwk', jwk,
      { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['verify']);
    const verified = await crypto.subtle.verify('RSASSA-PKCS1-v1_5', key,
      signature, new TextEncoder().encode(`${parts[0]}.${parts[1]}`));
    if (!verified) throw new Error('invalid signature');
  } catch {
    throw new InvalidIdentity('invalid_signature');
  }
  return { sub: claims.sub, exp: claims.exp };
}

export async function subjectSha256(sub) {
  const bytes = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(sub));
  return [...new Uint8Array(bytes)].map(b => b.toString(16).padStart(2, '0')).join('');
}
