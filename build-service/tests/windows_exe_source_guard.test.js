import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const root =
  new URL("../../", import.meta.url);

const read =
  path =>
    readFile(
      new URL(path, root),
      "utf8"
    );

test(
  "server rejects native local sources for Windows EXE",
  async () => {
    const server =
      await read(
        "build-service/server.js"
      );

    assert.ok(
      server.includes(
        "WINDOWS_EXE_SOURCE_INCOMPATIBLE"
      )
    );

    assert.ok(
      server.includes(
        '"webview-static"'
      )
    );

    assert.ok(
      server.includes(
        '"node-web"'
      )
    );

    assert.match(
      server,
      /c\.buildOutput === "exe"/
    );

    assert.match(
      server,
      /c\.sourceMode !== "URL"/
    );
  }
);

test(
  "Studio disables EXE output for native sources",
  async () => {
    const main =
      await read(
        "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
      );

    assert.ok(
      main.includes(
        "windowsExeCompatible"
      )
    );

    assert.ok(
      main.includes(
        "outputEnabled"
      )
    );

    assert.ok(
      main.includes(
        "Windows EXE bu proje türüyle uyumlu değil."
      )
    );

    assert.ok(
      main.includes(
        '"android-gradle"'
      ) ||
      main.includes(
        '"webview-static"'
      )
    );
  }
);
