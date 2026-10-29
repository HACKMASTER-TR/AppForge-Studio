import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
const root = fileURLToPath(new URL('../../', import.meta.url));
const src = path.join(root, 'android-app/app/src/main/java/com/appforge/studio/build/OfflineToolCatalog.kt');
test('real Kotlin offline catalog validates identity, licenses, ABI and cache without mutation', {timeout:120000}, () => {
  assert.ok(fs.existsSync(src));
  const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'appforge-v10-catalog-'));
  try {
    fs.writeFileSync(path.join(temp, 'Check.kt'), `package com.appforge.studio.build
import java.io.File
import java.security.MessageDigest
fun main(args: Array<String>) {
 val cache = File(args[0]); cache.mkdirs()
 fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
 val body = "reviewed fixture payload".toByteArray(); val license = "synthetic MIT notice".toByteArray()
 val p = OfflineToolPackage("fixture", "1.0", "aarch64-linux-android", sha(body), "https://packages.termux.dev/fixture", "MIT", sha(license), body.size.toLong())
 val manifest = OfflineToolCatalog.encode(listOf(p))
 check(OfflineToolCatalog.decode(manifest) == listOf(p))
 fun verify(pack: OfflineToolPackage = p) = OfflineToolCatalog.inspect(listOf(pack), cache, p.abi)
 check(!verify().complete)
 File(cache, p.sha256).writeBytes(body)
 check(!verify().complete)
 File(cache,"licenses").mkdirs(); File(cache,"licenses/" + p.licenseSha256).writeBytes(license)
 check(verify().complete)
 val snapshot = cache.walkTopDown().filter { it.isFile }.associate { it.relativeTo(cache).path to sha(it.readBytes()) }
 check(!OfflineToolCatalog.inspect(listOf(p), cache, "aarch64-linux-gnu").complete)
 check(!verify(p.copy(size = p.size + 1)).complete)
 File(cache, p.sha256).writeText("wrong"); check(!verify().complete); File(cache,p.sha256).writeBytes(body)
 for (bad in listOf(p.copy(name="../escape"), p.copy(license="UNKNOWN"), p.copy(sha256="../escape"), p.copy(upstream="http://example.org"), p.copy(size=-1))) {
  check(runCatching { OfflineToolCatalog.encode(listOf(bad)) }.isFailure)
 }
 check(runCatching { OfflineToolCatalog.decode(manifest + manifest) }.isFailure)
 val linked = File(cache, "linked"); java.nio.file.Files.createSymbolicLink(linked.toPath(), cache.toPath())
 check(runCatching { OfflineToolCatalog.inspect(listOf(p), linked, p.abi) }.isFailure)
 check(cache.walkTopDown().filter { it.isFile && !it.path.contains("linked") }.associate { it.relativeTo(cache).path to sha(it.readBytes()) } == snapshot)
 println("V10 catalog behavioral checks passed")
}`);
    execFileSync('kotlinc', [src, path.join(temp,'Check.kt'), '-include-runtime', '-d', path.join(temp,'check.jar')], {timeout:90000,stdio:'pipe'});
    assert.match(execFileSync('java', ['-jar',path.join(temp,'check.jar'),path.join(temp,'cache')], {encoding:'utf8', timeout:15000}), /behavioral checks passed/);
  } finally { fs.rmSync(temp, {recursive:true,force:true}); }
});
