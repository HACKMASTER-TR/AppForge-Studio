import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const read = (path) =>
  readFile(new URL(`../../${path}`, import.meta.url), "utf8");

test("Stage 10K opens main Terminal after verified base Linux without hard-gating on apt", async () => {
  const manager = await read(
    "android-app/app/src/main/java/com/appforge/studio/terminal/AndroidLinuxRuntimeManager.kt"
  );
  const coordinator = await read(
    "android-app/app/src/main/java/com/appforge/studio/terminal/TerminalDevelopmentEnvironment.kt"
  );

  assert.match(manager, /suspend fun ensureBaseEnvironment/);
  assert.match(manager, /suspend fun ensureDevelopmentTools/);
  assert.match(manager, /baseEnvironmentMutex\.withLock/);
  assert.match(manager, /developmentToolsMutex\.withLock/);
  assert.match(manager, /developmentProfileCommand/);

  assert.match(coordinator, /manager\.ensureBaseEnvironment/);
  assert.doesNotMatch(coordinator, /manager\.ensureDevelopmentEnvironment/);
  assert.match(coordinator, /startDevelopmentToolsInBackground/);
  assert.match(coordinator, /toolsAttempted/);
  assert.match(coordinator, /Developer tools background setup failed; base terminal remains available/);

  const oldHardGate =
    /\.ready\s*&&[\s\S]*?developmentProfileReady/;
  assert.doesNotMatch(coordinator, oldHardGate);
});

test("Stage 10K resolves rootfs hard-link chains before failing safely", async () => {
  const installer = await read(
    "android-app/app/src/main/java/com/appforge/studio/terminal/VerifiedLinuxRootfsInstaller.kt"
  );

  assert.match(installer, /unresolvedHardLinks/);
  assert.match(installer, /while \(unresolvedHardLinks\.isNotEmpty\(\)\)/);
  assert.match(installer, /resolvedThisPass/);
  assert.match(installer, /if \(!source\.exists\(\)\) \{\s*continue\s*\}/);
  assert.match(installer, /check\(resolvedThisPass > 0\)/);
  assert.match(installer, /Files\.createLink/);
  assert.match(installer, /Files\.copy/);
  assert.match(installer, /MAX_TOTAL_EXTRACTED_BYTES/);
});
