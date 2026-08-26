import test from "node:test";
import assert from "node:assert/strict";
import {
  promises as fs
} from "fs";
import os from "os";
import path from "path";
import AdmZip from "adm-zip";

import {
  normalizePhpBackendUrl,
  preparePhpRemoteBackendSource
} from "../src/phpRemoteBackendEngine.js";

async function makeZip(
  root,
  files
) {
  const project =
    path.join(
      root,
      "project"
    );

  for (
    const [
      relative,
      content
    ] of
    Object.entries(
      files
    )
  ) {
    const target =
      path.join(
        project,
        relative
      );

    await fs.mkdir(
      path.dirname(
        target
      ),
      {
        recursive: true
      }
    );

    await fs.writeFile(
      target,
      content
    );
  }

  const zipPath =
    path.join(
      root,
      "project.zip"
    );

  const zip =
    new AdmZip();

  zip.addLocalFolder(
    project
  );

  zip.writeZip(
    zipPath
  );

  return zipPath;
}

test(
  "PHP remote URL requires public HTTPS",
  () => {
    assert.equal(
      normalizePhpBackendUrl(
        "https://example.com/app"
      ),
      "https://example.com/app/"
    );

    assert.throws(
      () =>
        normalizePhpBackendUrl(
          "http://example.com"
        ),
      /yalnız HTTPS/
    );

    assert.throws(
      () =>
        normalizePhpBackendUrl(
          "https://127.0.0.1"
        ),
      /private ağ/
    );

    assert.throws(
      () =>
        normalizePhpBackendUrl(
          "https://192.168.1.10"
        ),
      /private ağ/
    );

    assert.throws(
      () =>
        normalizePhpBackendUrl(
          "https://user:pass@example.com"
        ),
      /kullanıcı adı\/parola/
    );
  }
);

test(
  "Laravel remote backend contract is detected",
  async () => {
    const root =
      await fs.mkdtemp(
        path.join(
          os.tmpdir(),
          "appforge-php-remote-"
        )
      );

    try {
      const zipPath =
        await makeZip(
          root,
          {
            "composer.json":
              JSON.stringify(
                {
                  require: {
                    "php":
                      "^8.3",
                    "laravel/framework":
                      "^12.0"
                  }
                }
              ),
            "artisan":
              "#!/usr/bin/env php\n",
            "public/index.php":
              "<?php echo 'ok';\n",
            "appforge.remote.json":
              JSON.stringify(
                {
                  backendUrl:
                    "https://shop.example.com/app",
                  healthPath:
                    "/health",
                  openExternalLinks:
                    true
                }
              )
          }
        );

      const prepared =
        await preparePhpRemoteBackendSource(
          {
            projectZip:
              zipPath,
            workDir:
              path.join(
                root,
                "work"
              )
          }
        );

      assert.equal(
        prepared.framework,
        "laravel"
      );

      assert.equal(
        prepared.contract.backendUrl,
        "https://shop.example.com/app/"
      );

      assert.equal(
        prepared.contract.healthPath,
        "/health"
      );

      assert.equal(
        prepared.buildReady,
        false
      );

      assert.match(
        prepared.buildBlockedReason,
        /router bağlantısı/
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

test(
  "PHP project without remote contract stays blocked",
  async () => {
    const root =
      await fs.mkdtemp(
        path.join(
          os.tmpdir(),
          "appforge-php-blocked-"
        )
      );

    try {
      const zipPath =
        await makeZip(
          root,
          {
            "index.php":
              "<?php echo 'ok';\n"
          }
        );

      const prepared =
        await preparePhpRemoteBackendSource(
          {
            projectZip:
              zipPath,
            workDir:
              path.join(
                root,
                "work"
              )
          }
        );

      assert.equal(
        prepared.framework,
        "php"
      );

      assert.equal(
        prepared.contract,
        null
      );

      assert.match(
        prepared.buildBlockedReason,
        /appforge\.remote\.json/
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
