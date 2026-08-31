import test from "node:test";
import assert from "node:assert/strict";
import {
  promises as fs
} from "fs";
import path from "path";
import {
  fileURLToPath
} from "url";

const here =
  path.dirname(
    fileURLToPath(
      import.meta.url
    )
  );

const serviceRoot =
  path.resolve(
    here,
    ".."
  );

test(
  "dedicated source worker has startup isolation guards",
  async () => {
    const source =
      await fs.readFile(
        path.join(
          serviceRoot,
          "source-worker.js"
        ),
        "utf8"
      );

    for (
      const marker of [
        "SOURCE_BUILD_REQUIRE_ISOLATION=true",
        "assertSourceBuildIsolation(",
        "status.attestedIsolation",
        "process.getuid() ===",
        "root kullanıcıyla çalıştırılamaz",
        'await import(',
        '"./worker.js"'
      ]
    ) {
      assert.ok(
        source.includes(
          marker
        ),
        marker
      );
    }
  }
);

test(
  "source worker Dockerfile is non-root and explicitly attested",
  async () => {
    const docker =
      await fs.readFile(
        path.join(
          serviceRoot,
          "Dockerfile.source-worker"
        ),
        "utf8"
      );

    for (
      const marker of [
        "USER 10001:10001",
        "SOURCE_BUILD_ISOLATION_MODE=dedicated",
        "SOURCE_BUILD_REQUIRE_ISOLATION=true",
        "SOURCE_BUILD_ISOLATION_CAPABILITY=source-isolation-dedicated",
        "source-isolation-dedicated",
        'CMD ["npm", "run", "worker:source"]'
      ]
    ) {
      assert.ok(
        docker.includes(
          marker
        ),
        marker
      );
    }

    assert.match(
      docker,
      /GRADLE_USER_HOME=\/app\/user-cache\/10001\/gradle/
    );

    assert.match(
      docker,
      /GRADLE_CACHE_ROOT=\/app\/user-cache\/10001\/gradle-cache/
    );
  }
);

test(
  "source worker package command exists",
  async () => {
    const pkg =
      JSON.parse(
        await fs.readFile(
          path.join(
            serviceRoot,
            "package.json"
          ),
          "utf8"
        )
      );

    assert.equal(
      pkg.scripts[
        "worker:source"
      ],
      "node --import ./instrument.mjs source-worker.js"
    );
  }
);
