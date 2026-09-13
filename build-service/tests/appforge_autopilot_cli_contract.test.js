import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const cliUrl =
  new URL("../../scripts/appforge", import.meta.url);

test("AppForge Autopilot exposes the admin control-plane commands", async () => {
  const source = await readFile(cliUrl, "utf8");

  for (const command of [
    "status", "doctor", "test", "security", "dashboard",
    "ci", "report", "submit", "resume", "recover", "release", "deploy", "rollback"
  ]) {
    assert.match(source, new RegExp(`["']${command}["']`));
  }

  assert.match(source, /dashboard_refresh_seconds/);
  assert.match(source, /def github_repo\(\):/);
  assert.ok(
    source.includes(
      'f"repos/{repo}/pulls"'
    )
  );
  assert.ok(
    source.includes(
      '"?state=open&per_page=100"'
    )
  );
  assert.match(
    source,
    /head\.get\("ref"\)\s*==\s*branch/
  );
  assert.match(
    source,
    /existing\s*=\s*pr_number\(\)/
  );
  assert.ok(
    source.includes('f"repos/{repo}/commits/"')
  );
  assert.ok(
    source.includes('f"{head_sha}/check-runs"')
  );
  assert.ok(
    source.includes('f"repos/{repo}/pulls/{num}/merge"')
  );

  assert.doesNotMatch(
    source,
    /\["gh",\s*"pr"/
  );
  assert.match(source, /android-debug\.yml/);
  assert.match(source, /workflow_display_name/);
  assert.match(source, /wait_required_main_ci/);
  assert.match(source, /Latest APK release/);
  assert.match(source, /stale_contracts/);
  assert.match(source, /scan_secrets/);
  assert.match(source, /forbidden_files/);
  assert.match(source, /APPFORGE_ADMIN_SESSION/);
  assert.match(source, /PERMISSION_DENIED/);
  assert.match(source, /require_admin_session/);
});
