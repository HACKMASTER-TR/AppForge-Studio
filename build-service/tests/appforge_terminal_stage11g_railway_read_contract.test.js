import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const clientUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/terminal/ExternalConnectionsClient.kt",
  import.meta.url
);

const panelUrl = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/terminal/ConnectionsPanel.kt",
  import.meta.url
);

test("Stage 11G adds read-only Railway project access test", async () => {
  const source =
    await readFile(clientUrl, "utf8");

  assert.match(
    source,
    /suspend fun readRailwayOverview\(/
  );

  assert.match(
    source,
    /query AppForgeRailwayReadTest/
  );

  assert.match(
    source,
    /projects \{ edges \{ node \{/
  );

  assert.match(
    source,
    /services \{ edges \{ node \{ id name \}/
  );

  assert.match(
    source,
    /environments \{ edges \{ node \{ id name \}/
  );

  assert.match(
    source,
    /https:\/\/backboard\.railway\.com\/graphql\/v2/
  );

  assert.doesNotMatch(
    source,
    /mutation AppForgeRailwayReadTest/
  );
});

test("Stage 11G never exposes the Railway token in UI", async () => {
  const panel =
    await readFile(panelUrl, "utf8");

  assert.match(
    panel,
    /Proje Erişimini Test Et/
  );

  assert.match(
    panel,
    /readRailwayOverview\(\s*current\.accessToken/
  );

  assert.doesNotMatch(
    panel,
    /Text\(\s*current\.accessToken/
  );

  assert.doesNotMatch(
    panel,
    /clipboard\.setText\([\s\S]{0,100}current\.accessToken/
  );
});

test("Stage 11G fixes nullable Railway identity labels", async () => {
  const client =
    await readFile(clientUrl, "utf8");

  const panel =
    await readFile(panelUrl, "utf8");

  assert.match(
    client,
    /private fun railwayField\(/
  );

  assert.match(
    client,
    /json\.isNull\(key\)/
  );

  assert.match(
    panel,
    /displayExternalAccountLabel\(/
  );

  assert.match(
    panel,
    /ignoreCase = true/
  );
});

test("Stage 11G preserves secure connection storage", async () => {
  const panel =
    await readFile(panelUrl, "utf8");

  assert.match(
    panel,
    /SecureAccountStore\.loadExternalConnection/
  );

  assert.match(
    panel,
    /SecureAccountStore\.saveExternalConnection/
  );

  assert.match(
    panel,
    /PasswordVisualTransformation/
  );
});
