import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const androidRoot = new URL(
  "../../android-app/app/",
  import.meta.url
);

const source = (name) =>
  readFile(
    new URL(
      `src/main/java/com/appforge/studio/terminal/${name}`,
      androidRoot
    ),
    "utf8"
  );

const manifest = () =>
  readFile(new URL("src/main/AndroidManifest.xml", androidRoot), "utf8");

test("Stage 9A keeps long Linux PTY work on a non-exported specialUse service that survives task removal", async () => {
  const xml = await manifest();
  const linuxService =
    xml.match(
      /<service\s+android:name="\.terminal\.LinuxTerminalJobService"[\s\S]*?<\/service>/
    )?.[0] ?? "";

  assert.match(linuxService, /android:exported="false"/);
  assert.match(linuxService, /android:stopWithTask="false"/);
  assert.match(linuxService, /android:foregroundServiceType="specialUse"/);
  assert.match(linuxService, /PROPERTY_SPECIAL_USE_FGS_SUBTYPE/);
  assert.doesNotMatch(linuxService, /foregroundServiceType="dataSync"/);
});

test("Stage 9A explicitly requests Android notification permission and suppresses result notifications when unavailable", async () => {
  const [permission, panel, service] = await Promise.all([
    source("TerminalNotificationPermission.kt"),
    source("LinuxMultiSessionTerminalPanel.kt"),
    source("LinuxTerminalJobService.kt")
  ]);

  assert.match(permission, /Manifest\.permission\.POST_NOTIFICATIONS/);
  assert.match(permission, /ActivityResultContracts\.RequestPermission/);
  assert.match(permission, /rememberLauncherForActivityResult/);
  assert.match(permission, /NotificationManagerCompat/);
  assert.match(permission, /areNotificationsEnabled\(\)/);
  assert.match(panel, /TerminalNotificationPermissionControl/);
  assert.match(service, /TerminalNotificationPermission\.isGranted/);
});

test("Stage 9A rolls back foreground-start failures instead of leaving ACTIVE metadata behind", async () => {
  const [service, panel] = await Promise.all([
    source("LinuxTerminalJobService.kt"),
    source("LinuxMultiSessionTerminalPanel.kt")
  ]);

  assert.match(service, /runCatching\s*\{\s*showRunningNotifications\(\)/s);
  assert.match(service, /rollbackForegroundStart\(/);
  assert.match(service, /LinuxBackgroundJobStatus\.FAILED/);
  assert.match(service, /LinuxPtySessionRegistry\.terminate\(sessionId\)/);
  assert.match(service, /stopSelf\(startId\)/);
  assert.match(panel, /arka plan koruması başlatılıyor/);
  assert.doesNotMatch(panel, /foreground service ile arka planda korunuyor/);
});

test("Stage 9A timeout and service destruction cannot leave protected PTYs running without foreground protection", async () => {
  const service = await source("LinuxTerminalJobService.kt");

  const timeoutStart = service.indexOf("override fun onTimeout(");
  const destroyStart = service.indexOf("override fun onDestroy()", timeoutStart);
  const beginStart = service.indexOf("private fun beginProtection", destroyStart);
  assert.ok(timeoutStart >= 0 && destroyStart > timeoutStart && beginStart > destroyStart);

  const timeoutBody = service.slice(timeoutStart, destroyStart);
  const destroyBody = service.slice(destroyStart, beginStart);

  assert.match(timeoutBody, /protectedSessions\.clear\(\)/);
  assert.match(timeoutBody, /LinuxBackgroundJobStatus\.TIMED_OUT/);
  assert.match(timeoutBody, /LinuxPtySessionRegistry\.terminate\(sessionId\)/);
  assert.match(timeoutBody, /ServiceCompat\.stopForeground/);
  assert.match(timeoutBody, /stopSelf\(\)/);

  assert.match(destroyBody, /protectedSessions\.clear\(\)/);
  assert.match(destroyBody, /LinuxBackgroundJobStatus\.PROCESS_LOST/);
  assert.match(destroyBody, /LinuxPtySessionRegistry\.terminate\(sessionId\)/);
});

test("Stage 9A prevents fast-exit ghost running state and never terminates a PTY while holding the registry lock", async () => {
  const registry = await source("LinuxPtySessionRegistry.kt");

  assert.match(
    registry,
    /current\.running\s*=\s*current\.exitCode == null/
  );

  const terminateStart = registry.indexOf("fun terminate(");
  const closeStart = registry.indexOf("fun closeSession(", terminateStart);
  assert.ok(terminateStart >= 0 && closeStart > terminateStart);
  const terminateBody = registry.slice(terminateStart, closeStart);

  assert.match(terminateBody, /val session\s*=\s*synchronized\(lock\)/s);
  assert.match(terminateBody, /session\?\.terminate\(\)/);

  const synchronizedEnd = terminateBody.indexOf("\n        // Native/process shutdown");
  const terminateCall = terminateBody.indexOf("session?.terminate()", synchronizedEnd);
  assert.ok(synchronizedEnd > 0 && terminateCall > synchronizedEnd);
});
