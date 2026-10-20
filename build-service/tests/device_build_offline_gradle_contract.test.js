import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../.."
);

const engine = fs.readFileSync(
  path.join(repoRoot, "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"),
  "utf8"
);

const installer = fs.readFileSync(
  path.join(repoRoot, "android-app/app/src/main/assets/device-build/install-toolchain.sh"),
  "utf8"
);

test("Gradle receives device offline state", () => {
  assert.match(
    engine,
    /APPFORGE_DEVICE_OFFLINE=\$\{if \(state\.offline\) 1 else 0\} \/opt\/appforge-device\/ensure-gradle/
  );
});

test("offline Gradle does not download missing versions", () => {
  assert.match(
    installer,
    /APPFORGE_OFFLINE_GRADLE_MISSING/
  );

  assert.match(
    installer,
    /valid_cache=1/
  );

  assert.match(
    installer,
    /sha256sum -c -/
  );

  assert.ok(
    installer.indexOf('cat > "$ROOT/ensure-gradle"')
    <
    installer.indexOf('if [ -f "$READY" ]')
  );
});
