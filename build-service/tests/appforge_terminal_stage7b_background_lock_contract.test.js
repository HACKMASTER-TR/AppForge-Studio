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

const manifest = () =>
  readFile(new URL("src/main/AndroidManifest.xml", androidRoot), "utf8");

test("Stage 7B uses a user-started specialUse foreground service for long Linux PTY work", async () => {
  const [service, xml] = await Promise.all([
    source("terminal/LinuxTerminalJobService.kt"),
    manifest()
  ]);

  assert.match(xml, /FOREGROUND_SERVICE_SPECIAL_USE/);
  assert.match(xml, /foregroundServiceType="specialUse"/);
  assert.match(xml, /PROPERTY_SPECIAL_USE_FGS_SUBTYPE/);
  const linuxService =
    xml.match(
      /<service\s+android:name="\.terminal\.LinuxTerminalJobService"[\s\S]*?<\/service>/
    )?.[0] ?? "";

  assert.match(linuxService, /foregroundServiceType="specialUse"/);
  assert.doesNotMatch(linuxService, /foregroundServiceType="dataSync"/);

  assert.match(service, /ContextCompat\.startForegroundService/);
  assert.match(service, /ServiceCompat\.startForeground/);
  assert.match(service, /FOREGROUND_SERVICE_TYPE_SPECIAL_USE/);
  assert.match(service, /START_NOT_STICKY/);
  assert.match(service, /ACTION_CANCEL/);
});

test("Stage 7B implements Android 15+ foreground timeout shutdown", async () => {
  const service = await source("terminal/LinuxTerminalJobService.kt");

  assert.match(service, /override fun onTimeout\(/);
  assert.match(service, /LinuxBackgroundJobStatus\.TIMED_OUT/);
  assert.match(service, /LinuxPtySessionRegistry\.terminate/);
  assert.match(service, /ServiceCompat\.stopForeground/);
  assert.match(service, /stopSelf\(\)/);
});

test("background metadata restore never claims killed work resumed", async () => {
  const store = await source("terminal/LinuxBackgroundJobStore.kt");

  assert.match(store, /LinuxBackgroundJobStatus\.PROCESS_LOST/);
  assert.match(store, /value\.status == LinuxBackgroundJobStatus\.ACTIVE/);
  assert.match(store, /value\.status == LinuxBackgroundJobStatus\.CANCELLING/);
  assert.match(store, /workspaceFingerprint/);

  const persistStart = store.indexOf("private fun persistLocked");
  const pruneStart = store.indexOf("private fun pruneLocked");
  assert.ok(persistStart >= 0 && pruneStart > persistStart);
  const persisted = store.slice(persistStart, pruneStart);

  assert.doesNotMatch(
    persisted,
    /command|terminalOutput|snapshot|plainText|accessToken|password|privateKey|workspacePath/
  );
});

test("Terminal startup lock is optional and uses the existing biometric guard", async () => {
  const [lock, workspace, security] = await Promise.all([
    source("terminal/TerminalStartupBiometricLock.kt"),
    source("terminal/TerminalWorkspaceScreen.kt"),
    source("terminal/TerminalSecurityCenterPanel.kt")
  ]);

  assert.match(lock, /TerminalStartupLockPreferences/);
  assert.match(lock, /TerminalBiometricGuard\.authenticate/);
  assert.match(lock, /biometric_enabled_v1/);
  assert.match(workspace, /TerminalStartupBiometricGate/);
  assert.match(workspace, /startupAccessGranted/);
  assert.match(security, /TerminalStartupLockSettingsPanel/);
});

test("Linux terminal accepts direct IME typing and paste without a command field", async () => {
  const panel = await source("terminal/LinuxMultiSessionTerminalPanel.kt");

  assert.match(panel, /BasicTextField/);
  assert.match(panel, /terminalImeDelta/);
  assert.match(panel, /LinuxPtySessionRegistry\s*\.write/);
  assert.match(panel, /Terminale dokun/);
  assert.doesNotMatch(panel, /Linux komutu \/ giriş/);
  assert.doesNotMatch(panel, /Text\("Gönder"\)/);
});

test("foreground completion owns result notifications without duplicate PTY notifications", async () => {
  const [service, registry] = await Promise.all([
    source("terminal/LinuxTerminalJobService.kt"),
    source("terminal/LinuxPtySessionRegistry.kt")
  ]);

  assert.match(service, /LinuxBackgroundJobStatus\.COMPLETED/);
  assert.match(service, /LinuxBackgroundJobStatus\.FAILED/);
  assert.match(service, /LinuxBackgroundJobStatus\.CANCELLED/);
  assert.match(service, /postFinalNotification/);
  assert.match(registry, /!LinuxBackgroundJobStore\s*\.isActiveSession/);
});
