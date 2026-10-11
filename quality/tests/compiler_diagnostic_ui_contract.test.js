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


test(
  "admin error copy is visible only through the verified owner gate",
  () => {
    assert.match(
      mainActivity,
      /ADMIN_BUILD_ERROR_COPY_V1/
    );

    assert.match(
      mainActivity,
      /val adminBuildErrorCopyVisible/
    );

    assert.match(
      mainActivity,
      /OwnerAccessPolicy[\s\S]{0,300}\.isActiveOwner/
    );

    assert.match(
      mainActivity,
      /if\s*\(\s*adminBuildErrorCopyVisible\s*\)[\s\S]{0,5000}"HATAYI KOPYALA"/
    );

    assert.equal(
      (
        mainActivity.match(
          /"HATAYI KOPYALA"/g
        ) || []
      ).length,
      1
    );
  }
);

test(
  "admin error copy revalidates owner access before clipboard write",
  () => {
    const start =
      mainActivity.indexOf(
        "ADMIN_BUILD_ERROR_COPY_BUTTON_V1"
      );

    assert.ok(
      start >= 0
    );

    const section =
      mainActivity.slice(
        start,
        start + 7000
      );

    assert.match(
      section,
      /val stillVerifiedOwner/
    );

    assert.match(
      section,
      /OwnerAccessPolicy[\s\S]{0,300}\.isActiveOwner/
    );

    assert.match(
      section,
      /setPrimaryClip/
    );

    assert.match(
      section,
      /Yönetici doğrulamasının süresi doldu/
    );
  }
);

test(
  "admin error copy uses the sanitized visible diagnostic log",
  () => {
    assert.match(
      mainActivity,
      /val adminBuildErrorCopyPayload/
    );

    assert.match(
      mainActivity,
      /visibleLocalBuildLogs[\s\S]{0,400}\.forEach/
    );

    assert.match(
      mainActivity,
      /\.take\(\s*32_000\s*\)/
    );

    assert.match(
      mainActivity,
      /ClipData[\s\S]{0,200}\.newPlainText/
    );

    assert.match(
      mainActivity,
      /ClipboardManager/
    );

    assert.match(
      mainActivity,
      /Hassas log satırı gizlendi/
    );
  }
);
