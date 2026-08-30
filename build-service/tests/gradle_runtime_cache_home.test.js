import test from "node:test";
import assert from "node:assert/strict";
import { promises as fs } from "node:fs";

test(
  "runtime Gradle uses the Worker prewarmed cache home",
  async () => {
    const configSource =
      await fs.readFile(
        new URL(
          "../src/config.js",
          import.meta.url
        ),
        "utf8"
      );

    const engineSource =
      await fs.readFile(
        new URL(
          "../src/buildEngine.js",
          import.meta.url
        ),
        "utf8"
      );

    const dockerSource =
      await fs.readFile(
        new URL(
          "../Dockerfile.worker",
          import.meta.url
        ),
        "utf8"
      );

    assert.match(
      configSource,
      /APPFORGE_GRADLE_USER_HOME/
    );

    assert.match(
      engineSource,
      /GRADLE_USER_HOME:\s*config\.gradleUserHome/
    );

    assert.match(
      dockerSource,
      /ENV APPFORGE_GRADLE_USER_HOME=\/root\/\.gradle/
    );

    assert.match(
      dockerSource,
      /ENV GRADLE_USER_HOME=\/root\/\.gradle/
    );
  }
);
