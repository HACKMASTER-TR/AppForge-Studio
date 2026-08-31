import test from "node:test";
import assert from "node:assert/strict";
import { promises as fs } from "node:fs";
import os from "node:os";
import path from "node:path";
import AdmZip from "adm-zip";

import {
  computeCacheKey,
  projectContentSha256
} from "../src/buildCache.js";

async function writeZip(
  file,
  entries
) {
  const zip =
    new AdmZip();

  for (
    const [
      name,
      content
    ] of entries
  ) {
    zip.addFile(
      name,
      Buffer.from(
        content
      )
    );
  }

  zip.writeZip(file);
}

test(
  "project archive cache ignores ZIP entry order",
  async () => {
    const dir =
      await fs.mkdtemp(
        path.join(
          os.tmpdir(),
          "appforge-cache-"
        )
      );

    try {
      const first =
        path.join(
          dir,
          "first.zip"
        );

      const second =
        path.join(
          dir,
          "second.zip"
        );

      await writeZip(
        first,
        [
          [
            "app/src/main/a.txt",
            "A"
          ],
          [
            "app/src/main/b.txt",
            "B"
          ]
        ]
      );

      await writeZip(
        second,
        [
          [
            "app/src/main/b.txt",
            "B"
          ],
          [
            "app/src/main/a.txt",
            "A"
          ]
        ]
      );

      assert.equal(
        await projectContentSha256(
          first
        ),
        await projectContentSha256(
          second
        )
      );

      const config = {
        appName:
          "Native Test",
        packageName:
          "com.appforge.nativecache",
        sourceMode:
          "LOCAL",
        sourceBuildEngine:
          "android-gradle",
        versionName:
          "1.0.0",
        versionCode:
          1,
        buildOutput:
          "apk"
      };

      assert.equal(
        await computeCacheKey(
          config,
          {
            projectFile:
              first
          }
        ),
        await computeCacheKey(
          config,
          {
            projectFile:
              second
          }
        )
      );
    } finally {
      await fs.rm(
        dir,
        {
          recursive: true,
          force: true
        }
      );
    }
  }
);

test(
  "project archive cache changes when source content changes",
  async () => {
    const dir =
      await fs.mkdtemp(
        path.join(
          os.tmpdir(),
          "appforge-cache-change-"
        )
      );

    try {
      const first =
        path.join(
          dir,
          "first.zip"
        );

      const second =
        path.join(
          dir,
          "second.zip"
        );

      await writeZip(
        first,
        [
          [
            "app/src/main/a.txt",
            "VERSION-A"
          ]
        ]
      );

      await writeZip(
        second,
        [
          [
            "app/src/main/a.txt",
            "VERSION-B"
          ]
        ]
      );

      assert.notEqual(
        await projectContentSha256(
          first
        ),
        await projectContentSha256(
          second
        )
      );
    } finally {
      await fs.rm(
        dir,
        {
          recursive: true,
          force: true
        }
      );
    }
  }
);
