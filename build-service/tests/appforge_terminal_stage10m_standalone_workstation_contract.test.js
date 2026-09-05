import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const read = (path) =>
  readFile(
    new URL(`../../${path}`, import.meta.url),
    "utf8"
  );

const terminalSource = (name) =>
  read(
    `android-app/app/src/main/java/com/appforge/studio/terminal/${name}`
  );

const terminalAsset = (name) =>
  read(
    `android-app/app/src/main/assets/terminal/${name}`
  );

test("Stage 10M bridges the Keystore-backed GitHub connection into only the live PTY", async () => {
  const [bootstrap, pty, proroot] =
    await Promise.all([
      terminalSource(
        "TerminalStandaloneDeveloperBootstrap.kt"
      ),
      terminalSource(
        "LocalPtyTerminalPanel.kt"
      ),
      terminalSource(
        "ProrootRuntimeContract.kt"
      )
    ]);

  assert.match(
    bootstrap,
    /SecureAccountStore/
  );

  assert.match(
    bootstrap,
    /loadExternalConnection/
  );

  assert.match(
    bootstrap,
    /"github"/
  );

  assert.match(
    bootstrap,
    /cacheDir/
  );

  assert.match(
    bootstrap,
    /Os\.chmod/
  );

  assert.match(
    bootstrap,
    /MODE_0600/
  );

  assert.match(
    bootstrap,
    /TerminalGitCredentialLease/
  );

  assert.match(
    bootstrap,
    /deleteRecursively/
  );

  assert.match(
    pty,
    /TerminalGitCredentialBridge\s*\.clearStale/
  );

  assert.match(
    pty,
    /TerminalGitCredentialBridge\s*\.prepare/
  );

  assert.match(
    pty,
    /GIT_ASKPASS/
  );

  assert.match(
    pty,
    /GIT_TERMINAL_PROMPT/
  );

  assert.match(
    pty,
    /credential\.helper/
  );

  assert.match(
    pty,
    /closeGitCredentialLease/
  );

  assert.match(
    proroot,
    /githubCredentialFile/
  );

  assert.match(
    proroot,
    /APPFORGE_GITHUB_CREDENTIAL_GUEST_PATH/
  );

  assert.match(
    proroot,
    /MAX_GITHUB_CREDENTIAL_BYTES/
  );
});

test("Stage 10M askpass is GitHub-host restricted and reads the ephemeral credential mount", async () => {
  const askpass =
    await terminalAsset(
      "appforge-git-askpass"
    );

  assert.match(
    askpass,
    /github\.com/
  );

  assert.match(
    askpass,
    /\/run\/appforge\/github-credential/
  );

  assert.match(
    askpass,
    /Username/
  );

  assert.match(
    askpass,
    /Password/
  );

  assert.match(
    askpass,
    /sed -n '1p'/
  );

  assert.match(
    askpass,
    /sed -n '2p'/
  );

  assert.doesNotMatch(
    askpass,
    /ghp_[A-Za-z0-9]+|github_pat_[A-Za-z0-9_]+/
  );
});

test("Stage 10M installs standalone workstation helpers", async () => {
  const [
    bootstrap,
    doctor,
    ready,
    testHelper,
    ci,
    apk
  ] = await Promise.all([
    terminalSource(
      "TerminalStandaloneDeveloperBootstrap.kt"
    ),
    terminalAsset(
      "appforge-doctor"
    ),
    terminalAsset(
      "appforge-ready"
    ),
    terminalAsset(
      "appforge-test"
    ),
    terminalAsset(
      "appforge-ci"
    ),
    terminalAsset(
      "appforge-apk"
    )
  ]);

  for (const helper of [
    "appforge-git-askpass",
    "appforge-doctor",
    "appforge-ready",
    "appforge-test",
    "appforge-ci",
    "appforge-apk"
  ]) {
    assert.ok(
      bootstrap.includes(`"${helper}"`),
      `missing installed helper ${helper}`
    );
  }

  for (const tool of [
    "git",
    "ssh",
    "curl",
    "wget",
    "python3",
    "node",
    "npm",
    "java",
    "gradle",
    "clang",
    "cmake",
    "jq",
    "nano"
  ]) {
    assert.ok(
      doctor.includes(tool),
      `doctor does not check ${tool}`
    );
  }

  assert.match(
    ready,
    /git[\s\\\\]*-C[\s\\\\]*"\$repo_root"[\s\\\\]*push[\s\\\\]*--dry-run[\s\\\\]*origin[\s\\\\]*HEAD/
  );

  assert.match(
    ready,
    /git[\s\\\\]*-C[\s\\\\]*"\$repo_root"[\s\\\\]*ls-remote[\s\\\\]*origin[\s\\\\]*HEAD/
  );

  assert.match(
    ready,
    /standalone-persistence-probe-v1/
  );

  assert.match(
    testHelper,
    /npm test/
  );

  assert.match(
    ci,
    /actions\/runs/
  );

  assert.match(
    ci,
    /workflow_runs/
  );

  assert.match(
    apk,
    /appforge-studio-latest/
  );

  assert.match(
    apk,
    /AppForgeDownloads/
  );

  assert.match(
    apk,
    /sha256sum/
  );
});

test("Stage 10M keeps the Linux environment persistent and adds a shell editor", async () => {
  const [manager, foundation] =
    await Promise.all([
      terminalSource(
        "AndroidLinuxRuntimeManager.kt"
      ),
      terminalSource(
        "LinuxRuntimeFoundation.kt"
      )
    ]);

  assert.match(
    manager,
    /noBackupFilesDir/
  );

  assert.match(
    manager,
    /appforge-dev-suite-v3/
  );

  assert.match(
    foundation,
    /"nano"/
  );

  assert.match(
    foundation,
    /"less"/
  );

  assert.match(
    foundation,
    /"file"/
  );

  assert.match(
    foundation,
    /"procps"/
  );
});

test("Stage 10M does not regress the device-verified IME architecture", async () => {
  const pty =
    await terminalSource(
      "LocalPtyTerminalPanel.kt"
    );

  assert.doesNotMatch(
    pty,
    /\.imePadding\(\)/
  );

  assert.match(
    pty,
    /WindowInsets\.ime/
  );

  assert.match(
    pty,
    /\.offset\s*\{\s*IntOffset\([\s\S]*?y\s*=\s*-imeInsets\.getBottom\(this\)/
  );

  assert.match(
    pty,
    /value\s*=\s*imeValue/
  );

  assert.match(
    pty,
    /autoCorrectEnabled\s*=\s*false/
  );

  for (const key of [
    '"CTRL+C"',
    '"CTRL+A"',
    '"CTRL+E"',
    '"CTRL+R"',
    '"CTRL+U"',
    '"CTRL+W"'
  ]) {
    assert.ok(
      pty.includes(key),
      `verified key disappeared: ${key}`
    );
  }
});
