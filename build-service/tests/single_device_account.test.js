import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const read = path =>
  readFile(new URL(path, import.meta.url), "utf8");

test("device binding survives account deletion and is unique", async () => {
  const sql = await read("../sql/015_single_account_device.sql");

  assert.match(sql, /device_hash TEXT PRIMARY KEY/);
  assert.match(sql, /user_id UUID UNIQUE/);
  assert.match(sql, /ON DELETE SET NULL/);
  assert.equal(sql.trimStart().startsWith("CREATE TABLE"), true);
});

test("account device binding is atomic and rejects account sharing", async () => {
  const auth = await read("../src/auth.js");

  assert.match(auth, /appforge-device:/);
  assert.match(auth, /DEVICE_ALREADY_BOUND/);
  assert.match(auth, /ACCOUNT_BOUND_TO_ANOTHER_DEVICE/);
  assert.match(auth, /30 \* 24 \* 60 \* 60 \* 1000/);
  assert.match(auth, /X-AppForge-Device-ID/);
});

test("registration login and transfer are device aware", async () => {
  const server = await read("../server.js");

  assert.match(server, /requestDeviceId\(req\)/);
  assert.match(server, /bindAccountDevice/);
  assert.match(server, /\/api\/auth\/device-transfer/);
  assert.match(server, /transferAccountDevice/);
});

test("Android account build workspace and Pro clients send device identity", async () => {
  const files = await Promise.all([
    read("../../android-app/app/src/main/java/com/appforge/studio/net/AppForgeAccountClient.kt"),
    read("../../android-app/app/src/main/java/com/appforge/studio/build/BuildApiClient.kt"),
    read("../../android-app/app/src/main/java/com/appforge/studio/net/WorkspaceClient.kt"),
    read("../../android-app/app/src/main/java/com/appforge/studio/security/StudioSecurityClient.kt")
  ]);

  for (const text of files) {
    assert.match(text, /X-AppForge-Device-ID/);
  }
});

test("Android device identity is pseudonymous and reinstall stable", async () => {
  const identity = await read(
    "../../android-app/app/src/main/java/com/appforge/studio/security/StudioDeviceIdentity.kt"
  );

  assert.match(identity, /Settings\.Secure\.ANDROID_ID/);
  assert.match(identity, /SHA-256/);
  assert.doesNotMatch(identity, /SharedPreferences/);
});
