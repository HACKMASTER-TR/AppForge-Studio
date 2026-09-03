import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const androidRoot = new URL(
  "../../android-app/app/",
  import.meta.url
);

const source = (path) =>
  readFile(new URL(`src/main/java/com/appforge/studio/${path}`, androidRoot), "utf8");

test("AppForge Terminal is reachable from Studio home and main navigation", async () => {
  const [main, home] = await Promise.all([
    source("MainActivity.kt"),
    source("ui/StudioHomeV2.kt")
  ]);

  assert.match(main, /AppScreen\.TERMINAL/);
  assert.match(main, /TerminalWorkspaceScreen/);
  assert.match(main, /onOpenTerminal\s*=/);
  assert.match(home, /fun StudioHomeV2[\s\S]*onOpenTerminal: \(\) -> Unit/);
  assert.match(home, /onClick = onOpenTerminal/);
});

test("terminal workspace includes project terminal, files, Git, SSH, tools and connections", async () => {
  const workspace = await source("terminal/TerminalWorkspaceScreen.kt");

  for (const tab of [
    "TERMINAL",
    "FILES",
    "GIT",
    "CONNECTIONS",
    "SSH",
    "TOOLS"
  ]) {
    assert.match(workspace, new RegExp(`TerminalWorkspaceTab\\.${tab}`));
  }

  assert.match(workspace, /ProjectLibrary\.restore/);
  assert.match(workspace, /onOpenBuilder/);
  assert.match(workspace, /onOpenAi/);
});

test("terminal commands are reviewed and execute inside the Android app process", async () => {
  const [policy, engine, server] = await Promise.all([
    source("terminal/TerminalCommandPolicy.kt"),
    source("terminal/LocalTerminalEngine.kt"),
    readFile(new URL("../server.js", import.meta.url), "utf8")
  ]);

  assert.match(policy, /mkfs\|mkswap\|fdisk\|parted/);
  assert.ok(policy.includes("git\\\\s+reset\\\\s+--hard"));
  assert.match(engine, /ProcessBuilder\(\s*"\/system\/bin\/sh"/);
  assert.doesNotMatch(server, /["']\/api\/(?:terminal|shell)["']/);
});

test("GitHub device flow and Railway native PKCE use official HTTPS endpoints and encrypted storage", async () => {
  const [client, connections, secureStore, main, manifest, buildFile, privacy] = await Promise.all([
    source("terminal/ExternalConnectionsClient.kt"),
    source("terminal/ConnectionsPanel.kt"),
    source("security/SecureAccountStore.kt"),
    source("MainActivity.kt"),
    readFile(new URL("src/main/AndroidManifest.xml", androidRoot), "utf8"),
    readFile(new URL("build.gradle.kts", androidRoot), "utf8"),
    readFile(new URL("../../docs/privacy.html", androidRoot), "utf8")
  ]);

  assert.match(client, /https:\/\/github\.com\/login\/device\/code/);
  assert.match(client, /https:\/\/github\.com\/login\/oauth\/access_token/);
  assert.doesNotMatch(client, /railway\.com\/oauth\/device/);
  assert.match(client, /https:\/\/backboard\.railway\.com\/oauth\/auth/);
  assert.match(client, /https:\/\/backboard\.railway\.com\/oauth\/token/);
  assert.match(client, /response_type=code/);
  assert.match(client, /code_challenge_method=S256/);
  assert.match(client, /"code_verifier"/);
  assert.match(client, /prompt=consent/);
  assert.match(client, /appforge-studio:\/\/auth\/railway/);
  assert.match(client, /MessageDigest\.isEqual/);
  assert.match(client, /authorization_pending/);
  assert.match(client, /slow_down/);
  assert.match(connections, /APPFORGE_GITHUB_OAUTH_CLIENT_ID/);
  assert.match(connections, /APPFORGE_RAILWAY_OAUTH_CLIENT_ID/);
  assert.match(connections, /savePendingExternalAuthorization/);
  assert.match(main, /data\.path == "\/railway"/);
  assert.match(main, /externalAuthorizationSequence/);
  assert.match(manifest, /android:scheme="appforge-studio"/);
  assert.match(manifest, /android:host="auth"/);
  assert.match(secureStore, /saveExternalConnection/);
  assert.match(secureStore, /savePendingExternalAuthorization/);
  assert.match(secureStore, /AES\/GCM\/NoPadding/);
  assert.match(secureStore, /json\.getString\("provider"\)[\s\S]*safeProvider/);
  assert.doesNotMatch(buildFile, /client_secret/i);
  assert.match(privacy, /GitHub ve Railway bağlantıları/);
  assert.match(privacy, /tokenı AppForge Build Service'e göndermez/);
});

test("embedded Git and SSH dependencies are part of the Android app", async () => {
  const buildFile = await readFile(
    new URL("build.gradle.kts", androidRoot),
    "utf8"
  );

  assert.match(buildFile, /org\.eclipse\.jgit:org\.eclipse\.jgit/);
  assert.match(buildFile, /com\.github\.mwiede:jsch/);
  assert.match(buildFile, /net\.i2p\.crypto:eddsa/);
});

test("encrypted GitHub credentials are restricted to trusted HTTPS remotes", async () => {
  const [gitService, gitPanel] = await Promise.all([
    source("terminal/GitWorkspaceService.kt"),
    source("terminal/GitWorkspacePanel.kt")
  ]);

  assert.match(gitService, /remoteUri\.scheme\.equals\([\s\S]*"https"/);
  assert.match(gitService, /remoteAllowed\(remoteUrl\)/);
  assert.match(gitService, /originUrl\(repository\)/);
  assert.match(gitService, /uri\.userInfo == null/);
  assert.match(gitService, /repositoryRoot == safeWorkspace/);
  assert.match(gitPanel, /setOf\("github\.com"\)/);
});

test("SSH host fingerprints are probed without user credentials", async () => {
  const sshClient = await source("terminal/SshTerminalClient.kt");

  assert.match(sshClient, /suspend fun probe\(\s*profile: SshProfile\s*\)/);
  assert.match(sshClient, /appforge-host-key-probe/);
  assert.match(sshClient, /createJsch\(\s*SshAuth\(\)/);
  assert.match(sshClient, /session\.hostKey/);
});
