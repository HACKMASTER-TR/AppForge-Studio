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

test(
  "download files expose explicit action menu",
  () => {
    assert.match(
      panel,
      /TerminalDownloadActionDialog/,
    );

    assert.match(
      panel,
      /Terminalde kullan/,
    );

    assert.match(
      panel,
      /Çıkart/,
    );

    assert.match(
      panel,
      /Görüntüle/,
    );
  },
);

test(
  "download archive actions support common formats",
  () => {
    assert.match(
      panel,
      /\.tar\.gz/,
    );

    assert.match(
      panel,
      /\.tgz/,
    );

    assert.match(
      panel,
      /\.tar/,
    );

    assert.match(
      panel,
      /\.zip/,
    );
  },
);

test(
  "download runnable actions use terminal commands",
  () => {
    assert.match(
      panel,
      /sh \$path/,
    );

    assert.match(
      panel,
      /python3 \$path/,
    );

    assert.match(
      panel,
      /node \$path/,
    );

    assert.match(
      panel,
      /java -jar \$path/,
    );

    assert.doesNotMatch(
      panel,
      /Runtime\.getRuntime\(\)\.exec/,
    );

    assert.doesNotMatch(
      panel,
      /ProcessBuilder\(/,
    );
  },
);

test(
  "download actions are routed through terminal command policy",
  () => {
    const screen =
      fs.readFileSync(
        path.join(
          repo,
          "android-app/app/src/main/java/com/appforge/studio/terminal/TerminalWorkspaceScreen.kt",
        ),
        "utf8",
      );

    assert.match(
      screen,
      /onRunCommand = \{ command ->/,
    );

    assert.match(
      screen,
      /runCommand\(/,
    );
  },
);


test(
  "downloads actions use the real PTY terminal",
  () => {
    assert.match(
      screen,
      /LocalPtySessionRegistry/,
    );

    assert.match(
      screen,
      /\.appforge-downloads/,
    );

    assert.match(
      screen,
      /LocalPtySessionRegistry[\s\S]*?\.write\(/,
    );

    assert.match(
      screen,
      /TerminalCommandPolicy\.review/,
    );

    assert.match(
      screen,
      /sourceRoot\.absolutePath/,
    );

    assert.match(
      screen,
      /\/workspace\/\.appforge-downloads/,
    );
  },
);

test(
  "downloads PTY bridge stays account isolated",
  () => {
    assert.match(
      screen,
      /MessageDigest[\s\S]*?SHA-256/,
    );

    assert.match(
      screen,
      /accountKey/,
    );

    assert.match(
      screen,
      /terminal-downloads\/\$accountKey/,
    );
  },
);
