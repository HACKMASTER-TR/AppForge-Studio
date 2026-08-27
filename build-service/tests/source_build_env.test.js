import test from "node:test";
import assert from "node:assert/strict";

import {
  createSourceBuildEnv,
  sourceBuildEnvKeys
} from "../src/sourceBuildEnv.js";

test(
  "source build environment keeps runtime variables and removes service secrets",
  () => {
    const inherited = {
      PATH:
        "/safe/bin",
      PREFIX:
        "/data/data/com.termux/files/usr",
      LD_PRELOAD:
        "/data/data/com.termux/files/usr/lib/libtermux-exec.so",
      ANDROID_SDK_ROOT:
        "/opt/android-sdk",
      JAVA_HOME:
        "/usr/lib/jvm/java-17",
      DATABASE_URL:
        "postgres://SECRET",
      REDIS_URL:
        "redis://SECRET",
      JWT_SECRET:
        "SECRET_JWT",
      AWS_SECRET_ACCESS_KEY:
        "SECRET_AWS",
      OPENAI_API_KEY:
        "SECRET_OPENAI"
    };

    const env =
      createSourceBuildEnv(
        {
          CI:
            "true",
          HOME:
            "/tmp/source-home"
        },
        inherited
      );

    assert.equal(
      env.PATH,
      "/safe/bin"
    );

    assert.equal(
      env.PREFIX,
      "/data/data/com.termux/files/usr"
    );

    assert.match(
      env.LD_PRELOAD,
      /libtermux-exec/
    );

    assert.equal(
      env.ANDROID_SDK_ROOT,
      "/opt/android-sdk"
    );

    assert.equal(
      env.JAVA_HOME,
      "/usr/lib/jvm/java-17"
    );

    assert.equal(
      env.CI,
      "true"
    );

    assert.equal(
      env.HOME,
      "/tmp/source-home"
    );

    for (
      const key of [
        "DATABASE_URL",
        "REDIS_URL",
        "JWT_SECRET",
        "AWS_SECRET_ACCESS_KEY",
        "OPENAI_API_KEY"
      ]
    ) {
      assert.equal(
        env[key],
        undefined,
        `${key} source build'e sızmamalı`
      );
    }
  }
);

test(
  "source build environment forwards trusted runtime cache variables",
  () => {
    const inherited = {
      HOME: "/home/appforge",
      APPFORGE_USER_CACHE_ROOT: "/app/user-cache/10001",
      GRADLE_USER_HOME: "/app/user-cache/10001/gradle",
      NPM_CONFIG_CACHE: "/app/user-cache/10001/npm",
      PIP_CACHE_DIR: "/app/user-cache/10001/pip",
      PUB_CACHE: "/app/user-cache/10001/pub",
      DOTNET_CLI_HOME: "/app/user-cache/10001/dotnet",
      NUGET_PACKAGES: "/app/user-cache/10001/nuget",
      XDG_CACHE_HOME: "/app/user-cache/10001/xdg-cache",
      XDG_DATA_HOME: "/app/user-cache/10001/xdg-data",
      XDG_STATE_HOME: "/app/user-cache/10001/xdg-state",
      YARN_CACHE_FOLDER: "/app/user-cache/10001/yarn",
      COREPACK_HOME: "/opt/appforge-corepack",
      COREPACK_ENABLE_NETWORK: "0",
      COREPACK_DEFAULT_TO_LATEST: "0",
      COREPACK_ENABLE_DOWNLOAD_PROMPT: "0",
      DOTNET_SKIP_FIRST_TIME_EXPERIENCE: "1",
      FLUTTER_SUPPRESS_ANALYTICS: "true",
      DART_SUPPRESS_ANALYTICS: "true",
      FLUTTER_ALREADY_LOCKED: "true"
    };

    const env =
      createSourceBuildEnv(
        {},
        inherited
      );

    for (
      const [key, value] of
      Object.entries(inherited)
    ) {
      assert.equal(
        env[key],
        value,
        `${key} source build ortamına aktarılmalı`
      );
    }

    const keys =
      sourceBuildEnvKeys();

    for (
      const key of
      Object.keys(inherited)
    ) {
      assert.ok(
        keys.includes(key),
        `${key} SAFE_SOURCE_ENV_KEYS içinde olmalı`
      );
    }
  }
);
