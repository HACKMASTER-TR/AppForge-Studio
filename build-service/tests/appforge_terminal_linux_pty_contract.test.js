import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const androidRoot = new URL(
  "../../android-app/app/",
  import.meta.url
);

const readAndroid = (path) =>
  readFile(new URL(path, androidRoot), "utf8");

test("AppForge builds a native PTY bridge with public Android forkpty APIs", async () => {
  const [buildFile, cmake, native] = await Promise.all([
    readAndroid("build.gradle.kts"),
    readAndroid("src/main/cpp/CMakeLists.txt"),
    readAndroid("src/main/cpp/appforge_pty.c")
  ]);

  assert.match(buildFile, /externalNativeBuild/);
  assert.match(buildFile, /src\/main\/cpp\/CMakeLists\.txt/);
  assert.match(cmake, /add_library\([\s\S]*appforge_pty[\s\S]*SHARED/);
  assert.match(native, /forkpty\s*\(/);
  assert.match(native, /TIOCSWINSZ/);
  assert.match(native, /execve\s*\(/);
  assert.match(native, /waitpid\s*\(/);
  assert.doesNotMatch(native, /\bsystem\s*\(/);
  assert.doesNotMatch(native, /\bpopen\s*\(/);
});

test("interactive Linux PTY starts only the verified packaged Proroot launcher", async () => {
  const [session, packaged, runtime, manager] = await Promise.all([
    readAndroid(
      "src/main/java/com/appforge/studio/terminal/InteractiveLinuxPtySession.kt"
    ),
    readAndroid(
      "src/main/java/com/appforge/studio/terminal/PackagedLinuxEngine.kt"
    ),
    readAndroid(
      "src/main/java/com/appforge/studio/terminal/ProrootRuntimeContract.kt"
    ),
    readAndroid(
      "src/main/java/com/appforge/studio/terminal/AndroidLinuxRuntimeManager.kt"
    )
  ]);

  assert.match(session, /packagedEngine[\s\S]*requireLauncher/);
  assert.match(session, /AppForgePtyBridge\.spawn/);
  assert.match(session, /xterm-256color/);
  assert.match(session, /sendControlC/);
  assert.match(session, /session\.resize|AppForgePtyBridge\.resize/);
  assert.doesNotMatch(session, /ProcessBuilder\s*\(/);
  assert.doesNotMatch(session, /\/system\/bin\/sh/);
  assert.match(packaged, /ProrootPinnedRuntime\.assets/);
  assert.match(runtime, /buildInteractiveShellArguments/);
  assert.match(runtime, /"\/bin\/bash"/);
  assert.match(manager, /PackagedLinuxEngine/);
  assert.match(manager, /requireReadyRootfs/);
});

test("ANSI terminal parser and Linux UI support interactive control sequences", async () => {
  const [ansi, panel] = await Promise.all([
    readAndroid(
      "src/main/java/com/appforge/studio/terminal/AnsiTerminalBuffer.kt"
    ),
    readAndroid(
      "src/main/java/com/appforge/studio/terminal/LinuxInteractiveTerminalPanel.kt"
    )
  ]);

  assert.match(ansi, /ParserState\.CSI/);
  assert.match(ansi, /applySgr/);
  assert.match(ansi, /eraseDisplay/);
  assert.match(ansi, /cursorVisible/);
  assert.match(ansi, /ansi256/);
  assert.match(panel, /Ctrl\+C/);
  assert.match(panel, /"\\u001b\[A"/);
  assert.match(panel, /onSizeChanged/);
  assert.match(panel, /session\.resize/);
  assert.match(panel, /apt-get update/);
  assert.match(panel, /python3/);
  assert.match(panel, /node/);
});
