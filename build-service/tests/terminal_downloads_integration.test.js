import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const here =
  path.dirname(
    fileURLToPath(import.meta.url),
  );

const repo =
  path.resolve(
    here,
    "../..",
  );

const screen =
  fs.readFileSync(
    path.join(
      repo,
      "android-app/app/src/main/java/com/appforge/studio/terminal/TerminalWorkspaceScreen.kt",
    ),
    "utf8",
  );

const panel =
  fs.readFileSync(
    path.join(
      repo,
      "android-app/app/src/main/java/com/appforge/studio/terminal/TerminalDownloadsPanel.kt",
    ),
    "utf8",
  );

test(
  "terminal exposes Downloads tab",
  () => {
    assert.match(
      screen,
      /DOWNLOADS\("İndirilenler"/,
    );

    assert.match(
      screen,
      /TerminalWorkspaceTab\.DOWNLOADS/,
    );
  },
);

test(
  "terminal exposes downloads commands",
  () => {
    assert.match(
      screen,
      /appforge downloads/,
    );

    assert.match(
      screen,
      /appforge import/,
    );
  },
);

test(
  "downloads use Android SAF without broad storage access",
  () => {
    assert.match(
      panel,
      /OpenMultipleDocuments/,
    );

    assert.doesNotMatch(
      panel,
      /MANAGE_EXTERNAL_STORAGE/,
    );

    assert.doesNotMatch(
      panel,
      /READ_EXTERNAL_STORAGE/,
    );
  },
);

test(
  "downloads inbox is isolated per AppForge account",
  () => {
    assert.match(
      panel,
      /accountEmail/,
    );

    assert.match(
      panel,
      /SHA-256/,
    );

    assert.match(
      panel,
      /terminal-downloads/,
    );
  },
);

test(
  "download import is bounded and filename safe",
  () => {
    assert.match(
      panel,
      /MAX_IMPORTED_DOWNLOAD_BYTES/,
    );

    assert.match(
      panel,
      /sanitizeFileName/,
    );

    assert.match(
      panel,
      /uniqueTarget/,
    );
  },
);


test(
  "downloads panel avoids invalid Compose weight import",
  () => {
    assert.doesNotMatch(
      panel,
      /^import androidx\.compose\.foundation\.layout\.weight$/m,
    );

    assert.match(
      panel,
      /\.weight\(1f\)/,
    );
  },
);
