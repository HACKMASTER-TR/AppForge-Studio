import assert from "node:assert/strict";
import test from "node:test";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repo =
  path.resolve(
    path.dirname(
      fileURLToPath(
        import.meta.url
      )
    ),
    "../.."
  );

const matrix =
  JSON.parse(
    fs.readFileSync(
      path.join(
        repo,
        "quality/acceptance/device_offline_acceptance_matrix.json"
      ),
      "utf8"
    )
  );

const capabilities =
  fs.readFileSync(
    path.join(
      repo,
      "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildCapabilities.kt"
    ),
    "utf8"
  );


test(
  "offline acceptance matrix has a versioned bounded scope",
  () => {
    assert.equal(
      matrix.schemaVersion,
      1
    );

    assert.match(
      matrix.scope,
      /does not imply arbitrary imported dependency sets/
    );

    const ids =
      matrix
        .androidFixtures
        .map(
          item =>
            item.id
        );

    assert.equal(
      new Set(
        ids
      ).size,
      ids.length
    );
  }
);


test(
  "all currently READY Android device engines have explicit acceptance evidence",
  () => {
    const tracked =
      new Set(
        matrix
          .androidFixtures
          .map(
            item =>
              item.engine
          )
      );

    for (
      const engine of [
        "webview-static",
        "node-web",
        "android-gradle",
        "python-android",
        "expo"
      ]
    ) {
      assert.equal(
        tracked.has(
          engine
        ),
        true,
        `missing acceptance entry for ${engine}`
      );

      const start =
        capabilities.indexOf(
          `engine =\n                    "${engine}"`
        );

      assert.notEqual(
        start,
        -1
      );

      const block =
        capabilities.slice(
          start,
          start + 1500
        );

      assert.match(
        block,
        /DeviceBuildSupport\.READY/
      );
    }
  }
);


test(
  "proven non-Expo Android fixtures retain physical offline APK and AAB PASS",
  () => {
    for (
      const id of [
        "static-html-js",
        "react-vite",
        "native-java",
        "native-kotlin",
        "python-chaquopy"
      ]
    ) {
      const item =
        matrix
          .androidFixtures
          .find(
            entry =>
              entry.id === id
          );

      assert.ok(
        item,
        `missing ${id}`
      );

      assert.equal(
        item.offlineApk,
        "PASS"
      );

      assert.equal(
        item.offlineAab,
        "PASS"
      );
    }
  }
);


test(
  "Expo SDK54 controlled internet-off APK AAB and runtime acceptance is closed",
  () => {
    const expo =
      matrix
        .androidFixtures
        .find(
          entry =>
            entry.id ===
            "expo-sdk54-rn0814"
        );

    assert.equal(
      expo.deviceApk,
      "PASS"
    );

    assert.equal(
      expo.deviceAab,
      "PASS"
    );

    assert.equal(
      expo.offlineApk,
      "PASS"
    );

    assert.equal(
      expo.offlineAab,
      "PASS"
    );

    assert.equal(
      expo.deviceRuntimeEvidence,
      "APPFORGE_EXPO54_DEVICE_PASS"
    );

    assert.equal(
      expo.runtimeEvidence,
      "APPFORGE_EXPO54_OFFLINE_PASS"
    );

    assert.equal(
      expo.onlinePrimeBuildNo,
      "AF-0000001018"
    );

    assert.equal(
      expo.offlineBuildNo,
      "AF-0000001019"
    );

    assert.equal(
      expo.offlineBuildDuration,
      "03:07"
    );

    assert.equal(
      expo.fixtureSha256,
      "0804ac6f58d9419f211e24e1f812eb95a05910fa70b261e98792718fb1916833"
    );

    assert.match(
      expo.note,
      /does not imply arbitrary cold dependency sets/
    );
  }
);


test(
  "Windows EXE acceptance keeps V23 physical persistence pending",
  () => {
    const windows =
      matrix.windowsPortable;

    assert.deepEqual(
      windows.supportedEngines,
      [
        "webview-static",
        "node-web"
      ]
    );

    assert.equal(
      windows.deviceLocalOfflinePackaging,
      "PASS"
    );

    assert.equal(
      windows.physicalWindowsExecution,
      "PASS"
    );

    assert.equal(
      windows.runtimeEvidence,
      "JAVASCRIPT_OK"
    );

    assert.equal(
      windows.v23CiPersistentProfileRelaunch,
      "PASS"
    );

    assert.equal(
      windows.v23PhysicalPersistentProfileRelaunch,
      "PENDING"
    );

    assert.equal(
      windows.v23PhysicalSameAppExeUpdatePersistence,
      "PENDING"
    );
  }
);


test(
  "unaccepted future engines remain non-READY",
  () => {
    for (
      const item of
        matrix.notAcceptedOffline
    ) {
      const start =
        capabilities.indexOf(
          `engine =\n                    "${item.engine}"`
        );

      assert.notEqual(
        start,
        -1,
        `missing capability ${item.engine}`
      );

      const block =
        capabilities.slice(
          start,
          start + 1500
        );

      assert.match(
        block,
        new RegExp(
          `DeviceBuildSupport\\.${item.support}`
        )
      );
    }
  }
);
