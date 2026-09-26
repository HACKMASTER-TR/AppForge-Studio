import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

const mainActivity =
  fs.readFileSync(
    new URL(
      "../../android-app/app/src/main/java/com/appforge/studio/MainActivity.kt",
      import.meta.url
    ),
    "utf8"
  );

test(
  "Builder keeps preserved compiler diagnostics visible ahead of the normal log tail",
  () => {
    assert.match(
      mainActivity,
      /APPFORGE_COMPILER_DIAGNOSTICS_BEGIN/
    );

    assert.match(
      mainActivity,
      /APPFORGE_COMPILER_DIAGNOSTICS_END/
    );

    assert.match(
      mainActivity,
      /val compilerDiagnostics/
    );

    assert.match(
      mainActivity,
      /compilerDiagnostics\s*\+[\s\S]{0,160}logs\.takeLast/
    );

    assert.match(
      mainActivity,
      /\.take\(\s*82\s*\)/
    );

    assert.match(
      mainActivity,
      /\.distinct\(\)/
    );
  }
);

test(
  "Builder error summary prefers an exact Kotlin compiler diagnostic",
  () => {
    assert.match(
      mainActivity,
      /startsWith\(\s*"DIAG e:"[\s\S]{0,100}ignoreCase\s*=\s*true/
    );

    assert.match(
      mainActivity,
      /startsWith\(\s*"DIAG error:"[\s\S]{0,100}ignoreCase\s*=\s*true/
    );

    assert.match(
      mainActivity,
      /unresolved reference/
    );

    assert.match(
      mainActivity,
      /type mismatch/
    );
  }
);

test(
  "compiler diagnostic UI still redacts credential-bearing log lines",
  () => {
    assert.match(
      mainActivity,
      /storepassword/
    );

    assert.match(
      mainActivity,
      /client_secret/
    );

    assert.match(
      mainActivity,
      /Hassas log satırı gizlendi/
    );
  }
);
