import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const serverUrl =
  new URL(
    "../server.js",
    import.meta.url
  );

const sqlUrl =
  new URL(
    "../sql/011_build_numbers.sql",
    import.meta.url
  );

const apiUrl =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/build/BuildApiClient.kt",
    import.meta.url
  );

const mainUrl =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
    import.meta.url
  );

const numbersUrl =
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/AppForgeBuildNumbers.kt",
    import.meta.url
  );

test(
  "public build number is server-backed and normal UI hides technical ids",
  async () => {
    const [
      server,
      sql,
      api,
      main,
      numbers
    ] =
      await Promise.all([
        readFile(serverUrl, "utf8"),
        readFile(sqlUrl, "utf8"),
        readFile(apiUrl, "utf8"),
        readFile(mainUrl, "utf8"),
        readFile(numbersUrl, "utf8")
      ]);

    assert.equal(
      sql.includes(
        "build_no BIGINT"
      ),
      true
    );

    assert.equal(
      sql.includes(
        "appforge_build_no_seq"
      ),
      true
    );

    assert.equal(
      server.includes(
        'build_no AS "buildNo"'
      ),
      true
    );

    assert.equal(
      server.includes(
        "publicBuildNumber("
      ),
      true
    );

    assert.equal(
      api.includes(
        "val buildNo: Long?"
      ),
      true
    );

    assert.equal(
      numbers.includes(
        '"AF-"'
      ),
      true
    );

    assert.equal(
      numbers.includes(
        "getSharedPreferences("
      ),
      false
    );

    for (
      const hidden of [
        '"Build Service URL"',
        '"Build API Key"',
        '"Canlı Windows logu"',
        '"Canlı Gradle logu"',
        '"Build ID"'
      ]
    ) {
      assert.equal(
        main.includes(hidden),
        false,
        `Normal UI still exposes: ${hidden}`
      );
    }

    assert.equal(
      main.includes(
        '"Derleme No"'
      ),
      true
    );
  }
);
