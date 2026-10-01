import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const read = async (path) =>
  readFile(
    new URL(
      `../../${path}`,
      import.meta.url
    ),
    "utf8"
  );


test(
  "portable host persists Chromium state outside temporary runtime",
  async () => {
    const main =
      await read(
        "windows-host/main.cjs"
      );

    for (
      const marker of [
        "LOCALAPPDATA",
        "AppForgePortable",
        '"userData"',
        '"sessionData"',
        "persistentUserData",
        "persistentSessionData"
      ]
    ) {
      assert.ok(
        main.includes(
          marker
        ),
        marker
      );
    }

    assert.doesNotMatch(
      main,
      /app\.setPath\([\s\S]{0,80}"userData"[\s\S]{0,120}runtimeRoot/
    );
  }
);


test(
  "portable local apps use a stable privileged appforge origin",
  async () => {
    const main =
      await read(
        "windows-host/main.cjs"
      );

    for (
      const marker of [
        "protocol.registerSchemesAsPrivileged",
        "standard:",
        "secure:",
        "supportFetchAPI:",
        "allowServiceWorkers:",
        "protocol.handle(",
        "appforge",
        "localAppUrl(",
        "pathToFileURL"
      ]
    ) {
      assert.ok(
        main.includes(
          marker
        ),
        marker
      );
    }

    assert.equal(
      main.includes(
        "window.loadFile("
      ),
      false,
      "LOCAL portable projects must not depend on changing file:// origins"
    );
  }
);


test(
  "portable host smoke payload exercises localStorage and IndexedDB",
  async () => {
    const smoke =
      await read(
        "windows-host/scripts/append-smoke-payload.cjs"
      );

    assert.ok(
      smoke.includes(
        "localStorage"
      )
    );

    assert.ok(
      smoke.includes(
        "indexedDB"
      )
    );

    assert.ok(
      smoke.includes(
        "__APPFORGE_SMOKE_STATE__"
      )
    );
  }
);


test(
  "Windows CI proves persistence across relaunch and EXE relocation",
  async () => {
    const workflow =
      await read(
        ".github/workflows/windows-portable-host.yml"
      );

    for (
      const marker of [
        "PORTABLE_STABLE_ORIGIN=PASS",
        "PORTABLE_LOCALSTORAGE_PERSISTENCE=PASS",
        "PORTABLE_INDEXEDDB_PERSISTENCE=PASS",
        "PORTABLE_RELAUNCH_PERSISTENCE=PASS",
        "PORTABLE_RELOCATION_PERSISTENCE=PASS",
        "PORTABLE_RUNTIME_CLEANUP=PASS"
      ]
    ) {
      assert.ok(
        workflow.includes(
          marker
        ),
        marker
      );
    }
  }
);

test(
  "portable host flushes DOM storage for forced-termination durability",
  async () => {
    const main =
      await read(
        "windows-host/main.cjs"
      );

    for (
      const marker of [
        "STORAGE_DURABILITY_FLUSH_MS",
        "flushStorageData",
        "startStorageDurabilityFlush",
        "timer.unref"
      ]
    ) {
      assert.ok(
        main.includes(
          marker
        ),
        marker
      );
    }

    assert.match(
      main,
      /webContents[\s\S]*session[\s\S]*flushStorageData\(\)/
    );

    assert.match(
      main,
      /startStorageDurabilityFlush\(\s*window\s*\)/
    );
  }
);


test(
  "Windows CI proves forced-termination recovery for localStorage and IndexedDB",
  async () => {
    const workflow =
      await read(
        ".github/workflows/windows-portable-host.yml"
      );

    for (
      const marker of [
        "PORTABLE_FORCED_TERMINATION=PASS",
        "PORTABLE_CRASH_LOCALSTORAGE_RECOVERY=PASS",
        "PORTABLE_CRASH_INDEXEDDB_RECOVERY=PASS",
        "PORTABLE_CRASH_RECOVERY=PASS"
      ]
    ) {
      assert.ok(
        workflow.includes(
          marker
        ),
        marker
      );
    }
  }
);
