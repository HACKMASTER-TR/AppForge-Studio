import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const adapterUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/terminal/TermuxTerminalCoreAdapter.kt",
  import.meta.url
);

const nativeUrl = new URL(
  "../../android-app/termux-terminal-emulator/src/main/jni/termux.c",
  import.meta.url
);

test("Termux mirror passes a real argv[0] before shell options", async () => {
  const source = await readFile(adapterUrl, "utf8");
  const start = source.indexOf("internal fun TermuxTerminalMirrorHost(");
  assert.ok(start >= 0, "TermuxTerminalMirrorHost missing");

  const mirror = source.slice(start);
  assert.match(
    mirror,
    /arguments\s*=\s*listOf\(\s*"\/system\/bin\/sh",\s*"-c",\s*"stty raw -echo 2>\/dev\/null; exec \/system\/bin\/cat"/s
  );
  assert.doesNotMatch(
    mirror,
    /arguments\s*=\s*listOf\(\s*"-c",/s
  );
});

test("vendored Termux JNI forwards argv exactly to execvp", async () => {
  const source = await readFile(nativeUrl, "utf8");
  assert.match(source, /execvp\(cmd,\s*argv\);/);
  assert.match(
    source,
    /argv\[i\]\s*=\s*strdup\(arg_utf8\);/
  );
});

test("launch spec documents full execvp argv semantics", async () => {
  const source = await readFile(adapterUrl, "utf8");
  assert.match(
    source,
    /Full execvp argv\. Element 0 is argv\[0\], not the first shell option\./
  );
});
