import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const read = path =>
  readFile(
    new URL(path, import.meta.url),
    "utf8"
  );

test(
  "device identity stays unique while accounts support multiple devices",
  async () => {
    const legacy =
      await read(
        "../sql/015_single_account_device.sql"
      );

    const multi =
      await read(
        "../sql/016_multi_account_devices.sql"
      );

    assert.match(
      legacy,
      /device_hash TEXT PRIMARY KEY/
    );

    assert.match(
      legacy,
      /ON DELETE SET NULL/
    );

    assert.match(
      multi,
      /DROP CONSTRAINT IF EXISTS appforge_account_devices_user_id_key/
    );

    assert.match(
      multi,
      /idx_account_devices_user_last_seen/
    );
  }
);

test(
  "account device binding supports verified multi-device access",
  async () => {
    const auth =
      await read("../src/auth.js");

    assert.match(
      auth,
      /appforge-user:/
    );

    assert.match(
      auth,
      /appforge-device:/
    );

    assert.match(
      auth,
      /MAX_ACCOUNT_DEVICES/
    );

    assert.match(
      auth,
      /DEVICE_ALREADY_BOUND/
    );

    assert.match(
      auth,
      /DEVICE_ADD_REQUIRED/
    );

    assert.match(
      auth,
      /DEVICE_LIMIT_REACHED/
    );

    assert.doesNotMatch(
      auth,
      /ACCOUNT_BOUND_TO_ANOTHER_DEVICE/
    );

    assert.doesNotMatch(
      auth,
      /30 \* 24 \* 60 \* 60 \* 1000/
    );

    assert.match(
      auth,
      /X-AppForge-Device-ID/
    );
  }
);

test(
  "registration login and device add are device aware",
  async () => {
    const server =
      await read("../server.js");

    assert.match(
      server,
      /requestDeviceId\(req\)/
    );

    assert.match(
      server,
      /bindAccountDevice/
    );

    assert.match(
      server,
      /\/api\/auth\/device-transfer/
    );

    assert.match(
      server,
      /transferAccountDevice/
    );
  }
);

test(
  "Android clients send device identity",
  async () => {
    const files =
      await Promise.all([
        read(
          "../../android-app/app/src/main/java/com/appforge/studio/net/AppForgeAccountClient.kt"
        ),
        read(
          "../../android-app/app/src/main/java/com/appforge/studio/build/BuildApiClient.kt"
        ),
        read(
          "../../android-app/app/src/main/java/com/appforge/studio/net/WorkspaceClient.kt"
        ),
        read(
          "../../android-app/app/src/main/java/com/appforge/studio/security/StudioSecurityClient.kt"
        )
      ]);

    for (const text of files) {
      assert.match(
        text,
        /X-AppForge-Device-ID/
      );
    }
  }
);

test(
  "Android device identity is pseudonymous and reinstall stable",
  async () => {
    const identity =
      await read(
        "../../android-app/app/src/main/java/com/appforge/studio/security/StudioDeviceIdentity.kt"
      );

    assert.match(
      identity,
      /Settings\.Secure\.ANDROID_ID/
    );

    assert.match(
      identity,
      /SHA-256/
    );

    assert.doesNotMatch(
      identity,
      /SharedPreferences/
    );
  }
);
