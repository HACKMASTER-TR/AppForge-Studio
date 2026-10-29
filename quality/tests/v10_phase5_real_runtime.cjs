// Opt-in exact-artifact probes. Usage: node this-file <phase5 artifacts directory>.
const assert = require('node:assert/strict');
const path = require('node:path');
const {spawnSync} = require('node:child_process');
const root = path.resolve(process.argv[2]);
if (process.argv[3] === 'images') {
  const {imageSize} = require(path.join(root, 'image-size-2.0.4/package/dist/cjs/index.js'));
  const box = (name, payload = Buffer.alloc(0), size = payload.length + 8) => {
    const b = Buffer.alloc(8 + payload.length); b.writeUInt32BE(size); b.write(name, 4); payload.copy(b, 8); return b;
  };
  const icns = size => { const b = Buffer.alloc(16); b.write('icns'); b.writeUInt32BE(16, 4); b.write('ic07', 8); b.writeUInt32BE(size, 12); return b; };
  assert.equal(imageSize(icns(8)).width, 128);
  for (const size of [0, 1, 7]) assert.throws(() => imageSize(icns(size)));
  const jxl = size => Buffer.concat([box('JXL ', Buffer.from([13, 10, 135, 10])), box('ftyp', Buffer.from('jxl ')), box('jxlp', Buffer.alloc(0), size)]);
  for (const size of [0, 1, 8, 11, 0xffffffff]) assert.throws(() => imageSize(jxl(size)));
  const heif = size => Buffer.concat([box('ftyp', Buffer.from('heic')), box('meta', Buffer.concat([Buffer.alloc(4), box('iprp', box('ipco', box('ispe', Buffer.alloc(12), size)))]))]);
  assert.equal(imageSize(heif(20)).width, 0);
  for (const size of [0, 1, 8, 19, 0xffffffff]) assert.throws(() => imageSize(heif(size)));
  for (const sample of [icns(0), jxl(0), heif(0)]) {
    for (let length = 0; length < sample.length; length++) {
      try { imageSize(Uint8Array.from(sample.subarray(0, length))); } catch {}
    }
  }
  console.log('ICNS/JXL/HEIF malformed and truncation probes PASS');
} else if (process.argv[3] === 'schemas') {
  const {z} = require(path.join(root, 'zod-4.6.6/package/index.cjs'));
  const schema = z.object({name: z.string(), args: z.array(z.string()).max(4), enabled: z.boolean().optional()});
  assert.deepEqual(schema.parse({name: 'answer', args: ['42']}), {name: 'answer', args: ['42']});
  assert.equal(schema.safeParse({name: 42, args: [null]}).success, false);
  assert.equal(z.toJSONSchema(schema).properties.args.maxItems, 4);
  let reads = 0;
  const oversized = new Array(4096);
  Object.defineProperty(oversized, 0, {get() { reads++; return 'bad'; }});
  assert.equal(z.array(z.string()).max(4).safeParse(oversized).success, false);
  assert.equal(reads, 0, 'oversized collection rejected before element traversal');
  assert.equal(z.set(z.string()).max(4).safeParse(new Set(Array.from({length: 4096}, (_, i) => String(i)))).success, false);
  console.log('Zod schema/JSON schema and bounded oversized collection probes PASS');
} else {
  for (const mode of ['images', 'schemas']) {
    const r = spawnSync(process.execPath, ['--max-old-space-size=128', __filename, root, mode], {timeout: 5000, encoding: 'utf8'});
    process.stdout.write(r.stdout || ''); process.stderr.write(r.stderr || '');
    assert.ifError(r.error); assert.equal(r.status, 0, mode);
  }
}
