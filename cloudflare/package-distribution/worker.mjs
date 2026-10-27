// AppForge V9 R2 STAGING READ-ONLY gateway. PUBLIC ACCESS IS DISABLED by default.
// This cannot be enabled accidentally by creating a bucket: the explicit
// staging switch is required and wrangler.example.toml sets workers_dev=false.
// No PUT, DELETE, LIST, release alias or trust-policy mutation routes exist.
const SHA = '[0-9a-f]{64}';
const RE = new RegExp(`^/v9/objects/sha256/([0-9a-f]{2})/(${SHA})$`);
const MAX_RANGE = 16 * 1024 * 1024;
const common = { 'X-Content-Type-Options': 'nosniff', 'Referrer-Policy': 'no-referrer' };
const deny = (message, status) => new Response(message, {
  status, headers: { ...common, 'Cache-Control': 'no-store' },
});

function rangeOf(header, size) {
  if (!header) return null;
  const match = /^bytes=(\d+)-(\d*)$/.exec(header);
  if (!match) return false;
  const start = Number(match[1]);
  const end = match[2] === '' ? Math.min(start + MAX_RANGE - 1, size - 1) : Number(match[2]);
  if (!Number.isSafeInteger(start) || !Number.isSafeInteger(end) ||
      start < 0 || end < start || start >= size || end >= size || end - start + 1 > MAX_RANGE) return false;
  return { start, end, offset: start, length: end - start + 1 };
}

export default {
  async fetch(request, env) {
    // Even a misconfigured private binding alone cannot enable the gateway.
    if (env?.STAGING_READ_ENABLED !== 'YES_I_UNDERSTAND_STAGING_ONLY') return deny('Staging disabled', 503);
    if (!env.PACKAGES_BUCKET) return deny('Storage binding unavailable', 503);
    if (!['GET', 'HEAD'].includes(request.method)) return deny('Method not allowed', 405);
    const url = new URL(request.url);
    if (url.search || url.hash || url.pathname.includes('%')) return deny('Invalid path', 400);
    const match = RE.exec(url.pathname);
    if (!match || !match[2].startsWith(match[1])) return deny('Not found', 404);
    const key = `objects/sha256/${match[1]}/${match[2]}`;
    const meta = await env.PACKAGES_BUCKET.head(key);
    if (!meta) return deny('Not found', 404);
    const headers = new Headers({
      ...common,
      'Content-Type': 'application/octet-stream',
      'Cache-Control': 'public, max-age=31536000, immutable',
      'Accept-Ranges': 'bytes',
    });
    if (meta.httpEtag) headers.set('ETag', meta.httpEtag);
    if (request.method === 'HEAD') {
      headers.set('Content-Length', String(meta.size));
      return new Response(null, { status: 200, headers });
    }
    const range = rangeOf(request.headers.get('Range'), meta.size);
    if (range === false) {
      headers.set('Content-Range', `bytes */${meta.size}`);
      return new Response(null, { status: 416, headers });
    }
    const obj = await env.PACKAGES_BUCKET.get(key, range ? { range: { offset: range.offset, length: range.length } } : undefined);
    if (!obj || !('body' in obj)) return deny('Not found', 404);
    if (range) {
      headers.set('Content-Range', `bytes ${range.start}-${range.end}/${meta.size}`);
      headers.set('Content-Length', String(range.length));
      return new Response(obj.body, { status: 206, headers });
    }
    headers.set('Content-Length', String(meta.size));
    return new Response(obj.body, { status: 200, headers });
  },
};
