import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const androidRoot = new URL(
  "../../android-app/app/",
  import.meta.url
);

const source = (path) =>
  readFile(
    new URL(`src/main/java/com/appforge/studio/${path}`, androidRoot),
    "utf8"
  );

test("Linux PTY registry owns multiple sessions outside Compose disposal", async () => {
  const [registry, panel] = await Promise.all([
    source("terminal/LinuxPtySessionRegistry.kt"),
    source("terminal/LinuxMultiSessionTerminalPanel.kt")
  ]);

  assert.match(registry, /MAX_SESSIONS\s*=\s*6/);
  assert.match(registry, /MutableStateFlow/);
  assert.match(registry, /InteractiveLinuxPtySession/);
  assert.match(registry, /fun createSession/);
  assert.match(registry, /suspend fun start/);
  assert.match(registry, /suspend fun resize/);
  assert.match(registry, /fun closeSession/);
  assert.doesNotMatch(panel, /DisposableEffect[\s\S]*session\.close/);
});

test("Linux session descriptors restore without pretending killed PTYs are alive", async () => {
  const registry = await source("terminal/LinuxPtySessionRegistry.kt");

  assert.match(registry, /getSharedPreferences/);
  assert.match(registry, /restoreDescriptorsLocked/);
  assert.match(registry, /restored = true/);
  assert.match(registry, /running = false/);
  assert.match(registry, /starting = false/);

  const persistStart = registry.indexOf("private fun persistLocked");
  const publishStart = registry.indexOf("private fun publishLocked");
  assert.ok(persistStart >= 0 && publishStart > persistStart);
  const persist = registry.slice(persistStart, publishStart);
  assert.doesNotMatch(persist, /snapshot|plainText\(\)/);
});

test("Linux terminal supports split view and completion notifications", async () => {
  const [panel, notifier, runtime] = await Promise.all([
    source("terminal/LinuxMultiSessionTerminalPanel.kt"),
    source("terminal/LinuxSessionNotifier.kt"),
    source("terminal/LinuxRuntimePanel.kt")
  ]);

  assert.match(panel, /splitEnabled/);
  assert.match(panel, /secondarySessionId/);
  assert.match(panel, /"Bölünmüş"/);
  assert.match(panel, /sendControlC/);
  assert.match(panel, /setNotifyOnCompletion/);
  assert.match(notifier, /NotificationCompat\.Builder/);
  assert.match(notifier, /POST_NOTIFICATIONS/);
  assert.match(notifier, /"appforge-linux-terminal"/);
  assert.match(runtime, /LinuxMultiSessionTerminalPanel/);
});

test("persisted Linux session metadata excludes terminal output and secrets", async () => {
  const registry = await source("terminal/LinuxPtySessionRegistry.kt");

  const persistStart = registry.indexOf("private fun persistLocked");
  const publishStart = registry.indexOf("private fun publishLocked");
  assert.ok(persistStart >= 0 && publishStart > persistStart);
  const persist = registry.slice(persistStart, publishStart);

  assert.match(persist, /"workspace"/);
  assert.match(persist, /"distribution"/);
  assert.match(persist, /"notify"/);
  assert.doesNotMatch(persist, /accessToken|password|privateKey|plainText\(\)|snapshot/);
  assert.match(registry, /TerminalSecretMasker\.redact/);
});
