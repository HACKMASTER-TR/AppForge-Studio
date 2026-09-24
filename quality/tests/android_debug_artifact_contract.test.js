import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const workflow = await readFile(
  new URL(
    "../../.github/workflows/android-debug.yml",
    import.meta.url
  ),
  "utf8"
);

test("Android Debug publishes a downloadable test APK artifact", () => {
  assert.match(
    workflow,
    /actions\/upload-artifact@v5/
  );

  assert.match(
    workflow,
    /AppForgeStudio-debug-\$\{\{\s*github\.sha\s*\}\}/
  );

  assert.match(
    workflow,
    /path:\s*AppForgeStudio-latest\.apk/
  );

  assert.match(
    workflow,
    /if-no-files-found:\s*error/
  );
});

test("latest GitHub Release remains main-only", () => {
  assert.match(
    workflow,
    /Publish latest APK release[\s\S]*github\.ref == 'refs\/heads\/main'/
  );
});

test("main push cannot publish a Release without explicit approval", () => {
  assert.match(workflow, /publish_latest_release:/);
  assert.match(workflow, /github\.event_name == 'workflow_dispatch' && inputs\.publish_latest_release == true/);
});
