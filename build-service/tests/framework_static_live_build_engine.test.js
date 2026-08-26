import test from "node:test";
import assert from "node:assert/strict";
import {
  promises as fs
} from "fs";
import os from "os";
import path from "path";

import {
  frameworkStaticOutputDirectory,
  rewriteFrameworkStaticAssets
} from "../src/frameworkStaticBuildEngine.js";

test(
  "framework static output directories are deterministic",
  () => {
    assert.equal(
      frameworkStaticOutputDirectory(
        "nextjs"
      ),
      "out"
    );

    assert.equal(
      frameworkStaticOutputDirectory(
        "nuxt"
      ),
      ".output/public"
    );

    assert.throws(
      () =>
        frameworkStaticOutputDirectory(
          "unknown"
        ),
      /Bilinmeyen static framework/
    );
  }
);

test(
  "absolute HTML and CSS asset roots become WebView-relative",
  async () => {
    const root =
      await fs.mkdtemp(
        path.join(
          os.tmpdir(),
          "appforge-framework-assets-"
        )
      );

    try {
      await fs.writeFile(
        path.join(
          root,
          "index.html"
        ),
`<!doctype html>
<link rel="stylesheet" href="/_next/static/app.css">
<script src="/_next/static/app.js"></script>
<img src="/logo.png">
`
      );

      await fs.writeFile(
        path.join(
          root,
          "app.css"
        ),
`body { background-image: url("/_next/static/bg.png"); }
`
      );

      const changed =
        await rewriteFrameworkStaticAssets(
          root
        );

      assert.equal(
        changed,
        2
      );

      const html =
        await fs.readFile(
          path.join(
            root,
            "index.html"
          ),
          "utf8"
        );

      const css =
        await fs.readFile(
          path.join(
            root,
            "app.css"
          ),
          "utf8"
        );

      assert.match(
        html,
        /href="\.\/_next/
      );

      assert.match(
        html,
        /src="\.\/logo\.png/
      );

      assert.match(
        css,
        /url\("\.\/_next/
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
