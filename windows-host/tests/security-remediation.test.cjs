"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const AdmZip = require("adm-zip");

test("ZIP decompression rejects a zero declared size before expanding entry content", () => {
  const zip = new AdmZip();
  zip.addFile("bounded.txt", Buffer.alloc(128 * 1024, 65));
  const bytes = zip.toBuffer();
  const central = bytes.indexOf(Buffer.from([0x50, 0x4b, 0x01, 0x02]));
  assert.ok(central > 0);
  assert.equal(bytes.readUInt16LE(central + 10), 8);
  bytes.writeUInt32LE(0, central + 24);
  bytes.writeUInt32LE(0, 22);
  const entry = new AdmZip(bytes).getEntry("bounded.txt");
  assert.throws(() => entry.getData());
});

test("ordinary compressed ZIP entries still round trip", () => {
  const zip = new AdmZip();
  const content = Buffer.from("AppForge offline payload\n".repeat(128));
  zip.addFile("index.html", content);
  assert.deepEqual(new AdmZip(zip.toBuffer()).getEntry("index.html").getData(), content);
});
