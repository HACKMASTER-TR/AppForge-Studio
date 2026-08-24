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
