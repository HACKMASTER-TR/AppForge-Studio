import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const url = new URL(
  "../../android-app/app/src/main/java/com/appforge/studio/terminal/GitWorkspaceService.kt",
  import.meta.url
);

test("missing Git repository is rejected before JGit build", async () => {
  const source = await readFile(url, "utf8");

  const gitDirCheck =
    source.indexOf("val gitDir =");

  const friendlyError =
    source.indexOf(
      "Bu çalışma alanında Git deposu yok. Önce Git Başlat'a dokunun."
    );

  const buildCall =
    source.indexOf("repositoryBuilder.build()");

  assert.ok(gitDirCheck >= 0);
  assert.ok(friendlyError > gitDirCheck);
  assert.ok(buildCall > friendlyError);

  assert.equal(
    source.includes(
      ".findGitDir(safeWorkspace)\n                    .readEnvironment()\n                    .build()"
    ),
    false
  );
});
