import test from "node:test";
import assert from "node:assert/strict";
import {
  promises as fs
} from "node:fs";
import path from "node:path";
import {
  fileURLToPath
} from "node:url";

const here =
  path.dirname(
    fileURLToPath(
      import.meta.url
    )
  );

const repoRoot =
  path.resolve(
    here,
    "..",
    ".."
  );

test(
  "repository enforces portable text line endings",
  async () => {
    const attributes =
      await fs.readFile(
        path.join(
          repoRoot,
          ".gitattributes"
        ),
        "utf8"
      );

    assert.match(
      attributes,
      /^\* text=auto eol=lf$/m
    );
  }
);

test(
  "GitHub workflows avoid deprecated Node 20 action majors",
  async () => {
    const workflowDir =
      path.join(
        repoRoot,
        ".github",
        "workflows"
      );

    const files =
      (
        await fs.readdir(
          workflowDir
        )
      )
        .filter(
          file =>
            file.endsWith(
              ".yml"
            ) ||
            file.endsWith(
              ".yaml"
            )
        );

    const deprecated = [
      "actions/checkout@v4",
      "actions/setup-node@v4",
      "actions/setup-java@v4",
      "gradle/actions/setup-gradle@v4",
      "actions/upload-artifact@v4",
      "docker/setup-buildx-action@v3",
      "docker/login-action@v3",
      "docker/metadata-action@v5",
      "docker/build-push-action@v6"
    ];

    for (
      const file of files
    ) {
      const source =
        await fs.readFile(
          path.join(
            workflowDir,
            file
          ),
          "utf8"
        );

      for (
        const marker of deprecated
      ) {
        assert.equal(
          source.includes(
            marker
          ),
          false,
          `${file} eski Action sürümünü kullanıyor: ${marker}`
        );
      }
    }
  }
);

test(
  "build service excludes deprecated glob dependency chain",
  async () => {
    const packageJson =
      JSON.parse(
        await fs.readFile(
          path.join(
            repoRoot,
            "build-service",
            "package.json"
          ),
          "utf8"
        )
      );

    const lock =
      JSON.parse(
        await fs.readFile(
          path.join(
            repoRoot,
            "build-service",
            "package-lock.json"
          ),
          "utf8"
        )
      );

    assert.equal(
      packageJson.dependencies.googleapis,
      "^176.0.0"
    );

    assert.equal(
      packageJson.overrides?.["googleapis-common"]?.gaxios,
      "7.3.1"
    );

    assert.equal(
      Object.hasOwn(
        lock.packages,
        "node_modules/glob"
      ),
      false
    );

    assert.equal(
      Object.hasOwn(
        lock.packages,
        "node_modules/rimraf"
      ),
      false
    );
  }
);
