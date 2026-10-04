import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const client = await readFile(
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/security/StudioSecurityClient.kt",
    import.meta.url
  ),
  "utf8"
);

const worker = await readFile(
  new URL(
    "../../cloudflare/control-plane/src/index.mjs",
    import.meta.url
  ),
  "utf8"
);

const oauth = await readFile(
  new URL(
    "../../cloudflare/control-plane/src/google_service_account_oauth.mjs",
    import.meta.url
  ),
  "utf8"
);

const decode = await readFile(
  new URL(
    "../../cloudflare/control-plane/src/play_integrity_decode.mjs",
    import.meta.url
  ),
  "utf8"
);

const route = await readFile(
  new URL(
    "../../cloudflare/control-plane/src/play_integrity_route.mjs",
    import.meta.url
  ),
  "utf8"
);

test(
  "Android and backend share a server-recomputable request binding",
  () => {
    assert.match(
      client,
      /appforge-integrity-v1\|\$action\|\$nonce\|\$timestamp/
    );

    assert.doesNotMatch(
      client,
      /\$userId\|\$action\|\$nonce\|\$timestamp/
    );

    assert.match(
      route,
      /appforge-integrity-v1\|/
    );

    assert.match(
      route,
      /integrity_request_binding_mismatch/
    );
  }
);

test(
  "backend uses Google service-account OAuth with Play Integrity scope",
  () => {
    assert.match(
      oauth,
      /https:\/\/www\.googleapis\.com\/auth\/playintegrity/
    );

    assert.match(
      oauth,
      /https:\/\/oauth2\.googleapis\.com\/token/
    );

    assert.match(
      oauth,
      /RSASSA-PKCS1-v1_5/
    );

    assert.match(
      oauth,
      /PLAY_INTEGRITY_SERVICE_ACCOUNT_PRIVATE_KEY/
    );
  }
);

test(
  "server calls Google decodeIntegrityToken and verifies request binding",
  () => {
    assert.match(
      decode,
      /playintegrity\.googleapis\.com/
    );

    assert.match(
      decode,
      /:decodeIntegrityToken/
    );

    assert.match(
      decode,
      /tokenPayloadExternal/
    );

    assert.match(
      decode,
      /requestPackageName/
    );

    assert.match(
      decode,
      /requestHash/
    );

    assert.match(
      decode,
      /timestampMillis/
    );

    assert.match(
      decode,
      /PLAY_RECOGNIZED/
    );
  }
);

test(
  "only exact config and attest security routes are enabled",
  () => {
    assert.match(
      worker,
      /pathname === '\/api\/security\/config'/
    );

    assert.match(
      worker,
      /pathname === '\/api\/security\/attest'/
    );

    assert.match(
      route,
      /play_integrity_server_not_configured/
    );

    assert.match(
      route,
      /integrity_policy_denied/
    );
  }
);
