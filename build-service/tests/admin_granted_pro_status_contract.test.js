import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const server = fs.readFileSync(
  new URL("../server.js", import.meta.url),
  "utf8"
);

const client = fs.readFileSync(
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/security/StudioSecurityClient.kt",
    import.meta.url
  ),
  "utf8"
);

const accounts = fs.readFileSync(
  new URL(
    "../../android-app/app/src/main/java/com/appforge/studio/AdminAccountsScreen.kt",
    import.meta.url
  ),
  "utf8"
);

test(
  "admin granted PRO bypasses Play Integrity without weakening Google Play PRO",
  () => {
    assert.match(
      server,
      /"admin_panel"/
    );

    assert.match(
      server,
      /"admin_full_access"/
    );

    assert.match(
      server,
      /!adminManagedEntitlement/
    );

    assert.match(
      server,
      /config\.proRequireIntegrity[\s\S]*?!adminManagedEntitlement[\s\S]*?requireIntegrityHeader/
    );

    assert.match(
      client,
      /integritySession\s*=\s*null/
    );

    assert.match(
      client,
      /attest\([\s\S]*?"pro_status"/
    );

    assert.match(
      accounts,
      /PRO • YÖNETİCİ TARAFINDAN VERİLDİ/
    );
  }
);
