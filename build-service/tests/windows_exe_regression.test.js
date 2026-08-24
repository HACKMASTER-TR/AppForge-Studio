import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const server =
  new URL(
    "../server.js",
    import.meta.url
  );

const runtime =
  new URL(
    "../src/workerRuntime.js",
    import.meta.url
  );

const engine =
  new URL(
    "../src/windowsBuild.js",
    import.meta.url
  );

const storage =
  new URL(
    "../src/storage.js",
    import.meta.url
  );

const dockerfile =
  new URL(
    "../Dockerfile.windows-worker",
    import.meta.url
  );

test(
  "EXE builds route to Windows worker capability",
  async () => {
    const text =
      await readFile(
        server,
        "utf8"
      );

    assert.equal(
      text.includes(
        '"windows-exe"'
      ),
      true
    );

    assert.equal(
      text.includes(
        '"exe"'
      ),
      true
    );
  }
);

test(
  "Worker runtime dispatches EXE builds",
  async () => {
    const text =
      await readFile(
        runtime,
        "utf8"
      );

    assert.equal(
      text.includes(
        "executeWindowsBuild"
      ),
      true
    );

    assert.match(
      text,
      /buildOutput\s*===\s*["']exe["']/
    );
  }
);

test(
  "Windows portable engine has AppForge manifest",
  async () => {
    const text =
      await readFile(
        engine,
        "utf8"
      );

    for (
      const marker of [
        "WINDOWS_ELECTRON_PORTABLE",
        "appforge-project.json",
        "windows-x64",
        "portable",
        "putOutput",
        "43.4.1"
      ]
    ) {
      assert.equal(
        text.includes(marker),
        true,
        `Missing Windows marker: ${marker}`
      );
    }
  }
);

test(
  "Windows worker uses Wine Electron builder",
  async () => {
    const text =
      await readFile(
        dockerfile,
        "utf8"
      );

    assert.equal(
      text.includes(
        "electronuserland/builder:wine"
      ),
      true
    );

    assert.equal(
      text.includes(
        "electron-builder@26.15.3"
      ),
      true
    );
  }
);

test(
  "Artifact storage accepts generic binary output",
  async () => {
    const text =
      await readFile(
        storage,
        "utf8"
      );

    assert.equal(
      text.includes(
        "application/octet-stream"
      ),
      true
    );
  }
);

test(
  "EXE builds use Windows-specific preflight",
  async () => {
    const serverText =
      await readFile(
        server,
        "utf8"
      );

    const engineText =
      await readFile(
        engine,
        "utf8"
      );

    assert.equal(
      engineText.includes(
        "export function preflightWindows"
      ),
      true
    );

    assert.equal(
      serverText.includes(
        "preflightWindows("
      ),
      true
    );

    assert.match(
      serverText,
      /c\.buildOutput\s*===\s*"exe"/
    );
  }
);

test(
  "EXE builds cannot be routed to Android worker",
  async () => {
    const text =
      await readFile(
        server,
        "utf8"
      );

    assert.match(
      text,
      /c\.buildOutput\s*===\s*"exe"[\s\S]{0,150}"windows-exe"/
    );
  }
);

test(
  "Windows build skips unused npm dependency scan",
  async () => {
    const text =
      await readFile(
        engine,
        "utf8"
      );

    assert.equal(
      text.includes(
        '"before-build.cjs"'
      ),
      true
    );

    assert.equal(
      text.includes(
        'beforeBuild:'
      ),
      true
    );

    assert.equal(
      text.includes(
        '"site/**/*"'
      ),
      true
    );
  }
);

test(
  "Windows portable EXE uses low-memory compression",
  async () => {
    const text =
      await readFile(
        engine,
        "utf8"
      );

    assert.match(
      text,
      /compression:\s*\n\s*"store"/
    );
  }
);


test(
  "Windows EXE exposes AppForgeMedia and MediaSession bridge",
  async () => {
    const text =
      await readFile(
        engine,
        "utf8"
      );

    for (
      const marker of [
        '"preload.cjs"',
        'contextBridge',
        '"AppForgeMedia"',
        '"appforge-media-state"',
        'navigator.mediaSession',
        '"nexttrack"',
        '"previoustrack"',
        'backgroundThrottling: false',
        'Windows AppForgeMedia + MediaSession bridge hazır'
      ]
    ) {
      assert.equal(
        text.includes(marker),
        true,
        `Missing Windows media marker: ${marker}`
      );
    }
  }
);


test(
  "Windows media bridge supports packaged local media safely",
  async () => {
    const text =
      await readFile(
        engine,
        "utf8"
      );

    for (
      const marker of [
        "const START_PAGE",
        /resolved\.protocol\s*===\s*"file:"/,
        "document.location.protocol",
        "siteRoot.href",
        "resolved.href.startsWith(",
        '"Yerel medya site klasörünün dışında."'
      ]
    ) {
      const found =
        marker instanceof RegExp
          ? marker.test(text)
          : text.includes(marker);

      assert.equal(
        found,
        true,
        `Missing Windows local media marker: ${marker}`
      );
    }
  }
);


test(
  "Windows EXE embeds reversible AppForge conversion payload",
  async () => {
    const text =
      await readFile(
        engine,
        "utf8"
      );

    for (
      const marker of [
        "APPFORGE-EXE-V1!",
        "AFEXEP01",
        "appendAppForgeConversionPayload",
        "writeBigUInt64BE",
        "EXE_MAX_PAYLOAD_BYTES",
        "projectRoot:",
        "exeToApk:",
        "EXE → APK dönüşüm paketi eklendi"
      ]
    ) {
      assert.equal(
        text.includes(marker),
        true,
        `Missing Windows conversion payload marker: ${marker}`
      );
    }
  }
);
