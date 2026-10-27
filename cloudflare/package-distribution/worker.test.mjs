import test from 'node:test';
import assert from 'node:assert/strict';
import worker from './worker.mjs';
const sha = 'ab' + 'c'.repeat(62);
const url = `https://staging.example.test/v9/objects/sha256/ab/${sha}`;
const bytes = new TextEncoder().encode('abcdefghij');
function fixture() {
  let heads = 0, gets = 0;
  return {
    env: {
      STAGING_READ_ENABLED: 'YES_I_UNDERSTAND_STAGING_ONLY',
      PACKAGES_BUCKET: {
        async head(key) { heads++; assert.equal(key, `objects/sha256/ab/${sha}`); return { size: bytes.length, httpEtag: '"fixture"' }; },
        async get(key, opts) {
          gets++;
          assert.equal(key, `objects/sha256/ab/${sha}`);
          const buf = opts?.range ? bytes.slice(opts.range.offset, opts.range.offset + opts.range.length) : bytes;
          return { body: new Blob([buf]).stream() };
        },
      },
    },
    count: () => ({ heads, gets }),
  };
}
test('disabled by default even with R2 binding', async () => {
  const f = fixture(); delete f.env.STAGING_READ_ENABLED;
  const res = await worker.fetch(new Request(url), f.env);
  assert.equal(res.status, 503); assert.deepEqual(f.count(), { heads: 0, gets: 0 });
});
test('missing bucket never exposes objects', async () => {
  const f = fixture(); delete f.env.PACKAGES_BUCKET;
  assert.equal((await worker.fetch(new Request(url), f.env)).status, 503);
});
test('only safe GET and HEAD methods', async () => {
  const f = fixture();
  for (const method of ['PUT', 'DELETE', 'POST']) assert.equal((await worker.fetch(new Request(url, { method }), f.env)).status, 405);
  assert.deepEqual(f.count(), { heads: 0, gets: 0 });
});
test('rejects traversal, query and mismatched hash prefix', async () => {
  const f = fixture();
  for (const p of [`${url}?bust=1`, url.replace('/ab/', '/ff/'), url.replace('/ab/', '/%61b/'), `${url}/../secret`]) {
    const res = await worker.fetch(new Request(p), f.env);
    assert.ok([400, 404].includes(res.status), `${p}: ${res.status}`);
  }
  assert.deepEqual(f.count(), { heads: 0, gets: 0 });
});
test('HEAD metadata no body', async () => {
  const f = fixture();
  const res = await worker.fetch(new Request(url, { method: 'HEAD' }), f.env);
  assert.equal(res.status, 200); assert.equal(res.headers.get('content-length'), '10');
  assert.deepEqual(f.count(), { heads: 1, gets: 0 });
});
test('GET immutable object streams bytes', async () => {
  const f = fixture();
  const res = await worker.fetch(new Request(url), f.env);
  assert.equal(res.status, 200); assert.equal(await res.text(), 'abcdefghij');
  assert.equal(res.headers.get('x-content-type-options'), 'nosniff');
  assert.deepEqual(f.count(), { heads: 1, gets: 1 });
});
test('bounded Range returns exact resume slice', async () => {
  const f = fixture();
  const res = await worker.fetch(new Request(url, { headers: { Range: 'bytes=4-8' } }), f.env);
  assert.equal(res.status, 206); assert.equal(await res.text(), 'efghi');
  assert.equal(res.headers.get('content-range'), 'bytes 4-8/10');
});
test('invalid or multiple range rejected before body read', async () => {
  for (const value of ['bytes=8-3', 'bytes=0-2,4-5', 'bytes=200-300']) {
    const f = fixture();
    const res = await worker.fetch(new Request(url, { headers: { Range: value } }), f.env);
    assert.equal(res.status, 416); assert.deepEqual(f.count(), { heads: 1, gets: 0 });
  }
});
