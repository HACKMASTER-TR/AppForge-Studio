import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const androidRoot = new URL(
  "../../android-app/app/",
  import.meta.url
);

const source = (path) =>
  readFile(
    new URL(
      `src/main/java/com/appforge/studio/${path}`,
      androidRoot
    ),
    "utf8"
  );

test("advanced Git is integrated into the existing embedded Git panel", async () => {
  const [panel, advanced] = await Promise.all([
    source("terminal/GitWorkspacePanel.kt"),
    source("terminal/AdvancedGitPanel.kt")
  ]);

  assert.match(panel, /AdvancedGitPanel\(/);
  assert.match(advanced, /\.stagePath\(/);
  assert.match(advanced, /\.unstagePath\(/);
  assert.match(advanced, /\.createBranch\(/);
  assert.match(advanced, /\.checkoutBranch\(/);
  assert.match(advanced, /GitConflictChoice\.OURS/);
  assert.match(advanced, /GitConflictChoice\.THEIRS/);
});

test("advanced JGit layer validates paths, branch names and caps diff output", async () => {
  const service = await source(
    "terminal/AdvancedGitService.kt"
  );

  assert.match(service, /Repository\.isValidRefName/);
  assert.match(service, /canonicalFile/);
  assert.match(service, /setCached\(staged\)/);
  assert.match(service, /MAX_DIFF_BYTES/);
  assert.match(service, /CheckoutCommand\.Stage\.OURS/);
  assert.match(service, /CheckoutCommand\.Stage\.THEIRS/);
  assert.doesNotMatch(service, /reset\(\).*setMode.*HARD/s);
  assert.doesNotMatch(service, /force\s*=\s*true/i);
});

test("GitHub DevOps client is pinned to api.github.com and does not follow redirects", async () => {
  const client = await source(
    "terminal/GitHubDevOpsClient.kt"
  );

  assert.match(client, /https:\/\/api\.github\.com/);
  assert.match(client, /url\.protocol == "https"/);
  assert.match(client, /url\.host == API_HOST/);
  assert.match(client, /instanceFollowRedirects\s*=\s*false/);
  assert.match(client, /X-GitHub-Api-Version/);
  assert.match(client, /Authorization/);
  assert.match(client, /Bearer \$token/);
  assert.match(client, /MAX_RESPONSE_BYTES/);
  assert.doesNotMatch(client, /releases\/latest/);
  assert.doesNotMatch(client, /SharedPreferences/);
  assert.doesNotMatch(client, /writeText|appendText|FileOutputStream/);
});

test("GitHub dashboard covers PRs, Actions, releases and artifacts", async () => {
  const client = await source(
    "terminal/GitHubDevOpsClient.kt"
  );

  assert.match(client, /\/pulls\?state=open/);
  assert.match(client, /\/actions\/runs\?per_page=20/);
  assert.match(client, /\/releases\?per_page=20/);
  assert.match(client, /\/actions\/artifacts\?per_page=20/);
  assert.match(client, /createPullRequest/);
  assert.match(client, /method\s*=\s*"POST"/);
});
