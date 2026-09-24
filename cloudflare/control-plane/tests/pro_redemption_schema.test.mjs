import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const migration = name =>
  readFileSync(
    new URL(`../migrations/${name}`, import.meta.url),
    'utf8'
  );

const codes = migration('0002_admin_pro_codes.sql');
const grants = migration('0003_pro_grants.sql');

test('redemption has a unique transaction identifier', () => {
  assert.match(codes, /redemption_id TEXT UNIQUE/);
  assert.match(codes, /code_hash TEXT NOT NULL UNIQUE/);
  assert.match(codes, /redeemed_by_thumbprint TEXT/);
  assert.doesNotMatch(codes, /plaintext_code TEXT/i);
});

test('one activation code cannot create multiple Pro grants', () => {
  assert.match(
    grants,
    /activation_code_id TEXT NOT NULL UNIQUE/
  );
  assert.match(
    grants,
    /installation_id TEXT PRIMARY KEY NOT NULL/
  );
});

test('public key is stored without a private key', () => {
  assert.match(
    grants,
    /CREATE TABLE IF NOT EXISTS pro_installation_keys/
  );
  assert.match(
    grants,
    /public_key_spki TEXT NOT NULL/
  );
  assert.doesNotMatch(
    grants,
    /private_key\s+TEXT/i
  );
});

test('server challenges have unique request nonce markers', () => {
  assert.match(
    grants,
    /CREATE TABLE IF NOT EXISTS installation_challenges/
  );
  assert.match(
    grants,
    /nonce_hash TEXT NOT NULL/
  );
  assert.match(
    grants,
    /request_nonce_hash TEXT NOT NULL UNIQUE/
  );
  assert.match(
    grants,
    /consumed_at INTEGER/
  );
});
