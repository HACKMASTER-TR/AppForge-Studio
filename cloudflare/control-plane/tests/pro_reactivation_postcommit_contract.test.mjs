import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const source = readFileSync(
  new URL(
    '../src/pro_redemption.mjs',
    import.meta.url
  ),
  'utf8'
);

const start = source.indexOf(
  'async function ownership(env, body, operation)'
);

const end = source.indexOf(
  'async function status(env, body)',
  start
);

assert.ok(start >= 0);
assert.ok(end > start);

const ownership = source.slice(start, end);

test(
  'reactivation keeps atomic receipt guard',
  () => {
    assert.match(
      ownership,
      /receiptGuard\(env,\s*\{/
    );

    assert.match(
      ownership,
      /pro_redemption_receipts/
    );
  }
);

test(
  'reactivation does not trust exact D1 meta changes',
  () => {
    assert.doesNotMatch(
      ownership,
      /results\.some\(\s*item\s*=>\s*item\?\.meta\?\.changes\s*!==\s*1/
    );
  }
);

test(
  'reactivation proves committed code grant and receipt',
  () => {
    assert.match(
      ownership,
      /JOIN pro_admin_grants g/
    );

    assert.match(
      ownership,
      /JOIN pro_redemption_receipts r/
    );

    assert.match(
      ownership,
      /c\.state = 'redeemed'/
    );

    assert.match(
      ownership,
      /g\.state = 'active'/
    );

    assert.match(
      ownership,
      /c\.redemption_id = \?/
    );

    assert.match(
      ownership,
      /r\.redemption_id = \?/
    );

    assert.match(
      ownership,
      /receipt_redemption_id !== redemptionId/
    );
  }
);

test(
  'revoked recovery still cannot unlock Pro',
  () => {
    assert.match(
      ownership,
      /active:\s*grant\.state === 'active'/
    );
  }
);
