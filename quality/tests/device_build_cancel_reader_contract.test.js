import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const read = path =>
  fs.readFileSync(
    new URL("../../" + path, import.meta.url),
    "utf8"
  );

const shell = read(
  "android-app/app/src/main/java/com/appforge/studio/terminal/LinuxShellEngine.kt"
);

const deviceBuild = read(
  "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"
);

test(
  "device build cancel contains expected reader close instead of crashing AndroidRuntime",
  () => {
    assert.match(
      shell,
      /data class ActiveProcess[\s\S]*closeExpected:\s*AtomicBoolean/
    );

    assert.match(
      shell,
      /AtomicReference<IOException\?>/
    );

    assert.match(
      shell,
      /catch \(\s*io:\s*IOException\s*\)[\s\S]*!activeProcess[\s\S]*closeExpected[\s\S]*readerFailure[\s\S]*compareAndSet/
    );

    assert.match(
      shell,
      /readerFailure[\s\S]*get\(\)[\s\S]*throw it/
    );
  }
);

test(
  "explicit cancel marks pipe close expected before process destroy",
  () => {
    const start =
      shell.indexOf("fun cancel(");

    assert.notEqual(
      start,
      -1
    );

    const body =
      shell.slice(
        start
      );

    const expected =
      body.indexOf(".closeExpected");

    const destroy =
      body.indexOf(".destroy()");

    assert.ok(
      expected >= 0 &&
      destroy >= 0 &&
      expected < destroy
    );
  }
);

test(
  "timeout and coroutine teardown also mark reader close expected",
  () => {
    assert.match(
      shell,
      /if \(!completed\) \{[\s\S]*closeExpected[\s\S]*set\(true\)[\s\S]*process\.destroy\(\)/
    );

    assert.match(
      shell,
      /CancellationException[\s\S]*closeExpected[\s\S]*set\(true\)[\s\S]*destroyForcibly\(\)/
    );
  }
);

test(
  "device build sets cancelled before shell teardown",
  () => {
    const start =
      deviceBuild.indexOf(
        "fun cancel(id: String)"
      );

    assert.notEqual(
      start,
      -1
    );

    const body =
      deviceBuild.slice(
        start,
        start + 900
      );

    const cancelled =
      body.indexOf(
        "if (!state.cancelBuild()) return false"
      );

    const terminal = read("android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildTerminalState.kt");
    assert.match(terminal, /fun cancelBuild\(\): Boolean = synchronized\(this\) \{\s+if \(terminal\(\)\) return false\s+cancelled\.set\(true\)\s+status = "cancelled"/);
    assert.match(body, /synchronized\(state\) \{\s+if \(!state\.cancelBuild\(\)\) return false/);

    const shellCancel =
      body.indexOf(
        "state.shell?.cancel(session)"
      );

    assert.ok(
      cancelled >= 0 &&
      shellCancel >= 0 &&
      cancelled < shellCancel
    );
  }
);
