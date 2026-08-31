import test from "node:test";
import assert from "node:assert/strict";
import {
  promises as fs
} from "fs";
import os from "os";
import path from "path";
import AdmZip from "adm-zip";

import {
  normalizeNodeBackendUrl,
  prepareNodeRemoteBackendSource
} from "../src/nodeRemoteBackendEngine.js";

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
  "Node remote URL requires public HTTPS",
  () => {
    assert.equal(
      normalizeNodeBackendUrl(
        "https://api.example.com/v1"
      ),
      "https://api.example.com/v1/"
    );

    assert.throws(
      () =>
        normalizeNodeBackendUrl(
          "http://api.example.com"
        ),
      /yalnız HTTPS/
    );

    assert.throws(
      () =>
        normalizeNodeBackendUrl(
          "https://127.0.0.1"
        ),
      /private ağ/
    );

    assert.throws(
      () =>
        normalizeNodeBackendUrl(
          "https://192.168.1.20"
        ),
      /private ağ/
    );
  }
);

test(
  "Express remote backend contract is detected",
  async () => {
    const root =
      await fs.mkdtemp(
        path.join(
          os.tmpdir(),
          "appforge-node-remote-"
        )
      );

    try {
      const zipPath =
        await makeZip(
          root,
          {
            "package.json":
              JSON.stringify(
                {
                  dependencies: {
                    express:
                      "^5.0.0"
                  },
                  scripts: {
                    start:
                      "node server.js"
                  }
                }
              ),
            "server.js":
              "console.log('server');\n",
            "appforge.remote.json":
              JSON.stringify(
                {
                  backendUrl:
                    "https://api.example.com/app",
                  healthPath:
                    "/health",
                  openExternalLinks:
                    true
                }
              )
          }
        );

      const prepared =
        await prepareNodeRemoteBackendSource(
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
        "express"
      );

      assert.equal(
        prepared.startScript,
        "node server.js"
      );

      assert.equal(
        prepared.contract.backendUrl,
        "https://api.example.com/app/"
      );

      assert.equal(
        prepared.contract.healthPath,
        "/health"
      );

      assert.equal(
        prepared.buildReady,
        false
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
  "Node backend without remote contract stays blocked",
  async () => {
    const root =
      await fs.mkdtemp(
        path.join(
          os.tmpdir(),
          "appforge-node-blocked-"
        )
      );

    try {
      const zipPath =
        await makeZip(
          root,
          {
            "package.json":
              JSON.stringify(
                {
                  dependencies: {
                    fastify:
                      "^5.0.0"
                  }
                }
              ),
            "server.js":
              "console.log('ok');\n"
          }
        );

      const prepared =
        await prepareNodeRemoteBackendSource(
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
        "fastify"
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
