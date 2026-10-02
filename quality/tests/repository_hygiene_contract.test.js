import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import {
  execFileSync
} from "node:child_process";
import {
  fileURLToPath
} from "node:url";

const here =
  path.dirname(
    fileURLToPath(
      import.meta.url
    )
  );

const root =
  path.resolve(
    here,
    "..",
    ".."
  );

const read = relative =>
  fs.readFileSync(
    path.join(
      root,
      relative
    ),
    "utf8"
  );

test(
  "repository hygiene script passes the tracked tree",
  () => {
    const output =
      execFileSync(
        "python3",
        [
          path.join(
            root,
            "scripts/repository-hygiene-check.py"
          )
        ],
        {
          cwd: root,
          encoding: "utf8"
        }
      );

    assert.match(
      output,
      /REPOSITORY_HYGIENE=PASS/
    );
  }
);

test(
  "hard stability gate permanently runs hygiene and Second Brain freshness",
  () => {
    const gate =
      read(
        "scripts/appforge-stability-gate"
      );

    assert.match(
      gate,
      /scripts\/repository-hygiene-check\.py/
    );

    assert.match(
      gate,
      /generate-second-brain-snapshot\.py --check/
    );
  }
);

test(
  "generated AppForge latest APK is not source controlled",
  () => {
    const tracked =
      execFileSync(
        "git",
        [
          "ls-files",
          "--",
          "AppForgeStudio-latest.apk"
        ],
        {
          cwd: root,
          encoding: "utf8"
        }
      ).trim();

    assert.equal(
      tracked,
      ""
    );

    const ignore =
      read(
        ".gitignore"
      );

    assert.match(
      ignore,
      /^\/AppForgeStudio-latest\.apk$/m
    );
  }
);

test(
  "self APK delivery uses the exact HEAD Actions artifact",
  () => {
    const command =
      read(
        "android-app/app/src/main/assets/terminal/appforge-apk"
      );

    const workflow =
      read(
        ".github/workflows/android-debug.yml"
      );

    assert.match(
      workflow,
      /cp "\$APK" AppForgeStudio-latest\.apk/
    );

    assert.match(
      command,
      /actions\/runs\/\$run_id\/artifacts/
    );

    assert.match(
      command,
      /actions\/artifacts\/\$artifact_id\/zip/
    );

    assert.match(
      command,
      /select\(\.head_sha == \$head\)/
    );
  }
);
