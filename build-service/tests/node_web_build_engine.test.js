import test from "node:test";
import assert from "node:assert/strict";
import { promises as fs } from "fs";
import os from "os";
import path from "path";
import AdmZip from "adm-zip";
import {
  buildNodeWebSource
} from "../src/sourceBuildEngines.js";

test(
  "Node/Web engine builds a static npm project",
  async () => {
    const root =
      await fs.mkdtemp(
        path.join(
          os.tmpdir(),
          "appforge-node-web-"
        )
      );

    try {
      const project =
        path.join(
          root,
          "project"
        );

      await fs.mkdir(
        project,
        {
          recursive: true
        }
      );

      await fs.writeFile(
        path.join(
          project,
          "package.json"
        ),
        JSON.stringify(
          {
            name:
              "appforge-node-web-test",
            version:
              "1.0.0",
            private:
              true,
            scripts: {
              build:
                "node build.mjs"
            }
          },
          null,
          2
        )
      );

      await fs.writeFile(
        path.join(
          project,
          "build.mjs"
        ),
        [
          'import { promises as fs } from "fs";',
          'await fs.mkdir("dist", { recursive: true });',
          'await fs.writeFile("dist/index.html", \'<html><script src="/assets/app.js"></script></html>\');',
          'await fs.mkdir("dist/assets", { recursive: true });',
          'await fs.writeFile("dist/assets/app.js", "console.log(1)");'
        ].join(
          "\n"
        )
      );

      const inputZip =
        path.join(
          root,
          "input.zip"
        );

      const zip =
        new AdmZip();

      zip.addLocalFolder(
        project
      );

      zip.writeZip(
        inputZip
      );

      const result =
        await buildNodeWebSource(
          {
            projectZip:
              inputZip,
            workDir:
              path.join(
                root,
                "work"
              ),
            technology:
              "npm-web"
          }
        );

      const outputZip =
        new AdmZip(
          result.projectZip
        );

      const indexEntry =
        outputZip.getEntry(
          "index.html"
        );

      assert.ok(
        indexEntry,
        "compiled zip must contain index.html"
      );

      const html =
        indexEntry
          .getData()
          .toString(
            "utf8"
          );

      assert.match(
        html,
        /src="\.\/assets\/app\.js"/
      );
    } finally {
      await fs.rm(
        root,
        {
          recursive: true,
          force: true
        }
      );
    }
  }
);
