import test from "node:test";
import assert from "node:assert/strict";

import {
  createSourceBuildEnv
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
