// Opt-in regression; pass the absolute installed node-forge directory.
const assert = require('node:assert/strict');
const path = require('node:path');
assert.ok(path.isAbsolute(process.argv[2] || ''), 'explicit absolute module path required');
const forge = require(process.argv[2]);
assert.equal(require(path.join(process.argv[2], 'package.json')).version, '1.4.2');
const keys = forge.pki.rsa.generateKeyPair({bits: 1024, e: 3});
const md = forge.md.sha256.create().update('AppForge residual signature regression');
const digest = md.digest().getBytes();
const signature = keys.privateKey.sign(md);
assert.equal(keys.publicKey.verify(digest, signature), true);
assert.equal(keys.publicKey.verify(forge.md.sha256.create().update('tampered').digest().getBytes(), signature), false);
const a = forge.asn1;
const U = a.Class.UNIVERSAL;
const algorithm = a.create(U, a.Type.SEQUENCE, true, [
  a.create(U, a.Type.OID, false, a.oidToDer(forge.pki.oids.sha256).getBytes()),
  a.create(U, a.Type.NULL, false, ''),
  a.create(U, a.Type.NULL, false, '') // forbidden extra DigestAlgorithm element
]);
const info = a.create(U, a.Type.SEQUENCE, true, [algorithm,
  a.create(U, a.Type.OCTETSTRING, false, digest)]);
const malformed = keys.privateKey.sign(a.toDer(info).getBytes(), 'NONE');
let rejected = false;
try { rejected = keys.publicKey.verify(digest, malformed) === false; }
catch (error) { rejected = /ASN.1|DigestInfo|RSASSA/.test(error.message); }
assert.equal(rejected, true, 'extra nested DigestAlgorithm element must be rejected');
const pem = forge.pki.publicKeyToPem(keys.publicKey);
assert.equal(forge.pem.decode(Buffer.from(pem))[0].type, 'PUBLIC KEY');
assert.equal(forge.pki.publicKeyFromPem(pem).e.toString(), '3');
console.log('PASS: valid signature, tampered message, malformed DigestAlgorithm, PEM Buffer compatibility');
