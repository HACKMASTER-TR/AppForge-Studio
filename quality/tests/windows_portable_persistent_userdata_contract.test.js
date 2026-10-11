import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const main = await readFile(
  new URL(
    "../../windows-host/main.cjs",
    import.meta.url
  ),
  "utf8"
);

const storage = await readFile(
  new URL(
    "../../windows-host/storage.cjs",
    import.meta.url
  ),
  "utf8"
);

const packageJson = await readFile(
  new URL(
    "../../windows-host/package.json",
    import.meta.url
  ),
  "utf8"
);

const workflow = await readFile(
  new URL(
    "../../.github/workflows/windows-portable-host.yml",
    import.meta.url
  ),
  "utf8"
);


test(
  "Windows Portable keeps browser profile outside disposable runtime",
  () => {
    assert.ok(
      main.includes(
        "APPFORGE_WINDOWS_PERSISTENT_USERDATA_V23"
      )
    );

    assert.ok(
      main.includes(
        'app.setPath(\n    "userData"'
      )
    );

    assert.ok(
      main.includes(
        "persistentUserDataPath("
      )
    );

    assert.equal(
      main.includes(
        'path.join(\n      runtimeRoot,\n      "user-data"'
      ),
      false
    );
  }
);


test(
  "same appId owns stable persistent and runtime identities",
  () => {
    for (
      const marker of [
        "LOCALAPPDATA",
        "AppForgeStudio",
        "PortableApps",
        "normalizedAppId",
        "runtimeDirectoryName",
        "sha256"
      ]
    ) {
      assert.ok(
        storage.includes(
          marker
        ),
        `missing storage marker: ${marker}`
      );
    }

    assert.equal(
      storage.includes(
        "process.pid"
      ),
      false
    );

    assert.equal(
      main.includes(
        "process.pid"
      ),
      false
    );
  }
);


test(
  "persistent storage module is packaged into Windows host",
  () => {
    assert.ok(
      packageJson.includes(
        '"storage.cjs"'
      )
    );
  }
);


test(
  "Windows CI proves LocalStorage survives a real EXE relaunch",
  () => {
    for (
      const marker of [
        "APPFORGE_HOST_SMOKE_WRITE_VALUE",
        "WINDOWS_PERSISTENT_USERDATA=PASS",
        "WINDOWS_LOCALSTORAGE_RELAUNCH=PASS",
        "LocalStorage did not survive portable EXE relaunch",
        "Disposable runtime was not cleaned after first quit"
      ]
    ) {
      assert.ok(
        workflow.includes(
          marker
        ),
        `missing workflow marker: ${marker}`
      );
    }
  }
);
