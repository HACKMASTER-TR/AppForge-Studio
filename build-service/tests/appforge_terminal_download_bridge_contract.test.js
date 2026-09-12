import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

async function read(path) {
  return readFile(
    new URL(
      `../../${path}`,
      import.meta.url
    ),
    "utf8"
  );
}

test(
  "download action keeps interpreter mapping",
  async () => {
    const panel =
      await read(
        "android-app/app/src/main/java/com/appforge/studio/terminal/TerminalDownloadsPanel.kt"
      );

    assert.match(
      panel,
      /"python3 \$path"/
    );

    assert.match(
      panel,
      /"sh \$path"/
    );

    assert.match(
      panel,
      /"node \$path"/
    );

    assert.match(
      panel,
      /"java -jar \$path"/
    );

    assert.match(
      panel,
      /"Python ile çalıştır"/
    );

    assert.match(
      panel,
      /private fun terminalUseCommand\(/
    );
  }
);

test(
  "downloads commands are mirrored into Linux workspace before PTY execution",
  async () => {
    const source =
      await read(
        "android-app/app/src/main/java/com/appforge/studio/terminal/TerminalWorkspaceScreen.kt"
      );

    assert.match(
      source,
      /internal fun prepareTerminalDownloadCommand\(/
    );

    assert.match(
      source,
      /\.appforge-downloads\/\$accountKey/
    );

    assert.match(
      source,
      /\/workspace\/\.appforge-downloads\/\$accountKey/
    );

    assert.match(
      source,
      /rawCommand\.contains\(\s*sourceRoot\.absolutePath/
    );

    assert.match(
      source,
      /privateDownloadsRoot/
    );

    assert.match(
      source,
      /Android özel dosya yolu PTY komutuna sızdı/
    );

    assert.match(
      source,
      /prepareTerminalDownloadCommand\(/
    );

    assert.match(
      source,
      /TerminalCommandPolicy\.review\(\s*ptyCommand/
    );

    assert.match(
      source,
      /ptyCommand \+ "\\n"/
    );
  }
);
