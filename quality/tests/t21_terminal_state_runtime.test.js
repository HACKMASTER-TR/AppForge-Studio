import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../../', import.meta.url));
const source = path.join(root, 'android-app/app/src/main/java/com/appforge/studio/build');

test('real Kotlin job transitions serialize cancel, success, failure and artifact visibility', () => {
  const dir = fs.mkdtempSync(path.join(root, '.t21-terminal-'));
  try {
    fs.writeFileSync(path.join(dir, 'Check.kt'), `package com.appforge.studio.build
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
fun main() {
 fun ordered(first: (DeviceBuildTerminalState)->Unit, second: (DeviceBuildTerminalState)->Unit): DeviceBuildTerminalState {
  val state = DeviceBuildTerminalState().apply { advance(73,"building") }
  val ready = CountDownLatch(1); val committed = CountDownLatch(1)
  val failure = AtomicReference<Throwable?>()
  val contender = Thread {
   try { ready.countDown(); check(committed.await(5, TimeUnit.SECONDS)); second(state) }
   catch (t: Throwable) { failure.set(t) }
  }
  contender.start(); check(ready.await(5, TimeUnit.SECONDS))
  try { first(state) } finally { committed.countDown() }
  contender.join(5000); check(!contender.isAlive); failure.get()?.let { throw it }
  return state
 }
 // Model the engine's separate candidate assignments; use the real production
 // visibility predicate under the same monitor as snapshot(), not Android APIs.
 class Outputs : DeviceBuildTerminalState() {
  var apk: String? = null; var aab: String? = null
  fun snapshot() = synchronized(this) {
   Pair(apk.takeIf { artifactsVisible() }, aab.takeIf { artifactsVisible() })
  }
 }
 for (outcome in listOf("success", "cancel", "fail")) {
  val outputs = Outputs()
  outputs.apk = "candidate.apk"; check(outputs.snapshot() == Pair(null,null))
  outputs.aab = "candidate.aab"; check(outputs.snapshot() == Pair(null,null))
  when(outcome) {
   "success" -> check(outputs.complete("SUCCESS"))
   "cancel" -> { check(outputs.cancelBuild()); check(!outputs.complete("SUCCESS")) }
   else -> { check(outputs.fail("FAILURE")); check(!outputs.complete("SUCCESS")) }
  }
  check(outputs.snapshot() == if(outcome == "success") Pair("candidate.apk","candidate.aab") else Pair(null,null))
 }
 repeat(100) {
  val cancelled = ordered({ check(it.cancelBuild()) }, { check(!it.complete("SUCCESS")) })
  check(cancelled.status == "cancelled" && cancelled.progress == 73)
  check(!cancelled.artifactsVisible() && "SUCCESS" !in cancelled.logs)
  check(!cancelled.cancelBuild()); check(!cancelled.fail("FAILURE"))
  cancelled.advance(99,"building"); check(cancelled.status == "cancelled")
  check(cancelled.logs.size == 1)
  val success = ordered({ check(it.complete("SUCCESS")) }, { check(!it.cancelBuild()) })
  check(success.status == "success" && success.progress == 100 && !success.cancelled.get())
  check(success.artifactsVisible()); check(!success.fail("FAILURE")); check(!success.complete("DUPLICATE"))
  success.advance(50,"building"); check(success.status == "success" && success.logs == listOf("SUCCESS"))
  val failed = ordered({ check(it.fail("FAILURE")) }, { check(!it.complete("SUCCESS")) })
  check(failed.status == "failed" && failed.progress == 73 && !failed.artifactsVisible())
  check(!failed.cancelBuild()); failed.advance(99,"building"); check(failed.status == "failed")
  val cancelThenFail = ordered({ check(it.cancelBuild()) }, { check(!it.fail("FAILURE")) })
  check(cancelThenFail.status == "cancelled" && cancelThenFail.logs.size == 1)
  val failThenCancel = ordered({ check(it.fail("FAILURE")) }, { check(!it.cancelBuild()) })
  check(failThenCancel.status == "failed")
  ordered({ check(it.cancelBuild()) }, { check(!it.cancelBuild()) })
 }
 println("600 latch-ordered production transition scenarios passed")
}`);
    execFileSync('kotlinc', [path.join(source, 'DeviceBuildTerminalState.kt'), path.join(dir, 'Check.kt'), '-include-runtime', '-d', path.join(dir, 'check.jar')], { timeout: 120000, stdio: 'pipe' });
    const output = execFileSync('java', ['-jar', path.join(dir, 'check.jar')], { timeout: 30000, encoding: 'utf8' });
    assert.match(output, /600 latch-ordered/);
  } finally { fs.rmSync(dir, { recursive: true, force: true }); }
});

test('Android engine wires both completion routes and all artifacts to terminal guards', () => {
  const engine = fs.readFileSync(path.join(source, 'DeviceBuildEngine.kt'), 'utf8');
  assert.match(engine, /\) : DeviceBuildTerminalState\(\)/);
  assert.equal((engine.match(/state\.complete\(/g) || []).length, 2);
  assert.doesNotMatch(engine, /state\.status\s*=/);
  assert.doesNotMatch(engine, /state\.progress = 100/);
  assert.match(engine, /state\.fail\(/);
  assert.match(engine, /if \(!state\.cancelBuild\(\)\) return false/);
  assert.match(engine, /exe = state\.exe\.takeIf \{ state\.artifactsVisible\(\) \}/);
  const artifact = engine.slice(engine.indexOf('fun artifact('), engine.indexOf('fun cancel('));
  assert.ok(artifact.indexOf('if (state.status != "success" || state.cancelled.get()) return null') < artifact.indexOf('val file ='));
  assert.match(artifact, /DeviceArtifactKind.WINDOWS_EXE/);
  assert.match(artifact, /DeviceArtifactKind.WINDOWS_NATIVE_EXE/);
});
