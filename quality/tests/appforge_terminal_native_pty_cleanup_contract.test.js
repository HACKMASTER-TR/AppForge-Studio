import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const nativePath =
  "../../android-app/app/src/main/cpp/appforge_pty.c";

const readNative = () =>
  readFile(
    new URL(nativePath, import.meta.url),
    "utf8"
  );

test(
  "native PTY spawn failures terminate and reap unreturned children",
  async () => {
    const source = await readNative();

    assert.match(
      source,
      /static void terminate_and_reap_child\(pid_t pid\)/
    );

    assert.match(
      source,
      /kill\(-pid,\s*SIGKILL\)/
    );

    assert.match(
      source,
      /kill\(pid,\s*SIGKILL\)/
    );

    assert.match(
      source,
      /waitpid\(pid,\s*&status,\s*0\)/
    );

    assert.match(
      source,
      /result == -1 && errno == EINTR/
    );

    assert.equal(
      (
        source.match(
          /terminate_and_reap_child\(pid\);/g
        ) || []
      ).length,
      3
    );
  }
);

test(
  "normal PTY terminate keeps graceful SIGHUP and SIGTERM behavior",
  async () => {
    const source = await readNative();

    const start = source.indexOf(
      "Java_com_appforge_studio_terminal_AppForgePtyBridge_nativeTerminate"
    );

    assert.notEqual(
      start,
      -1,
      "nativeTerminate bulunamadı"
    );

    const terminate = source.slice(start);

    assert.match(
      terminate,
      /kill\(-\(pid_t\) process_id,\s*SIGHUP\)/
    );

    assert.match(
      terminate,
      /kill\(-\(pid_t\) process_id,\s*SIGTERM\)/
    );

    assert.match(
      terminate,
      /kill\(\(pid_t\) process_id,\s*SIGTERM\)/
    );

    assert.doesNotMatch(
      terminate,
      /SIGKILL/
    );
  }
);
