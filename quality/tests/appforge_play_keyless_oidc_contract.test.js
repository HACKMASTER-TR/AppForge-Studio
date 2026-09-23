import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const workflow = await readFile(
  new URL(
    "../../.github/workflows/android-play-release.yml",
    import.meta.url
  ),
  "utf8"
);

test(
  "Play release uses keyless GitHub OIDC authentication",
  () => {
    assert.match(
      workflow,
      /id-token:\s*write/
    );

    assert.match(
      workflow,
      /google-github-actions\/auth@v3/
    );

    assert.match(
      workflow,
      /projects\/564043752274\/locations\/global\/workloadIdentityPools\/appforge-github\/providers\/appforge-github-oidc/
    );

    assert.match(
      workflow,
      /appforge-play-publisher@appforge-18a5a\.iam\.gserviceaccount\.com/
    );

    assert.match(
      workflow,
      /create_credentials_file:\s*true/
    );

    assert.match(
      workflow,
      /cleanup_credentials:\s*true/
    );
  }
);

test(
  "Play uploader consumes temporary WIF credentials",
  () => {
    assert.match(
      workflow,
      /serviceAccountJson:\s*\$\{\{\s*steps\.google-auth\.outputs\.credentials_file_path\s*\}\}/
    );

    assert.doesNotMatch(
      workflow,
      /APPFORGE_PLAY_SERVICE_ACCOUNT_JSON/
    );

    assert.doesNotMatch(
      workflow,
      /serviceAccountJsonPlainText/
    );
  }
);
