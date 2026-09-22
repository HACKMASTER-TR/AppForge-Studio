import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here =
  path.dirname(
    fileURLToPath(
      import.meta.url
    )
  );

const repo =
  path.resolve(
    here,
    "../.."
  );

const read =
  relative =>
    fs.readFileSync(
      path.join(
        repo,
        relative
      ),
      "utf8"
    );


test(
  "Windows portable host versions are pinned",
  () => {
    const pkg =
      JSON.parse(
        read(
          "windows-host/package.json"
        )
      );

    assert.equal(
      pkg.devDependencies.electron,
      "43.4.1"
    );

    assert.equal(
      pkg.devDependencies["electron-builder"],
      "26.15.3"
    );

    assert.equal(
      pkg.dependencies["adm-zip"],
      "0.6.0"
    );

    assert.equal(
      pkg.build.win.target[0].target,
      "portable"
    );

    assert.deepEqual(
      pkg.build.win.target[0].arch,
      [
        "x64"
      ]
    );
  }
);


test(
  "Windows portable host reads the outer portable EXE payload",
  () => {
    const main =
      read(
        "windows-host/main.cjs"
      );

    const payload =
      read(
        "windows-host/payload.cjs"
      );

    assert.match(
      main,
      /PORTABLE_EXECUTABLE_FILE/
    );

    assert.match(
      payload,
      /APPFORGE-EXE-V1!/
    );

    assert.match(
      payload,
      /AFEXEP01/
    );

    assert.match(
      payload,
      /path traversal/
    );

    assert.match(
      payload,
      /MAX_SITE_FILES/
    );

    assert.match(
      payload,
      /MAX_SITE_BYTES/
    );
  }
);


test(
  "Windows CI builds and executes an appended portable payload",
  () => {
    const workflow =
      read(
        ".github/workflows/windows-portable-host.yml"
      );

    assert.match(
      workflow,
      /runs-on: windows-latest/
    );

    assert.match(
      workflow,
      /Build generic Windows portable host/
    );

    assert.match(
      workflow,
      /append-smoke-payload\.cjs/
    );

    assert.match(
      workflow,
      /APPFORGE_HOST_SMOKE_FILE/
    );

    assert.match(
      workflow,
      /WINDOWS_PORTABLE_PAYLOAD_SMOKE=PASS/
    );

    assert.match(
      workflow,
      /Get-FileHash/
    );
  }
);


test(
  "Windows capability remains gated until real acceptance",
  () => {
    const capability =
      read(
        "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildCapabilities.kt"
      );

    const offline =
      read(
        "android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt"
      );

    assert.match(
      capability,
      /engine\s*=\s*"windows-web"[\s\S]*DeviceBuildSupport\.PLANNED/
    );

    assert.match(
      offline,
      /windowsExeReady\s*=\s*false/
    );
  }
);
