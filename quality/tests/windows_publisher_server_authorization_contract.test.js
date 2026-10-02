import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here =
  path.dirname(
    fileURLToPath(
      import.meta.url
    )
  );

const repo =
  path.resolve(
    here,
    "../.."
  );

const read =
  relative =>
    fs.readFileSync(
      path.join(
        repo,
        relative
      ),
      "utf8"
    );

test(
  "publisher signing requires server verified one time grant",
  () => {
    const client =
      read(
        "android-app/app/src/main/java/com/appforge/studio/build/WindowsPublisherSigningAuthorizationClient.kt"
      );

    const policy =
      read(
        "android-app/app/src/main/java/com/appforge/studio/build/WindowsPublisherSigningPolicy.kt"
      );

    assert.match(
      policy,
      /authorizeAndConsume/
    );

    for (
      const marker of [
        "/api/admin/windows-signing/grant",
        "/api/admin/windows-signing/consume",
        "artifactSha256",
        "requestNonce",
        "SecureRandom",
        "currentGoogleIdToken",
        "consumedGrantIds"
      ]
    ) {
      assert.ok(
        client.includes(marker),
        marker
      );
    }
  }
);

test(
  "provider binds exact authorized unsigned artifact",
  () => {
    const provider =
      read(
        "android-app/app/src/main/java/com/appforge/studio/build/WindowsPublisherSigningProvider.kt"
      );

    assert.match(
      provider,
      /WindowsPublisherSigningAuthorization/
    );

    const matches =
      provider.match(
        /requireArtifactMatch/g
      ) || [];

    assert.ok(
      matches.length >= 2
    );
  }
);

test(
  "server keeps issue consume and replay evidence",
  () => {
    const worker =
      read(
        "cloudflare/control-plane/src/index.mjs"
      );

    for (
      const marker of [
        "windows_signing_grant_issued",
        "windows_signing_grant_consumed",
        "signing_grant_replay",
        "verifiedAdminHash",
        "actor_reference_hash"
      ]
    ) {
      assert.ok(
        worker.includes(marker),
        marker
      );
    }
  }
);

test(
  "publisher server authorization adds no D1 migration",
  () => {
    const migrations =
      fs.readdirSync(
        path.join(
          repo,
          "cloudflare/control-plane/migrations"
        )
      )
        .filter(
          file =>
            file.endsWith(".sql")
        )
        .sort();

    assert.deepEqual(
      migrations,
      [
        "0001_accountless_control_plane.sql",
        "0002_admin_pro_codes.sql",
        "0003_pro_grants.sql",
        "0004_pro_grant_revocation.sql",
        "0005_pro_lifecycle.sql"
      ]
    );
  }
);
